package soundcode.engine

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import soundcode.domain.*

/** Logica pura del buffer di note per i visualizzatori: headless-safe. */
class ActiveNoteTest extends AnyFunSuite with Matchers:

  test("midiToHz: A4=440, A3=220, A5=880 (temperamento equabile)") {
    ActiveNote.midiToHz(69) shouldBe (440.0 +- 0.001)
    ActiveNote.midiToHz(57) shouldBe (220.0 +- 0.001)
    ActiveNote.midiToHz(81) shouldBe (880.0 +- 0.001)
  }

  test("fromPayload: una nota diventa ActiveNote con frequenza giusta e ampiezza 1 di default") {
    val n = ActiveNote.fromPayload(Sound.NoteInText(Note("a", 4)), Nil)
    n.map(_.frequencyHz.round) shouldBe Some(440L)
    n.map(_.amplitude) shouldBe Some(1.0)
  }

  test("fromPayload: il gain diventa l'ampiezza") {
    val n = ActiveNote.fromPayload(Sound.NoteInText(Note("a", 4)), List(AudioEffect.Gain(0.5)))
    n.map(_.amplitude) shouldBe Some(0.5)
  }

  test("fromPayload: percussioni, pause ed effetti non producono note") {
    ActiveNote.fromPayload(Sound.SampleInText(Sample("bd")), Nil) shouldBe None
    ActiveNote.fromPayload(Sound.Rest(), Nil) shouldBe None
    ActiveNote.fromPayload(AudioEffect.Gain(1.0), Nil) shouldBe None
  }
