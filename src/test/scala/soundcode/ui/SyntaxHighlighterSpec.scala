package soundcode.ui

import org.fxmisc.richtext.InlineCssTextArea
import org.scalatest.prop.TableDrivenPropertyChecks
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

  private val numberCases = Table(
    ("source", "number"),
    ("setcps(0.5)", "0.5"),
    ("setcpm(120)", "120"),
    ("setcps(-0.25)", "-0.25"),
    ("setcps(+1.5)", "+1.5")
  )

  test("highlights numeric configuration values"):
    forAll(numberCases): (source, number) =>
      onFxThread:
        val editor = highlightedEditor(source)

        assertRangeContains(
          editor,
          start = source.indexOf(number),
          length = number.length,
          expectedStyle = UITheme.Number
        )

  test("string style takes precedence over numbers inside strings"):
    onFxThread:
      val source = """gain("0.5")"""
      val editor = highlightedEditor(source)
      val numberStart = source.indexOf("0.5")

      assertRangeContains(
        editor,
        start = numberStart,
        length = "0.5".length,
        expectedStyle = UITheme.String
      )
      assertRangeDoesNotContain(
        editor,
        start = numberStart,
        length = "0.5".length,
        unexpectedStyle = UITheme.Number
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

  private def highlightedEditor(source: String): InlineCssTextArea =
    val editor = new InlineCssTextArea
    editor.replaceText(source)

    SyntaxHighlighter.applyTo(editor)

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
