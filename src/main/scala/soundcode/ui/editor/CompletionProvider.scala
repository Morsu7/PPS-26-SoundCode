package soundcode.ui.editor

import soundcode.parser.SoundCodeLanguage

private[ui] object CompletionProvider:
  type CompletionItem =
    SoundCodeLanguage.Construct

  final case class Context(
      textBeforeCaret: String,
      prefix: String,
      lineBeforeCaret: String
  ):
    def isInsideQuotes: Boolean =
      textBeforeCaret.count(_ == '"') % 2 == 1

    def isAfterDot: Boolean =
      lineBeforeCaret
        .dropRight(prefix.length)
        .endsWith(".")

    def isAtLineStart: Boolean =
      lineBeforeCaret.trim == prefix

  private val afterDotCandidates =
    SoundCodeLanguage.Generative.all ++
      SoundCodeLanguage.Transformation.all ++
      SoundCodeLanguage.Visualization.all

  private val lineStartCandidates =
    SoundCodeLanguage.Generative.all ++
      SoundCodeLanguage.Settings.all

  def contextFrom(
      text: String,
      caretPosition: Int
  ): Context =
    val textBeforeCaret =
      text.take(caretPosition)

    val prefix =
      textBeforeCaret.reverse
        .takeWhile(character =>
          character.isLetterOrDigit ||
            character == '_'
        )
        .reverse

    val lineBeforeCaret =
      textBeforeCaret.drop(
        textBeforeCaret.lastIndexOf('\n') + 1
      )

    Context(
      textBeforeCaret = textBeforeCaret,
      prefix = prefix,
      lineBeforeCaret = lineBeforeCaret
    )

  def suggestionsFor(
      context: Context
  ): Vector[CompletionItem] =
    if context.isInsideQuotes then
      Vector.empty
    else
      val candidates =
        if context.isAfterDot then
          afterDotCandidates
        else if context.isAtLineStart then
          lineStartCandidates
        else
          Vector.empty

      candidates.filter(
        _.name.startsWith(context.prefix)
      )