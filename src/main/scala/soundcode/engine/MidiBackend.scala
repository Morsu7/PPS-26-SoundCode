package soundcode.engine

import soundcode.domain.*
import soundcode.audio.{AudioEngine, GmDrumMap, MidiNote}

/** Backend audio MIDI per [[AudioPlayer]]: traduce ogni evento schedulato in comandi
  * per un [[AudioEngine]] basato su `javax.sound.midi`.
  *
  *   - `Sound.NoteInText`   -> nota intonata (nome nota -> numero MIDI via [[MidiNote]])
  *   - `Sound.SampleInText` -> percussione General MIDI (via [[GmDrumMap]], canale 10)
  *   - `Sound.Rest` / `AudioEffect` -> ignorati (nessun suono)
  *
  * Sostituisce la vecchia sottoclasse `MidiAudioPlayer`: nella nuova architettura l'audio
  * si aggancia per composizione, implementando [[AudioBackend]].
  */
class MidiBackend(engine: AudioEngine = AudioEngine.default()) extends AudioBackend:

  private val DefaultVelocity = 96

  override def triggerSound(payload: AudioPayload, durationMs: Long, extensions: List[AudioPayload]): Unit =
    payload match
      case Sound.NoteInText(note, _) =>
        val noteStr = s"${note.name}${note.accidental}${note.octave}" // es. "C#4"
        MidiNote.toMidi(noteStr).foreach(engine.playNote(_, DefaultVelocity, durationMs))
      case Sound.SampleInText(sample, _) =>
        GmDrumMap.toGmNote(sample.value).foreach(engine.playDrum(_, DefaultVelocity, durationMs))
      case _ => () // Sound.Rest, AudioEffect

  /** Rilascia le risorse del motore audio (synth + executor). */
  def close(): Unit = engine.close()
