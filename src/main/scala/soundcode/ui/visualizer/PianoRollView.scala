package soundcode.ui.visualizer

import scalafx.scene.paint.Color
import scalafx.scene.canvas.GraphicsContext
import soundcode.ui.UITheme
import soundcode.domain.{ScheduledEvent, AudioPayload, Sound}
import soundcode.domain.Tempo
import soundcode.domain.Timeline
import scalafx.scene.text.TextAlignment
import scalafx.scene.text.Font
import scalafx.geometry.VPos

final class PianorollView(
    timeline: Timeline[AudioPayload],
    tempo: Tempo
) extends CanvasAnimatedView(tempo):
  val visualEvents: Seq[VisualEvent] = toVisualEvents()

  private val loopLength = timeline.loopLength.toDouble

  private def toVisualEvents(): Seq[VisualEvent] =
    val events = timeline.events.collect {
      case ScheduledEvent(_, part, Sound.NoteInText(note, _), _) =>
        (note.toString, part)
      case ScheduledEvent(_, part, Sound.SampleInText(sample, _), _) =>
        (sample.toString, part)
    }

    val laneByNote = events.map(_._1).distinct.sorted.zipWithIndex.toMap

    events.map { case (note, interval) =>
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
      currentBeat - playheadX / pixelsPerCycle - maxDuration

    val lastVisibleBeat =
      currentBeat + (canvasWidth - playheadX) / pixelsPerCycle

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
      val x = playheadX + (event.start - currentBeat) * pixelsPerCycle
      val y = event.lane * laneHeight + config.verticalPadding
      val width = event.duration * pixelsPerCycle
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

      val horizontalTextPadding = 4.0
      val availableTextWidth = width - horizontalTextPadding * 2

      if availableTextWidth > 0 then
        gc.fill =
          if isActive then Color.web(UITheme.Foreground)
          else Color.web(UITheme.Background)

        gc.font = Font.font(math.min(12.0, height * 0.55))
        gc.textAlign = TextAlignment.Left
        gc.textBaseline = VPos.Center

        gc.fillText(
          event.label,
          x + horizontalTextPadding,
          y + height / 2,
          availableTextWidth
        )
    }
