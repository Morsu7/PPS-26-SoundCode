package soundcode.mvu

import soundcode.domain.*

final case class AppModel(
    positions: Set[TextPosition] = Set.empty,
    timelines: List[Timeline[AudioPayload]] = List.empty,
    errors: Option[String] = None,
    tempo: Tempo = Tempo(0.5),
    isPlaying: Boolean = false
)
