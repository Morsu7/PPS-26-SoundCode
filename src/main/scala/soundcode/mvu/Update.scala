package soundcode.mvu

import soundcode.mvu.Cmd.*

object Update:
  def update(model: AppModel, msg: Msg): (AppModel, Cmd) =
    msg match
      case Msg.CodeUpdateRequested(code) =>
        (
          model,
          Cmd.ParseAndInterpret(code)
        )

      /*case Msg.CodeParsed(streams, errors) =>
        println(s"Code parsed with errors: $errors")
        (
          model.copy(streams = streams),
          Cmd.UpdateTimeline(streams)
        )*/

      // Play/Stop non modificano il model (evitano un re-render che sovrascriverebbe
      // il testo non ancora confermato con "Update"): eseguono solo il comando audio.
      case Msg.PlayRequested =>
        (model, Cmd.StartPlayback)

      case Msg.StopRequested =>
        (model, Cmd.StopPlayback)

      case Msg.PlaybackTick(currentBeat) =>
        (
          // model.copy(currentBeat = currentBeat, isPlaying = true),
          model,
          NoOp
        )

      case _ =>
        (
          model,
          NoOp
        )
