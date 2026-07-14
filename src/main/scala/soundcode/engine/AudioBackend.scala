package soundcode.engine

import soundcode.domain.*


trait AudioBackend:
  def triggerSound(payload: AudioPayload, durationMs: Long, extensions: List[AudioPayload]): Unit


class ConsoleAudioBackend extends AudioBackend:
  override def triggerSound(payload: AudioPayload, durationMs: Long, extensions: List[AudioPayload]): Unit =
    println(s"PLAYING: $payload per ${durationMs}ms con effetti: $extensions")

class SuperDirtBackend extends AudioBackend:
  override def triggerSound(payload: AudioPayload, durationMs: Long, extensions: List[AudioPayload]): Unit =
    payload match
      case Sound.SampleInText(s, _) => // Invia il comando OSC/MIDI al synth
      case _ => // Gestisci altri casi...
