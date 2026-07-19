package soundcode.mvu

import soundcode.parser.SoundCodeParser
import fastparse.*
import soundcode.interpreter.Interpreter
import soundcode.domain.*
import soundcode.engine.{AudioPlayer, SchedulerImpl}

sealed trait Cmd:
  def run(dispatch: Msg => Unit, audio: AudioPlayer): Unit

object Cmd:
  case object NoOp extends Cmd:
    override def run(dispatch: Msg => Unit, audio: AudioPlayer): Unit = ()

  case class ParseAndInterpret(code: String) extends Cmd:
    override def run(dispatch: Msg => Unit, audio: AudioPlayer): Unit =
      val parser = new SoundCodeParser

      val (streams, requests, errors) = parser.parseProgram(code) match {
        case Right(programAST) =>
          (Interpreter.interpret(programAST), Interpreter.extractVisualizerRequests(programAST), None)

        case Left(errorMsg) =>
          (List.empty[Pattern[AudioPayload]], List.empty[VisualizerRequest], Some(s"Parsing failed:\n$errorMsg"))
      }

      dispatch(Msg.CodeParsed(streams, requests, errors))

  /** Avvia il loop di riproduzione dell'AudioPlayer. */
  case object StartPlayback extends Cmd:
    override def run(dispatch: Msg => Unit, audio: AudioPlayer): Unit = audio.start()

  /** Ferma il loop di riproduzione dell'AudioPlayer. */
  case object StopPlayback extends Cmd:
    override def run(dispatch: Msg => Unit, audio: AudioPlayer): Unit = audio.stop()

  /** Arma la timeline dell'AudioPlayer con gli stream appena interpretati. */
  case class UpdateTimeline(
    streams: List[Pattern[AudioPayload]],
    visualizers: List[VisualizerRequest]
  ) extends Cmd :
    override def run(dispatch: Msg => Unit, audio: AudioPlayer): Unit = {
      audio.updateTimeline(SchedulerImpl.generateInfiniteTimeline(streams))
      dispatch(Msg.UpdateTimelines(SchedulerImpl.generateBoundedTimelines(streams), visualizers))
    }