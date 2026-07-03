package soundcode.ui.editor

import javafx.scene.control.ListView
import javafx.scene.input.{KeyCode, KeyEvent, MouseEvent}
import javafx.stage.Popup
import org.fxmisc.richtext.GenericStyledArea
import scala.jdk.CollectionConverters.*

private object AutocompleteSupport:
  private case class CompletionItem(
      label: String,
      insertText: String,
      detail: String
  ):
    override def toString: String = s"$label ($detail)"

  private val generativeBlocks = Vector(
    CompletionItem("note", """note("$0")""", "Generates a note block")
  )

  private val transformations = Vector(
  )

  def install(area: GenericStyledArea[?, ?, ?]): Unit =
    val popup = new Popup()
    val list = new ListView[CompletionItem]()

    list.setPrefWidth(260)
    list.setPrefHeight(180)
    list.setStyle("""
      |-fx-background-color: #1f1f24;
      |-fx-control-inner-background: #1f1f24;
      |-fx-text-fill: #f4f4f5;
      |-fx-font-family: 'Cascadia Code', 'JetBrains Mono', 'Consolas';
      |-fx-font-size: 13px;
      |""".stripMargin)

    popup.getContent.add(list)

    def currentPrefix(): String =
      val text = area.getText
      val caret = area.getCaretPosition
      text
        .take(caret)
        .reverse
        .takeWhile(ch => ch.isLetterOrDigit || ch == '#')
        .reverse

    def insideQuotesBeforeCaret(): Boolean =
      area.getText.take(area.getCaretPosition).count(_ == '"') % 2 == 1

    def currentLineBeforeCaret(): String =
      val text = area.getText.take(area.getCaretPosition)
      text.drop(text.lastIndexOf('\n') + 1)

    def suggestions(): Vector[CompletionItem] =
      val prefix = currentPrefix()
      val line = currentLineBeforeCaret()
      val trimmed = line.trim

      val base =
        if line.take(line.length - prefix.length).endsWith(".") then
          generativeBlocks ++ transformations
        else if trimmed == prefix then generativeBlocks
        else Vector.empty

      base.filter(_.label.startsWith(prefix))

    def showPopup(): Unit =
      val items = suggestions()

      if items.isEmpty then popup.hide()
      else
        list.getItems.setAll(items.asJava)
        list.getSelectionModel.select(0)

        val bounds = area.getCaretBounds
        if bounds.isPresent && area.getScene != null then
          val b = bounds.get()
          popup.show(area.getScene.getWindow, b.getMinX, b.getMaxY + 4)

    def insertSelected(): Unit =
      val selected = list.getSelectionModel.getSelectedItem
      if selected != null then
        val prefix = currentPrefix()
        val caret = area.getCaretPosition
        val from = caret - prefix.length

        area.replaceText(from, caret, selected.insertText)

        val placeholderIndex = selected.insertText.indexOf("$0")
        if placeholderIndex >= 0 then
          val placeholderStart = from + placeholderIndex
          area.replaceText(placeholderStart, placeholderStart + 2, "")
          area.moveTo(placeholderStart)

        popup.hide()

    list.addEventFilter(
      MouseEvent.MOUSE_CLICKED,
      (event: MouseEvent) =>
        if event.getClickCount == 2 then
          insertSelected()
          event.consume()
    )

    area.addEventFilter(
      KeyEvent.KEY_PRESSED,
      (event: KeyEvent) =>
        if popup.isShowing then
          event.getCode match
            case KeyCode.ENTER | KeyCode.TAB =>
              insertSelected()
              event.consume()

            case KeyCode.ESCAPE =>
              popup.hide()
              event.consume()

            case KeyCode.DOWN =>
              list.getSelectionModel.selectNext()
              event.consume()

            case KeyCode.UP =>
              list.getSelectionModel.selectPrevious()
              event.consume()

            case _ =>
    )

    area.addEventFilter(
      KeyEvent.KEY_RELEASED,
      (event: KeyEvent) =>
        event.getCode match
          case KeyCode.ENTER | KeyCode.TAB | KeyCode.ESCAPE | KeyCode.UP |
              KeyCode.DOWN | KeyCode.LEFT | KeyCode.RIGHT =>
          case _ =>
            showPopup()
    )
