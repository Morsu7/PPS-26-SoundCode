package soundcode.ui

import javafx.scene.Scene
import javafx.scene.control.{Button, ToolBar}
import javafx.scene.input.{KeyCode, KeyCodeCombination, KeyCombination}
import org.scalatest.prop.TableDrivenPropertyChecks
import soundcode.domain.Tempo
import soundcode.mvu.Msg

import scala.collection.mutable.ArrayBuffer

class MainViewSpec 
    extends UITestSupport
    with TableDrivenPropertyChecks:
  
  test("creates the main layout with editor and toolbar"):
    onFxThread:
      val view = MainView(_ => ())

      assert(view.root.delegate.getCenter != null)
      assert(view.root.delegate.getTop.isInstanceOf[ToolBar])

  private val buttonCases = Table(
    ("label", "expectedMessage"),
    ("Play", Msg.PlayRequested),
    ("Stop", Msg.StopRequested)
  )

  test("toolbar buttons dispatch playback messages"):
    forAll(buttonCases): (label, expectedMessage) =>
      onFxThread:
        val dispatched = ArrayBuffer.empty[Msg]
        val view = MainView(dispatched += _)

        toolbarButton(view, label).fire()

        assert(dispatched.toList == List(expectedMessage))

  test("Update button dispatches the current source code"):
    onFxThread:
      val dispatched = ArrayBuffer.empty[Msg]
      val view = MainView(dispatched += _)

      toolbarButton(view, "Update").fire()

      assert(
        dispatched.toList ==
          List(Msg.CodeUpdateRequested(view.currentCode))
      )

  private val shortcutCases = Table(
    ("key", "modifier", "expectedMessage"),
    (
      KeyCode.ENTER,
      KeyCombination.CONTROL_DOWN,
      Msg.PlayRequested
    ),
    (
      KeyCode.PERIOD,
      KeyCombination.CONTROL_DOWN,
      Msg.StopRequested
    )
  )

  test("installs playback keyboard shortcuts when attached to a scene"):
    forAll(shortcutCases): (key, modifier, expectedMessage) =>
      onFxThread:
        val dispatched = ArrayBuffer.empty[Msg]
        val view = MainView(dispatched += _)
        val scene = new Scene(view.root.delegate)

        val shortcut =
          new KeyCodeCombination(key, modifier)

        val action = scene.getAccelerators.get(shortcut)

        assert(action != null)

        action.run()

        assert(dispatched.toList == List(expectedMessage))

  test("Ctrl+U dispatches a code update with the current source"):
    onFxThread:
      val dispatched = ArrayBuffer.empty[Msg]
      val view = MainView(dispatched += _)
      val scene = new Scene(view.root.delegate)

      val shortcut =
        new KeyCodeCombination(
          KeyCode.U,
          KeyCombination.CONTROL_DOWN
        )

      val action = scene.getAccelerators.get(shortcut)

      assert(action != null)

      action.run()

      assert(
        dispatched.toList ==
          List(Msg.CodeUpdateRequested(view.currentCode))
      )

  private def toolbarButton(
      view: MainView,
      label: String
  ): Button =
    val toolbar =
      view.root.delegate.getTop.asInstanceOf[ToolBar]

    toolbar.getItems
      .stream()
      .filter {
        case button: Button => button.getText == label
        case _              => false
      }
      .findFirst()
      .orElseThrow(() =>
        new AssertionError(
          s"Toolbar button '$label' was not found"
        )
      )
      .asInstanceOf[Button]