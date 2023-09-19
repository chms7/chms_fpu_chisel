import chisel3._
import chisel3.util._
import fpu._

// * MulAdder IO
class MulAdderIn extends FPUIO {
  // Operand Sign & Exp & Man
  val operand = new Bundle{
    val sign      = Input(Vec(3, UInt(SignLEN.W    )))
    val exponent  = Input(Vec(3, UInt(ExponentLEN.W)))
    val mantissa  = Input(Vec(3, UInt(MantissaLEN.W)))
  }
  // Operand Info
  val operandInfo = new Bundle{
    val isBoxed      = Input(Vec(3, Bool()))
    val isNormal     = Input(Vec(3, Bool()))
    val isSubnormal  = Input(Vec(3, Bool()))
    val isZero       = Input(Vec(3, Bool()))
    val isInf        = Input(Vec(3, Bool()))
    val isNan        = Input(Vec(3, Bool()))
    val isSignalling = Input(Vec(3, Bool()))
    val isQuiet      = Input(Vec(3, Bool()))
  }
}
class MulAdderOut extends FPUIO {
  val exponentProduct       = Output(SInt(ExponentExtLEN.W))
  val exponentDifference    = Output(SInt(ExponentExtLEN.W))
  val exponentTentative     = Output(SInt(ExponentExtLEN.W))
  val addendShiftAmount     = Output(UInt(AddendShiftAmountLEN.W))  
  val stickyBeforeAdd       = Output(UInt(1.W))
  val effectiveSubstraction = Output(UInt(1.W))
  val adderSum              = Output(UInt((3*PrecisionLEN+4).W))
  val signFinal             = Output(UInt(1.W))
}
class MulAdderIO extends FPUIO {
  val in  = new MulAdderIn
  val out = new MulAdderOut
}

// * MulAdder Module
class MulAdder extends FPUModule {
  val io = IO(new MulAdderIO)
  
  // * ------------------
  // * Exponent DataPath
  // * ------------------
  // . Exponent Extend: Zero, and convert to SInt
  val exponentExt = io.in.operand.exponent.map{exponent => Cat(0.U(2.W), exponent).asSInt}
  // . Addend Exponent
  // val exponentAddend  = exponentExt(2) + (Cat(0.U(1.W), ~io.in.operandInfo.isNormal(2))).asSInt
  val exponentAddend  = (io.in.operand.exponent(2) +& (~io.in.operandInfo.isNormal(2))).asSInt
  // . Product Exponent
  val exponentProduct = Mux((io.in.operandInfo.isZero(0) | io.in.operandInfo.isZero(1)),
                          2.S -& ExponentBias.S, // ? product is 0
                          // exponentExt(0) + exponentExt(1) - ExponentBias.S
                          //   + io.in.operandInfo.isSubnormal(0).asSInt + io.in.operandInfo.isSubnormal(1).asSInt)
                          // 1.U, // ? product is 0, isSubnormal so taken as 1
                          (io.in.operand.exponent(0) +& io.in.operandInfo.isSubnormal(0) +
                           io.in.operand.exponent(1) +  io.in.operandInfo.isSubnormal(1)
                           - ExponentBias.U).asSInt)
  // . Exponent Difference of Product and Addend
  val exponentDifference = exponentAddend -& exponentProduct
  // . Exponent Tentative:  choose the larger exponent
  val exponentTentative  = Mux((exponentDifference > 0.S),
                                exponentAddend, exponentProduct)
  // . Addend Shift Amount: shift the smaller addend
  // ?
  val addendShiftAmount = MuxCase(
    // Addend-anchored, Product Saturated Shift
    0.U,
    Array(
      // Product-anchored, Addend Saturated Shift
      (exponentDifference <= (-2 * PrecisionLEN - 1).S)
        -> (3 * PrecisionLEN + 4).U,
      // Addend and Product will have mutual bits to add
      ((exponentDifference <= (PrecisionLEN + 2).S) & (exponentDifference > ((-2 * PrecisionLEN - 1).S)))
        -> (PrecisionLEN.S + 3.S - exponentDifference).asUInt
    )
  )

  // * ------------------
  // * Product DataPath
  // * ------------------
  // . Mantissa Extend: Normal -> 1, Subnormal -> 0
  val mantissaExt = VecInit(Cat(io.in.operandInfo.isNormal(0), io.in.operand.mantissa(0)),
                            Cat(io.in.operandInfo.isNormal(1), io.in.operand.mantissa(1)),
                            Cat(io.in.operandInfo.isNormal(2), io.in.operand.mantissa(2)))
  // . Mantissa Product: into a 3p+4 bit vector, padded with 2 bits for round and sticky
  // | 000...000 | product | RS |
  //  <-  p+2  -> <-  2p -> < 2>  = [3p+4]
  val mantissaProduct = Cat(0.U((PrecisionLEN+2).W) ,(mantissaExt(0) * mantissaExt(1)) << 2)

  // * ------------------
  // * AddendProcess
  // * ------------------
  // ? Addend Shift
  // BEFORE THE SHIFT:
  // | mantissa_c | 000..000 |
  //  <-    p   -> <- 3p+4 ->
  // AFTER THE SHIFT:
  // | 000..........000 | mantissa_c | 000...............0GR |  sticky bits  |
  //  <- addend_shamt -> <-    p   -> <- 2p+4-addend_shamt -> <-  up to p  ->
  val addend = ((mantissaExt(2) << (3*PrecisionLEN+4)) >> addendShiftAmount)
  val addendAfterShift = addend(4*PrecisionLEN+4-1,PrecisionLEN)
  val addendStickyBits = addend(PrecisionLEN-1,0)
  val stickyBeforeAdd  = addendStickyBits.orR

  val effectiveSubstraction = io.in.operand.sign.reduce(_^_)
  val addendShifted = Mux(effectiveSubstraction.asBool,
                              ~addendAfterShift, addendAfterShift)
  val injectCarryIn = effectiveSubstraction & (~stickyBeforeAdd)

  // * ------------------
  // * Adder
  // * ------------------
  val adderSumRaw   = mantissaProduct +& addendShifted + injectCarryIn
  val adderSumCarry = adderSumRaw(3*PrecisionLEN+4)
  val adderSum      = Mux(effectiveSubstraction.asBool & (~adderSumCarry),
                          -adderSumRaw, adderSumRaw)  // ?
  
  val signTentative = io.in.operand.sign(0) ^ io.in.operand.sign(1)
  val signFinal     = Mux(effectiveSubstraction.asBool & (adderSumCarry === signTentative), 
                          1.U(1.W), Mux(effectiveSubstraction.asBool, 
                                        0.U(1.W), signTentative))

  // * Out
  io.out.exponentProduct        := exponentProduct
  io.out.exponentDifference     := exponentDifference
  io.out.exponentTentative      := exponentTentative
  io.out.addendShiftAmount      := addendShiftAmount
  io.out.stickyBeforeAdd        := stickyBeforeAdd
  io.out.effectiveSubstraction  := effectiveSubstraction
  io.out.adderSum               := adderSum
  io.out.signFinal              := signFinal
}
