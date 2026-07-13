package soundcode.engine

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import soundcode.domain.*
import soundcode.interpreter.interpret
import soundcode.audio.AudioEngine

/** Verifica il dispatch di `MidiAudioPlayer.triggerSound` (nota -> numero MIDI,
  * sample -> percussione GM) senza toccare hardware audio: inietta un `AudioEngine`
  * fittizio che registra le chiamate. Interamente headless-safe.
  */
/*class MidiAudioPlayerDispatchTest extends AnyFunSuite with Matchers {

  given Scheduler = SchedulerImpl

  class RecordingEngine extends AudioEngine {
    var notes: List[(Int, Long)] = Nil
    var drums: List[(Int, Long)] = Nil
    def playNote(midiNote: Int, velocity: Int, durationMs: Long): Unit =
      notes = notes :+ (midiNote, durationMs)
    def playDrum(gmNote: Int, velocity: Int, durationMs: Long): Unit =
      drums = drums :+ (gmNote, durationMs)
    def close(): Unit = ()
  }

  private def playCycle(player: MidiAudioPlayer, cycleIndex: Int, cps: Double = 0.5): Unit =
    val cycleDurationMs = (1000.0 / cps).toLong
    val startMs = cycleIndex * cycleDurationMs
    val endMs = startMs + cycleDurationMs - 1
    for (t <- startMs to endMs) player.tick(AbsoluteTime(t))

  test("samples are dispatched as General MIDI drum notes") {
    val engine = new RecordingEngine
    val player = new MidiAudioPlayer(0.5, engine)
    player.updateTimeline(interpret("sound(\"bd hh sn hh\")"))
    playCycle(player, 0)

    engine.drums.map(_._1) shouldBe List(36, 42, 38, 42)
    engine.drums.map(_._2) shouldBe List(500L, 500L, 500L, 500L)
    engine.notes shouldBe empty
  }

  test("notes are dispatched as MIDI note numbers (c e g -> 60 64 67)") {
    val engine = new RecordingEngine
    val player = new MidiAudioPlayer(0.5, engine)
    player.updateTimeline(interpret("note(\"c e g\")"))
    playCycle(player, 0)

    engine.notes.map(_._1) shouldBe List(60, 64, 67)
    engine.drums shouldBe empty
  }

  test("unknown sample produces no sound") {
    val engine = new RecordingEngine
    val player = new MidiAudioPlayer(0.5, engine)
    player.updateTimeline(interpret("sound(\"piano\")"))
    playCycle(player, 0)

    engine.notes shouldBe empty
    engine.drums shouldBe empty
  }
}*/
