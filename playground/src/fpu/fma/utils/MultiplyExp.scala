import chisel3._
import chisel3.util._
import fpu._

class MultiplyExpIn extends FPUIO {
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
class MultiplyExpOut extends FPUIO {
  val exponentDifference = Output(SInt(ExponentLEN.W))
  // val exponentTentative  = Output(SInt(ExponentLEN.W))
  val exponentTentative  = Output(UInt(ExponentLEN.W))
  val addendShiftAmount  = Output(UInt(AddendShiftAmountLEN.W))  
}
class MultiplyExpIO extends FPUIO {
  val in  = new MultiplyExpIn
  val out = new MultiplyExpOut
}

class MultiplyExp extends FPUModule {
  val io = IO(new MultiplyExpIO)

  val exponentExt = io.in.operand.exponent.map{exponent => Cat(0.U(1.W), exponent).asSInt}

  // val exponentAddend  = exponentExt(2) + (Cat(0.U(1.W), ~io.in.operandInfo.isNormal(2))).asSInt
  val exponentAddend  = io.in.operand.exponent(2) +& (~io.in.operandInfo.isNormal(2))
  val exponentProduct = Mux((io.in.operandInfo.isZero(0) | io.in.operandInfo.isZero(1)),
                        // 2.S - ExponentBias.S, // product is 0
                        // exponentExt(0) + exponentExt(1) - ExponentBias.S + io.in.operandInfo.isSubnormal(0).asSInt + io.in.operandInfo.isSubnormal(1).asSInt)
                        0.U, // product is 0
                        io.in.operand.exponent(0) +& io.in.operandInfo.isSubnormal(0) +&
                        io.in.operand.exponent(1) +& io.in.operandInfo.isSubnormal(1)
                        -& ExponentBias.U)

  // io.out.exponentDifference := exponentAddend - exponentProduct
  io.out.exponentDifference := (exponentAddend - exponentProduct).asSInt
  io.out.exponentTentative  := Mux((io.out.exponentDifference > 0.S),
                               exponentAddend,
                               exponentProduct)
  

  io.out.addendShiftAmount  := MuxCase(0.U, // default
    Array(
      (io.out.exponentDifference <= (-2 * PrecisionLEN - 1).S)
        -> (3 * PrecisionLEN + 4).U,
      (io.out.exponentDifference <= (PrecisionLEN + 2).S)
        -> (PrecisionLEN.S + 3.S - io.out.exponentDifference).asUInt
    )
  )
}