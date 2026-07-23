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

  private var runtime: Option[SoundCodeRuntime] = None

  override def start(): Unit =
    given Scheduler = SchedulerImpl

    val initialModel = AppModel()

    val mainView = MainView(msg => runtime.foreach(_.dispatch(msg)))

    val initializedRuntime = SoundCodeRuntime(
      initialModel = initialModel,
      // Il render puo' essere invocato dal thread audio (callback di highlight):
      // marshalliamo sempre sul thread JavaFX per non toccare la UI fuori da esso.
      render = model =>
        if Platform.isFxApplicationThread then mainView.render(model)
        else Platform.runLater(() => mainView.render(model)),
      backend = MidiBackend()
    )
    runtime = Some(initializedRuntime)
    mainView.render(initialModel)

    // Arma la timeline con il codice iniziale, così il primo Play produce subito suono.
    initializedRuntime.dispatch(Msg.CodeUpdateRequested(mainView.currentCode))

    stage = new JFXApp3.PrimaryStage:
      title = "SoundCode"
      scene = new Scene(1280, 720):
        root = mainView.root

  override def stopApp(): Unit =
    runtime.foreach(_.shutdown())
    super.stopApp()
