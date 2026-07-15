package soundcode.ui

import org.fxmisc.richtext.InlineCssTextArea
import org.scalatest.prop.TableDrivenPropertyChecks
import soundcode.ui.editor.AutocompleteSupport

class AutocompleteSupportSpec
    extends UITestSupport
    with TableDrivenPropertyChecks:

  private val completionCases = Table(
    (
      "description",
      "initialText",
      "expectedText",
      "expectedCaret"
    ),
    (
      "generative block at line start",
      "sou",
      """sound("")""",
      """sound("""".length
    ),
    (
      "transformation after dot",
      """sound("bd").ga""",
      """sound("bd").gain("")""",
      """sound("bd").gain("""".length
    ),
    (
      "generative block after dot",
      """sound("bd").so""",
      """sound("bd").sound("")""",
      """sound("bd").sound("""".length
    )
  )

  test("inserts the first matching completion"):
    forAll(completionCases):
      (
          _,
          initialText,
          expectedText,
          expectedCaret
      ) =>
        onFxThread:
          val editor = editorWith(initialText)

          val completed =
            AutocompleteSupport.completeFirst(editor)

          assert(completed)
          assert(editor.getText == expectedText)
          assert(
            editor.getCaretPosition == expectedCaret
          )

  private val unsupportedContexts = Table(
    ("description", "text"),
    (
      "inside a string literal",
      """sound("sou"""
    ),
    (
      "transformation without dot",
      """sound("bd") ga"""
    ),
    (
      "unknown prefix",
      "unknown"
    ),
    (
      "middle of an expression",
      """sound("bd") sou"""
    )
  )

  test("does not complete unsupported contexts"):
    forAll(unsupportedContexts): (_, text) =>
      onFxThread:
        val editor = editorWith(text)

        val completed =
          AutocompleteSupport.completeFirst(editor)

        assert(!completed)
        assert(editor.getText == text)
        assert(editor.getCaretPosition == text.length)

  test("replaces only the prefix before the caret"):
    onFxThread:
      val initialText =
        "sou trailing"

      val editor = editorWith(
        initialText,
        caretPosition = 3
      )

      val completed =
        AutocompleteSupport.completeFirst(editor)

      assert(completed)
      assert(
        editor.getText ==
          """sound("") trailing"""
      )
      assert(
        editor.getCaretPosition ==
          """sound("""".length
      )

  test("completes at the start of a new line"):
    onFxThread:
      val initialText =
        """sound("bd")
          |sou""".stripMargin

      val editor = editorWith(initialText)

      val completed =
        AutocompleteSupport.completeFirst(editor)

      assert(completed)
      assert(
        normalizedText(editor) == "sound(\"bd\")\nsound(\"\")"
      )

  private def editorWith(
      text: String,
      caretPosition: Int = -1
  ): InlineCssTextArea =
    val editor = new InlineCssTextArea
    editor.replaceText(text)

    editor.moveTo(
      if caretPosition >= 0 then caretPosition
      else editor.getLength
    )

    editor

  private def normalizedText(
      editor: InlineCssTextArea
  ): String =
    editor.getText
      .replace("\r\n", "\n")
      .replace("\r", "\n")
