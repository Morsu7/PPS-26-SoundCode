package soundcode.ui.visualizer

import soundcode.domain.{
  AudioPayload,
  ScheduledEvent,
  Tempo,
  VisualizerKind
}

object VisualizerViewFactory:
  def create(
    kind: VisualizerKind,
    timeline: Seq[ScheduledEvent[AudioPayload]],
    tempo: Tempo
  ): AnimatedView =
    kind match
      case VisualizerKind.Pianoroll =>
        new PianorollView(timeline, tempo)

      case _ => null