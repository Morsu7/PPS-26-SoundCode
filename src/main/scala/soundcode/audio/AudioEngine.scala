package soundcode.audio

/** Parametri di una nota intonata da suonare.
  *
  * Oltre a nota/strumento/durata porta gli effetti *per-canale* come `Option`:
  * `None` significa "manopola non toccata" (il canale resta al suo default), così
  * senza effetti il comportamento è identico a prima.
  *
  *   - `pan`        -> posizione stereo (CC10), 0 = sinistra, 64 = centro, 127 = destra
  *   - `reverb`     -> quantità di riverbero (CC91)
  *   - `brightness` -> apertura del filtro / brillantezza (CC74)
  *
  * `velocity` (0..127) veicola il `gain`: è per-nota, quindi due note con gain diverso
  * possono convivere sullo stesso canale.
  */
case class NoteSpec(
    midiNote: Int,
    velocity: Int,
    durationMs: Long,
    program: Int,
    pan: Option[Int] = None,
    reverb: Option[Int] = None,
    brightness: Option[Int] = None
)

/** Backend audio astratto: il player delega qui la produzione effettiva del suono. */
trait AudioEngine:
  /** Suona una nota intonata secondo lo [[NoteSpec]] (nota, strumento GM, effetti). */
  def playNote(spec: NoteSpec): Unit

  /** Suona una percussione General MIDI (canale 10). */
  def playDrum(gmNote: Int, velocity: Int, durationMs: Long): Unit

  /** Rilascia le risorse (synth, executor). Deve essere idempotente. */
  def close(): Unit

  /** Imposta il volume master (0..100). Default: nessun effetto (es. NoopAudioEngine). */
  def setVolume(level: Double): Unit = ()

/** Engine silenzioso: fallback usato quando nessun sintetizzatore MIDI è disponibile
  * (es. ambiente headless / CI). Non produce suono e non lancia mai eccezioni.
  */
object NoopAudioEngine extends AudioEngine:
  def playNote(spec: NoteSpec): Unit = ()
  def playDrum(gmNote: Int, velocity: Int, durationMs: Long): Unit = ()
  def close(): Unit = ()

object AudioEngine:
  /** Il sintetizzatore MIDI reale se disponibile, altrimenti il NoopAudioEngine. */
  def default(): AudioEngine = MidiAudioEngine.create().getOrElse(NoopAudioEngine)
