package soundcode.engine

import soundcode.domain.*

import scala.concurrent.duration.Duration


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
class AudioPlayer(
    val tempo: Tempo,
    backend: AudioBackend,
    onHighlightChange: Set[TextPosition] => Unit = _ => (),
    onActiveNotes: Seq[ActiveNote] => Unit = _ => ()
) {

  private val loopResolutionMs = 1L

  @volatile private var isRunning = false
  private var thread: Option[Thread] = None

  @volatile private var eventStream: LazyList[ScheduledEvent[AudioPayload]] = LazyList.empty
  @volatile private var firstTickTimeMs: Option[AbsoluteTime] = None

  private var activeHighlights: Map[TextPosition, AbsoluteTime] = Map.empty
  private var currentHighlightSet: Set[TextPosition] = Set.empty

  // Buffer delle note attualmente in suono, per i visualizzatori (es. oscilloscopio).
  private var activeNotes: List[(ActiveNote, AbsoluteTime)] = Nil
  private var currentNoteBuffer: Seq[ActiveNote] = Nil

  def updateTimeline(stream: LazyList[ScheduledEvent[AudioPayload]]): Unit =
    this.eventStream = stream
    this.firstTickTimeMs = None
    this.activeHighlights = Map.empty
    this.activeNotes = Nil
    notifyHighlightsChanged()
    notifyActiveNotesChanged()

  /** Avvia il thread di riproduzione in background. */
  def start(): Unit = if (!isRunning) {
    isRunning = true
    thread = Some(new Thread(() => while (isRunning) {
      val now = System.currentTimeMillis()
      tick(AbsoluteTime(now))

      val sleepTime = loopResolutionMs - (System.currentTimeMillis() - now)
      if (sleepTime > 0) Thread.sleep(sleepTime)
    }))
    thread.get.start()
  }

  /** Ferma la riproduzione, chiude il thread e pulisce gli highlight attivi sulla UI. */
  def stop(): Unit =
    isRunning = false
    thread.foreach(_.join())
    activeHighlights = Map.empty
    activeNotes = Nil
    notifyHighlightsChanged()
    notifyActiveNotesChanged()

  /** Avanza lo stato del player in base all'istante di tempo corrente.
   *
   * Estrae dallo stream gli eventi la cui finestra temporale è maturata, li invia al backend
   * audio e aggiorna la mappa delle posizioni testuali da evidenziare a schermo.
   *
   * @param now Il tempo assoluto di sistema al momento dell'invocazione.
   */
  def tick(now: AbsoluteTime): Unit = {

    val firstPerformance = firstTickTimeMs.getOrElse(now)
    if firstTickTimeMs.isEmpty then firstTickTimeMs = Some(firstPerformance)

    val initialHighlights = activeHighlights
    val initialNotes = activeNotes
    activeHighlights = activeHighlights.filter { case (_, expireAtMs) => expireAtMs > now }
    activeNotes = activeNotes.filter { case (_, expireAtMs) => expireAtMs > now }
    val (toProcess, futureEvents) = eventStream.span { nextEvent =>
      val expectedTriggerMs = firstPerformance + tempo.offsetMs(nextEvent.part.start)
      now >= expectedTriggerMs
    }
    eventStream = futureEvents
    toProcess.filter(e => e.part.start == e.whole.start).foreach { nextEvent =>
      val durationMs = tempo.durationMs(nextEvent.whole.start, nextEvent.whole.end)
      backend.triggerSound(nextEvent.value, durationMs, nextEvent.appliedExtensions)

      val allPayloads = nextEvent.value :: nextEvent.appliedExtensions

      val newHighlights = allPayloads.flatMap(_.position).map { pos =>pos -> (now + durationMs)}
      activeHighlights = activeHighlights ++ newHighlights

      ActiveNote.fromPayload(nextEvent.value, nextEvent.appliedExtensions).foreach { note =>
        activeNotes = activeNotes :+ (note -> (now + durationMs))
      }
    }

    if (activeHighlights != initialHighlights) notifyHighlightsChanged()
    if (activeNotes != initialNotes) notifyActiveNotesChanged()
  }

  private def notifyHighlightsChanged(): Unit =
    val newSet = activeHighlights.keySet
    if newSet != currentHighlightSet then
      currentHighlightSet = newSet
      onHighlightChange(currentHighlightSet)

  private def notifyActiveNotesChanged(): Unit =
    val newBuffer = activeNotes.map(_._1)
    if newBuffer != currentNoteBuffer then
      currentNoteBuffer = newBuffer
      onActiveNotes(newBuffer)

}