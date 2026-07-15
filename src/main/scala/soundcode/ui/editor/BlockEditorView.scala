package soundcode.ui.editor

import javafx.scene.Node
import javafx.scene.input.{KeyEvent, MouseEvent}
import javafx.scene.text.{FontSmoothingType, TextFlow}
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.{GenericStyledArea, TextExt}
import org.fxmisc.richtext.model.{SegmentOps, StyledSegment, TextOps}
import scalafx.Includes.jfxNode2sfx
import scalafx.application.Platform
import scalafx.scene.layout.StackPane
import soundcode.mvu.AppModel
import soundcode.ui.UITheme
import soundcode.ui.visualizer.AnimatedView

import java.util.function.{BiConsumer, Function, IntFunction}
import scala.jdk.CollectionConverters.*
import soundcode.domain.ScheduledEvent
import soundcode.domain.AudioPayload
import soundcode.domain.Tempo
import soundcode.domain.VisualizerRequest
import soundcode.domain.VisualizerKind
import soundcode.ui.visualizer.PianorollView

private final case class EditorParagraphStyle(
    visualizerSpace: Double = 0
)

final class BlockEditorView(
    initialCode: String =
      """|note("<c4 e4 g4 e4> <a3 c4 e4 c4> <f3 a3 c4 a3> <g3 b3 d4 b3>").sound("piano")
     |sound("bd hh [cp, hh] hh")
     |""".stripMargin.trim,
    baseTempo: Tempo
):
  private final case class VisualizerDecoration(
      anchorLine: Int,
      view: AnimatedView
  )

  private var decorations =
    Vector.empty[VisualizerDecoration]

  private val VisualizerHeight = 120.0
  private val VisualizerSpacing = 16.0
  private val ReservedVisualizerSpace = VisualizerHeight + VisualizerSpacing

  private type Segment = String

  private val segmentOps: TextOps[String, String] =
    SegmentOps.styledTextOps[String]()

  private val area: GenericStyledArea[EditorParagraphStyle, String, String] =
    new GenericStyledArea[EditorParagraphStyle, String, String](
      EditorParagraphStyle(),
      new BiConsumer[TextFlow, EditorParagraphStyle]:
        override def accept(
            textFlow: TextFlow,
            style: EditorParagraphStyle
        ): Unit =
          textFlow.setPadding(
            new javafx.geometry.Insets(
              0,
              0,
              style.visualizerSpace,
              0
            )
          )
      ,
      "",
      segmentOps,
      false, // undo based just on text changes, not style changes
      new Function[StyledSegment[String, String], Node]:
        override def apply(
            styled: StyledSegment[String, String]
        ): Node =
          val text = new TextExt(styled.getSegment)
          text.setFontSmoothingType(FontSmoothingType.GRAY)
          text.setStyle(styled.getStyle)
          text
    )

  // for testing purposes expose the underlying area
  private[ui] def editorArea = area

  private var visualizers = Vector.empty[AnimatedView]
  private var renderedTimelines = List.empty[Seq[ScheduledEvent[AudioPayload]]]
  private var renderedTempo = baseTempo
  private var renderedVisualizerRequests = List.empty[VisualizerRequest]
  private var highlightScheduled = false
  private var replacingCode = false
  private var playbackHighlightsEnabled = true

  private val scrollPane =
    new VirtualizedScrollPane(area)

  private val visualizerOverlay =
    new javafx.scene.layout.Pane()

  visualizerOverlay.setMouseTransparent(true)
  visualizerOverlay.setPickOnBounds(false)

  private val overlayClip =
    new javafx.scene.shape.Rectangle()

  overlayClip
    .widthProperty()
    .bind(visualizerOverlay.widthProperty())

  overlayClip
    .heightProperty()
    .bind(visualizerOverlay.heightProperty())

  visualizerOverlay.setClip(overlayClip)

  val root: StackPane = new StackPane:
    children = Seq(
      jfxNode2sfx(scrollPane),
      jfxNode2sfx(visualizerOverlay)
    )

  configureArea()
  setCode(initialCode)
  area.getUndoManager.forgetHistory()

  private def installVisualizerNodes(): Unit =
    import scala.jdk.CollectionConverters.*

    visualizerOverlay.getChildren.clear()

    decorations.foreach { decoration =>
      val node = decoration.view.root.delegate

      node.setManaged(false)
      node.setMouseTransparent(true)
      node.setVisible(false)

      visualizerOverlay.getChildren.add(node)
    }

    Platform.runLater {
      layoutVisualizers()
    }

  private def layoutVisualizers(): Unit =
    if visualizerOverlay.getScene == null then return

    decorations.foreach { decoration =>
      val node = decoration.view.root.delegate

      if decoration.anchorLine < 0 ||
        decoration.anchorLine >= area.getParagraphs.size()
      then node.setVisible(false)
      else
        val bounds =
          area.getParagraphBoundsOnScreen(decoration.anchorLine)

        if bounds.isEmpty then node.setVisible(false)
        else
          val paragraphBounds = bounds.get()

          val paragraphTop =
            visualizerOverlay.screenToLocal(
              paragraphBounds.getMinX,
              paragraphBounds.getMinY
            )

          // Il paragrafo comprende anche il padding inferiore.
          val visualizerY =
            paragraphTop.getY +
              paragraphBounds.getHeight -
              ReservedVisualizerSpace +
              4

          val gutterWidth = 40.0
          val horizontalMargin = 8.0

          val visualizerX =
            gutterWidth + horizontalMargin

          val visualizerWidth =
            math.max(
              0,
              visualizerOverlay.getWidth -
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

  private def applyDecorationSpacing(): Unit =
    (0 until area.getParagraphs.size()).foreach { paragraphIndex =>
      area.setParagraphStyle(
        paragraphIndex,
        EditorParagraphStyle()
      )
    }

    decorations.foreach { decoration =>
      if decoration.anchorLine >= 0 &&
        decoration.anchorLine < area.getParagraphs.size()
      then
        area.setParagraphStyle(
          decoration.anchorLine,
          EditorParagraphStyle(
            visualizerSpace = ReservedVisualizerSpace
          )
        )
    }

    Platform.runLater {
      layoutVisualizers()
    }

  def render(state: AppModel): Unit =
    if state.timelines != renderedTimelines || state.visualizers != renderedVisualizerRequests || state.tempo != renderedTempo
    then
      refreshVisualizers(state.timelines, state.visualizers, state.tempo)

      renderedTimelines = state.timelines
      renderedVisualizerRequests = state.visualizers
      renderedTempo = state.tempo

    if state.positions.isEmpty then
      playbackHighlightsEnabled = true
      SyntaxHighlighter.applyTo(area)
    else if playbackHighlightsEnabled then
      SyntaxHighlighter.applyTo(area, state.positions)
    else SyntaxHighlighter.applyTo(area)

  def currentCode: String =
    normalizeNewlines(
      area.getText
    )

  def play(): Unit =
    visualizers.foreach(_.play())

  def stop(): Unit =
    visualizers.foreach(_.stop())

  private def configureArea(): Unit =
    area.setWrapText(true)
    area.setParagraphGraphicFactory(
      LineNumberFactory(lineNumberStyle)
    )
    area.setStyle(editorStyle)

    applyEditorChrome()
    AutoPairingSupport.install(area)
    AutocompleteSupport.install(area)

    area
      .plainTextChanges()
      .subscribe { _ =>
        if !replacingCode then playbackHighlightsEnabled = false

        scheduleHighlight()
      }

    area.textProperty().addListener { (_, _, _) =>
      Platform.runLater {
        applyDecorationSpacing()
      }
    }

    visualizerOverlay.widthProperty().addListener { (_, _, _) =>
      Platform.runLater {
        layoutVisualizers()
      }
    }

    visualizerOverlay.heightProperty().addListener { (_, _, _) =>
      Platform.runLater {
        layoutVisualizers()
      }
    }

    area.viewportDirtyEvents().subscribe { _ =>
      Platform.runLater {
        layoutVisualizers()
      }
    }

  private def applyEditorChrome(): Unit =
    Platform.runLater {
      area.lookupAll(".caret").asScala.foreach {
        _.setStyle(s"-fx-stroke: ${UITheme.Foreground};")
      }

      area.lookupAll(".paragraph-box").asScala.foreach {
        _.setStyle(UITheme.backgroundStyle)
      }
    }

  private def setCode(code: String): Unit =
    val normalized = normalizeNewlines(code)

    replacingCode = true

    try
      area.replaceText(normalized)
      SyntaxHighlighter.applyTo(area)
    finally replacingCode = false

  private def refreshVisualizers(
      timelines: List[Seq[ScheduledEvent[AudioPayload]]],
      requests: List[VisualizerRequest],
      tempo: Tempo
  ): Unit =
    visualizers.foreach(_.stop())

    decorations = requests.flatMap { request =>
      timelines
        .lift(request.streamIndex)
        .map { timeline =>
          val view =
            request.kind match
              case VisualizerKind.PianoRoll =>
                new PianorollView(timeline, tempo)

          VisualizerDecoration(
            anchorLine = lineForStream(request.streamIndex),
            view = view
          )
        }
    }.toVector

    visualizers = decorations.map(_.view)

    applyDecorationSpacing()
    installVisualizerNodes()

  private def lineForStream(streamIndex: Int): Int =
    area.getText
      .split("\n", -1)
      .zipWithIndex
      .filter { case (line, _) => line.trim.nonEmpty }
      .lift(streamIndex)
      .map(_._2)
      .getOrElse(-1)

  private def scheduleHighlight(): Unit =
    if !highlightScheduled && !replacingCode then
      highlightScheduled = true

      Platform.runLater {
        try SyntaxHighlighter.applyTo(area)
        finally highlightScheduled = false
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
       |-fx-padding: 2 8 0 4;
       |-fx-alignment: top-right;
       |""".stripMargin
