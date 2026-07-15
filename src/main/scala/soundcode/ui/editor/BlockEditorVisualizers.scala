package soundcode.ui.editor

import soundcode.ui.visualizer.AnimatedView
import soundcode.ui.visualizer.PianorollView
import soundcode.ui.visualizer.OscilloscopeView
import soundcode.domain.{
  AudioPayload,
  Fraction,
  Interval,
  Note,
  ScheduledEvent,
  Sound,
  TextPosition
}
import soundcode.domain.Tempo
import soundcode.domain.Timeline

private object BlockEditorVisualizers:
  def forLine(
      lineIndex: Int,
      timelines: List[Timeline[AudioPayload]],
      tempo: Tempo
  ): Option[AnimatedView] =
    if lineIndex == 0 && timelines.nonEmpty then
      Some(
        new PianorollView(
          timeline = timelines(0),
          tempo = tempo
        )
      )
    else None
