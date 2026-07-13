/*package soundcode.interpreter

import org.scalatest.funsuite.AnyFunSuite
import fastparse.*
import org.scalatest.Assertions.fail
import soundcode.parser.SoundCodeParser
import soundcode.domain.{AudioEffect, Sound, Track, TextPosition, RecursivePattern as AP}

def interpret(input: String): List[Track] = {
    val ast = new SoundCodeParser().parseProgram(input)
    ast match {
        case Right(programAST) => Interpreter.interpret(programAST)
        case Left(errorMsg) => fail(s"Parsing failed: $errorMsg")
    }
}

class InterpreterFunSuite extends AnyFunSuite {

    test("interpret a simple note block") {
        val streams = interpret("note(\"c# e3\")")
        assert(streams.length == 1)
        val stream = streams.head
        // Il base è un Pattern (List[List[Element]]), prendiamo il primo elemento della prima sequenza
        val note = stream.base.head.head.asInstanceOf[Sound.NoteInText]
        assert(note.note.value == "c#4") 
    }

    test("interpret a simple sound block") {
        val streams = interpret("sound(\"bd hh\")")
        assert(streams.length == 1)
        val sample = streams.head.base.head.head.asInstanceOf[Sound.SampleInText]
        assert(sample.sample.value == "bd")
    }

    test("interpret a stream with a note block and a sound extension") {
        // La base è note, l'estensione è sound
        val streams = interpret("note(\"c#\").sound(\"bd\").sound(\"hh\").note(\"e3\")")
        val stream = streams.head
        
        assert(stream.base.head.head.isInstanceOf[Sound.NoteInText])
        assert(stream.extensions.length == 1)
        assert(stream.extensions.head.head.head.isInstanceOf[Sound.SampleInText])
    }

    test("interpret multiple streams") {
        val streams = interpret("note(\"c# e3\")\nsound(\"bd hh\")")
        assert(streams.length == 2)
        assert(streams.head.base.head.head.isInstanceOf[Sound.NoteInText])
        assert(streams(1).base.head.head.isInstanceOf[Sound.SampleInText])
    }

    test("interpret a stream with transformation effects") {
        val streams = interpret("note(\"c#\").gain(\"5\").room(\"10\")")
        val stream = streams.head
        
        // base è il pattern dei note, extensions contiene i 2 effetti
        assert(stream.extensions.length == 2)
        // Estrai l'effetto dal pattern (assumendo che il pattern dell'effetto contenga l'effetto come unico elemento)
        val effect1 = stream.extensions.head.head.head.asInstanceOf[AudioEffect.Gain]
        val effect2 = stream.extensions(1).head.head.asInstanceOf[AudioEffect.Room]
        
        assert(effect1.value == 5.0)
        assert(effect2.value == 10.0)
    }

    test("interpret a stream with complex nested patterns") {
        val streams = interpret("note(\"c# [e3 g4]\")")
        val stream = streams.head
        val sequence = stream.base.head
        
        assert(sequence.length == 2)
        assert(sequence(1).isInstanceOf[AP.SubPattern])
        
        val sub = sequence(1).asInstanceOf[AP.SubPattern].pattern
        assert(sub.head.head.asInstanceOf[Sound.NoteInText].note.value == "e3")
    }
}*/