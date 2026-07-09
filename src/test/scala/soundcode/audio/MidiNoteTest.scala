package soundcode.audio

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MidiNoteTest extends AnyFunSuite with Matchers {

  test("middle C is 60 and A440 is 69") {
    MidiNote.toMidi("c4") shouldBe Some(60)
    MidiNote.toMidi("a4") shouldBe Some(69)
  }

  test("missing octave defaults to 4") {
    MidiNote.toMidi("a") shouldBe Some(69)
    MidiNote.toMidi("c") shouldBe Some(60)
  }

  test("octaves shift by 12 semitones") {
    MidiNote.toMidi("c5") shouldBe Some(72)
    MidiNote.toMidi("c3") shouldBe Some(48)
  }

  test("'#' and 's' are equivalent sharps") {
    MidiNote.toMidi("c#4") shouldBe Some(61)
    MidiNote.toMidi("cs4") shouldBe Some(61)
    MidiNote.toMidi("f#5") shouldBe Some(78)
    MidiNote.toMidi("fs5") shouldBe Some(78)
  }

  test("flats lower by one semitone") {
    MidiNote.toMidi("eb3") shouldBe Some(51) // enarmonico di d#3
    MidiNote.toMidi("bb3") shouldBe Some(58)
    MidiNote.toMidi("b3") shouldBe Some(59)
  }

  test("input is trimmed and case-insensitive") {
    MidiNote.toMidi("  C4 ") shouldBe Some(60)
    MidiNote.toMidi("F#5") shouldBe Some(78)
  }

  test("invalid or out-of-range input returns None") {
    MidiNote.toMidi("h4") shouldBe None
    MidiNote.toMidi("") shouldBe None
    MidiNote.toMidi("cx4") shouldBe None
    MidiNote.toMidi("c99") shouldBe None
  }
}
