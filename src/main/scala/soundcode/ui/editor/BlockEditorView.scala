package soundcode.ui.editor

import javafx.scene.Node
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

import java.util.function.{BiConsumer, Function}
import scala.jdk.CollectionConverters.*
import soundcode.domain.ScheduledEvent
import soundcode.domain.AudioPayload
import soundcode.domain.Tempo
import soundcode.domain.VisualizerRequest
import soundcode.domain.VisualizerKind
import soundcode.ui.visualizer.PianorollView
import soundcode.ui.visualizer.OscilloscopeView

object BlockEditorView:
  private final case class EditorParagraphStyle(
      visualizerSpace: Double = 0
  )

  private final case class RenderedVisualizersState(
      timelines: List[Seq[ScheduledEvent[AudioPayload]]],
      requests: List[VisualizerRequest],
      tempo: Tempo,
      timelineRevision: Long
  )

  private final case class AnchoredVisualizer(
      anchorOffset: Int,
      view: AnimatedView
  )

final class BlockEditorView(
    initialCode: String =
      """|note("<c4 e4 g4 e4> <a3 c4 e4 c4> <f3 a3 c4 a3> <g3 b3 d4 b3>").sound("piano")
     |sound("bd hh [cp, hh] hh")
     |""".stripMargin.trim
):
  import BlockEditorView.*

  private var anchoredVisualizers = Vector.empty[AnchoredVisualizer]

  private val VisualizerHeight = 120.0
  private val VisualizerSpacing = 16.0
  private val ReservedVisualizerSpace = VisualizerHeight + VisualizerSpacing

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
  private[ui] def editorArea: GenericStyledArea[?, String, String] = area

  private var visualizersState: Option[RenderedVisualizersState] = None

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

    anchoredVisualizers.foreach { decoration =>
      val node = decoration.view.root.delegate

      node.setManaged(false)
      node.setMouseTransparent(true)
      node.setVisible(false)

      visualizerOverlay.getChildren.add(node)
    }

    Platform.runLater {
      layoutVisualizers()
    }

  private def updateVisualizerAnchors(
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
        then math.max(0, changePosition - 1)
        else if visualizer.anchorOffset >= removedEnd then
          math.max(0, visualizer.anchorOffset + delta)
        else visualizer.anchorOffset

      visualizer.copy(anchorOffset = updatedOffset)
    }

  private def layoutVisualizers(): Unit =
    if visualizerOverlay.getScene == null then return

    anchoredVisualizers.foreach { visualizer =>
      val node = visualizer.view.root.delegate
      val anchorLine = lineForOffset(visualizer.anchorOffset)

      if anchorLine < 0 || anchorLine >= area.getParagraphs.size()
      then node.setVisible(false)
      else
        val bounds =
          area.getParagraphBoundsOnScreen(anchorLine)

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

  private def applyVisualizerSpacing(): Unit =
    (0 until area.getParagraphs.size()).foreach { paragraphIndex =>
      area.setParagraphStyle(
        paragraphIndex,
        EditorParagraphStyle()
      )
    }

    anchoredVisualizers.foreach { visualizer =>
      val anchorLine = lineForOffset(visualizer.anchorOffset)

      if anchorLine >= 0 &&
        anchorLine < area.getParagraphs.size()
      then
        area.setParagraphStyle(
          anchorLine,
          EditorParagraphStyle(
            visualizerSpace = ReservedVisualizerSpace
          )
        )
    }

    Platform.runLater {
      layoutVisualizers()
    }

  def render(state: AppModel): Unit =
    val nextVisualizersState = RenderedVisualizersState(
      timelines = state.timelines,
      requests = state.visualizers,
      tempo = state.tempo,
      timelineRevision = state.timelineRevision
    )

    val isStaleVisualizerState =
      visualizersState.exists(
        _.timelineRevision > nextVisualizersState.timelineRevision
      )

    if !isStaleVisualizerState && !visualizersState.contains(nextVisualizersState) then
      renderVisualizers(
        nextState = nextVisualizersState,
        isPlaying = state.isPlaying
      )
      visualizersState = Some(nextVisualizersState)
      playbackHighlightsEnabled = true

    anchoredVisualizers.foreach {
      case AnchoredVisualizer(_, oscilloscope: OscilloscopeView) =>
        oscilloscope.updateNotes(state.activeNotes)

      case _ => ()
    }

    renderHighlights(state)

  def currentCode: String =
    normalizeNewlines(
      area.getText
    )

  def play(): Unit =
    anchoredVisualizers.foreach(_.view.play())

  def stop(): Unit =
    anchoredVisualizers.foreach(_.view.stop())

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
      .subscribe { change =>
        if !replacingCode then
          playbackHighlightsEnabled = false

          updateVisualizerAnchors(
            changePosition = change.getPosition,
            removedLength = change.getRemoved.length,
            insertedLength = change.getInserted.length
          )

          Platform.runLater {
            applyVisualizerSpacing()
          }

        scheduleHighlight()
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

  private def renderHighlights(state: AppModel): Unit =
    val playbackPositions =
      Option.when(playbackHighlightsEnabled)(state.positions)
        .getOrElse(Set.empty)
  
    SyntaxHighlighter.applyTo(
      area,
      playbackPositions
    )

  private def renderVisualizers(
      nextState: RenderedVisualizersState,
      isPlaying: Boolean
  ): Unit =
    anchoredVisualizers.foreach(_.view.stop())

    anchoredVisualizers = nextState.requests.flatMap { request =>
      nextState.timelines
        .lift(request.streamIndex)
        .map { timeline =>
          val view =
            request.kind match
              case VisualizerKind.PianoRoll =>
                new PianorollView(timeline, nextState.tempo)

              case VisualizerKind.Oscilloscope =>
                new OscilloscopeView(nextState.tempo)

          AnchoredVisualizer(
            anchorOffset = request.sourceOffset,
            view = view
          )
        }
    }.toVector

    applyVisualizerSpacing()
    installVisualizerNodes()

    if isPlaying then
      anchoredVisualizers.foreach(_.view.play())

  private def lineForOffset(offset: Int): Int =
    val safeOffset =
      offset.max(0).min(area.getLength)

    area.getText
      .take(safeOffset)
      .count(_ == '\n')

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
