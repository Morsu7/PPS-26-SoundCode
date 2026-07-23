package soundcode.domain

enum VisualizerKind:
  case Pianoroll
  case Oscilloscope

final case class VisualizerRequest(
    streamIndex: Int,
    kind: VisualizerKind,
    sourceOffset: Int
)
