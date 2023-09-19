import chisel3._
import chisel3.util._
import fpu._
import scala.language.experimental

// * Round IO
class RoundIn extends FPUIO {
  val preRoundAbs           = Input(UInt((ExponentLEN+MantissaLEN).W))
  val preRoundSign          = Input(UInt(1.W))
  val roundStickyBits       = Input(UInt(2.W))
  val roundModeIn           = Input(UInt(3.W))
  val effectiveSubstraction = Input(UInt(1.W))
}
class RoundOut extends FPUIO {
  val roundedAbs  = Output(UInt((ExponentLEN+MantissaLEN).W))
  val roundedSign = Output(UInt(1.W))
  val resultZero  = Output(UInt(1.W))
}
class RoundIO extends FPUIO {
  val in = new RoundIn
  val out = new RoundOut
}

// * Round BlackBox
class fpnew_rounding extends BlackBox with FPUParameter {
  val io = IO(new Bundle {
    val abs_value_i             = Input(UInt((ExponentLEN+MantissaLEN).W))
    val sign_i                  = Input(UInt(1.W))
    val round_sticky_bits_i     = Input(UInt(2.W))
    val rnd_mode_i              = Input(UInt(3.W))
    val effective_subtraction_i = Input(UInt(1.W))
    val abs_rounded_o           = Output(UInt((ExponentLEN+MantissaLEN).W))
    val sign_o                  = Output(UInt(1.W))
    val exact_zero_o            = Output(UInt(1.W))
  })
}

// * Round Module
class Round extends FPUModule {
  val io = IO(new RoundIO)

  val fpnew_rounding = Module(new fpnew_rounding)
  fpnew_rounding.io.abs_value_i             := io.in.preRoundAbs          
  fpnew_rounding.io.sign_i                  := io.in.preRoundSign         
  fpnew_rounding.io.round_sticky_bits_i     := io.in.roundStickyBits      
  fpnew_rounding.io.rnd_mode_i              := io.in.roundModeIn          
  fpnew_rounding.io.effective_subtraction_i := io.in.effectiveSubstraction
  io.out.roundedAbs  := fpnew_rounding.io.abs_rounded_o
  io.out.roundedSign := fpnew_rounding.io.sign_o       
  io.out.resultZero  := fpnew_rounding.io.exact_zero_o 

}
