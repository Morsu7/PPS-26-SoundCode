package soundcode.engine.pattern

import soundcode.engine.support.*

class CorePatternsTest extends SchedulerTestBase {

  test("""sound("bd hh sn hh")""") {
    val pattern = seq(bd, hh, sn, hh)

    check(pattern).inCycle(0)(
      ExpectedEvent("bd", 0 -> (1\4)),
      ExpectedEvent("hh", (1\4) -> (1\2)),
      ExpectedEvent("sn", (1\2) -> (3\4)),
      ExpectedEvent("hh", (3\4) -> 1)
    )
  }

  test("""note("<e4 g4, f4 b4>")""") {
    val pattern = par(alt(e4, g4), alt(f4, b4))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("E4", 0 -> 1),
      ExpectedEvent("F4", 0 -> 1)
    )

    check(pattern).inCycleUnordered(1)(
      ExpectedEvent("G4", 1 -> 2),
      ExpectedEvent("B4", 1 -> 2)
    )

    check(pattern).inCycleUnordered(2)(
      ExpectedEvent("E4", 2 -> 3),
      ExpectedEvent("F4", 2 -> 3)
    )
  }

  test("""note("<e3 g4, a5 b6>") - Alternazione di Paralleli""") {
    val pattern = alt(par(e3, a5), par(g4, b6))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("E4", 0 -> 1),
      ExpectedEvent("A4", 0 -> 1)
    )

    check(pattern).inCycleUnordered(1)(
      ExpectedEvent("G4", 1 -> 2),
      ExpectedEvent("B4", 1 -> 2)
    )
  }

  test("""sound("<bd bd hh bd rim bd hh bd>")""") {
    val pattern = alt(bd, bd, hh, bd, rim, bd, hh, bd)
    val samples = Vector("bd", "bd", "hh", "bd", "rim", "bd", "hh", "bd")

    for (i <- 0 until 8) {
      check(pattern).inCycle(i)(
        ExpectedEvent(samples(i), i -> (i + 1))
      )
    }
  }

  test("""sound("<bd [hh sn]> cp")""") {
    val pattern = seq(alt(bd, seq(hh, sn)), cp)

    check(pattern).inCycle(0)(
      ExpectedEvent("bd", 0 -> (1\2)),
      ExpectedEvent("cp", (1\2) -> 1)
    )

    check(pattern).inCycle(1)(
      ExpectedEvent("hh", 1 -> (5\4)),
      ExpectedEvent("sn", (5\4) -> (3\2)),
      ExpectedEvent("cp", (3\2) -> 2)
    )
  }

  test("""sound("bd hh sn hh [hh, [sn <bd hh>]]")""") {
    val pattern = seq(bd, hh, sn, hh, par(hh, seq(sn, alt(bd, hh))))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\5)),
      ExpectedEvent("hh", (1\5) -> (2\5)),
      ExpectedEvent("sn", (2\5) -> (3\5)),
      ExpectedEvent("hh", (3\5) -> (4\5)),

      // Il parallelo finale:
      ExpectedEvent("hh", (4\5) -> 1),
      ExpectedEvent("sn", (4\5) -> (9\10)),
      ExpectedEvent("bd", (9\10) -> 1)
    )

    check(pattern).inCycleUnordered(1)(
      ExpectedEvent("bd", 1 -> (6\5)),
      ExpectedEvent("hh", (6\5) -> (7\5)),
      ExpectedEvent("sn", (7\5) -> (8\5)),
      ExpectedEvent("hh", (8\5) -> (9\5)),

      // Il parallelo finale (ciclo 2 dell'alternazione):
      ExpectedEvent("hh", (9\5) -> 2),
      ExpectedEvent("sn", (9\5) -> (19\10)),
      ExpectedEvent("hh", (19\10) -> 2)
    )
  }
}