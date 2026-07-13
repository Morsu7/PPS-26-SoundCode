package soundcode.engine.pattern

import soundcode.engine.support.*

class TimeModifierPatternTest extends SchedulerTestBase {

  test("""sound("bd sn").fast(2)""") {
    val streams = List(fast(2, seq(bd, sn)))
    assertCycle(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 4),
      ExpEvent("sn", 1 \ 4, 1 \ 2),
      ExpEvent("bd", 1 \ 2, 3 \ 4),
      ExpEvent("sn", 3 \ 4, 1 \ 1)
    )
  }

  test("""sound("bd").fast("1 2")""") {
    val speedPattern = seq(num(1.0), num(2.0))
    val streams = List(fast(speedPattern, bd))

    assertCycle(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("bd", 1 \ 2, 1 \ 1)
    )
  }

  test("""sound("bd, hh, sn").fast("<1 2 4>").gain("<0.5 1 2>").fast(2)""") {
    val base = par(bd, hh, sn)
    val fastPattern = fast(alt(num(1), num(2), num(4)), base)
    val withGain = ext(fastPattern, alt(gain(0.5), gain(1), gain(2)))
    val streams = List(fast(2.0, withGain))

    assertCycleUnordered(streams, 0)(
      // Prima metà
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.5")),
      ExpEvent("hh", 0 \ 1, 1 \ 2, List("0.5")),
      ExpEvent("sn", 0 \ 1, 1 \ 2, List("0.5")),

      // Seconda metà (velocità 2x)
      ExpEvent("bd", 1 \ 2, 3 \ 4, List("1.0")),
      ExpEvent("hh", 1 \ 2, 3 \ 4, List("1.0")),
      ExpEvent("sn", 1 \ 2, 3 \ 4, List("1.0")),
      ExpEvent("bd", 3 \ 4, 1 \ 1, List("1.0")),
      ExpEvent("hh", 3 \ 4, 1 \ 1, List("1.0")),
      ExpEvent("sn", 3 \ 4, 1 \ 1, List("1.0"))
    )
  }

  test("""sound("bd  sn").late(0.25).fast(2)""") {
    val innerLate = late(num(0.25), seq(bd, sn))
    val streams = List(fast(2.0, innerLate))

    assertCycleUnordered(streams, 0)(
      // Prima metà (da 0 a 0.5)
      ExpEvent("sn", 0 \ 1, 1 \ 8),
      ExpEvent("bd", 1 \ 8, 3 \ 8),
      ExpEvent("sn", 3 \ 8, 1 \ 2),
      // Seconda metà (da 0.5 a 1.0)
      ExpEvent("sn", 1 \ 2, 5 \ 8),
      ExpEvent("bd", 5 \ 8, 7 \ 8),
      ExpEvent("sn", 7 \ 8, 1 \ 1)
    )
  }

  test("""sound("bd sn").fast(2).late(0,25)""") {
    val innerFast = fast(2.0, seq(bd, sn))
    val streams = List(late(num(0.25), innerFast))

    assertCycleUnordered(streams, 0)(
      ExpEvent("sn", 0 \ 1, 1 \ 4), // Il rullante compresso del ciclo -1 !
      ExpEvent("bd", 1 \ 4, 1 \ 2),
      ExpEvent("sn", 1 \ 2, 3 \ 4),
      ExpEvent("bd", 3 \ 4, 1 \ 1) // La testa del 2° rullante va a finire nel ciclo successivo!
    )
  }

  test("""sound("bd sn").slow(2)""") {
    val streams = List(slow(2, seq(bd, sn)))
    assertCycle(streams, 0)(ExpEvent("bd", 0 \ 1, 1 \ 1))
    assertCycle(streams, 1)(ExpEvent("sn", 1 \ 1, 2 \ 1))
    assertCycle(streams, 2)(ExpEvent("bd", 2 \ 1, 3 \ 1))
    assertCycle(streams, 3)(ExpEvent("sn", 3 \ 1, 4 \ 1))
  }

  test("""sound("bd").slow(2)""") {
    val streams = List(slow(2, seq(bd)))
    assertCycle(streams, 0)(ExpEvent("bd", 0 \ 1, 1 \ 1))
    assertCycle(streams, 1)(ExpEvent("bd", 1 \ 1, 2 \ 1))
  }

  test("""sound("bd [hh, cp] sn [rim clap]").slow("<1 2>")""") {

    val base = seq(bd, par(hh, cp), sn, seq(rim, clap))

    val speedPattern = alt(num(1.0), num(2.0))
    val streams = List(slow(speedPattern, base))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 4),
      ExpEvent("hh", 1 \ 4, 1 \ 2),
      ExpEvent("cp", 1 \ 4, 1 \ 2),
      ExpEvent("sn", 1 \ 2, 3 \ 4),
      ExpEvent("rim", 3 \ 4, 7 \ 8),
      ExpEvent("clap", 7 \ 8, 1 \ 1)
    )

    assertCycleUnordered(streams, 1)(
      ExpEvent("sn", 1 \ 1, 3 \ 2),
      ExpEvent("rim", 3 \ 2, 7 \ 4),
      ExpEvent("clap", 7 \ 4, 2 \ 1)
    )

    assertCycleUnordered(streams, 2)(
      ExpEvent("bd", 2 \ 1, 9 \ 4), // 2.0 -> 2.25
      ExpEvent("hh", 9 \ 4, 5 \ 2), // 2.25 -> 2.50
      ExpEvent("cp", 9 \ 4, 5 \ 2),
      ExpEvent("sn", 5 \ 2, 11 \ 4), // 2.50 -> 2.75
      ExpEvent("rim", 11 \ 4, 23 \ 8), // 2.75 -> 2.875
      ExpEvent("clap", 23 \ 8, 3 \ 1) // 2.875 -> 3.0
    )
  }

  test("""sound("bd, hh, sn")""") {
    val streams = List(par(bd, hh, sn))
    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 1),
      ExpEvent("hh", 0 \ 1, 1 \ 1),
      ExpEvent("sn", 0 \ 1, 1 \ 1)
    )
  }

  test("""sound("bd") sound("hh")""") {
    val streams = List(bd, hh)
    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 1),
      ExpEvent("hh", 0 \ 1, 1 \ 1)
    )
  }

  test("""sound("bd sn").late(1/4)""") {
    val streams = List(late(num(0.25), seq(bd, sn)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("sn", 0 \ 1, 1 \ 4),
      ExpEvent("bd", 1 \ 4, 3 \ 4),
      ExpEvent("sn", 3 \ 4, 1 \ 1)
    )

    assertCycleUnordered(streams, 1)(
      ExpEvent("sn", 1 \ 1, 5 \ 4),
      ExpEvent("bd", 5 \ 4, 7 \ 4),
      ExpEvent("sn", 7 \ 4, 2 \ 1)
    )
  }

  test("""sound("bd hh sn").late(1/4)""") {
    val streams = List(late(num(0.25), seq(bd, hh, sn)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("sn", 0 \ 1, 1 \ 4), // Coda del rullante del ciclo -1 (0 -> 3/12)
      ExpEvent("bd", 1 \ 4, 7 \ 12), // Cassa (3/12 -> 7/12)
      ExpEvent("hh", 7 \ 12, 11 \ 12), // Hi-hat (7/12 -> 11/12)
      ExpEvent("sn", 11 \ 12, 1 \ 1) // Testa del rullante (11/12 -> 12/12)
    )
  }

  test("""sound("bd sn").fast(2).late(0,25)""") {
    val innerFast = fast(2.0, seq(bd, sn))
    val streams = List(late(num(0.25), innerFast))

    assertCycleUnordered(streams, 0)(
      ExpEvent("sn", 0 \ 1, 1 \ 4), // Il rullante compresso del ciclo -1 !
      ExpEvent("bd", 1 \ 4, 1 \ 2),
      ExpEvent("sn", 1 \ 2, 3 \ 4),
      ExpEvent("bd", 3 \ 4, 1 \ 1) // La testa del 2° rullante va a finire nel ciclo successivo!
    )
  }

  test("""sound("bd sn").late("[0 1/2]")""") {
    // Prima metà del ciclo [0.0 -> 0.5]: offset 0
    // Seconda metà del ciclo [0.5 -> 1.0]: offset 0.5
    val streams = List(late(seq(num(0.0), num(0.5)), seq(bd, sn)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2), // Cassa normale nella prima metà
      ExpEvent("bd", 1 \ 2, 1 \ 1) // Il late a 0.5 va a rileggere l'inizio del pattern.
    )
  }

  test("""sound("bd sn").late("<0 1/2>")""") {
    // Ciclo 0 (tutto il ciclo): offset 0
    // Ciclo 1 (tutto il ciclo): offset 0.5
    val streams = List(late(alt(num(0.0), num(0.5)), seq(bd, sn)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("sn", 1 \ 2, 1 \ 1)
    )

    // Nel ciclo 1, TUTTO il pattern scivola in avanti di 0.5.
    // Il rullante del ciclo 0 invade il ciclo 1, e la cassa scivola sulla seconda metà.
    assertCycleUnordered(streams, 1)(
      ExpEvent("sn", 1 \ 1, 3 \ 2), // Coda rullante del ciclo precedente (1.0 -> 1.5)
      ExpEvent("bd", 3 \ 2, 2 \ 1) // Cassa shiftata (1.5 -> 2.0)
    )
  }

  test("""sound("bd sn").late(2)""") {
    val streams = List(late(num(2.0), seq(bd, sn)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("sn", 1 \ 2, 1 \ 1)
    )
  }

  test("""sound("bd sn").early(1/4)""") {
    val streams = List(early(num(0.25), seq(bd, sn)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 4),
      ExpEvent("sn", 1 \ 4, 3 \ 4),
      ExpEvent("bd", 3 \ 4, 1 \ 1)
    )

    assertCycleUnordered(streams, 1)(
      ExpEvent("bd", 1 \ 1, 5 \ 4),
      ExpEvent("sn", 5 \ 4, 7 \ 4),
      ExpEvent("bd", 7 \ 4, 2 \ 1)
    )
  }

  test("""sound("bd hh sn").early(1/4)""") {
    val streams = List(early(num(0.25), seq(bd, hh, sn)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 12),
      ExpEvent("hh", 1 \ 12, 5 \ 12),
      ExpEvent("sn", 5 \ 12, 9 \ 12),
      ExpEvent("bd", 9 \ 12, 1 \ 1)
    )
  }

  test("""sound("bd sn").early("[0 1/4]")""") {
    val streams = List(early(seq(num(0.0), num(0.25)), seq(bd, sn)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("sn", 1 \ 2, 3 \ 4),
      ExpEvent("bd", 3 \ 4, 1 \ 1)
    )
  }

  test("""sound("bd sn").early("<0 1/2>")""") {
    val streams = List(early(alt(num(0.0), num(0.5)), seq(bd, sn)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("sn", 1 \ 2, 1 \ 1)
    )

    assertCycleUnordered(streams, 1)(
      ExpEvent("sn", 1 \ 1, 3 \ 2),
      ExpEvent("bd", 3 \ 2, 2 \ 1)
    )
  }

}
