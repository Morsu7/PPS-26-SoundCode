package soundcode.mvu

import soundcode.domain.*
import soundcode.engine.ActiveNote

enum Msg:
  case CodeUpdateRequested(code: String)
  case CodeParsed(streams: List[Pattern[AudioPayload]], visualizerRequests: List[VisualizerRequest], errors: Option[String])
  // possible implementation of a tick message to update the current beat in the piano roll view
  case PlaybackTick(currentBeat: Double)
  case UpdateHighlightText(positions: Set[TextPosition])
  case UpdateTimelines(timelines: List[Seq[ScheduledEvent[AudioPayload]]], visualizerRequests: List[VisualizerRequest])
  case ActiveNotesChanged(notes: Seq[ActiveNote])
  // Controllo della riproduzione audio (pulsanti Play/Stop)
  case PlayRequested
  case StopRequested
  // Manual error dismissal message
  case ErrorDismissed
