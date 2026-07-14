package soundcode.engine.resolvers
import soundcode.domain.*
import soundcode.engine.*

object TimeWarpResolver:

  def resolve[T](pattern: Pattern.TimeWarp[T], timeWindow: Interval): List[ScheduledEvent[T]] =
    val innerPattern = pattern.pattern

    pattern.modifier match
      case PatternModifier.FastForward(factor) =>
        applyDynamicModifier(factor, innerPattern, timeWindow, zoomIn = _ * _, zoomOut = (e, f) => e.mapTime(_ / f))

      case PatternModifier.SlowMotion(factor) =>
        applyDynamicModifier(factor, innerPattern, timeWindow, zoomIn = _ / _, zoomOut = (e, f) => e.mapTime(_ * f))

      case PatternModifier.Late(offset) =>
        applyDynamicModifier(offset, innerPattern, timeWindow, zoomIn = _ - _, zoomOut = (e, o) => e.mapTime(_ + o))

      case PatternModifier.Early(offset) =>
        applyDynamicModifier(offset, innerPattern, timeWindow, zoomIn = _ + _, zoomOut = (e, o) => e.mapTime(_ - o))

      case PatternModifier.Reverse =>
        val events = for cycle <- timeWindow.spanningCycles yield
          val cycleStart = Fraction(cycle)
          val cycleEnd = cycleStart + 1

          Interval(cycleStart, cycleEnd).intersect(timeWindow).toList.flatMap { activeWindow =>
            val reverseF = (t: Fraction) => cycleStart + cycleEnd - t
            val queryWindow = Interval(reverseF(activeWindow.end), reverseF(activeWindow.start))

            innerPattern.resolve(queryWindow).map { e =>
              e.copy(
                whole = Interval(reverseF(e.whole.end), reverseF(e.whole.start)),
                part = Interval(reverseF(e.part.end), reverseF(e.part.start))
              )
            }
          }
        events.toList.flatten

      case PatternModifier.Repetition(times) =>
        val events = for
          paramEvent <- times.resolve(timeWindow)
          n = math.max(1, math.round(paramEvent.value).toInt)
          activeWindow <- paramEvent.part.intersect(timeWindow).toList
          innerEvent <- innerPattern.resolve(activeWindow)

          wholeDur = innerEvent.whole.duration
          stepDur = wholeDur / n

          i <- 0 until n
          newWholeStart = innerEvent.whole.start + (stepDur * i)
          newWhole = Interval(newWholeStart, newWholeStart + stepDur)
          newPart <- newWhole.intersect(innerEvent.part).toList
        yield innerEvent.copy(whole = newWhole, part = newPart)

        events

      case PatternModifier.Juxtaposition(modifiers) =>
        val modifiedPatterns = modifiers.map(mod => Pattern.TimeWarp(mod, innerPattern))
        Pattern.Parallel(innerPattern :: modifiedPatterns).resolve(timeWindow)

      case PatternModifier.Offset(offset, modifiers) =>
        val delayedPattern = Pattern.TimeWarp(PatternModifier.Late(Pattern.Atom(offset)), innerPattern)
        val finalTransformed = modifiers.foldLeft(delayedPattern)((pat, mod) => Pattern.TimeWarp(mod, pat))
        Pattern.Parallel(List(innerPattern, finalTransformed)).resolve(timeWindow)

  private def applyDynamicModifier[T](
                                       parameter: Pattern[Double],
                                       innerPattern: Pattern[T],
                                       timeWindow: Interval,
                                       zoomIn: (Interval, Fraction) => Interval,
                                       zoomOut: (ScheduledEvent[T], Fraction) => ScheduledEvent[T]
                                     ): List[ScheduledEvent[T]] =
    for
      paramEvent <- parameter.resolve(timeWindow)
      paramValue = Fraction(paramEvent.value)

      activeWindow <- paramEvent.part.intersect(timeWindow).toList
      warpedWindow = zoomIn(activeWindow, paramValue)

      cycle <- warpedWindow.spanningCycles
      cycleStart = Fraction(cycle)

      cycleWindow <- Interval(cycleStart, cycleStart + 1).intersect(warpedWindow).toList
      innerEvent <- innerPattern.resolve(cycleWindow)
    yield zoomOut(innerEvent, paramValue)