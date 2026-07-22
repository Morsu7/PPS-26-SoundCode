# Verifica e validazione

## Strategia

La verifica combina test unitari, test d'integrazione, test UI e prove prestazionali. I test devono controllare comportamento e invarianti, non soltanto l'assenza di eccezioni. I dispositivi esterni sono sostituiti con backend fake/no-op quando l'obiettivo non è validare Java Sound.

## Suite automatizzata esistente

| Area | Suite rappresentative | Copertura comportamentale |
|---|---|---|
| Parser | `ProgramParserSuite` | Programmi validi/non validi e costrutti della grammatica. |
| Interprete | `InterpreterFunSuite` | Traduzione dell'AST in pattern e payload. |
| Pattern/scheduler | `CorePatternsTest`, `TimeModifierPatternTest`, `RepetitionPatternTest`, `ReversePatternTest`, `OffsetModifierTest`, `JuxtapositionTest`, `ExtensionsPatternTest` | Intervalli di sequenza, parallelo, alternanza, trasformazioni ed effetti. |
| Player/backend | `AudioPlayerTest`, `MidiBackendTest`, `MidiBackendPipelineTest` | Dispatch temporale e traduzione verso il backend. |
| Audio | `MidiNoteTest`, `MidiAudioEngineTest`, `GmInstrumentMapTest`, `GmDrumMapTest` | Conversioni MIDI e gestione del motore. |
| UI | `MainViewSpec`, `BlockEditorViewSpec`, `AnimatedViewSpec`, `SyntaxHighlighterSpec`, `AutocompleteSupportSpec`, `AutoPairingSupportSpec` | Rendering, editor e supporti interattivi. |

La pipeline `.github/workflows/scala.yml` esegue l'intera suite con `xvfb-run -a sbt test` su Ubuntu/JDK 21.

Alla verifica locale del 21 luglio 2026 la suite comprendeva 164 test in 22 suite: 164 superati, nessun fallimento, test ignorato o suite abortita. Questo dato fotografa una revisione e non sostituisce il risultato CI associato alla consegna.

## Copertura

Il repository non configura ancora uno strumento di code coverage; pertanto non è corretto riportare una percentuale. Prima della consegna occorre integrare scoverage, eseguire `sbt clean coverage test coverageReport` e riportare almeno:

| Area | Line coverage | Branch coverage | Lacune note |
|---|---:|---:|---|
| Dominio e resolver | da misurare | da misurare | — |
| Parser e interprete | da misurare | da misurare | — |
| Player e backend | da misurare | da misurare | — |
| Audio concreto | da misurare | da misurare | dipendenza dall'ambiente MIDI |
| MVU e UI | da misurare | da misurare | rendering e temporizzazione grafica |

La percentuale globale, da sola, non stima il rischio di modifica: il commento finale dovrà indicare soprattutto rami non esercitati, confini infrastrutturali e funzionalità temporali sensibili.

## Prove non funzionali da automatizzare

- **Reattività (RNF-02):** generare il programma di riferimento, misurare parsing + interpretazione + scheduling separatamente e verificare che nessuna operazione lunga avvenga sul thread JavaFX.
- **Jitter (RNF-03):** usare un backend strumentato e un clock monotono; registrare deadline e istante di callback per 1.000 eventi, calcolare massimo e percentile 99.
- **Update (RNF-04):** sostituire la timeline a metà ciclo con clock controllabile e verificare che il vecchio pattern termini il ciclo e il nuovo inizi al confine successivo.
- **Robustezza (RNF-05):** property/fuzz test su testo arbitrario e casi limite della grammatica; ogni risultato deve essere successo o errore gestito.
- **Scalabilità (RNF-06):** programma da 10 stream × 100 eventi/ciclo, warm-up JVM documentato, almeno 30 esecuzioni e report di mediana/percentile 95/massimo e memoria.

## Validazione con l'utente

Una prova finale guidata deve coprire: scrittura di un beat, melodia con accidenti, gruppo annidato, alternanza, parallelismo, trasformazione temporale, errore e correzione, update live, stop, evidenziazione e visualizzazioni. Per ogni passo si annotano input, risultato atteso, risultato osservato e ambiente (OS, JDK, dispositivo MIDI).
