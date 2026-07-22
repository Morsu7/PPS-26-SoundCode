package soundcode.engine

import soundcode.domain.*
import soundcode.engine.resolvers.*

object PatternResolver:
  def resolve[T](pattern: Pattern[T])(using timeWindow: Interval): List[ScheduledEvent[T]] =
    if timeWindow.start >= timeWindow.end then Nil
    else pattern match
      case a @ Pattern.Atom(_)                => AtomResolver.resolve(a)
      case s @ Pattern.Sequence(_)            => SequenceResolver.resolve(s)
      case p @ Pattern.Parallel(_)            => ParallelResolver.resolve(p)
      case alt @ Pattern.Alternation(_)       => AlternationResolver.resolve(alt)
      case tw @ Pattern.TimeWarp(_, _)        => TimeWarpResolver.resolve(tw)
      case p: Pattern.WithExtensions => WithExtensionsResolver.resolve(p)