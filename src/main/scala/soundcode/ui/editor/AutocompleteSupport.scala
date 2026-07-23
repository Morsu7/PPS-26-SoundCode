package soundcode.ui.editor

import javafx.scene.control.{
  Label,
  ListCell,
  ListView,
  OverrunStyle,
  ScrollBar,
  Tooltip
}
import javafx.scene.input.{KeyCode, KeyEvent, MouseEvent}
import javafx.stage.Popup
import org.fxmisc.richtext.GenericStyledArea
import javafx.application.Platform
import javafx.geometry.Orientation
import javafx.scene.layout.{HBox, Priority, VBox}
import javafx.util.Duration
import scala.jdk.CollectionConverters.*
import soundcode.parser.SoundCodeLanguage
import soundcode.ui.theme.UITheme

object AutocompleteSupport:
  private type CompletionItem = SoundCodeLanguage.Construct

  private final case class Completion(
      from: Int,
      to: Int,
      text: String,
      caretPosition: Int
  )

  private final class CompletionListCell(
      list: ListView[CompletionItem]
  ) extends ListCell[CompletionItem]:

    private val keyword = new Label()
    private val arguments = new Label()
    private val overloadHint = new Label()
    private val detail = new Label()
    private val signatureTooltip = new Tooltip()
    private val argumentsRow = new HBox(6, arguments, overloadHint)
    private val content = new VBox(2, keyword, argumentsRow, detail)

    content.setFillWidth(true)
    content
      .prefWidthProperty()
      .bind(list.widthProperty().subtract(34))

    keyword.setMaxWidth(Double.MaxValue)
    keyword.setStyle(UITheme.autocompleteKeywordStyle)

    arguments.setMaxWidth(Double.MaxValue)
    arguments.setWrapText(true)
    arguments.setStyle(UITheme.autocompleteArgumentsStyle)
    HBox.setHgrow(arguments, Priority.ALWAYS)

    overloadHint.setStyle(UITheme.autocompleteOverloadStyle)

    signatureTooltip.setShowDelay(Duration.millis(150))
    Tooltip.install(arguments, signatureTooltip)
    Tooltip.install(overloadHint, signatureTooltip)

    detail.setMaxWidth(Double.MaxValue)
    detail.setTextOverrun(OverrunStyle.ELLIPSIS)
    detail.setStyle(UITheme.autocompleteDetailStyle)

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
        setStyle(UITheme.autocompleteEmptyCellStyle)
      else
        val additionalOverloads =
          getItem.argumentLists.size - 1

        keyword.setText(getItem.name)
        arguments.setText(s"(${getItem.argumentLists.head})")
        overloadHint.setText(
          if additionalOverloads == 1 then "+1 overload"
          else s"+$additionalOverloads overloads"
        )
        overloadHint.setVisible(additionalOverloads > 0)
        overloadHint.setManaged(additionalOverloads > 0)
        detail.setText(getItem.detail)
        signatureTooltip.setText(
          getItem.signatures.mkString("\n")
        )

        setText(null)
        setGraphic(content)
        setStyle(
          if isSelected then UITheme.autocompleteSelectedCellStyle
          else UITheme.autocompleteDefaultCellStyle
        )

  private def contextFor(
      area: GenericStyledArea[?, ?, ?]
  ): CompletionProvider.Context =
    CompletionProvider.contextFrom(
      text = area.getText,
      caretPosition = area.getCaretPosition
    )

  private def completionFor(
      context: CompletionProvider.Context,
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

    CompletionProvider.suggestionsFor(context).headOption match
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
    list.setPrefWidth(UITheme.AutocompleteWidth)
    list.setMinHeight(UITheme.AutocompleteMinHeight)
    list.setMaxHeight(UITheme.AutocompleteExpandedMaxHeight)
    list.setStyle(UITheme.autocompletePopupStyle)

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
            bar.setPrefWidth(UITheme.AutocompleteScrollbarWidth)
            bar.setStyle(UITheme.autocompleteScrollbarStyle)

            Option(bar.lookup(".thumb")).foreach(
              _.setStyle(UITheme.autocompleteScrollbarThumbStyle)
            )

            Option(bar.lookup(".track")).foreach(
              _.setStyle(UITheme.autocompleteScrollbarTrackStyle)
            )

            Seq(".increment-button", ".decrement-button").foreach(selector =>
              Option(bar.lookup(selector)).foreach { button =>
                button.setStyle(UITheme.autocompleteScrollbarButtonStyle)
              }
            )

          case _ =>
        }
      )

    list.setCellFactory(_ => new CompletionListCell(list))

    def showPopup(): Unit =
      val context = contextFor(area)
      val items = CompletionProvider.suggestionsFor(context)

      if items.isEmpty then popup.hide()
      else
        list.getItems.setAll(items.asJava)
        list.setPrefHeight(
          math.max(
            UITheme.AutocompleteMaxHeight,
            math.min(
              UITheme.AutocompleteExpandedMaxHeight,
              items.size *
                UITheme.AutocompleteEstimatedCellHeight +
                UITheme.AutocompleteHeightPadding
            )
          )
        )
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
