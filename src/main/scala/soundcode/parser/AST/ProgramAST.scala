package soundcode.parser.AST

import soundcode.parser.AST.Transformations._
import soundcode.parser.AST.Visualizers._

trait Block

// Each block contains a pattern
case class StreamBlock(base: GenerativeBlock, extensions: List[ExtensionBlock], visualizer: Option[VisualizerBlock]) extends Block
sealed trait ExtensionBlock extends Block
case class GenerativeExtensionBlock(block: GenerativeBlock) extends ExtensionBlock
case class TransformationExtensionBlock(block: TransformationBlock) extends ExtensionBlock

sealed trait GenerativeBlock extends Block
case class SoundBlock(pattern: Pattern[Sample | Silence]) extends GenerativeBlock
case class NoteBlock(pattern: Pattern[Note | Silence]) extends GenerativeBlock

// A Pattern is a series of sequences played in parallel, each sequence lasts exactly for a cycle
case class Pattern[A <: Atom](elems: List[Sequence[A]])

// A sequence is a series of elements played in sequence, each element lasts for a fraction of the cycle
case class Sequence[A <: Atom](elems: List[Element[A]])

// An element is either an atom (note/sample) or a sub-pattern 
sealed trait Element[+A <: Atom]
case class AtomElement[A <: Atom](atom: A) extends Element[A]
case class SubPatternElement[A <: Atom](pattern: Pattern[A]) extends Element[A]
case class AlternationElement[A <: Atom](pattern: Pattern[A]) extends Element[A] // A pattern contained in <> brackets, played in alternation (one element per cycle, round-robin)

case class SpeedModifiedElement[A <: Atom](
  element: Element[A], 
  isMulFactor: Boolean,
  factor: Config
) extends Element[A]

sealed trait Atom
case class Note(name: String, accidental: Option[String], octave: Int, startIndex: Int, endIndex: Int) extends Atom {
  override def toString: String = {
    val accStr = accidental.getOrElse("")
    s"$name$accStr$octave"
  }
}
case class Sample(value: String, startIndex: Int, endIndex: Int) extends Atom
case class Config(value: Double, startIndex: Int, endIndex: Int) extends Atom // Numerical values for transformations
case class Silence(startIndex: Int, endIndex: Int) extends Atom // Represents a silence in the pattern

import soundcode.utils.parser.ASTPrinter
case class ProgramAST(blocks: List[Block]) extends Block {
  override def toString: String = ASTPrinter.renderTree(this)
}