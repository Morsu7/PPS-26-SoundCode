package soundcode.engine

import soundcode.domain.*

import scala.concurrent.duration.Duration

class AudioPlayer(val tempo: Tempo, backend: AudioBackend, onHighlightChange: Set[TextPosition] => Unit = _ => ()) {

  private val loopResolutionMs = 1L

  @volatile private var isRunning = false
  private var thread: Option[Thread] = None

  @volatile private var eventStream: LazyList[ScheduledEvent[AudioPayload]] = LazyList.empty
  @volatile private var firstTickTimeMs: Option[Long] = None

  private var activeHighlights: Map[TextPosition, Long] = Map.empty
  private var currentHighlightSet: Set[TextPosition] = Set.empty

  def updateTimeline(stream: LazyList[ScheduledEvent[AudioPayload]]): Unit =
    this.eventStream = stream
    this.firstTickTimeMs = None
    this.activeHighlights = Map.empty
    notifyHighlightsChanged()

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

  def stop(): Unit =
    isRunning = false
    thread.foreach(_.join())
    activeHighlights = Map.empty
    notifyHighlightsChanged()

  def tick(now: AbsoluteTime): Unit = {

    val firstPerformance = firstTickTimeMs.getOrElse(now.toLong)
    if firstTickTimeMs.isEmpty then firstTickTimeMs = Some(firstPerformance)

    val initialHighlights = activeHighlights
    activeHighlights = activeHighlights.filter { case (_, expireAtMs) => expireAtMs > now.toLong }
    val (toProcess, futureEvents) = eventStream.span { nextEvent =>
      val expectedTriggerMs = firstPerformance + tempo.offsetMs(nextEvent.part.start)
      now.toLong >= expectedTriggerMs
    }
    eventStream = futureEvents
    toProcess.filter(e => e.part.start == e.whole.start).foreach { nextEvent =>
      val durationMs = tempo.durationMs(nextEvent.whole.start, nextEvent.whole.end)
      backend.triggerSound(nextEvent.value, durationMs, nextEvent.appliedExtensions)

      val allPayloads = nextEvent.value :: nextEvent.appliedExtensions

      val newHighlights = allPayloads.collect {
        case Sound.NoteInText(_, pos) => pos -> (now.toLong + durationMs)
        case Sound.SampleInText(_, pos) => pos -> (now.toLong + durationMs)
        case Sound.Rest(pos) => pos -> (now.toLong + durationMs)
      }
      activeHighlights = activeHighlights ++ newHighlights
    }

    if (activeHighlights != initialHighlights) notifyHighlightsChanged()
  }

  private def notifyHighlightsChanged(): Unit =
    val newSet = activeHighlights.keySet
    if newSet != currentHighlightSet then
      currentHighlightSet = newSet
      onHighlightChange(currentHighlightSet)

}