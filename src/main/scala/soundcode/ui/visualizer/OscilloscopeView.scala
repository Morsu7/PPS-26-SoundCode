package soundcode.ui.visualizer

import scalafx.scene.canvas.GraphicsContext
import scalafx.scene.paint.Color
import soundcode.ui.UITheme

final class OscilloscopeView extends CanvasAnimatedView:

  override protected def draw(
      gc: GraphicsContext,
      currentBeat: Double,
      w: Double,
      h: Double
  ): Unit =
    drawBaseline(gc, w, h)
    drawWaveform(gc, currentBeat, w, h)

  private def drawBaseline(gc: GraphicsContext, w: Double, h: Double): Unit =
    val centerY = h * 0.5

    gc.stroke = Color.web(UITheme.VisualizerLine)
    gc.lineWidth = 1
    gc.strokeLine(0, centerY, w, centerY)

  private def drawWaveform(
      gc: GraphicsContext,
      currentBeat: Double,
      w: Double,
      h: Double
  ): Unit =
    val samples = 256
    val visibleBeats = w / pixelsPerBeat
    val centerY = h * 0.5
    var amplitude = (h - config.verticalPadding * 2) * 0.38

    gc.stroke = Color.web(UITheme.Foreground)
    gc.lineWidth = 2
    gc.beginPath()

    for sample <- 0 until samples do
      val progress = sample.toDouble / (samples - 1)
      val beat = currentBeat + progress * visibleBeats
      val x = progress * w
      val y = centerY + signalAt(beat) * amplitude

      if sample == 0 then gc.moveTo(x, y)
      else gc.lineTo(x, y)

    gc.stroke()

  private def signalAt(beat: Double): Double =
    Math.sin(beat * 2 * Math.PI) * 0.5 + Math.sin(beat * 4 * Math.PI) * 0.25
