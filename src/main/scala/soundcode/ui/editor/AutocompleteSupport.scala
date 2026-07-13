package soundcode.ui.editor

import javafx.scene.control.{Label, ListCell, ListView, OverrunStyle, ScrollBar}
import javafx.scene.input.{KeyCode, KeyEvent, MouseEvent}
import javafx.stage.Popup
import org.fxmisc.richtext.GenericStyledArea
import javafx.application.Platform
import javafx.geometry.Orientation
import javafx.scene.layout.VBox
import scala.jdk.CollectionConverters.*
import soundcode.parser.SoundCodeLanguage

object AutocompleteSupport:
  private case class CompletionItem(
      label: String,
      insertText: String,
      detail: String
  ):
    override def toString: String = s"$label ($detail)"

  private object CompletionCatalog:
    private def toCompletionItem(
        construct: SoundCodeLanguage.Construct
    ): CompletionItem =
      CompletionItem(
        construct.name,
        construct.snippet,
        construct.detail
      )

    val generativeBlocks: Vector[CompletionItem] =
      SoundCodeLanguage.Generative.all.map(toCompletionItem)

    val transformations: Vector[CompletionItem] =
      SoundCodeLanguage.Transformation.all.map(toCompletionItem)

  private final case class CompletionContext(
      textBeforeCaret: String,
      prefix: String,
      lineBeforeCaret: String
  ):
    def isInsideQuotes: Boolean =
      textBeforeCaret.count(_ == '"') % 2 == 1

    def isAfterDot: Boolean =
      lineBeforeCaret.take(lineBeforeCaret.length - prefix.length).endsWith(".")

    def isAtLineStart: Boolean =
      lineBeforeCaret.trim == prefix

  private object CompletionContext:
    def from(area: GenericStyledArea[?, ?, ?]): CompletionContext =
      val textBeforeCaret = area.getText.take(area.getCaretPosition)

      val prefix =
        textBeforeCaret.reverse
          .takeWhile(ch => ch.isLetterOrDigit || ch == '_')
          .reverse

      val lineBeforeCaret =
        textBeforeCaret.drop(textBeforeCaret.lastIndexOf('\n') + 1)

      CompletionContext(textBeforeCaret, prefix, lineBeforeCaret)

  def install(area: GenericStyledArea[?, ?, ?]): Unit =
    val popup = new Popup()
    val list = new ListView[CompletionItem]()

    popup.setAutoHide(true)
    popup.setHideOnEscape(true)
    popup.setConsumeAutoHidingEvents(false)
    popup.getContent.add(list)

    list.setFocusTraversable(false)
    list.setPrefWidth(340)
    list.setMinHeight(132)
    list.setMaxHeight(156)
    list.setFixedCellSize(48)
    list.setStyle("""
      |-fx-background-color: #25252b;
      |-fx-control-inner-background: #25252b;
      |-fx-background-radius: 6;
      |-fx-border-color: #3a3a42;
      |-fx-border-radius: 6;
      |-fx-padding: 4;
      |-fx-font-family: 'Cascadia Code', 'JetBrains Mono', 'Consolas';
      |-fx-font-size: 13px;
      |""".stripMargin)

    def styleScrollBar(): Unit =
      Platform.runLater(() =>
        list.lookupAll(".scroll-bar").asScala.foreach {
          case bar: ScrollBar if bar.getOrientation == Orientation.HORIZONTAL =>
            bar.setVisible(false)
            bar.setManaged(false)
            bar.setOpacity(0)
            bar.setPrefHeight(0)
            bar.setMaxHeight(0)

          case bar: ScrollBar if bar.getOrientation == Orientation.VERTICAL =>
            bar.setPrefWidth(10)
            bar.setStyle("-fx-background-color: transparent;")

            Option(bar.lookup(".thumb")).foreach(
              _.setStyle("""
                |-fx-background-color: #5a5a66;
                |-fx-background-radius: 999;
                |""".stripMargin)
            )

            Option(bar.lookup(".track")).foreach(
              _.setStyle("-fx-background-color: transparent;")
            )

            Seq(".increment-button", ".decrement-button").foreach(selector =>
              Option(bar.lookup(selector)).foreach { button =>
                button.setStyle("""
                  |-fx-background-color: transparent;
                  |-fx-padding: 0;
                  |""".stripMargin)
              }
            )

          case _ =>
        }
      )

    list.setCellFactory(_ =>
      new ListCell[CompletionItem]:
        private val keyword = new Label()
        private val detail = new Label()
        private val content = new VBox(2, keyword, detail)

        content.setFillWidth(true)
        content.prefWidthProperty().bind(list.widthProperty().subtract(34))

        keyword.setMaxWidth(Double.MaxValue)
        keyword.setTextOverrun(OverrunStyle.ELLIPSIS)
        keyword.setStyle("""
          |-fx-text-fill: #f4f4f5;
          |-fx-font-size: 13px;
          |-fx-font-weight: 700;
          |""".stripMargin)

        detail.setMaxWidth(Double.MaxValue)
        detail.setTextOverrun(OverrunStyle.ELLIPSIS)
        detail.setStyle("""
          |-fx-text-fill: #b8b8c2;
          |-fx-font-size: 11px;
          |""".stripMargin)

        private def refreshStyle(): Unit =
          if isEmpty || getItem == null then
            setText(null)
            setGraphic(null)
            setStyle("-fx-background-color: transparent;")
          else
            keyword.setText(getItem.label)
            detail.setText(getItem.detail)

            setText(null)
            setGraphic(content)
            setStyle(
              if isSelected then """
                |-fx-background-color: #3a3a42;
                |-fx-background-radius: 4;
                |-fx-padding: 5 8;
                |""".stripMargin
              else """
                |-fx-background-color: transparent;
                |-fx-background-radius: 4;
                |-fx-padding: 5 8;
                |""".stripMargin
            )

        override def updateItem(item: CompletionItem, empty: Boolean): Unit =
          super.updateItem(item, empty)
          refreshStyle()

        override def updateSelected(selected: Boolean): Unit =
          super.updateSelected(selected)
          refreshStyle()
    )

    def suggestions(): Vector[CompletionItem] =
      val context = CompletionContext.from(area)

      if context.isInsideQuotes then Vector.empty
      else
        val candidates =
          if context.isAfterDot then
            CompletionCatalog.generativeBlocks ++ CompletionCatalog.transformations
          else if context.isAtLineStart then CompletionCatalog.generativeBlocks
          else Vector.empty

        candidates.filter(_.label.startsWith(context.prefix))

    def showPopup(): Unit =
      val items = suggestions()

      if items.isEmpty then popup.hide()
      else
        list.getItems.setAll(items.asJava)
        list.setPrefHeight(math.max(156, math.min(240, items.size * 48 + 10)))
        list.getSelectionModel.clearAndSelect(0)
        list.getFocusModel.focus(0)
        list.scrollTo(0)

        val bounds = area.getCaretBounds
        if bounds.isPresent && area.getScene != null then
          val b = bounds.get()
          popup.show(
            area.getScene.getWindow,
            b.getMinX,
            b.getMaxY + 6
          )
          styleScrollBar()

    def insertSelected(): Unit =
      val selected = list.getSelectionModel.getSelectedItem

      if selected != null then
        val prefix = CompletionContext.from(area).prefix
        val caret = area.getCaretPosition
        val from = caret - prefix.length

        area.replaceText(from, caret, selected.insertText)

        val placeholderIndex = selected.insertText.indexOf("$0")
        if placeholderIndex >= 0 then
          val placeholderStart = from + placeholderIndex
          area.replaceText(placeholderStart, placeholderStart + 2, "")
          area.moveTo(placeholderStart)

        popup.hide()

    def moveSelection(delta: Int): Unit =
      val itemCount = list.getItems.size

      if itemCount > 0 then
        val current = list.getSelectionModel.getSelectedIndex
        val next =
          if current < 0 then 0
          else math.max(0, math.min(itemCount - 1, current + delta))

        list.getSelectionModel.clearAndSelect(next)
        list.getFocusModel.focus(next)
        list.scrollTo(next)

    area.focusedProperty().addListener { (_, _, focused) =>
      if !focused then popup.hide()
    }

    area.addEventFilter(
      MouseEvent.MOUSE_PRESSED,
      (_: MouseEvent) => popup.hide()
    )

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
              moveSelection(1)
              event.consume()

            case KeyCode.UP =>
              moveSelection(-1)
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
