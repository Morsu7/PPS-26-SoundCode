package soundcode.parser.AST

object Visualizers {
    sealed trait VisualizerBlock extends Block

    case class PianoRollBlock(startIndex: Int) extends VisualizerBlock
}