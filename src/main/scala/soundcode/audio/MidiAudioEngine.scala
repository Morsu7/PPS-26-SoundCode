package soundcode.audio

import javax.sound.midi.{MidiChannel, MidiSystem, Synthesizer}
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import scala.util.Try

/** Backend audio basato su `javax.sound.midi` (incluso nel JDK, nessuna dipendenza esterna).
  *
  * Ogni evento fa un `noteOn` immediato e schedula il relativo `noteOff` dopo `durationMs`
  * su un executor daemon dedicato, così il loop di tick dell'[[soundcode.engine.AudioPlayer]]
  * non si blocca mai.
  */
final class MidiAudioEngine private (
    synth: Synthesizer,
    noteChannel: MidiChannel,
    drumChannel: MidiChannel,
    scheduler: ScheduledExecutorService
) extends AudioEngine:

  def playNote(midiNote: Int, velocity: Int, durationMs: Long): Unit =
    fire(noteChannel, midiNote, velocity, durationMs)

  def playDrum(gmNote: Int, velocity: Int, durationMs: Long): Unit =
    fire(drumChannel, gmNote, velocity, durationMs)

  private def fire(channel: MidiChannel, note: Int, velocity: Int, durationMs: Long): Unit =
    val n = note.max(0).min(127)
    val v = velocity.max(1).min(127) // velocity 0 equivarrebbe a un note-off
    channel.noteOn(n, v)
    val noteOff: Runnable = () => channel.noteOff(n)
    scheduler.schedule(noteOff, durationMs.max(1), TimeUnit.MILLISECONDS)

  def close(): Unit =
    Try(scheduler.shutdownNow())
    Try(synth.close())

object MidiAudioEngine:
  private val NoteChannelIdx = 0
  private val DrumChannelIdx = 9 // General MIDI: il canale 10 (indice 9) è dedicato alle percussioni

  /** Apre il sintetizzatore di default del sistema.
    * Ritorna None (senza lanciare) in ambienti headless / senza synth / senza soundbank.
    */
  def create(): Option[MidiAudioEngine] = Try {
    val synth = MidiSystem.getSynthesizer
    synth.open()
    val channels = synth.getChannels
    require(channels.length > DrumChannelIdx, "Il synth espone troppi pochi canali MIDI")
    val exec = Executors.newSingleThreadScheduledExecutor { (r: Runnable) =>
      val t = new Thread(r, "midi-noteoff")
      t.setDaemon(true)
      t
    }
    new MidiAudioEngine(synth, channels(NoteChannelIdx), channels(DrumChannelIdx), exec)
  }.toOption
