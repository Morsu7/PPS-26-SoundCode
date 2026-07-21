package soundcode.engine.pattern

import soundcode.engine.support.SchedulerTestBase
import soundcode.domain.*
import soundcode.engine.support.*

class OffsetModifierTest extends SchedulerTestBase {


  test("""sound("bd hh").off(1/4)""") {
    val streams = List(seq(bd, hh).off(1 \ 4))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("hh", 1 \ 2, 1 \ 1),
      ExpEvent("hh", 0 \ 1, 1 \ 4),
      ExpEvent("bd", 1 \ 4, 3 \ 4),
      ExpEvent("hh", 3 \ 4, 1 \ 1)
    )
  }

  test("""sound("bd hh").off(1/4, rev)""") {
    val streams = List(seq(bd, hh).off(1 \ 4, rev))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("hh", 1 \ 2, 1 \ 1),
      ExpEvent("bd", 0 \ 1, 1 \ 4),
      ExpEvent("hh", 1 \ 4, 3 \ 4),
      ExpEvent("bd", 3 \ 4, 1 \ 1)
    )
  }

  test("""sound("bd hh").off(1/4, gain(0.5))""") {
    val streams = List(seq(bd, hh).off(1 \ 4, gain(0.5)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("hh", 1 \ 2, 1 \ 1),
      ExpEvent("hh", 0 \ 1, 1 \ 4, List("0.5")),
      ExpEvent("bd", 1 \ 4, 3 \ 4, List("0.5")),
      ExpEvent("hh", 3 \ 4, 1 \ 1, List("0.5"))
    )
  }

  test("""sound("bd hh").off("<1/4 1/2>")""") {
    val offsetPattern = alt(num(0.25), num(0.5))
    val streams = List(seq(bd, hh).off(offsetPattern))


    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("hh", 1 \ 2, 1 \ 1),
      ExpEvent("hh", 0 \ 1, 1 \ 4),
      ExpEvent("bd", 1 \ 4, 3 \ 4),
      ExpEvent("hh", 3 \ 4, 1 \ 1)
    )


    assertCycleUnordered(streams, 1)(
      ExpEvent("bd", 1 \ 1, 3 \ 2),
      ExpEvent("hh", 3 \ 2, 2 \ 1),
      ExpEvent("hh", 1 \ 1, 3 \ 2),
      ExpEvent("bd", 3 \ 2, 2 \ 1)
    )
  }
}