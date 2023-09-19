import chisel3._
import chisel3.util._
import fpu._

class AdderIn extends FPUIO {
  val operand = new Bundle{
    val sign      = Input(Vec(3, UInt(SignLEN.W    )))
    val exponent  = Input(Vec(3, UInt(ExponentLEN.W)))
    val mantissa  = Input(Vec(3, UInt(MantissaLEN.W)))
  }
  val mantissaProduct = Input(UInt((3*PrecisionLEN+4).W))
  val addendShifted   = Input(UInt((3*PrecisionLEN+4).W))
  val injectCarryIn   = Input(UInt(1.W))
  val effectiveSubstraction = Input(UInt(1.W))
}
class AdderOut extends FPUIO {
  val sum       = Output(UInt((3*PrecisionLEN+4).W))
  val signFinal = Output(UInt(1.W))
}
class AdderIO extends FPUIO {
  val in = new AdderIn
  val out = new AdderOut
}

class Adder extends FPUModule {
  val io = IO(new AdderIO)

  val signTentative = io.in.operand.sign(0) ^ io.in.operand.sign(1)
  val sumRaw   = (io.in.mantissaProduct +& io.in.addendShifted +& io.in.injectCarryIn)
  // val sumRaw: UInt((3*PrecisionLEN+5).W)   = (io.in.mantissaProduct + io.in.addendShifted + io.in.injectCarryIn)
  val sumCarry = sumRaw(3*PrecisionLEN+4)

  io.out.sum       := Mux(io.in.effectiveSubstraction.asBool & (~sumCarry),
                          -sumRaw,
                          sumRaw)
  io.out.signFinal := Mux(io.in.effectiveSubstraction.asBool & (sumCarry === signTentative), 
                          1.U(1.W),
                          Mux(io.in.effectiveSubstraction.asBool, 
                              0.U,
                              signTentative))

}