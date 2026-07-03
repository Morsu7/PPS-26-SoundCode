package soundcode.ui.editor

import javafx.scene.Node
import javafx.scene.text.TextFlow
import org.fxmisc.richtext.TextExt
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.GenericStyledArea
import org.fxmisc.richtext.model.{SegmentOps, StyledSegment, TextOps}
import org.reactfx.util.{Either as RichEither}
import scalafx.Includes.jfxNode2sfx
import scalafx.application.Platform
import scalafx.scene.layout.StackPane
import soundcode.ui.visualizer.AnimatedView

import javafx.scene.text.FontSmoothingType
import java.util.function.{BiConsumer, Function}
import scala.jdk.CollectionConverters.*
import java.util.Optional
import soundcode.mvu.AppModel
import soundcode.ui.UITheme

final class BlockEditorView(
    initialCode: String = """|note("c4 a4").sound("piano")
           |sound("hb hd hh")
           |""".stripMargin.trim
):
  private type Segment = RichEither[String, EmbeddedVisualizerSegment]

  private val textOps = SegmentOps.styledTextOps[String]()
  private val segmentOps: TextOps[Segment, String] =
    TextOps.eitherL[String, EmbeddedVisualizerSegment, String](
      textOps,
      EmbeddedVisualizerSegmentOps,
      (_, _) => Optional.empty()
    )

  private var visualizers = Vector.empty[AnimatedView]
  private var highlightScheduled = false
  private var replacingCode = false

  private def applyEditorChrome(): Unit =
    Platform.runLater {
      area.lookupAll(".caret").asScala.foreach {
        _.setStyle(s"-fx-stroke: ${UITheme.Foreground};")
      }

      area.lookupAll(".paragraph-box").asScala.foreach {
        _.setStyle(UITheme.backgroundStyle)
      }
    }

  private def isVisualizerParagraph(paragraphIndex: Int): Boolean =
    area
      .getParagraph(paragraphIndex)
      .getSegments
      .asScala
      .exists(_.isRight)

  private def visibleLineNumber(paragraphIndex: Int): Int =
    (0 to paragraphIndex)
      .count(index => !isVisualizerParagraph(index))

  private val area: GenericStyledArea[Unit, Segment, String] =
    new GenericStyledArea[Unit, Segment, String](
      (),
      new BiConsumer[TextFlow, Unit]:
        override def accept(t: TextFlow, u: Unit): Unit = ()
      ,
      "",
      segmentOps,
      new Function[StyledSegment[Segment, String], Node]:
        override def apply(styled: StyledSegment[Segment, String]): Node =
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
                val wrapper = new javafx.scene.layout.VBox()
                wrapper.setFillWidth(true)
                wrapper.setMinWidth(0)
                wrapper.setMaxWidth(Double.MaxValue)
                wrapper
                  .prefWidthProperty()
                  .bind(area.widthProperty().subtract(48))
                wrapper.setStyle("-fx-padding: 4 0 8 0;")

                view.root.delegate match
                  case region: javafx.scene.layout.Region =>
                    region.setMinWidth(0)
                    region.setMaxWidth(Double.MaxValue)
                    region.prefWidthProperty().bind(wrapper.widthProperty())
                  case _ =>

                wrapper.getChildren.add(view.root.delegate)
                wrapper
    )

  area.setWrapText(true)
  area.setParagraphGraphicFactory(
    new java.util.function.IntFunction[Node]:
      override def apply(line: Int): Node =
        val label = new javafx.scene.control.Label()

        label.setText(
          if isVisualizerParagraph(line) then ""
          else visibleLineNumber(line).toString
        )

        label.setStyle(
          s"""
             |-fx-background-color: ${UITheme.Background};
             |-fx-text-fill: ${UITheme.Muted};
             |-fx-font-family: ${UITheme.FontFamily};
             |-fx-font-size: 13px;
             |-fx-padding: 0 8 0 4;
             |-fx-alignment: center-right;
             |""".stripMargin
        )

        label.setMinWidth(24)
        label.setPrefWidth(24)

        label
  )
  area.setStyle(
    s"""
      |-fx-font-family: ${UITheme.FontFamily};
      |-fx-font-size: 15px;
      |-fx-background-color: ${UITheme.Background};
      |-fx-control-inner-background: ${UITheme.Background};
      |-fx-text-fill: ${UITheme.Foreground};
      |""".stripMargin
  )
  applyEditorChrome()

  AutoPairingSupport.install(area)
  AutocompleteSupport.install(area)

  area.textProperty().addListener { (_, _, _) =>
    if !replacingCode then scheduleHighlight()
  }

  val root: StackPane = new StackPane:
    children = Seq(jfxNode2sfx(new VirtualizedScrollPane(area)))

  def render(state: AppModel): Unit =
    // setCodeWithVisualizers(state.code)

    SyntaxHighlighter.applyTo(area, state.positions)

  def currentCode: String =
    area.getParagraphs.asScala
      .filterNot(paragraph => paragraph.getSegments.asScala.exists(_.isRight))
      .map(_.getText)
      .mkString("\n")
      .replace("\r\n", "\n")
      .replace("\r", "\n")

  def play(): Unit =
    visualizers.foreach(_.play())

  def stop(): Unit =
    visualizers.foreach(_.stop())

  private def setCodeWithVisualizers(code: String): Unit =
    val normalized = code.replace("\r\n", "\n").replace("\r", "\n")

    replacingCode = true

    try
      area.replaceText(normalized)
      SyntaxHighlighter.applyTo(area)

      visualizers = Vector.empty

      visualizerInsertions(normalized).reverse.foreach {
        case (position, view) =>
          visualizers = visualizers :+ view
          area.insertText(position, "\n")
          area.insert(
            position + 1,
            RichEither.right[String, EmbeddedVisualizerSegment](
              EmbeddedVisualizerSegment.View(view)
            ),
            ""
          )
      }
    finally replacingCode = false

  setCodeWithVisualizers(initialCode)

  private def visualizerInsertions(code: String): Vector[(Int, AnimatedView)] =
    val lines = code.split("\n", -1).toVector

    val offsets =
      lines
        .scanLeft(0)((offset, line) => offset + line.length + 1)
        .init

    lines.zip(offsets).zipWithIndex.flatMap { case ((line, offset), index) =>
      val lineEnd = offset + line.length
      BlockEditorVisualizers.forLine(index).map(view => lineEnd -> view)
    }

  private def scheduleHighlight(): Unit =
    if !highlightScheduled then
      highlightScheduled = true

      Platform.runLater {
        highlightScheduled = false
        SyntaxHighlighter.applyTo(area)
      }
