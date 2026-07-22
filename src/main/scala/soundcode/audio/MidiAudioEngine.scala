package soundcode.audio

import javax.sound.midi.{MidiChannel, MidiSystem, Synthesizer}
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import scala.util.Try

/** Backend audio basato su `javax.sound.midi` (incluso nel JDK, nessuna dipendenza esterna).
  *
  * Ogni evento fa un `noteOn` immediato e schedula il relativo `noteOff` dopo `durationMs`
  * su un executor daemon dedicato, così il loop di tick dell'[[soundcode.engine.AudioPlayer]]
  * non si blocca mai.
  *
  * I canali melodici sono allocati dinamicamente per "voce" (strumento + effetti per-canale)
  * tramite [[ChannelAllocator]]: strumenti/pan diversi finiscono su canali diversi e non si
  * "pestano", mentre le note della stessa voce condividono un canale (polifonia). Il canale 10
  * (indice 9) resta riservato alle percussioni.
  */
final class MidiAudioEngine private (
    synth: Synthesizer,
    channels: Array[MidiChannel],
    scheduler: ScheduledExecutorService
) extends AudioEngine:

  import MidiAudioEngine.*

  private val allocator =
    new ChannelAllocator[Voice](channels.indices.filter(_ != DrumChannelIdx).toIndexedSeq)

  def playNote(spec: NoteSpec): Unit =
    val voice = Voice(spec.program, spec.pan, spec.reverb, spec.brightness)
    val channel = channels(allocator.channelFor(voice))
    channel.programChange(voice.program)
    // Inviamo solo i CC effettivamente richiesti: senza effetti il canale resta al default.
    voice.pan.foreach(channel.controlChange(Cc.Pan, _))
    voice.reverb.foreach(channel.controlChange(Cc.Reverb, _))
    voice.brightness.foreach(channel.controlChange(Cc.Brightness, _))
    fire(channel, spec.midiNote, spec.velocity, spec.durationMs)

  def playDrum(gmNote: Int, velocity: Int, durationMs: Long): Unit =
    fire(channels(DrumChannelIdx), gmNote, velocity, durationMs)

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
  private val DrumChannelIdx = 9 // General MIDI: il canale 10 (indice 9) è dedicato alle percussioni

  /** Control Change usati per rendere udibili gli effetti (numeri standard GM/MIDI). */
  private object Cc:
    val Pan = 10
    val Reverb = 91
    val Brightness = 74

  /** Identità di una voce: canali diversi servono solo quando cambia lo strumento o un
    * effetto *per-canale*. Il gain non c'è (viaggia nella velocity, che è per-nota).
    */
  private final case class Voice(program: Int, pan: Option[Int], reverb: Option[Int], brightness: Option[Int])

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
    new MidiAudioEngine(synth, channels, exec)
  }.toOption
