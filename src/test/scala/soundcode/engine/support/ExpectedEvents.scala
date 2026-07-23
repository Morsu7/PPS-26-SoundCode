package soundcode.engine.support

import soundcode.domain.Fraction

import scala.annotation.targetName

type TimePoint = Int | Fraction

extension (tp: TimePoint)
  def toFrac: Fraction = tp match
    case i: Int      => Fraction(i)
    case f: Fraction => f

case class ExpectedEvent(element: String, part: (Fraction, Fraction), whole: (Fraction, Fraction), extras: List[String] = Nil):
  override def toString: String =
    val exStr = if extras.isEmpty then "" else extras.mkString(" [", ", ", "]")
    s"'$element' @ (${part._1} -> ${part._2}) [whole: ${whole._1} -> ${whole._2}]$exStr"

object ExpectedEvent:
  private def toFrac(p: (TimePoint, TimePoint)): (Fraction, Fraction) =
    (p._1.toFrac, p._2.toFrac)

  def apply(element: String, part: (TimePoint, TimePoint), extras: String*): ExpectedEvent =
    val p = toFrac(part)
    new ExpectedEvent(element, p, p, extras.toList)

  def apply(element: String, part: (TimePoint, TimePoint), effects: List[String]): ExpectedEvent =
    val p = toFrac(part)
    new ExpectedEvent(element, p, p, effects)

  def apply(element: String, part: (TimePoint, TimePoint), whole: (TimePoint, TimePoint)): ExpectedEvent =
    new ExpectedEvent(element, toFrac(part), toFrac(whole), Nil)

  @targetName("applyWithTimePointWhole")
  def apply(element: String, part: (TimePoint, TimePoint), whole: (TimePoint, TimePoint), effects: List[String]): ExpectedEvent =
    new ExpectedEvent(element, toFrac(part), toFrac(whole), effects)
