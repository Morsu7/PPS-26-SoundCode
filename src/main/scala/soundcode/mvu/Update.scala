package soundcode.mvu

import soundcode.mvu.Cmd.*

object Update:
  def update(model: AppModel, msg: Msg): (AppModel, Cmd) =
    msg match
      case Msg.CodeUpdateRequested(code) => (model, Cmd.ParseAndInterpret(code))
      case Msg.CodeParsed(streams, visualizers, Some(errors)) => (model.copy(errors=Some(errors)), Cmd.NoOp)
      case Msg.CodeParsed(streams, visualizers, None) => (model.copy(errors=None), Cmd.UpdateTimeline(streams, visualizers))
      case Msg.PlayRequested => (model.copy(isPlaying = true), Cmd.StartPlayback)
      case Msg.StopRequested => (model.copy(isPlaying = false), Cmd.StopPlayback)
      case Msg.PlaybackTick(currentBeat) => (model, NoOp)
      case Msg.UpdateHighlightText(positions) => (model.copy(positions = positions), NoOp)
      case Msg.UpdateTimelines(timelines, visualizers) => (model.copy(visualizers = visualizers, timelines = timelines, timelineRevision = model.timelineRevision + 1), NoOp)
      case Msg.ErrorDismissed => (model.copy(errors = None), NoOp)
      case _ => (model, NoOp)