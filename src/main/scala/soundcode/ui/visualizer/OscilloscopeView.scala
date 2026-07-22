package soundcode.ui.visualizer

import scalafx.scene.canvas.GraphicsContext
import scalafx.scene.paint.Color
import soundcode.ui.UITheme
import soundcode.domain.Tempo
import soundcode.engine.ActiveNote

final class OscilloscopeView(
    tempo: Tempo
) extends CanvasAnimatedView(tempo):

  private var activeNotes: Seq[ActiveNote] = Seq.empty

  def updateNotes(notes: Seq[ActiveNote]): Unit =
    activeNotes = notes

  override protected def draw(
      gc: GraphicsContext,
      currentCycle: Double,
      w: Double,
      h: Double
  ): Unit =
    drawBaseline(gc, w, h)
    drawWaveform(gc, currentCycle, w, h)

  private def drawBaseline(
      gc: GraphicsContext,
      w: Double,
      h: Double
  ): Unit =
    val centerY = h * 0.5

    gc.stroke = Color.web(UITheme.VisualizerLine)
    gc.lineWidth = 1
    gc.strokeLine(0, centerY, w, centerY)

  private def drawWaveform(
      gc: GraphicsContext,
      currentCycle: Double,
      w: Double,
      h: Double
  ): Unit =
    val samples = 512
    val windowSeconds = 0.080
    val elapsedSeconds = currentCycle / tempo.cps
    val centerY = h * 0.5
    val heightScale =
      (h - config.verticalPadding * 2) * 0.30

    gc.stroke = Color.web(UITheme.Foreground)
    gc.lineWidth = 2
    gc.beginPath()

    for sample <- 0 until samples do
      val progress =
        sample.toDouble / (samples - 1)

      val timeSeconds =
        elapsedSeconds + progress * windowSeconds

      val rawSignal = signalAt(timeSeconds)

      // Solo adattamento grafico per mantenere il segnale nel canvas.
      val displayedSignal = Math.tanh(rawSignal)

      val x = progress * w
      val y = centerY - displayedSignal * heightScale

      if sample == 0 then gc.moveTo(x, y)
      else gc.lineTo(x, y)

    gc.stroke()

  private def signalAt(timeSeconds: Double): Double =
    activeNotes.iterator
      .map { note =>
        note.amplitude *
          Math.sin(
            2.0 *
              Math.PI *
              note.frequencyHz *
              timeSeconds
          )
      }
      .sum