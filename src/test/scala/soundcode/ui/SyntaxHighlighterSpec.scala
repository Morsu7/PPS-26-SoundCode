package soundcode.ui

import org.fxmisc.richtext.InlineCssTextArea
import org.scalatest.prop.TableDrivenPropertyChecks
import soundcode.domain.TextPosition
import soundcode.ui.editor.SyntaxHighlighter
import soundcode.ui.theme.UITheme

class SyntaxHighlighterSpec
    extends UITestSupport
    with TableDrivenPropertyChecks:

  private val code =
    """note("c4").sound("piano")"""

  test("handles an empty editor"):
    onFxThread:
      val editor = highlightedEditor("")

      assert(editor.getText.isEmpty)

  test("highlights every character of function names"):
    onFxThread:
      val editor = highlightedEditor(code)

      assertRangeContains(
        editor,
        start = code.indexOf("note"),
        length = "note".length,
        expectedStyle = UITheme.Function
      )

      assertRangeContains(
        editor,
        start = code.indexOf("sound"),
        length = "sound".length,
        expectedStyle = UITheme.Function
      )

  test("highlights every character of string literals"):
    onFxThread:
      val editor = highlightedEditor(code)

      assertRangeContains(
        editor,
        start = code.indexOf("\"c4\""),
        length = "\"c4\"".length,
        expectedStyle = UITheme.String
      )

      assertRangeContains(
        editor,
        start = code.indexOf("\"piano\""),
        length = "\"piano\"".length,
        expectedStyle = UITheme.String
      )

  private val nonFunctionCases = Table(
    ("description", "source", "target"),
    ("identifier without parentheses", "note value", "note"),
    ("identifier followed by whitespace", "sound value", "sound"),
    ("property name", "player.sound", "sound")
  )

  test("does not highlight identifiers that are not function calls"):
    forAll(nonFunctionCases): (_, source, target) =>
      onFxThread:
        val editor = highlightedEditor(source)
        val start = source.indexOf(target)

        assertRangeDoesNotContain(
          editor,
          start,
          target.length,
          UITheme.Function
        )

  test("does not apply syntax styles to punctuation"):
    onFxThread:
      val editor = highlightedEditor(code)
      val dotPosition = code.indexOf('.')

      val style = editor.getStyleOfChar(dotPosition)

      assert(!style.contains(UITheme.Function))
      assert(!style.contains(UITheme.String))

  test("highlights escaped characters inside string literals"):
    onFxThread:
      val source = """note("c4 \"accent\"")"""
      val editor = highlightedEditor(source)
      val stringStart = source.indexOf('"')
      val stringLength = source.lastIndexOf('"') - stringStart + 1

      assertRangeContains(
        editor,
        stringStart,
        stringLength,
        UITheme.String
      )

  test("highlights syntax independently on multiple lines"):
    onFxThread:
      val source =
        "note(\"c4\")\nsound(\"piano\")"

      val editor = highlightedEditor(source)
      val soundStart = source.indexOf("sound")
      val pianoStart = source.indexOf("\"piano\"")

      assertRangeContains(
        editor,
        soundStart,
        "sound".length,
        UITheme.Function
      )

      assertRangeContains(
        editor,
        pianoStart,
        "\"piano\"".length,
        UITheme.String
      )

  test("applies playback style only to the requested range"):
    onFxThread:
      val editor = highlightedEditor(
        code,
        Set(TextPosition(0, 4))
      )

      assertRangeContains(
        editor,
        start = 0,
        length = 4,
        expectedStyle = "-rtfx-border-stroke-color"
      )

      val characterAfterRange = editor.getStyleOfChar(4)

      assert(
        !characterAfterRange.contains(
          "-rtfx-border-stroke-color"
        )
      )

  test("clamps playback ranges to source boundaries"):
    onFxThread:
      val editor = highlightedEditor(
        code,
        Set(TextPosition(-10, 4))
      )

      assertRangeContains(
        editor,
        start = 0,
        length = 4,
        expectedStyle = "-rtfx-border-stroke-color"
      )

  test("ignores empty and out-of-bounds playback ranges"):
    onFxThread:
      val editor = highlightedEditor(
        code,
        Set(
          TextPosition(4, 4),
          TextPosition(100, 120),
          TextPosition(-10, -1)
        )
      )

      (0 until editor.getLength).foreach { index =>
        assert(
          !editor
            .getStyleOfChar(index)
            .contains("-rtfx-border-stroke-color")
        )
      }

  test("reapplying syntax highlighting removes old playback styles"):
    onFxThread:
      val editor = highlightedEditor(
        code,
        Set(TextPosition(0, 4))
      )

      assert(
        editor
          .getStyleOfChar(0)
          .contains("-rtfx-border-stroke-color")
      )

      SyntaxHighlighter.applyTo(editor)

      assert(
        !editor
          .getStyleOfChar(0)
          .contains("-rtfx-border-stroke-color")
      )

      assert(
        editor
          .getStyleOfChar(0)
          .contains(UITheme.Function)
      )

  private def highlightedEditor(
      source: String,
      playbackPositions: Set[TextPosition] = Set.empty
  ): InlineCssTextArea =
    val editor = new InlineCssTextArea
    editor.replaceText(source)

    SyntaxHighlighter.applyTo(
      editor,
      playbackPositions
    )

    editor

  private def assertRangeContains(
      editor: InlineCssTextArea,
      start: Int,
      length: Int,
      expectedStyle: String
  ): Unit =
    (start until start + length).foreach { index =>
      assert(
        editor.getStyleOfChar(index).contains(expectedStyle),
        s"Expected style '$expectedStyle' at index $index"
      )
    }

  private def assertRangeDoesNotContain(
      editor: InlineCssTextArea,
      start: Int,
      length: Int,
      unexpectedStyle: String
  ): Unit =
    (start until start + length).foreach { index =>
      assert(
        !editor.getStyleOfChar(index).contains(unexpectedStyle),
        s"Unexpected style '$unexpectedStyle' at index $index"
      )
    }

  test("string style takes precedence over function-like text inside strings"):
    onFxThread:
      val source = """note("sound(test)")"""
      val editor = highlightedEditor(source)
      val soundStart = source.indexOf("sound")
  
      assertRangeContains(
        editor,
        start = soundStart,
        length = "sound".length,
        expectedStyle = UITheme.String
      )
  
      assertRangeDoesNotContain(
        editor,
        start = soundStart,
        length = "sound".length,
        unexpectedStyle = UITheme.Function
      )