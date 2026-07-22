package soundcode.parser.AST
import soundcode.parser.AST.{Pattern, Atom}

object Transformations {
    sealed trait TransformationBlock extends Block

    // audio effects
    case class Gain(pattern: Pattern[Config]) extends TransformationBlock
    case class Pan(pattern: Pattern[Config]) extends TransformationBlock
    case class Room(pattern: Pattern[Config]) extends TransformationBlock
    case class Delay(pattern: Pattern[Config], timePattern: Option[Pattern[Config]], feedbackPattern: Option[Pattern[Config]]) extends TransformationBlock
    case class LowPassFilter(pattern: Pattern[Config]) extends TransformationBlock
    case class HighPassFilter(pattern: Pattern[Config]) extends TransformationBlock

    case class Reverse() extends TransformationBlock
    case class Repetition(pattern: Pattern[Config]) extends TransformationBlock

    case class FastForward(pattern: Pattern[Config]) extends TransformationBlock
    case class SlowMotion(pattern: Pattern[Config]) extends TransformationBlock

    case class Early(pattern: Pattern[Config]) extends TransformationBlock
    case class Late(pattern: Pattern[Config]) extends TransformationBlock

    case class Juxtaposition(transformations: List[TransformationBlock]) extends TransformationBlock
    case class Offset(offset: Pattern[Config], transformations: List[TransformationBlock]) extends TransformationBlock

    case class Unknown(name: String, pattern: Pattern[Config]) extends TransformationBlock
}