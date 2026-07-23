package soundcode.engine

import soundcode.domain.*

import scala.concurrent.duration.Duration

private case class PlayerState(
            eventStream: LazyList[ScheduledEvent[AudioPayload]] = LazyList.empty,
            firstTickTimeMs: Option[AbsoluteTime] = None,
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
    state = PlayerState(eventStream = stream) // Reset pulito in una riga
    onHighlightChange(state.currentHighlightSet)
  }

  /** Avvia il thread di riproduzione in background. */
  def start(): Unit = lock.synchronized {
    if (!isRunning) {
      isRunning = true
      thread = Some(new Thread(() => {
        while (true) {
          val running = lock.synchronized { isRunning }
          if (!running) return

          val now = System.currentTimeMillis()
          tick(AbsoluteTime(now))

          val sleepTime = loopResolutionMs - (System.currentTimeMillis() - now)
          if (sleepTime > 0) Thread.sleep(sleepTime)
        }
      }))
      thread.get.start()
    }
  }

  /** Ferma la riproduzione, chiude il thread e pulisce gli highlight attivi sulla UI. */
  def stop(): Unit = {
    val t = lock.synchronized {
      isRunning = false
      state = state.copy(activeHighlights = Map.empty, currentHighlightSet = Set.empty)
      onHighlightChange(state.currentHighlightSet)
      thread
    }
    t.foreach(_.join())
  }

  /** Avanza lo stato del player in base all'istante di tempo corrente.
   *
   * Estrae dallo stream gli eventi la cui finestra temporale è maturata, li invia al backend
   * audio e aggiorna la mappa delle posizioni testuali da evidenziare a schermo.
   *
   * @param now Il tempo assoluto di sistema al momento dell'invocazione.
   */
  def tick(now: AbsoluteTime): Unit = lock.synchronized {
    val currentState = this.state
    val firstPerformance = currentState.firstTickTimeMs.getOrElse(now)
    val validHighlights = currentState.activeHighlights.filter { case (_, expireAtMs) => expireAtMs > now }

    val (toProcess, futureEvents) = currentState.eventStream.span { nextEvent =>
      val expectedTriggerMs = firstPerformance + tempo.offsetMs(nextEvent.part.start)
      now >= expectedTriggerMs
    }

    var updatedHighlights = validHighlights

    toProcess.filter(e => e.part.start == e.whole.start).foreach { nextEvent =>
      val durationMs = tempo.durationMs(nextEvent.whole.start, nextEvent.whole.end)
      backend.triggerSound(nextEvent.value, durationMs, nextEvent.appliedExtensions)

      val allPayloads = nextEvent.value :: nextEvent.appliedExtensions
      val allPositions = allPayloads.flatMap(_.position) ++ nextEvent.modifierPositions

      val newHighlights = allPositions.map { pos => pos -> (now + durationMs) }
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

  /** Imposta il volume master (0..100), delegando al backend audio. */
  def setVolume(level: Double): Unit = backend.setVolume(level)
}