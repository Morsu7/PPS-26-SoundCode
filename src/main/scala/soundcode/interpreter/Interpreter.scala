package soundcode.interpreter

import soundcode.parser.AST
import soundcode.parser.AST.Transformations
import soundcode.domain.*
import soundcode.domain.Pattern.*
import soundcode.domain.Sound.SampleInText

object Interpreter {

    // temp
    var found = false

    def interpret(tree: AST.ProgramAST): List[Pattern[AudioPayload]] = {
        tree.blocks.collect {
            case sb: AST.StreamBlock =>
            val (basePattern, baseType) = interpretGenerativeBlock(sb.base)

            applyExtensions(basePattern, baseType, sb.extensions)
        }
    }

    private def interpretGenerativeBlock(block: AST.GenerativeBlock): (Pattern[AudioPayload], String) = block match {
        case AST.SoundBlock(pattern) => (interpretPattern(pattern)(c => interpretSoundAtom(c)), "sound")
        case AST.NoteBlock(pattern)  => (interpretPattern(pattern)(c => interpretSoundAtom(c)), "note")
    }

    private def applyExtensions(basePattern: Pattern[AudioPayload], baseType: String, extensions: List[AST.ExtensionBlock]): Pattern[AudioPayload] = {
        val (finalBase, finalEffects, _) = extensions.foldLeft((basePattern, List.empty[Pattern[AudioPayload]], false)) {
            case ((currentBase, accEffects, foundGen), ext) => ext match {

            case AST.GenerativeExtensionBlock(block) if !foundGen =>
                val (pattern, gType) = interpretGenerativeBlock(block)
                if (gType != baseType) (currentBase, accEffects :+ pattern, true)
                else (currentBase, accEffects, false)

            case AST.TransformationExtensionBlock(transBlock) =>
                val transformed = applyTransformation(currentBase, transBlock)
                
                transformed match {
                case tw: Pattern.TimeWarp[_] =>
                    val baseWithEffects = if (accEffects.isEmpty) currentBase else Pattern.WithExtensions(currentBase, accEffects)
                    // Applichiamo il TimeWarp sopra il blocco precedente
                    (Pattern.TimeWarp(tw.modifier, baseWithEffects), List.empty, foundGen)
                    
                case atom => 
                    (currentBase, accEffects :+ atom, foundGen)
                }

            case _ => (currentBase, accEffects, foundGen)
            }
        }

        // Applichiamo gli effetti rimasti dopo l'ultima trasformazione
        if (finalEffects.isEmpty) finalBase 
        else Pattern.WithExtensions(finalBase, finalEffects)
    }

    private def applyTransformation(basePattern: Pattern[AudioPayload], transBlock: AST.Transformations.TransformationBlock): Pattern[AudioPayload] = transBlock match {
        case AST.Transformations.Gain(pattern) => interpretPattern(pattern)(c => Atom(AudioEffect.Gain(c.value)))
        case AST.Transformations.Pan(pattern) => interpretPattern(pattern)(c => Atom(AudioEffect.Pan(c.value)))
        case AST.Transformations.Room(pattern) => interpretPattern(pattern)(c => Atom(AudioEffect.Room(c.value)))
        case AST.Transformations.Delay(pattern) => interpretPattern(pattern)(c => Atom(AudioEffect.Delay(c.value, c.value, c.value))) // TODO fix parsing for delay
        case AST.Transformations.LowPassFilter(pattern) => interpretPattern(pattern)(c => Atom(AudioEffect.LowPass(c.value)))
        case AST.Transformations.HighPassFilter(pattern) => interpretPattern(pattern)(c => Atom(AudioEffect.HighPass(c.value)))

        case AST.Transformations.Reverse() => Pattern.TimeWarp(PatternModifier.Reverse, basePattern)
        case AST.Transformations.Repetition(pattern) => Pattern.TimeWarp(PatternModifier.Repetition(interpretPattern(pattern)(interpretConfigAtom)), basePattern)
        case AST.Transformations.FastForward(pattern) => Pattern.TimeWarp(PatternModifier.FastForward(interpretPattern(pattern)(interpretConfigAtom)), basePattern)
        case AST.Transformations.SlowMotion(pattern) => Pattern.TimeWarp(PatternModifier.SlowMotion(interpretPattern(pattern)(interpretConfigAtom)), basePattern)
        case AST.Transformations.Early(pattern) => Pattern.TimeWarp(PatternModifier.Early(interpretPattern(pattern)(interpretConfigAtom)), basePattern)
        case AST.Transformations.Late(pattern) => Pattern.TimeWarp(PatternModifier.Late(interpretPattern(pattern)(interpretConfigAtom)), basePattern)

        case _ => ???
    }

    private def interpretPattern[A <: AST.Atom, B](pattern: AST.Pattern[A])(buildAtom: A => Pattern[B]): Pattern[B] = {
        pattern.elems match {
            case head :: Nil => interpretSequence(head)(buildAtom)
            case seqs => Pattern.Parallel(seqs.map(interpretSequence(_)(buildAtom)))
        }
    }

    private def smartSequence[B](elements: List[Pattern[B]]): Pattern[B] = {
        elements match {
            case head :: Nil   => head          // Evita Sequence(List(X)) -> X
            case elems         => Pattern.Sequence(elems)
        }
    }

    private def smartParallel[B](elements: List[Pattern[B]]): Pattern[B] = elements match {
        case head :: Nil => head
        case elems       => Pattern.Parallel(elems)
    }

    private def interpretSequence[A <: AST.Atom, B](sequence: AST.Sequence[A])(buildAtom: A => Pattern[B]): Pattern[B] = {
        smartSequence(sequence.elems.map(interpretElement(_)(buildAtom)))
    }

    private def interpretElement[A <: AST.Atom, B](element: AST.Element[A])(buildAtom: A => Pattern[B]): Pattern[B] = element match {
        case AST.AlternationElement(pattern) =>
            smartParallel(
                pattern.elems.map { seq =>
                    Pattern.Alternation(
                        seq.elems.map(interpretElement(_)(buildAtom))
                    )
                }
            )
        case AST.SubPatternElement(pattern) => interpretPattern(pattern)(buildAtom)

        case AST.AtomElement(atom) => buildAtom(atom)

        case AST.SpeedModifiedElement(_, _, _) => ???
    }

    private def interpretSoundAtom(atom: AST.Atom): Pattern[AudioPayload] = atom match {
        case AST.Sample(value, startPos, endPos) => Pattern.Atom(Sound.SampleInText(Sample(value), TextPosition(startPos, endPos)))
        case AST.Note(name, accidental, octave, startPos, endPos) =>
            val acc: Accidental = accidental match {
                case Some("#") | Some("s") => Accidental.Sharp
                case Some("b") => Accidental.Flat
                case _ => Accidental.Natural
            }
            Pattern.Atom(Sound.NoteInText(Note(name, acc, octave), TextPosition(startPos, endPos)))
        case AST.Silence(startPos, endPos) => Pattern.Atom(Sound.Rest(TextPosition(startPos, endPos)))
        case _ => throw new IllegalArgumentException("Expected Sample or Note")
    }

    private def interpretConfigAtom(atom: AST.Atom): Pattern[Double] = atom match {
        case AST.Config(value, startPos, endPos) => Pattern.Atom(value)
        case _ => throw new IllegalArgumentException("Expected Config")
    }
}
