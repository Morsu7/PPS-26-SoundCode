package soundcode.ui.theme

import javafx.scene.text.Font

private[ui] object UITheme:
  // Base palette
  val Background = "#1f1f24"
  val Foreground = "#f4f4f5"
  val Muted = "#8b949e"
  val Transparent = "transparent"

  // Syntax
  val String = "#d9f99d"
  val Function = "#93c5fd"
  val Number = "#fbbf24"

  // Visualizers
  val VisualizerLine = "#505058"

  // Autocomplete
  val PopupBackground = "#25252b"
  val PopupBorder = "#3a3a42"
  val PopupSelection = "#3a3a42"
  val PopupDetail = "#b8b8c2"
  val ScrollbarThumb = "#5a5a66"

  // Errors
  val ErrorForeground = "#ffb4ab"
  val ErrorBackground = "#3f1d24"

  // Typography
  val EditorFontSize = 15
  val NormalFontSize = 13
  val DetailFontSize = 11
  val CloseButtonFontSize = 18

  private val bundledFont =
    Option(
      getClass.getResourceAsStream("/fonts/CascadiaCode-Regular.ttf")
    ).flatMap { stream =>
      try Option(Font.loadFont(stream, EditorFontSize))
      finally stream.close()
    }

  val FontFamily =
    bundledFont
      .map(font => s"'${font.getFamily}'")
      .getOrElse("'Monospaced'")

  // Autocomplete dimensions
  val AutocompleteWidth = 420.0
  val AutocompleteMinHeight = 132.0
  val AutocompleteMaxHeight = 156.0
  val AutocompleteExpandedMaxHeight = 240.0
  val AutocompleteEstimatedCellHeight = 64.0
  val AutocompleteScrollbarWidth = 10.0
  val AutocompleteHeightPadding = 10.0

  // Editor dimensions
  val LineNumberWidth = 40.0

  def backgroundStyle: String =
    s"-fx-background-color: $Background;"

  def transparentBackgroundStyle: String =
    s"-fx-background-color: $Transparent;"

  def caretStyle: String =
    s"-fx-stroke: $Foreground;"

  def editorStyle: String =
    s"""
      |-fx-font-family: $FontFamily;
      |-fx-font-size: ${EditorFontSize}px;
      |-fx-background-color: $Background;
      |-fx-control-inner-background: $Background;
      |-fx-text-fill: $Foreground;
      |""".stripMargin

  def lineNumberStyle: String =
    s"""
      |-fx-background-color: $Background;
      |-fx-text-fill: $Muted;
      |-fx-font-family: $FontFamily;
      |-fx-font-size: ${NormalFontSize}px;
      |-fx-padding: 2 8 0 4;
      |-fx-alignment: top-right;
      |""".stripMargin

  // MainView styles
  def toolbarStyle: String =
    s"""
      |-fx-background-color: $Background;
      |-fx-padding: 6;
      |""".stripMargin

  def toolbarLabelStyle: String =
    s"-fx-text-fill: $Foreground;"

  // Autocomplete styles

  def autocompletePopupStyle: String =
    s"""
      |-fx-background-color: $PopupBackground;
      |-fx-control-inner-background: $PopupBackground;
      |-fx-text-background-color: $Foreground;
      |-fx-background-radius: 6;
      |-fx-border-color: $PopupBorder;
      |-fx-border-radius: 6;
      |-fx-padding: 4;
      |-fx-font-family: $FontFamily;
      |-fx-font-size: ${NormalFontSize}px;
      |""".stripMargin

  def autocompleteKeywordStyle: String =
    s"""
      |-fx-text-fill: $Foreground;
      |-fx-font-size: ${NormalFontSize}px;
      |-fx-font-weight: 700;
      |""".stripMargin

  def autocompleteArgumentsStyle: String =
    s"""
      |-fx-text-fill: $Function;
      |-fx-font-family: $FontFamily;
      |-fx-font-size: ${DetailFontSize}px;
      |""".stripMargin

  def autocompleteOverloadStyle: String =
    s"""
      |-fx-text-fill: $Muted;
      |-fx-font-size: ${DetailFontSize}px;
      |-fx-font-style: italic;
      |""".stripMargin

  def autocompleteDetailStyle: String =
    s"""
      |-fx-text-fill: $Muted;
      |-fx-font-size: ${DetailFontSize}px;
      |""".stripMargin

  def autocompleteSelectedCellStyle: String =
    s"""
      |-fx-background-color: $PopupSelection;
      |-fx-background-radius: 4;
      |-fx-padding: 5 8;
      |-fx-text-background-color: $Foreground;
      |-fx-text-fill: $Foreground;
      |""".stripMargin

  def autocompleteDefaultCellStyle: String =
    s"""
      |-fx-background-color: $Transparent;
      |-fx-background-radius: 4;
      |-fx-padding: 5 8;
      |-fx-text-background-color: $Foreground;
      |-fx-text-fill: $Foreground;
      |""".stripMargin

  def autocompleteEmptyCellStyle: String =
    s"""
      |-fx-background-color: $Transparent;
      |-fx-text-background-color: $Foreground;
      |-fx-text-fill: $Foreground;
      |""".stripMargin

  def autocompleteScrollbarStyle: String =
    transparentBackgroundStyle

  def autocompleteScrollbarThumbStyle: String =
    s"""
      |-fx-background-color: $ScrollbarThumb;
      |-fx-background-radius: 999;
      |""".stripMargin

  def autocompleteScrollbarTrackStyle: String =
    transparentBackgroundStyle

  def autocompleteScrollbarButtonStyle: String =
    s"""
      |-fx-background-color: $Transparent;
      |-fx-padding: 0;
      |""".stripMargin

  // Error styles

  def errorTextStyle: String =
    s"""
      |-fx-text-fill: $ErrorForeground;
      |-fx-font-family: $FontFamily;
      |-fx-font-size: ${NormalFontSize}px;
      |""".stripMargin

  def errorCloseButtonStyle: String =
    s"""
      |-fx-background-color: $Transparent;
      |-fx-text-fill: $ErrorForeground;
      |-fx-font-size: ${CloseButtonFontSize}px;
      |-fx-font-weight: bold;
      |-fx-cursor: hand;
      |""".stripMargin

  def errorBannerStyle: String =
    s"""
      |-fx-background-color: $ErrorBackground;
      |-fx-padding: 10;
      |""".stripMargin

  // SyntaxHighlighter styles
  def textStyle(color: String): String =
    s"""
      |-fx-fill: $color;
      |-fx-font-smoothing-type: gray;
      |""".stripMargin

  def syntaxDefaultStyle: String =
    textStyle(Foreground)

  def syntaxStringStyle: String =
    textStyle(String)

  def syntaxNumberStyle: String =
    textStyle(Number)

  def syntaxFunctionStyle: String =
    s"""
      |${textStyle(Function)}
      |-fx-font-weight: bold;
      |""".stripMargin
