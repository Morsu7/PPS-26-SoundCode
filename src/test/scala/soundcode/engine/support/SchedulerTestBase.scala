package soundcode.engine.support

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import soundcode.domain.*
import soundcode.engine.*

trait SchedulerTestBase extends AnyFunSuite with Matchers {

  def resolve(patterns: List[Pattern[AudioPayload]], cycle: Int): List[ExpEvent] =
    val cycleStart = cycle \ 1
    val cycleEnd = (cycle + 1) \ 1

    SchedulerImpl.generateInfiniteTimeline(patterns)
      .dropWhile(_.part.start < cycleStart)
      .takeWhile(_.part.start < cycleEnd)
      .map(_.toExp)
      .toList

  def assertCycle(streams: List[Pattern[AudioPayload]], cycle: Int)(expected: ExpEvent*): Unit =
    resolve(streams, cycle) should contain theSameElementsInOrderAs expected.toList

  def assertCycleUnordered(streams: List[Pattern[AudioPayload]], cycle: Int)(expected: ExpEvent*): Unit =
    resolve(streams, cycle) should contain theSameElementsAs expected.toList

  def assertBoundedTrack(viewData: Seq[Seq[ScheduledEvent[AudioPayload]]], trackIndex: Int, expectedDuration: Fraction)(expected: ExpEvent*): Unit =
    val track = viewData(trackIndex)
    track.last.part.end shouldBe expectedDuration
    track.map(_.toExp).toList should contain theSameElementsInOrderAs expected.toList
}

case class ExpEvent(element: String, start: Fraction, end: Fraction, extensions: List[String] = Nil):
  override def toString: String = {
    val extStr = if (extensions.isEmpty) "" else s" + [${extensions.mkString(", ")}]"
    s"('$element' @ $start -> $end$extStr)"
  }

extension (e: ScheduledEvent[AudioPayload])
  def toExp: ExpEvent =
    def extractName(payload: AudioPayload): String = payload match
      case Sound.SampleInText(s, _) => s.value
      case Sound.NoteInText(n, _) => n.value
      case Sound.Rest(_) => ""
      case AudioEffect.Gain(v) => v.toString
      case AudioEffect.Room(v) => v.toString
      case AudioEffect.Pan(v) => v.toString
      case _ => "unknown"

    val name = extractName(e.value)
    val exts = e.appliedExtensions.map(extractName)

    ExpEvent(name, e.part.start, e.part.end, exts)
