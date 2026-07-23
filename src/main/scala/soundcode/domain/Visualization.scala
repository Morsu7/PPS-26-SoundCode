package soundcode.domain

enum VisualizerKind:
  case Pianoroll

final case class VisualizerRequest(
    streamIndex: Int,
    kind: VisualizerKind,
    sourceOffset: Int
)
