package soundcode.ui.editor

import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.{Rectangle, StrokeType}
import org.fxmisc.richtext.GenericStyledArea
import soundcode.domain.TextPosition
import soundcode.ui.theme.UITheme

import scala.collection.mutable

final class PlaybackHighlightOverlayManager[P](
    area: GenericStyledArea[P, String, String],
    overlay: Pane
):
  private val highlightNodes =
    mutable.Map.empty[TextPosition, Rectangle]

  private val highlightPool =
    mutable.ArrayDeque.empty[Rectangle]

  private var activePositions =
    Set.empty[TextPosition]

  overlay.setMouseTransparent(true)
  overlay.setPickOnBounds(false)

  private val overlayClip = new Rectangle()
  overlayClip.widthProperty().bind(overlay.widthProperty())
  overlayClip.heightProperty().bind(overlay.heightProperty())
  overlay.setClip(overlayClip)

  def positions: Set[TextPosition] =
    activePositions

  def render(requestedPositions: Set[TextPosition]): Unit =
    val nextPositions = validPositions(requestedPositions)
    val removedPositions = highlightNodes.keySet.diff(nextPositions)
    val addedPositions = nextPositions.diff(highlightNodes.keySet)

    removedPositions.foreach(removeHighlight)
    addedPositions.foreach(addHighlight)

    activePositions = nextPositions
    layout(addedPositions)

  def layout(
      positionsToLayout: Iterable[TextPosition] = highlightNodes.keys
  ): Unit =
    positionsToLayout.foreach { position =>
      highlightNodes.get(position).foreach { highlight =>
        highlight.setVisible(false)

        area
          .getCharacterBoundsOnScreen(
            position.startIndex,
            position.endIndex
          )
          .ifPresent { screenBounds =>
            val bounds = overlay.screenToLocal(screenBounds)
            val verticalInset = 2.0

            highlight.setX(bounds.getMinX)
            highlight.setY(bounds.getMinY + verticalInset)
            highlight.setWidth(bounds.getWidth)
            highlight.setHeight(
              (bounds.getHeight - verticalInset * 2).max(1)
            )
            highlight.setVisible(true)
          }
      }
    }

  private def validPositions(
      positions: Set[TextPosition]
  ): Set[TextPosition] =
    val textLength = area.getLength

    positions.flatMap { position =>
      val start = position.startIndex.max(0)
      val end = position.endIndex.min(textLength)

      Option.when(start < end)(TextPosition(start, end))
    }

  private def addHighlight(position: TextPosition): Unit =
    val highlight =
      highlightPool.removeHeadOption()
        .getOrElse(createHighlight())

    highlight.setVisible(false)
    highlightNodes(position) = highlight
    overlay.getChildren.add(highlight)

  private def removeHighlight(position: TextPosition): Unit =
    highlightNodes.remove(position).foreach { highlight =>
      overlay.getChildren.remove(highlight)
      highlightPool.append(highlight)
    }

  private def createHighlight(): Rectangle =
    val highlight = new Rectangle()
    highlight.setArcWidth(4)
    highlight.setArcHeight(4)
    highlight.setFill(Color.web(UITheme.String, 0.18))
    highlight.setStroke(Color.web(UITheme.String))
    highlight.setStrokeType(StrokeType.INSIDE)
    highlight.setMouseTransparent(true)
    highlight
