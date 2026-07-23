package soundcode.ui

import org.scalatest.funsuite.AnyFunSuite
import soundcode.ui.editor.CompletionProvider

class CompletionProviderSpec extends AnyFunSuite:

  test("extracts the prefix before the caret"):
    val context =
      CompletionProvider.contextFrom(
        text = """sound("bd").ga""",
        caretPosition = """sound("bd").ga""".length
      )

    assert(context.prefix == "ga")
    assert(context.isAfterDot)

  test("recognizes a generative construct at line start"):
    val context =
      CompletionProvider.contextFrom(
        text = "  sou",
        caretPosition = 5
      )

    assert(context.prefix == "sou")
    assert(context.isAtLineStart)

  test("does not suggest completions inside quotes"):
    val text = """sound("sou"""

    val context =
      CompletionProvider.contextFrom(
        text = text,
        caretPosition = text.length
      )

    assert(
      CompletionProvider
        .suggestionsFor(context)
        .isEmpty
    )

  test("suggests transformations after a dot"):
    val text = """sound("bd").ga"""

    val context =
      CompletionProvider.contextFrom(
        text = text,
        caretPosition = text.length
      )

    val names =
      CompletionProvider
        .suggestionsFor(context)
        .map(_.name)

    assert(names.contains("gain"))