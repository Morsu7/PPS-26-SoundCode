package soundcode.engine.pattern

import soundcode.engine.support.*
import soundcode.engine.support.given_Conversion_Fraction_Pattern

class OffsetModifierTest extends SchedulerTestBase {

  test("""sound("bd hh").off(1/4)""") {
    val pattern = seq(bd, hh).off(1 \ 4)

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2)),
      ExpectedEvent("hh", (1\2) -> 1),
      ExpectedEvent("hh", 0 -> (1\4), whole = (-1\4) -> (1\4)), // Se c'è un whole tagliato a cavallo, lo specifichiamo; altrimenti se è interno o standard usiamo le frazioni corrette
      ExpectedEvent("bd", (1\4) -> (3\4)),
      ExpectedEvent("hh", (3\4) -> 1, whole = (3\4) -> (5\4))
    )
  }

  test("""sound("bd hh").off(1/4, rev)""") {
    val pattern = seq(bd, hh).off(1 \ 4, rev)

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2)),
      ExpectedEvent("hh", (1\2) -> 1),
      ExpectedEvent("bd", 0 -> (1\4), whole = (-1\4) -> (1\4)),
      ExpectedEvent("hh", (1\4) -> (3\4)),
      ExpectedEvent("bd", (3\4) -> 1, whole = (3\4) -> (5\4))
    )
  }

  test("""sound("bd hh").off(1/4, gain(0.5))""") {
    val pattern = seq(bd, hh).off(1 \ 4, gain(0.5))

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2)),
      ExpectedEvent("hh", (1\2) -> 1),
      ExpectedEvent("hh", 0 -> (1\4), whole = (-1\4) -> (1\4), effects = List("0.5")),
      ExpectedEvent("bd", (1\4) -> (3\4), effects = List("0.5")),
      ExpectedEvent("hh", (3\4) -> 1, whole = (3\4) -> (5\4), effects = List("0.5"))
    )
  }

  test("""sound("bd hh").off("<1/4 1/2>")""") {
    // Usiamo il nostro alt o pattern per l'offset dinamico
    val offsetPattern = alt(1 \ 4, 1 \ 2)
    val pattern = seq(bd, hh).off(offsetPattern)

    check(pattern).inCycleUnordered(0)(
      ExpectedEvent("bd", 0 -> (1\2)),
      ExpectedEvent("hh", (1\2) -> 1),
      ExpectedEvent("hh", 0 -> (1\4), whole = (-1\4) -> (1\4)),
      ExpectedEvent("bd", (1\4) -> (3\4)),
      ExpectedEvent("hh", (3\4) -> 1, whole = (3\4) -> (5\4))
    )

    check(pattern).inCycleUnordered(1)(
      ExpectedEvent("bd", 1 -> (3\2)),
      ExpectedEvent("hh", (3\2) -> 2),
      ExpectedEvent("hh", 1 -> (3\2)),
      ExpectedEvent("bd", (3\2) -> 2)
    )
  }
}