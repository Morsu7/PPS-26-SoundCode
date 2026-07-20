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
  private type CompletionItem = SoundCodeLanguage.Construct

  private val afterDotCandidates =
    SoundCodeLanguage.Generative.all ++
      SoundCodeLanguage.Transformation.all ++
      SoundCodeLanguage.Visualization.all

  private val PopupListStyle =
    """
      |-fx-background-color: #25252b;
      |-fx-control-inner-background: #25252b;
      |-fx-text-background-color: #f4f4f5;
      |-fx-background-radius: 6;
      |-fx-border-color: #3a3a42;
      |-fx-border-radius: 6;
      |-fx-padding: 4;
      |-fx-font-family: 'Cascadia Code', 'JetBrains Mono', 'Consolas';
      |-fx-font-size: 13px;
      |""".stripMargin

  private val KeywordStyle =
    """
      |-fx-text-fill: #f4f4f5;
      |-fx-font-size: 13px;
      |-fx-font-weight: 700;
      |""".stripMargin

  private val DetailStyle =
    """
      |-fx-text-fill: #b8b8c2;
      |-fx-font-size: 11px;
      |""".stripMargin

  private val SelectedCellStyle =
    """
      |-fx-background-color: #3a3a42;
      |-fx-background-radius: 4;
      |-fx-padding: 5 8;
      |""".stripMargin

  private val DefaultCellStyle =
    """
      |-fx-background-color: transparent;
      |-fx-background-radius: 4;
      |-fx-padding: 5 8;
      |""".stripMargin

  private final case class Completion(
      from: Int,
      to: Int,
      text: String,
      caretPosition: Int
  )

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

  private final class CompletionListCell(
      list: ListView[CompletionItem]
  ) extends ListCell[CompletionItem]:

    private val keyword = new Label()
    private val detail = new Label()
    private val content = new VBox(2, keyword, detail)

    content.setFillWidth(true)
    content
      .prefWidthProperty()
      .bind(list.widthProperty().subtract(34))

    keyword.setMaxWidth(Double.MaxValue)
    keyword.setTextOverrun(OverrunStyle.ELLIPSIS)
    keyword.setStyle(KeywordStyle)

    detail.setMaxWidth(Double.MaxValue)
    detail.setTextOverrun(OverrunStyle.ELLIPSIS)
    detail.setStyle(DetailStyle)

    override def updateItem(
        item: CompletionItem,
        empty: Boolean
    ): Unit =
      super.updateItem(item, empty)
      refreshStyle()

    override def updateSelected(
        selected: Boolean
    ): Unit =
      super.updateSelected(selected)
      refreshStyle()

    private def refreshStyle(): Unit =
      if isEmpty || getItem == null then
        setText(null)
        setGraphic(null)
        setStyle("-fx-background-color: transparent;")
      else
        keyword.setText(getItem.name)
        detail.setText(getItem.detail)

        setText(null)
        setGraphic(content)
        setStyle(
          if isSelected then SelectedCellStyle
          else DefaultCellStyle
        )

  private object CompletionContext:

    def from(
        text: String,
        caretPosition: Int
    ): CompletionContext =
      val textBeforeCaret = text.take(caretPosition)

      val prefix =
        textBeforeCaret.reverse
          .takeWhile(ch => ch.isLetterOrDigit || ch == '_')
          .reverse

      val lineBeforeCaret =
        textBeforeCaret.drop(textBeforeCaret.lastIndexOf('\n') + 1)

      CompletionContext(textBeforeCaret, prefix, lineBeforeCaret)

  private def contextFor(
      area: GenericStyledArea[?, ?, ?]
  ): CompletionContext =
    CompletionContext.from(
      text = area.getText,
      caretPosition = area.getCaretPosition
    )

  private def suggestionsFor(
      context: CompletionContext
  ): Vector[CompletionItem] =
    if context.isInsideQuotes then Vector.empty
    else
      val candidates =
        if context.isAfterDot then afterDotCandidates
        else if context.isAtLineStart then SoundCodeLanguage.Generative.all
        else Vector.empty

      candidates.filter(
        _.name.startsWith(context.prefix)
      )

  private def completionFor(
      context: CompletionContext,
      caretPosition: Int,
      item: CompletionItem
  ): Completion =
    val from = caretPosition - context.prefix.length
    val placeholderIndex = item.snippet.indexOf("$0")

    val text =
      if placeholderIndex >= 0 then
        item.snippet.patch(
          placeholderIndex,
          "",
          "$0".length
        )
      else item.snippet

    val finalCaret =
      if placeholderIndex >= 0 then from + placeholderIndex
      else from + text.length

    Completion(
      from = from,
      to = caretPosition,
      text = text,
      caretPosition = finalCaret
    )

  private def applyCompletion(
      area: GenericStyledArea[?, ?, ?],
      completion: Completion
  ): Unit =
    area.replaceText(
      completion.from,
      completion.to,
      completion.text
    )

    area.moveTo(completion.caretPosition)

  private[ui] def completeFirst(
      area: GenericStyledArea[?, ?, ?]
  ): Boolean =
    val caretPosition = area.getCaretPosition
    val context = contextFor(area)

    suggestionsFor(context).headOption match
      case Some(item) =>
        val completion =
          completionFor(context, caretPosition, item)

        applyCompletion(area, completion)
        true

      case None =>
        false

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
    list.setStyle(PopupListStyle)

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

    list.setCellFactory(_ => new CompletionListCell(list))

    def showPopup(): Unit =
      val context = contextFor(area)
      val items = suggestionsFor(context)

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
      val caretPosition = area.getCaretPosition
      val context = contextFor(area)

      Option(
        list.getSelectionModel.getSelectedItem
      ).foreach { selected =>
        applyCompletion(
          area,
          completionFor(
            context,
            caretPosition,
            selected
          )
        )

        popup.hide()
      }

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
        if event.isControlDown || event.isMetaDown || event.isAltDown then
          popup.hide()
        else
          event.getCode match
            case KeyCode.ENTER | KeyCode.TAB | KeyCode.ESCAPE | KeyCode.UP |
                KeyCode.DOWN | KeyCode.LEFT | KeyCode.RIGHT =>
            case _ =>
              showPopup()
    )
