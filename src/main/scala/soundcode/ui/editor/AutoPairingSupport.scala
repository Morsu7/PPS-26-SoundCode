package soundcode.ui.editor

import javafx.scene.input.KeyEvent

import org.fxmisc.richtext.GenericStyledArea

object AutoPairingSupport:
  def install(area: GenericStyledArea[?, ?, ?]): Unit =
    area.addEventFilter(
      KeyEvent.KEY_TYPED,
      (event: KeyEvent) =>
        event.getCharacter match
          case "\"" =>
            if shouldInsertClosingQuote(area) then
              event.consume()
              insertPair(area, "\"", "\"")

          case "(" =>
            event.consume()
            insertPair(area, "(", ")")

          case "[" =>
            event.consume()
            insertPair(area, "[", "]")

          case "<" =>
            event.consume()
            insertPair(area, "<", ">")

          case _ =>
    )

  private def insertPair(
      area: GenericStyledArea[?, ?, ?],
      open: String,
      close: String
  ): Unit =
    val caret = area.getCaretPosition
    area.insertText(caret, open + close)
    area.moveTo(caret + open.length)

  private def shouldInsertClosingQuote(
      area: GenericStyledArea[?, ?, ?]
  ): Boolean =
    val before = area.getText.take(area.getCaretPosition)
    before.count(_ == '"') % 2 == 0
