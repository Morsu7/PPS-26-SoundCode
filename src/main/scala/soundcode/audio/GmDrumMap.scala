package soundcode.audio

/** Mappa gli identificatori dei sample essenziali ai numeri di percussione General MIDI.
  * Questi numeri vanno suonati sul canale MIDI 10 (indice 9).
  */
object GmDrumMap:

  val map: Map[String, Int] = Map(
    "bd"  -> 36, // Bass Drum 1 (kick)
    "sn"  -> 38, // Acoustic Snare
    "rim" -> 37, // Side Stick / Rimshot
    "cp"  -> 39, // Hand Clap
    "hh"  -> 42, // Closed Hi-Hat
    "oh"  -> 46, // Open Hi-Hat
    "cr"  -> 49, // Crash Cymbal 1
    "rd"  -> 51, // Ride Cymbal 1
    "lt"  -> 45, // Low Tom
    "mt"  -> 47, // Low-Mid Tom
    "ht"  -> 50  // High Tom
  )

  /** Ritorna Some(gmNote) per un sample noto, altrimenti None (il sample verrà saltato). */
  def toGmNote(sample: String): Option[Int] = map.get(sample.trim.toLowerCase)
