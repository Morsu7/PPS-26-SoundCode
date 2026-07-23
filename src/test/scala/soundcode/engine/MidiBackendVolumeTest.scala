package soundcode.engine

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import soundcode.audio.{AudioEngine, NoteSpec}

/** Il MidiBackend deve inoltrare il volume master al motore audio (headless-safe). */
class MidiBackendVolumeTest extends AnyFunSuite with Matchers:

  private class RecordingEngine extends AudioEngine:
    var volumes: List[Double] = Nil
    def playNote(spec: NoteSpec): Unit = ()
    def playDrum(gmNote: Int, velocity: Int, durationMs: Long): Unit = ()
    def close(): Unit = ()
    override def setVolume(level: Double): Unit = volumes = volumes :+ level

  test("MidiBackend.setVolume inoltra il livello al motore audio") {
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    backend.setVolume(42.0)
    engine.volumes shouldBe List(42.0)
  }
