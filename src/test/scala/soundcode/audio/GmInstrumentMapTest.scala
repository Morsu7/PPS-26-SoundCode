package soundcode.audio

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GmInstrumentMapTest extends AnyFunSuite with Matchers:

  test("nomi predefiniti -> programmi General MIDI") {
    GmInstrumentMap.toProgram("piano") shouldBe Some(0)
    GmInstrumentMap.toProgram("bass") shouldBe Some(33)
    GmInstrumentMap.toProgram("saw") shouldBe Some(81)
    GmInstrumentMap.toProgram("flute") shouldBe Some(73)
  }

  test("normalizza maiuscole e spazi") {
    GmInstrumentMap.toProgram("  Piano ") shouldBe Some(0)
  }

  test("nome sconosciuto -> None") {
    GmInstrumentMap.toProgram("bd") shouldBe None
    GmInstrumentMap.toProgram("xyz") shouldBe None
  }
