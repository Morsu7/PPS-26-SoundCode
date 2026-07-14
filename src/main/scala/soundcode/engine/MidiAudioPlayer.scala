package soundcode.engine

import soundcode.domain.*
import soundcode.audio.{AudioEngine, GmDrumMap, MidiNote}

/** [[AudioPlayer]] udibile: riempie il seam `triggerSound` traducendo ogni evento schedulato
  * in un comando per un [[AudioEngine]] MIDI.
  *
  *   - `Sound.NoteInText`   -> nota intonata (nome nota -> numero MIDI via [[MidiNote]])
  *   - `Sound.SampleInText` -> percussione General MIDI (via [[GmDrumMap]])
  *
  * La classe base `AudioPlayer` resta invariata, così i test esistenti che fanno override di
  * `triggerSound` continuano a funzionare senza modifiche.
  */
/*final class MidiAudioPlayer(cps: Double = 0.5, engine: AudioEngine = AudioEngine.default())(using
    Scheduler,
    Resolvable[Pattern]
) extends AudioPlayer(cps):

  private val DefaultVelocity = 96

  override protected def triggerSound(element: Element, durationMs: Long, extensions: List[Element]): Unit =
    element match
      case Sound.NoteInText(note, _) =>
        MidiNote.toMidi(note.value).foreach(engine.playNote(_, DefaultVelocity, durationMs))
      case Sound.SampleInText(sample, _) =>
        GmDrumMap.toGmNote(sample.value).foreach(engine.playDrum(_, DefaultVelocity, durationMs))
      case _ => ()

  /** Ferma la riproduzione e rilascia le risorse dell'engine. */
  def close(): Unit =
    stop()
    engine.close()*/
