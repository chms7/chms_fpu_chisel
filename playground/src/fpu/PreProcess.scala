import chisel3._
import chisel3.util._
import fpu._

// * PreProcess IO
class PreProcessIn extends FPUIO {
  // . PreProcess In
  val operand         = Input(Vec(3, UInt(FLEN.W)))
  val operandIsBoxed  = Input(Vec(3, Bool()))
  val opInfo          = Input(UInt(OPInfoLEN.W))
  val roundMode       = Input(UInt(3.W))
}
class PreProcessOut extends FPUIO {
  // Operand Sign & Exp & Man
  val operandFMA  = new Bundle{
    val sign      = Output(Vec(3, UInt(SignLEN.W    )))
    val exponent  = Output(Vec(3, UInt(ExponentLEN.W)))
    val mantissa  = Output(Vec(3, UInt(MantissaLEN.W)))
  }
  val operandFCMP = new Bundle{
    val sign      = Output(Vec(3, UInt(SignLEN.W    )))
    val exponent  = Output(Vec(3, UInt(ExponentLEN.W)))
    val mantissa  = Output(Vec(3, UInt(MantissaLEN.W)))
  }
  // Decode Info
  val instType = new Bundle{
    val fmadd   = Output(Bool())
    val fmsub   = Output(Bool())
    val fnmsub  = Output(Bool())
    val fnmadd  = Output(Bool())
    val fadd    = Output(Bool())
    val fsub    = Output(Bool())
    val fmul    = Output(Bool())
    val fminmax = Output(Bool())
    val fmvxw   = Output(Bool())
    val fmvwx   = Output(Bool())
    val fsgnj   = Output(Bool())
    val fcmp    = Output(Bool())
    val fclass  = Output(Bool())
  }
  val operandInfo = new Bundle{
    val isBoxed      = Output(Vec(3, Bool()))
    val isNormal     = Output(Vec(3, Bool()))
    val isSubnormal  = Output(Vec(3, Bool()))
    val isZero       = Output(Vec(3, Bool()))
    val isInf        = Output(Vec(3, Bool()))
    val isNan        = Output(Vec(3, Bool()))
    val isSignalling = Output(Vec(3, Bool()))
    val isQuiet      = Output(Vec(3, Bool()))
  }
  // Round Mode
  val roundMode = new Bundle{
    val RNE = Output(Bool())
    val RTZ = Output(Bool())
    val RDN = Output(Bool())
    val RUP = Output(Bool())
    val RMM = Output(Bool())
  }
}
class PreProcessIO extends FPUIO {
  val in  = new PreProcessIn
  val out = new PreProcessOut
}

// * PreProcess Module
class PreProcess extends FPUModule {
  val io = IO(new PreProcessIO)

  // * Input Operands
  // val signIn      = Mux(io.in.opInfo(9) === 1.U,
  //                    VecInit(io.in.operand(0)(SignBitStart),
  //                            io.in.operand(1)(SignBitStart),
  //                            io.in.operand(2)(SignBitStart)),
  //                    VecInit(io.in.operand(1)(SignBitStart),
  //                            io.in.operand(2)(SignBitStart),
  //                            io.in.operand(0)(SignBitStart)))
  // val exponentIn  = Mux(io.in.opInfo(9) === 1.U,
  //                    VecInit(io.in.operand(0)(ExponentBitStart, ExponentBitEnd),
  //                            io.in.operand(1)(ExponentBitStart, ExponentBitEnd),
  //                            io.in.operand(2)(ExponentBitStart, ExponentBitEnd)),
  //                    VecInit(io.in.operand(1)(ExponentBitStart, ExponentBitEnd),
  //                            io.in.operand(2)(ExponentBitStart, ExponentBitEnd),
  //                            io.in.operand(0)(ExponentBitStart, ExponentBitEnd)))
  // val mantissaIn  = Mux(io.in.opInfo(9) === 1.U,
  //                    VecInit(io.in.operand(0)(MantissaBitStart, MantissaBitEnd),
  //                            io.in.operand(1)(MantissaBitStart, MantissaBitEnd),
  //                            io.in.operand(2)(MantissaBitStart, MantissaBitEnd)),
  //                    VecInit(io.in.operand(1)(MantissaBitStart, MantissaBitEnd),
  //                            io.in.operand(2)(MantissaBitStart, MantissaBitEnd),
  //                            io.in.operand(0)(MantissaBitStart, MantissaBitEnd)))
  val signIn      = VecInit(io.in.operand(0)(SignBitStart),
                            io.in.operand(1)(SignBitStart),
                            io.in.operand(2)(SignBitStart))
  val exponentIn  = VecInit(io.in.operand(0)(ExponentBitStart, ExponentBitEnd),
                            io.in.operand(1)(ExponentBitStart, ExponentBitEnd),
                            io.in.operand(2)(ExponentBitStart, ExponentBitEnd))
  val mantissaIn  = VecInit(io.in.operand(0)(MantissaBitStart, MantissaBitEnd),
                            io.in.operand(1)(MantissaBitStart, MantissaBitEnd),
                            io.in.operand(2)(MantissaBitStart, MantissaBitEnd))

  // * Instruction Info
  val opInfo = io.in.opInfo
  val instInfo = io.in.opInfo(10, 4)
  io.out.instType.fmadd   := opInfo(4 ).asBool
  io.out.instType.fmsub   := opInfo(5 ).asBool
  io.out.instType.fnmsub  := opInfo(6 ).asBool
  io.out.instType.fnmadd  := opInfo(7 ).asBool
  io.out.instType.fadd    := opInfo(8 ).asBool
  io.out.instType.fsub    := opInfo(9 ).asBool
  io.out.instType.fmul    := opInfo(10).asBool
  io.out.instType.fminmax := opInfo(13).asBool
  io.out.instType.fmvxw   := opInfo(18).asBool
  io.out.instType.fmvwx   := opInfo(19).asBool
  io.out.instType.fsgnj   := opInfo(20).asBool
  io.out.instType.fcmp    := opInfo(21).asBool
  io.out.instType.fclass  := opInfo(22).asBool

  // * Round Mode
  io.out.roundMode.RNE := io.in.roundMode === 0.U(3.W)
  io.out.roundMode.RTZ := io.in.roundMode === 1.U(3.W)
  io.out.roundMode.RDN := io.in.roundMode === 2.U(3.W)
  io.out.roundMode.RUP := io.in.roundMode === 3.U(3.W)
  io.out.roundMode.RMM := io.in.roundMode === 4.U(3.W)

  // * Operand Info
  io.out.operandInfo.isBoxed       := io.in.operandIsBoxed
  io.out.operandInfo.isNormal      := VecInit(  // exp not all 0, not all1
    (io.out.operandInfo.isBoxed(0)) & (exponentIn(0) =/= 0.U(ExponentLEN.W))   & (exponentIn(0) =/= 255.U(MantissaLEN.W)),
    (io.out.operandInfo.isBoxed(1)) & (exponentIn(1) =/= 0.U(ExponentLEN.W))   & (exponentIn(1) =/= 255.U(MantissaLEN.W)),
    (io.out.operandInfo.isBoxed(2)) & (exponentIn(2) =/= 0.U(ExponentLEN.W))   & (exponentIn(2) =/= 255.U(MantissaLEN.W)))
  io.out.operandInfo.isSubnormal   := VecInit(  // exp all 0, man not all 0
    (io.out.operandInfo.isBoxed(0)) & (exponentIn(0) === 0.U(ExponentLEN.W))   & (!io.out.operandInfo.isZero(0)),
    (io.out.operandInfo.isBoxed(1)) & (exponentIn(1) === 0.U(ExponentLEN.W))   & (!io.out.operandInfo.isZero(1)),
    (io.out.operandInfo.isBoxed(2)) & (exponentIn(2) === 0.U(ExponentLEN.W))   & (!io.out.operandInfo.isZero(2)))
  io.out.operandInfo.isZero        := VecInit(  // exp all 0, man all 0
    (io.out.operandInfo.isBoxed(0)) & (exponentIn(0) === 0.U(ExponentLEN.W))   & (mantissaIn(0) === 0.U(MantissaLEN.W)),
    (io.out.operandInfo.isBoxed(1)) & (exponentIn(1) === 0.U(ExponentLEN.W))   & (mantissaIn(1) === 0.U(MantissaLEN.W)),
    (io.out.operandInfo.isBoxed(2)) & (exponentIn(2) === 0.U(ExponentLEN.W))   & (mantissaIn(2) === 0.U(MantissaLEN.W)))
    // (io.out.operandInfo.isBoxed(2)) & (exponentIn(2) === 0.U(ExponentLEN.W))   & (mantissaIn(2) === 0.U(MantissaLEN.W)) | (io.in.opInfo(10) === 1.U(1.W)))
  io.out.operandInfo.isInf         := VecInit(  // exp all 1, man all 0
    (io.out.operandInfo.isBoxed(0)) & (exponentIn(0) === 255.U(ExponentLEN.W)) & (mantissaIn(0) === 0.U(MantissaLEN.W)),
    (io.out.operandInfo.isBoxed(1)) & (exponentIn(1) === 255.U(ExponentLEN.W)) & (mantissaIn(1) === 0.U(MantissaLEN.W)),
    (io.out.operandInfo.isBoxed(2)) & (exponentIn(2) === 255.U(ExponentLEN.W)) & (mantissaIn(2) === 0.U(MantissaLEN.W)))
  io.out.operandInfo.isNan         := VecInit(  // exp all 1, man not all 0
    (io.out.operandInfo.isBoxed(0)) & (exponentIn(0) === 255.U(ExponentLEN.W)) & (mantissaIn(0) =/= 0.U(MantissaLEN.W)),
    (io.out.operandInfo.isBoxed(1)) & (exponentIn(1) === 255.U(ExponentLEN.W)) & (mantissaIn(1) =/= 0.U(MantissaLEN.W)),
    (io.out.operandInfo.isBoxed(2)) & (exponentIn(2) === 255.U(ExponentLEN.W)) & (mantissaIn(2) =/= 0.U(MantissaLEN.W)))
  io.out.operandInfo.isSignalling  := VecInit(  // is nan, man msb == 1
    (io.out.operandInfo.isBoxed(0)) & (io.out.operandInfo.isNan(0)) & (mantissaIn(0)(MantissaLEN-1) === 0.U(1.W)),
    (io.out.operandInfo.isBoxed(1)) & (io.out.operandInfo.isNan(1)) & (mantissaIn(1)(MantissaLEN-1) === 0.U(1.W)),
    (io.out.operandInfo.isBoxed(2)) & (io.out.operandInfo.isNan(2)) & (mantissaIn(2)(MantissaLEN-1) === 0.U(1.W)))
  io.out.operandInfo.isQuiet       := VecInit(
    (io.out.operandInfo.isNan(0))   & (io.out.operandInfo.isSignalling(0)),
    (io.out.operandInfo.isNan(1))   & (io.out.operandInfo.isSignalling(1)),
    (io.out.operandInfo.isNan(2))   & (io.out.operandInfo.isSignalling(2)))

  // * Process operands according to operation type
  // | FMADD    | FMADD:  none
  // | FMSUB    | FMSUB:  Invert sign of operand 2
  // | FNMSUB   | FNMSUB: Invert sign of operand 0
  // | FNMADD   | FNMADD: Invert sign of operands 0 and 2
  // | FADD     | FADD:   Set operand 0 to +1.0
  // | FSUB     | FSUB:   Set operand 0 to +1.0, invert sign of operand 2
  // | FMUL     | FMUL:   Set operand 2 to +0.0 or -0.0 depending on the rounding mode
  // | *others* | *invalid*
  // | FCMP     | none
  
  // * Sign
  io.out.operandFCMP.sign := VecInit( signIn(0),                signIn(1),            signIn(2)             )
  io.out.operandFMA.sign  := Mux1H(Seq(  // ? default
    instInfo(0)    ->  VecInit( signIn(0),                      signIn(1),            signIn(2)             ),  // FMADD
    instInfo(1)    ->  VecInit( signIn(0),                      signIn(1),           ~signIn(2)             ),  // FMSUB
    instInfo(2)    ->  VecInit(~signIn(0),                      signIn(1),            signIn(2)             ),  // FNMSUB
    instInfo(3)    ->  VecInit(~signIn(0),                      signIn(1),           ~signIn(2)             ),  // FNMADD
    instInfo(4)    ->  VecInit(0.U(SignLEN.W),                  signIn(1),            signIn(2)             ),  // FADD
    instInfo(5)    ->  VecInit(0.U(SignLEN.W),                 ~signIn(1),            signIn(2)             ),  // FSUB
    instInfo(6)    ->  VecInit(signIn(0),                       signIn(1),            Mux(io.out.roundMode.RDN, // FMUL
                                                                                        0.U(SignLEN.W), 1.U(SignLEN.W))),
  ))
  
  // * Exponent
  io.out.operandFCMP.exponent := VecInit(exponentIn(0),         exponentIn(1),        exponentIn(2)          )
  io.out.operandFMA.exponent  := Mux1H(Seq(
    instInfo(0)    ->  VecInit(exponentIn(0),                   exponentIn(1),        exponentIn(2)          ),  // FMADD
    instInfo(1)    ->  VecInit(exponentIn(0),                   exponentIn(1),        exponentIn(2)          ),  // FMSUB
    instInfo(2)    ->  VecInit(exponentIn(0),                   exponentIn(1),        exponentIn(2)          ),  // FNMSUB
    instInfo(3)    ->  VecInit(exponentIn(0),                   exponentIn(1),        exponentIn(2)          ),  // FNMADD
    instInfo(4)    ->  VecInit(ExponentBias.U(ExponentLEN.W),   exponentIn(1),        exponentIn(2)          ),  // FADD
    instInfo(5)    ->  VecInit(ExponentBias.U(ExponentLEN.W),   exponentIn(1),        exponentIn(2)          ),  // FSUB
    instInfo(6)    ->  VecInit(exponentIn(0),                   exponentIn(1),        0x00.U(ExponentLEN.W)  ),  // FMUL
  ))

  // * Mantissa
  io.out.operandFCMP.mantissa := VecInit(mantissaIn(0),         mantissaIn(1),        mantissaIn(2)          )
  io.out.operandFMA.mantissa  := Mux1H(Seq(
    instInfo(0)    ->  VecInit(mantissaIn(0),                   mantissaIn(1),        mantissaIn(2)          ),  // FMADD
    instInfo(1)    ->  VecInit(mantissaIn(0),                   mantissaIn(1),        mantissaIn(2)          ),  // FMSUB
    instInfo(2)    ->  VecInit(mantissaIn(0),                   mantissaIn(1),        mantissaIn(2)          ),  // FNMSUB
    instInfo(3)    ->  VecInit(mantissaIn(0),                   mantissaIn(1),        mantissaIn(2)          ),  // FNMADD
    instInfo(4)    ->  VecInit(0.U(MantissaLEN.W),              mantissaIn(1),        mantissaIn(2)          ),  // FADD
    instInfo(5)    ->  VecInit(0.U(MantissaLEN.W),              mantissaIn(1),        mantissaIn(2)          ),  // FSUB
    instInfo(6)    ->  VecInit(mantissaIn(0),                   mantissaIn(1),        0.U(MantissaLEN.W)     ),  // FMUL
  ))
  // ? MixVec(sign, exp, man)

}