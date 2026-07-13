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

    val initialModel = AppModel()
    val audioPlayer = new MidiAudioPlayer()

    lazy val mainView: MainView = MainView(runtime.dispatch)

    lazy val runtime: SoundCodeRuntime =
      SoundCodeRuntime(
        initialModel = initialModel,
        render = model => mainView.render(model),
        audioPlayer = audioPlayer
      )
    mainView.render(initialModel)

    // Arma la timeline con il codice iniziale, così il primo Play produce subito suono.
    runtime.dispatch(Msg.CodeUpdateRequested(mainView.currentCode))

    stage = new JFXApp3.PrimaryStage:
      title = "SoundCode"
      scene = new Scene(800, 500):
        root = mainView.root

  override def stopApp(): Unit =
    if runtime != null then runtime.shutdown()
    super.stopApp()
