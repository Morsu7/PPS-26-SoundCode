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

  private val existingClosingCharacters = Table(
    ("initialText", "closing"),
    ("()", ")"),
    ("[]", "]"),
    ("<>", ">"),
    ("\"\"", "\"")
  )

  private val wrappingPairs = Table(
    ("opening", "expectedText"),
    ("(", "(text)"),
    ("[", "[text]"),
    ("<", "<text>"),
    ("\"", "\"text\"")
  )

  private val closingCharacters = Table(
    ("closing", "expectedText"),
    (")", "text)"),
    ("]", "text]"),
    (">", "text>")
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

  test("moves over an existing closing character without duplicating it"):
    forAll(existingClosingCharacters): (initialText, closing) =>
      onFxThread:
        val editor = installedEditor(initialText)
        editor.moveTo(1)

        fireKeyTyped(editor, closing)

        assert(editor.getText == initialText)
        assert(editor.getCaretPosition == 2)

  test("inserts a closing character when it is not already present"):
    forAll(closingCharacters): (closing, expectedText) =>
      onFxThread:
        val editor = installedEditor("text")

        fireKeyTyped(editor, closing)

        assert(editor.getText == expectedText)
        assert(editor.getCaretPosition == expectedText.length)

  test("does not skip a different closing character"):
    onFxThread:
      val editor = installedEditor("x]")
      editor.moveTo(1)

      fireKeyTyped(editor, ")")

      assert(editor.getText == "x)]")
      assert(editor.getCaretPosition == 2)

  test("does not skip a closing character when text is selected"):
    onFxThread:
      val editor = installedEditor("ab)")
      editor.selectRange(1, 2)

      fireKeyTyped(editor, ")")

      assert(editor.getText == "a))")
      assert(editor.getCaretPosition == 2)

  test("wraps selected text with the matching pair"):
    forAll(wrappingPairs): (opening, expectedText) =>
      onFxThread:
        val editor = installedEditor("text")
        editor.selectRange(0, editor.getLength)

        fireKeyTyped(editor, opening)

        assert(editor.getText == expectedText)
        assert(editor.getSelectedText == "text")
        assert(editor.getSelection.getStart == 1)
        assert(editor.getSelection.getEnd == 5)

  test("wraps a selection in the middle of the text"):
    onFxThread:
      val editor = installedEditor("before text after")

      val start = "before ".length
      val end = start + "text".length

      editor.selectRange(start, end)
      fireKeyTyped(editor, "(")

      assert(editor.getText == "before (text) after")
      assert(editor.getSelectedText == "text")
      assert(editor.getSelection.getStart == start + 1)
      assert(editor.getSelection.getEnd == end + 1)

  test("uses the provided custom pairs"):
    onFxThread:
      val editor = installedEditor(
        pairs = Seq(
          AutoPairingSupport.Pair("{", "}")
        )
      )

      fireKeyTyped(editor, "{")

      assert(editor.getText == "{}")
      assert(editor.getCaretPosition == 1)

  test("skips an existing custom closing character"):
    onFxThread:
      val editor = installedEditor(
        initialText = "{}",
        pairs = Seq(
          AutoPairingSupport.Pair("{", "}")
        )
      )
      editor.moveTo(1)

      fireKeyTyped(editor, "}")

      assert(editor.getText == "{}")
      assert(editor.getCaretPosition == 2)

  private def installedEditor(
    initialText: String = "",
    pairs: Seq[AutoPairingSupport.Pair] = AutoPairingSupport.DefaultPairs
  ): InlineCssTextArea =
    val editor = new InlineCssTextArea
    editor.replaceText(initialText)
    editor.moveTo(initialText.length)
    AutoPairingSupport.install(editor, pairs)
    editor
