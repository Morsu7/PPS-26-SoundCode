package soundcode.ui

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

import javafx.scene.input.{KeyEvent, KeyCode}
import scalafx.application.Platform
import org.scalatest.funsuite.AnyFunSuite
import org.fxmisc.richtext.GenericStyledArea

trait UITestSupport extends AnyFunSuite:
  // ensures tests create and inspect JavaFX components on the JavaFX Application Thread.
  protected def onFxThread[A](body: => A): A =
    if Platform.isFxApplicationThread then body
    else
      val result = AtomicReference[A]()
      val error = AtomicReference[Throwable]()
      val latch = CountDownLatch(1)

      try Platform.startup(() => ())
      catch case _: IllegalStateException => ()

      Platform.runLater(() =>
        try result.set(body)
        catch case throwable: Throwable => error.set(throwable)
        finally latch.countDown()
      )

      val completed = latch.await(5, TimeUnit.SECONDS)

      if !completed then
        throw RuntimeException(
          "Timed out waiting for JavaFX Application Thread"
        )

      if error.get() != null then throw error.get()
      result.get()

  protected def fireKeyTyped(
      editor: GenericStyledArea[?, ?, ?],
      character: String
  ): Unit =
    editor.fireEvent(
      new KeyEvent(
        KeyEvent.KEY_TYPED,
        character,
        "",
        KeyCode.UNDEFINED,
        false,
        false,
        false,
        false
      )
    )

  protected def fireKeyPressed(
      editor: GenericStyledArea[?, ?, ?],
      keyCode: KeyCode
  ): Unit =
    editor.fireEvent(
      new KeyEvent(
        KeyEvent.KEY_PRESSED,
        "",
        "",
        keyCode,
        false,
        false,
        false,
        false
      )
    )
