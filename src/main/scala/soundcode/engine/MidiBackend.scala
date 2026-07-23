package soundcode.engine

import soundcode.domain.*
import soundcode.audio.{AudioEngine, GmDrumMap, GmInstrumentMap, MidiNote, NoteSpec}

/** Backend audio MIDI per [[AudioPlayer]]: traduce ogni evento schedulato in comandi
  * per un [[AudioEngine]] basato su `javax.sound.midi`.
  *
  *   - `Sound.NoteInText`   -> nota intonata (nome nota -> numero MIDI via [[MidiNote]])
  *   - `Sound.SampleInText` -> percussione General MIDI (via [[GmDrumMap]], canale 10)
  *   - `Sound.Rest`         -> ignorato (nessun suono)
  *
  * Gli effetti presenti nelle estensioni vengono resi udibili traducendoli in parametri MIDI:
  *   - `Gain`    -> velocity della nota (per-nota)
  *   - `Pan`     -> posizione stereo (CC10)
  *   - `Room`    -> riverbero (CC91)
  *   - `LowPass` -> apertura del filtro / brillantezza (CC74)
  *
  * (`HighPass`/`Delay` non hanno una resa MIDI standard: trasportati fin qui ma non ancora resi.)
  */
class MidiBackend(engine: AudioEngine = AudioEngine.default()) extends AudioBackend:

  import MidiBackend.*

  override def triggerSound(payload: AudioPayload, durationMs: Long, extensions: List[AudioPayload]): Unit =
    payload match
      case Sound.NoteInText(note, _) =>
        val noteStr = s"${note.name}${note.accidental}${note.octave}" // es. "C#4"
        MidiNote.toMidi(noteStr).foreach { midi =>
          engine.playNote(
            NoteSpec(
              midiNote = midi,
              velocity = velocityFrom(extensions),
              durationMs = durationMs,
              program = instrumentFrom(extensions),
              pan = panFrom(extensions),
              reverb = reverbFrom(extensions),
              brightness = brightnessFrom(extensions)
            )
          )
        }
      case Sound.SampleInText(sample, _) =>
        GmDrumMap.toGmNote(sample.value).foreach(engine.playDrum(_, velocityFrom(extensions), durationMs))
      case _ => () // Sound.Rest, AudioEffect

  /** Rilascia le risorse del motore audio (synth + executor). */
  override def close(): Unit = engine.close()

  /** Inoltra il volume master al motore audio. */
  override def setVolume(level: Double): Unit = engine.setVolume(level)

object MidiBackend:
  private val DefaultVelocity = 96 // corrisponde a gain(1): assenza di gain = gain neutro
  private val DefaultProgram = 0 // Acoustic Grand Piano

  // Range di frequenza di taglio (Hz) del lpf, mappato sul CC74 in scala logaritmica.
  private val MinCutoffHz = 20.0
  private val MaxCutoffHz = 20000.0

  /** Ricava il programma GM da un'eventuale estensione sound
    * (es. `note("c").sound("piano")` porta un `SampleInText("piano")`); default: piano.
    */
  private def instrumentFrom(extensions: List[AudioPayload]): Int =
    extensions
      .collectFirst { case Sound.SampleInText(s, _) => GmInstrumentMap.toProgram(s.value) }
      .flatten
      .getOrElse(DefaultProgram)

  /** `gain` come moltiplicatore sulla velocity di base (96), limitato a 1..127. */
  private def velocityFrom(extensions: List[AudioPayload]): Int =
    extensions
      .collectFirst { case AudioEffect.Gain(g) => clamp(Math.round(DefaultVelocity * g.value).toInt, 1, 127) }
      .getOrElse(DefaultVelocity)

  /** `pan` 0.0..1.0 (0 = sinistra, 1 = destra) -> valore CC10 0..127; assente -> None (centro). */
  private def panFrom(extensions: List[AudioPayload]): Option[Int] =
    extensions.collectFirst { case AudioEffect.Pan(p) => clamp(Math.round(p.value * 127).toInt, 0, 127) }

  /** `room` (quantità di riverbero, ~0..1) -> valore CC91 0..127. */
  private def reverbFrom(extensions: List[AudioPayload]): Option[Int] =
    extensions.collectFirst { case AudioEffect.Room(r) => clamp(Math.round(r.value * 127).toInt, 0, 127) }

  /** `lpf` (frequenza di taglio in Hz) -> CC74 0..127 su scala logaritmica:
    * più alto il taglio, più aperto/brillante il suono.
    */
  private def brightnessFrom(extensions: List[AudioPayload]): Option[Int] =
    extensions.collectFirst { case AudioEffect.LowPass(hz) => hzToCc(hz.value) }

  private def hzToCc(hz: Double): Int =
    val clamped = hz.max(MinCutoffHz).min(MaxCutoffHz)
    val ratio = Math.log(clamped / MinCutoffHz) / Math.log(MaxCutoffHz / MinCutoffHz)
    clamp(Math.round(ratio * 127).toInt, 0, 127)

  private def clamp(x: Int, lo: Int, hi: Int): Int = x.max(lo).min(hi)
