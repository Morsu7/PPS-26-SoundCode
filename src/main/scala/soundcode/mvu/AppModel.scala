package soundcode.mvu

import soundcode.domain.*

final case class AppModel(
   positions: Set[TextPosition] = Set.empty,
   timelines: List[Seq[ScheduledEvent[AudioPayload]]] = List.empty,
   errors: Option[String]= None,
   isPlaying: Boolean = false
)
