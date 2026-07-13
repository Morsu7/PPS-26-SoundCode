package soundcode.parser

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterAll
import scala.collection.mutable.ListBuffer
import fastparse._
import soundcode.parser.AST._
import soundcode.parser.AST.Transformations._

class SoundCodeParserSuite extends AnyFunSuite with BeforeAndAfterAll {

    private def DEBUG = false
    private val testReports = ListBuffer[String]()

    override def afterAll(): Unit = {
        if (DEBUG) {
            val finalReport = new StringBuilder()
            finalReport.append("\n" + "=" * 50 + "\n")
            finalReport.append("          REPORT COMPLETO DEL PARSER\n")
            finalReport.append("=" * 50 + "\n")
            
            testReports.foreach(report => finalReport.append(report).append("\n"))
            finalReport.append("=" * 50 + "\n")

            alert(finalReport.toString()) 
        }
    }

    private def parse(input: String) = {
        val res = new SoundCodeParser().parseProgram(input)
        if (DEBUG) {
            val outputPart = res match {
                case Parsed.Success(ast, _) => s"OUTPUT:\n$ast\n"
                case f: Parsed.Failure      => s"OUTPUT: Failure -> ${f.msg}\n"
            }
            
            val report = s"\nINPUT: $input\n$outputPart"
            testReports.append(report)
        }
        res
    }

    test("single sound block") {
        parse("sound(\"bd\")") match {
        case Parsed.Success(ast, _) =>
            val stream = ast.blocks.head.asInstanceOf[StreamBlock]
            val block = stream.base.asInstanceOf[SoundBlock]
            
            val expected = Pattern(List(
            Sequence(List(
                AtomElement(Sample("bd"))
            ))
            ))
            assert(block.pattern == expected)

        case f: Parsed.Failure =>
            fail(s"Parsing failed: ${f.msg}")
        }
    }

    test("single note block") {
        parse("note(\"c4\")") match {
        case Parsed.Success(ast, _) =>
            val stream = ast.blocks.head.asInstanceOf[StreamBlock]
            val block = stream.base.asInstanceOf[NoteBlock]

            val expected = Pattern(List(
            Sequence(List(
                AtomElement(Note("c", None, 4))
            ))
            ))
            assert(block.pattern == expected)

        case f: Parsed.Failure =>
            fail(s"Parsing failed: ${f.msg}")
        }
    }

    test("sequence in sound block") {
        parse("sound(\"[bd hh sd]\")") match {
        case Parsed.Success(ast, _) =>
            val stream = ast.blocks.head.asInstanceOf[StreamBlock]
            val block = stream.base.asInstanceOf[SoundBlock]

            val expected = Pattern(List(
            Sequence(List(
                SubPatternElement(Pattern(List(
                Sequence(List(
                    AtomElement(Sample("bd")),
                    AtomElement(Sample("hh")),
                    AtomElement(Sample("sd"))
                ))
                )))
            ))
            ))
            assert(block.pattern == expected)

        case f: Parsed.Failure =>
            fail(s"Parsing failed: ${f.msg}")
        }
    }

    test("alternation in sound block") {
        parse("sound(\"bd <hh oh>\")") match {
        case Parsed.Success(ast, _) =>
            val stream = ast.blocks.head.asInstanceOf[StreamBlock]
            val block = stream.base.asInstanceOf[SoundBlock]

            val expected = Pattern(List(
            Sequence(List(
                AtomElement(Sample("bd")),
                AlternationElement(Pattern(List(
                Sequence(List(
                    AtomElement(Sample("hh")),
                    AtomElement(Sample("oh"))
                ))
                )))
            ))
            ))
            assert(block.pattern == expected)

        case f: Parsed.Failure => 
            fail(s"Parsing failed: ${f.msg}")
        }
    }

    test("parallel in sound block") {
        parse("sound(\"bd,hh,sd\")") match {
        case Parsed.Success(ast, _) =>
            val stream = ast.blocks.head.asInstanceOf[StreamBlock]
            val block = stream.base.asInstanceOf[SoundBlock]
            val expected = Pattern(List(
            Sequence(List(AtomElement(Sample("bd")))),
            Sequence(List(AtomElement(Sample("hh")))),
            Sequence(List(AtomElement(Sample("sd"))))
            ))
            assert(block.pattern == expected)
        case f: Parsed.Failure => fail(s"Parsing failed: ${f.msg}")
        }
    }

    test("note + sound attachment") {
        parse("note(\"c4\").sound(\"bd\")") match {
        case Parsed.Success(ast, _) =>
            val stream = ast.blocks.head.asInstanceOf[StreamBlock]
            val noteBlock = stream.base.asInstanceOf[NoteBlock]

            assert(stream.extensions.size == 1)
            val attachedSound = stream.extensions.head.asInstanceOf[GenerativeExtensionBlock].block.asInstanceOf[SoundBlock]
            
            val expectedSoundPattern = Pattern(List(
            Sequence(List(AtomElement(Sample("bd"))))
            ))
            assert(attachedSound.pattern == expectedSoundPattern)

        case f: Parsed.Failure =>
            fail(s"Parsing failed: ${f.msg}")
        }
    }

    test("multiple blocks") {
        parse("sound(\"bd\")\nnote(\"c4\")\nsound(\"hh\")") match {
        case Parsed.Success(ast, _) =>
            assert(ast.blocks.length == 3)
            assert(ast.blocks(0).asInstanceOf[StreamBlock].base.isInstanceOf[SoundBlock])
            assert(ast.blocks(1).asInstanceOf[StreamBlock].base.isInstanceOf[NoteBlock])
            assert(ast.blocks(2).asInstanceOf[StreamBlock].base.isInstanceOf[SoundBlock])

        case f: Parsed.Failure =>
            fail(s"Parsing failed: ${f.msg}")
        }
    }

    test("complex mixed pattern") {
        parse("sound(\"bd hh, [bd <hh oh>]\")") match {
        case Parsed.Success(ast, _) =>
            val stream = ast.blocks.head.asInstanceOf[StreamBlock]
            val block = stream.base.asInstanceOf[SoundBlock]

            val expected = Pattern(List(
                Sequence(List(
                AtomElement(Sample("bd")),
                AtomElement(Sample("hh"))
                )),
                Sequence(List(
                SubPatternElement(Pattern(List(
                    Sequence(List(
                    AtomElement(Sample("bd")),
                    AlternationElement(Pattern(List(
                        Sequence(List(
                        AtomElement(Sample("hh")),
                        AtomElement(Sample("oh"))
                        ))
                    )))
                    ))
                )))
                ))
            ))
            assert(block.pattern == expected)
        case f: Parsed.Failure => fail(s"Parsing failed: ${f.msg}")
        }
    }

    test("single stream with no extensions") {
        val input = "sound(\"bd\")"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Success[?]])
        val program = result.get.value
        
        val stream = program.blocks.head.asInstanceOf[StreamBlock]
        assert(stream.base.isInstanceOf[SoundBlock])
        assert(stream.extensions.isEmpty == true)
    }

    test("mixed chain: generative + transformation") {
        val input = "note(\"c4\").sound(\"bd\").gain(\"0.8\")"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Success[?]])
        val program = result.get.value
        val stream = program.blocks.head.asInstanceOf[StreamBlock]
        
        assert(stream.base.isInstanceOf[NoteBlock])
        
        assert(stream.extensions.size == 2)
        
        val ext1 = stream.extensions(0).asInstanceOf[GenerativeExtensionBlock]
        assert(ext1.block.isInstanceOf[SoundBlock])
        
        val ext2 = stream.extensions(1).asInstanceOf[TransformationExtensionBlock]
        assert(ext2.block.isInstanceOf[Gain])
    }

    test("multiple chains on multiple lines") {
        val input = "sound(\"hh\").fast(\"2\")\nnote(\"e3\").pan(\"0.1\")"
            
        val result = parse(input)
        assert(result.isInstanceOf[fastparse.Parsed.Success[?]])
        val program = result.get.value
        
        assert(program.blocks.size == 2)
    }

    test("parsing failure for invalid input") {
        val input = ".gain(\"0.8\").sound(\"bd\")"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Failure])
    }

    test("unknown transformation block") {
        val input = "sound(\"bd\").customTransform(\"1.5\")"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Success[?]])
        val program = result.get.value
        val stream = program.blocks.head.asInstanceOf[StreamBlock]
        
        assert(stream.base.isInstanceOf[SoundBlock])
        assert(stream.extensions.size == 1)
        
        val ext = stream.extensions.head.asInstanceOf[TransformationExtensionBlock]
        assert(ext.block.isInstanceOf[Unknown])
        
        val unknownBlock = ext.block.asInstanceOf[Unknown]
        assert(unknownBlock.name == "customTransform")
        assert(unknownBlock.pattern.elems.head.elems.head.asInstanceOf[AtomElement[Config]].atom.value == 1.5)
    }

    test("using complex patterns in transformations") {
        val input = "sound(\"bd\").gain(\"[0.5 0.8]\")"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Success[?]])
        val program = result.get.value
        val stream = program.blocks.head.asInstanceOf[StreamBlock]
        
        assert(stream.base.isInstanceOf[SoundBlock])
        assert(stream.extensions.size == 1)
        
        val ext = stream.extensions.head.asInstanceOf[TransformationExtensionBlock]
        assert(ext.block.isInstanceOf[Gain])
        
        val gainBlock = ext.block.asInstanceOf[Gain]
        val pattern = gainBlock.pattern
        
        assert(pattern.elems.size == 1) // A single sequence in the pattern
        val mainSequence = pattern.elems.head
        
        // the sequence should contain a SubPatternElement with two Config elements inside
        assert(mainSequence.elems.size == 1) 
        

        val subPatternEl = mainSequence.elems.head.asInstanceOf[SubPatternElement[Config]]
        val innerPattern = subPatternEl.pattern
        
        assert(innerPattern.elems.size == 1) // a single sequence inside the sub-pattern
        val innerSequence = innerPattern.elems.head
        
        // the sequence should contain two Config elements
        assert(innerSequence.elems.size == 2) 
    }

    test("all transformations chain verification") {
        // giant chain with all transformations
        val input = "sound(\"bd\").rev().gain(\"0.8\").pan(\"0.5\").room(\"0.2\").delay(\"0.1\").lpf(\"500\").hpf(\"1000\").fast(\"2\").slow(\"0.5\").early(\"0.1\").late(\"0.2\").ply(\"4\")"
        
        val result = parse(input)
        assert(result.isInstanceOf[fastparse.Parsed.Success[?]])
        
        val program = result.get.value
        val stream = program.blocks.head.asInstanceOf[StreamBlock]
        
        assert(stream.base.isInstanceOf[SoundBlock])
        
        val extensions = stream.extensions.map(_.asInstanceOf[TransformationExtensionBlock].block)
        assert(extensions.size == 12)
        assert(extensions(0).isInstanceOf[Reverse])
        assert(extensions(1).isInstanceOf[Gain])
        assert(extensions(2).isInstanceOf[Pan])
        assert(extensions(3).isInstanceOf[Room])
        assert(extensions(4).isInstanceOf[Delay])
        assert(extensions(5).isInstanceOf[LowPassFilter])
        assert(extensions(6).isInstanceOf[HighPassFilter])
        assert(extensions(7).isInstanceOf[FastForward])
        assert(extensions(8).isInstanceOf[SlowMotion])
        assert(extensions(9).isInstanceOf[Early])
        assert(extensions(10).isInstanceOf[Late])
        assert(extensions(11).isInstanceOf[Repetition])
    }

    test("parsing offset with multiple transformations separated by spaces") {
        val input = "sound(\"bd\").off(\"0.25\", rev() gain(\"0.8\") lpf(\"500\"))"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Success[?]])
        val program = result.get.value
        val stream = program.blocks.head.asInstanceOf[StreamBlock]
        
        val offBlock = stream.extensions.head.asInstanceOf[TransformationExtensionBlock].block.asInstanceOf[Offset]

        assert(offBlock.offset.elems.size == 1)
        
        assert(offBlock.transformations.size == 3)
        assert(offBlock.transformations(0).isInstanceOf[Reverse])
        assert(offBlock.transformations(1).isInstanceOf[Gain])
        assert(offBlock.transformations(2).isInstanceOf[LowPassFilter])
    }

    test("parsing offset with mini-notation and spaces") {
        val input = "sound(\"bd\").off(\"[0.25 0.5]\" ,  rev())"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Success[?]])
        val program = result.get.value
        val stream = program.blocks.head.asInstanceOf[StreamBlock]
        
        val offBlock = stream.extensions.head.asInstanceOf[TransformationExtensionBlock].block.asInstanceOf[Offset]
        
        assert(offBlock.offset.elems.head.elems.head.isInstanceOf[SubPatternElement[?]])
        assert(offBlock.transformations.size == 1)
        assert(offBlock.transformations.head.isInstanceOf[Reverse])
    }

    test("parsing juxtaposition with multiple transformations separated by commas") {
        val input = "sound(\"hh\").jux(rev() ,gain(\"0.5\"),  pan(\"0.2\"))"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Success[?]])
        val program = result.get.value
        val stream = program.blocks.head.asInstanceOf[StreamBlock]
        
        val juxBlock = stream.extensions.head.asInstanceOf[TransformationExtensionBlock].block.asInstanceOf[Juxtaposition]
        
        assert(juxBlock.transformations.size == 3)
        assert(juxBlock.transformations(0).isInstanceOf[Reverse])
        assert(juxBlock.transformations(1).isInstanceOf[Gain])
        assert(juxBlock.transformations(2).isInstanceOf[Pan])
    }

    test("parsing failure for offset missing comma separator") {
        val input = "sound(\"bd\").off(\"0.25\" rev())"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Failure])
    }

    test("parsing failure for offset missing transformations") {
        val input = "sound(\"bd\").off(\"0.25\", )"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Failure])
    }

    test("parsing failure for juxtaposition separated by spaces instead of commas") {
        val input = "sound(\"hh\").jux(rev() gain(\"0.5\"))"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Failure])
    }

    test("parsing failure for empty juxtaposition") {
        val input = "sound(\"hh\").jux()"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Failure])
    }

    test("parsing with whitespace tolerance") {
        val input = "sound(  \" bd     \").gain(\"0.8    \")\n\nnote(\"    c4\" ).pan( \"0.5\"  )"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Success[?]])
        val program = result.get.value
        assert(program.blocks.size == 2)
    }

    test("usage of speed controlled items") {
        val input = "sound(\"bd*2\")\nnote(\"c4/0.5\")\nsound(\"[hh bd, <bd bd>*2]\").note(\"a a*2 b/2\")"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Success[?]])
        val program = result.get.value
        
        assert(program.blocks.size == 3)

        // row 1: sound("bd*2")
        val stream1 = program.blocks(0).asInstanceOf[StreamBlock]
        val soundBlock = stream1.base.asInstanceOf[SoundBlock]
        val soundPattern = soundBlock.pattern
        val soundSequence = soundPattern.elems.head
        val soundElement = soundSequence.elems.head.asInstanceOf[SpeedModifiedElement[Sample]]
        
        assert(soundElement.isMulFactor == true)
        assert(soundElement.factor.value == 2.0)

        // row 2: note("c4/0.5")
        val stream2 = program.blocks(1).asInstanceOf[StreamBlock]
        val noteBlock = stream2.base.asInstanceOf[NoteBlock]
        val notePattern = noteBlock.pattern
        val noteSequence = notePattern.elems.head
        val noteElement = noteSequence.elems.head.asInstanceOf[SpeedModifiedElement[Note]]
        
        assert(noteElement.isMulFactor == false)
        assert(noteElement.factor.value == 0.5)

        // row 3: sound("[hh bd, <bd bd>*2]").note("a a*2 b/2")
        // Questa riga contiene una catena (.note). Verifichiamo che l'estensione contenga i modificatori attesi
        val stream3 = program.blocks(2).asInstanceOf[StreamBlock]
        
        assert(stream3.extensions.nonEmpty)
    }

    test("full usage of note naming with accidentals and octaves") {
        val input = "note(\"C4 d#5 Eb3 F6 g2 a#0 Bb9\")"
        val result = parse(input)
        
        assert(result.isInstanceOf[fastparse.Parsed.Success[?]])
        val program = result.get.value

        val expectedNotes = List(
            Note("c", None, 4),
            Note("d", Some("#"), 5),
            Note("e", Some("b"), 3),
            Note("f", None, 6),
            Note("g", None, 2),
            Note("a", Some("#"), 0),
            Note("b", Some("b"), 9)
        )

        program.blocks.zip(expectedNotes).foreach { case (block, expectedNote) =>
            val stream = block.asInstanceOf[StreamBlock]
            val noteBlock = stream.base.asInstanceOf[NoteBlock]
            val pattern = noteBlock.pattern
            val sequence = pattern.elems.head
            val element = sequence.elems.head.asInstanceOf[AtomElement[Note]]
            
            assert(element.atom == expectedNote)
        }
    }
}