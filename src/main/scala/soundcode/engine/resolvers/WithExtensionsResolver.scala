package soundcode.engine.resolvers
import soundcode.domain.*
import soundcode.engine.*

object WithExtensionsResolver:
  def resolve(pattern: Pattern.WithExtensions)(using timeWindow: Interval): List[ScheduledEvent[AudioPayload]] =
    pattern.base.resolve.map { baseEvent =>
      val activeExts = pattern.extensions
        .flatMap(ext => ext.resolve(using baseEvent.part))
        .filter(e => e.part.start <= baseEvent.part.start && e.part.end > baseEvent.part.start)
        .map(_.value)
      baseEvent.copy(appliedExtensions = baseEvent.appliedExtensions ++ activeExts)
    }