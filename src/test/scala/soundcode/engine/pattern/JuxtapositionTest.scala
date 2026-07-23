package soundcode.engine.pattern

import soundcode.engine.support.*

class JuxtapositionTest extends SchedulerTestBase {

  test("""sound("bd hh").jux(rev)""") {
    val pattern = seq(bd, hh).jux(rev)

    check(pattern).inCycleUnordered(0)(
      // Canale Sinistro (Pan 0.0) -> Originale
      ExpectedEvent("bd", 0 -> (1\2), "0.0"),
      ExpectedEvent("hh", (1\2) -> 1, "0.0"),

      // Canale Destro (Pan 1.0) -> Reverse
      ExpectedEvent("hh", 0 -> (1\2), "1.0"),
      ExpectedEvent("bd", (1\2) -> 1, "1.0")
    )
  }

  test("""sound("bd").jux()""") {
    val pattern = bd.jux()

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> 1, "0.0"),
      ExpectedEvent("bd", 0 -> 1, "1.0")
    )
  }

  test("""sound("bd [hh sn]").jux(rev)""") {
    val pattern = seq(bd, seq(hh, sn)).jux(rev)

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2), "0.0"),
      ExpectedEvent("hh", (1\2) -> (3\4), "0.0"),
      ExpectedEvent("sn", (3\4) -> 1, "0.0"),

      // --- Canale Destro (Reverse) ---
      ExpectedEvent("sn", 0 -> (1\4), "1.0"),
      ExpectedEvent("hh", (1\4) -> (1\2), "1.0"),
      ExpectedEvent("bd", (1\2) -> 1, "1.0")
    )
  }

  test("""sound("bd <hh cp>").jux(rev)""") {
    val pattern = seq(bd, alt(hh, cp)).jux(rev)

    // CICLO 0: suona "bd hh" e il suo reverse
    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2), "0.0"),
      ExpectedEvent("hh", (1\2) -> 1, "0.0"),
      ExpectedEvent("hh", 0 -> (1\2), "1.0"),
      ExpectedEvent("bd", (1\2) -> 1, "1.0")
    )

    // CICLO 1: suona "bd cp" e il suo reverse
    check(pattern).inCycleUnordered(1)(
      ExpectedEvent("bd", 1 -> (3\2), "0.0"),
      ExpectedEvent("cp", (3\2) -> 2, "0.0"),
      ExpectedEvent("cp", 1 -> (3\2), "1.0"),
      ExpectedEvent("bd", (3\2) -> 2, "1.0")
    )
  }

  test("""sound("bd hh").jux(fast(2))""") {
    val pattern = seq(bd, hh).jux(fast(2.0))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2), "0.0"),
      ExpectedEvent("hh", (1\2) -> 1, "0.0"),

      // Ripete il pattern 2 volte nello stesso ciclo (durate dimezzate)
      ExpectedEvent("bd", 0 -> (1\4), "1.0"),
      ExpectedEvent("hh", (1\4) -> (1\2), "1.0"),
      ExpectedEvent("bd", (1\2) -> (3\4), "1.0"),
      ExpectedEvent("hh", (3\4) -> 1, "1.0")
    )
  }

  test("""sound("bd hh").jux(late(1/4))""") {
    val pattern = seq(bd, hh).jux(late(1\4))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2), "0.0"),
      ExpectedEvent("hh", (1\2) -> 1, "0.0"),

      // --- Canale Destro (Ritardato di 1/4) ---
      // L'hi-hat troncato a inizio ciclo proviene in realtà dal ciclo -1!
      ExpectedEvent("hh",
        0 -> (1\4),
        whole = (-1\4) -> (1\4),
        effects = List("1.0")),
      ExpectedEvent("bd", (1\4) -> (3\4), "1.0"),
      ExpectedEvent("hh",
        (3\4) -> 1,
        whole = (3\4) -> (5\4),
        effects = List("1.0"))
    )
  }

  test("""sound("bd [hh sn]").jux(rev, late(1/4))""") {
    val pattern = seq(bd, seq(hh, sn)).jux(rev, late(1\4))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2), "0.0"),
      ExpectedEvent("hh", (1\2) -> (3\4), "0.0"),
      ExpectedEvent("sn", (3\4) -> 1, "0.0"),

      // --- Canale Destro (Reverse + Late 1/4) ---
      ExpectedEvent("bd", 0 -> (1\4), whole = (-1\4) -> (1\4), effects = List("1.0")),
      ExpectedEvent("sn", (1\4) -> (1\2), "1.0"),
      ExpectedEvent("hh", (1\2) -> (3\4), "1.0"),
      ExpectedEvent("bd", (3\4) -> 1, whole = (3\4) -> (5\4), effects = List("1.0"))
    )
  }

  test("""sound("bd hh").jux(gain(0.5))""") {
    val pattern = seq(bd, hh).jux(gain(0.5))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2), "0.0"),
      ExpectedEvent("hh", (1\2) -> 1, "0.0"),

      // --- Canale Destro (Originale + Gain + Pan) ---
      ExpectedEvent("bd", 0 -> (1\2), "0.5", "1.0"),
      ExpectedEvent("hh", (1\2) -> 1, "0.5", "1.0")
    )
  }

  test("""sound("bd [hh sn]").jux(rev, room(0.8))""") {
    val pattern = seq(bd, seq(hh, sn)).jux(rev, room(0.8))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2), "0.0"),
      ExpectedEvent("hh", (1\2) -> (3\4), "0.0"),
      ExpectedEvent("sn", (3\4) -> 1, "0.0"),

      // --- Canale Destro (Reverse + Room + Pan) ---
      ExpectedEvent("sn", 0 -> (1\4), "0.8", "1.0"),
      ExpectedEvent("hh", (1\4) -> (1\2), "0.8", "1.0"),
      ExpectedEvent("bd", (1\2) -> 1, "0.8", "1.0")
    )
  }

  test("""sound("bd hh").jux(gain("0.2 0.8"))""") {
    val pattern = seq(bd, hh).jux(seq(gain(0.2), gain(0.8)))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2), "0.0"),
      ExpectedEvent("hh", (1\2) -> 1, "0.0"),

      // --- Canale Destro (Originale + Sequenza Gain + Pan) ---
      ExpectedEvent("bd", 0 -> (1\2), "0.2", "1.0"),
      ExpectedEvent("hh", (1\2) -> 1, "0.8", "1.0")
    )
  }

  test("""sound("bd hh").jux(rev, room("0.3 [0.6 0.9]"))""") {
    // Composizione pura: seq di effetti annidata!
    val pattern = seq(bd, hh).jux(rev, seq(room(0.3), seq(room(0.6), room(0.9))))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2), "0.0"),
      ExpectedEvent("hh", (1\2) -> 1, "0.0"),

      // --- Canale Destro (Reverse + Pattern Room + Pan 1.0) ---
      ExpectedEvent("hh", 0 -> (1\2), "0.3", "1.0"),
      ExpectedEvent("bd", (1\2) -> 1, "0.6", "1.0")
    )
  }
}