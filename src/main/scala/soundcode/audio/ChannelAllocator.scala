package soundcode.audio

/** Assegna a ogni "voce" (V) un indice di canale MIDI.
  *
  *   - voci uguali riusano lo stesso canale  -> la polifonia (accordi, note ribattute)
  *     è gratis: molte note della stessa voce condividono un solo canale;
  *   - una voce nuova prende il canale successivo in round-robin;
  *   - quando le voci distinte superano i canali disponibili, i canali assegnati da più
  *     tempo vengono riassegnati (voice stealing): degrada con un piccolo glitch invece
  *     di fallire.
  *
  * Una "voce" è la combinazione di strumento + effetti per-canale (pan/reverb/brightness):
  * è ciò che rende due suoni incompatibili sullo stesso canale (es. il `jux`, che vuole
  * lo stesso strumento a sinistra e a destra nello stesso istante).
  *
  * NON è thread-safe: va usato dal solo thread che genera le note (il player).
  */
final class ChannelAllocator[V](channels: IndexedSeq[Int]):
  require(channels.nonEmpty, "Serve almeno un canale disponibile")

  private val voiceOf: Array[Option[V]] = Array.fill[Option[V]](channels.length)(None)
  private var next = 0

  /** Indice del canale MIDI da usare per questa voce. */
  def channelFor(voice: V): Int =
    val existing = voiceOf.indexWhere(_.contains(voice))
    if existing >= 0 then channels(existing)
    else
      val slot = next
      next = (next + 1) % channels.length
      voiceOf(slot) = Some(voice)
      channels(slot)
