package soundcode.engine

import soundcode.domain.*

class AudioPlayer(val tempo: Tempo) {

  private val loopResolutionMs = 1L

  @volatile private var isRunning = false
  private var thread: Option[Thread] = None

  @volatile private var eventStream: LazyList[ScheduledEvent[AudioPayload]] = LazyList.empty
  @volatile private var firstTickTimeMs: Option[Long] = None

  def updateTimeline(stream: LazyList[ScheduledEvent[AudioPayload]]): Unit = this.eventStream = stream; this.firstTickTimeMs = None

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

  def stop(): Unit = isRunning = false; thread.foreach(_.join())

  def tick(now: AbsoluteTime): Unit = {
    if (firstTickTimeMs.isEmpty) firstTickTimeMs = Some(now.toLong)

    while (eventStream.nonEmpty) {
      val nextEvent = eventStream.head
      val expectedTriggerMs = firstTickTimeMs.get + tempo.offsetMs(nextEvent.part.start)
      if (now.toLong >= expectedTriggerMs) {
        eventStream = eventStream.tail
        if (nextEvent.part.start == nextEvent.whole.start) {
          val durationMs = tempo.durationMs(nextEvent.whole.start, nextEvent.whole.end)
          triggerSound(nextEvent.value, durationMs, nextEvent.appliedExtensions)
        }
      } else {
        return
      }
    }
  }

  protected def triggerSound(payload: AudioPayload, durationMs: Long, extensions: List[AudioPayload]): Unit = {
    payload match {
      case Sound.Rest(_) => ???
      case Sound.SampleInText(s, _) => ???
      case Sound.NoteInText(_, _) => ???
      case _ => ???
    }
  }
}