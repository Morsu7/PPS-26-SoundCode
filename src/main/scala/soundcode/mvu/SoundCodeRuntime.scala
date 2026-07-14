package soundcode.mvu

import soundcode.domain.Tempo
import soundcode.engine.*

class SoundCodeRuntime(initialModel: AppModel, render: AppModel => Unit, tempo: Tempo, backend: AudioBackend):
  private var model: AppModel = initialModel

  private val audioPlayer = new AudioPlayer(tempo, backend, positions => {
    dispatch(Msg.UpdateHighlightText(positions))
  })

  def dispatch(msg: Msg): Unit =
    val (nextModel, cmd) = Update.update(model, msg)

    if nextModel != model then
      model = nextModel
      render(model)

    cmd.run(dispatch, audioPlayer)

  def currentModel: AppModel = model

  def shutdown(): Unit = audioPlayer.stop()
