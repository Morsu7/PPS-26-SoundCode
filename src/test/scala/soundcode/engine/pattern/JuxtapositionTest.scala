package soundcode.engine.pattern

import soundcode.engine.support.SchedulerTestBase
import soundcode.domain.*
import soundcode.engine.support.*

class JuxtapositionTest extends SchedulerTestBase {

  test("""sound("bd hh").jux(rev)""") {
    val streams = List(seq(bd, hh).jux(rev))

    assertCycleUnordered(streams, 0)(
      // Canale Sinistro (Pan 0.0) -> Originale
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 1 \ 1, List("0.0")),

      // Canale Destro (Pan 1.0) -> Reverse
      ExpEvent("hh", 0 \ 1, 1 \ 2, List("1.0")),
      ExpEvent("bd", 1 \ 2, 1 \ 1, List("1.0"))
    )
  }

  test("""sound("bd").jux()""") {
    val streams = List(bd.jux())

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 1, List("0.0")),
      ExpEvent("bd", 0 \ 1, 1 \ 1, List("1.0"))
    )
  }

  test("""sound("bd [hh sn]").jux(rev)""") {
    val streams = List(seq(bd, seq(hh, sn)).jux(rev))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 3 \ 4, List("0.0")),
      ExpEvent("sn", 3 \ 4, 1 \ 1, List("0.0")),

      // --- Canale Destro (Reverse) ---
      ExpEvent("sn", 0 \ 1, 1 \ 4, List("1.0")),
      ExpEvent("hh", 1 \ 4, 1 \ 2, List("1.0")),
      ExpEvent("bd", 1 \ 2, 1 \ 1, List("1.0"))
    )
  }

  test("""sound("bd <hh cp>").jux(rev)""") {
    val pattern = seq(bd, alt(hh, cp)).jux(rev)
    val streams = List(pattern)

    // CICLO 0: suona "bd hh" e il suo reverse
    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 1 \ 1, List("0.0")),
      ExpEvent("hh", 0 \ 1, 1 \ 2, List("1.0")),
      ExpEvent("bd", 1 \ 2, 1 \ 1, List("1.0"))
    )

    // CICLO 1: suona "bd cp" e il suo reverse
    assertCycleUnordered(streams, 1)(
      ExpEvent("bd", 1 \ 1, 3 \ 2, List("0.0")),
      ExpEvent("cp", 3 \ 2, 2 \ 1, List("0.0")),
      ExpEvent("cp", 1 \ 1, 3 \ 2, List("1.0")),
      ExpEvent("bd", 3 \ 2, 2 \ 1, List("1.0"))
    )
  }

  test("""sound("bd hh").jux(fast(2))""") {
    val streams = List(seq(bd, hh).jux(fast(2.0)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 1 \ 1, List("0.0")),

      // Ripete il pattern 2 volte nello stesso ciclo (durate dimezzate)
      ExpEvent("bd", 0 \ 1, 1 \ 4, List("1.0")),
      ExpEvent("hh", 1 \ 4, 1 \ 2, List("1.0")),
      ExpEvent("bd", 1 \ 2, 3 \ 4, List("1.0")),
      ExpEvent("hh", 3 \ 4, 1 \ 1, List("1.0"))
    )
  }

  test("""sound("bd hh").jux(late(1/4))""") {
    val streams = List(seq(bd, hh).jux(late(1 \ 4)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 1 \ 1, List("0.0")),

      // --- Canale Destro (Ritardato di 1/4) ---
      ExpEvent("hh", 0 \ 1, 1 \ 4, List("1.0")),
      ExpEvent("bd", 1 \ 4, 3 \ 4, List("1.0")),
      ExpEvent("hh", 3 \ 4, 1 \ 1, List("1.0"))
    )
  }

  test("""sound("bd [hh sn]").jux(rev, late(1/4))""") {
    val streams = List(seq(bd, seq(hh, sn)).jux(rev, late(1 \ 4)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 3 \ 4, List("0.0")),
      ExpEvent("sn", 3 \ 4, 1 \ 1, List("0.0")),

      // --- Canale Destro (Reverse + Late 1/4) ---
      ExpEvent("bd", 0 \ 1, 1 \ 4, List("1.0")),
      ExpEvent("sn", 1 \ 4, 1 \ 2, List("1.0")),
      ExpEvent("hh", 1 \ 2, 3 \ 4, List("1.0")),
      ExpEvent("bd", 3 \ 4, 1 \ 1, List("1.0"))
    )
  }

  test("""sound("bd hh").jux(gain(0.5))""") {
    val streams = List(seq(bd, hh).jux(gain(0.5)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 1 \ 1, List("0.0")),

      // --- Canale Destro (Originale + Gain + Pan) ---
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.5", "1.0")),
      ExpEvent("hh", 1 \ 2, 1 \ 1, List("0.5", "1.0"))
    )
  }

  test("""sound("bd [hh sn]").jux(rev, room(0.8))""") {
    val streams = List(seq(bd, seq(hh, sn)).jux(rev, room(0.8)))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 3 \ 4, List("0.0")),
      ExpEvent("sn", 3 \ 4, 1 \ 1, List("0.0")),

      // --- Canale Destro (Reverse + Room + Pan) ---
      ExpEvent("sn", 0 \ 1, 1 \ 4, List("0.8", "1.0")),
      ExpEvent("hh", 1 \ 4, 1 \ 2, List("0.8", "1.0")),
      ExpEvent("bd", 1 \ 2, 1 \ 1, List("0.8", "1.0"))
    )
  }

  test("""sound("bd hh").jux(gain("0.2 0.8"))""") {
    // Usiamo 'seq' per creare la sequenza di gain direttamente nella chiamata
    val streams = List(seq(bd, hh).jux(seq(gain(0.2), gain(0.8))))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 1 \ 1, List("0.0")),

      // --- Canale Destro (Originale + Sequenza Gain + Pan) ---
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.2", "1.0")),
      ExpEvent("hh", 1 \ 2, 1 \ 1, List("0.8", "1.0"))
    )
  }

  test("""sound("bd hh").jux(rev, room("0.3 [0.6 0.9]"))""") {
    // Composizione pura: seq di effetti annidata!
    val streams = List(seq(bd, hh).jux(rev, seq(room(0.3), seq(room(0.6), room(0.9)))))

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 1 \ 1, List("0.0")),

      // --- Canale Destro (Reverse + Pattern Room + Pan 1.0) ---
      ExpEvent("hh", 0 \ 1, 1 \ 2, List("0.3", "1.0")),
      ExpEvent("bd", 1 \ 2, 1 \ 1, List("0.6", "1.0"))
    )
  }
}