package soundcode.ui

import scalafx.application.JFXApp3
import scalafx.scene.Scene
import soundcode.mvu.SoundCodeRuntime
import soundcode.mvu.AppModel
import soundcode.mvu.Msg
import soundcode.mvu.Update
import soundcode.engine.{MidiAudioPlayer, Scheduler, SchedulerImpl}
import soundcode.engine.Resolvable.given

object SoundCodeFrame extends JFXApp3:

  private var runtime: SoundCodeRuntime = null

  override def start(): Unit =
    given Scheduler = SchedulerImpl

    var mainView: MainView = null
    val initialModel = AppModel()
    val audioPlayer = new MidiAudioPlayer()

    runtime = SoundCodeRuntime(
      initialModel = initialModel,
      render =
        (model: AppModel) => if mainView != null then mainView.render(model),
      audioPlayer = audioPlayer
    )

    mainView = MainView(runtime.dispatch)
    mainView.render(initialModel)

    // Arma la timeline con il codice iniziale, così il primo Play produce subito suono.
    runtime.dispatch(Msg.CodeUpdateRequested(initialModel.code))

    stage = new JFXApp3.PrimaryStage:
      title = "SoundCode"
      scene = new Scene(800, 500):
        root = mainView.root

  override def stopApp(): Unit =
    if runtime != null then runtime.shutdown()
    super.stopApp()
