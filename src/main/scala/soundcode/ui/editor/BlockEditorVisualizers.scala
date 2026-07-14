package soundcode.ui.editor

import soundcode.ui.visualizer.AnimatedView
import soundcode.ui.visualizer.PianorollView
import soundcode.ui.visualizer.OscilloscopeView
import soundcode.domain.{
  AudioPayload,
  Fraction,
  Interval,
  Note,
  ScheduledEvent,
  Sound,
  TextPosition
}

private object BlockEditorVisualizers:
  def forLine(lineIndex: Int): Option[AnimatedView] =
    if lineIndex == 0 then
      Some(
        new PianorollView(
          Seq[ScheduledEvent[AudioPayload]](
            ScheduledEvent(
              whole = Interval(Fraction(0), Fraction(1, 4)),
              part = Interval(Fraction(0), Fraction(1, 4)),
              value = Sound.NoteInText(
                Note("C4"),
                TextPosition(0, 2)
              )
            ),
            ScheduledEvent(
              whole = Interval(Fraction(1, 4), Fraction(1, 2)),
              part = Interval(Fraction(1, 4), Fraction(1, 2)),
              value = Sound.NoteInText(
                Note("E4"),
                TextPosition(3, 5)
              )
            ),
            ScheduledEvent(
              whole = Interval(Fraction(1, 2), Fraction(3, 4)),
              part = Interval(Fraction(1, 2), Fraction(3, 4)),
              value = Sound.NoteInText(
                Note("G4"),
                TextPosition(6, 8)
              )
            ),
            ScheduledEvent(
              whole = Interval(Fraction(3, 4), Fraction(1)),
              part = Interval(Fraction(3, 4), Fraction(1)),
              value = Sound.NoteInText(
                Note("B4"),
                TextPosition(9, 11)
              )
            )
          )
          // CycleCalculator.
        )
      )
    // else if offset == 1 then Some(new OscilloscopeView)
    else None
