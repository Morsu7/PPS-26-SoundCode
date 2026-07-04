package soundcode.ui.editor

import javafx.scene.Node
import javafx.scene.input.{KeyEvent, MouseEvent}
import javafx.scene.text.{FontSmoothingType, TextFlow}
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.{GenericStyledArea, TextExt}
import org.fxmisc.richtext.model.{SegmentOps, StyledSegment, TextOps}
import org.reactfx.util.{Either as RichEither}
import scalafx.Includes.jfxNode2sfx
import scalafx.application.Platform
import scalafx.scene.layout.StackPane
import soundcode.mvu.AppModel
import soundcode.ui.UITheme
import soundcode.ui.visualizer.AnimatedView

import java.util.Optional
import java.util.function.{BiConsumer, Function, IntFunction}
import scala.jdk.CollectionConverters.*

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

  private val area: GenericStyledArea[Unit, Segment, String] =
    new GenericStyledArea[Unit, Segment, String](
      (),
      new BiConsumer[TextFlow, Unit]:
        override def accept(textFlow: TextFlow, style: Unit): Unit = ()
      ,
      "",
      segmentOps,
      new Function[StyledSegment[Segment, String], Node]:
        override def apply(styled: StyledSegment[Segment, String]): Node =
          renderSegment(styled)
    )

  configureArea()
  setCodeWithVisualizers(initialCode)

  val root: StackPane = new StackPane:
    children = Seq(jfxNode2sfx(new VirtualizedScrollPane(area)))

  def render(state: AppModel): Unit =
    // setCodeWithVisualizers(state.code)
    SyntaxHighlighter.applyTo(area, state.positions)

  def currentCode: String =
    normalizeNewlines(
      area.getParagraphs.asScala
        .filterNot(paragraph => paragraph.getSegments.asScala.exists(_.isRight))
        .map(_.getText)
        .mkString("\n")
    )

  def play(): Unit =
    visualizers.foreach(_.play())

  def stop(): Unit =
    visualizers.foreach(_.stop())

  private def configureArea(): Unit =
    area.setWrapText(true)
    area.setParagraphGraphicFactory(lineNumberFactory)
    area.setStyle(editorStyle)

    applyEditorChrome()
    installVisualizerInputGuard()
    AutoPairingSupport.install(area)
    AutocompleteSupport.install(area)

    area.textProperty().addListener { (_, _, _) =>
      if !replacingCode then scheduleHighlight()
    }

  private def renderSegment(styled: StyledSegment[Segment, String]): Node =
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

  private def visualizerWrapper(view: AnimatedView): Node =
    val wrapper = new javafx.scene.layout.VBox()

    wrapper.setFillWidth(true)
    wrapper.setMinWidth(0)
    wrapper.setMaxWidth(Double.MaxValue)
    wrapper.prefWidthProperty().bind(area.widthProperty().subtract(48))
    wrapper.setStyle("-fx-padding: 4 0 8 0;")

    view.root.delegate match
      case region: javafx.scene.layout.Region =>
        region.setMinWidth(0)
        region.setMaxWidth(Double.MaxValue)
        region.prefWidthProperty().bind(wrapper.widthProperty())

      case _ =>

    wrapper.getChildren.add(view.root.delegate)
    wrapper

  private def lineNumberFactory: IntFunction[Node] =
    new IntFunction[Node]:
      override def apply(line: Int): Node =
        val label = new javafx.scene.control.Label()

        label.setText(
          if isVisualizerParagraph(line) then ""
          else visibleLineNumber(line).toString
        )

        label.setStyle(lineNumberStyle)
        label.setMinWidth(24)
        label.setPrefWidth(24)

        label

  private def applyEditorChrome(): Unit =
    Platform.runLater {
      area.lookupAll(".caret").asScala.foreach {
        _.setStyle(s"-fx-stroke: ${UITheme.Foreground};")
      }

      area.lookupAll(".paragraph-box").asScala.foreach {
        _.setStyle(UITheme.backgroundStyle)
      }
    }

  private def installVisualizerInputGuard(): Unit =
    area.addEventFilter(
      KeyEvent.KEY_TYPED,
      (event: KeyEvent) =>
        if isVisualizerParagraph(area.getCurrentParagraph) then
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

  private def isVisualizerParagraph(paragraphIndex: Int): Boolean =
    paragraphIndex >= 0 &&
      paragraphIndex < area.getParagraphs.size() &&
      area
        .getParagraph(paragraphIndex)
        .getSegments
        .asScala
        .exists(_.isRight)

  private def visibleLineNumber(paragraphIndex: Int): Int =
    (0 to paragraphIndex).count(index => !isVisualizerParagraph(index))

  private def codePositionNear(paragraphIndex: Int): Int =
    val previousCodeParagraph =
      (paragraphIndex - 1 to 0 by -1).find(index =>
        !isVisualizerParagraph(index)
      )

    val nextCodeParagraph =
      (paragraphIndex + 1 until area.getParagraphs.size())
        .find(index => !isVisualizerParagraph(index))

    previousCodeParagraph
      .map(index =>
        area.getAbsolutePosition(index, area.getParagraph(index).length())
      )
      .orElse(
        nextCodeParagraph.map(index => area.getAbsolutePosition(index, 0))
      )
      .getOrElse(0)

  private def moveCaretOutOfVisualizer(): Unit =
    val paragraphIndex = area.getCurrentParagraph

    if isVisualizerParagraph(paragraphIndex) then
      area.moveTo(codePositionNear(paragraphIndex))

  private def setCodeWithVisualizers(code: String): Unit =
    val normalized = normalizeNewlines(code)

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

  private def normalizeNewlines(text: String): String =
    text
      .replace("\r\n", "\n")
      .replace("\r", "\n")

  private def editorStyle: String =
    s"""
       |-fx-font-family: ${UITheme.FontFamily};
       |-fx-font-size: 15px;
       |-fx-background-color: ${UITheme.Background};
       |-fx-control-inner-background: ${UITheme.Background};
       |-fx-text-fill: ${UITheme.Foreground};
       |""".stripMargin

  private def lineNumberStyle: String =
    s"""
       |-fx-background-color: ${UITheme.Background};
       |-fx-text-fill: ${UITheme.Muted};
       |-fx-font-family: ${UITheme.FontFamily};
       |-fx-font-size: 13px;
       |-fx-padding: 0 8 0 4;
       |-fx-alignment: center-right;
       |""".stripMargin
