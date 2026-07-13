package soundcode.engine.pattern

import soundcode.domain.*
import soundcode.engine.support.*

class RepetitionPatternTest extends SchedulerTestBase {

  test("sound(\"bd sn\").ply(2)") {
    // s("bd sn").repeat(2) -> bd da 0 a 0.25 e da 0.25 a 0.5; sn da 0.5 a 0.75 e da 0.75 a 1.0
    val streams = List(repeat(2.0, seq(bd, sn)))

    assertCycle(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 4),
      ExpEvent("bd", 1 \ 4, 1 \ 2),
      ExpEvent("sn", 1 \ 2, 3 \ 4),
      ExpEvent("sn", 3 \ 4, 1 \ 1)
    )
  }

  test(" sound(\"bd sn\").ply(<1 2>)") {
    // Se il parametro cambia, il ply si adatta
    // Nel ciclo 0 (param 1): bd sn
    // Nel ciclo 1 (param 2): bd bd sn sn
    val streams = List(repeat(alt(num(1), num(2)), seq(bd, sn)))

    // Ciclo 0 (param 1)
    assertCycle(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("sn", 1 \ 2, 1 \ 1)
    )

    // Ciclo 1 (param 2)
    assertCycle(streams, 1)(
      ExpEvent("bd", 1 \ 1, 5 \ 4),
      ExpEvent("bd", 5 \ 4, 6 \ 4),
      ExpEvent("sn", 6 \ 4, 7 \ 4),
      ExpEvent("sn", 7 \ 4, 8 \ 4)
    )
  }
}
