package soundcode.mvu

import soundcode.mvu.Cmd.*

object Update:
  def update(model: AppModel, msg: Msg): (AppModel, Cmd) =
    msg match
      case Msg.CodeUpdateRequested(code) => (model, Cmd.ParseAndInterpret(code))
      case Msg.CodeParsed(streams, errors) => (model.copy(errors=errors), Cmd.UpdateTimeline(streams))
      case Msg.PlayRequested => (model.copy(isPlaying = true), Cmd.StartPlayback)
      case Msg.StopRequested => (model.copy(isPlaying = false), Cmd.StopPlayback)
      case Msg.PlaybackTick(currentBeat) => (model, NoOp)
      case Msg.UpdateHighlightText(positions) => (model.copy(positions = positions), NoOp)
      case Msg.UpdateTimelines(timelinesNew) => (model.copy(timelines = timelinesNew), NoOp)
      case _ => (model, NoOp)