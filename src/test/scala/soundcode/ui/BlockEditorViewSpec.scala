package soundcode.ui

import soundcode.ui.editor.BlockEditorView
import soundcode.mvu.AppModel
import soundcode.domain.TextPosition
import soundcode.domain.Tempo
import org.scalatest.prop.TableDrivenPropertyChecks
import soundcode.ui.theme.UITheme

class BlockEditorViewSpec 
    extends UITestSupport
    with TableDrivenPropertyChecks:
      
  test("creates an editor containing the default source code"):
    onFxThread:
      val editor = new BlockEditorView

      assert(editor.root != null)
      assert(editor.currentCode.nonEmpty)

  test("returns the initial source code"):
    onFxThread:
      val code = "note(\"c4 a4\").sound(\"piano\")\nsound(\"hb hd hh\")"

      val editor = new BlockEditorView(code)

      assert(editor.currentCode == code)

  private val newlineCases = Table(
    ("description", "source", "expected"),
    (
      "Windows CRLF",
      "note(\"c4\")\r\nsound(\"hh\")",
      "note(\"c4\")\nsound(\"hh\")"
    ),
    (
      "legacy carriage return",
      "note(\"c4\")\rsound(\"hh\")",
      "note(\"c4\")\nsound(\"hh\")"
    ),
    (
      "Unix newline",
      "note(\"c4\")\nsound(\"hh\")",
      "note(\"c4\")\nsound(\"hh\")"
    )
  )

  test("normalizes source-code newlines"):
    forAll(newlineCases): (_, source, expected) =>
      onFxThread:
        val editor = new BlockEditorView(source)

        assert(editor.currentCode == expected)

  test("render applies playback highlighting to the requested range"):
    onFxThread:
      val code = """note("c4").sound("piano")"""
      val editor = new BlockEditorView(code)

      editor.render(
        AppModel(positions = Set(TextPosition(0, 4)))
      )

      val highlightedStyle =
        editor.editorArea.getStyleOfChar(0)

      assert(highlightedStyle.contains("-rtfx-border-stroke-color"))
      assert(highlightedStyle.contains("-fx-font-weight: bold"))

  test("render preserves syntax highlighting outside playback range"):
    onFxThread:
      val code = """note("c4").sound("piano")"""
      val editor = new BlockEditorView(code)

      editor.render(
        AppModel(positions = Set(TextPosition(0, 4)))
      )

      val stringStart = code.indexOf("\"c4\"")
      val stringStyle =
        editor.editorArea.getStyleOfChar(stringStart)

      assert(stringStyle.contains(UITheme.String))
      assert(!stringStyle.contains("-rtfx-border-stroke-color"))

  test("render ignores playback positions outside the source code"):
    onFxThread:
      val code = """note("c4")"""
      val editor = new BlockEditorView(code)

      editor.render(
        AppModel(
          positions = Set(
            TextPosition(-10, -1),
            TextPosition(100, 120)
          )
        )
      )

      assert(editor.currentCode == code)

  test("render does not modify source code"):
    onFxThread:
      val code = "note(\"c4 a4\").sound(\"piano\")\nsound(\"hb hd hh\")"

      val editor = new BlockEditorView(code)

      editor.render(
        AppModel(positions = Set(TextPosition(0, 4)))
      )

      assert(editor.currentCode == code)