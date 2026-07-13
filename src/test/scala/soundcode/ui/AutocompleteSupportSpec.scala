package soundcode.ui

import javafx.scene.Scene
import javafx.scene.input.{KeyCode, KeyEvent}
import javafx.stage.Stage
import org.fxmisc.richtext.InlineCssTextArea
import soundcode.ui.UITestSupport
import soundcode.ui.editor.AutocompleteSupport

class AutocompleteSupportSpec extends UITestSupport:
  test("suggests and inserts a generative block at line start"):
    onFxThread:
      val editor = mountedEditor("sou")

      AutocompleteSupport.install(editor)

      triggerAutocomplete(editor)
      fireKeyPressed(editor, KeyCode.ENTER)

      assert(editor.getText == """sound("")""")
      assert(editor.getCaretPosition == """sound("""".length)

  test("suggests and inserts a transformation after dot"):
    onFxThread:
      val editor = mountedEditor("""sound("bd").ga""")

      AutocompleteSupport.install(editor)

      triggerAutocomplete(editor)
      fireKeyPressed(editor, KeyCode.ENTER)

      assert(editor.getText == """sound("bd").gain("")""")
      assert(editor.getCaretPosition == """sound("bd").gain("""".length)

  test("does not suggest completions inside string literals"):
    onFxThread:
      val editor = mountedEditor("""sound("sou""")

      AutocompleteSupport.install(editor)

      triggerAutocomplete(editor)

      assert(editor.getText == """sound("sou""")

  test("does not suggest transformations without dot context"):
    onFxThread:
      val editor = mountedEditor("""sound("bd") ga""")

      AutocompleteSupport.install(editor)

      triggerAutocomplete(editor)

      assert(editor.getText == """sound("bd") ga""")

  test("escape dismisses autocomplete without changing text"):
    onFxThread:
      val editor = mountedEditor("sou")

      AutocompleteSupport.install(editor)

      triggerAutocomplete(editor)
      fireKeyPressed(editor, KeyCode.ESCAPE)

      assert(editor.getText == "sou")

  private def mountedEditor(text: String): InlineCssTextArea =
    val editor = new InlineCssTextArea
    editor.replaceText(text)
    editor.moveTo(text.length)

    val stage = new Stage()
    stage.setScene(new Scene(editor, 500, 300))
    stage.show()

    editor.requestFocus()
    editor

  private def triggerAutocomplete(editor: InlineCssTextArea): Unit =
    editor.fireEvent(
      new KeyEvent(
        KeyEvent.KEY_RELEASED,
        "",
        "",
        KeyCode.UNDEFINED,
        false,
        false,
        false,
        false
      )
    )
