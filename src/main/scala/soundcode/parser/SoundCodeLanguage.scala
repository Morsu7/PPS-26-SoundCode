package soundcode.parser

object SoundCodeLanguage:
  enum ConstructKind:
    case Generative, Transformation

  final case class Construct(
      name: String,
      snippet: String,
      detail: String,
      kind: ConstructKind
  )

  object Generative:
    val Sound = "sound"
    val Note = "note"

    val all: Vector[Construct] = Vector(
      Construct(
        Sound,
        """sound("$0")""",
        "Generates a sample pattern",
        ConstructKind.Generative
      ),
      Construct(
        Note,
        """note("$0")""",
        "Generates a note pattern",
        ConstructKind.Generative
      )
    )

  object Transformation:
    val Gain = "gain"
    val Pan = "pan"
    val Room = "room"
    val Delay = "delay"
    val LowPassFilter = "lpf"
    val HighPassFilter = "hpf"
    val Reverse = "rev"
    val FastForward = "fast"
    val SlowMotion = "slow"
    val Early = "early"
    val Late = "late"
    val Repetition = "ply"
    val Juxtaposition = "jux"
    val Offset = "off"

    val all: Vector[Construct] = Vector(
      Construct(
        Gain,
        """gain("$0")""",
        "Changes volume",
        ConstructKind.Transformation
      ),
      Construct(
        Pan,
        """pan("$0")""",
        "Moves sound left or right",
        ConstructKind.Transformation
      ),
      Construct(
        Room,
        """room("$0")""",
        "Adds room amount",
        ConstructKind.Transformation
      ),
      Construct(
        Delay,
        """delay("$0")""",
        "Adds delay amount",
        ConstructKind.Transformation
      ),
      Construct(
        LowPassFilter,
        """lpf("$0")""",
        "Applies a low-pass filter",
        ConstructKind.Transformation
      ),
      Construct(
        HighPassFilter,
        """hpf("$0")""",
        "Applies a high-pass filter",
        ConstructKind.Transformation
      ),
      Construct(
        Reverse,
        """rev()""",
        "Reverses the pattern",
        ConstructKind.Transformation
      ),
      Construct(
        FastForward,
        """fast($0)""",
        "Speeds up the pattern",
        ConstructKind.Transformation
      ),
      Construct(
        SlowMotion,
        """slow($0)""",
        "Slows down the pattern",
        ConstructKind.Transformation
      ),
      Construct(
        Early,
        """early($0)""",
        "Moves events earlier",
        ConstructKind.Transformation
      ),
      Construct(
        Late,
        """late($0)""",
        "Moves events later",
        ConstructKind.Transformation
      ),
      Construct(
        Repetition,
        """ply("$0")""",
        "Repeats events",
        ConstructKind.Transformation
      ),
      Construct(
        Juxtaposition,
        """jux($0)""",
        "Combines transformations",
        ConstructKind.Transformation
      ),
      Construct(
        Offset,
        """off("$0", )""",
        "Applies transformations with an offset",
        ConstructKind.Transformation
      )
    )

  val all: Vector[Construct] =
    Generative.all ++ Transformation.all
