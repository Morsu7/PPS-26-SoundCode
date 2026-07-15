package soundcode.ui

import scalafx.application.JFXApp3
import scalafx.scene.Scene
import scalafx.application.Platform
import soundcode.mvu.SoundCodeRuntime
import soundcode.mvu.AppModel
import soundcode.mvu.Msg
import soundcode.mvu.Update
import soundcode.engine.{Scheduler, SchedulerImpl, MidiBackend}

object SoundCodeFrame extends JFXApp3:

  private var runtime: SoundCodeRuntime = null

  override def start(): Unit =
    given Scheduler = SchedulerImpl

    val initialModel = AppModel()

    lazy val mainView: MainView = MainView(runtime.dispatch, initialModel.tempo)

    runtime = SoundCodeRuntime(
      initialModel = initialModel,
      // Il render puo' essere invocato dal thread audio (callback di highlight):
      // marshalliamo sempre sul thread JavaFX per non toccare la UI fuori da esso.
      render = model =>
        if Platform.isFxApplicationThread then mainView.render(model)
        else Platform.runLater(() => mainView.render(model)),
      backend = MidiBackend()
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
