package soundcode.engine

import soundcode.domain.*
import soundcode.engine.resolvers.*

object PatternResolver:
  def resolve[T](pattern: Pattern[T], timeWindow: Interval): List[ScheduledEvent[T]] =
    if timeWindow.start >= timeWindow.end then Nil
    else pattern match
      case p: Pattern.Atom[_]        => AtomResolver.resolve(p.asInstanceOf[Pattern.Atom[T]], timeWindow)
      case p: Pattern.Sequence[_]    => SequenceResolver.resolve(p.asInstanceOf[Pattern.Sequence[T]], timeWindow)
      case p: Pattern.Parallel[_]    => ParallelResolver.resolve(p.asInstanceOf[Pattern.Parallel[T]], timeWindow)
      case p: Pattern.Alternation[_] => AlternationResolver.resolve(p.asInstanceOf[Pattern.Alternation[T]], timeWindow)
      case p: Pattern.TimeWarp[_]    => TimeWarpResolver.resolve(p.asInstanceOf[Pattern.TimeWarp[T]], timeWindow)
      case p: Pattern.WithExtensions => WithExtensionsResolver.resolve(p, timeWindow)

extension [T](pattern: Pattern[T])
  def resolve(timeWindow: Interval): List[ScheduledEvent[T]] =
    PatternResolver.resolve(pattern, timeWindow)


/*package soundcode.engine

import soundcode.domain.*
import soundcode.engine.resolvers.given
import scala.deriving.Mirror
import scala.compiletime.{erasedValue, summonInline}
import scala.reflect.ClassTag

trait PatternResolver[P]:
  def resolve[T](pattern: P, timeWindow: Interval): List[ScheduledEvent[T]]

object PatternResolver:

  private inline def summonAll[Elems <: Tuple]: List[(Class[?], PatternResolver[Any])] =
    inline erasedValue[Elems] match
      case _: EmptyTuple => Nil
      case _: (h *: t) =>
        val resolver  = summonInline[PatternResolver[h]].asInstanceOf[PatternResolver[Any]]
        val classTag  = summonInline[ClassTag[h]]
        (classTag.runtimeClass, resolver) :: summonAll[t]

  private inline def buildRegistry(using m: Mirror.SumOf[Pattern[Any]]): Map[Class[?], PatternResolver[Any]] =
    summonAll[m.MirroredElemTypes].toMap

  private val registry: Map[Class[?], PatternResolver[Any]] = buildRegistry

  private def lookup(pattern: Any): PatternResolver[Any] =
    registry.getOrElse(
      pattern.getClass,
      throw new NoSuchElementException(s"Nessun PatternResolver registrato per ${pattern.getClass.getSimpleName}")
    )

  given dispatcher: PatternResolver[Pattern[?]] with
    def resolve[T](pattern: Pattern[?], timeWindow: Interval): List[ScheduledEvent[T]] =
      lookup(pattern).resolve(pattern, timeWindow)

extension [T](pattern: Pattern[T])
  def resolve(timeWindow: Interval)(using res: PatternResolver[Pattern[?]]): List[ScheduledEvent[T]] =
    if timeWindow.start >= timeWindow.end then Nil
    else res.resolve(pattern, timeWindow)*/