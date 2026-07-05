package soundcode.ui.editor

import org.fxmisc.richtext.GenericStyledArea
import scala.jdk.CollectionConverters.*

private final class BlockEditorDocument[Segment](
    area: GenericStyledArea[?, Segment, ?],
    isVisualizerSegment: Segment => Boolean
):
  def isVisualizerParagraph(paragraphIndex: Int): Boolean =
    paragraphIndex >= 0 &&
      paragraphIndex < area.getParagraphs.size() &&
      area
        .getParagraph(paragraphIndex)
        .getSegments
        .asScala
        .exists(isVisualizerSegment)

  def visibleLineNumber(paragraphIndex: Int): Int =
    (0 to paragraphIndex).count(index => !isVisualizerParagraph(index))

  def codePositionNear(paragraphIndex: Int): Int =
    val previousCodeParagraph =
      (paragraphIndex - 1 to 0 by -1).find(index =>
        !isVisualizerParagraph(index)
      )

    val nextCodeParagraph =
      (paragraphIndex + 1 until area.getParagraphs.size())
        .find(index => !isVisualizerParagraph(index))

    previousCodeParagraph
      .map(index =>
        area.getAbsolutePosition(index, area.getParagraph(index).length())
      )
      .orElse(
        nextCodeParagraph.map(index => area.getAbsolutePosition(index, 0))
      )
      .getOrElse(0)
