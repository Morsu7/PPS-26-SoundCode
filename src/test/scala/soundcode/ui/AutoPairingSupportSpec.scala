package soundcode.ui

import org.fxmisc.richtext.InlineCssTextArea
import soundcode.ui.editor.AutoPairingSupport
import org.scalatest.prop.TableDrivenPropertyChecks

class AutoPairingSupportSpec 
    extends UITestSupport
    with TableDrivenPropertyChecks:

  private val supportedPairs = Table(
    ("typed", "expectedText"),
    ("(", "()"),
    ("[", "[]"),
    ("<", "<>"),
    ("\"", "\"\"")
  )

  test("inserts the matching pair and places the caret between it"):
    forAll(supportedPairs): (typed, expectedText) =>
      onFxThread:
        val editor = installedEditor()

        fireKeyTyped(editor, typed)

        assert(editor.getText == expectedText)
        assert(editor.getCaretPosition == 1)

  test("inserts the pair at the current caret position"):
    onFxThread:
      val editor = installedEditor("abcd")
      editor.moveTo(2)

      fireKeyTyped(editor, "(")

      assert(editor.getText == "ab()cd")
      assert(editor.getCaretPosition == 3)

  test("does not auto-pair a quote inside an open string"):
    onFxThread:
      val editor = installedEditor("\"text")
  
      fireKeyTyped(editor, "\"")
  
      assert(editor.getText == "\"text\"")
      assert(editor.getCaretPosition == 6)

  private def installedEditor(
      initialText: String = ""
  ): InlineCssTextArea =
    val editor = new InlineCssTextArea
    editor.replaceText(initialText)
    editor.moveTo(initialText.length)
    AutoPairingSupport.install(editor)
    editor