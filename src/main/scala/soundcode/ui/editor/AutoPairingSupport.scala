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
            if hasSelection(area) then
              event.consume()
              insertPairOrWrap(area, "\"", "\"")
            else if shouldSkipClosing(area, "\"") then
              event.consume()
              skipClosing(area)
            else if shouldAutoPairQuote(area) then
              event.consume()
              insertPair(area, "\"", "\"")

          case "(" =>
            event.consume()
            insertPairOrWrap(area, "(", ")")

          case "[" =>
            event.consume()
            insertPairOrWrap(area, "[", "]")

          case "<" =>
            event.consume()
            insertPairOrWrap(area, "<", ">")

          case closing @ (")" | "]" | ">") =>
            if shouldSkipClosing(area, closing) then
              event.consume()
              skipClosing(area)

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

  private def insertPairOrWrap(
      area: GenericStyledArea[?, ?, ?],
      open: String,
      close: String
  ): Unit =
    val selection = area.getSelection

    if hasSelection(area) then
      val start = selection.getStart
      val end = selection.getEnd
      val selectedText =
        area.getText(start, end)

      area.replaceText(
        start,
        end,
        open + selectedText + close
      )

      area.selectRange(
        start + open.length,
        end + open.length
      )
    else insertPair(area, open, close)

  private def shouldAutoPairQuote(
      area: GenericStyledArea[?, ?, ?]
  ): Boolean =
    val before = area.getText.take(area.getCaretPosition)
    before.count(_ == '"') % 2 == 0

  private def shouldSkipClosing(
      area: GenericStyledArea[?, ?, ?],
      closing: String
  ): Boolean =
    val caret = area.getCaretPosition

    !hasSelection(area) &&
    caret < area.getLength &&
    area.getText(caret, caret + 1) == closing

  private def hasSelection(
      area: GenericStyledArea[?, ?, ?]
  ): Boolean =
    area.getSelection.getLength > 0

  private def skipClosing(
      area: GenericStyledArea[?, ?, ?]
  ): Unit =
    area.moveTo(area.getCaretPosition + 1)
