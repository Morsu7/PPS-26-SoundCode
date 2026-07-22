package soundcode.engine.resolvers
import soundcode.domain.*
import soundcode.engine.*

object SequenceResolver:
  def resolve[T](pattern: Pattern.Sequence[T])(using timeWindow: Interval): List[ScheduledEvent[T]] =
    val elements = pattern.elements
    if elements.isEmpty then return Nil
    val n = elements.size
    val step = Fraction(1, n)

    val events = for
      cycle <- timeWindow.spanningCycles
      cycleStart = Fraction(cycle)
      (element, index) <- elements.zipWithIndex

      slotStart = cycleStart + (step * index)
      slot = Interval(slotStart, slotStart + step)

      overlap <- slot.intersect(timeWindow).toList
      zoomedInWindow = overlap.map(t => (t - slotStart) * n + cycleStart)

      childEvent <- element.resolve(using zoomedInWindow)

      finalEvent <- childEvent
        .mapTime(t => ((t - cycleStart) / n) + slotStart)
        .clipTo(overlap)
        .toList
    yield finalEvent

    events.toList