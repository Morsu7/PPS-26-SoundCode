package soundcode.interpreter

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.Assertions.fail
import org.scalatest.matchers.should.Matchers._
import org.scalatest.Inside._

import fastparse.*

import soundcode.parser.SoundCodeParser

import soundcode.domain.*
import soundcode.domain.Sound.*
import soundcode.domain.Pattern.*

def interpret(input: String): List[Pattern[AudioPayload]] = {
    val ast = new SoundCodeParser().parseProgram(input)
    ast match {
        case Right(programAST) => Interpreter.interpret(programAST)
        case Left(errorMsg) => fail(s"Parsing failed: $errorMsg")
    }
}

class InterpreterFunSuite extends AnyFunSuite {

    test("interpret a simple note block") {
        val streams = interpret("note(\"c# e3\").note(\"g4\")")
        
        streams should matchPattern {
            case List(Pattern.Sequence(List(Pattern.Atom(NoteInText(Note("C", "#", 4), _)), _))) =>
        }
    }

    test("interpret a simple sound block") {
        val streams = interpret("sound(\"bd hh\").note(\"g4\")")
        
        streams.head should matchPattern {
            case Pattern.WithExtensions(Pattern.Sequence(_), _) =>
        }

        inside(streams.head) {
            case Pattern.WithExtensions(Pattern.Sequence(elements), extensions) =>
                // Verifica base
                elements.head match {
                    case Pattern.Atom(SampleInText(Sample("bd"), _)) => // OK
                    case other => fail(s"Expected bd sample, found $other")
                }
                
                extensions should have size 1
                extensions.head match {
                    case Pattern.Atom(note: NoteInText) => note.note.toString() shouldBe "G4"
                    case _ => fail("Expected NoteInText")
                }
        }
    }

    test("interpret a stream with transformation effects") {
        val streams = interpret("note(\"c#\").gain(\"5\").room(\"10\")")
        //println(streams)
        
        inside(streams.head) {
            case Pattern.WithExtensions(_, extensions) =>
                extensions should have size 2
                
                // Verifica effetti in modo leggibile e senza cast
                extensions(0) should matchPattern { case Pattern.Atom(AudioEffect.Gain(5.0)) => }
                extensions(1) should matchPattern { case Pattern.Atom(AudioEffect.Room(10.0)) => }
        }
    }

    test("interpret a stream with complex nested patterns") {
        val streams = interpret("note(\"c# [e3 g4]\")")
        //println(streams.head)
        inside(streams.head) {
            case Pattern.Sequence(elements) =>
                elements should have size 2
                
                // Navighiamo nella struttura annidata in modo sicuro
                inside(elements(1)) {
                    case Pattern.Sequence(inner) =>
                        inner.head match {
                            case Pattern.Atom(n: NoteInText) => n.note.toString() shouldBe "E3"
                            case other => fail(s"Expected NoteInText, found $other")
                        }
                    case other => fail(s"Expected nested sequence, found $other")
                }
        }
    }

    test("interpret a stream with a transformation") {
        val streams = interpret("note(\"c#\").rev()")
        
        inside(streams.head) {
            case Pattern.TimeWarp(modifier, pattern) =>
                modifier shouldBe PatternModifier.Reverse
                pattern should matchPattern { case Pattern.Atom(NoteInText(Note("C", "#", 4), _)) => }
        }
    }

    test("interpret complex chain: note + gain + sound + rev") {
        val streams = interpret("note(\"c#\").gain(\"5\").sound(\"bd\").rev()")
        
        inside(streams.head) {
            case Pattern.TimeWarp(modifier, pattern) =>
                modifier shouldBe PatternModifier.Reverse
                
                inside(pattern) {
                    case Pattern.WithExtensions(_, extensions) =>
                        extensions should have size 2
                        
                        // Verifica il Gain
                        extensions(0) should matchPattern { case Pattern.Atom(AudioEffect.Gain(5.0)) => }
                        
                        // Verifica il Sound (Sample)
                        extensions(1) should matchPattern { case Pattern.Atom(s: SampleInText) if s.sample.value == "bd" => }
                        
                    case other => fail(s"Expected WithExtensions, but found $other")
                }
                
            case other => fail(s"Expected TimeWarp, but found $other")
        }
    }

    test("interpret a stream with silence") {
        val streams = interpret("note(\"c# ~ ~\").sound(\"piano ~\")")
        
        inside(streams.head) {
            case Pattern.WithExtensions(base, extensions) =>
                println(s"Base: $base")
                base should matchPattern { case Pattern.Sequence(List(_, Pattern.Atom(Rest(_)), _)) => }
                extensions(0) should matchPattern { case Pattern.Sequence(List(_, Pattern.Atom(Rest(_)))) => }
                
            case other => fail(s"Expected WithExtensions, but found $other")
        }
    }
}