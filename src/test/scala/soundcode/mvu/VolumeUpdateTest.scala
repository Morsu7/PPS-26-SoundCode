package soundcode.mvu

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** Verifica il cablaggio MVU del volume: messaggio -> aggiornamento model + comando. */
class VolumeUpdateTest extends AnyFunSuite with Matchers:

  test("VolumeChangeRequested aggiorna model.volume e produce Cmd.SetVolume") {
    val (model, cmd) = Update.update(AppModel(), Msg.VolumeChangeRequested(42.0))
    model.volume shouldBe 42.0
    cmd shouldBe Cmd.SetVolume(42.0)
  }
