package soundcode.ui.editor

import org.fxmisc.richtext.GenericStyledArea
import soundcode.ui.theme.UITheme

object SyntaxHighlighter:
  private type EditorArea =
    GenericStyledArea[?, ?, String]

  private val StringPattern =
    "\"([^\"\\\\]|\\\\.)*\"".r

  private val FunctionPattern =
    """\b[a-zA-Z_][a-zA-Z0-9_]*(?=\()""".r

  private val NumberPattern =
    """(?<![a-zA-Z0-9_.])[+-]?\d+(?:\.\d+)?(?![a-zA-Z0-9_.])""".r

  def applyTo(area: GenericStyledArea[?, ?, String]): Unit =
    applySyntaxHighlighting(area)

  def applyTo(
      area: GenericStyledArea[?, ?, String],
      paragraphIndex: Int
  ): Unit =
    applyToParagraph(area, paragraphIndex)

  private def applySyntaxHighlighting(
      area: EditorArea
  ): Unit =
    (0 until area.getParagraphs.size()).foreach { paragraphIndex =>
      applyToParagraph(area, paragraphIndex)
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
        UITheme.syntaxDefaultStyle
      )

      FunctionPattern.findAllMatchIn(text).foreach { matched =>
        area.setStyle(
          paragraphIndex,
          matched.start,
          matched.end,
          UITheme.syntaxFunctionStyle
        )
      }

      NumberPattern.findAllMatchIn(text).foreach { matched =>
        area.setStyle(
          paragraphIndex,
          matched.start,
          matched.end,
          UITheme.syntaxNumberStyle
        )
      }

      StringPattern.findAllMatchIn(text).foreach { matched =>
        area.setStyle(
          paragraphIndex,
          matched.start,
          matched.end,
          UITheme.syntaxStringStyle
        )
      }
