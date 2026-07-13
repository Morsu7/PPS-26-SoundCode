/*package soundcode.interpreter

import soundcode.parser.AST._
import soundcode.parser.AST.Transformations._
import soundcode.domain

object Interpreter {

    private def interpretGenerative(block: GenerativeBlock): (domain.Pattern, String) = block match {
        case SoundBlock(pattern) => (interpretPattern(pattern)(interpretSoundAtom), "sound")
        case NoteBlock(pattern)  => (interpretPattern(pattern)(interpretSoundAtom), "note")
    }

    def interpret(tree: ProgramAST): List[domain.Track] = {
        tree.blocks.map { case StreamBlock(base, extensions) =>
            // note: only one sound and note block are allowed per stream, we will take the first and ignore the rest
            // the first generative block is the base and dictate the main cycle division, the rest are extensions
            // if the extensions contains a generative block, it will be a different type (sound vs note) and placed at pos 0 of the list
            
            val (basePattern, baseType): (domain.Pattern, String) = interpretGenerative(base)

            // 2. Find the extension that is NOT the same type as the base
            val generativeExtensionPattern = extensions.collectFirst {
                case GenerativeExtensionBlock(gBlock) =>
                    val (pattern, gType) = interpretGenerative(gBlock)
                    if (gType != baseType) Some(pattern) else None
            }.flatten

            val effectPatterns: List[domain.Pattern] = extensions.collect {
                case TransformationExtensionBlock(transBlock) => interpretTransformationBlock(transBlock)
            }

            domain.Track(
                base = basePattern,
                extensions = generativeExtensionPattern.toList ++ effectPatterns
            )
        }
    }

    private def interpretTransformationBlock(block: TransformationBlock): domain.Pattern = {
        block match {
            //case Reverse() => List(Seq(domain.PatternModifier.Reverse))
            case Juxtaposition(ts) => ts.flatMap(interpretTransformationBlock)
            case Offset(_, ts)     => ts.flatMap(interpretTransformationBlock)
            case Unknown(name, _)  => throw new IllegalArgumentException(s"Unknown: $name")
            
            // Gestiamo le trasformazioni come BUILDER di intere strutture Pattern
            case effectBlock =>
            block match {
                // Se nel tuo dominio Gain vuole direttamente il Pattern[Double] o Pattern[Effect],
                // usiamo interpretPattern per convertire l'albero di Config in elementi del dominio
                case Gain(p)           => interpretPattern(p)(c => domain.AudioEffect.Gain(c.value))
                case Pan(p)            => interpretPattern(p)(c => domain.AudioEffect.Pan(c.value))
                case Room(p)           => interpretPattern(p)(c => domain.AudioEffect.Room(c.value))
                //case Delay(p)          => interpretPattern(p)(c => domain.PatternModifier.Delay(c.value))
                case LowPassFilter(p)  => interpretPattern(p)(c => domain.AudioEffect.LowPass(c.value))
                case HighPassFilter(p) => interpretPattern(p)(c => domain.AudioEffect.HighPass(c.value))
                //case Repetition(p)     => interpretPattern(p)(c => domain.PatternModifier.Repetition(c.value))
                //case FastForward(p)    => interpretPattern(p)(c => domain.PatternModifier.FastForward(c.value))
                //case SlowMotion(p)     => interpretPattern(p)(c => domain.PatternModifier.SlowMotion(c.value))
                //case Early(p)          => interpretPattern(p)(c => domain.PatternModifier.Early(c.value))
                //case Late(p)           => interpretPattern(p)(c => domain.PatternModifier.Late(c.value))
                case _                 => throw new MatchError(effectBlock)
            }
        }
    }

    private def interpretPattern[A <: Atom](pattern: Pattern[A])(buildAtom: A => domain.Element): domain.Pattern = {
        pattern.elems.map { sequence => 
            sequence.elems.map { element => interpretElement(element)(buildAtom) }
        }
    }

    private def interpretElement[A <: Atom](element: Element[A])(buildAtom: A => domain.Element): domain.Element = {
        element match {
        case AtomElement(atom) => buildAtom(atom)
        case SubPatternElement(p) => domain.RecursivePattern.SubPattern(interpretPattern(p)(buildAtom))
        case AlternationElement(p) => domain.RecursivePattern.AlternationPattern(interpretPattern(p)(buildAtom))
        // TODO implement speed modifiers
        case SpeedModifiedElement(base, _, _) => interpretElement(base)(buildAtom)
        }
    }

    private def interpretSoundAtom(atom: Atom): domain.Sound = atom match {
        case Sample(_, _, _) => interpretSampleAtom(atom)
        case Note(_, _, _, _, _) => interpretNoteAtom(atom)
        case _ => throw new IllegalArgumentException("Expected Sample or Note")
    }

    private def interpretSampleAtom(atom: Atom): domain.Sound = atom match {
        case Sample(value, startIndex, endIndex) => domain.Sound.SampleInText(domain.Sample(value), domain.TextPosition(startIndex, endIndex))
        case _ => throw new IllegalArgumentException("Expected Sample")
    }

    private def interpretNoteAtom(atom: Atom): domain.Sound = atom match {
        case Note(name, accidental, octave, startIndex, endIndex) => 
        val noteStr = s"$name${accidental.getOrElse("")}${octave}"
        domain.Sound.NoteInText(domain.Note(noteStr), domain.TextPosition(startIndex, endIndex))
        case _ => throw new IllegalArgumentException("Expected Note")
    }
}*/