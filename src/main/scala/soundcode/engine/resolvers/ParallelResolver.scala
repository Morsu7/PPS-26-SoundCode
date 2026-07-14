package soundcode.engine.resolvers
import soundcode.domain.*
import soundcode.engine.*

object ParallelResolver:
  def resolve[T](pattern: Pattern.Parallel[T], timeWindow: Interval): List[ScheduledEvent[T]] =
    pattern.layers.flatMap(layer => layer.resolve(timeWindow))