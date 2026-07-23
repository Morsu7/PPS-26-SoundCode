package soundcode.ui

import org.scalatest.funsuite.AnyFunSuite
import soundcode.parser.SoundCodeLanguage
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

  test("completion items describe their arguments"):
    val fast =
      SoundCodeLanguage.Transformation.all
        .find(_.name == SoundCodeLanguage.Transformation.FastForward)
        .get

    assert(fast.signature == """fast(factor: "numeric pattern")""")

  test("delay exposes both supported signatures"):
    val delay =
      SoundCodeLanguage.Transformation.all
        .find(_.name == SoundCodeLanguage.Transformation.Delay)
        .get

    assert(
      delay.signatures == Vector(
        """delay(amount: "numeric pattern")""",
        """delay(amount: "numeric pattern", time: "numeric pattern", feedback: "numeric pattern")"""
      )
    )

  test("numeric pattern completions insert quoted arguments"):
    val namesRequiringQuotedPattern = Set(
      SoundCodeLanguage.Transformation.Gain,
      SoundCodeLanguage.Transformation.Pan,
      SoundCodeLanguage.Transformation.Room,
      SoundCodeLanguage.Transformation.Delay,
      SoundCodeLanguage.Transformation.LowPassFilter,
      SoundCodeLanguage.Transformation.HighPassFilter,
      SoundCodeLanguage.Transformation.FastForward,
      SoundCodeLanguage.Transformation.SlowMotion,
      SoundCodeLanguage.Transformation.Early,
      SoundCodeLanguage.Transformation.Late,
      SoundCodeLanguage.Transformation.Repetition,
      SoundCodeLanguage.Transformation.Offset
    )

    val snippets =
      SoundCodeLanguage.Transformation.all
        .filter(item => namesRequiringQuotedPattern.contains(item.name))
        .map(item => item.name -> item.snippet)
        .toMap

    snippets.foreach { case (name, snippet) =>
      assert(
        snippet.startsWith(s"""$name("$$0""""),
        s"$name must put its numeric pattern inside quotes: $snippet"
      )
    }
