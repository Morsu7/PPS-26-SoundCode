package soundcode.engine

import soundcode.domain.*

object CycleCalculator:

  def lengthOf[T](pattern: Pattern[T]): Int = pattern match
    case Pattern.Atom(_) => 1

    case Pattern.Sequence(elements) =>
      if elements.isEmpty then 1 else elements.map(lengthOf).product

    case Pattern.Parallel(layers) =>
      if layers.isEmpty then 1 else layers.map(lengthOf).product

    case Pattern.Alternation(elements) =>
      val choices = elements.size
      val inner = if elements.isEmpty then 1 else elements.map(lengthOf).product
      choices * inner

    case Pattern.TimeWarp(_, inner) =>
      lengthOf(inner)

    case Pattern.WithExtensions(base, extensions) =>
      (base :: extensions).map(lengthOf).product