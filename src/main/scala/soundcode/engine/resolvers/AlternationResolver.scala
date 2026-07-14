package soundcode.engine.resolvers
import soundcode.domain.*
import soundcode.engine.*

object AlternationResolver:
  def resolve[T](pattern: Pattern.Alternation[T], timeWindow: Interval): List[ScheduledEvent[T]] =
    val elements = pattern.elements
    if elements.isEmpty then return Nil

    val events = for cycle <- timeWindow.spanningCycles yield
      val cycleStart = Fraction(cycle)
      Interval(cycleStart, cycleStart + 1).intersect(timeWindow).toList.flatMap { activeWindow =>
        val activeIndex = (cycle.abs % elements.size).toInt
        val timeOffset = Fraction(cycle - (cycle / elements.size))

        elements(activeIndex)
          .resolve(activeWindow.map(_ - timeOffset))
          .map(_.mapTime(_ + timeOffset))
      }

    events.toList.flatten