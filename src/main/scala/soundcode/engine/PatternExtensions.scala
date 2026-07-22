package soundcode.engine

import soundcode.domain.*

extension [T](pattern: Pattern[T])

  def cycleLength: Int = pattern match
    case Pattern.Atom(_) => 1
    case Pattern.Sequence(elements) => if elements.isEmpty then 1 else elements.map(_.cycleLength).product
    case Pattern.Parallel(layers) => if layers.isEmpty then 1 else layers.map(_.cycleLength).product
    case Pattern.Alternation(elements) =>
      val choices = elements.size
      val inner = if elements.isEmpty then 1 else elements.map(_.cycleLength).product
      choices * inner
    case Pattern.TimeWarp(_, inner) => inner.cycleLength
    case Pattern.WithExtensions(base, extensions) => (base :: extensions).map(_.cycleLength).product

  def resolve(using timeWindow: Interval): List[ScheduledEvent[T]] =
    PatternResolver.resolve(pattern)