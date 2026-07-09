package soundcode.mvu

import soundcode.domain.*

enum Msg:
  case CodeUpdateRequested(code: String)
  case CodeParsed(streams: List[Stream], errors: Option[String])
  // possible implementation of a tick message to update the current beat in the piano roll view
  case PlaybackTick(currentBeat: Double)
  case UpdateHighlightText(positions: Set[TextPosition])
  case UpdateTimelines(timelines: List[Seq[Sound]])
  // Controllo della riproduzione audio (pulsanti Play/Stop)
  case PlayRequested
  case StopRequested
