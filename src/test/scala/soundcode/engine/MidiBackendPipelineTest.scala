package soundcode.engine

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import soundcode.domain.*
import soundcode.audio.{AudioEngine, NoteSpec}
import soundcode.parser.SoundCodeParser
import soundcode.interpreter.Interpreter

/** Test END-TO-END sulle note: parte dal testo DSL, passa da parser + interprete + scheduler
  * e verifica cosa arriva davvero al [[MidiBackend]] (numero MIDI e strumento).
  *
  * A differenza dei test dello scheduler (che controllano solo la lettera della nota),
  * qui verifichiamo la ricostruzione completa nome+accidentale+ottava e la selezione strumento.
  */
class MidiBackendPipelineTest extends AnyFunSuite with Matchers:

  private class RecordingEngine extends AudioEngine:
    var notes: List[NoteSpec] = Nil
    var drums: List[Int] = Nil
    def playNote(spec: NoteSpec): Unit = notes = notes :+ spec
    def playDrum(gmNote: Int, velocity: Int, durationMs: Long): Unit = drums = drums :+ gmNote
    def close(): Unit = ()

  /** Interpreta il codice e restituisce gli eventi del primo ciclo, in ordine temporale. */
  private def firstCycle(code: String): List[ScheduledEvent[AudioPayload]] =
    val ast = new SoundCodeParser().parseProgram(code).toOption.getOrElse(fail(s"parse fallito: $code"))
    val patterns = Interpreter.interpret(ast)
    SchedulerImpl.generateInfiniteTimeline(patterns)
      .dropWhile(_.part.start < Fraction(0))
      .takeWhile(_.part.start < Fraction(1))
      .toList

  private def play(code: String): RecordingEngine =
    val engine = new RecordingEngine
    val backend = new MidiBackend(engine)
    firstCycle(code).foreach(e => backend.triggerSound(e.value, 100L, e.appliedExtensions))
    engine

  test("note con diesis e bemolle danno i numeri MIDI corretti (pipeline completa)") {
    play("note(\"c#4 eb3 g4\")").notes.map(_.midiNote) shouldBe List(61, 51, 67)
  }

  test("nota senza ottava usa l'ottava di default (documenta il comportamento del parser)") {
    play("note(\"a\")").notes.map(_.midiNote) shouldBe List(69) // atteso a4
  }

  test("una sequenza di note conserva l'ordine temporale") {
    play("note(\"c4 e4 g4\")").notes.map(_.midiNote) shouldBe List(60, 64, 67)
  }

  test("note(...).sound(\"bass\") applica lo strumento a tutte le note") {
    val r = play("note(\"c4 e4 g4\").sound(\"bass\")")
    r.notes.map(_.midiNote) shouldBe List(60, 64, 67)
    r.notes.map(_.program) shouldBe List(33, 33, 33) // bass = programma GM 33
  }

  test("sound(...) di sola batteria finisce sul canale percussioni") {
    val r = play("sound(\"bd sn hh\")")
    r.drums shouldBe List(36, 38, 42)
    r.notes shouldBe empty
  }

  test("note(...).jux(rev) manda meta' voci a sinistra (pan 0) e meta' a destra (pan 127) (story #3)") {
    val r = play("note(\"c4 e4\").jux(rev())")
    r.notes.map(_.pan).count(_.contains(0)) shouldBe 2
    r.notes.map(_.pan).count(_.contains(127)) shouldBe 2
  }

  test("note(...).room(...).lpf(...) rende reverb e brightness (pipeline completa, story #3)") {
    val r = play("note(\"c4\").room(\"0.5\").lpf(\"1000\")")
    r.notes.head.reverb shouldBe defined
    r.notes.head.brightness shouldBe defined
  }
