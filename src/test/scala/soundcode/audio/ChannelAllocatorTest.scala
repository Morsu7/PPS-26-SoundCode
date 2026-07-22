package soundcode.audio

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** Logica pura di allocazione dei canali MIDI: interamente headless-safe (nessun synth). */
class ChannelAllocatorTest extends AnyFunSuite with Matchers:

  test("la stessa voce riusa sempre lo stesso canale (polifonia gratis)") {
    val alloc = new ChannelAllocator[String](IndexedSeq(0, 1, 2, 3))
    alloc.channelFor("piano") shouldBe 0
    alloc.channelFor("piano") shouldBe 0
    alloc.channelFor("piano") shouldBe 0
  }

  test("voci diverse prendono canali diversi in ordine") {
    val alloc = new ChannelAllocator[String](IndexedSeq(0, 1, 2, 3))
    alloc.channelFor("piano") shouldBe 0
    alloc.channelFor("bass") shouldBe 1
    alloc.channelFor("lead") shouldBe 2
  }

  test("usa solo gli indici forniti (es. saltando il canale batteria 9)") {
    val melodic = (0 to 15).filter(_ != 9).toIndexedSeq
    val alloc = new ChannelAllocator[Int](melodic)
    val used = melodic.indices.map(alloc.channelFor)
    used should contain theSameElementsAs melodic
    used should not contain 9
  }

  test("in overflow riusa i canali dai piu' vecchi (voice stealing)") {
    val alloc = new ChannelAllocator[String](IndexedSeq(0, 1))
    alloc.channelFor("a") shouldBe 0
    alloc.channelFor("b") shouldBe 1
    alloc.channelFor("c") shouldBe 0 // ruba il canale 0 (voce piu' vecchia)
    alloc.channelFor("d") shouldBe 1
  }

  test("due voci che differiscono solo per il pan usano canali diversi (caso jux)") {
    val alloc = new ChannelAllocator[(Int, Int)](IndexedSeq(0, 1, 2, 3))
    val left = (0, 0) // stesso strumento, pan a sinistra
    val right = (0, 127) // stesso strumento, pan a destra
    alloc.channelFor(left) should not be alloc.channelFor(right)
    alloc.channelFor(left) shouldBe 0 // resta stabile
  }
