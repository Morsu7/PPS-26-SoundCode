package soundcode.ui.editor

import org.fxmisc.richtext.GenericStyledArea
import soundcode.domain.TextPosition
import soundcode.ui.UITheme

object SyntaxHighlighter:
  private type EditorArea =
    GenericStyledArea[?, ?, String]

  private val StringPattern =
    "\"([^\"\\\\]|\\\\.)*\"".r

  private val FunctionPattern =
    """\b[a-zA-Z_][a-zA-Z0-9_]*(?=\()""".r

  private val DefaultStyle =
    UITheme.textStyle(UITheme.Foreground)

  private val StringStyle =
    UITheme.textStyle(UITheme.String)

  private val FunctionStyle =
    s"${UITheme.textStyle(UITheme.Function)} -fx-font-weight: bold;"

  private val PlaybackHighlightStyle =
    s"""
       |-fx-fill: ${UITheme.String};
       |-fx-font-weight: bold;
       |-rtfx-background-color: transparent;
       |-rtfx-border-stroke-color: ${UITheme.String};
       |-rtfx-border-stroke-width: 1px;
       |-rtfx-border-stroke-type: centered;
       |""".stripMargin

  def applyTo(
      area: GenericStyledArea[?, ?, String],
      playbackPositions: Set[TextPosition] = Set.empty
  ): Unit =
    applySyntaxHighlighting(area)
    applyPlaybackHighlighting(area, playbackPositions)

  private def applySyntaxHighlighting(
      area: EditorArea
  ): Unit =
    (0 until area.getParagraphs.size()).foreach { paragraphIndex =>
      applyToParagraph(area, paragraphIndex)
    }

  private def applyPlaybackHighlighting(
      area: EditorArea,
      positions: Set[TextPosition]
  ): Unit =
    val textLength = area.getLength

    positions.foreach { position =>
      val start = position.startIndex.max(0)
      val end = position.endIndex.min(textLength)

      if start < end then
        area.setStyle(
          start,
          end,
          PlaybackHighlightStyle
        )
    }

  private def isValidParagraph(
      area: EditorArea,
      index: Int
  ): Boolean =
    index >= 0 && index < area.getParagraphs.size()

  private def applyToParagraph(
      area: EditorArea,
      paragraphIndex: Int
  ): Unit =
    if isValidParagraph(area, paragraphIndex) then
      val text =
        area.getParagraph(paragraphIndex).getText

      area.setStyle(
        paragraphIndex,
        0,
        text.length,
        DefaultStyle
      )

      FunctionPattern.findAllMatchIn(text).foreach { matched =>
        area.setStyle(
          paragraphIndex,
          matched.start,
          matched.end,
          FunctionStyle
        )
      }

      StringPattern.findAllMatchIn(text).foreach { matched =>
        area.setStyle(
          paragraphIndex,
          matched.start,
          matched.end,
          StringStyle
        )
      }
