package soundcode.ui

import org.fxmisc.richtext.InlineCssTextArea
import soundcode.domain.TextPosition
import soundcode.ui.editor.SyntaxHighlighter

class SyntaxHighlighterSpec extends UITestSupport:

  test("highlights function names"):
    onFxThread:
      val editor = new InlineCssTextArea
      editor.replaceText("""note("c4").sound("piano")""")

      SyntaxHighlighter.applyTo(editor)

      assert(editor.getStyleOfChar(0).contains(UITheme.Function))
      assert(editor.getStyleOfChar(1).contains(UITheme.Function))
      assert(editor.getStyleOfChar(2).contains(UITheme.Function))
      assert(editor.getStyleOfChar(3).contains(UITheme.Function))

      val soundStart = editor.getText.indexOf("sound")
      assert(editor.getStyleOfChar(soundStart).contains(UITheme.Function))

  test("highlights string literals"):
    onFxThread:
      val editor = new InlineCssTextArea
      editor.replaceText("""note("c4").sound("piano")""")

      SyntaxHighlighter.applyTo(editor)

      val firstStringStart = editor.getText.indexOf("\"c4\"")
      val secondStringStart = editor.getText.indexOf("\"piano\"")

      assert(editor.getStyleOfChar(firstStringStart).contains(UITheme.String))
      assert(
        editor.getStyleOfChar(firstStringStart + 1).contains(UITheme.String)
      )
      assert(editor.getStyleOfChar(secondStringStart).contains(UITheme.String))
      assert(
        editor.getStyleOfChar(secondStringStart + 1).contains(UITheme.String)
      )

  test("does not highlight plain text as function or string"):
    onFxThread:
      val editor = new InlineCssTextArea
      editor.replaceText("""note("c4").sound("piano")""")

      SyntaxHighlighter.applyTo(editor)

      val dotPosition = editor.getText.indexOf(".")

      assert(!editor.getStyleOfChar(dotPosition).contains(UITheme.Function))
      assert(!editor.getStyleOfChar(dotPosition).contains(UITheme.String))

  test("playback highlight overrides syntax style on selected range"):
    onFxThread:
      val editor = new InlineCssTextArea
      editor.replaceText("""note("c4").sound("piano")""")

      SyntaxHighlighter.applyTo(
        editor,
        playbackPositions = Set(TextPosition(0, 4))
      )

      val style = editor.getStyleOfChar(0)

      assert(style.contains("-rtfx-border-stroke-color"))
      assert(style.contains(UITheme.String))
      assert(style.contains("-fx-font-weight: bold"))
