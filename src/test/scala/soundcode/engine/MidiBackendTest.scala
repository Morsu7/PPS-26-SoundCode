package soundcode.engine

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import soundcode.domain.*
import soundcode.audio.{AudioEngine, NoteSpec}

/** Verifica il dispatch di [[MidiBackend]] (nota -> numero MIDI, sample -> percussione GM)
  * senza toccare hardware audio: inietta un `AudioEngine` fittizio che registra le chiamate.
  * Interamente headless-safe.
  */
class MidiBackendTest extends AnyFunSuite with Matchers:

  private class RecordingEngine extends AudioEngine:
    var notes: List[NoteSpec] = Nil
    var drums: List[(Int, Long)] = Nil
    def playNote(spec: NoteSpec): Unit = notes = notes :+ spec
    def playDrum(gmNote: Int, velocity: Int, durationMs: Long): Unit = drums = drums :+ (gmNote, durationMs)
    def close(): Unit = ()

  private val pos = Some(TextPosition(0, 0))

  test("una nota viene tradotta nel numero MIDI e suonata come playNote") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, Nil)
    backend.triggerSound(Sound.NoteInText(Note("a", 4), pos), 500, Nil)
    engine.notes.map(_.midiNote) shouldBe List(60, 69)
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
    engine.notes.map(s => (s.midiNote, s.program, s.durationMs)) shouldBe List((60, 33, 500)) // bass -> program GM 33
  }

  test("senza estensione lo strumento e' il piano (program 0)") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, Nil)
    engine.notes.map(_.program) shouldBe List(0)
  }

  test("il gain scala la velocity: moltiplicatore sulla base 96, clampato 1..127 (story #3)") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, List(AudioEffect.Gain(ConfigInText(0.5))))
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, List(AudioEffect.Gain(ConfigInText(2.0))))
    engine.notes.map(_.velocity) shouldBe List(48, 127) // 96*0.5=48 ; 96*2=192 -> clamp 127
  }

  test("senza gain la velocity e' quella di default (96)") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, Nil)
    engine.notes.map(_.velocity) shouldBe List(96)
  }

  test("il pan 0..1 diventa CC10 0..127 (0=sinistra, 0.5=centro, 1=destra) (story #3)") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, List(AudioEffect.Pan(ConfigInText(0.0))))
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, List(AudioEffect.Pan(ConfigInText(0.5))))
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, List(AudioEffect.Pan(ConfigInText(1.0))))
    engine.notes.map(_.pan) shouldBe List(Some(0), Some(64), Some(127))
  }

  test("senza pan il campo resta None (centro)") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, Nil)
    engine.notes.map(_.pan) shouldBe List(None)
  }

  test("il room diventa CC91 (reverb) 0..127 (story #3)") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, List(AudioEffect.Room(ConfigInText(0.0))))
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, List(AudioEffect.Room(ConfigInText(0.8))))
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, List(AudioEffect.Room(ConfigInText(2.0))))
    engine.notes.map(_.reverb) shouldBe List(Some(0), Some(102), Some(127)) // 0.8*127=101.6->102 ; 2.0->clamp
  }

  test("il lpf (Hz) diventa CC74 (brightness): estremi del range su scala log (story #3)") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, List(AudioEffect.LowPass(ConfigInText(20.0))))
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, List(AudioEffect.LowPass(ConfigInText(20000.0))))
    engine.notes.map(_.brightness) shouldBe List(Some(0), Some(127))
  }

  test("un cutoff piu' alto produce una brightness maggiore (monotonia)") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, List(AudioEffect.LowPass(ConfigInText(200.0))))
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, List(AudioEffect.LowPass(ConfigInText(2000.0))))
    val bs = engine.notes.flatMap(_.brightness)
    bs(0) should be < bs(1)
  }

  test("senza room/lpf i campi reverb e brightness restano None") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.triggerSound(Sound.NoteInText(Note("c", 4), pos), 500, Nil)
    engine.notes.map(s => (s.reverb, s.brightness)) shouldBe List((None, None))
  }
