package soundcode.engine.pattern

import soundcode.engine.support.*

class CorePatternsTest extends SchedulerTestBase {

  test("""sound("bd hh sn hh")""") {
    val streams = List(seq(bd, hh, sn, hh))
    assertCycle(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 4),
      ExpEvent("hh", 1 \ 4, 1 \ 2),
      ExpEvent("sn", 1 \ 2, 3 \ 4),
      ExpEvent("hh", 3 \ 4, 1 \ 1)
    )
  }

  test("""sound("<bd bd hh bd rim bd hh bd>")""") {
    val pattern = alt(bd, bd, hh, bd, rim, bd, hh, bd)
    val streams = List(pattern)
    val samples = Vector("bd", "bd", "hh", "bd", "rim", "bd", "hh", "bd")

    for (i <- 0 until 8) {
      val result = resolve(streams, i)
      result should have size 1
      result.head shouldBe ExpEvent(samples(i), i \ 1, (i + 1) \ 1)
    }
  }

  test("""sound("<bd [hh sn]> cp")""") {
    val streams = List(seq(alt(bd, seq(hh, sn)), cp))

    assertCycle(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("cp", 1 \ 2, 1 \ 1)
    )

    assertCycle(streams, 1)(
      ExpEvent("hh", 1 \ 1, 5 \ 4),
      ExpEvent("sn", 5 \ 4, 3 \ 2),
      ExpEvent("cp", 3 \ 2, 2 \ 1)
    )
  }

  test("""sound("bd hh sn hh [hh, [sn <bd hh>]]")""") {
    val streams = List(seq(bd, hh, sn, hh, par(hh, seq(sn, alt(bd, hh)))))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 5),
      ExpEvent("hh", 1 \ 5, 2 \ 5),
      ExpEvent("sn", 2 \ 5, 3 \ 5),
      ExpEvent("hh", 3 \ 5, 4 \ 5),
      ExpEvent("hh", 4 \ 5, 1 \ 1),
      ExpEvent("sn", 4 \ 5, 9 \ 10),
      ExpEvent("bd", 9 \ 10, 1 \ 1)
    )

    assertCycleUnordered(streams, 1)(
      ExpEvent("bd", 1 \ 1, 6 \ 5),
      ExpEvent("hh", 6 \ 5, 7 \ 5),
      ExpEvent("sn", 7 \ 5, 8 \ 5),
      ExpEvent("hh", 8 \ 5, 9 \ 5),
      ExpEvent("hh", 9 \ 5, 2 \ 1),
      ExpEvent("sn", 9 \ 5, 19 \ 10),
      ExpEvent("hh", 19 \ 10, 2 \ 1)
    )
  }

}
