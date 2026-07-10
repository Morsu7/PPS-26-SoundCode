package soundcode.audio

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GmDrumMapTest extends AnyFunSuite with Matchers {

  test("essential drums map to their General MIDI notes") {
    GmDrumMap.toGmNote("bd") shouldBe Some(36)
    GmDrumMap.toGmNote("sn") shouldBe Some(38)
    GmDrumMap.toGmNote("rim") shouldBe Some(37)
    GmDrumMap.toGmNote("cp") shouldBe Some(39)
    GmDrumMap.toGmNote("hh") shouldBe Some(42)
    GmDrumMap.toGmNote("oh") shouldBe Some(46)
    GmDrumMap.toGmNote("cr") shouldBe Some(49)
    GmDrumMap.toGmNote("rd") shouldBe Some(51)
    GmDrumMap.toGmNote("lt") shouldBe Some(45)
    GmDrumMap.toGmNote("mt") shouldBe Some(47)
    GmDrumMap.toGmNote("ht") shouldBe Some(50)
  }

  test("lookup is trimmed and case-insensitive") {
    GmDrumMap.toGmNote(" BD ") shouldBe Some(36)
  }

  test("unknown sample returns None") {
    GmDrumMap.toGmNote("piano") shouldBe None
    GmDrumMap.toGmNote("xyz") shouldBe None
  }
}
