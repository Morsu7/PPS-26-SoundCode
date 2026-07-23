package soundcode.parser

object SoundCodeLanguage:
  enum ConstructKind:
    case Generative, Transformation, Visualization, Configuration

  final case class Construct(
      name: String,
      argumentLists: Vector[String],
      snippet: String,
      detail: String,
      kind: ConstructKind
  ):
    require(argumentLists.nonEmpty, "A construct must define at least one signature")

    def signature: String =
      signatures.head

    def signatures: Vector[String] =
      argumentLists.map(arguments => s"$name($arguments)")

  sealed trait ConstructCategory:
    def kind: ConstructKind
    def all: Vector[Construct]

    protected final def construct(
        name: String,
        arguments: String,
        snippet: String,
        detail: String,
        alternativeArguments: Vector[String] = Vector.empty
    ): Construct =
      Construct(
        name = name,
        argumentLists = arguments +: alternativeArguments,
        snippet = snippet,
        detail = detail,
        kind = kind
      )

  object Settings extends ConstructCategory:
    override val kind: ConstructKind = ConstructKind.Configuration

    val SetCps = "setcps"
    val SetCpm = "setcpm"

    override val all: Vector[Construct] = Vector(
      construct(
        SetCps,
        "cyclesPerSecond: number",
        "setcps($0)",
        "Sets the number of cycles per second"
      ),
      construct(
        SetCpm,
        "cyclesPerMinute: number",
        "setcpm($0)",
        "Sets the number of cycles per minute"
      )
    )

  object Generative extends ConstructCategory:
    override val kind: ConstructKind = ConstructKind.Generative

    val Sound = "sound"
    val Note = "note"

    override val all: Vector[Construct] = Vector(
      construct(
        Sound,
        """pattern: "sample pattern"""",
        """sound("$0")""",
        "Generates a sample pattern"
      ),
      construct(
        Note,
        """pattern: "note pattern"""",
        """note("$0")""",
        "Generates a note pattern"
      )
    )

  object Transformation extends ConstructCategory:
    override val kind: ConstructKind = ConstructKind.Transformation

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

    override val all: Vector[Construct] = Vector(
      construct(
        Gain,
        """amount: "numeric pattern"""",
        """gain("$0")""",
        "Changes volume"
      ),
      construct(
        Pan,
        """position: "numeric pattern"""",
        """pan("$0")""",
        "Moves sound left or right"
      ),
      construct(
        Room,
        """amount: "numeric pattern"""",
        """room("$0")""",
        "Adds room amount"
      ),
      construct(
        Delay,
        """amount: "numeric pattern"""",
        """delay("$0")""",
        "Adds delay amount",
        Vector(
          """amount: "numeric pattern", time: "numeric pattern", feedback: "numeric pattern""""
        )
      ),
      construct(
        LowPassFilter,
        """cutoff: "numeric pattern"""",
        """lpf("$0")""",
        "Applies a low-pass filter"
      ),
      construct(
        HighPassFilter,
        """cutoff: "numeric pattern"""",
        """hpf("$0")""",
        "Applies a high-pass filter"
      ),
      construct(
        Reverse,
        "",
        """rev()""",
        "Reverses the pattern"
      ),
      construct(
        FastForward,
        """factor: "numeric pattern"""",
        """fast("$0")""",
        "Speeds up the pattern"
      ),
      construct(
        SlowMotion,
        """factor: "numeric pattern"""",
        """slow("$0")""",
        "Slows down the pattern"
      ),
      construct(
        Early,
        """offset: "numeric pattern"""",
        """early("$0")""",
        "Moves events earlier"
      ),
      construct(
        Late,
        """offset: "numeric pattern"""",
        """late("$0")""",
        "Moves events later"
      ),
      construct(
        Repetition,
        """count: "numeric pattern"""",
        """ply("$0")""",
        "Repeats events"
      ),
      construct(
        Juxtaposition,
        "transformations: transformation*",
        """jux($0)""",
        "Combines transformations"
      ),
      construct(
        Offset,
        """offset: "numeric pattern", transformations: transformation*""",
        """off("$0", )""",
        "Applies transformations with an offset"
      )
    )

  object Visualization extends ConstructCategory:
    override val kind: ConstructKind = ConstructKind.Visualization

    val Pianoroll = "_pianoroll"

    override val all: Vector[Construct] = Vector(
      construct(
        Pianoroll,
        "",
        """_pianoroll()""",
        "Visualizes the pattern as a pianoroll"
      )
    )

  val categories: Vector[ConstructCategory] =
    Vector(Settings, Generative, Transformation, Visualization)

  val all: Vector[Construct] =
    categories.flatMap(_.all)
