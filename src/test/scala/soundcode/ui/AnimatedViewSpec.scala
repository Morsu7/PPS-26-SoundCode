package soundcode.ui

import scalafx.scene.canvas.GraphicsContext
import soundcode.domain.Tempo
import soundcode.ui.visualizer.CanvasAnimatedView

class AnimatedViewSpec extends UITestSupport:

  private val tempo = Tempo(0.5)

  test("exposes a root node"):
    onFxThread:
      val view = TestAnimatedView(tempo)

      assert(view.root != null)

  test("is initially stopped"):
    onFxThread:
      val view = TestAnimatedView(tempo)

      assert(!view.isRunning)

  test("play starts the animation"):
    onFxThread:
      val view = TestAnimatedView(tempo)

      view.play()

      assert(view.isRunning)

      // Evita di lasciare attivo il timer dopo il test.
      view.stop()

  test("stop terminates the animation"):
    onFxThread:
      val view = TestAnimatedView(tempo)

      view.play()
      view.stop()

      assert(!view.isRunning)

  test("play and stop are idempotent"):
    onFxThread:
      val view = TestAnimatedView(tempo)

      view.play()
      view.play()

      assert(view.isRunning)

      view.stop()
      view.stop()

      assert(!view.isRunning)

  private final class TestAnimatedView(
      tempo: Tempo
  ) extends CanvasAnimatedView(tempo):

    override protected def draw(
        gc: GraphicsContext,
        currentCycle: Double,
        width: Double,
        height: Double
    ): Unit =
      ()

  private object TestAnimatedView:
    def apply(tempo: Tempo): TestAnimatedView =
      new TestAnimatedView(tempo)
