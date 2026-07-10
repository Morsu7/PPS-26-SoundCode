package soundcode.ui

import scalafx.scene.layout.BorderPane
import scalafx.geometry.Insets
import scalafx.scene.control.ToolBar
import scalafx.scene.control.Button

import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.scene.Scene
import javafx.scene.input.{KeyCode, KeyCodeCombination, KeyCombination}

import soundcode.ui.editor.BlockEditorView
import soundcode.mvu.Msg
import soundcode.mvu.AppModel
import scala.annotation.nowarn

class MainView(
    dispatch: Msg => Unit
):
  private val editorView = new BlockEditorView

  private def requestPlay(): Unit =
    dispatch(Msg.PlayRequested)

  private def requestStop(): Unit =
    dispatch(Msg.StopRequested)

  private def requestCodeUpdate(): Unit =
    dispatch(Msg.CodeUpdateRequested(editorView.currentCode))

  private def installShortcuts(scene: Scene): Unit =
    val accelerators = scene.getAccelerators

    accelerators.put(
      new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN),
      () => requestPlay()
    )

    accelerators.put(
      new KeyCodeCombination(KeyCode.PERIOD, KeyCombination.CONTROL_DOWN),
      () => requestStop()
    )

    accelerators.put(
      new KeyCodeCombination(KeyCode.U, KeyCombination.CONTROL_DOWN),
      () => requestCodeUpdate()
    )

  def render(state: AppModel): Unit =
    editorView.render(state)

    if state.isPlaying then editorView.play()
    else editorView.stop()

  val root: BorderPane = new BorderPane:
    padding = Insets(10)
    style = UITheme.backgroundStyle
    center = editorView.root
    top = toolbar

  root.delegate
    .sceneProperty()
    .addListener(
      new ChangeListener[Scene]:
        override def changed(
            observable: ObservableValue[? <: Scene],
            oldScene: Scene,
            newScene: Scene
        ): Unit =
          if newScene != null then installShortcuts(newScene)
    )

  // to remove this nowarn we should update the scalfx version to a version that supports scala 3
  @nowarn("msg=Implicit parameters should be provided with a `using` clause")
  private def toolbar: ToolBar =
    new ToolBar:
      style = s"${UITheme.backgroundStyle} -fx-padding: 6;"
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
