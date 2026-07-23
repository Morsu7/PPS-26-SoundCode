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
import soundcode.ui.theme.UITheme

import java.util.function.{BiConsumer, Function}
import scala.jdk.CollectionConverters.*
import soundcode.domain.ScheduledEvent
import soundcode.domain.AudioPayload
import soundcode.domain.Tempo
import soundcode.domain.VisualizerRequest

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

  private case class ViewState(
      visualizers: Option[RenderedVisualizersState] = None,
      highlightScheduled: Boolean = false,
      replacingCode: Boolean = false,
      playbackHighlightsEnabled: Boolean = true
  )

final class BlockEditorView(
    initialCode: String =
      """|note("<c4 e4 g4 e4> <a3 c4 e4 c4> <f3 a3 c4 a3> <g3 b3 d4 b3>").sound("piano")
     |sound("bd hh [cp, hh] hh")
     |""".stripMargin.trim
):
  import BlockEditorView.*

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

  private var viewState = ViewState()

  private val scrollPane =
    new VirtualizedScrollPane(area)

  private val visualizerOverlay =
    new javafx.scene.layout.Pane()

  private val playbackOverlay =
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

  private val visualizerManager =
    new VisualizerOverlayManager(
      area = area,
      overlay = visualizerOverlay,
      emptyParagraphStyle = EditorParagraphStyle(),
      visualizerParagraphStyle = space =>
        EditorParagraphStyle(visualizerSpace = space),
      gutterWidth = UITheme.LineNumberWidth
    )

  val root: StackPane = new StackPane:
    children = Seq(
      jfxNode2sfx(scrollPane),
      jfxNode2sfx(visualizerOverlay),
      jfxNode2sfx(playbackOverlay)
    )

  private val playbackHighlightManager =
    new PlaybackHighlightOverlayManager(
      area = area,
      overlay = playbackOverlay
    )

  private[ui] def playbackHighlightPositions =
    playbackHighlightManager.positions

  configureArea()
  setCode(initialCode)
  area.getUndoManager.forgetHistory()

  def render(state: AppModel): Unit =
    val nextVisualizersState = RenderedVisualizersState(
      timelines = state.timelines,
      requests = state.visualizers,
      tempo = state.tempo,
      timelineRevision = state.timelineRevision
    )
    val isStaleVisualizerState =
      viewState.visualizers.exists(
        _.timelineRevision > nextVisualizersState.timelineRevision
      )

    if !isStaleVisualizerState && !viewState.visualizers.contains(nextVisualizersState) then
      renderVisualizers(
        nextState = nextVisualizersState,
        isPlaying = state.isPlaying
      )
      viewState = viewState.copy(
        visualizers = Some(nextVisualizersState),
        playbackHighlightsEnabled = true
      )

    renderHighlights(state)

  def currentCode: String =
    normalizeNewlines(
      area.getText
    )

  def play(): Unit =
    visualizerManager.play()

  def stop(): Unit =
    visualizerManager.stop()

  private def configureArea(): Unit =
    area.setWrapText(true)
    area.setParagraphGraphicFactory(
      LineNumberFactory(UITheme.lineNumberStyle)
    )
    area.setStyle(UITheme.editorStyle)

    applyEditorChrome()
    AutoPairingSupport.install(area)
    AutocompleteSupport.install(area)

    area
      .plainTextChanges()
      .subscribe { change =>
        if !viewState.replacingCode then
          viewState = viewState.copy(
            playbackHighlightsEnabled = false
          )
          playbackHighlightManager.render(Set.empty)

          visualizerManager.updateAnchors(
            changePosition = change.getPosition,
            removedLength = change.getRemoved.length,
            insertedLength = change.getInserted.length
          )

          Platform.runLater {
            visualizerManager.applySpacing()
          }

        scheduleHighlight()
      }

    visualizerOverlay.widthProperty().addListener { (_, _, _) =>
      Platform.runLater {
        visualizerManager.layout()
        playbackHighlightManager.layout()
      }
    }

    visualizerOverlay.heightProperty().addListener { (_, _, _) =>
      Platform.runLater {
        visualizerManager.layout()
        playbackHighlightManager.layout()
      }
    }

    area.viewportDirtyEvents().subscribe { _ =>
      Platform.runLater {
        visualizerManager.layout()
        playbackHighlightManager.layout()
      }
    }

  private def applyEditorChrome(): Unit =
    Platform.runLater {
      area.lookupAll(".caret").asScala.foreach {
        _.setStyle(UITheme.caretStyle)
      }

      area.lookupAll(".paragraph-box").asScala.foreach {
        _.setStyle(UITheme.backgroundStyle)
      }
    }

  private def setCode(code: String): Unit =
    val normalized = normalizeNewlines(code)

    viewState = viewState.copy(replacingCode = true)

    try
      area.replaceText(normalized)
      SyntaxHighlighter.applyTo(area)
    finally viewState = viewState.copy(replacingCode = false)

  private def renderHighlights(state: AppModel): Unit =
    val playbackPositions =
      Option.when(viewState.playbackHighlightsEnabled)(state.positions)
        .getOrElse(Set.empty)

    playbackHighlightManager.render(playbackPositions)

  private def renderVisualizers(
      nextState: RenderedVisualizersState,
      isPlaying: Boolean
  ): Unit =
    visualizerManager.render(
      timelines = nextState.timelines,
      requests = nextState.requests,
      tempo = nextState.tempo,
      isPlaying = isPlaying
    )

  private def scheduleHighlight(): Unit =
    if !viewState.highlightScheduled && !viewState.replacingCode then
      viewState = viewState.copy(highlightScheduled = true)

      Platform.runLater {
        try SyntaxHighlighter.applyTo(area)
        finally viewState = viewState.copy(highlightScheduled = false)
      }

  private def normalizeNewlines(text: String): String =
    text
      .replace("\r\n", "\n")
      .replace("\r", "\n")
