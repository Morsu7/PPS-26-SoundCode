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
    if (firstTickTimeMs.isEmpty) firstTickTimeMs = Some(now.toLong)
    //controllo per highLight
    var highlightsChanged = false

    val initialSize = activeHighlights.size
    activeHighlights = activeHighlights.filter { case (_, expireAtMs) => expireAtMs > now.toLong }
    if activeHighlights.size != initialSize then highlightsChanged = true
    //-----------------------

    var processing = true
    while (eventStream.nonEmpty && processing) {
      val nextEvent = eventStream.head
      val expectedTriggerMs = firstTickTimeMs.get + tempo.offsetMs(nextEvent.part.start)

      if (now.toLong >= expectedTriggerMs) {
        eventStream = eventStream.tail
        if (nextEvent.part.start == nextEvent.whole.start) {
          val durationMs = tempo.durationMs(nextEvent.whole.start, nextEvent.whole.end)
          backend.triggerSound(nextEvent.value, durationMs, nextEvent.appliedExtensions)
          //aggiunta highLight
          val positionOpt = nextEvent.value match
            case Sound.NoteInText(_, pos)   => Some(pos)
            case Sound.SampleInText(_, pos) => Some(pos)
            case Sound.Rest(pos)            => Some(pos)
            case _                          => None

          positionOpt.foreach { pos =>
            activeHighlights = activeHighlights + (pos -> (now.toLong + durationMs))
            highlightsChanged = true
          }
          //-----------------------
        }
      }else {
        processing = false
      }
    }

    if highlightsChanged then notifyHighlightsChanged()
  }

  private def notifyHighlightsChanged(): Unit =
    val newSet = activeHighlights.keySet
    if newSet != currentHighlightSet then
      currentHighlightSet = newSet
      onHighlightChange(currentHighlightSet)

}