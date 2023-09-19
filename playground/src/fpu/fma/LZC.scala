import chisel3._
import chisel3.util._
import fpu._

// * LZC IO
class LZCIn extends FPUIO {
  val sumLower = Input(UInt(LowerSumWidth.W))
}
class LZCOut extends FPUIO {
  val leadingZeroCount = Output(UInt(LZCResultWidth.W))
  val lzcZeroes        = Output(UInt(1.W))
}
class LZCIO extends FPUIO {
  val in = new LZCIn
  val out = new LZCOut
}

// * LZC BlackBox
class lzc extends BlackBox with FPUParameter {
  val io = IO(new Bundle {
    val in_i = Input(UInt(LowerSumWidth.W))
    val cnt_o = Output(UInt(LZCResultWidth.W))
    val empty_o = Output(UInt(1.W))
  })
}

// * LZC Module
class LZC extends FPUModule {
  val io = IO(new LZCIO)

  val lzc = Module(new lzc)
  lzc.io.in_i             := io.in.sumLower
  io.out.leadingZeroCount := lzc.io.cnt_o  
  io.out.lzcZeroes        := lzc.io.empty_o

}
