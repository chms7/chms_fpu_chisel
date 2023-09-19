import chisel3._
import chisel3.util._
import fpu._

// * MultiplyMan IO
class MultiplyManIn extends FPUIO {
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
class MultiplyManOut extends FPUIO {
  val mantissaExt       = Output(Vec(3, UInt(PrecisionLEN.W)))
  val mantissaProduct   = Output(UInt((3*PrecisionLEN+4).W))  // ?
}
class MultiplyManIO extends FPUIO {
  val in  = new MultiplyManIn
  val out = new MultiplyManOut
}

// * MultiplyMan Module
class MultiplyMan extends FPUModule {
  val io = IO(new MultiplyManIO)

  io.out.mantissaExt := VecInit(Cat(io.in.operandInfo.isNormal(0), io.in.operand.mantissa(0)),
                                Cat(io.in.operandInfo.isNormal(1), io.in.operand.mantissa(1)),
                                Cat(io.in.operandInfo.isNormal(2), io.in.operand.mantissa(2)))

  // val product = io.out.mantissaExt(0) * io.out.mantissaExt(1)
  // io.out.mantissaProduct := product << 2
  io.out.mantissaProduct := Cat(0.U((PrecisionLEN+2).W) ,(io.out.mantissaExt(0) * io.out.mantissaExt(1)) << 2)
}