package soundcode.engine

import soundcode.domain.{AudioEffect, AudioPayload, Fraction, Note, Pattern, PatternModifier, Sample, Sound, TextPosition}


extension (n: Int)
  def \(d: Int): Fraction = Fraction(n.toLong, d.toLong)

extension (n: Long)
  def \(d: Long): Fraction = Fraction(n, d)

val dummyPos = TextPosition(0, 0)
// Suoni Base
def bd = Pattern.Atom(Sound.SampleInText(Sample("bd"), dummyPos))
def hh = Pattern.Atom(Sound.SampleInText(Sample("hh"), dummyPos))
def sn = Pattern.Atom(Sound.SampleInText(Sample("sn"), dummyPos))
def cp = Pattern.Atom(Sound.SampleInText(Sample("cp"), dummyPos))
def rim = Pattern.Atom(Sound.SampleInText(Sample("rim"), dummyPos))
def clap = Pattern.Atom(Sound.SampleInText(Sample("clap"), dummyPos))

// Note
def c4 = Pattern.Atom(Sound.NoteInText(Note("c4"), dummyPos))
def f4 = Pattern.Atom(Sound.NoteInText(Note("f4"), dummyPos))
def g4 = Pattern.Atom(Sound.NoteInText(Note("g4"), dummyPos))
def cSharp4 = Pattern.Atom(Sound.NoteInText(Note("c#4"), dummyPos))

// Effetti
def gain(v: Double) = Pattern.Atom(AudioEffect.Gain(v))
def room(v: Double) = Pattern.Atom(AudioEffect.Room(v))

// Combinatori
def seq[T](p: Pattern[T]*): Pattern[T] = Pattern.Sequence(p.toList)
def par[T](p: Pattern[T]*): Pattern[T] = Pattern.Parallel(p.toList)
def alt[T](p: Pattern[T]*): Pattern[T] = Pattern.Alternation(p.toList)
def ext(base: Pattern[AudioPayload], exts: Pattern[AudioPayload]*): Pattern[AudioPayload] = Pattern.WithExtensions(base, exts.toList)
def num(v: Double) = Pattern.Atom(v)

def fast[T](factor: Double, p: Pattern[T]): Pattern[T] = Pattern.TimeWarp(PatternModifier.FastForward(Pattern.Atom(factor)), p)
def slow[T](factor: Double, p: Pattern[T]): Pattern[T] = Pattern.TimeWarp(PatternModifier.SlowMotion(Pattern.Atom(factor)), p)

def fast[T](factor: Pattern[Double], p: Pattern[T]): Pattern[T] = Pattern.TimeWarp(PatternModifier.FastForward(factor), p)
def slow[T](factor: Pattern[Double], p: Pattern[T]): Pattern[T] = Pattern.TimeWarp(PatternModifier.SlowMotion(factor), p)


def late[T](offset: Pattern[Double], pattern: Pattern[T]): Pattern[T] = Pattern.TimeWarp(PatternModifier.Late(offset), pattern)
def late[T](offset: Double, pattern: Pattern[T]): Pattern[T] = Pattern.TimeWarp(PatternModifier.Late(Pattern.Atom(offset)), pattern)
def early[T](offset: Pattern[Double], pattern: Pattern[T]): Pattern[T] = Pattern.TimeWarp(PatternModifier.Early(offset), pattern)
def early[T](offset: Double, pattern: Pattern[T]): Pattern[T] = Pattern.TimeWarp(PatternModifier.Early(Pattern.Atom(offset)), pattern)