package soundcode.parser

import fastparse._, NoWhitespace._
import soundcode.parser.AST._
import soundcode.parser.AST.Transformations._
import soundcode.parser.AST.Visualizers._
import soundcode.parser.AST.Settings._

import soundcode.utils.parser.formatParseError
import soundcode.domain.VisualizerRequest

class SoundCodeParser {

  /** Entrypoint for parsing a SoundCode program It takes a string input and
    * returns a Parsed[ProgramAST] result, which can be either a success or a
    * failure.
    */
  def parseProgram(input: String): Either[String, ProgramAST] = {
    parse(input, prog(using _)) match {
      case Parsed.Success(ast, _) => Right(ast)
      case f: Parsed.Failure      => Left(formatParseError(input, f))
    }
  }

  private def prog(using P[?]): P[ProgramAST] =
    P((block).rep(1) ~ End)
      .map(streams => ProgramAST(streams.toList)
    )

  private def block(using P[?]): P[Block] =
    P((streamBlock | settingBlock) ~ ("\n".rep(1) | End))

  private def settingBlock(using P[?]): P[SettingBlock] =
    P( setting ~/ "(" ~ ws ~ configAtom ~ ws ~ ")").map {
      case (constructor, value) => constructor(value)
    }.opaque("Setting command")

  private def setting(using P[?]): P[Config => SettingBlock] = 
    P(
      SoundCodeLanguage.Settings.SetCpm.!.map(_ => Settings.CPM.apply) |
      SoundCodeLanguage.Settings.SetCps.!.map(_ => Settings.CPS.apply)
    )

  // A "stream" is a generative block optionally chained with generative or transformation blocks
  private def streamBlock(using P[?]): P[StreamBlock] =
    P(generativeBlock ~ ("." ~ extensionBlock).rep  ~ ("." ~/ visualizerBlock).?).map {
      case (baseBlock, extensionSeq, visualizerSeq) =>
        StreamBlock(baseBlock, extensionSeq.toList, visualizerSeq)
    }

  private def generativeBlock(using P[?]): P[GenerativeBlock] =
    P(soundBlock | noteBlock)

  private def extensionBlock(using P[?]): P[ExtensionBlock] =
    P(
      generativeBlock.map(GenerativeExtensionBlock.apply) 
      | transformationBlock.map(TransformationExtensionBlock.apply)
    )

  private def visualizerBlock(using P[?]): P[VisualizerBlock] =
    P(
      Index ~ SoundCodeLanguage.Visualization.Pianoroll ~/ "(" ~ ws ~ ")"
    ).map { startIndex =>
      PianoRollBlock(startIndex)
  }

  private def noteBlock(using P[?]): P[NoteBlock] =
    P(
      SoundCodeLanguage.Generative.Note ~/ "(" ~ ws ~ wrapped(pattern(
        notePatternAtom)
      ) ~ ws ~ ")"
    ).map(NoteBlock.apply)

  private def soundBlock(using P[?]): P[SoundBlock] =
    P(
      SoundCodeLanguage.Generative.Sound ~/ "(" ~ ws ~ wrapped(pattern(
        samplePatternAtom)
      ) ~ ws ~ ")"
    ).map(SoundBlock.apply)

  private def notePatternAtom(using P[?]): P[Note | Silence] = P(noteAtom | silenceAtom)

  private def samplePatternAtom(using P[?]): P[Sample | Silence] = P(sampleAtom | silenceAtom)

  private def wrapped[T](parser: => P[T])(using P[?]): P[T] =
    P(quote ~/ ws ~ parser ~ ws ~ quote)

  /*private def wrappedPattern[T <: Atom](atom: => P[T])(using
      P[?]
  ): P[Pattern[T]] =
    P(quote ~/ ws ~ pattern(atom) ~ ws ~ quote)*/

  private def pattern[T <: Atom](atom: => P[T])(using P[?]): P[Pattern[T]] =
    P(sequence(atom).rep(1, sep = P(ws ~ "," ~ ws))).map(seq =>
      Pattern(seq.toList)
    )

  // A sequence is a series of elements played in sequence, each element lasts for a fraction of the cycle
  private def sequence[T <: Atom](atom: => P[T])(using P[?]): P[Sequence[T]] =
    P(element(atom).rep(1, sep = P(ws))).map(seq => Sequence(seq.toList))

  private def element[T <: Atom](atom: => P[T])(using P[?]): P[Element[T]] = P(
    baseElement(atom) ~ (StringIn("*", "/").! ~/ configAtom).?
  ).map {
    case (base, Some((opStr, factor))) =>
      SpeedModifiedElement(base, opStr == "*", factor)
    case (base, None) =>
      base // Restituisce l'elemento base puro
  }

  // An element is either an atom (note/sample) or a sub-pattern
  private def baseElement[T <: Atom](atom: => P[T])(using P[?]): P[Element[T]] =
    P(
      atom.map(a => AtomElement[T](a): Element[T]) |
        subPattern(atom).map(s => s: Element[T]) |
        alternationPattern(atom).map(a => a: Element[T])
    )

  private def subPattern[T <: Atom](atom: => P[T])(using
      P[?]
  ): P[SubPatternElement[T]] =
    P("[" ~/ ws ~ pattern(atom) ~ ws ~ "]").map(SubPatternElement.apply)

  private def alternationPattern[T <: Atom](atom: => P[T])(using
      P[?]
  ): P[AlternationElement[T]] =
    P("<" ~/ ws ~ pattern(atom) ~ ws ~ ">").map(AlternationElement.apply)

  /*
    ---------- TRANS PARSER ----------
   */

  // clean rules for transformations permitted in juxtaposition and offset, without the unknown extension
  private def inlineTransformation(using P[?]): P[TransformationBlock] = P(
    gain | pan | room | delay | lowPassFilter | highPassFilter |
      fastForward | slowMotion | early | late | reverse | repetition
  )

  private def transformationBlock(using P[?]): P[TransformationBlock] =
    P(
      inlineTransformation
        | offset
        | juxtaposition
        | unknownTransformationFallback
    )

  private def unknownExtension(using P[?]): P[Unknown] = P(
    identifier ~ "(" ~ wrapped(pattern(configAtom)) ~ ")"
  ).map { case (name, pat) => Unknown(name, pat) }

  private def gain(using P[?]): P[Gain] =
    P(
      SoundCodeLanguage.Transformation.Gain ~/ "(" ~ ws ~ wrapped(pattern(
        configAtom
      )) ~ ws ~ ")"
    ).map(Gain.apply)

  private def pan(using P[?]): P[Pan] =
    P(
      SoundCodeLanguage.Transformation.Pan ~/ "(" ~ ws ~ wrapped(pattern(
        configAtom
      )) ~ ws ~ ")"
    ).map(Pan.apply)

  private def room(using P[?]): P[Room] =
    P(
      SoundCodeLanguage.Transformation.Room ~/ "(" ~ ws ~ wrapped(pattern(
        configAtom
      )) ~ ws ~ ")"
    ).map(Room.apply)

  private def delay(using P[?]): P[Delay] =
    P(
      SoundCodeLanguage.Transformation.Delay ~/
      "(" ~/ ws ~
      wrapped(pattern(configAtom)) ~/
      (
        (ws ~ ")").map(_ => None)
        |
        (
          ws ~ "," ~/ ws ~ wrapped(pattern(configAtom)) ~/
          (ws ~ ",").opaque("\",\" (il metodo delay richiede esattamente 1 o 3 parametri)") ~/
          ws ~ wrapped(pattern(configAtom)) ~ 
          ws ~ ")"
        ).map(Some(_))
      )
    ).map {
      case (value, Some((time, feedback))) =>
        Delay(value, Some(time), Some(feedback))
      case (value, None) =>
        Delay(value, None, None)
    }

  private def lowPassFilter(using P[?]): P[LowPassFilter] =
    P(
      SoundCodeLanguage.Transformation.LowPassFilter ~/ "(" ~ ws ~ wrapped(pattern(
        configAtom
      )) ~ ws ~ ")"
    ).map(LowPassFilter.apply)

  private def highPassFilter(using P[?]): P[HighPassFilter] =
    P(
      SoundCodeLanguage.Transformation.HighPassFilter ~/ "(" ~ ws ~ wrapped(pattern(
        configAtom
      )) ~ ws ~ ")"
    ).map(HighPassFilter.apply)

  private def fastForward(using P[?]): P[FastForward] =
    P(
      SoundCodeLanguage.Transformation.FastForward ~/ "(" ~ ws ~ wrapped(pattern(
        configAtom
      )) ~ ws ~ ")"
    ).map(FastForward.apply)

  private def slowMotion(using P[?]): P[SlowMotion] =
    P(
      SoundCodeLanguage.Transformation.SlowMotion ~/ "(" ~ ws ~ wrapped(pattern(
        configAtom
      )) ~ ws ~ ")"
    ).map(SlowMotion.apply)

  private def early(using P[?]): P[Early] =
    P(
      SoundCodeLanguage.Transformation.Early ~/ "(" ~ ws ~ wrapped(pattern(
        configAtom
      )) ~ ws ~ ")"
    )
      .map(Early.apply)

  private def late(using P[?]): P[Late] =
    P(
      SoundCodeLanguage.Transformation.Late ~/ "(" ~ ws ~ wrapped(pattern(
        configAtom
      )) ~ ws ~ ")"
    )
      .map(Late.apply)

  private def reverse(using P[?]): P[Reverse] =
    P(SoundCodeLanguage.Transformation.Reverse ~ "()").map(_ => Reverse())

  private def repetition(using P[?]): P[Repetition] =
    P(
      SoundCodeLanguage.Transformation.Repetition ~/ "(" ~ ws ~ wrapped(pattern(
        configAtom
      )) ~ ws ~ ")"
    )
      .map(Repetition.apply)

  private def juxtaposition(using P[?]): P[Juxtaposition] =
    P(
      SoundCodeLanguage.Transformation.Juxtaposition ~/ "(" ~ ws ~
        inlineTransformation.rep(1, sep = P(ws ~ "," ~ ws)) ~
        ws ~ ")"
    ).map(transSeq => Juxtaposition(transSeq.toList))

  private def offset(using P[?]): P[Offset] =
    P(
      SoundCodeLanguage.Transformation.Offset ~/ "(" ~ ws ~ wrapped(pattern(
        configAtom
      )) ~ ws ~ "," ~/ ws ~
        inlineTransformation.rep(1, sep = CharsWhileIn(" \t", 1)) ~
        ws ~ ")"
    ).map { case (offsetPattern, transSeq) =>
      Offset(offsetPattern, transSeq.toList)
    }

  // Fallback per catturare qualsiasi trasformazione che non esiste
  private def unknownTransformationFallback(using P[?]): P[TransformationBlock] =
    P(identifier.! ~/ Pass).flatMap { name =>
      Pass.filter(_ => false).opaque(s"a valid transformation. '$name' is not recognized.")
    }.map(_ => null.asInstanceOf[TransformationBlock]) // Non arriverà mai qui perché fallisce prima

  /*
    ---------- ATOMS PARSER ----------
   */
  // A note is a pitch (a-g) followed by an optional octave (0-9)
  private def noteAtom(using P[?]): P[Note] = P(
    // note name (case-insensitive, lowercase in AST)
    Index ~
      CharIn("a-gA-G").! ~
      StringIn("#", "s", "b").!.? ~
      CharsWhileIn("0-9").!.? ~
      Index
  ).map { case (startIndex, pitch, accidentalOpt, octaveOpt, endIndex) =>
    val cleanAccidental = accidentalOpt.map(a => if (a == "s") "#" else a)
    val octave = octaveOpt.map(_.toInt).getOrElse(4)
    Note(pitch.toLowerCase, cleanAccidental, octave, startIndex, endIndex)
  }.opaque("Musical Note (e.g., c4, f#5, Bb3)")

  // A sample is a string of lowercase letters and numbers
  private def sampleAtom(using P[?]): P[Sample] =
    P(Index ~ CharsWhileIn("a-z0-9").! ~ Index)
      .map { case (startIndex, value, endIndex) =>
        Sample(value, startIndex, endIndex)
      }
      .opaque("Audio Sample Identifier (alphanumeric lowercase)")

  private def configAtom(using P[?]): P[Config] =
    P(
      Index ~ (CharIn("+\\-").? ~ CharsWhileIn("0-9") ~ ("." ~ CharsWhileIn(
        "0-9"
      )).?).! ~ Index
    ).map { case (startIndex, value, endIndex) =>
      Config(value.toDouble, startIndex, endIndex)
    }.opaque("Numeric Value (integer or decimal)")


  private def silenceAtom(using P[?]): P[Silence] =
    P(Index ~ tilde ~ Index)
      .map { case (startIndex, endIndex) =>
        Silence(startIndex, endIndex)
      }
      .opaque("Silence Atom (keyword 'silence')")

  

  /*
    ---------- UTILS PARSER ----------
   */
  // parser for generic names
  private def identifier(using P[?]): P[String] = P(CharsWhileIn("a-zA-Z").!)

  // parser for whitespace (spaces and tabs)
  private def ws(using P[?]): P[Unit] = P(CharsWhileIn(" \t", 0))

  // parser for quote character
  private def quote(using P[?]): P[Unit] = P("\"").opaque("quotes (\")")

  // parser for tilde character
  private def tilde(using P[?]): P[Unit] = P("~").opaque("tilde (~)")
}
