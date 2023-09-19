import chisel3._
import chisel3.util._
import fpu._

// * AddendProcess IO
class AddendProcessIn extends FPUIO {
  val mantissaExt = Input(Vec(3, UInt(PrecisionLEN.W)))
  val addendShiftAmount = Input(UInt(AddendShiftAmountLEN.W))
  val effectiveSubstraction = Input(UInt(1.W))
}
class AddendProcessOut extends FPUIO {
  val addendShifted  = Output(UInt((3*PrecisionLEN+4).W))
  val injectCarryIn  = Output(UInt(1.W))
}
class AddendProcessIO extends FPUIO {
  val in  = new AddendProcessIn
  val out = new AddendProcessOut
}

// * AddendProcess Module
class AddendProcess extends FPUModule {
  val io = IO(new AddendProcessIO)

  val addend = ((io.in.mantissaExt(2) << (3*PrecisionLEN+4)) >> io.in.addendShiftAmount)
  val addendAfterShift = addend(4*PrecisionLEN+4-1,PrecisionLEN)
  val addendStickyBits = addend(PrecisionLEN-1,0)
  val stickyBeforeAdd  = (PopCount(addendStickyBits) > 0.U).asUInt

  io.out.addendShifted := Mux(io.in.effectiveSubstraction.asBool,
                              ~addendAfterShift, addendAfterShift)
  io.out.injectCarryIn := io.in.effectiveSubstraction & (~stickyBeforeAdd)

}