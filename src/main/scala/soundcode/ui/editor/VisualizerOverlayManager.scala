package soundcode.ui.editor

import javafx.scene.layout.Pane
import org.fxmisc.richtext.GenericStyledArea
import scalafx.application.Platform
import soundcode.domain.{
  AudioPayload,
  ScheduledEvent,
  Tempo,
  VisualizerRequest
}
import soundcode.ui.visualizer.{
  AnimatedView,
  VisualizerViewFactory
}

final class VisualizerOverlayManager[P](
  area: GenericStyledArea[P, String, String],
  overlay: Pane,
  emptyParagraphStyle: P,
  visualizerParagraphStyle: Double => P,
  gutterWidth: Double
):
  private final case class AnchoredVisualizer(
      anchorOffset: Int,
      view: AnimatedView
  )

  private var anchoredVisualizers =
    Vector.empty[AnchoredVisualizer]

  private val VisualizerHeight = 120.0
  private val VisualizerSpacing = 16.0

  private val ReservedVisualizerSpace =
    VisualizerHeight + VisualizerSpacing

  def render(
    timelines: List[Seq[ScheduledEvent[AudioPayload]]],
    requests: List[VisualizerRequest],
    tempo: Tempo,
    isPlaying: Boolean
  ): Unit =
    stop()

    anchoredVisualizers = requests.flatMap { request =>
      timelines.lift(request.streamIndex).map { timeline =>
        AnchoredVisualizer(
          anchorOffset = request.sourceOffset,
          view = VisualizerViewFactory.create(
            kind = request.kind,
            timeline = timeline,
            tempo = tempo
          )
        )
      }
    }.toVector

    applySpacing()
    installNodes()

    if isPlaying then play()

  def updateAnchors(
    changePosition: Int,
    removedLength: Int,
    insertedLength: Int
  ): Unit =
    val removedEnd = changePosition + removedLength
    val delta = insertedLength - removedLength

    anchoredVisualizers = anchoredVisualizers.map { visualizer =>
      val updatedOffset =
        if removedLength > 0 &&
            visualizer.anchorOffset >= changePosition &&
            visualizer.anchorOffset < removedEnd
        then
          math.max(0, changePosition - 1)
        else if visualizer.anchorOffset >= removedEnd then
          math.max(0, visualizer.anchorOffset + delta)
        else
          visualizer.anchorOffset

      visualizer.copy(anchorOffset = updatedOffset)
    }

  def play(): Unit =
    anchoredVisualizers.foreach(_.view.play())

  def stop(): Unit =
    anchoredVisualizers.foreach(_.view.stop())

  private def lineForOffset(offset: Int): Int =
    val safeOffset =
      offset.max(0).min(area.getLength)

    area.getText
      .take(safeOffset)
      .count(_ == '\n')

  private def installNodes(): Unit =
    overlay.getChildren.clear()

    anchoredVisualizers.foreach { anchored =>
      val node = anchored.view.root.delegate

      node.setManaged(false)
      node.setMouseTransparent(true)
      node.setVisible(false)

      overlay.getChildren.add(node)
    }

    Platform.runLater {
      layout()
    }

  def layout(): Unit =
    if overlay.getScene == null then return

    anchoredVisualizers.foreach { visualizer =>
      val node = visualizer.view.root.delegate
      val anchorLine = lineForOffset(visualizer.anchorOffset)

      if anchorLine < 0 || anchorLine >= area.getParagraphs.size() then
        node.setVisible(false)
      else
        val bounds =
          area.getParagraphBoundsOnScreen(anchorLine)

        if bounds.isEmpty then
          node.setVisible(false)
        else
          val paragraphBounds = bounds.get()

          val paragraphTop =
            overlay.screenToLocal(
              paragraphBounds.getMinX,
              paragraphBounds.getMinY
            )

          val visualizerY =
            paragraphTop.getY +
              paragraphBounds.getHeight -
              ReservedVisualizerSpace +
              4

          val horizontalMargin = 8.0
          val visualizerX = gutterWidth + horizontalMargin

          val visualizerWidth =
            math.max(
              0,
              overlay.getWidth -
                visualizerX -
                horizontalMargin
            )

          node match
            case region: javafx.scene.layout.Region =>
              region.setMinWidth(0)
              region.setMaxWidth(Double.MaxValue)

              region.resizeRelocate(
                visualizerX,
                visualizerY,
                visualizerWidth,
                VisualizerHeight
              )

            case _ =>
              node.relocate(
                visualizerX,
                visualizerY
              )

          node.setVisible(true)
    }

  def applySpacing(): Unit =
    (0 until area.getParagraphs.size()).foreach { paragraphIndex =>
      area.setParagraphStyle(
        paragraphIndex,
        emptyParagraphStyle
      )
    }

    anchoredVisualizers.foreach { visualizer =>
      val anchorLine =
        lineForOffset(visualizer.anchorOffset)

      if anchorLine >= 0 &&
          anchorLine < area.getParagraphs.size()
      then
        area.setParagraphStyle(
          anchorLine,
          visualizerParagraphStyle(
            ReservedVisualizerSpace
          )
        )
    }

    Platform.runLater {
      layout()
    }