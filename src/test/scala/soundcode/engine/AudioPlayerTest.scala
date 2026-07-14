package soundcode.engine

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import soundcode.domain.*
import soundcode.engine.support.*

class AudioPlayerTest extends AnyFunSuite with Matchers {

  test("bd hh sn hh") {
    val pattern = seq(bd, hh, sn, hh)
    val player = setupPlayer(pattern)
    val events = playCycle(player, 0)

    events should have size 4
    events should contain theSameElementsInOrderAs List(
      PlayedEvent("bd", 0L, 500L),
      PlayedEvent("hh", 500L, 500L),
      PlayedEvent("sn", 1000L, 500L),
      PlayedEvent("hh", 1500L, 500L)
    )
  }

  test("sound(\"bd hh\").note(\"c f g\")") {
    // Il bd dura [0, 0.5]. Le note durano [0, 0.333], [0.333, 0.667], [0.667, 1.0]
    // Nuova logica esatta:
    // bd (0 -> 0.5) tocca c4 (0 -> 0.333) e f4 (0.333 -> 0.667)
    // hh (0.5 -> 1.0) tocca f4 (0.333 -> 0.667) e g4 (0.667 -> 1.0)
    val pattern = ext(seq(bd, hh), seq(c4, f4, g4))
    val player = setupPlayer(pattern)
    val events = playCycle(player, 0)

    events should contain theSameElementsInOrderAs List(
      PlayedEvent("bd", 0L, 1000L, List("C")),
      PlayedEvent("hh", 1000L, 1000L, List("F"))
    )
  }

  test("bd hh sn hh [hh , sn < bd hh > ] (Due Cicli)") {
    val pattern = seq(bd, hh, sn, hh, par(hh, seq(sn, alt(bd, hh))))
    val player = setupPlayer(pattern)

    // --- CICLO 0 ---
    val cycle0 = playCycle(player, 0)
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
    val cycle1 = playCycle(player, 1)
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
    val myCps = 1.0
    val pattern = seq(bd, hh, sn, hh)
    val player = setupPlayer(pattern, cps = myCps)
    val events = playCycle(player, 0, myCps)

    events should have size 4
    events should contain theSameElementsInOrderAs List(
      PlayedEvent("bd", 0L, 250L),
      PlayedEvent("hh", 250L, 250L),
      PlayedEvent("sn", 500L, 250L),
      PlayedEvent("hh", 750L, 250L)
    )
  }

  test("Scalabilità estrema: cps = 2.0 (Mezzo secondo per ciclo)") {
    val myCps = 2.0
    val pattern = seq(bd, hh, sn, hh)
    val player = setupPlayer(pattern, cps = myCps)
    val events = playCycle(player, 0, myCps)

    events should contain theSameElementsInOrderAs List(
      PlayedEvent("bd", 0L, 125L),
      PlayedEvent("hh", 125L, 125L),
      PlayedEvent("sn", 250L, 125L),
      PlayedEvent("hh", 375L, 125L)
    )
  }

  test("Propagazione degli Effetti (Gain e Room)") {
    val pattern = ext(seq(bd, sn, hh), seq(gain(3.0), gain(5.0)), room(6.0))
    val player = setupPlayer(pattern)
    val events = playCycle(player, 0)

    events should contain theSameElementsInOrderAs List(
      PlayedEvent("bd", 0L, 667L, List("gain(3.0)", "room(6.0)")),
      PlayedEvent("sn", 666L, 667L, List("gain(3.0)", "room(6.0)")),
      PlayedEvent("hh", 1333L, 667L, List("gain(5.0)", "room(6.0)"))
    )
  }

  case class PlayedEvent(name: String, triggerTimeMs: Long, durationMs: Long, extensions: List[String] = Nil)

  class TestableAudioPlayer(tempo: Tempo) extends AudioPlayer(tempo) {
    var playedEvents: List[PlayedEvent] = Nil
    var simulatedNow: AbsoluteTime = AbsoluteTime(0L)

    override def tick(now: AbsoluteTime): Unit = {
      simulatedNow = now
      super.tick(now)
    }

    override protected def triggerSound(payload: AudioPayload, durationMs: Long, extensions: List[AudioPayload]): Unit = {
      def extractName(p: AudioPayload): String = p match {
        case Sound.SampleInText(s, _) => s.value
        case Sound.NoteInText(n, _) => n.name
        case AudioEffect.Gain(v) => s"gain($v)"
        case AudioEffect.Room(v) => s"room($v)"
        case _ => "unknown"
      }

      val name = extractName(payload)
      val extNames = extensions.map(extractName)
      playedEvents = playedEvents :+ PlayedEvent(name, simulatedNow.toLong, durationMs, extNames)
    }
  }

  def setupPlayer(pattern: Pattern[AudioPayload], cps: Double = 0.5): TestableAudioPlayer = {
    val player = new TestableAudioPlayer(Tempo(cps))
    // Generiamo lo stream passando il pattern in una List, come richiesto dal nuovo generatore
    val playerStream = SchedulerImpl.generateInfiniteTimeline(List(pattern))
    player.updateTimeline(playerStream)
    player
  }

  def playCycle(player: TestableAudioPlayer, cycleIndex: Int, cps: Double = 0.5): List[PlayedEvent] = {
    val tempo = Tempo(cps)
    val cycleDurationMs = tempo.cycleDurationMs.toLong

    val startMs = cycleIndex * cycleDurationMs
    val endMs = startMs + cycleDurationMs - 1

    for (t <- startMs to endMs) {
      player.tick(AbsoluteTime(t))
    }

    val events = player.playedEvents
    player.playedEvents = Nil
    events
  }
}