// import chisel3._
// import chisel3.util._
// import fpu._

// // * Normalizer BlackBox
// class normalizer extends BlackBox with FPUParameter {
//   val io = IO(new Bundle {
//     val effective_subtraction = Input(UInt(1.W))
//     val exponent_product      = Input(SInt(ExponentExtLEN.W))
//     val exponent_difference   = Input(SInt(ExponentExtLEN.W))
//     val tentative_exponent    = Input(SInt(ExponentExtLEN.W))
//     val addend_shamt          = Input(UInt(AddendShiftAmountLEN.W))  
//     val sticky_before_add     = Input(UInt(1.W))
//     val sum                   = Input(UInt((3*PrecisionLEN+4).W))
//     val final_sign            = Input(UInt(1.W))
//     val rnd_mode              = Input(UInt(3.W))
//     val regular_result        = Output(UInt(FLEN.W))
//     val regular_status        = Output(UInt(5.W))
//   })
// }

// // * Normalizer Module
// class Normalizer extends FPUModule {
//   val io = IO(new NormalizeIO)

//   val normalizer = Module(new normalizer)

//   normalizer.io.effective_subtraction := io.in.effectiveSubstraction
//   normalizer.io.exponent_product      := io.in.exponentProduct      
//   normalizer.io.exponent_difference   := io.in.exponentDifference   
//   normalizer.io.tentative_exponent    := io.in.exponentTentative    
//   normalizer.io.addend_shamt          := io.in.addendShiftAmount    
//   normalizer.io.sticky_before_add     := io.in.stickyBeforeAdd      
//   normalizer.io.sum                   := io.in.adderSum             
//   normalizer.io.final_sign            := io.in.signFinal            
//   normalizer.io.rnd_mode              := io.in.roundModeIn
//   io.out.resultRegular    := normalizer.io.regular_result
//   io.out.fflagsRegular.NV := normalizer.io.regular_status(4)
//   io.out.fflagsRegular.DZ := normalizer.io.regular_status(3)
//   io.out.fflagsRegular.OF := normalizer.io.regular_status(2)
//   io.out.fflagsRegular.UF := normalizer.io.regular_status(1)
//   io.out.fflagsRegular.NX := normalizer.io.regular_status(1)

// }