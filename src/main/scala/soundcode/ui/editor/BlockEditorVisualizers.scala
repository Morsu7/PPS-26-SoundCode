package soundcode.ui.editor

import soundcode.ui.visualizer.AnimatedView
import soundcode.ui.visualizer.PianoRollView
import soundcode.ui.visualizer.OscilloscopeView

private object BlockEditorVisualizers:
  def forLine(lineIndex: Int): Option[AnimatedView] =
    None
    // if lineIndex == 0 then Some(new PianoRollView)
    // else if offset == 1 then Some(new OscilloscopeView)
    // else None
