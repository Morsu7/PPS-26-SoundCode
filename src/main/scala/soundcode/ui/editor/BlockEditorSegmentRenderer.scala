package soundcode.ui.editor

import javafx.scene.Node
import javafx.scene.text.FontSmoothingType
import org.fxmisc.richtext.TextExt
import org.fxmisc.richtext.model.StyledSegment
import org.reactfx.util.{Either as RichEither}
import soundcode.ui.visualizer.AnimatedView
import javafx.scene.layout.VBox
import javafx.beans.value.ObservableNumberValue
import javafx.beans.binding.Bindings

private final class BlockEditorSegmentRenderer(
    editorWidth: () => ObservableNumberValue
):
  type Segment = RichEither[String, EmbeddedVisualizerSegment]

  def render(styled: StyledSegment[Segment, String]): Node =
    val segment = styled.getSegment

    if segment.isLeft then
      val text = new TextExt(segment.getLeft)
      text.setFontSmoothingType(FontSmoothingType.GRAY)
      text.setStyle(styled.getStyle)
      text
    else
      segment.getRight match
        case EmbeddedVisualizerSegment.Empty =>
          new javafx.scene.Group()

        case EmbeddedVisualizerSegment.View(view) =>
          visualizerWrapper(view)

  def visualizerWrapper(view: AnimatedView): Node =
    val wrapper = VBox()

    wrapper.setFillWidth(true)
    wrapper.setMinWidth(0)
    wrapper.setMaxWidth(Double.MaxValue)
    wrapper
      .prefWidthProperty()
      .bind(
        Bindings.subtract(editorWidth(), 48)
      )
    wrapper.setStyle("-fx-padding: 4 0 8 0;")

    view.root.delegate match
      case region: javafx.scene.layout.Region =>
        region.setMinWidth(0)
        region.setMaxWidth(Double.MaxValue)
        region.prefWidthProperty().bind(wrapper.widthProperty())

      case _ =>

    wrapper.getChildren.add(view.root.delegate)
    wrapper
