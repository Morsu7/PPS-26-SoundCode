package soundcode.engine

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import soundcode.domain.*
import soundcode.engine.support.*

class AudioPlayerTest extends AnyFunSuite with Matchers {

  test("bd hh sn hh") {
    val runner = new TestRunner()
    runner.load(seq(bd, hh, sn, hh))
    val events = runner.playCycle(0)

    events should have size 4
    events should contain theSameElementsInOrderAs List(
      PlayedEvent("bd", 0L, 500L),
      PlayedEvent("hh", 500L, 500L),
      PlayedEvent("sn", 1000L, 500L),
      PlayedEvent("hh", 1500L, 500L)
    )
  }

  test("sound(\"bd hh\").note(\"c f g\")") {
    val runner = new TestRunner()
    runner.load(ext(seq(bd, hh), seq(c4, f4, g4)))
    val events = runner.playCycle(0)

    events should contain theSameElementsInOrderAs List(
      PlayedEvent("bd", 0L, 1000L, List("C")),
      PlayedEvent("hh", 1000L, 1000L, List("F"))
    )
  }

  test("bd hh sn hh [hh , sn < bd hh > ] (Due Cicli)") {
    val runner = new TestRunner()
    runner.load(seq(bd, hh, sn, hh, par(hh, seq(sn, alt(bd, hh)))))

    // --- CICLO 0 ---
    val cycle0 = runner.playCycle(0)
    cycle0 should have size 7
    cycle0 should contain theSameElementsAs List(
      PlayedEvent("bd", 0L, 400L),
      PlayedEvent("hh", 400L, 400L),
      PlayedEvent("sn", 800L, 400L),
      PlayedEvent("hh", 1200L, 400L),
      PlayedEvent("hh", 1600L, 400L),
      PlayedEvent("sn", 1600L, 200L),
      PlayedEvent("bd", 1800L, 200L)
    )

    // --- CICLO 1 ---
    val cycle1 = runner.playCycle(1)
    cycle1 should have size 7
    cycle1 should contain theSameElementsAs List(
      PlayedEvent("bd", 2000L, 400L),
      PlayedEvent("hh", 2400L, 400L),
      PlayedEvent("sn", 2800L, 400L),
      PlayedEvent("hh", 3200L, 400L),
      PlayedEvent("hh", 3600L, 400L),
      PlayedEvent("sn", 3600L, 200L),
      PlayedEvent("hh", 3800L, 200L)
    )
  }

  test("Scalabilità del tempo: cps = 1.0 (1 secondo per ciclo)") {
    val runner = new TestRunner(cps = 1.0)
    runner.load(seq(bd, hh, sn, hh))
    val events = runner.playCycle(0)

    events should have size 4
    events should contain theSameElementsInOrderAs List(
      PlayedEvent("bd", 0L, 250L),
      PlayedEvent("hh", 250L, 250L),
      PlayedEvent("sn", 500L, 250L),
      PlayedEvent("hh", 750L, 250L)
    )
  }

  test("Scalabilità estrema: cps = 2.0 (Mezzo secondo per ciclo)") {
    val runner = new TestRunner(cps = 2.0)
    runner.load(seq(bd, hh, sn, hh))
    val events = runner.playCycle(0)

    events should contain theSameElementsInOrderAs List(
      PlayedEvent("bd", 0L, 125L),
      PlayedEvent("hh", 125L, 125L),
      PlayedEvent("sn", 250L, 125L),
      PlayedEvent("hh", 375L, 125L)
    )
  }

  test("Propagazione degli Effetti (Gain e Room)") {
    val runner = new TestRunner()
    runner.load(ext(seq(bd, sn, hh), seq(gain(3.0), gain(5.0)), room(6.0)))
    val events = runner.playCycle(0)

    events should contain theSameElementsInOrderAs List(
      PlayedEvent("bd", 0L, 667L, List("gain(3.0)", "room(6.0)")),
      PlayedEvent("sn", 666L, 667L, List("gain(3.0)", "room(6.0)")),
      PlayedEvent("hh", 1333L, 667L, List("gain(5.0)", "room(6.0)"))
    )
  }

  case class PlayedEvent(name: String, triggerTimeMs: Long, durationMs: Long, extensions: List[String] = Nil)
  
  class TestAudioBackend extends AudioBackend {
    var playedEvents: List[PlayedEvent] = Nil
    var simulatedNowMs: Long = 0L

    override def triggerSound(payload: AudioPayload, durationMs: Long, extensions: List[AudioPayload]): Unit = {
      def extractName(p: AudioPayload): String = p match {
        case Sound.SampleInText(s, _) => s.value
        case Sound.NoteInText(n, _) => n.name
        case AudioEffect.Gain(v) => s"gain(${v.value})"
        case AudioEffect.Room(v) => s"room(${v.value})"
        case _ => "unknown"
      }

      val name = extractName(payload)
      val extNames = extensions.map(extractName)
      playedEvents = playedEvents :+ PlayedEvent(name, simulatedNowMs, durationMs, extNames)
    }
  }


  class TestRunner(cps: Double = 0.5) {
    val backend = new TestAudioBackend()


    val audioPlayer = new AudioPlayer(Tempo(cps), backend, _ => ())

    def load(pattern: Pattern[AudioPayload]): Unit = {
      val stream = SchedulerImpl.generateInfiniteTimeline(List(pattern))

      audioPlayer.updateTimeline(stream)
    }

    def playCycle(cycleIndex: Int): List[PlayedEvent] = {
      val cycleDurationMs = Tempo(cps).cycleDurationMs.toLong
      val startMs = cycleIndex * cycleDurationMs
      val endMs = startMs + cycleDurationMs - 1

      for (t <- startMs to endMs) {
        backend.simulatedNowMs = t
        audioPlayer.tick(AbsoluteTime(t))
      }

      val events = backend.playedEvents
      backend.playedEvents = Nil
      events
    }
  }
}