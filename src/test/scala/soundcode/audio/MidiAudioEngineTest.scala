package soundcode.audio

import org.scalatest.funsuite.AnyFunSuite

/** Test del backend MIDI reale. Richiede un sintetizzatore disponibile: in ambienti
  * headless / senza soundbank il test viene *cancellato* (non fallito) tramite `assume`.
  */
class MidiAudioEngineTest extends AnyFunSuite {

  test("real MIDI engine plays note and drum without throwing") {
    val maybeEngine = MidiAudioEngine.create()
    assume(maybeEngine.isDefined, "Nessun sintetizzatore MIDI disponibile in questo ambiente")

    val engine = maybeEngine.get
    try {
      engine.playNote(60, 96, 50)
      engine.playDrum(36, 96, 50)
    } finally {
      engine.close()
      engine.close() // deve essere idempotente
    }
  }
}
