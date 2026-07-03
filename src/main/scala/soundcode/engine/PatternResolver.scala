package soundcode.engine

import soundcode.domain.*

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
        applyDynamicModifier(factor, innerPattern, timeWindow,
          zoomIn  = (w, f) => w.map(t => t * f),
          zoomOut = (e, f) => e.mapTime(t => t / f)
        )

      case PatternModifier.SlowMotion(factor) =>
        applyDynamicModifier(factor, innerPattern, timeWindow,
          zoomIn  = (w, f) => w.map(t => t / f),
          zoomOut = (e, f) => e.mapTime(t => t * f)
        )

      case PatternModifier.Late(offset) =>
        applyDynamicModifier(offset, innerPattern, timeWindow,
          zoomIn  = (w, o) => w.map(t => t - o),
          zoomOut = (e, o) => e.mapTime(t => t + o)
        )

      case PatternModifier.Early(offset) =>
        applyDynamicModifier(offset, innerPattern, timeWindow,
          zoomIn  = (w, o) => w.map(t => t + o),
          zoomOut = (e, o) => e.mapTime(t => t - o)
        )

      //Nuove aggiunte
      case PatternModifier.Reverse =>
        val startCycle = timeWindow.start.toDouble.floor.toLong
        val endCycle = timeWindow.end.toDouble.ceil.toLong

        val events = for cycle <- startCycle until endCycle yield
          val cycleStart = Fraction(cycle, 1L)
          val cycleEnd = cycleStart + Fraction(1L, 1L)

          Interval(cycleStart, cycleEnd).intersect(timeWindow).toList.flatMap { activeWindow =>
            val reverseF = (t: Fraction) => cycleStart + cycleEnd - t

            // LA CORREZIONE: Applichiamo la formula ma SCAMBIAMO start ed end!
            // In questo modo l'intervallo è sempre matematicamente valido (start < end).
            val queryWindow = Interval(reverseF(activeWindow.end), reverseF(activeWindow.start))

            query(innerPattern, queryWindow).map { e =>
              // Quando ricostruiamo l'evento in uscita, dobbiamo scambiare di nuovo
              // i suoi bordi, per riportarli alla linea temporale in cui ci troviamo.
              val newWhole = Interval(reverseF(e.whole.end), reverseF(e.whole.start))
              val newPart = Interval(reverseF(e.part.end), reverseF(e.part.start))
              e.copy(whole = newWhole, part = newPart)
            }
          }
        events.toList.flatten

      case PatternModifier.Delay(offset) =>
        // Matematicamente, un delay sul pattern globale è identico a "Late"
        resolveTimeWarp(PatternModifier.Late(offset), innerPattern, timeWindow)

      case PatternModifier.Repetition(times) =>
        // Ripetere l'intero pattern N volte nello stesso ciclo equivale a velocizzarlo (FastForward)
        resolveTimeWarp(PatternModifier.FastForward(times), innerPattern, timeWindow)

      case PatternModifier.Juxtaposition(modifiers) =>
        // JUX: Suona l'originale IN PARALLELO con le versioni modificate!
        // Trasformiamo al volo l'albero in un Pattern.Parallel
        val modifiedPatterns = modifiers.map(mod => Pattern.TimeWarp(mod, innerPattern))
        query(Pattern.Parallel(innerPattern :: modifiedPatterns), timeWindow)

      case PatternModifier.Offset(offset, modifiers) =>
        // OFF: Suona l'originale IN PARALLELO con una versione ritardata a cui applichiamo i modificatori
        val delayedPattern = Pattern.TimeWarp(PatternModifier.Late(Pattern.Atom(offset)), innerPattern)
        val finalTransformed = modifiers.foldLeft(delayedPattern) { (pat, mod) =>
          Pattern.TimeWarp(mod, pat)
        }
        query(Pattern.Parallel(List(innerPattern, finalTransformed)), timeWindow)

      case _ =>
        query(innerPattern, timeWindow)


  private def resolveAlternation[T](elements: List[Pattern[T]], timeWindow: Interval): List[ScheduledEvent[T]] =
    if elements.isEmpty then return Nil

    val startCycle = timeWindow.start.toDouble.floor.toLong
    val endCycle = timeWindow.end.toDouble.ceil.toLong

    val events = for cycle <- startCycle until endCycle yield
      val cycleStart = Fraction(cycle, 1L)
      val cycleEnd = cycleStart + Fraction(1L, 1L)

      Interval(cycleStart, cycleEnd).intersect(timeWindow).toList.flatMap { activeWindow =>
        val activeIndex = (cycle.abs % elements.size).toInt
        val timeOffset = Fraction(cycle - (cycle / elements.size), 1L)

        query(elements(activeIndex), activeWindow.map(t => t - timeOffset))
          .map(event => event.mapTime(t => t + timeOffset))
      }

    events.toList.flatten


  private def resolveSequence[T](elements: List[Pattern[T]], timeWindow: Interval): List[ScheduledEvent[T]] =
    if elements.isEmpty then return Nil
    val n = elements.size
    val zoomF = Fraction(n.toLong, 1L)
    val step = Fraction(1, n)

    val startCycle = timeWindow.start.toDouble.floor.toLong
    val endCycle = timeWindow.end.toDouble.ceil.toLong

    val events = for
      cycle <- startCycle until endCycle
      cycleStart = Fraction(cycle, 1L)
      (element, index) <- elements.zipWithIndex
      slotStart = cycleStart + (step * Fraction(index.toLong, 1L))
      slot = Interval(slotStart, slotStart + step)

      overlap <- slot.intersect(timeWindow).toList

      zoomedInWindow = overlap.map(t => (t - slotStart) * zoomF + cycleStart)

      childEvent <- query(element, zoomedInWindow)

      finalEvent <- childEvent
        .mapTime(t => ((t - cycleStart) / zoomF) + slotStart)
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
      paramValue = Fraction((paramEvent.value * 100).toLong, 100L)

      activeWindow <- paramEvent.part.intersect(timeWindow).toList
      warpedWindow = zoomIn(activeWindow, paramValue)

      startCycle = warpedWindow.start.toDouble.floor.toLong
      endCycle = warpedWindow.end.toDouble.ceil.toLong
      cycle <- startCycle until endCycle

      cycleStart = Fraction(cycle, 1L)
      cycleEnd = cycleStart + Fraction(1L, 1L)

      cycleWindow <- Interval(cycleStart, cycleEnd).intersect(warpedWindow).toList

      innerEvent <- query(innerPattern, cycleWindow)

    yield zoomOut(innerEvent, paramValue)