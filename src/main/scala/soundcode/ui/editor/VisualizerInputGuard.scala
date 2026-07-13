package soundcode.ui.editor

import javafx.scene.input.{KeyEvent, MouseEvent}
import scalafx.application.Platform
import org.fxmisc.richtext.GenericStyledArea

private object VisualizerInputGuard:
  def install[Segment](
      area: GenericStyledArea[?, Segment, ?],
      document: BlockEditorDocument[Segment]
  ): Unit =
    def moveCaretOutOfVisualizer(): Unit =
      val paragraphIndex = area.getCurrentParagraph

      if document.isVisualizerParagraph(paragraphIndex) then
        area.moveTo(document.codePositionNear(paragraphIndex))

    area.addEventFilter(
      KeyEvent.KEY_TYPED,
      (event: KeyEvent) =>
        if document.isVisualizerParagraph(area.getCurrentParagraph) then
          moveCaretOutOfVisualizer()
          event.consume()
    )

    area.addEventHandler(
      MouseEvent.MOUSE_RELEASED,
      (_: MouseEvent) => Platform.runLater(moveCaretOutOfVisualizer())
    )

    area.caretPositionProperty().addListener { (_, _, _) =>
      Platform.runLater(moveCaretOutOfVisualizer())
    }
