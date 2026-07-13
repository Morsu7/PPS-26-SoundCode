package soundcode.engine

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import soundcode.domain.*

class SchedulerTest extends AnyFunSuite with Matchers {

  def resolve(patterns: List[Pattern[AudioPayload]], cycle: Int): List[ExpEvent] =
    val cycleStart = cycle \ 1
    val cycleEnd = (cycle + 1) \ 1

    SchedulerImpl.generateInfiniteTimeline(patterns)
      .dropWhile(_.part.start < cycleStart)
      .takeWhile(_.part.start < cycleEnd)
      .map(_.toExp)
      .toList

  def assertCycle(streams: List[Pattern[AudioPayload]], cycle: Int)(expected: ExpEvent*): Unit =
    resolve(streams, cycle) should contain theSameElementsInOrderAs expected.toList

  def assertCycleUnordered(streams: List[Pattern[AudioPayload]], cycle: Int)(expected: ExpEvent*): Unit =
    resolve(streams, cycle) should contain theSameElementsAs expected.toList

  def assertBoundedTrack(viewData: Seq[Seq[ScheduledEvent[AudioPayload]]], trackIndex: Int, expectedDuration: Fraction)(expected: ExpEvent*): Unit =
    val track = viewData(trackIndex)
    track.last.part.end shouldBe expectedDuration
    track.map(_.toExp).toList should contain theSameElementsInOrderAs expected.toList

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

  test("""note("c4 f4 [g4 c4 c#4]").sound("<bd [hh sn]> cp").room("[4 5 [4]], <4 5 6>")""") {
    val base = seq(c4, f4, seq(g4, c4, cSharp4))
    val extSound = seq(alt(bd, seq(hh, sn)), cp)
    val extRoom = par(seq(room(4), room(5), seq(room(4))), alt(room(4), room(5), room(6)))
    val streams = List(ext(base, extSound, extRoom))

    assertCycle(streams, 0)(
      ExpEvent("C4", 0 \ 1, 1 \ 3, List("bd", "4.0", "4.0")),
      ExpEvent("F4", 1 \ 3, 2 \ 3, List("bd", "5.0", "4.0")),
      ExpEvent("G4", 2 \ 3, 7 \ 9, List("cp", "4.0", "4.0")),
      ExpEvent("C4", 7 \ 9, 8 \ 9, List("cp", "4.0", "4.0")),
      ExpEvent("C#4", 8 \ 9, 1 \ 1, List("cp", "4.0", "4.0"))
    )

    assertCycle(streams, 1)(
      ExpEvent("F4", 1 \ 1, 4 \ 3, List("hh", "4.0", "5.0")),
      ExpEvent("G4", 4 \ 3, 5 \ 3, List("sn", "5.0", "5.0")),
      ExpEvent("G4", 5 \ 3, 16 \ 9, List("cp", "4.0", "5.0")),
      ExpEvent("C4", 16 \ 9, 17 \ 9, List("cp", "4.0", "5.0")),
      ExpEvent("C#4", 17 \ 9, 2 \ 1, List("cp", "4.0", "5.0"))
    )
  }

  test("""sound("bd hh sn hh") sound("<cp rim bd>")""") {
    val p1 = seq(bd, hh, sn, hh)
    val p2 = alt(cp, rim, bd)
    val streams = List(p1, p2)

    val viewData = SchedulerImpl.generateBoundedTimelines(streams)

    viewData should have size 2
    assertBoundedTrack(viewData, trackIndex = 1, expectedDuration = 3 \ 1)(
      ExpEvent("cp", 0 \ 1, 1 \ 1),
      ExpEvent("rim", 1 \ 1, 2 \ 1),
      ExpEvent("bd", 2 \ 1, 3 \ 1)
    )
  }

  test("""sound("<bd cp>").note("<c4 f4 g4>")""") {
    val streams = List(ext(alt(bd, cp), alt(c4, f4, g4)))
    val viewData = SchedulerImpl.generateBoundedTimelines(streams)

    viewData should have size 1
    assertBoundedTrack(viewData, trackIndex = 0, expectedDuration = 6 \ 1)(
      ExpEvent("bd", 0 \ 1, 1 \ 1, List("c4")),
      ExpEvent("cp", 1 \ 1, 2 \ 1, List("f4")),
      ExpEvent("bd", 2 \ 1, 3 \ 1, List("g4")),
      ExpEvent("cp", 3 \ 1, 4 \ 1, List("c4")),
      ExpEvent("bd", 4 \ 1, 5 \ 1, List("f4")),
      ExpEvent("cp", 5 \ 1, 6 \ 1, List("g4"))
    )
  }

  test("""sound("<bd <hh sn>>")""") {
    val streams = List(alt(bd, alt(hh, sn)))
    val viewData = SchedulerImpl.generateBoundedTimelines(streams)

    viewData should have size 1
    assertBoundedTrack(viewData, trackIndex = 0, expectedDuration = 4 \ 1)(
      ExpEvent("bd", 0 \ 1, 1 \ 1),
      ExpEvent("hh", 1 \ 1, 2 \ 1),
      ExpEvent("bd", 2 \ 1, 3 \ 1),
      ExpEvent("sn", 3 \ 1, 4 \ 1)
    )
  }

  test("""sound("bd").note("<c4 f4>").gain("<3 4 5>")""") {
    val streams = List(ext(bd, alt(c4, f4), alt(gain(3), gain(4), gain(5))))
    val viewData = SchedulerImpl.generateBoundedTimelines(streams)

    viewData should have size 1
    assertBoundedTrack(viewData, trackIndex = 0, expectedDuration = 6 \ 1)(
      ExpEvent("bd", 0 \ 1, 1 \ 1, List("C4", "3.0")),
      ExpEvent("bd", 1 \ 1, 2 \ 1, List("F4", "4.0")),
      ExpEvent("bd", 2 \ 1, 3 \ 1, List("C4", "5.0")),
      ExpEvent("bd", 3 \ 1, 4 \ 1, List("F4", "3.0")),
      ExpEvent("bd", 4 \ 1, 5 \ 1, List("C4", "4.0")),
      ExpEvent("bd", 5 \ 1, 6 \ 1, List("F4", "5.0"))
    )
  }

  test("""sound("<bd cp>").note("c4 f4 g4")""") {
    val streams = List(ext(alt(bd, cp), seq(c4, f4, g4)))
    val viewData = SchedulerImpl.generateBoundedTimelines(streams)

    viewData should have size 1
    assertBoundedTrack(viewData, trackIndex = 0, expectedDuration = 2 \ 1)(
      ExpEvent("bd", 0 \ 1, 1 \ 1, List("C4")),
      ExpEvent("cp", 1 \ 1, 2 \ 1, List("C4"))
    )
  }

  test("""sound("<bd <hh <sn cp>>>")""") {
    val streams = List(alt(bd, alt(hh, alt(sn, cp))))
    val viewData = SchedulerImpl.generateBoundedTimelines(streams)

    viewData should have size 1
    assertBoundedTrack(viewData, trackIndex = 0, expectedDuration = 8 \ 1)(
      ExpEvent("bd", 0 \ 1, 1 \ 1),
      ExpEvent("hh", 1 \ 1, 2 \ 1),
      ExpEvent("bd", 2 \ 1, 3 \ 1),
      ExpEvent("sn", 3 \ 1, 4 \ 1),
      ExpEvent("bd", 4 \ 1, 5 \ 1),
      ExpEvent("hh", 5 \ 1, 6 \ 1),
      ExpEvent("bd", 6 \ 1, 7 \ 1),
      ExpEvent("cp", 7 \ 1, 8 \ 1)
    )
  }

  test("""sound("<bd cp <hh <sn rim clap>>>")""") {
    val streams = List(alt(bd, cp, alt(hh, alt(sn, rim, clap))))
    val viewData = SchedulerImpl.generateBoundedTimelines(streams)

    viewData should have size 1
    val trackTimeline = viewData.head

    trackTimeline.last.part.end shouldBe (18 \ 1)
    val events = trackTimeline.map(_.toExp).toVector

    events(0).element shouldBe "bd"
    events(1).element shouldBe "cp"
    events(2).element shouldBe "hh"
    events(3).element shouldBe "bd"
    events(4).element shouldBe "cp"
    events(5).element shouldBe "sn"
    events(17).element shouldBe "clap"
  }

  test("""sound("<bd cp>").note("<c4 f4 g4>").gain("<1 2 3 4 5>")""") {
    val streams = List(ext(alt(bd, cp), alt(c4, f4, g4), alt(gain(1), gain(2), gain(3), gain(4), gain(5))))
    val viewData = SchedulerImpl.generateBoundedTimelines(streams)

    viewData should have size 1
    val trackTimeline = viewData.head

    trackTimeline.last.part.end shouldBe (30 \ 1)
    trackTimeline should have size 30

    val events = trackTimeline.map(_.toExp).toVector

    events(0) shouldBe ExpEvent("bd", 0 \ 1, 1 \ 1, List("C4", "1.0"))
    events(14) shouldBe ExpEvent("bd", 14 \ 1, 15 \ 1, List("G4", "5.0"))
    events(29) shouldBe ExpEvent("cp", 29 \ 1, 30 \ 1, List("G4", "5.0"))
  }

  test("""sound("bd sn").fast(2)""") {
    val streams = List(fast(2, seq(bd, sn)))
    assertCycle(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 4),
      ExpEvent("sn", 1 \ 4, 1 \ 2),
      ExpEvent("bd", 1 \ 2, 3 \ 4),
      ExpEvent("sn", 3 \ 4, 1 \ 1)
    )
  }

  test("""sound("bd sn").slow(2)""") {
    val streams = List(slow(2, seq(bd, sn)))
    assertCycle(streams, 0)(ExpEvent("bd", 0 \ 1, 1 \ 1))
    assertCycle(streams, 1)(ExpEvent("sn", 1 \ 1, 2 \ 1))
    assertCycle(streams, 2)(ExpEvent("bd", 2 \ 1, 3 \ 1))
    assertCycle(streams, 3)(ExpEvent("sn", 3 \ 1, 4 \ 1))
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

  test("""sound("bd").fast("1 2")""") {
    val speedPattern = seq(num(1.0), num(2.0))
    val streams = List(fast(speedPattern, bd))

    assertCycle(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("bd", 1 \ 2, 1 \ 1)
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

  test("""sound("bd sn").late("[0 1/2]")""") {
    // Prima metà del ciclo [0.0 -> 0.5]: offset 0
    // Seconda metà del ciclo [0.5 -> 1.0]: offset 0.5
    val streams = List(late(seq(num(0.0), num(0.5)), seq(bd, sn)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2), // Cassa normale nella prima metà
      ExpEvent("bd", 1 \ 2, 1 \ 1)  // Il late a 0.5 va a rileggere l'inizio del pattern.
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
      ExpEvent("bd", 3 \ 2, 2 \ 1)  // Cassa shiftata (1.5 -> 2.0)
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

  //Aggiunte
  test("""sound("bd sn hh").reverse""") {
    val streams = List(Pattern.TimeWarp(PatternModifier.Reverse, seq(bd, sn , hh)))

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
    val streams = List(Pattern.TimeWarp(PatternModifier.Reverse, patternWithEffects))

    assertCycleUnordered(streams, 0)(
      ExpEvent("clap", 0 \ 1, 1 \ 6, List("G4", "1.0")),
      ExpEvent("rim", 1 \ 6, 1 \ 3, List("G4", "1.0")),
      ExpEvent("sn", 1 \ 3, 2 \ 3, List("C4", "1.0")),
      ExpEvent("bd", 2 \ 3, 1 \ 1, List("C4", "1.0")),
      ExpEvent("hh", 2 \ 3, 1 \ 1, List("C4", "1.0"))
    )

    assertCycleUnordered(streams, 1)(
      ExpEvent("clap", 1 \ 1, 7 \ 6, List("G4", "0.8")),
      ExpEvent("rim", 7 \ 6, 4 \ 3, List("G4", "0.8")),
      ExpEvent("cp", 4 \ 3, 5 \ 3, List("C4", "0.8")),
      ExpEvent("bd", 5 \ 3, 2 \ 1, List("C4", "0.8")),
      ExpEvent("hh", 5 \ 3, 2 \ 1, List("C4", "0.8"))
    )
  }

  test("""sound("bd sn").repetition(2)""") {
    // Verifica che la ripetizione comprima la sequenza raddoppiandone la frequenza nel ciclo
    val streams = List(Pattern.TimeWarp(PatternModifier.Repetition(num(2.0)), seq(bd, sn)))

    assertCycle(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 4),
      ExpEvent("sn", 1 \ 4, 1 \ 2),
      ExpEvent("bd", 1 \ 2, 3 \ 4),
      ExpEvent("sn", 3 \ 4, 1 \ 1)
    )
  }

  test("""sound("bd sn").jux(Reverse)""") {
    // Juxtaposition: suona il pattern originale in parallelo con la sua versione modificata.
    // Originale: bd [0, 0.5], sn [0.5, 1]
    // Reversed:  sn [0, 0.5], bd [0.5, 1]
    val mods = List(PatternModifier.Reverse)
    val streams = List(Pattern.TimeWarp(PatternModifier.Juxtaposition(mods), seq(bd, sn)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("sn", 0 \ 1, 1 \ 2),
      ExpEvent("sn", 1 \ 2, 1 \ 1),
      ExpEvent("bd", 1 \ 2, 1 \ 1)
    )
  }

  test("""sound("bd sn").offset(0.5, Reverse)""") {
    // Offset: suona l'originale in parallelo con una copia che viene prima ritardata (Late 0.5)
    // e successivamente modificata (Reverse).
    // Originale: bd [0, 0.5], sn [0.5, 1]
    // Copia con Offset: la combinazione geometrica di Late(0.5) + Reverse produce una simmetria
    // per cui bd e sn si sovrappongono perfettamente alle posizioni dell'originale.
    val mods = List(PatternModifier.Reverse)
    val streams = List(Pattern.TimeWarp(PatternModifier.Offset(0.5, mods), seq(bd, sn)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("bd", 0 \ 1, 1 \ 2),
      ExpEvent("sn", 1 \ 2, 1 \ 1),
      ExpEvent("sn", 1 \ 2, 1 \ 1)
    )
  }



}

case class ExpEvent(element: String, start: Fraction, end: Fraction, extensions: List[String] = Nil):
  override def toString: String = {
    val extStr = if (extensions.isEmpty) "" else s" + [${extensions.mkString(", ")}]"
    s"('$element' @ $start -> $end$extStr)"
  }

extension (e: ScheduledEvent[AudioPayload])
  def toExp: ExpEvent =
    def extractName(payload: AudioPayload): String = payload match
      case Sound.SampleInText(s, _) => s.value
      case Sound.NoteInText(n, _) => n.toString
      case AudioEffect.Gain(v) => v.toString
      case AudioEffect.Room(v) => v.toString
      case AudioEffect.Pan(v) => v.toString
      case _ => "unknown"

    val name = extractName(e.value)
    val exts = e.appliedExtensions.map(extractName)

    ExpEvent(name, e.part.start, e.part.end, exts)