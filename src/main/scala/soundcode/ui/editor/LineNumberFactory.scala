package soundcode.ui.editor

import javafx.scene.Node
import javafx.scene.control.Label

import java.util.function.IntFunction
import soundcode.ui.theme.UITheme

private object LineNumberFactory:
  def apply(
      style: String
  ): IntFunction[Node] =
    new IntFunction[Node]:
      override def apply(paragraphIndex: Int): Node =
        val label = new Label(
          (paragraphIndex + 1).toString
        )

        label.setStyle(style)
        label.setMinWidth(UITheme.LineNumberWidth)
        label.setPrefWidth(UITheme.LineNumberWidth)
        label.setMaxWidth(UITheme.LineNumberWidth)
        label
