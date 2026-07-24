package soundcode.ui.visualizer

import scalafx.scene.Node
import scalafx.scene.canvas.GraphicsContext
import scalafx.scene.layout.VBox
import scalafx.scene.canvas.Canvas
import scalafx.geometry.Insets
import scalafx.scene.paint.Color
import scalafx.animation.AnimationTimer
import scalafx.application.Platform
import scalafx.scene.layout.Pane
import soundcode.ui.theme.UITheme
import soundcode.domain.Tempo
import scala.collection.mutable.LinkedHashSet

private case class VisualEvent(
    label: String,
    start: Double,
    duration: Double,
    lane: Int,
    color: Color
)

trait AnimatedView:
  def root: Node
  def play(): Unit
  def stop(): Unit

private object SharedAnimationTicker:
  private val views = LinkedHashSet.empty[CanvasAnimatedView]

  private val timer = AnimationTimer { now =>
    views.foreach(_.onAnimationFrame(now))
  }

  def add(view: CanvasAnimatedView): Unit =
    if views.add(view) && views.size == 1 then timer.start()

  def remove(view: CanvasAnimatedView): Unit =
    if views.remove(view) && views.isEmpty then timer.stop()

abstract class CanvasAnimatedView(
    protected val tempo: Tempo
) extends AnimatedView:
  protected val pixelsPerCycle: Double = 130.0
  protected val canvasHeight: Double = 100.0

  protected val config: VisualizerConfig = VisualizerConfig.default

  protected val canvas: Canvas = new Canvas(0, canvasHeight)

  private val canvasPane = new Pane:
    minWidth = 0
    prefWidth = 0
    maxWidth = Double.MaxValue
    minHeight = canvasHeight
    prefHeight = canvasHeight
    maxHeight = canvasHeight
    children = canvas

  canvas.managed = false
  canvas.height = canvasHeight

  private val view = new VBox:
    spacing = 4
    padding = Insets(config.horizontalPadding)
    minWidth = 0
    prefWidth = 0
    maxWidth = Double.MaxValue
    children = canvasPane

  canvasPane.prefWidthProperty().bind(view.widthProperty())
  canvas.widthProperty().bind(canvasPane.widthProperty())

  canvas.width.onChange {
    redraw(lastCycle)
  }

  override val root: Node = view

  private var running = false
  private var resumedAtNano = 0L
  private var elapsedBeforePauseNano = 0L
  private var lastFrameNano: Long = 0L
  private var lastCycle = 0.0
  private val frameIntervalNanos = 1_000_000_000L / 60L

  private[ui] def isRunning: Boolean = running

  protected def clear(gc: GraphicsContext, w: Double, h: Double): Unit =
    gc.fill = Color.web(UITheme.Background)
    gc.fillRect(0, 0, w, h)

  protected def draw(
      gc: GraphicsContext,
      currentCycle: Double,
      w: Double,
      h: Double
  ): Unit

  private def redraw(currentCycle: Double): Unit =
    lastCycle = currentCycle

    val gc = canvas.graphicsContext2D
    val w = canvas.width.value
    val h = canvas.height.value

    if w <= 0 || h <= 0 then return

    clear(gc, w, h)
    draw(gc, lastCycle, w, h)

  Platform.runLater {
    redraw(0.0)
  }

  private[visualizer] def onAnimationFrame(now: Long): Unit =
    if lastFrameNano == 0L || now - lastFrameNano >= frameIntervalNanos then
      lastFrameNano = now

      val elapsedNano =
        elapsedBeforePauseNano + (System.nanoTime() - resumedAtNano)

      val elapsedSeconds = elapsedNano / 1e9
      val currentCycle = elapsedSeconds * tempo.cps

      redraw(currentCycle)

  override def play(): Unit =
    if !running then
      running = true
      resumedAtNano = System.nanoTime()
      lastFrameNano = 0L

      SharedAnimationTicker.add(this)

  override def stop(): Unit =
    if running then
      elapsedBeforePauseNano += System.nanoTime() - resumedAtNano
      running = false

      SharedAnimationTicker.remove(this)
