package soundcode.engine.pattern

import soundcode.domain.*
import soundcode.engine.support.*
import soundcode.engine.support.given_Conversion_Double_Pattern

class RepetitionPatternTest extends SchedulerTestBase {

  test("sound(\"bd sn\").ply(2)") {
    // Usiamo il metodo in catena .ply(2) anziché la funzione vecchia `repeat`
    val pattern = seq(bd, sn).ply(2.0)

    check(pattern).inCycle(0)(
      ExpectedEvent("bd", 0 -> (1\4)),
      ExpectedEvent("bd", (1\4) -> (1\2)),
      ExpectedEvent("sn", (1\2) -> (3\4)),
      ExpectedEvent("sn", (3\4) -> 1)
    )
  }

  test("sound(\"bd sn\").ply(<1 2>)") {
    // Se il parametro cambia, il ply si adatta
    val pattern = seq(bd, sn).ply(alt(1.0, 2.0))

    // Ciclo 0 (param 1)
    check(pattern).inCycle(0)(
      ExpectedEvent("bd", 0 -> (1\2)),
      ExpectedEvent("sn", (1\2) -> 1)
    )

    // Ciclo 1 (param 2)
    check(pattern).inCycle(1)(
      ExpectedEvent("bd", 1 -> (5\4)),
      ExpectedEvent("bd", (5\4) -> (6\4)),
      ExpectedEvent("sn", (6\4) -> (7\4)),
      ExpectedEvent("sn", (7\4) -> 2)
    )
  }
}