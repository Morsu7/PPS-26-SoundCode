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
    var notes: List[(Int, Int, Long)] = Nil // (midiNote, program, durationMs)
    var drums: List[(Int, Long)] = Nil
    def playNote(midiNote: Int, velocity: Int, durationMs: Long, program: Int): Unit = notes = notes :+ (midiNote, program, durationMs)
    def playDrum(gmNote: Int, velocity: Int, durationMs: Long): Unit = drums = drums :+ (gmNote, durationMs)
    def close(): Unit = ()

  private val pos = Some(TextPosition(0, 0))

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

  test("un sample sconosciuto come base viene ignorato") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.SampleInText(Sample("xyz"), pos), 250, Nil)
    engine.notes shouldBe empty
    engine.drums shouldBe empty
  }

  test("una nota con estensione sound seleziona lo strumento GM (story #2)") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    // note("c").sound("bass") -> la nota porta un SampleInText("bass") come estensione
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, List(Sound.SampleInText(Sample("bass"), pos)))
    engine.notes shouldBe List((60, 33, 500)) // bass -> program GM 33
  }

  test("senza estensione lo strumento e' il piano (program 0)") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, Nil)
    engine.notes.map(_._2) shouldBe List(0)
  }
