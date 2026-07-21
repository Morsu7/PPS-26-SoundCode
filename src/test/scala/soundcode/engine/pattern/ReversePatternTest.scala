package soundcode.engine.pattern

import soundcode.domain.*
import soundcode.engine.support.*

class ReversePatternTest extends SchedulerTestBase {
  test("""sound("bd sn hh").reverse""") {
    val streams = List(rev(seq(bd, sn, hh)))

    // L'inversione specchia il tempo all'interno del ciclo:
    // Il rullante (sn) passa all'inizio [0.0 -> 0.5] e la cassa (bd) alla fine [0.5 -> 1.0]
    assertCycle(streams, 0)(
      ExpEvent("hh", 0 \ 1, 1 \ 3),
      ExpEvent("sn", 1 \ 3, 2 \ 3),
      ExpEvent("bd", 2 \ 3, 1 \ 1)
    )
  }

  test("""sound("[bd, hh] <sn cp> [rim clap]").note("c4 g4").gain("<1 0.8>").reverse""") {
    val baseSound = seq(par(bd, hh), alt(sn, cp), seq(rim, clap))
    val fxNote = seq(c4, g4)
    val fxGain = alt(gain(1.0), gain(0.8))

    val patternWithEffects = ext(baseSound, fxNote, fxGain)
    val streams = List(rev(patternWithEffects))

    assertCycleUnordered(streams, 0)(
      ExpEvent("clap", 0 \ 1, 1 \ 6, List("G", "1.0")),
      ExpEvent("rim", 1 \ 6, 1 \ 3, List("G", "1.0")),
      ExpEvent("sn", 1 \ 3, 2 \ 3, List("C", "1.0")),
      ExpEvent("bd", 2 \ 3, 1 \ 1, List("C", "1.0")),
      ExpEvent("hh", 2 \ 3, 1 \ 1, List("C", "1.0"))
    )

    assertCycleUnordered(streams, 1)(
      ExpEvent("clap", 1 \ 1, 7 \ 6, List("G", "0.8")),
      ExpEvent("rim", 7 \ 6, 4 \ 3, List("G", "0.8")),
      ExpEvent("cp", 4 \ 3, 5 \ 3, List("C", "0.8")),
      ExpEvent("bd", 5 \ 3, 2 \ 1, List("C", "0.8")),
      ExpEvent("hh", 5 \ 3, 2 \ 1, List("C", "0.8"))
    )
  }
}
