package soundcode.engine

import soundcode.domain.*

import scala.annotation.tailrec

object PatternResolver:

  def query[T](pattern: Pattern[T], timeWindow: Interval): List[ScheduledEvent[T]] =
    if timeWindow.start >= timeWindow.end then return Nil

    pattern match
      case Pattern.Atom(value) => List(ScheduledEvent(whole = timeWindow, part = timeWindow, value))
      case Pattern.Parallel(layers) => layers.flatMap(layer => query(layer, timeWindow))
      case Pattern.Alternation(elements) => resolveAlternation(elements, timeWindow)
      case Pattern.Sequence(elements) => resolveSequence(elements, timeWindow)
      case Pattern.TimeWarp(modifier, inner) => resolveTimeWarp(modifier, inner, timeWindow)
      case we: Pattern.WithExtensions => resolveExtensions(we.base, we.extensions, timeWindow)
  
  private def resolveTimeWarp[T](modifier: PatternModifier[T], innerPattern: Pattern[T], timeWindow: Interval): List[ScheduledEvent[T]] =
    modifier match
      case PatternModifier.FastForward(factor) =>
        applyDynamicModifier(factor, innerPattern, timeWindow, zoomIn = _ * _, zoomOut = (e, f) => e.mapTime(_ / f))
      case PatternModifier.SlowMotion(factor) =>
        applyDynamicModifier(factor, innerPattern, timeWindow, zoomIn = _ / _, zoomOut = (e, f) => e.mapTime(_ * f))
      case PatternModifier.Late(offset) =>
        applyDynamicModifier(offset, innerPattern, timeWindow, zoomIn = _ - _, zoomOut = (e, o) => e.mapTime(_ + o))
      case PatternModifier.Early(offset) =>
        applyDynamicModifier(offset, innerPattern, timeWindow, zoomIn = _ + _, zoomOut = (e, o) => e.mapTime(_ - o))

      //Nuove aggiunte
      case PatternModifier.Reverse =>
        val events = for cycle <- timeWindow.spanningCycles yield
          val cycleStart = Fraction(cycle)
          val cycleEnd = cycleStart + 1

          Interval(cycleStart, cycleEnd).intersect(timeWindow).toList.flatMap { activeWindow =>
            val reverseF = (t: Fraction) => cycleStart + cycleEnd - t
            val queryWindow = Interval(reverseF(activeWindow.end), reverseF(activeWindow.start))

            query(innerPattern, queryWindow).map { e =>
              e.copy(
                whole = Interval(reverseF(e.whole.end), reverseF(e.whole.start)),
                part = Interval(reverseF(e.part.end), reverseF(e.part.start))
              )
            }
          }
        events.toList.flatten

      case PatternModifier.Repetition(times) =>
        val events = for
          paramEvent <- query(times, timeWindow)
          n = math.max(1, math.round(paramEvent.value).toInt)
          activeWindow <- paramEvent.part.intersect(timeWindow).toList
          innerEvent <- query(innerPattern, activeWindow)

          wholeDur = innerEvent.whole.duration
          stepDur = wholeDur / n   // Boom! Niente più Fraction(n, 1L)

          i <- 0 until n
          newWholeStart = innerEvent.whole.start + (stepDur * i)
          newWhole = Interval(newWholeStart, newWholeStart + stepDur)
          newPart <- newWhole.intersect(innerEvent.part).toList
        yield
          innerEvent.copy(whole = newWhole, part = newPart)

        events.toList

      case PatternModifier.Juxtaposition(modifiers) =>
        val modifiedPatterns = modifiers.map(mod => Pattern.TimeWarp(mod, innerPattern))
        query(Pattern.Parallel(innerPattern :: modifiedPatterns), timeWindow)

      case PatternModifier.Offset(offset, modifiers) =>
        val delayedPattern = Pattern.TimeWarp(PatternModifier.Late(Pattern.Atom(offset)), innerPattern)
        val finalTransformed = modifiers.foldLeft(delayedPattern)((pat, mod) => Pattern.TimeWarp(mod, pat))
        query(Pattern.Parallel(List(innerPattern, finalTransformed)), timeWindow)


  private def resolveAlternation[T](elements: List[Pattern[T]], timeWindow: Interval): List[ScheduledEvent[T]] =
    if elements.isEmpty then return Nil

    val events = for cycle <- timeWindow.spanningCycles yield
      val cycleStart = Fraction(cycle)

      Interval(cycleStart, cycleStart + 1).intersect(timeWindow).toList.flatMap { activeWindow =>
        val activeIndex = (cycle.abs % elements.size).toInt
        
        val timeOffset = Fraction(cycle - (cycle / elements.size))

        query(elements(activeIndex), activeWindow.map(_ - timeOffset))
          .map(_.mapTime(_ + timeOffset))
      }

    events.toList.flatten

  private def resolveSequence[T](elements: List[Pattern[T]], timeWindow: Interval): List[ScheduledEvent[T]] =
    if elements.isEmpty then return Nil
    val n = elements.size
    val step = Fraction(1, n)

    val events = for
      cycle <- timeWindow.spanningCycles
      cycleStart = Fraction(cycle)
      (element, index) <- elements.zipWithIndex

      slotStart = cycleStart + (step * index)
      slot = Interval(slotStart, slotStart + step)

      overlap <- slot.intersect(timeWindow).toList
      
      zoomedInWindow = overlap.map(t => (t - slotStart) * n + cycleStart)

      childEvent <- query(element, zoomedInWindow)

      finalEvent <- childEvent
        .mapTime(t => ((t - cycleStart) / n) + slotStart)
        .clipTo(overlap)
        .toList
    yield finalEvent

    events.toList

  private def resolveExtensions(base: Pattern[AudioPayload], extensions: List[Pattern[AudioPayload]], timeWindow: Interval): List[ScheduledEvent[AudioPayload]] =
    query(base, timeWindow).map { baseEvent =>
      val activeExts = extensions
        .flatMap(ext => query(ext, baseEvent.part))
        .filter(e => e.part.start <= baseEvent.part.start && e.part.end > baseEvent.part.start)
        .map(_.value)
      baseEvent.copy(appliedExtensions = baseEvent.appliedExtensions ++ activeExts)
    }

  private def applyDynamicModifier[T](parameter: Pattern[Double], innerPattern: Pattern[T], timeWindow: Interval, zoomIn: (Interval, Fraction) => Interval, zoomOut: (ScheduledEvent[T], Fraction) => ScheduledEvent[T]): List[ScheduledEvent[T]] =
    for
      paramEvent <- query(parameter, timeWindow)
      paramValue = Fraction(paramEvent.value)

      activeWindow <- paramEvent.part.intersect(timeWindow).toList
      warpedWindow = zoomIn(activeWindow, paramValue)

      cycle <- warpedWindow.spanningCycles
      cycleStart = Fraction(cycle)

      cycleWindow <- Interval(cycleStart, cycleStart + 1).intersect(warpedWindow).toList
      innerEvent <- query(innerPattern, cycleWindow)

    yield zoomOut(innerEvent, paramValue)