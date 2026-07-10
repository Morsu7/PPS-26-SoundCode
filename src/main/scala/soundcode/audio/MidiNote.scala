package soundcode.audio

/** Convertitore puro da nome nota (es. "c4", "f#5", "cs4", "eb3", "a") a numero MIDI (0..127).
  *
  * Convenzione: c4 = 60 (quindi a4 = 69, A440). Formula: midi = 12 * (octave + 1) + pitchClass + accidental.
  */
object MidiNote:

  /** Ottava usata quando il nome nota non la specifica (es. "a" -> "a4"). */
  val DefaultOctave: Int = 4

  private val pitchClass: Map[Char, Int] =
    Map('c' -> 0, 'd' -> 2, 'e' -> 4, 'f' -> 5, 'g' -> 7, 'a' -> 9, 'b' -> 11)

  /** Ritorna Some(midi) se la stringa è una nota valida e nel range 0..127, altrimenti None. */
  def toMidi(note: String): Option[Int] =
    val s = note.trim.toLowerCase
    if s.isEmpty then None
    else
      // La prima lettera è sempre la nota; una eventuale 'b' successiva è un bemolle.
      pitchClass.get(s.head).flatMap { pc =>
        val rest = s.tail
        val (accidental, octavePart) = rest.headOption match
          case Some('#') | Some('s') => (1, rest.drop(1))  // diesis
          case Some('b')             => (-1, rest.drop(1)) // bemolle
          case _                     => (0, rest)
        val octaveOpt = if octavePart.isEmpty then Some(DefaultOctave) else octavePart.toIntOption
        octaveOpt.flatMap { octave =>
          val midi = 12 * (octave + 1) + pc + accidental
          Option.when(midi >= 0 && midi <= 127)(midi)
        }
      }
