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

      case Msg.CodeParsed(streams, errors) =>
        println(s"Code parsed with errors: $errors")
        println(s"Parsed streams: $streams")
        (
          model.copy(streams = streams),
          NoOp
        )

      case Msg.PlayRequested =>
        (
          model.copy(isPlaying = true),
          NoOp // TODO: implement engine start
        )
      case Msg.StopRequested =>
        (
          model.copy(isPlaying = false),
          NoOp // TODO: implement engine stop
        )

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
