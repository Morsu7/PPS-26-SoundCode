package soundcode.engine.pattern

import soundcode.domain.*
import soundcode.engine.support.*

class ReversePatternTest extends SchedulerTestBase {

  test("""sound("bd sn hh").reverse""") {
    val pattern = seq(bd, sn, hh).reverse

    // L'inversione specchia il tempo all'interno del ciclo:
    // Il rullante (sn) passa all'inizio [0.0 -> 0.5] e la cassa (bd) alla fine [0.5 -> 1.0]
    check(pattern).inCycle(0)(
      ExpectedEvent("hh", 0 -> (1\3)),
      ExpectedEvent("sn", (1\3) -> (2\3)),
      ExpectedEvent("bd", (2\3) -> 1)
    )
  }

  test("""sound("[bd, hh] <sn cp> [rim clap]").note("c4 g4").gain("<1 0.8>").reverse""") {
    val baseSound = seq(par(bd, hh), alt(sn, cp), seq(rim, clap))
    val fxNote = seq(c4, g4)
    val fxGain = alt(gain(1.0), gain(0.8))

    val patternWithEffects = ext(baseSound, fxNote, fxGain)
    val pattern = patternWithEffects.reverse

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("clap", 0 -> (1\6), "G4", "1.0"),
      ExpectedEvent("rim", (1\6) -> (1\3), "G4", "1.0"),
      ExpectedEvent("sn", (1\3) -> (2\3), "C4", "1.0"),
      ExpectedEvent("bd", (2\3) -> 1, "C4", "1.0"),
      ExpectedEvent("hh", (2\3) -> 1, "C4", "1.0")
    )

    check(pattern).inCycleUnordered(1)(
      ExpectedEvent("clap", 1 -> (7\6), "G4", "0.8"),
      ExpectedEvent("rim", (7\6) -> (4\3), "G4", "0.8"),
      ExpectedEvent("cp", (4\3) -> (5\3), "C4", "0.8"),
      ExpectedEvent("bd", (5\3) -> 2, "C4", "0.8"),
      ExpectedEvent("hh", (5\3) -> 2, "C4", "0.8")
    )
  }
}