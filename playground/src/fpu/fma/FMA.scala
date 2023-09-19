import chisel3._
import chisel3.util._
import fpu._

// * FMA Module
class FMA extends FPUModule {
  val io = IO(new FComponentIO)
  val operand     = io.in.operand
  // val operandIn   = io.in.operandIn
  // val opInfo      = io.in.opInfo
  val operandInfo = io.in.operandInfo
  val roundMode   = io.in.roundMode
  // val roundModeIn = io.in.roundModeIn

  // * --------------------
  // * Special Case Process
  // * --------------------
  val SpecialProcess = Module(new SpecialProcess)
  // SpecialProcess In
  SpecialProcess.io.in.operand      := operand
  SpecialProcess.io.in.operandInfo  := operandInfo
  // Special Result
  val resultSpecial   = SpecialProcess.io.out.resultSpecial
  val fflagsSpecial   = SpecialProcess.io.out.fflagsSpecial
  val resultIsSpecial = SpecialProcess.io.out.resultIsSpecial

  // * --------------------
  // * MulAdder
  // * --------------------
  val MulAdder = Module(new MulAdder)
  // MulAdder In
  MulAdder.io.in.operand      := operand
  MulAdder.io.in.operandInfo  := operandInfo
  // MulAdder Out
  val exponentProduct       = MulAdder.io.out.exponentProduct
  val exponentDifference    = MulAdder.io.out.exponentDifference
  val exponentTentative     = MulAdder.io.out.exponentTentative
  val addendShiftAmount     = MulAdder.io.out.addendShiftAmount
  val stickyBeforeAdd       = MulAdder.io.out.stickyBeforeAdd
  val effectiveSubstraction = MulAdder.io.out.effectiveSubstraction
  val adderSum              = MulAdder.io.out.adderSum
  val signFinal             = MulAdder.io.out.signFinal
  
  // * --------------------
  // * Normalization
  // * --------------------
  val Normalize = Module(new Normalize)
  // Normalize In
  Normalize.io.in.effectiveSubstraction := effectiveSubstraction
  Normalize.io.in.exponentProduct       := exponentProduct
  Normalize.io.in.exponentDifference    := exponentDifference
  Normalize.io.in.exponentTentative     := exponentTentative
  Normalize.io.in.addendShiftAmount     := addendShiftAmount
  Normalize.io.in.stickyBeforeAdd       := stickyBeforeAdd
  Normalize.io.in.adderSum              := adderSum
  Normalize.io.in.signFinal             := signFinal
  Normalize.io.in.roundMode             := roundMode
  // Normalize Out
  val resultRegular = Normalize.io.out.resultRegular
  val fflagsRegular = Normalize.io.out.fflagsRegular
  
  // * --------------------
  // * ResultSelect
  // * --------------------
  io.out.result := Mux(resultIsSpecial.asBool, resultSpecial, resultRegular)
  io.out.fflags := Mux(resultIsSpecial.asBool, fflagsSpecial.asUInt, fflagsRegular.asUInt)

}