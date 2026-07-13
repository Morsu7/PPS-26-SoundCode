package soundcode.ui.editor

import org.fxmisc.richtext.model.SegmentOps
import soundcode.ui.visualizer.AnimatedView
import java.util.Optional

private sealed trait EmbeddedVisualizerSegment
private object EmbeddedVisualizerSegment:
  case object Empty extends EmbeddedVisualizerSegment
  final case class View(view: AnimatedView) extends EmbeddedVisualizerSegment

private object EmbeddedVisualizerSegmentOps
    extends SegmentOps[EmbeddedVisualizerSegment, String]:
  override def length(seg: EmbeddedVisualizerSegment): Int =
    seg match
      case EmbeddedVisualizerSegment.Empty   => 0
      case EmbeddedVisualizerSegment.View(_) => 1

  override def charAt(seg: EmbeddedVisualizerSegment, index: Int): Char =
    '\uFFFC'

  override def getText(seg: EmbeddedVisualizerSegment): String =
    ""

  override def subSequence(
      seg: EmbeddedVisualizerSegment,
      start: Int,
      end: Int
  ): EmbeddedVisualizerSegment =
    if start == 0 && end == length(seg) then seg
    else EmbeddedVisualizerSegment.Empty

  override def subSequence(
      seg: EmbeddedVisualizerSegment,
      start: Int
  ): EmbeddedVisualizerSegment =
    subSequence(seg, start, length(seg))

  override def joinSeg(
      current: EmbeddedVisualizerSegment,
      next: EmbeddedVisualizerSegment
  ): Optional[EmbeddedVisualizerSegment] =
    Optional.empty()

  override def createEmptySeg(): EmbeddedVisualizerSegment =
    EmbeddedVisualizerSegment.Empty
