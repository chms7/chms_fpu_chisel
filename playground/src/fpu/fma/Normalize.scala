import chisel3._
import chisel3.util._
import fpu._

// * Normalize IO
class NormalizeIn extends FPUIO {
  val effectiveSubstraction = Input(UInt(1.W))
  val exponentProduct       = Input(SInt(ExponentExtLEN.W))
  val exponentDifference    = Input(SInt(ExponentExtLEN.W))
  val exponentTentative     = Input(SInt(ExponentExtLEN.W))
  val addendShiftAmount     = Input(UInt(AddendShiftAmountLEN.W))  
  val stickyBeforeAdd       = Input(UInt(1.W))
  val adderSum              = Input(UInt((3*PrecisionLEN+4).W))
  val signFinal             = Input(UInt(1.W))
  val roundMode = new Bundle{
    val RNE = Input(Bool())
    val RTZ = Input(Bool())
    val RDN = Input(Bool())
    val RUP = Input(Bool())
    val RMM = Input(Bool())
  }
}
class NormalizeOut extends FPUIO {
  val resultRegular = Output(UInt(FLEN.W))
  val fflagsRegular = new Bundle{
    val NV = Output(UInt(1.W))
    val DZ = Output(UInt(1.W))
    val OF = Output(UInt(1.W))
    val UF = Output(UInt(1.W))
    val NX = Output(UInt(1.W))
  }
}
class NormalizeIO extends FPUIO {
  val in  = new NormalizeIn
  val out = new NormalizeOut
}

// * Normalize Module
class Normalize extends FPUModule {
  val io = IO(new NormalizeIO)

  // * ------------------
  // * LZC
  // * ------------------
  val LZC = Module(new LZC)
  // . Lower 2p+3 bits of sum are searched
  LZC.io.in.sumLower     := io.in.adderSum(LowerSumWidth-1, 0)
  // . Leading Zero Count
  val leadingZeroCount    = LZC.io.out.leadingZeroCount
  val leadingZeroCountSgn = (Cat(0.U(1.W), LZC.io.out.leadingZeroCount)).asSInt
  // . Only Zero Found
  val lzcZeroes           = LZC.io.out.lzcZeroes
  
  // * ------------------
  // * Large Normalize
  // * ------------------
  val normalizeShiftAmount = Wire(UInt(AddendShiftAmountLEN.W))
  val exponentNormalized   = Wire(SInt(ExponentExtLEN.W))
  when((io.in.exponentDifference <= 0.S) | (io.in.effectiveSubstraction.asBool & (io.in.exponentDifference <= 2.S))){
    when(((io.in.exponentProduct - leadingZeroCountSgn.asSInt + 1.S).asSInt >= 0.S) & (~lzcZeroes).asBool){
      normalizeShiftAmount := PrecisionLEN.U + 2.U + leadingZeroCount
      exponentNormalized   := io.in.exponentProduct - leadingZeroCountSgn.asSInt + 1.S   // ?
    }.otherwise{
      normalizeShiftAmount := (PrecisionLEN.S + 2.S + io.in.exponentProduct).asUInt // ?
      exponentNormalized   := 0.S
    }
  }.otherwise{
    normalizeShiftAmount := io.in.addendShiftAmount
    exponentNormalized   := io.in.exponentTentative
  }
  val sumShifted = Wire(UInt((3*PrecisionLEN+5).W))
  sumShifted := io.in.adderSum << normalizeShiftAmount
  
  // * ------------------
  // * Small Normalize
  // * ------------------
  // val mantissaFinal = Wire(UInt((PrecisionLEN+1).W))
  // val sumStickyBits = Wire(UInt((2*PrecisionLEN+3).W))
  val manAndSticky  = Wire(UInt((3*PrecisionLEN+4).W))
  val exponentFinal = Wire(SInt(ExponentExtLEN.W))
  // default
  // mantissaFinal   := sumShifted(3*PrecisionLEN+1, 2*PrecisionLEN+3)
  // sumStickyBits   := sumShifted(2*PrecisionLEN+2, 0)
  manAndSticky    := sumShifted(3*PrecisionLEN+3, 0)
  exponentFinal   := exponentNormalized
  when(sumShifted(3*PrecisionLEN+4)){
    // mantissaFinal := (sumShifted >> 1)(3*PrecisionLEN+1, 2*PrecisionLEN+3)
    // sumStickyBits := (sumShifted >> 1)(2*PrecisionLEN+2, 0)
    manAndSticky  := (sumShifted >> 1)(3*PrecisionLEN+3, 0)
    exponentFinal := exponentNormalized + 1.S
  }.elsewhen(sumShifted(3*PrecisionLEN+3)){
    // do nothing
  }.elsewhen(exponentNormalized > 1.S){
    // mantissaFinal := (sumShifted << 1)(3*PrecisionLEN+1, 2*PrecisionLEN+3)
    // sumStickyBits := (sumShifted << 1)(2*PrecisionLEN+2, 0)
    manAndSticky  := (sumShifted << 1)(3*PrecisionLEN+3, 0)
    exponentFinal := exponentNormalized - 1.S
  }.otherwise{
    exponentFinal := 0.S
  }
  val mantissaFinal = manAndSticky(3*PrecisionLEN+3, 2*PrecisionLEN+3)
  val sumStickyBits = manAndSticky(2*PrecisionLEN+2, 0)
  val stickyAfterNormalize = sumStickyBits.orR | io.in.stickyBeforeAdd

  // * ------------------
  // * Round
  // * ------------------
  val OFBeforeRound = exponentFinal >= 255.S
  val UFBeforeRound = exponentFinal === 0.S
  val preRoundSign     = io.in.signFinal
  val preRoundExponent = Mux(OFBeforeRound, 254.U, exponentFinal(ExponentLEN-1, 0))
  val preRoundMantissa = Mux(OFBeforeRound, 8388607.U(MantissaLEN.W), mantissaFinal(MantissaLEN, 1))
  val preRoundAbs      = Cat(preRoundExponent, preRoundMantissa)
  val roundStickyBits = Mux(OFBeforeRound, 3.U(2.W), Cat(mantissaFinal(0), stickyAfterNormalize))

  val Round = Module(new Round)
  Round.io.in.preRoundAbs           := preRoundAbs
  Round.io.in.preRoundSign          := preRoundSign
  Round.io.in.roundStickyBits       := roundStickyBits
  Round.io.in.roundModeIn           := io.in.roundMode.asUInt
  Round.io.in.effectiveSubstraction := io.in.effectiveSubstraction
  val roundedAbs  = Round.io.out.roundedAbs
  val roundedSign = Round.io.out.roundedSign
  val resultZero  = Round.io.out.resultZero

  val UFAfterRound = roundedAbs(ExponentLEN+MantissaLEN-1, MantissaLEN) === 0.U(ExponentLEN.W)
  val OFAfterRound = roundedAbs(ExponentLEN+MantissaLEN-1, MantissaLEN) === 255.U(ExponentLEN.W)

  // * ------------------
  // * Result Regular
  // * ------------------
  val resultRegular   = Cat(roundedSign, roundedAbs)
  val fflagsRegularNV = 0.U(1.W)
  val fflagsRegularDZ = 0.U(1.W)
  val fflagsRegularOF = OFBeforeRound | OFAfterRound
  val fflagsRegularNX = roundStickyBits.orR | OFBeforeRound | OFAfterRound
  val fflagsRegularUF = UFAfterRound & fflagsRegularNX

  // Output
  io.out.resultRegular    := resultRegular
  io.out.fflagsRegular.NV := fflagsRegularNV
  io.out.fflagsRegular.DZ := fflagsRegularDZ
  io.out.fflagsRegular.OF := fflagsRegularOF
  io.out.fflagsRegular.UF := fflagsRegularUF
  io.out.fflagsRegular.NX := fflagsRegularNX
  
  // io.out.mantissaFinal := mantissaFinal
  // io.out.sumStickyBits := sumStickyBits
}