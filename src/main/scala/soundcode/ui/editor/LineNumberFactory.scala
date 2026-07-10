package soundcode.ui.editor

import javafx.scene.Node
import javafx.scene.control.Label

import java.util.function.IntFunction

private object LineNumberFactory:
  def apply[Segment](
      document: BlockEditorDocument[Segment],
      style: String
  ): IntFunction[Node] =
    new IntFunction[Node]:
      override def apply(line: Int): Node =
        var label = new Label()

        label.setText(
          if document.isVisualizerParagraph(line) then ""
          else document.visibleLineNumber(line).toString
        )

        label.setStyle(style)
        label.setMinWidth(24)
        label.setPrefWidth(24)

        label
