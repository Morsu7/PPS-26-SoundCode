package soundcode.engine.resolvers
import soundcode.domain.*
import soundcode.engine.*

object TimeWarpResolver:

  def resolve[T](pattern: Pattern.TimeWarp[T])(using timeWindow: Interval): List[ScheduledEvent[T]] =
    val innerPattern = pattern.pattern

    pattern.modifier match
      case PatternModifier.FastForward(factor) =>
        applyDynamicModifier(factor, innerPattern, zoomIn = _ * _, zoomOut = (e, f) => e.mapTime(_ / f))

      case PatternModifier.SlowMotion(factor) =>
        applyDynamicModifier(factor, innerPattern, zoomIn = _ / _, zoomOut = (e, f) => e.mapTime(_ * f))

      case PatternModifier.Late(offset) =>
        applyDynamicModifier(offset, innerPattern, zoomIn = _ - _, zoomOut = (e, o) => e.mapTime(_ + o))

      case PatternModifier.Early(offset) =>
        applyDynamicModifier(offset, innerPattern, zoomIn = _ + _, zoomOut = (e, o) => e.mapTime(_ - o))

      case PatternModifier.Reverse =>
        val events = for cycle <- timeWindow.spanningCycles yield
          val cycleStart = Fraction(cycle)
          val cycleEnd = cycleStart + 1

          Interval(cycleStart, cycleEnd).intersect(timeWindow).toList.flatMap { activeWindow =>
            val reverseF = (t: Fraction) => cycleStart + cycleEnd - t
            val queryWindow = Interval(reverseF(activeWindow.end), reverseF(activeWindow.start))

            innerPattern.resolve(using queryWindow).map { e =>
              e.copy(
                whole = Interval(reverseF(e.whole.end), reverseF(e.whole.start)),
                part = Interval(reverseF(e.part.end), reverseF(e.part.start))
              )
            }
          }
        events.toList.flatten

      case PatternModifier.Repetition(times) =>
        val events = for
          paramEvent <- times.resolve
          n = math.max(1, math.round(paramEvent.value).toInt)
          activeWindow <- paramEvent.part.intersect(timeWindow).toList
          innerEvent <- innerPattern.resolve(using activeWindow)

          wholeDur = innerEvent.whole.duration
          stepDur = wholeDur / n

          i <- 0 until n
          newWholeStart = innerEvent.whole.start + (stepDur * i)
          newWhole = Interval(newWholeStart, newWholeStart + stepDur)
          newPart <- newWhole.intersect(innerEvent.part).toList
        yield innerEvent.copy(whole = newWhole, part = newPart)

        events

      case PatternModifier.Juxtaposition(modifiers) =>
        val rightPattern = applyModifiers(innerPattern, modifiers)

        List(innerPattern -> 0.0, rightPattern -> 1.0).flatMap { case (pattern, pan) =>
          pattern.resolve.map { e =>
            e.copy(appliedExtensions = e.appliedExtensions.overriddenBy(List(AudioEffect.Pan(pan))))
          }
        }

      case PatternModifier.Offset(offset, modifiers) =>
        val modifiedPattern = applyModifiers(innerPattern, modifiers)
        val delayedPattern = Pattern.TimeWarp(PatternModifier.Late(offset), modifiedPattern)

        Pattern.Parallel(List(innerPattern, delayedPattern)).resolve

  private def applyDynamicModifier[T](parameter: Pattern[Double], innerPattern: Pattern[T],
                                       zoomIn: (Interval, Fraction) => Interval,
                                       zoomOut: (ScheduledEvent[T], Fraction) => ScheduledEvent[T]
                                     )(using timeWindow: Interval): List[ScheduledEvent[T]] =
    for
      paramEvent <- parameter.resolve
      paramValue = Fraction(paramEvent.value)

      activeWindow <- paramEvent.part.intersect(timeWindow).toList
      warpedWindow = zoomIn(activeWindow, paramValue)

      cycle <- warpedWindow.spanningCycles
      cycleStart = Fraction(cycle)

      cycleWindow <- Interval(cycleStart, cycleStart + 1).intersect(warpedWindow).toList
      
      innerEvent <- innerPattern.resolve(using cycleWindow)
    yield zoomOut(innerEvent, paramValue)

  private def applyModifiers[T](basePattern: Pattern[T], modifiers: List[PatternModifier[T] | Pattern[AudioEffect]]): Pattern[T] =
    modifiers.foldLeft[Pattern[Any]](basePattern) {
      case (pat, mod: PatternModifier[_]) =>
        Pattern.TimeWarp(mod.asInstanceOf[PatternModifier[Any]], pat)
      case (pat, effectPat: Pattern[_]) =>
        Pattern.WithExtensions(pat.asInstanceOf[Pattern[AudioPayload]], List(effectPat.asInstanceOf[Pattern[AudioPayload]]))
    }.asInstanceOf[Pattern[T]]