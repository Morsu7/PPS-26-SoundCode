package soundcode.ui.visualizer

import scalafx.scene.paint.Color
import scalafx.scene.canvas.GraphicsContext
import soundcode.ui.UITheme
import soundcode.domain.{ScheduledEvent, AudioPayload, Sound, Tempo}
import scalafx.scene.text.TextAlignment
import scalafx.scene.text.Font
import scalafx.geometry.VPos

final class PianorollView(
    timeline: Seq[ScheduledEvent[AudioPayload]],
    tempo: Tempo
) extends CanvasAnimatedView(tempo):
  val visualEvents: Seq[VisualEvent] = toVisualEvents()

  private val loopLength = timeline
    .map(_.whole.end)
    .maxOption
    .map(_.toDouble)
    .getOrElse(0.0)

  private val maxEventDuration = visualEvents.map(_.duration).maxOption.getOrElse(0.0)

  private def toVisualEvents(): Seq[VisualEvent] =
    val events = timeline.collect {
      case ScheduledEvent(_, part, Sound.NoteInText(note, _), _, _) =>
        (note.toString, part)
      case ScheduledEvent(_, part, Sound.SampleInText(sample, _), _, _) =>
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
      currentCycle: Double,
      w: Double,
      h: Double
  ): Unit =
    val playheadX = w * 0.5

    drawPlayhead(gc, playheadX, h)
    drawNotes(gc, currentCycle, playheadX)

  private def drawPlayhead(gc: GraphicsContext, x: Double, h: Double): Unit =
    gc.stroke = Color.White
    gc.lineWidth = 2
    gc.strokeLine(x, config.verticalPadding, x, h - config.verticalPadding)

  private def drawNotes(
      gc: GraphicsContext,
      currentCycle: Double,
      playheadX: Double
  ): Unit =
    if visualEvents.isEmpty || loopLength <= 0.0 then return

    val canvasWidth = gc.canvas.width.value

    val firstVisibleCycle =
      currentCycle - playheadX / pixelsPerCycle - maxEventDuration

    val lastVisibleCycle =
      currentCycle + (canvasWidth - playheadX) / pixelsPerCycle

    val firstLoop =
      Math.floor(firstVisibleCycle / loopLength).toInt.max(0)

    val lastLoop =
      Math.ceil(lastVisibleCycle / loopLength).toInt

    val repeatedEvents =
      for
        loop <- firstLoop to lastLoop
        event <- visualEvents
      yield event.copy(start = event.start + loop * loopLength)

    repeatedEvents.foreach { event =>
      val x = playheadX + (event.start - currentCycle) * pixelsPerCycle
      val y = event.lane * laneHeight + config.verticalPadding
      val width = event.duration * pixelsPerCycle
      val height = laneHeight

      val isActive =
        currentCycle >= event.start && currentCycle < event.start + event.duration

      if isActive then
        gc.stroke = event.color
        gc.lineWidth = 2
        gc.strokeRect(x, y, width, height)
      else
        gc.fill = event.color
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
