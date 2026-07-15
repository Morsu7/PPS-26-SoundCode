package soundcode.engine

import soundcode.domain.*

trait Scheduler:
  def generateBoundedTimelines(patterns: List[Pattern[AudioPayload]]): List[Timeline[AudioPayload]]
  def generateInfiniteTimeline(patterns: List[Pattern[AudioPayload]]): LazyList[ScheduledEvent[AudioPayload]]

object SchedulerImpl extends Scheduler:

  def generateBoundedTimelines(patterns: List[Pattern[AudioPayload]]): List[Timeline[AudioPayload]] =
    patterns.map { pat =>
      val loopLength = CycleCalculator.lengthOf(pat)

      val events = 
        (0 until loopLength)
          .flatMap { cycle => 
            pat.resolve(
              Interval(
                Fraction(cycle),
                Fraction(cycle + 1)
              )
            )
          }
          .toList

      Timeline(
        events = events,
        loopLength = Fraction(loopLength)
      )
    }

  def generateInfiniteTimeline(patterns: List[Pattern[AudioPayload]]): LazyList[ScheduledEvent[AudioPayload]] =
    def loop(nCycle: Int): LazyList[ScheduledEvent[AudioPayload]] =
      val cycleEvents = patterns.flatMap(p => p.resolve(Interval(Fraction(nCycle), Fraction(nCycle + 1)))).sortBy(_.part.start.toDouble)
      LazyList.from(cycleEvents) #::: loop(nCycle + 1)
    loop(0)