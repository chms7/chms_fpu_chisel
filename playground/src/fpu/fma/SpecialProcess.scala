import chisel3._
import chisel3.util._
import fpu._
import java.util.spi.ResourceBundleProvider

// * SpecialProcess IO
class SpecialProcessIn extends FPUIO {
  val operand = new Bundle{
    val sign      = Input(Vec(3, UInt(SignLEN.W    )))
    val exponent  = Input(Vec(3, UInt(ExponentLEN.W)))
    val mantissa  = Input(Vec(3, UInt(MantissaLEN.W)))
  }
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
class SpecialProcessOut extends FPUIO {
  val resultSpecial = Output(UInt(FLEN.W))
  val fflagsSpecial = new Bundle{
    val NV = Output(UInt(1.W))
    val DZ = Output(UInt(1.W))
    val OF = Output(UInt(1.W))
    val UF = Output(UInt(1.W))
    val NX = Output(UInt(1.W))
  }
  val resultIsSpecial = Output(UInt(1.W))
}
class SpecialProcessIO extends FPUIO {
  val in  = new SpecialProcessIn
  val out = new SpecialProcessOut
}

// * SpecialProcess Module
class SpecialProcess extends FPUModule {
  val io = IO(new SpecialProcessIO)

  // operand Info
  val anyOperandInf         = io.in.operandInfo.isInf.reduce(_|_)
  val anyOperandNan         = io.in.operandInfo.isNan.reduce(_|_)
  val anyOperandSignalling  = io.in.operandInfo.isSignalling.reduce(_|_)

  val effectiveSubstraction = io.in.operand.sign.reduce(_^_)

  // Default
  io.out.resultSpecial    := Cat(1.U(SignLEN.W),    // qNaN
                                 255.U(ExponentLEN.W),
                                 4194304.U(MantissaLEN.W))
  io.out.fflagsSpecial.NV := 0.U
  io.out.fflagsSpecial.DZ := 0.U
  io.out.fflagsSpecial.OF := 0.U
  io.out.fflagsSpecial.UF := 0.U
  io.out.fflagsSpecial.NX := 0.U
  io.out.resultIsSpecial  := 0.U

  when((io.in.operandInfo.isInf(0) & io.in.operandInfo.isZero(1)) | (io.in.operandInfo.isZero(0) & io.in.operandInfo.isInf(1))){
    // * [(0 * Inf) + op2] or [(Inf * 0) + op2] : qNaN
    io.out.resultIsSpecial  := 1.U
    io.out.fflagsSpecial.NV := 1.U
  }.elsewhen(anyOperandNan){
    // * Any operand is NaN : qNaN
    io.out.resultIsSpecial  := 1.U
    io.out.fflagsSpecial.NV := anyOperandSignalling
  }.elsewhen(anyOperandInf){
    // * Any operand is Inf
    io.out.resultIsSpecial := 1.U
    when((io.in.operandInfo.isInf(0) | io.in.operandInfo.isInf(1)) & io.in.operandInfo.isInf(2) & effectiveSubstraction.asBool){
      // * [(Inf * op1) + Inf] or [(op0 * Inf) + Inf] and effectiveSubstraction : qNaN
      io.out.fflagsSpecial.NV := 1.U
    }.elsewhen(io.in.operandInfo.isInf(0) | io.in.operandInfo.isInf(1)){
      // * [(Inf * op1) + op2] or [(op0 * Inf) + op2] : Inf with sign of product
      io.out.resultSpecial  := Cat(io.in.operand.sign(0) ^ io.in.operand.sign(1), 255.U(ExponentLEN.W), 0.U(MantissaLEN.W))
    }.elsewhen(io.in.operandInfo.isInf(2)){
      // * [(op0 * op1) + Inf] : Inf with sign of addend
      io.out.resultSpecial  := Cat(io.in.operand.sign(2), 255.U(ExponentLEN.W), 0.U(MantissaLEN.W))
    }
  }
  // io.out.resultSpecial := MuxCase(resultSpecialDefault,  // default
  //   Array(
  //     ((io.in.operandInfo.isInf(0) & io.in.operandInfo.isZero(1)) | (io.in.operandInfo.isZero(0) & io.in.operandInfo.isInf(1)))
  //     -> resultSpecialDefault,
  //     (anyOperandNan)
  //     -> resultSpecialDefault,
  //     // (anyOperandInf)
  //     // -> 
  //   )
  // )
  // io.out.fflagsSpecial := MuxCase(fflagsSpecialDefault,  // default
  //   Array(
  //     ((io.in.operandInfo.isInf(0) & io.in.operandInfo.isZero(1)) | (io.in.operandInfo.isZero(0) & io.in.operandInfo.isInf(1)))
  //     -> resultSpecialDefault,
  //     (anyOperandNan)
  //     -> resultSpecialDefault,
  //     // (anyOperandInf)
  //     // -> 
  //   )
  // )
  // io.out.resultIsSpecial := MuxCase(resultIsSpecialDefault,  // default
  //   Array(
  //     ((io.in.operandInfo.isInf(0) & io.in.operandInfo.isZero(1)) | (io.in.operandInfo.isZero(0) & io.in.operandInfo.isInf(1)))
  //     -> 1.U,
  //     (anyOperandNan)
  //     -> 1.U,
  //     (anyOperandInf)
  //     -> 1.U
  //   )
  // )
}