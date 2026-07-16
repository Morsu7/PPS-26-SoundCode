package soundcode.engine

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import soundcode.domain.*
import soundcode.audio.AudioEngine

/** Verifica il dispatch di [[MidiBackend]] (nota -> numero MIDI, sample -> percussione GM)
  * senza toccare hardware audio: inietta un `AudioEngine` fittizio che registra le chiamate.
  * Interamente headless-safe.
  */
class MidiBackendTest extends AnyFunSuite with Matchers:

  private class RecordingEngine extends AudioEngine:
    var notes: List[(Int, Long)] = Nil
    var drums: List[(Int, Long)] = Nil
    def playNote(midiNote: Int, velocity: Int, durationMs: Long): Unit = notes = notes :+ (midiNote, durationMs)
    def playDrum(gmNote: Int, velocity: Int, durationMs: Long): Unit = drums = drums :+ (gmNote, durationMs)
    def close(): Unit = ()

  private val pos = TextPosition(0, 0)

  test("una nota viene tradotta nel numero MIDI e suonata come playNote") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, Nil)
    backend.triggerSound(Sound.NoteInText(Note("a", 4), pos), 500, Nil)
    engine.notes.map(_._1) shouldBe List(60, 69)
    engine.drums shouldBe empty
  }

  test("un sample di batteria viene tradotto nel numero GM e suonato come playDrum") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.SampleInText(Sample("bd"), pos), 250, Nil)
    backend.triggerSound(Sound.SampleInText(Sample("hh"), pos), 250, Nil)
    engine.drums.map(_._1) shouldBe List(36, 42)
    engine.notes shouldBe empty
  }

  test("una pausa (Rest) non produce suono") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.Rest(pos), 250, Nil)
    engine.notes shouldBe empty
    engine.drums shouldBe empty
  }

  test("un sample sconosciuto viene ignorato") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.SampleInText(Sample("piano"), pos), 250, Nil)
    engine.notes shouldBe empty
    engine.drums shouldBe empty
  }
