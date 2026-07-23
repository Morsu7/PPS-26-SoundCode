package soundcode.engine.support

import soundcode.domain.*


extension (n: Int)  def \(d: Int): Fraction = Fraction(n.toLong, d.toLong)
extension (n: Long) def \(d: Long): Fraction = Fraction(n, d)

type PatternParameter = Double | Int | Fraction | Pattern[Double]

extension (value: PatternParameter)
  def toPattern: Pattern[Double] = value match
    case d: Double          => Pattern.Atom(ConfigInText(d))
    case i: Int             => Pattern.Atom(ConfigInText(i.toDouble))
    case f: Fraction        => Pattern.Atom(ConfigInText(f.toDouble))
    //case p: Pattern[Double] @unchecked => p

given Conversion[Double, Pattern[Double]] = num(_)
given Conversion[Int, Pattern[Double]] = i => num(i.toDouble)
given Conversion[Fraction, Pattern[Double]] = f => num(f.toDouble)

private val pos = Some(TextPosition(0, 0))
private def sample(s: String) = Pattern.Atom(Sound.SampleInText(Sample(s), pos))
private def note(n: String) = Pattern.Atom(Sound.NoteInText(Note(n), pos))

def bd = sample("bd"); def hh = sample("hh"); def sn = sample("sn"); def cp = sample("cp")
def rim = sample("rim"); def clap = sample("clap")

def c4 = note("c"); def f4 = note("f"); def g4 = note("g"); def e3 = note("e")
def b6 = note("b"); def a5 = note("a"); def e4 = note("e"); def b4 = note("b")
def cSharp4 = note("c#")

def gain(v: Double) = Pattern.Atom(AudioEffect.Gain(ConfigInText(v)))
def room(v: Double) = Pattern.Atom(AudioEffect.Room(ConfigInText(v)))
def pan(v: Double)  = Pattern.Atom(AudioEffect.Pan(ConfigInText(v)))
def num(v: Double)  = Pattern.Atom(v)

def seq[T](p: Pattern[T]*): Pattern[T] = Pattern.Sequence(p.toList)
def par[T](p: Pattern[T]*): Pattern[T] = Pattern.Parallel(p.toList)
def alt[T](p: Pattern[T]*): Pattern[T] = Pattern.Alternation(p.toList)
def sound[T](p: Pattern[T]*): Pattern[T] = seq(p*)
def note[T](p: Pattern[T]*): Pattern[T] = seq(p*)

val rev: PatternModifier[Nothing] = PatternModifier.Reverse
def fast(factor: PatternParameter) = PatternModifier.FastForward(factor.toPattern)
def slow(factor: PatternParameter) = PatternModifier.SlowMotion(factor.toPattern)
def late(offset: PatternParameter) = PatternModifier.Late(offset.toPattern)
def early(offset: PatternParameter) = PatternModifier.Early(offset.toPattern)

extension [T](basePattern: Pattern[T])
  private def applyTimeModifier(modifier: PatternModifier[T]) = Pattern.TimeWarp(modifier, basePattern)

  def fast(factor: PatternParameter): Pattern[T]  = applyTimeModifier(PatternModifier.FastForward(factor.toPattern))
  def slow(factor: PatternParameter): Pattern[T]  = applyTimeModifier(PatternModifier.SlowMotion(factor.toPattern))
  def late(offset: PatternParameter): Pattern[T]  = applyTimeModifier(PatternModifier.Late(offset.toPattern))
  def early(offset: PatternParameter): Pattern[T] = applyTimeModifier(PatternModifier.Early(offset.toPattern))
  def ply(times: PatternParameter): Pattern[T]    = applyTimeModifier(PatternModifier.Repetition(times.toPattern))
  def reverse: Pattern[T]                         = applyTimeModifier(PatternModifier.Reverse)

  def jux(modifiers: (PatternModifier[T] | Pattern[AudioEffect])*): Pattern[T] =
    applyTimeModifier(PatternModifier.Juxtaposition(modifiers.toList))

  def off(offset: PatternParameter, modifiers: (PatternModifier[T] | Pattern[AudioEffect])*): Pattern[T] =
    applyTimeModifier(PatternModifier.Offset(offset.toPattern, modifiers.toList))

// 6. Applicazione esplicita delle estensioni audio coerente con l'AST
def ext(basePattern: Pattern[AudioPayload], extensions: Pattern[AudioPayload]*): Pattern[AudioPayload] =
  Pattern.WithExtensions(basePattern, extensions.toList)

extension (basePattern: Pattern[AudioPayload])
  def withExtensions(extensions: Pattern[AudioPayload]*): Pattern[AudioPayload] =
    ext(basePattern, extensions*)