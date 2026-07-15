package soundcode.ui

import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.scene.Scene
import javafx.scene.input.{
  KeyCode,
  KeyCodeCombination,
  KeyCombination
}

import scalafx.geometry.Insets
import scalafx.scene.control.{Button, ToolBar}
import scalafx.scene.layout.BorderPane

import scala.annotation.nowarn

import soundcode.domain.Tempo
import soundcode.mvu.{AppModel, Msg}
import soundcode.ui.editor.BlockEditorView

class MainView(
    dispatch: Msg => Unit,
    baseTempo: Tempo
):

  private val editorView =
    new BlockEditorView(baseTempo = baseTempo)

  private val errorBanner = new ErrorBanner(
    onDismiss = () => dispatch(Msg.ErrorDismissed)
  )

  private def requestPlay(): Unit =
    dispatch(Msg.PlayRequested)

  private def requestStop(): Unit =
    dispatch(Msg.StopRequested)

  private def requestCodeUpdate(): Unit =
    dispatch(
      Msg.CodeUpdateRequested(editorView.currentCode)
    )

  private def installShortcuts(scene: Scene): Unit =
    val accelerators = scene.getAccelerators

    accelerators.put(
      new KeyCodeCombination(
        KeyCode.ENTER,
        KeyCombination.CONTROL_DOWN
      ),
      () => requestPlay()
    )

    accelerators.put(
      new KeyCodeCombination(
        KeyCode.PERIOD,
        KeyCombination.CONTROL_DOWN
      ),
      () => requestStop()
    )

    accelerators.put(
      new KeyCodeCombination(
        KeyCode.U,
        KeyCombination.CONTROL_DOWN
      ),
      () => requestCodeUpdate()
    )

  def render(state: AppModel): Unit =
    editorView.render(state)
    errorBanner.render(state.errors)

    if state.isPlaying then
      editorView.play()
    else
      editorView.stop()

  @nowarn("msg=Implicit parameters should be provided with a `using` clause")
  private def toolbar: ToolBar =
    new ToolBar:
      style =
        s"${UITheme.backgroundStyle} -fx-padding: 6;"

      content = Seq(
        new Button("Play"):
          onAction = _ => requestPlay()
        ,
        new Button("Stop"):
          onAction = _ => requestStop()
        ,
        new Button("Update"):
          onAction = _ => requestCodeUpdate()
      )

  val root: BorderPane = new BorderPane:
    padding = Insets(10)
    style = UITheme.backgroundStyle

    top = toolbar
    center = editorView.root
    bottom = errorBanner.root

  def currentCode: String =
    editorView.currentCode

  root.delegate
    .sceneProperty()
    .addListener(
      new ChangeListener[Scene]:
        override def changed(
            observable: ObservableValue[? <: Scene],
            oldScene: Scene,
            newScene: Scene
        ): Unit =
          if newScene != null then
            installShortcuts(newScene)
    )