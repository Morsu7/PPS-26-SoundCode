package soundcode.ui

import scalafx.scene.layout.BorderPane
import scalafx.geometry.Insets
import scalafx.scene.control.ToolBar
import scalafx.scene.control.Button
import scalafx.scene.control.Label

import javafx.event.{ActionEvent, EventHandler}
import soundcode.ui.editor.BlockEditorView
import soundcode.mvu.Msg
import soundcode.mvu.AppModel
import scala.annotation.nowarn

class MainView(
    dispatch: Msg => Unit
):
  private val editorView = new BlockEditorView

  def render(model: AppModel): Unit =
    editorView.renderCode(model.code)

  val root: BorderPane = new BorderPane:
    padding = Insets(10)
    center = editorView.root
    top = toolbar

  // to remove this nowarn we should update the scalfx version to a version that supports scala 3
  @nowarn("msg=Implicit parameters should be provided with a `using` clause")
  private def toolbar: ToolBar =
    new ToolBar:
      content = Seq(
        new Button("Play"):
          onAction = _ =>
            editorView.play()
            dispatch(Msg.PlayRequested)
        ,
        new Button("Stop"):
          onAction = _ =>
            editorView.stop()
            dispatch(Msg.StopRequested)
        ,
        new Button("Update"):
          onAction =
            _ => dispatch(Msg.CodeUpdateRequested(editorView.currentCode))
      )
