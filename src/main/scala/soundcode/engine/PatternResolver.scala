package soundcode.engine

import soundcode.domain.*
import soundcode.engine.resolvers.*


/** Nodo centrale per il calcolo delle tempistiche dei singoli pattern.
 *
 * Riceve un [[Pattern]] generico e lo valuta all'interno di una specifica finestra temporale ([[Interval]]),
 * espandendolo in una lista piatta di eventi pronti per lo scheduling.
 *
 * Agisce come dispatcher: riconosce la struttura del pattern (se è un atomo, una sequenza, ecc.)
 * e delega il calcolo delle proporzioni e delle intersezioni temporali al resolver di competenza.
 */
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