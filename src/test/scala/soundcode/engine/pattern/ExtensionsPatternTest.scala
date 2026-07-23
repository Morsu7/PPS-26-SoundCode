package soundcode.engine.pattern

import soundcode.engine.support.*

class ExtensionsPatternTest extends SchedulerTestBase {

  test("""note("c4 f4 [g4 c4 c#4]").sound("<bd [hh sn]> cp").room("[4 5 [4]], <4 5 6>")""") {
    val base = seq(c4, f4, seq(g4, c4, cSharp4))
    val extSound = seq(alt(bd, seq(hh, sn)), cp)
    val extRoom = par(seq(room(4), room(5), seq(room(4))), alt(room(4), room(5), room(6)))

    // Usiamo la funzione esplicita ext o withExtensions per i livelli audio
    val pattern = ext(base, extSound, extRoom)

    check(pattern).inCycle(0)(
      ExpectedEvent("C4", 0 -> (1\3), effects = List("bd", "4.0")),
      ExpectedEvent("F4", (1\3) -> (2\3), effects = List("bd", "4.0")),
      ExpectedEvent("G4", (2\3) -> (7\9), effects = List("cp", "4.0")),
      ExpectedEvent("C4", (7\9) -> (8\9), effects = List("cp", "4.0")),
      ExpectedEvent("C#4", (8\9) -> 1, effects = List("cp", "4.0"))
    )

    check(pattern).inCycle(1)(
      ExpectedEvent("C4", 1 -> (4\3), effects = List("hh", "5.0")),
      ExpectedEvent("F4", (4\3) -> (5\3), effects = List("sn", "5.0")),
      ExpectedEvent("G4", (5\3) -> (16\9), effects = List("cp", "5.0")),
      ExpectedEvent("C4", (16\9) -> (17\9), effects = List("cp", "5.0")),
      ExpectedEvent("C#4", (17\9) -> 2, effects = List("cp", "5.0"))
    )
  }

  test("""sound("bd hh sn hh") sound("<cp rim bd>")""") {
    val p1 = seq(bd, hh, sn, hh)
    val p2 = alt(cp, rim, bd)

    check(p1, p2).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\4)), ExpectedEvent("hh", (1\4) -> (1\2)), ExpectedEvent("sn", (1\2) -> (3\4)), ExpectedEvent("hh", (3\4) -> 1),
      ExpectedEvent("cp", 0 -> 1)
    )

    check(p1, p2).inCycleUnordered(1)(
      ExpectedEvent("bd", 1 -> (5\4)), ExpectedEvent("hh", (5\4) -> (3\2)), ExpectedEvent("sn", (3\2) -> (7\4)), ExpectedEvent("hh", (7\4) -> 2),
      ExpectedEvent("rim", 1 -> 2)
    )

    check(p1, p2).inCycleUnordered(2)(
      ExpectedEvent("bd", 2 -> (9\4)), ExpectedEvent("hh", (9\4) -> (5\2)), ExpectedEvent("sn", (5\2) -> (11\4)), ExpectedEvent("hh", (11\4) -> 3),
      ExpectedEvent("bd", 2 -> 3)
    )
  }

  test("""sound("<bd cp>").note("<c4 f4 g4>")""") {
    val pattern = ext(alt(bd, cp), alt(c4, f4, g4))

    val expectedElements = Vector(
      ("bd", "C4"), ("cp", "F4"), ("bd", "G4"),
      ("cp", "C4"), ("bd", "F4"), ("cp", "G4")
    )

    for (((sample, note), i) <- expectedElements.zipWithIndex) {
      check(pattern).inCycle(i)(
        ExpectedEvent(sample, i -> (i + 1), note)
      )
    }
  }

  test("""sound("<bd <hh sn>>")""") {
    val pattern = alt(bd, alt(hh, sn))
    val expected = Vector("bd", "hh", "bd", "sn")

    for (i <- 0 until 4) {
      check(pattern).inCycle(i)(
        ExpectedEvent(expected(i), i -> (i + 1))
      )
    }
  }

  test("""sound("bd").note("<c4 f4>").gain("<3 4 5>")""") {
    val pattern = ext(bd, alt(c4, f4), alt(gain(3), gain(4), gain(5)))

    val expected = Vector(
      ("C4", "3.0"), ("F4", "4.0"), ("C4", "5.0"),
      ("F4", "3.0"), ("C4", "4.0"), ("F4", "5.0")
    )

    for (i <- 0 until 6) {
      check(pattern).inCycle(i)(
        ExpectedEvent("bd", i -> (i + 1), expected(i)._1, expected(i)._2)
      )
    }
  }

  test("""sound("<bd cp>").note("c4 f4 g4")""") {
    val pattern = ext(alt(bd, cp), seq(c4, f4, g4))

    check(pattern).inCycle(0)(
      ExpectedEvent("bd", 0 -> 1, "C4")
    )

    check(pattern).inCycle(1)(
      ExpectedEvent("cp", 1 -> 2, "C4")
    )
  }

  test("""sound("<bd <hh <sn cp>>>")""") {
    val pattern = alt(bd, alt(hh, alt(sn, cp)))
    val expected = Vector("bd", "hh", "bd", "sn", "bd", "hh", "bd", "cp")

    for (i <- 0 until 8) {
      check(pattern).inCycle(i)(
        ExpectedEvent(expected(i), i -> (i + 1))
      )
    }
  }

  test("""sound("<bd cp <hh <sn rim clap>>>")""") {
    val pattern = alt(bd, cp, alt(hh, alt(sn, rim, clap)))

    check(pattern).inCycle(0)(ExpectedEvent("bd", 0 -> 1))
    check(pattern).inCycle(1)(ExpectedEvent("cp", 1 -> 2))
    check(pattern).inCycle(2)(ExpectedEvent("hh", 2 -> 3))
    check(pattern).inCycle(3)(ExpectedEvent("bd", 3 -> 4))
    check(pattern).inCycle(4)(ExpectedEvent("cp", 4 -> 5))
    check(pattern).inCycle(5)(ExpectedEvent("sn", 5 -> 6))

    check(pattern).inCycle(17)(ExpectedEvent("clap", 17 -> 18))
  }

  test("""sound("<bd cp>").note("<c4 f4 g4>").gain("<1 2 3 4 5>")""") {
    val pattern = ext(alt(bd, cp), alt(c4, f4, g4), alt(gain(1), gain(2), gain(3), gain(4), gain(5)))

    check(pattern).inCycle(0)(
      ExpectedEvent("bd", 0 -> 1, "C4", "1.0")
    )

    check(pattern).inCycle(14)(
      ExpectedEvent("bd", 14 -> 15, "G4", "5.0")
    )

    check(pattern).inCycle(29)(
      ExpectedEvent("cp", 29 -> 30, "G4", "5.0")
    )
  }

  test("""note("c#4").gain("5").sound("bd").room("7")""") {
    val pattern = ext(cSharp4, gain(5.0), bd, room(7.0))

    check(pattern).inCycle(0)(
      ExpectedEvent("C#4", 0 -> 1, "5.0", "bd", "7.0")
    )
  }
}