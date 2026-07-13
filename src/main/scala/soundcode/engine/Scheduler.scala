package soundcode.engine

import soundcode.domain.*

trait Scheduler:
  def generateBoundedTimelines(patterns: List[Pattern[AudioPayload]]): List[Seq[ScheduledEvent[AudioPayload]]]
  def generateInfiniteTimeline(patterns: List[Pattern[AudioPayload]]): LazyList[ScheduledEvent[AudioPayload]]

object SchedulerImpl extends Scheduler:

  def generateBoundedTimelines(patterns: List[Pattern[AudioPayload]]): List[Seq[ScheduledEvent[AudioPayload]]] =
    patterns.map { pat =>
      val totalCycles = CycleCalculator.lengthOf(pat)
      (0 until totalCycles).flatMap(n => PatternResolver.query(pat, Interval(Fraction(n), Fraction((n + 1))))).toList
    }

  def generateInfiniteTimeline(patterns: List[Pattern[AudioPayload]]): LazyList[ScheduledEvent[AudioPayload]] =
    def loop(nCycle: Int): LazyList[ScheduledEvent[AudioPayload]] =
      val cycleEvents = patterns.flatMap(p => PatternResolver.query(p, Interval(Fraction(nCycle), Fraction((nCycle + 1))))).sortBy(_.part.start.toDouble)
      LazyList.from(cycleEvents) #::: loop(nCycle + 1)
    loop(0)