package soundcode.engine.resolvers
import soundcode.domain.*

object AtomResolver:
  def resolve[T](pattern: Pattern.Atom[T], timeWindow: Interval): List[ScheduledEvent[T]] =
    List(ScheduledEvent(whole = timeWindow, part = timeWindow, value = pattern.value))