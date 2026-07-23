package soundcode.interpreter

import soundcode.parser.AST
import soundcode.domain.*
import soundcode.domain.Pattern.*
import soundcode.domain.Sound.SampleInText

object Interpreter {

    def extractVisualizerRequests(tree: AST.ProgramAST): List[VisualizerRequest] =
        tree.blocks.zipWithIndex.flatMap {
            case (sb: AST.StreamBlock, index) =>
            sb.visualizer.map(v => interpretVisualizerRequest(v, index)).toList

            case _ =>
            Nil
        }

    def interpretVisualizerRequest(block: AST.Visualizers.VisualizerBlock, index: Int): VisualizerRequest = block match {
        case AST.Visualizers.PianoRollBlock(streamIndex) => VisualizerRequest(index, VisualizerKind.Pianoroll, streamIndex)
    }

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
                    
                case effects: List[Pattern[AudioPayload]] => 
                    (currentBase, accEffects ++ effects, foundGen)

                case atom: Pattern[AudioPayload] =>
                    (
                        currentBase,
                        accEffects :+ atom,
                        foundGen
                    )
                }

            case _ => (currentBase, accEffects, foundGen)
            }
        }

        // Applichiamo gli effetti rimasti dopo l'ultima trasformazione
        if (finalEffects.isEmpty) finalBase 
        else Pattern.WithExtensions(finalBase, finalEffects)
    }

    private def interpretDelay(valueP: AST.Pattern[AST.Config], timeP: Option[AST.Pattern[AST.Config]], feedbackP: Option[AST.Pattern[AST.Config]]): List[Pattern[AudioEffect]] =
        (
            List(valueP -> AudioEffect.DelayVolume.apply) ++
            timeP.map(_ -> AudioEffect.DelayTime.apply).toList ++
            feedbackP.map(_ -> AudioEffect.DelayFeedback.apply).toList
        ).map{ case (pattern, constructor) =>
            interpretPattern(pattern)(c => Atom(constructor(c.value, TextPosition.some(c.startIndex, c.endIndex)))
            )
        }

    private def interpretAudioEffect(transBlock: AST.Transformations.TransformationBlock): List[Pattern[AudioPayload]] = transBlock match{
        case AST.Transformations.Gain(pattern) => List(interpretPattern(pattern)(c => Atom(AudioEffect.Gain(c.value, TextPosition.some(c.startIndex, c.endIndex)))))
        case AST.Transformations.Pan(pattern) => List(interpretPattern(pattern)(c => Atom(AudioEffect.Pan(c.value, TextPosition.some(c.startIndex, c.endIndex)))))
        case AST.Transformations.Room(pattern) => List(interpretPattern(pattern)(c => Atom(AudioEffect.Room(c.value, TextPosition.some(c.startIndex, c.endIndex)))))
        case AST.Transformations.Delay(value, time, feedback) => interpretDelay(value, time, feedback)
        case AST.Transformations.LowPassFilter(pattern) => List(interpretPattern(pattern)(c => Atom(AudioEffect.LowPass(c.value, TextPosition.some(c.startIndex, c.endIndex)))))
        case AST.Transformations.HighPassFilter(pattern) => List(interpretPattern(pattern)(c => Atom(AudioEffect.HighPass(c.value, TextPosition.some(c.startIndex, c.endIndex)))))

        case _ => List.empty
    }

    private def interpretTimeWarp(transBlock: AST.Transformations.TransformationBlock): Option[PatternModifier[AudioPayload]] = transBlock match {
        case AST.Transformations.Reverse() => Some(PatternModifier.Reverse)
        case AST.Transformations.Repetition(pattern) => Some(PatternModifier.Repetition(interpretPattern(pattern)(interpretConfigAtom)))
        case AST.Transformations.FastForward(pattern) => Some(PatternModifier.FastForward(interpretPattern(pattern)(interpretConfigAtom)))
        case AST.Transformations.SlowMotion(pattern) => Some(PatternModifier.SlowMotion(interpretPattern(pattern)(interpretConfigAtom)))
        case AST.Transformations.Early(pattern) => Some(PatternModifier.Early(interpretPattern(pattern)(interpretConfigAtom)))
        case AST.Transformations.Late(pattern) => Some(PatternModifier.Late(interpretPattern(pattern)(interpretConfigAtom)))

        case _ => None
    }

    private def interpretInnerTransformation(basePattern: Pattern[AudioPayload])(transBlock: AST.Transformations.TransformationBlock): List[Pattern[AudioEffect]] | PatternModifier[AudioPayload] =
        interpretAudioEffect(transBlock) match {
            case effects @ (_ :: _) => effects.asInstanceOf[List[Pattern[AudioEffect]]]
            case List() => interpretTimeWarp(transBlock).getOrElse(throw new IllegalArgumentException("Unknown transformation block"))
        }

    private def applyTransformation(basePattern: Pattern[AudioPayload], transBlock: AST.Transformations.TransformationBlock): Pattern[AudioPayload] | List[Pattern[AudioPayload]] = {
        interpretAudioEffect(transBlock) match {
            case effects @ (_ :: _) => effects
            case List() => interpretTimeWarp(transBlock) match {
                    case Some(modifier) => Pattern.TimeWarp(modifier, basePattern)
                    case None => transBlock match {
                        case AST.Transformations.Juxtaposition(transformations) =>
                            Pattern.TimeWarp(PatternModifier.Juxtaposition(transformations.flatMap(tb => 
                                interpretInnerTransformation(basePattern)(tb) match {
                                    case effects: List[Pattern[AudioEffect]] => effects
                                    case modifier: PatternModifier[AudioPayload] => List(modifier)
                                }
                            )), basePattern)
                        case AST.Transformations.Offset(offsetPattern, transformations) =>
                            Pattern.TimeWarp(PatternModifier.Offset(interpretPattern(offsetPattern)(interpretConfigAtom), transformations.flatMap(tb => 
                                interpretInnerTransformation(basePattern)(tb) match {
                                    case effects: List[Pattern[AudioEffect]] => effects
                                    case modifier: PatternModifier[AudioPayload] => List(modifier)
                                }
                            )), basePattern)
                        case _ => throw new IllegalArgumentException("Unknown transformation block")
                }
            }
        }
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

        case AST.SpeedModifiedElement(element, isFastForward, factor) => isFastForward match {
            case true  => Pattern.TimeWarp(PatternModifier.FastForward(interpretConfigAtom(factor)), interpretElement(element)(buildAtom))
            case false => Pattern.TimeWarp(PatternModifier.SlowMotion(interpretConfigAtom(factor)), interpretElement(element)(buildAtom))
        }
    }

    private def interpretSoundAtom(atom: AST.Atom): Pattern[AudioPayload] = atom match {
        case AST.Sample(value, startPos, endPos) => Pattern.Atom(Sound.SampleInText(Sample(value), Some(TextPosition(startPos, endPos))))
        case AST.Note(name, accidental, octave, startPos, endPos) =>
            val acc: Accidental = accidental match {
                case Some("#") | Some("s") => Accidental.Sharp
                case Some("b") => Accidental.Flat
                case _ => Accidental.Natural
            }
            Pattern.Atom(Sound.NoteInText(Note(name, acc, octave), Some(TextPosition(startPos, endPos))))
        case AST.Silence(startPos, endPos) => Pattern.Atom(Sound.Rest(Some(TextPosition(startPos, endPos))))
        case _ => throw new IllegalArgumentException("Expected Sample or Note")
    }

    private def interpretConfigAtom(atom: AST.Atom): Pattern[Double] = atom match {
        case AST.Config(value, startPos, endPos) => Pattern.Atom(value)
        case _ => throw new IllegalArgumentException("Expected Config")
    }
}
