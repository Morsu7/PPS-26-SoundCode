package soundcode.engine.resolvers
import soundcode.domain.*
import soundcode.engine.*

object WithExtensionsResolver:
  def resolve(pattern: Pattern.WithExtensions, timeWindow: Interval): List[ScheduledEvent[AudioPayload]] =
    pattern.base.resolve(timeWindow).map { baseEvent =>
      val activeExts = pattern.extensions
        .flatMap(ext => ext.resolve(baseEvent.part))
        .filter(e => e.part.start <= baseEvent.part.start && e.part.end > baseEvent.part.start)
        .map(_.value)
      baseEvent.copy(appliedExtensions = baseEvent.appliedExtensions ++ activeExts)
    }