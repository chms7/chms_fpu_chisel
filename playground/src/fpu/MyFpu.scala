import chisel3._
import chisel3.util._
import fpu._

// * MyFpu IO
class MyFpuIn extends FPUIO {
  val operandIn       = Input(Vec(3, UInt(FLEN.W)))
  val opInfo          = Input(UInt(OPInfoLEN.W))
  val roundModeIn     = Input(UInt(3.W))
}
class MyFpuOut extends FPUIO {
  val result = Output(UInt(FLEN.W))
  val fflags = Output(UInt(5.W))
}
class MyFpuIO extends FPUIO {
  val in  = Flipped(Decoupled((new MyFpuIn)))
  val out = Decoupled(new MyFpuOut)
}

// * MyFpu Module
class Fpu6 extends FPUModule {
  val io = IO(new MyFpuIO)

  // * --------------------
  // * PreProcess
  // * --------------------
  val PreProcess = Module(new PreProcess)
  // PreProcess In
  PreProcess.io.in.operand        := io.in.bits.operandIn
  PreProcess.io.in.operandIsBoxed := VecInit(true.B, true.B, true.B)
  PreProcess.io.in.opInfo         := io.in.bits.opInfo
  PreProcess.io.in.roundMode      := io.in.bits.roundModeIn
  // PreProcess Out
  val operandFMA  = PreProcess.io.out.operandFMA
  val operandFCMP = PreProcess.io.out.operandFCMP
  val instType    = PreProcess.io.out.instType
  val operandInfo = PreProcess.io.out.operandInfo
  val roundMode   = PreProcess.io.out.roundMode

  // * --------------------
  // * FMA
  // * --------------------
  val FMA = Module(new FMA)
  FMA.io.in.operandIn   := io.in.bits.operandIn
  FMA.io.in.operand     := operandFMA
  FMA.io.in.instType    := instType
  FMA.io.in.opInfo      := io.in.bits.opInfo
  FMA.io.in.operandInfo := operandInfo
  FMA.io.in.roundModeIn := io.in.bits.roundModeIn
  FMA.io.in.roundMode   := roundMode
  val resultFMA = FMA.io.out.result
  val fflagsFMA = FMA.io.out.fflags

  // * --------------------
  // * FCMP
  // * --------------------
  val FCMP = Module(new FCMP)
  FCMP.io.in.operandIn   := io.in.bits.operandIn
  FCMP.io.in.operand     := operandFCMP
  FCMP.io.in.instType    := instType
  FCMP.io.in.opInfo      := io.in.bits.opInfo
  FCMP.io.in.operandInfo := operandInfo
  FCMP.io.in.roundModeIn := io.in.bits.roundModeIn
  FCMP.io.in.roundMode   := roundMode
  val resultFCMP = FCMP.io.out.result
  val fflagsFCMP = FCMP.io.out.fflags

  // * --------------------
  // * Result Selection
  // * --------------------
  io.out.bits.result := Mux1H(Seq(
    io.in.bits.opInfo(4)  -> resultFMA,   // fmadd
    io.in.bits.opInfo(5)  -> resultFMA,   // fmsub
    io.in.bits.opInfo(6)  -> resultFMA,   // fnmsub
    io.in.bits.opInfo(7)  -> resultFMA,   // fnmadd
    io.in.bits.opInfo(8)  -> resultFMA,   // fadd
    io.in.bits.opInfo(9)  -> resultFMA,   // fsub
    io.in.bits.opInfo(10) -> resultFMA,   // fmul
    io.in.bits.opInfo(20) -> resultFCMP,  // fsgnj
    io.in.bits.opInfo(13) -> resultFCMP,  // fminmax
    io.in.bits.opInfo(21) -> resultFCMP,  // fcmp
    io.in.bits.opInfo(22) -> resultFCMP,  // fclass
  ))
  io.out.bits.fflags := Mux1H(Seq(
    io.in.bits.opInfo(4)  -> fflagsFMA,   // fmadd
    io.in.bits.opInfo(5)  -> fflagsFMA,   // fmsub
    io.in.bits.opInfo(6)  -> fflagsFMA,   // fnmsub
    io.in.bits.opInfo(7)  -> fflagsFMA,   // fnmadd
    io.in.bits.opInfo(8)  -> fflagsFMA,   // fadd
    io.in.bits.opInfo(9)  -> fflagsFMA,   // fsub
    io.in.bits.opInfo(10) -> fflagsFMA,   // fmul
    io.in.bits.opInfo(20) -> fflagsFCMP,  // fsgnj
    io.in.bits.opInfo(13) -> fflagsFCMP,  // fminmax
    io.in.bits.opInfo(21) -> fflagsFCMP,  // fcmp
    io.in.bits.opInfo(22) -> fflagsFCMP,  // fclass
  ))

  io.in.ready  := io.out.valid
  io.out.valid := io.in.valid
}