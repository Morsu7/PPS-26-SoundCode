package soundcode.engine

import soundcode.domain.*
import soundcode.audio.MidiNote

/** Una nota attualmente in suono, pensata per i visualizzatori (es. oscilloscopio):
  * `frequencyHz` è l'altezza in Hz, `amplitude` il volume (ricavato dal `gain`, 1.0 = neutro).
  *
  * È il "buffer di note" richiesto per disegnare la forma d'onda reale: sommando
  * `amplitude * sin(2π * frequencyHz * t)` sulle note attive si ottiene il segnale.
  */
final case class ActiveNote(frequencyHz: Double, amplitude: Double)

object ActiveNote:
  private val ReferenceMidi = 69 // A4
  private val ReferenceHz = 440.0
  private val DefaultAmplitude = 1.0

  /** Frequenza (Hz) di un numero MIDI, temperamento equabile: `f = 440 * 2^((midi-69)/12)`. */
  def midiToHz(midi: Int): Double =
    ReferenceHz * Math.pow(2.0, (midi - ReferenceMidi) / 12.0)

  /** Ricava la nota attiva da un payload melodico; `None` per percussioni, pause ed effetti.
    * L'ampiezza viene dal `gain` presente nelle estensioni (default 1.0).
    */
  def fromPayload(payload: AudioPayload, extensions: List[AudioPayload]): Option[ActiveNote] =
    payload match
      case Sound.NoteInText(note, _) =>
        val noteStr = s"${note.name}${note.accidental}${note.octave}"
        MidiNote.toMidi(noteStr).map(m => ActiveNote(midiToHz(m), amplitudeOf(extensions)))
      case _ => None

  private def amplitudeOf(extensions: List[AudioPayload]): Double =
    extensions.collectFirst { case AudioEffect.Gain(g, _) => g }.getOrElse(DefaultAmplitude)
