import chisel3._
import chisel3.util._
import fpu._

// * FCMP Module
class FCMP extends FPUModule {
  val io = IO(new FComponentIO)
  val operand     = io.in.operand
  val operandIn   = io.in.operandIn
  val instType    = io.in.instType
  val opInfo      = io.in.opInfo
  val operandInfo = io.in.operandInfo
  val roundMode   = io.in.roundMode
  val roundModeIn = io.in.roundModeIn

  val anyOperandInf         = io.in.operandInfo.isInf(0) | io.in.operandInfo.isInf(1)
  val anyOperandNan         = io.in.operandInfo.isNan(0) | io.in.operandInfo.isNan(1)
  val anyOperandSignalling  = io.in.operandInfo.isSignalling(0) | io.in.operandInfo.isSignalling(1)

  // * --------------------
  // * Special Case
  // * --------------------
  val operandEqual    = (operandIn(0) === operandIn(1)) |
                        (operandInfo.isZero(0) & operandInfo.isZero(1))
  val operand0Smaller = (operandIn(0) < operandIn(1)) ^ (operand.sign(0) | operand.sign(1)).asBool

  // * --------------------
  // * Sign Injection
  // * --------------------
  val sign0 = operand.sign(0) & operandInfo.isBoxed(0)
  val sign1 = operand.sign(1) & operandInfo.isBoxed(1)
  val resultSgnjT = Mux(~operandInfo.isBoxed(0),
                     Cat("b1".U,         "b11111111".U,        4194304.U(MantissaLEN.W)),
                     operandIn(0))
  val resultSgnj = MuxCase(0.U, Seq(
    (roundModeIn === 0.U) -> Cat( sign1,        resultSgnjT(30, 0)),
    (roundModeIn === 1.U) -> Cat(~sign1,        resultSgnjT(30, 0)),
    (roundModeIn === 2.U) -> Cat(sign0 ^ sign1, resultSgnjT(30, 0)),
    (roundModeIn === 3.U) -> operandIn(0),
    (roundModeIn === 4.U) -> resultSgnjT,
    (roundModeIn === 5.U) -> resultSgnjT,
    (roundModeIn === 6.U) -> resultSgnjT,
    (roundModeIn === 7.U) -> resultSgnjT,
  ))
  val fflagsSgnj       = 0.U(3.W)
  val sgnjExtensionBit = resultSgnj(31)

  // * --------------------
  // * Min Max
  // * --------------------
  val resultMinMax = Wire(UInt(FLEN.W))
  when (operandInfo.isNan(0) & operandInfo.isNan(1)) {
    resultMinMax := Cat("b0".U, "b11111111".U, 4194304.U(MantissaLEN.W))
  }.elsewhen (operandInfo.isNan(0)) {
    resultMinMax := operandIn(1)
  }.elsewhen (operandInfo.isNan(1)) {
    resultMinMax := operandIn(0)
  }.otherwise {
    resultMinMax := MuxCase(0.U, Seq(
      (roundModeIn === 0.U) -> Mux(operand0Smaller, operandIn(0), operandIn(1)),
      (roundModeIn === 1.U) -> Mux(operand0Smaller, operandIn(1), operandIn(0)),
      (roundModeIn === 2.U) -> Cat("b0".U, "b11111111".U, 4194304.U(MantissaLEN.W)),
      (roundModeIn === 3.U) -> Cat("b0".U, "b11111111".U, 4194304.U(MantissaLEN.W)),
      (roundModeIn === 4.U) -> Cat("b0".U, "b11111111".U, 4194304.U(MantissaLEN.W)),
      (roundModeIn === 5.U) -> Cat("b0".U, "b11111111".U, 4194304.U(MantissaLEN.W)),
      (roundModeIn === 6.U) -> Cat("b0".U, "b11111111".U, 4194304.U(MantissaLEN.W)),
      (roundModeIn === 7.U) -> Cat("b0".U, "b11111111".U, 4194304.U(MantissaLEN.W)),
    ))
  }
  val fflagsMinMax = Cat(anyOperandSignalling.asUInt, 0.U(4.W))
  val minmaxExtensionBit = "b1".U

  // * --------------------
  // * Comparison
  // * --------------------
  val resultCmp = Wire(UInt(FLEN.W))
  val fflagsCmp = Wire(UInt(5.W))
  resultCmp := 0.U
  fflagsCmp := 0.U
  when (anyOperandSignalling) {
    fflagsCmp := Cat(1.U(1.W), 0.U(4.W))
  }.otherwise {
    resultCmp := MuxCase(0.U, Seq(
      (roundModeIn === 0.U) -> Mux(anyOperandNan, 0.U, (operand0Smaller | operandEqual)),
      (roundModeIn === 1.U) -> Mux(anyOperandNan, 0.U, (operand0Smaller & ~operandEqual)),
      (roundModeIn === 2.U) -> Mux(anyOperandNan, 0.U, operandEqual),
      (roundModeIn === 3.U) -> 0.U,
      (roundModeIn === 4.U) -> 0.U,
      (roundModeIn === 5.U) -> 0.U,
      (roundModeIn === 6.U) -> 0.U,
      (roundModeIn === 7.U) -> 0.U,
    ))
    fflagsCmp := Mux1H(Seq(
      (roundModeIn === 0.U) -> Mux(anyOperandNan, Cat(1.U(1.W), 0.U(4.W)), 0.U),
      (roundModeIn === 1.U) -> Mux(anyOperandNan, Cat(1.U(1.W), 0.U(4.W)), 0.U) ,
      (roundModeIn === 2.U) -> 0.U,
      (roundModeIn === 3.U) -> 0.U,
      (roundModeIn === 4.U) -> 0.U,
      (roundModeIn === 5.U) -> 0.U,
      (roundModeIn === 6.U) -> 0.U,
      (roundModeIn === 7.U) -> 0.U,
    ))
  }

  // * --------------------
  // * Classification
  // * --------------------
  val classMask = Wire(UInt(10.W))
  when (operandInfo.isNormal(0)) {
    classMask := Mux(operand.sign(0).asBool, NEGNORM.U, POSNORM.U)
  }.elsewhen (operandInfo.isSubnormal(0)) {
    classMask := Mux(operand.sign(0).asBool, NEGSUBNORM.U, POSSUBNORM.U)
  }.elsewhen (operandInfo.isZero(0)) {
    classMask := Mux(operand.sign(0).asBool, NEGZERO.U, POSZERO.U)
  }.elsewhen (operandInfo.isInf(0)) {
    classMask := Mux(operand.sign(0).asBool, NEGINF.U, POSINF.U)
  }.elsewhen (operandInfo.isNan(0)) {
    classMask := Mux(operandInfo.isSignalling(0), SNAN.U, QNAN.U)
  }.otherwise {
    classMask := QNAN.U
  }
  val fflagsClass       = 0.U(5.W)
  val classExtensionBit = 0.U(1.W)

  // * --------------------
  // * Result Selection
  // * --------------------
  val result = Mux1H(Seq(
    instType.fsgnj    -> resultSgnj,
    instType.fminmax  -> resultMinMax,
    instType.fcmp     -> resultCmp,
    instType.fclass   -> Cat(0.U(22.W), classMask),
  ))
  val fflags = Mux1H(Seq(
    instType.fsgnj    -> fflagsSgnj,
    instType.fminmax  -> fflagsMinMax,
    instType.fcmp     -> fflagsCmp,
    instType.fclass   -> fflagsClass,
  ))
  
  io.out.result := result
  io.out.fflags := fflags

}