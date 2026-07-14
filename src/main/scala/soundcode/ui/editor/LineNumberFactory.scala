package soundcode.ui.editor

import javafx.scene.Node
import javafx.scene.control.Label

import java.util.function.IntFunction

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
        label.setMinWidth(40)
        label.setPrefWidth(40)
        label.setMaxWidth(40)
        label
