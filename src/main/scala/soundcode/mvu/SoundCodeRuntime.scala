package soundcode.mvu;

import soundcode.engine.*

class SoundCodeRuntime(
    initialModel: AppModel,
    render: AppModel => Unit,
    //audioPlayer: MidiAudioPlayer
):
  private var model: AppModel = initialModel

  def dispatch(msg: Msg): Unit =
    val (nextModel, cmd) = Update.update(model, msg)

    if nextModel != model then
      model = nextModel
      render(model)

    //cmd.run(dispatch, audioPlayer)

  def currentModel: AppModel = model

  /** Da invocare alla chiusura dell'app: ferma la riproduzione e rilascia le risorse MIDI. */
  //def shutdown(): Unit = audioPlayer.close()
