package soundcode.mvu

import soundcode.domain.*


final case class AppModel(
    positions: Set[TextPosition] = Set.empty,
    timelines: List[Seq[ScheduledEvent[AudioPayload]]] = List.empty,
    visualizers: List[VisualizerRequest] = List.empty,
    errors: Option[String] = None,
    tempo: Tempo = Tempo.default,
    isPlaying: Boolean = false,
    timelineRevision: Long = 0L,
    volume: Double = 100.0
)
