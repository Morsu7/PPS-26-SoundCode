package soundcode.engine

import soundcode.domain.*

/** Motore responsabile della pianificazione temporale degli eventi musicali.
 *
 * Trasforma l'albero sintattico dei [[Pattern]] in sequenze lineari di eventi ([[ScheduledEvent]]),
 * calcolando e assegnando a ciascun evento la sua esatta collocazione nel tempo (espressa in cicli/frazioni).
 */
trait Scheduler:
  def generateBoundedTimelines(patterns: List[Pattern[AudioPayload]]): List[Seq[ScheduledEvent[AudioPayload]]]
  def generateInfiniteTimeline(patterns: List[Pattern[AudioPayload]]): LazyList[ScheduledEvent[AudioPayload]]

/** Implementazione standard dello [[Scheduler]].
 *
 * Offre due modalità principali di generazione:
 * - `generateBoundedTimelines`: crea una sequenza finita determinando la lunghezza naturale del pattern,
 *   utile per esportazioni o analisi statiche della traccia.
 * - `generateInfiniteTimeline`: genera un flusso infinito di eventi elaborati dinamicamente,
 *   progettato per la riproduzione continua (es. live coding).
 */
object SchedulerImpl extends Scheduler:

  def generateBoundedTimelines(patterns: List[Pattern[AudioPayload]]): List[Seq[ScheduledEvent[AudioPayload]]] =
    patterns.map { pat =>
      val totalCycles = pat.cycleLength
      (0 until totalCycles).flatMap{n =>
        given Interval = Interval(Fraction(n), Fraction(n + 1))
        pat.resolve
      }.toList
    }

  def generateInfiniteTimeline(patterns: List[Pattern[AudioPayload]]): LazyList[ScheduledEvent[AudioPayload]] =
    def loop(nCycle: Int): LazyList[ScheduledEvent[AudioPayload]] =
      given Interval = Interval(Fraction(nCycle), Fraction(nCycle + 1))
      val cycleEvents = patterns.flatMap(p => p.resolve).sortBy(_.part.start.toDouble)
      LazyList.from(cycleEvents) #::: loop(nCycle + 1)
    loop(0)