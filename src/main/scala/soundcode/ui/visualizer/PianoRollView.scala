package soundcode.ui.visualizer

import scalafx.scene.paint.Color
import scalafx.scene.canvas.GraphicsContext
import soundcode.ui.UITheme
import soundcode.domain.{ScheduledEvent, AudioPayload, Sound}

final class PianorollView(
    timeline: Seq[ScheduledEvent[AudioPayload]] = List.empty,
    loopLength: Double = 1.0
) extends CanvasAnimatedView:
  val visualEvents: Seq[VisualEvent] = toVisualEvents()

  private def toVisualEvents(): Seq[VisualEvent] =
    val notes = timeline.collect {
      case ScheduledEvent(_, part, Sound.NoteInText(note, _), _) =>
        (note.toString, part)
    }

    val laneByNote = notes.map(_._1).distinct.sorted.zipWithIndex.toMap

    notes.map { case (note, interval) =>
      VisualEvent(
        label = note,
        start = interval.start.toDouble,
        duration = interval.duration.toDouble,
        lane = laneByNote(note),
        color = Color.web(UITheme.Foreground)
      )
    }

  val laneCount = visualEvents.map(_.lane).maxOption.getOrElse(0) + 1
  val laneHeight = (canvasHeight - config.verticalPadding * 2) / laneCount

  override protected def draw(
      gc: GraphicsContext,
      currentBeat: Double,
      w: Double,
      h: Double
  ): Unit =
    val playheadX = w * 0.5

    drawPlayhead(gc, playheadX, h)
    drawNotes(gc, currentBeat, playheadX)

  private def drawPlayhead(gc: GraphicsContext, x: Double, h: Double): Unit =
    gc.stroke = Color.White
    gc.lineWidth = 2
    gc.strokeLine(x, config.verticalPadding, x, h - config.verticalPadding)

  private def drawNotes(
      gc: GraphicsContext,
      currentBeat: Double,
      playheadX: Double
  ): Unit =
    val canvasWidth = gc.canvas.width.value
    val maxDuration = visualEvents.map(_.duration).maxOption.getOrElse(0.0)

    val firstVisibleBeat =
      currentBeat - playheadX / pixelsPerBeat - maxDuration

    val lastVisibleBeat =
      currentBeat + (canvasWidth - playheadX) / pixelsPerBeat

    val firstLoop =
      Math.floor(firstVisibleBeat / loopLength).toInt.max(0)

    val lastLoop =
      Math.ceil(lastVisibleBeat / loopLength).toInt

    val repeatedEvents =
      for
        loop <- firstLoop to lastLoop
        event <- visualEvents
      yield event.copy(start = event.start + loop * loopLength)

    repeatedEvents.foreach { event =>
      val x = playheadX + (event.start - currentBeat) * pixelsPerBeat
      val y = event.lane * laneHeight + config.verticalPadding
      val width = event.duration * pixelsPerBeat
      val height = laneHeight

      val isActive =
        currentBeat >= event.start && currentBeat < event.start + event.duration

      if isActive then
        gc.stroke = Color.web(UITheme.Foreground)
        gc.lineWidth = 2
        gc.strokeRect(x, y, width, height)
      else
        gc.fill = Color.web(UITheme.Foreground)
        gc.fillRect(x, y, width, height)
    }
