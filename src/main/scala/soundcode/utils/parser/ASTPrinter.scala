package soundcode.utils.parser

import soundcode.parser.AST._
import soundcode.parser.AST.Transformations._
import soundcode.parser.AST.Visualizers._

object ASTPrinter {
  extension (program: ProgramAST) {
    def renderTree: String = {
      val sb = new StringBuilder("ProgramAST\n")
      program.blocks.zipWithIndex.foreach { case (block, idx) =>
        sb.append(formatBlock(block, "", idx == program.blocks.size - 1))
        if (idx < program.blocks.size - 1) sb.append("\n")
      }
      sb.toString()
    }
  }

  private def formatBlock(block: Block, indent: String, isLast: Boolean): String = {
    val marker = if (isLast) "`-- " else "|-- "
    val nextIndent = indent + (if (isLast) "    " else "|   ")
    
    block match {
      case StreamBlock(base, extensions, visualizer) =>
      val children: List[Block] =
        extensions ++ visualizer.toList

      val baseStr =
        s"$indent${marker}StreamBlock\n" +
          formatBlock(base, nextIndent, children.isEmpty)

      if (children.isEmpty) {
        baseStr
      } else {
        val childrenStr = children.zipWithIndex.map { case (child, idx) =>
          formatBlock(child, nextIndent, idx == children.size - 1)
        }.mkString("\n")

        s"$baseStr\n$childrenStr"
      }

      case GenerativeExtensionBlock(genBlock) =>
        s"$indent${marker}GenerativeExtensionBlock (.)\n${formatBlock(genBlock, nextIndent, isLast = true)}"

      case TransformationExtensionBlock(transBlock) =>
        s"$indent${marker}TransformationExtensionBlock (.)\n${formatBlock(transBlock, nextIndent, isLast = true)}"

      case v: VisualizerBlock => v match {
        case PianoRollBlock(startIndex) =>
          s"$indent${marker}VisualizerBlock: PianoRollBlock(startIndex=$startIndex)"
      }

      case t: TransformationBlock =>
        t match {
          case Reverse() =>
            s"$indent${marker}TransformationBlock: .reverse"

          case Gain(p)            => formatTransWithParam("gain", p, nextIndent, indent, marker)
          case Pan(p)             => formatTransWithParam("pan", p, nextIndent, indent, marker)
          case Room(p)            => formatTransWithParam("room", p, nextIndent, indent, marker)
          case Delay(p)           => formatTransWithParam("delay", p, nextIndent, indent, marker)
          case LowPassFilter(p)   => formatTransWithParam("lpf", p, nextIndent, indent, marker)
          case HighPassFilter(p)  => formatTransWithParam("hpf", p, nextIndent, indent, marker)
          case FastForward(p)     => formatTransWithParam("fast", p, nextIndent, indent, marker)
          case SlowMotion(p)      => formatTransWithParam("slow", p, nextIndent, indent, marker)
          case Early(p)           => formatTransWithParam("early", p, nextIndent, indent, marker)
          case Late(p)            => formatTransWithParam("late", p, nextIndent, indent, marker)
          case Repetition(p)      => formatTransWithParam("ply", p, nextIndent, indent, marker)
          case Juxtaposition(transList) =>
            val transStr = transList.zipWithIndex.map { case (trans, idx) =>
              formatBlock(trans, nextIndent, idx == transList.size - 1)
            }.mkString("\n")
            s"$indent${marker}TransformationBlock: .jux(...)\n$transStr"
          case Offset(offsetPat, transList) =>
            val offsetStr = formatPattern(offsetPat, nextIndent, isLast = false)
            val transStr = transList.zipWithIndex.map { case (trans, idx) =>
              formatBlock(trans, nextIndent, idx == transList.size - 1)
            }.mkString("\n")
            s"$indent${marker}TransformationBlock: .off(...)\n$offsetStr\n$transStr"
            
          case Unknown(name, pat) => formatTransWithParam("unknown", pat, nextIndent, indent, marker)
        }

      case SoundBlock(pat) =>
        s"$indent${marker}SoundBlock\n${formatPattern(pat, nextIndent, isLast = true)}"
        
      case NoteBlock(pat) =>
        s"$indent${marker}NoteBlock\n${formatPattern(pat, nextIndent, isLast = true)}"
    }
  }

  private def formatTransWithParam(name: String, pat: Pattern[Config], nextIndent: String, indent: String, marker: String): String = {
    s"$indent${marker}TransformationBlock: .$name(...)\n${formatPattern(pat, nextIndent, isLast = true)}"
  }

  private def formatPattern[A <: Atom](pat: Pattern[A], indent: String, isLast: Boolean): String = {
    val marker = if (isLast) "`-- " else "|-- "
    val nextIndent = indent + (if (isLast) "    " else "|   ")
    val children = pat.elems.zipWithIndex.map { case (seq, idx) =>
      val sMarker = if (idx == pat.elems.size - 1) "`-- " else "|-- "
      val sIndent = nextIndent + (if (idx == pat.elems.size - 1) "    " else "|   ")
      s"$nextIndent${sMarker}Sequence (Whitespace separated)\n${seq.elems.zipWithIndex.map { case (el, elIdx) => 
        formatElement(el, sIndent, elIdx == seq.elems.size - 1)
      }.mkString("\n")}"
    }.mkString("\n")
    s"$indent${marker}Pattern (Comma separated)\n$children"
  }

  private def formatElement[A <: Atom](el: Element[A], indent: String, isLast: Boolean): String = {
    val marker = if (isLast) "`-- " else "|-- "
    val nextIndent = indent + (if (isLast) "    " else "|   ")
    el match {
      case SpeedModifiedElement(element, isMulFactor, factor) => 
        s"$indent${marker}SpeedModifiedElement: ${if (isMulFactor) "Multiply" else "Divide"} by ${factor.value}\n${formatElement(element, nextIndent, isLast = true)}"
      
      case AtomElement(atom) => 
        val atomStr = atom match { 
          case n: Note => s"Note(name=${n.name}, acc=${n.accidental.getOrElse("none")}, oct=${n.octave}) [${n.startIndex}:${n.endIndex}]"
          case s: Sample => s"Sample(${s.value}) [${s.startIndex}:${s.endIndex}]"
          case c: Config => s"Config(${c.value}) [${c.startIndex}:${c.endIndex}]" 
        }
        s"$indent${marker}Atom: $atomStr"
        
      case SubPatternElement(p) => s"$indent${marker}SubPatternElement [Square brackets contains]\n${formatPattern(p, nextIndent, isLast = true)}"
      case AlternationElement(p) => s"$indent${marker}AlternationElement [Angular brackets contains]\n${formatPattern(p, nextIndent, isLast = true)}"
    }
  }
}