package soundcode.parser.AST

object Settings {
    // Blocks containing general configuration
    sealed trait SettingBlock extends Block

    case class CPM(value: Double) extends SettingBlock
    case class CPS(value: Double) extends SettingBlock
}
