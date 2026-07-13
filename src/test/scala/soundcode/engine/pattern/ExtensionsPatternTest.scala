package soundcode.engine.pattern

import soundcode.engine.support.*

class ExtensionsPatternTest extends SchedulerTestBase {

  test("""note("c4 f4 [g4 c4 c#4]").sound("<bd [hh sn]> cp").room("[4 5 [4]], <4 5 6>")""") {
    val base = seq(c4, f4, seq(g4, c4, cSharp4))
    val extSound = seq(alt(bd, seq(hh, sn)), cp)
    val extRoom = par(seq(room(4), room(5), seq(room(4))), alt(room(4), room(5), room(6)))
    val streams = List(ext(base, extSound, extRoom))

    assertCycle(streams, 0)(
      ExpEvent("C", 0 \ 1, 1 \ 3, List("bd", "4.0", "4.0")),
      ExpEvent("F", 1 \ 3, 2 \ 3, List("bd", "5.0", "4.0")),
      ExpEvent("G", 2 \ 3, 7 \ 9, List("cp", "4.0", "4.0")),
      ExpEvent("C", 7 \ 9, 8 \ 9, List("cp", "4.0", "4.0")),
      ExpEvent("C#", 8 \ 9, 1 \ 1, List("cp", "4.0", "4.0"))
    )

    assertCycle(streams, 1)(
      ExpEvent("C", 1 \ 1, 4 \ 3, List("hh", "4.0", "5.0")),
      ExpEvent("F", 4 \ 3, 5 \ 3, List("sn", "5.0", "5.0")),
      ExpEvent("G", 5 \ 3, 16 \ 9, List("cp", "4.0", "5.0")),
      ExpEvent("C", 16 \ 9, 17 \ 9, List("cp", "4.0", "5.0")),
      ExpEvent("C#", 17 \ 9, 2 \ 1, List("cp", "4.0", "5.0"))
    )
  }

  test("""sound("bd hh sn hh") sound("<cp rim bd>")""") {
    val p1 = seq(bd, hh, sn, hh)
    val p2 = alt(cp, rim, bd)
    val streams = List(p1, p2)

    // Entrambi gli stream vengono valutati in parallelo dallo Scheduler
    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 4), ExpEvent("hh", 1 \ 4, 1 \ 2), ExpEvent("sn", 1 \ 2, 3 \ 4), ExpEvent("hh", 3 \ 4, 1 \ 1), // p1
      ExpEvent("cp", 0 \ 1, 1 \ 1) // p2
    )

    assertCycleUnordered(streams, 1)(
      ExpEvent("bd", 1 \ 1, 5 \ 4), ExpEvent("hh", 5 \ 4, 3 \ 2), ExpEvent("sn", 3 \ 2, 7 \ 4), ExpEvent("hh", 7 \ 4, 2 \ 1), // p1
      ExpEvent("rim", 1 \ 1, 2 \ 1) // p2
    )

    assertCycleUnordered(streams, 2)(
      ExpEvent("bd", 2 \ 1, 9 \ 4), ExpEvent("hh", 9 \ 4, 5 \ 2), ExpEvent("sn", 5 \ 2, 11 \ 4), ExpEvent("hh", 11 \ 4, 3 \ 1), // p1
      ExpEvent("bd", 2 \ 1, 3 \ 1) // p2
    )
  }

  test("""sound("<bd cp>").note("<c4 f4 g4>")""") {
    val streams = List(ext(alt(bd, cp), alt(c4, f4, g4)))

    // Essendo due alternanze lunghe rispettivamente 2 e 3 cicli, il minimo comune multiplo è 6 cicli.
    val expectedElements = Vector(
      ("bd", "C"), ("cp", "F"), ("bd", "G"),
      ("cp", "C"), ("bd", "F"), ("cp", "G")
    )

    for (i <- 0 until 6) {
      assertCycle(streams, i)(
        ExpEvent(expectedElements(i)._1, i \ 1, (i + 1) \ 1, List(expectedElements(i)._2))
      )
    }
  }

  test("""sound("<bd <hh sn>>")""") {
    val streams = List(alt(bd, alt(hh, sn)))

    val expected = Vector("bd", "hh", "bd", "sn")

    for (i <- 0 until 4) {
      assertCycle(streams, i)(
        ExpEvent(expected(i), i \ 1, (i + 1) \ 1)
      )
    }
  }

  test("""sound("bd").note("<c4 f4>").gain("<3 4 5>")""") {
    val streams = List(ext(bd, alt(c4, f4), alt(gain(3), gain(4), gain(5))))

    val expected = Vector(
      ("C", "3.0"), ("F", "4.0"), ("C", "5.0"),
      ("F", "3.0"), ("C", "4.0"), ("F", "5.0")
    )

    for (i <- 0 until 6) {
      assertCycle(streams, i)(
        ExpEvent("bd", i \ 1, (i + 1) \ 1, List(expected(i)._1, expected(i)._2))
      )
    }
  }

  test("""sound("<bd cp>").note("c4 f4 g4")""") {
    val streams = List(ext(alt(bd, cp), seq(c4, f4, g4)))

    // La sequenza interna cade a frazioni, la nota "c4" viene presa al volo come estensione
    assertCycle(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 1, List("C"))
    )
    assertCycle(streams, 1)(
      ExpEvent("cp", 1 \ 1, 2 \ 1, List("C"))
    )
  }

  test("""sound("<bd <hh <sn cp>>>")""") {
    val streams = List(alt(bd, alt(hh, alt(sn, cp))))

    val expected = Vector("bd", "hh", "bd", "sn", "bd", "hh", "bd", "cp")

    for (i <- 0 until 8) {
      assertCycle(streams, i)(
        ExpEvent(expected(i), i \ 1, (i + 1) \ 1)
      )
    }
  }

  test("""sound("<bd cp <hh <sn rim clap>>>")""") {
    val streams = List(alt(bd, cp, alt(hh, alt(sn, rim, clap))))

    // Invece di testare tutti i 18 cicli, campioniamo i punti critici dell'albero:
    assertCycle(streams, 0)(ExpEvent("bd", 0 \ 1, 1 \ 1))
    assertCycle(streams, 1)(ExpEvent("cp", 1 \ 1, 2 \ 1))
    assertCycle(streams, 2)(ExpEvent("hh", 2 \ 1, 3 \ 1))
    assertCycle(streams, 3)(ExpEvent("bd", 3 \ 1, 4 \ 1))
    assertCycle(streams, 4)(ExpEvent("cp", 4 \ 1, 5 \ 1))
    assertCycle(streams, 5)(ExpEvent("sn", 5 \ 1, 6 \ 1))

    // E andiamo dritti all'ultimo elemento che cicla dopo 18 giri
    assertCycle(streams, 17)(ExpEvent("clap", 17 \ 1, 18 \ 1))
  }

  test("""sound("<bd cp>").note("<c4 f4 g4>").gain("<1 2 3 4 5>")""") {
    val streams = List(ext(alt(bd, cp), alt(c4, f4, g4), alt(gain(1), gain(2), gain(3), gain(4), gain(5))))

    // Il minimo comune multiplo di (2, 3, 5) è 30 cicli. Testiamo gli apici.

    // Ciclo 0 (Indici: bd=0, c4=0, gain=0)
    assertCycle(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 1, List("C", "1.0"))
    )

    // Ciclo 14 (Indici: 14%2=0 -> bd, 14%3=2 -> g4, 14%5=4 -> 5.0)
    assertCycle(streams, 14)(
      ExpEvent("bd", 14 \ 1, 15 \ 1, List("G", "5.0"))
    )

    // Ciclo 29 - Ultimo giro (Indici: 29%2=1 -> cp, 29%3=2 -> g4, 29%5=4 -> 5.0)
    assertCycle(streams, 29)(
      ExpEvent("cp", 29 \ 1, 30 \ 1, List("G", "5.0"))
    )
  }

  test("""note("c#4").gain("5").sound("bd").room("7")""") {
    // Il primo elemento (note) fa da base, gli altri si accodano come estensioni
    val base = cSharp4 // c#4
    val extGain = gain(5.0)
    val extSound = bd
    val extRoom = room(7.0)

    val streams = List(ext(base, extGain, extSound, extRoom))

    // Essendo tutti pattern fissi (Atom) della durata di 1 ciclo,
    // avremo un singolo evento perfetto da 0 a 1 con tutte le estensioni applicate.
    assertCycle(streams, 0)(
      ExpEvent("C#", 0 \ 1, 1 \ 1, List("5.0", "bd", "7.0"))
    )
  }
}