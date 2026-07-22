package soundcode.engine

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import soundcode.domain.*
import soundcode.engine.support.*

/** Verifica che l'AudioPlayer emetta, sul suo clock, il buffer di note attive (hook onActiveNotes). */
class ActiveNotesPlaybackTest extends AnyFunSuite with Matchers:

  private class SilentBackend extends AudioBackend:
    def triggerSound(payload: AudioPayload, durationMs: Long, extensions: List[AudioPayload]): Unit = ()

  test("onActiveNotes riceve le note melodiche in suono con la frequenza corretta") {
    var seen = Set.empty[Long]
    val player = new AudioPlayer(
      Tempo(0.5),
      new SilentBackend,
      _ => (),
      notes => seen ++= notes.map(_.frequencyHz.round)
    )
    player.updateTimeline(SchedulerImpl.generateInfiniteTimeline(List(seq(c4, e4, g4))))

    val cycleMs = Tempo(0.5).cycleDurationMs.toLong
    for t <- 0L to (cycleMs - 1) do player.tick(AbsoluteTime(t))

    // c4=60->~262 Hz, e4=64->~330 Hz, g4=67->~392 Hz
    seen should contain allOf (262L, 330L, 392L)
  }

  test("le percussioni non producono note nel buffer") {
    var sawNonEmpty = false
    val player = new AudioPlayer(
      Tempo(0.5),
      new SilentBackend,
      _ => (),
      notes => if notes.nonEmpty then sawNonEmpty = true
    )
    player.updateTimeline(SchedulerImpl.generateInfiniteTimeline(List(seq(bd, hh)))) // solo percussioni

    val cycleMs = Tempo(0.5).cycleDurationMs.toLong
    for t <- 0L to (cycleMs - 1) do player.tick(AbsoluteTime(t))

    sawNonEmpty shouldBe false
  }
