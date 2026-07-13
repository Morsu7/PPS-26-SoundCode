package soundcode.domain

import scala.annotation.tailrec

// ==========================================
// 1. TIPI BASE E TEMPORALI
// ==========================================

case class Fraction(n: Long, d: Long) extends Ordered[Fraction]:
  require(d != 0, "Denominatore zero")
  private val g = gcd(n.abs, d.abs)
  val num: Long = if (d < 0) -n / g else n / g
  val den: Long = d.abs / g

  override def toString: String = s"$num/$den"
  override def equals(obj: Any): Boolean = obj match
    case that: Fraction => this.num == that.num && this.den == that.den
    case _ => false
  override def hashCode(): Int = (num, den).##
  @tailrec
  private def gcd(a: Long, b: Long): Long = if (b == 0) a else gcd(b, a % b)
  def +(that: Fraction): Fraction = Fraction(num * that.den + that.num * den, den * that.den)
  def -(that: Fraction): Fraction = Fraction(num * that.den - that.num * den, den * that.den)
  def *(that: Fraction): Fraction = Fraction(num * that.num, den * that.den)
  def /(that: Fraction): Fraction = Fraction(num * that.den, den * that.num)
  def toDouble: Double = num.toDouble / den.toDouble
  def compare(that: Fraction): Int = (this.num * that.den).compare(that.num * this.den)

object Fraction:
  def apply(n: Long): Fraction = Fraction(n, 1)
  def apply(n: Double): Fraction = Fraction(n.toLong, 1)
  def apply(n: Int): Fraction = Fraction(n.toLong, 1)

case class Interval(start: Fraction, end: Fraction):
  def duration: Fraction = end - start
  def intersect(other: Interval): Option[Interval] =
    val newStart = if (start > other.start) start else other.start
    val newEnd = if (end < other.end) end else other.end
    if (newStart < newEnd) Some(Interval(newStart, newEnd)) else None
  def map(f: Fraction => Fraction): Interval = Interval(f(start), f(end))

enum Accidental:
  case Sharp, Flat, Natural
extension (a: Accidental)
  def symbol: String = a match
    case Accidental.Sharp   => "#"
    case Accidental.Flat    => "b"
    case Accidental.Natural => ""

opaque type Note = NoteData
private case class NoteData(name: String, accidental: Accidental, octave: Int) {
  override def toString: String = s"${name}${accidental.symbol}${octave}"
}
object Note:
  def apply(name: String): Note = NoteData(name.toUpperCase(), Accidental.Natural, 4)
  def apply(name: String, accidental: Accidental): Note = NoteData(name.toUpperCase(), accidental, 4)
  def apply(name: String, octave: Int): Note = NoteData(name.toUpperCase(), Accidental.Natural, octave)
  def apply(name: String, accidental: Accidental, octave: Int): Note = NoteData(name.toUpperCase(), accidental, octave)
  def unapply(n: Note): Some[(String, String, Int)] = Some((n.name.toUpperCase(), n.accidental.symbol, n.octave))

  extension (n: Note)
    def name: String = n.name
    def accidental: String = n.accidental.symbol
    def octave: Int = n.octave

opaque type Sample = String
object Sample:
  def apply(value: String): Sample = value
  def unapply(s: Sample): Some[String] = Some(s)
  extension (s: Sample) def value: String = s


// ==========================================
// 2. Syntax Element
// ==========================================

case class TextPosition(startIndex: Int, endIndex: Int)

sealed trait AudioPayload

enum Sound extends AudioPayload:
  case NoteInText(note: Note, position: TextPosition)
  case SampleInText(sample: Sample, position: TextPosition)
  case Rest(position: TextPosition)

enum AudioEffect extends AudioPayload:
  case Gain(value: Double)
  case Pan(value: Double)
  case Room(value: Double)
  case LowPass(value: Double)
  case HighPass(value: Double)
  case Delay(volume: Double, time: Double, feedback: Double)

enum PatternModifier[+T]:
  case Reverse
  case FastForward(factor: Pattern[Double])
  case SlowMotion(factor: Pattern[Double])
  case Late(offset: Pattern[Double])
  case Early(offset: Pattern[Double])
  case Repetition(times: Pattern[Double])
  case Juxtaposition(modifiers: List[PatternModifier[T]])
  case Offset(offset: Double, modifiers: List[PatternModifier[T]])


sealed trait Pattern[+T]
object Pattern:
  case class Atom[T](value: T) extends Pattern[T]
  case class Sequence[T](elements: List[Pattern[T]]) extends Pattern[T]
  case class Parallel[T](layers: List[Pattern[T]]) extends Pattern[T]
  case class Alternation[T](elements: List[Pattern[T]]) extends Pattern[T]
  case class TimeWarp[T](modifier: PatternModifier[T], pattern: Pattern[T]) extends Pattern[T]
  case class WithExtensions(base: Pattern[AudioPayload], extensions: List[Pattern[AudioPayload]]) extends Pattern[AudioPayload]

// ==========================================
// 4. ESECUZIONE E SCHEDULING
// ==========================================

case class ScheduledEvent[+T](whole: Interval, part: Interval, value: T, appliedExtensions: List[AudioPayload] = Nil):
  def mapTime(f: Fraction => Fraction): ScheduledEvent[T] =
    this.copy(whole = whole.map(f), part = part.map(f))
  def clipTo(window: Interval): Option[ScheduledEvent[T]] =
    this.part.intersect(window).map(validPart => this.copy(part = validPart))

case class Tempo(cps: Double) {
  val cycleDurationMs: Double = 1000.0 / cps

  def durationMs(start: Fraction, end: Fraction): Long =
    Math.round((end.toDouble - start.toDouble) * cycleDurationMs)

  def offsetMs(phase: Fraction): Long =
    (phase.toDouble * cycleDurationMs).toLong
}


opaque type AbsoluteTime = Long
object AbsoluteTime:
  def apply(value: Long): AbsoluteTime = value
  extension (t: AbsoluteTime)
    def toLong: Long = t
    def +(other: Long): AbsoluteTime = AbsoluteTime(t + other)
    def -(other: Long): AbsoluteTime = AbsoluteTime(t - other)
    def <(other: AbsoluteTime): Boolean = t < other
    def <=(other: AbsoluteTime): Boolean = t <= other