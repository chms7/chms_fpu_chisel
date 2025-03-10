import chisel3._

class Switch extends Module {
  val io = IO(new Bundle {
    val in1 = Input(UInt(1.W))
    val in2 = Input(UInt(1.W))
    val out = Output(UInt(1.W))
  })

  io.out := io.in1 ^ io.in2
}