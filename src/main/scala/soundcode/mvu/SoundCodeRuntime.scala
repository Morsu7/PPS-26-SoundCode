package soundcode.mvu

import soundcode.domain.Tempo
import soundcode.engine.*
import scalafx.application.Platform

class SoundCodeRuntime(
    initialModel: AppModel,
    render: AppModel => Unit,
    backend: AudioBackend
):
  private var model: AppModel = initialModel

  private val audioPlayer = new AudioPlayer(
    initialModel.tempo,
    backend,
    positions => {
      Platform.runLater {
        dispatch(Msg.UpdateHighlightText(positions))
      }
    },
    notes => {
      Platform.runLater {
        dispatch(Msg.ActiveNotesChanged(notes))
      }
    }
  )

  def dispatch(msg: Msg): Unit =
    val (nextModel, cmd) = Update.update(model, msg)

    if nextModel != model then
      model = nextModel
      render(model)

    cmd.run(dispatch, audioPlayer)

  def currentModel: AppModel = model

  def shutdown(): Unit = audioPlayer.stop()
