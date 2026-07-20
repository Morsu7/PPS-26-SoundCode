package soundcode.audio

/** Mappa nomi di strumenti predefiniti (ispirati a Strudel) ai numeri di programma
  * General MIDI (0..127). Usata per `note("...").sound("nomeStrumento")`.
  *
  * Esempi: `piano`, `bass`, `saw`, `pad`. Un nome sconosciuto ritorna None
  * (il chiamante userà lo strumento di default, il piano).
  */
object GmInstrumentMap:

  val map: Map[String, Int] = Map(
    "piano"   -> 0,   // Acoustic Grand Piano
    "epiano"  -> 4,   // Electric Piano 1
    "organ"   -> 16,  // Drawbar Organ
    "guitar"  -> 24,  // Nylon Guitar
    "bass"    -> 33,  // Electric Bass (finger)
    "violin"  -> 40,  // Violin
    "strings" -> 48,  // String Ensemble 1
    "trumpet" -> 56,  // Trumpet
    "brass"   -> 61,  // Brass Section
    "sax"     -> 65,  // Alto Sax
    "flute"   -> 73,  // Flute
    "square"  -> 80,  // Lead 1 (square)
    "saw"     -> 81,  // Lead 2 (sawtooth)
    "lead"    -> 81,  // alias di saw
    "pad"     -> 88,  // Pad 1 (new age)
    "bell"    -> 14   // Tubular Bells
  )

  /** Ritorna Some(program) per un nome noto, altrimenti None. */
  def toProgram(name: String): Option[Int] = map.get(name.trim.toLowerCase)
