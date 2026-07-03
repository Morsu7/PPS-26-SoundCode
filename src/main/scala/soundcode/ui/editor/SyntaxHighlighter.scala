package soundcode.ui.editor

import org.fxmisc.richtext.GenericStyledArea
import soundcode.ui.UITheme
import soundcode.domain.TextPosition

object SyntaxHighlighter:
  private val StringPattern = "\"([^\"\\\\]|\\\\.)*\"".r
  private val FunctionPattern = """\b[a-zA-Z_][a-zA-Z0-9_]*(?=\()""".r

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

  def applyTo(area: GenericStyledArea[?, ?, String]): Unit =
    val text = area.getText

    area.setStyle(0, text.length, DefaultStyle)

    FunctionPattern.findAllMatchIn(text).foreach { m =>
      area.setStyle(
        textIndexToEditorIndex(area, m.start),
        textIndexToEditorIndex(area, m.end),
        FunctionStyle
      )
    }

    StringPattern.findAllMatchIn(text).foreach { m =>
      area.setStyle(
        textIndexToEditorIndex(area, m.start),
        textIndexToEditorIndex(area, m.end),
        StringStyle
      )
    }

  def applyTo(
      area: GenericStyledArea[?, ?, String],
      playbackPositions: Set[TextPosition]
  ): Unit =
    applyTo(area)

    val textLength = area.getText.length

    playbackPositions.foreach { position =>
      val start = Math.max(0, position.startIndex)
      val end = Math.min(textLength, position.endIndex)

      if start < end then area.setStyle(start, end, PlaybackHighlightStyle)
    }

  private def textIndexToEditorIndex(
      area: GenericStyledArea[?, ?, String],
      textIndex: Int
  ): Int =
    import scala.jdk.CollectionConverters.*

    var textOffset = 0
    var editorOffset = 0

    area.getParagraphs.asScala.foreach { paragraph =>
      val isVisualizerParagraph =
        paragraph.getSegments.asScala.exists {
          case segment: org.reactfx.util.Either[?, ?] => segment.isRight
          case _                                      => false
        }

      if isVisualizerParagraph then editorOffset += paragraph.length()
      else
        val paragraphTextLength = paragraph.getText.length

        if textIndex <= textOffset + paragraphTextLength then
          return editorOffset + (textIndex - textOffset)

        textOffset += paragraphTextLength + 1
        editorOffset += paragraph.length() + 1
    }

    editorOffset
