package soundcode.engine.pattern

import soundcode.engine.support.SchedulerTestBase
import soundcode.domain.*
import soundcode.domain.PatternModifier.*
import soundcode.engine.support.*

class JuxtapositionTest extends SchedulerTestBase {


  test("""sound("bd hh").jux(rev)""") {
    val basePattern = seq(bd, hh)

    val juxPattern = Pattern.TimeWarp(Juxtaposition(List(Reverse)), basePattern)
    val streams = List(juxPattern)

    assertCycleUnordered(streams, 0)(
      // Canale Sinistro (Pan 0.0) -> Originale
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 1 \ 1, List("0.0")),

      // Canale Destro (Pan 1.0) -> Reverse
      ExpEvent("hh", 0 \ 1, 1 \ 2, List("1.0")),
      ExpEvent("bd", 1 \ 2, 1 \ 1, List("1.0"))
    )
  }

  test("""sound("bd").jux(id)""") {
    val basePattern = bd
    // id in Strudel/Tidal non applica modifiche, crea solo lo sdoppiamento stereo
    val juxPattern = Pattern.TimeWarp(Juxtaposition(Nil), basePattern)
    val streams = List(juxPattern)

    assertCycleUnordered(streams, 0)(
      ExpEvent("bd", 0 \ 1, 1 \ 1, List("0.0")),
      ExpEvent("bd", 0 \ 1, 1 \ 1, List("1.0"))
    )
  }

  test("""sound("bd [hh sn]").jux(rev)""") {
    // Originale: bd dura metà ciclo, hh un quarto, sn un quarto
    val basePattern = seq(bd, seq(hh, sn))

    val juxPattern = Pattern.TimeWarp(Juxtaposition(List(Reverse)), basePattern)
    val streams = List(juxPattern)

    assertCycleUnordered(streams, 0)(
      // --- Canale Sinistro (Originale) ---
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 3 \ 4, List("0.0")),
      ExpEvent("sn", 3 \ 4, 1 \ 1, List("0.0")),

      // --- Canale Destro (Reverse) ---
      // Invertendo il ciclo, i colpi più fitti [hh sn] finiscono all'inizio
      // diventando [sn hh], seguiti dal 'bd' che chiude il ciclo.
      ExpEvent("sn", 0 \ 1, 1 \ 4, List("1.0")),
      ExpEvent("hh", 1 \ 4, 1 \ 2, List("1.0")),
      ExpEvent("bd", 1 \ 2, 1 \ 1, List("1.0"))
    )
  }

  test("""sound("bd <hh cp>").jux(rev)""") {
    // Un pattern che cambia ad ogni ciclo, avvolto in un jux
    val basePattern = seq(bd, alt(hh, cp))

    val juxPattern = Pattern.TimeWarp(Juxtaposition(List(Reverse)), basePattern)
    val streams = List(juxPattern)

    // CICLO 0: suona "bd hh" e il suo reverse
    assertCycleUnordered(streams, 0)(
      // Sinistra
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 1 \ 1, List("0.0")),
      // Destra
      ExpEvent("hh", 0 \ 1, 1 \ 2, List("1.0")),
      ExpEvent("bd", 1 \ 2, 1 \ 1, List("1.0"))
    )

    // CICLO 1: l'Alternation scatta in avanti. Suona "bd cp" e il suo reverse
    assertCycleUnordered(streams, 1)(
      // Sinistra
      ExpEvent("bd", 1 \ 1, 3 \ 2, List("0.0")),
      ExpEvent("cp", 3 \ 2, 2 \ 1, List("0.0")),
      // Destra
      ExpEvent("cp", 1 \ 1, 3 \ 2, List("1.0")),
      ExpEvent("bd", 3 \ 2, 2 \ 1, List("1.0"))
    )
  }

  test("""sound("bd hh").jux(fast(2))""") {
    val basePattern = seq(bd, hh)

    // jux(fast(2)) applica un raddoppio di velocità al canale destro
    val modifier = Juxtaposition(List(FastForward(Pattern.Atom(2.0))))
    val juxPattern = Pattern.TimeWarp(modifier, basePattern)
    val streams = List(juxPattern)

    assertCycleUnordered(streams, 0)(
      // --- Canale Sinistro (1x velocità) ---
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 1 \ 1, List("0.0")),

      // --- Canale Destro (2x velocità) ---
      // Ripete il pattern 2 volte nello stesso ciclo (durate dimezzate)
      // Prima ripetizione (0 -> 1/2)
      ExpEvent("bd", 0 \ 1, 1 \ 4, List("1.0")),
      ExpEvent("hh", 1 \ 4, 1 \ 2, List("1.0")),
      // Seconda ripetizione (1/2 -> 1)
      ExpEvent("bd", 1 \ 2, 3 \ 4, List("1.0")),
      ExpEvent("hh", 3 \ 4, 1 \ 1, List("1.0"))
    )
  }

  test("""sound("bd hh").jux(late(1/4))""") {
    val basePattern = seq(bd, hh)

    val modifier = Juxtaposition(List(Late(Pattern.Atom(0.25))))
    val juxPattern = Pattern.TimeWarp(modifier, basePattern)
    val streams = List(juxPattern)

    assertCycleUnordered(streams, 0)(
      // --- Canale Sinistro (Originale) ---
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 1 \ 1, List("0.0")),

      // --- Canale Destro (Ritardato di 0.25) ---
      // 1. La coda dell' 'hh' del ciclo precedente (ciclo -1) slitta nel ciclo 0
      ExpEvent("hh", 0 \ 1, 1 \ 4, List("1.0")),
      // 2. 'bd' del ciclo 0 slitta a 1/4
      ExpEvent("bd", 1 \ 4, 3 \ 4, List("1.0")),
      // 3. 'hh' del ciclo 0 slitta a 3/4 e viene troncato alla fine della finestra (1/1)
      ExpEvent("hh", 3 \ 4, 1 \ 1, List("1.0"))
    )
  }

  test("""sound("bd [hh sn]").jux(rev, late(1/4))""") {
    val basePattern = seq(bd, seq(hh, sn))

    val modifiers = List(
      Reverse,
      Late(Pattern.Atom(0.25))
    )
    val juxPattern = Pattern.TimeWarp(Juxtaposition(modifiers), basePattern)
    val streams = List(juxPattern)

    assertCycleUnordered(streams, 0)(
      // --- Canale Sinistro (Nessun Modificatore) ---
      ExpEvent("bd", 0 \ 1, 1 \ 2, List("0.0")),
      ExpEvent("hh", 1 \ 2, 3 \ 4, List("0.0")),
      ExpEvent("sn", 3 \ 4, 1 \ 1, List("0.0")),

      // --- Canale Destro (Reverse + Late 1/4) ---
      // 1. Il reverse dell'ultimo elemento del ciclo -1 ('bd') straborda nel ciclo 0 a causa del delay
      ExpEvent("bd", 0 \ 1, 1 \ 4, List("1.0")),
      // 2. Il reverse degli elementi del ciclo 0 ('sn' e 'hh') slittano avanti di 1/4
      ExpEvent("sn", 1 \ 4, 1 \ 2, List("1.0")),
      ExpEvent("hh", 1 \ 2, 3 \ 4, List("1.0")),
      // 3. Il reverse del primo elemento del ciclo 0 ('bd') finisce in fondo, troncato a 1/1
      ExpEvent("bd", 3 \ 4, 1 \ 1, List("1.0"))
    )
  }
}