package fpu
import chisel3._
import chisel3.util._

// Parameter define of FPU
trait FPUParameter {
  val OPInfoLEN         = 23

  val FLEN              = 32

  val SignLEN           = 1
  val SignBitStart      = FLEN-1
  val SignBitEnd        = SignBitStart-SignLEN+1

  val ExponentLEN       = 8
  val ExponentBitStart  = SignBitEnd-1
  val ExponentBitEnd    = ExponentBitStart-ExponentLEN+1
  val ExponentBias      = 127
  val ExponentExtLEN    = ExponentLEN+2

  val MantissaLEN       = 23
  val MantissaBitStart  = ExponentBitEnd-1
  val MantissaBitEnd    = MantissaBitStart-MantissaLEN+1
  val PrecisionLEN      = MantissaLEN+1 // MantissaLEN + Implicit Bit

  val AddendShiftAmountLEN = 7
  val LowerSumWidth  = 2*PrecisionLEN+3
  val LZCResultWidth = 6

  val NEGINF     = "b00_0000_0001"
  val NEGNORM    = "b00_0000_0010"
  val NEGSUBNORM = "b00_0000_0100"
  val NEGZERO    = "b00_0000_1000"
  val POSZERO    = "b00_0001_0000"
  val POSSUBNORM = "b00_0010_0000"
  val POSNORM    = "b00_0100_0000"
  val POSINF     = "b00_1000_0000"
  val SNAN       = "b01_0000_0000"
  val QNAN       = "b10_0000_0000"
}

// Module of FPU
abstract class FPUModule extends Module with FPUParameter

// IO of FPU
abstract class FPUIO extends Bundle with FPUParameter

// * FComponent IO
class FComponentIn extends FPUIO {
  // Operand Sign & Exp & Man
  val operandIn = Input(Vec(3, UInt(FLEN.W)))
  val operand   = new Bundle{
    val sign      = Input(Vec(3, UInt(SignLEN.W    )))
    val exponent  = Input(Vec(3, UInt(ExponentLEN.W)))
    val mantissa  = Input(Vec(3, UInt(MantissaLEN.W)))
  }
  // Instruction Info
  val instType = new Bundle{
    val fmadd   = Input(Bool())
    val fmsub   = Input(Bool())
    val fnmsub  = Input(Bool())
    val fnmadd  = Input(Bool())
    val fadd    = Input(Bool())
    val fsub    = Input(Bool())
    val fmul    = Input(Bool())
    val fminmax = Input(Bool())
    val fmvxw   = Input(Bool())
    val fmvwx   = Input(Bool())
    val fsgnj   = Input(Bool())
    val fcmp    = Input(Bool())
    val fclass  = Input(Bool())
  }
  // Operand Info
  val opInfo      = Input(UInt(OPInfoLEN.W))
  // val operandInfo = Input(new operandInfo)
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
  // Round Mode
  val roundModeIn = Input(UInt(3.W))
  val roundMode   = new Bundle{
    val RNE = Input(Bool())
    val RTZ = Input(Bool())
    val RDN = Input(Bool())
    val RUP = Input(Bool())
    val RMM = Input(Bool())
  }
}
class FComponentOut extends FPUIO {
  val result = Output(UInt(FLEN.W))
  val fflags = Output(UInt(5.W))
}
class FComponentIO extends FPUIO {
  val in  = new FComponentIn
  val out = new FComponentOut
  // val in  = Flipped(Decoupled((new FComponentIn)))
  // val out = Decoupled(new FComponentOut)
}