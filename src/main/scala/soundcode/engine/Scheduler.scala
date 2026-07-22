package soundcode.engine

import soundcode.domain.*

trait Scheduler:
  def generateBoundedTimelines(patterns: List[Pattern[AudioPayload]]): List[Seq[ScheduledEvent[AudioPayload]]]
  def generateInfiniteTimeline(patterns: List[Pattern[AudioPayload]]): LazyList[ScheduledEvent[AudioPayload]]

object SchedulerImpl extends Scheduler:

  def generateBoundedTimelines(patterns: List[Pattern[AudioPayload]]): List[Seq[ScheduledEvent[AudioPayload]]] =
    patterns.map { pat =>
      val totalCycles = pat.cycleLength
      (0 until totalCycles).flatMap{n =>
        given Interval = Interval(Fraction(n), Fraction(n + 1))
        pat.resolve
      }.toList
    }

  def generateInfiniteTimeline(patterns: List[Pattern[AudioPayload]]): LazyList[ScheduledEvent[AudioPayload]] =
    def loop(nCycle: Int): LazyList[ScheduledEvent[AudioPayload]] =
      given Interval = Interval(Fraction(nCycle), Fraction(nCycle + 1))
      val cycleEvents = patterns.flatMap(p => p.resolve).sortBy(_.part.start.toDouble)
      LazyList.from(cycleEvents) #::: loop(nCycle + 1)
    loop(0)