package soundcode.engine.support

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import soundcode.domain.*
import soundcode.engine.*

// Adattatore da Evento Schedulato a Evento Atteso
extension (e: ScheduledEvent[AudioPayload])
  def toExp: ExpectedEvent =
    def name(p: AudioPayload): String = p match
      case Sound.SampleInText(s, _) => s.value
      case Sound.NoteInText(n, _)   => n.toString // Stampa 'C4' (molto più leggibile nei test!)
      case Sound.Rest(_)            => "~"
      case AudioEffect.Gain(v)      => v.value.toString
      case AudioEffect.Room(v)      => v.value.toString
      case AudioEffect.Pan(v)       => v.value.toString
      case _                        => "effect"

    ExpectedEvent(
      name(e.value),
      e.part.start -> e.part.end,
      e.whole.start -> e.whole.end,
      e.appliedExtensions.map(name)
    )

trait SchedulerTestBase extends AnyFunSuite with Matchers:

  def check(patterns: Pattern[AudioPayload]*): PatternChecker = PatternChecker(patterns.toList)

  class PatternChecker(patterns: List[Pattern[AudioPayload]]):
    def inCycle(c: Int)(expected: ExpectedEvent*): Unit =
      resolve(c) should contain theSameElementsInOrderAs expected

    def inCycleUnordered(c: Int)(expected: ExpectedEvent*): Unit =
      resolve(c) should contain theSameElementsAs expected

    private def resolve(c: Int): List[ExpectedEvent] =
      SchedulerImpl.generateInfiniteTimeline(patterns)
        .dropWhile(_.part.start < (c \ 1))
        .takeWhile(_.part.start < ((c + 1) \ 1))
        .map(_.toExp).toList