package soundcode.engine

import soundcode.domain.*


trait AudioBackend:
  def triggerSound(payload: AudioPayload, durationMs: Long, extensions: List[AudioPayload]): Unit
  def close(): Unit = ()

class ConsoleAudioBackend extends AudioBackend:
  override def triggerSound(payload: AudioPayload, durationMs: Long, extensions: List[AudioPayload]): Unit =
    println(s"PLAYING: $payload per ${durationMs}ms con effetti: $extensions")
