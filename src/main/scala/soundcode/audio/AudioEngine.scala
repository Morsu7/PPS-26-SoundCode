package soundcode.audio

/** Backend audio astratto: il player delega qui la produzione effettiva del suono. */
trait AudioEngine:
  /** Suona una nota intonata. midiNote/velocity in 0..127, program = strumento GM 0..127. */
  def playNote(midiNote: Int, velocity: Int, durationMs: Long, program: Int): Unit

  /** Suona una percussione General MIDI (canale 10). */
  def playDrum(gmNote: Int, velocity: Int, durationMs: Long): Unit

  /** Rilascia le risorse (synth, executor). Deve essere idempotente. */
  def close(): Unit

/** Engine silenzioso: fallback usato quando nessun sintetizzatore MIDI è disponibile
  * (es. ambiente headless / CI). Non produce suono e non lancia mai eccezioni.
  */
object NoopAudioEngine extends AudioEngine:
  def playNote(midiNote: Int, velocity: Int, durationMs: Long, program: Int): Unit = ()
  def playDrum(gmNote: Int, velocity: Int, durationMs: Long): Unit = ()
  def close(): Unit = ()

object AudioEngine:
  /** Il sintetizzatore MIDI reale se disponibile, altrimenti il NoopAudioEngine. */
  def default(): AudioEngine = MidiAudioEngine.create().getOrElse(NoopAudioEngine)
