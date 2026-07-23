package soundcode.engine

import soundcode.domain.*

import scala.concurrent.duration.Duration

private case class PlayerState(
                                eventStream: LazyList[ScheduledEvent[AudioPayload]] = LazyList.empty,
                                firstTickTimeMs: Option[AbsoluteTime] = None,
                                pausedDurationMs: Long = 0L,
                                pausedAtMs: Option[Long] = None,
                                activeHighlights: Map[TextPosition, AbsoluteTime] = Map.empty,
                                currentHighlightSet: Set[TextPosition] = Set.empty
                              )

/** Riproduttore audio multithread che consuma una sequenza temporale di eventi musicali.
 *
 * Il player gestisce il ciclo di vita della riproduzione svolgendo tre compiti principali:
 * 1. Sincronizzare il tempo logico/musicale con il tempo reale di sistema.
 * 2. Instradare i payload da riprodurre verso il generico [[AudioBackend]] per l'emissione del suono.
 * 3. Tracciare la posizione nel testo degli eventi in esecuzione ([[TextPosition]]), notificando
 *    l'interfaccia utente per l'evidenziazione dinamica del codice (highlighting).
 *
 * @param tempo Definisce i cicli per secondo (CPS) e traduce le frazioni temporali in millisecondi.
 * @param backend Il motore fisico (es. MIDI o synth) che esegue i comandi sonori.
 * @param onHighlightChange Callback invocata per informare la UI di quali porzioni di testo evidenziare.
 */
class AudioPlayer(var tempo: Tempo, backend: AudioBackend, onHighlightChange: Set[TextPosition] => Unit = _ => ()) {

  private val loopResolutionMs = 1L

  private var state = PlayerState()
  private val lock = new Object()

  private var isRunning = false
  private var thread: Option[Thread] = None

  def updateTimeline(stream: LazyList[ScheduledEvent[AudioPayload]]): Unit = lock.synchronized {
    state = PlayerState(eventStream = stream)
    onHighlightChange(state.currentHighlightSet)
  }

  /** Avvia o riprende il thread di riproduzione in background. */
  def start(): Unit = lock.synchronized {
    if (!isRunning) {
      isRunning = true

      val updatedState = for
        pTime <- state.pausedAtMs
      yield
        val additionalPause = System.currentTimeMillis() - pTime
        state.copy(
          pausedDurationMs = state.pausedDurationMs + additionalPause,
          pausedAtMs = None
        )

      updatedState.foreach(s => state = s)

      thread = Some(new Thread(() => {
        while lock.synchronized(isRunning) do
          val startedAt = System.currentTimeMillis()
          tick(AbsoluteTime(startedAt))

          val sleepTime =
            loopResolutionMs - (System.currentTimeMillis() - startedAt)

          if sleepTime > 0 then
            Thread.sleep(sleepTime)
      }))
      thread.get.start()
    }
  }

  /** Mette in pausa la riproduzione, chiude il thread e registra l'istante dello stop. */
  def stop(): Unit = {
      if isRunning then {
        val t = lock.synchronized {
          isRunning = false
          state = state.copy(
            activeHighlights = Map.empty,
            currentHighlightSet = Set.empty,
            pausedAtMs = Some(System.currentTimeMillis())
          )
          onHighlightChange(state.currentHighlightSet)
          thread
        }
        t.foreach(_.join())
      }
  }

  /** Avanza lo stato del player in base all'istante di tempo corrente. */
  def tick(now: AbsoluteTime): Unit = lock.synchronized {
    val currentState = this.state
    val firstPerformance = currentState.firstTickTimeMs.getOrElse(now)

    val effectiveNowMs = now - currentState.pausedDurationMs

    val validHighlights = currentState.activeHighlights.filter { case (_, expireAtMs) => expireAtMs > effectiveNowMs }

    val (toProcess, futureEvents) = currentState.eventStream.span { nextEvent =>
      val expectedTriggerMs = firstPerformance + tempo.offsetMs(nextEvent.part.start)
      effectiveNowMs >= expectedTriggerMs
    }

    var updatedHighlights = validHighlights

    toProcess.filter(e => e.part.start == e.whole.start).foreach { nextEvent =>
      val durationMs = tempo.durationMs(nextEvent.whole.start, nextEvent.whole.end)
      backend.triggerSound(nextEvent.value, durationMs, nextEvent.appliedExtensions)

      val allPayloads = nextEvent.value :: nextEvent.appliedExtensions
      val allPositions = allPayloads.flatMap(_.position) ++ nextEvent.modifierPositions

      val newHighlights = allPositions.map { pos => pos -> (effectiveNowMs + durationMs) }
      updatedHighlights = updatedHighlights ++ newHighlights
    }

    val newHighlightSet = updatedHighlights.keySet
    if (newHighlightSet != currentState.currentHighlightSet) {
      onHighlightChange(newHighlightSet)
    }

    this.state = currentState.copy(
      eventStream = futureEvents,
      firstTickTimeMs = Some(firstPerformance),
      activeHighlights = updatedHighlights,
      currentHighlightSet = newHighlightSet
    )
  }

  def updateTempo(newTempo: Tempo): Unit = lock.synchronized {
    this.tempo = newTempo
  }

  def setVolume(level: Double): Unit = backend.setVolume(level)
}