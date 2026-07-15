package soundcode.domain

enum VisualizerKind:
  case PianoRoll
  case Oscilloscope

final case class VisualizerRequest(
    streamIndex: Int,
    kind: VisualizerKind
)
