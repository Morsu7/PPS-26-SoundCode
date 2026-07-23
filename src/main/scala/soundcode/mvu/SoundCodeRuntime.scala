package soundcode.mvu

import soundcode.engine.*
import scala.collection.mutable

class SoundCodeRuntime(
    initialModel: AppModel,
    render: AppModel => Unit,
    backend: AudioBackend
):
  private var model: AppModel = initialModel

  private val dispatchLock = new Object
  private val pendingMessages = mutable.Queue.empty[Msg]
  private var isDraining = false

  private val audioPlayer = new AudioPlayer(
    initialModel.tempo,
    backend,
    positions => dispatch(Msg.UpdateHighlightText(positions))
  )

  def dispatch(msg: Msg): Unit =
    val mustDrain = dispatchLock.synchronized {
      pendingMessages.enqueue(msg)

      if isDraining then
        false
      else
        isDraining = true
        true
    }

    if mustDrain then drainMessages()

  private def drainMessages(): Unit =
    var continue = true

    while continue do
      val nextMessage = dispatchLock.synchronized {
        pendingMessages.dequeueFirst(_ => true) match
          case some @ Some(_) => some

          case None =>
            isDraining = false
            continue = false
            None
      }

      nextMessage.foreach(processMessage)

  private def processMessage(msg: Msg): Unit =
    val (nextModel, cmd) = Update.update(model, msg)

    if nextModel != model then
      model = nextModel
      render(model)

    cmd.run(dispatch, audioPlayer)

  def currentModel: AppModel =
    dispatchLock.synchronized(model)

  def shutdown(): Unit =
    try audioPlayer.stop()
    finally backend.close()