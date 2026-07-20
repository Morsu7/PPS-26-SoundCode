package soundcode.ui

import scalafx.animation.{FadeTransition, Interpolator}
import scalafx.geometry.Pos
import scalafx.scene.control.{Button, Label}
import scalafx.scene.layout.{HBox, Priority}
import scalafx.util.Duration

import scala.annotation.nowarn

final class ErrorBanner(
  onDismiss: () => Unit
):
  
  private val AnimDuration = Duration(100)

  private val errorLabel = new Label:
    wrapText = true
    maxWidth = Double.MaxValue

    style =
      """
        |-fx-text-fill: #ffb4ab;
        |-fx-font-family: 'Cascadia Code', 'JetBrains Mono', 'Consolas';
        |-fx-font-size: 13px;
        |""".stripMargin

  @nowarn("msg=Implicit parameters should be provided with a `using` clause")
  private val closeButton = new Button("\u00D7"):
    focusTraversable = false

    style =
      """
        |-fx-background-color: transparent;
        |-fx-text-fill: #ffb4ab;
        |-fx-font-size: 18px;
        |-fx-font-weight: bold;
        |-fx-cursor: hand;
        |""".stripMargin

    onAction = _ => dismiss()

  val root: HBox = new HBox:
    visible = false
    managed = false
    opacity = 1.0
    alignment = Pos.CenterLeft
    spacing = 8

    style =
      """
        |-fx-background-color: #3f1d24;
        |-fx-padding: 10;
        |""".stripMargin

    children = Seq(errorLabel, closeButton)

  HBox.setHgrow(errorLabel, Priority.Always)

  private var displayedError: Option[String] = None

  private val fadeIn =
    new FadeTransition(AnimDuration, root):
      fromValue = 0.0
      toValue = 1.0
      interpolator = Interpolator.EaseOut

  private val fadeOut =
    new FadeTransition(AnimDuration, root):
      toValue = 0.0
      interpolator = Interpolator.EaseOut

      onFinished = _ =>
        closeButton.disable = false
        onDismiss()

  def render(error: Option[String]): Unit =
    error match
      case Some(message) =>
        errorLabel.text = message
        root.visible = true
        root.managed = true

        if !displayedError.contains(message) then
          fadeIn.stop()
          fadeOut.stop()

          displayedError = Some(message)
          closeButton.disable = false
          root.opacity = 0.0

          fadeIn.playFromStart()

      case None =>
        fadeIn.stop()
        fadeOut.stop()

        displayedError = None
        errorLabel.text = ""
        closeButton.disable = false

        root.opacity = 1.0
        root.visible = false
        root.managed = false

  private def dismiss(): Unit =
    fadeIn.stop()
    fadeOut.stop()

    closeButton.disable = true
    fadeOut.fromValue = root.opacity.value
    fadeOut.playFromStart()