package soundcode.engine.pattern

import soundcode.domain.*
import soundcode.engine.support.*
import soundcode.engine.support.given_Conversion_Double_Pattern
import soundcode.engine.support.given_Conversion_Fraction_Pattern

class TimeModifierPatternTest extends SchedulerTestBase {

  test("""sound("bd sn").fast(2)""") {
    val pattern = seq(bd, sn).fast(2.0)

    check(pattern).inCycle(0)(
      ExpectedEvent("bd", 0 -> (1\4)),
      ExpectedEvent("sn", (1\4) -> (1\2)),
      ExpectedEvent("bd", (1\2) -> (3\4)),
      ExpectedEvent("sn", (3\4) -> 1)
    )
  }

  test("""sound("bd").fast("1 2")""") {
    val speedPattern = seq(1.0, 2.0)
    val pattern = bd.fast(speedPattern)

    check(pattern).inCycle(0)(
      ExpectedEvent("bd", 0 -> (1\2)),
      ExpectedEvent("bd", (1\2) -> 1)
    )
  }

  test("""sound("bd, hh, sn").fast("<1 2 4>").gain("<0.5 1 2>").fast(2)""") {
    val base = par(bd, hh, sn)
    val fastPattern = base.fast(alt(1.0, 2.0, 4.0))
    val withGain = ext(fastPattern, alt(gain(0.5), gain(1.0), gain(2.0)))
    val pattern = withGain.fast(2.0)

    check(pattern).inCycleUnordered(0)(
      // Prima metà
      ExpectedEvent("bd", 0 -> (1\2), "0.5"),
      ExpectedEvent("hh", 0 -> (1\2), "0.5"),
      ExpectedEvent("sn", 0 -> (1\2), "0.5"),

      // Seconda metà (velocità 2x)
      ExpectedEvent("bd", (1\2) -> (3\4), "1.0"),
      ExpectedEvent("hh", (1\2) -> (3\4), "1.0"),
      ExpectedEvent("sn", (1\2) -> (3\4), "1.0"),
      ExpectedEvent("bd", (3\4) -> 1, "1.0"),
      ExpectedEvent("hh", (3\4) -> 1, "1.0"),
      ExpectedEvent("sn", (3\4) -> 1, "1.0")
    )
  }

  test("""sound("bd  sn").late(0.25).fast(2)""") {
    val innerLate = seq(bd, sn).late(0.25)
    val pattern = innerLate.fast(2.0)

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("sn", 0 -> (1\8), whole = (-1\8) -> (1\8)),
      ExpectedEvent("bd", (1\8) -> (3\8)),
      ExpectedEvent("sn", (3\8) -> (1\2), whole = (3\8) -> (5\8)),
      ExpectedEvent("sn", (1\2) -> (5\8), whole = (3\8) -> (5\8)),
      ExpectedEvent("bd", (5\8) -> (7\8)),
      ExpectedEvent("sn", (7\8) -> 1, whole = (7\8) -> (9\8))
    )
  }

  test("""sound("bd sn").fast(2).late(0,25)""") {
    val innerFast = seq(bd, sn).fast(2.0)
    val pattern = innerFast.late(0.25)

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("sn", 0 -> (1\4)),
      ExpectedEvent("bd", (1\4) -> (1\2)),
      ExpectedEvent("sn", (1\2) -> (3\4)),
      ExpectedEvent("bd", (3\4) -> 1)
    )
  }

  test("""sound("bd sn").slow(2)""") {
    val pattern = seq(bd, sn).slow(2.0)

    check(pattern).inCycle(0)(ExpectedEvent("bd", 0 -> 1))
    check(pattern).inCycle(1)(ExpectedEvent("sn", 1 -> 2))
    check(pattern).inCycle(2)(ExpectedEvent("bd", 2 -> 3))
    check(pattern).inCycle(3)(ExpectedEvent("sn", 3 -> 4))
  }

  test("""sound("bd").slow(2)""") {
    val pattern = seq(bd).slow(2.0)

    check(pattern).inCycle(0)(ExpectedEvent("bd", 0 -> 1, whole = 0 -> 2))
    check(pattern).inCycle(1)(ExpectedEvent("bd", 1 -> 2, whole = 0 -> 2))
  }

  test("""sound("bd [hh, cp] sn [rim clap]").slow("<1 2>")""") {
    val base = seq(bd, par(hh, cp), sn, seq(rim, clap))
    val pattern = base.slow(alt(1.0, 2.0))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\4)),
      ExpectedEvent("hh", (1\4) -> (1\2)),
      ExpectedEvent("cp", (1\4) -> (1\2)),
      ExpectedEvent("sn", (1\2) -> (3\4)),
      ExpectedEvent("rim", (3\4) -> (7\8)),
      ExpectedEvent("clap", (7\8) -> 1)
    )

    check(pattern).inCycleUnordered(1)(
      ExpectedEvent("sn", 1 -> (3\2)),
      ExpectedEvent("rim", (3\2) -> (7\4)),
      ExpectedEvent("clap", (7\4) -> 2)
    )

    check(pattern).inCycleUnordered(2)(
      ExpectedEvent("bd", 2 -> (9\4)),
      ExpectedEvent("hh", (9\4) -> (5\2)),
      ExpectedEvent("cp", (9\4) -> (5\2)),
      ExpectedEvent("sn", (5\2) -> (11\4)),
      ExpectedEvent("rim", (11\4) -> (23\8)),
      ExpectedEvent("clap", (23\8) -> 3)
    )
  }

  test("""sound("bd, hh, sn")""") {
    val pattern = par(bd, hh, sn)
    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> 1),
      ExpectedEvent("hh", 0 -> 1),
      ExpectedEvent("sn", 0 -> 1)
    )
  }

  test("""sound("bd") sound("hh")""") {
    val pattern = seq(bd, hh) // o l'equivalente della sequenza
    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2)),
      ExpectedEvent("hh", (1\2) -> 1)
    )
  }

  test("""sound("bd sn").late(1/4)""") {
    val pattern = seq(bd, sn).late(1\4)

    check(pattern).inCycleUnordered(0)(
      // Il rullante del ciclo -1 era [-1/2 -> 0]. Con +1/4 diventa:
      ExpectedEvent("sn", 0 -> (1 \ 4), whole = (-1 \ 4) -> (1 \ 4)),
      ExpectedEvent("bd", (1 \ 4) -> (3 \ 4)), // Intero dentro il ciclo
      // Il rullante del ciclo 0 era [1/2 -> 1]. Con +1/4 diventa:
      ExpectedEvent("sn", (3 \ 4) -> 1, whole = (3 \ 4) -> (5 \ 4))
    )

    check(pattern).inCycleUnordered(1)(
      // Coda del rullante del ciclo 0
      ExpectedEvent("sn", 1 -> (5 \ 4), whole = (3 \ 4) -> (5 \ 4)),
      ExpectedEvent("bd", (5 \ 4) -> (7 \ 4)),
      // Rullante del ciclo 1 era [3/2 -> 2]. Con +1/4 diventa:
      ExpectedEvent("sn", (7 \ 4) -> 2, whole = (7 \ 4) -> (9 \ 4))
    )
  }

  test("""sound("bd hh sn").late(1/4)""") {
    val pattern = seq(bd, hh, sn).late(1\4)

    check(pattern).inCycleUnordered(0)(
      // L'ultimo elemento (sn) del ciclo -1 durava [-1/3 -> 0].
      // -1/3 è -4/12. Aggiungiamo 1/4 (cioè 3/12) -> whole = -1/12 -> 3/12
      ExpectedEvent("sn", 0 -> (1\4), whole = (-1\12) -> (1\4)),
      ExpectedEvent("bd", (1\4) -> (7\12)),
      ExpectedEvent("hh", (7\12) -> (11\12)),
      // 'sn' del ciclo 0 durava [2/3 -> 1]. Aggiungendo 1/4 (3/12) diventa 11/12 -> 15/12
      ExpectedEvent("sn", (11\12) -> 1, whole = (11\12) -> (5\4))
    )
  }

  test("""sound("bd sn").late("[0 1/2]")""") {
    val pattern = seq(bd, sn).late(seq(0.0, 0.5))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2)),
      ExpectedEvent("bd", (1\2) -> 1)
    )
  }

  test("""sound("bd sn").late("<0 1/2>")""") {
    val pattern = seq(bd, sn).late(alt(0.0, 0.5))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2)),
      ExpectedEvent("sn", (1\2) -> 1)
    )

    check(pattern).inCycleUnordered(1)(
      ExpectedEvent("sn", 1 -> (3\2), whole = 1 -> (3\2)),
      ExpectedEvent("bd", (3\2) -> 2)
    )
  }

  test("""sound("bd sn").late(2)""") {
    val pattern = seq(bd, sn).late(2.0)

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2)),
      ExpectedEvent("sn", (1\2) -> 1)
    )
  }

  test("""sound("bd sn").early(1/4)""") {
    val pattern = seq(bd, sn).early(1\4)

    check(pattern).inCycleUnordered(0)(
      // 'bd' era [0 -> 1/2]. Tirato indietro di 1/4 (-1/4)
      ExpectedEvent("bd", 0 -> (1 \ 4), whole = (-1 \ 4) -> (1 \ 4)),
      ExpectedEvent("sn", (1 \ 4) -> (3 \ 4)),
      // 'bd' del ciclo 1 era [1 -> 3/2]. Tirato indietro di 1/4 diventa [3/4 -> 5/4]
      ExpectedEvent("bd", (3 \ 4) -> 1, whole = (3 \ 4) -> (5 \ 4))
    )

    check(pattern).inCycleUnordered(1)(
      ExpectedEvent("bd", 1 -> (5 \ 4), whole = (3 \ 4) -> (5 \ 4)),
      ExpectedEvent("sn", (5 \ 4) -> (7 \ 4)),
      // 'bd' del ciclo 2 era [2 -> 5/2]. Tirato indietro diventa [7/4 -> 9/4]
      ExpectedEvent("bd", (7 \ 4) -> 2, whole = (7 \ 4) -> (9 \ 4))
    )
  }

  test("""sound("bd hh sn").early(1/4)""") {
    val pattern = seq(bd, hh, sn).early(1\4)

    check(pattern).inCycleUnordered(0)(
      // 'bd' era [0 -> 1/3 (4/12)]. Tirato indietro di 3/12 -> whole = -1/4 -> 1/12
      ExpectedEvent("bd", 0 -> (1\12), whole = (-1\4) -> (1\12)),
      ExpectedEvent("hh", (1\12) -> (5\12)),
      ExpectedEvent("sn", (5\12) -> (9\12)),
      // 'bd' del ciclo 1 era [1 (12/12) -> 16/12]. Tirato indietro di 3/12 diventa [9/12 -> 13/12]
      ExpectedEvent("bd", (9\12) -> 1, whole = (9\12) -> (13\12))
    )
  }

  test("""sound("bd sn").early("[0 1/4]")""") {
    val pattern = seq(bd, sn).early(seq(0.0, 1\4))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2)), // 1a metà: offset 0 (nessuno shift)

      // 2a metà: offset 1/4 indietro. L''sn' nativo [1/2 -> 1] indietreggia a [1/4 -> 3/4]
      ExpectedEvent("sn", (1\2) -> (3\4), whole = (1\4) -> (3\4)),

      // Entra anche la 'bd' del ciclo 1 (nativa [1 -> 3/2]), indietreggiando a [3/4 -> 5/4]
      ExpectedEvent("bd", (3\4) -> 1, whole = (3\4) -> (5\4))
    )
  }

  test("""sound("bd sn").early("<0 1/2>")""") {
    val pattern = seq(bd, sn).early(alt(0.0, 0.5))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1 \ 2)),
      ExpectedEvent("sn", (1 \ 2) -> 1)
    )

    check(pattern).inCycleUnordered(1)(
      // Tutto anticipato di mezzo ciclo.
      // 'sn' era [3/2 -> 2], diventa [1 -> 3/2]. 'bd' era [2 -> 5/2], diventa [3/2 -> 2]
      // I part e whole combaciano esattamente, quindi li omettiamo
      ExpectedEvent("sn", 1 -> (3 \ 2)),
      ExpectedEvent("bd", (3 \ 2) -> 2)
    )
  }
}