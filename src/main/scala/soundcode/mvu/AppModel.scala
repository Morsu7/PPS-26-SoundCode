package soundcode.mvu

import soundcode.domain.*

final case class AppModel(
    positions: Set[TextPosition] = Set.empty,
    timelines: List[Seq[Sound]] = List.empty,

    streams: List[Stream] = List.empty
)
