---
layout: default
title: Verifica e Validazione
---

## Strategia

La verifica automatizzata segue gli stessi confini del progetto: parser e interprete, dominio ed engine, audio/MIDI e interfaccia. Lo scopo non è soltanto mostrare che la versione corrente funziona, ma delimitare quali modifiche possono essere effettuate con una buona probabilità che una regressione venga rilevata.

La suite combina:

- test unitari delle trasformazioni pure e delle funzioni di supporto;
- test comportamentali dei pattern su intervalli temporali attesi;
- test d'integrazione dalla DSL fino agli eventi o alle richieste MIDI;
- test UI eseguiti sul thread JavaFX;
- uno smoke test dipendente dalla disponibilità del sintetizzatore locale.

Quando l'hardware non è l'oggetto della prova, le dipendenze esterne sono sostituite con backend o motori audio fake. Questo rende deterministici i test di scheduling e permette di verificare i comandi prodotti senza richiedere un dispositivo MIDI.

## Dimensione della suite

La pipeline `.github/workflows/scala.yml` esegue `xvfb-run -a sbt test` su Ubuntu con Temurin JDK 21. Xvfb fornisce il display virtuale necessario ai componenti JavaFX. Il risultato della pipeline associato alla revisione di consegna deve essere considerato l'evidenza definitiva dell'esito, perché un conteggio statico non dimostra che tutti i test siano stati completati con successo.

## Stima della copertura per area

Il repository non configura scoverage o un altro misuratore di code coverage. Non è quindi possibile riportare onestamente percentuali di line o branch coverage. La valutazione seguente è **qualitativa** e deriva dal confronto tra casi implementati e comportamenti esplicitamente esercitati dalle suite.

| Area | Suite principali | Copertura stimata | Comportamenti coperti |
|---|---|---|---|
| Parser | `SoundCodeParserSuite` | Alta | Generatori, sequenze, paralleli, alternanze, annidamenti, trasformazioni, visualizzatore, silenzi, accidenti, spazi ed errori sintattici, incluso `delay` con arità valida e non valida. |
| Interprete | `InterpreterFunSuite` | Medio-alta | Note, sample, silenzi, pattern annidati, effetti, trasformazioni, catene complesse e juxtaposition. |
| Dominio e resolver | Suite `engine.pattern` | Alta sui casi funzionali | Sequenza, parallelo, alternanza, reverse, fast/slow, early/late, repetition, estensioni, juxtaposition e offset, anche annidati e con parametri dinamici. |
| Scheduler | Suite `engine.pattern`, `MidiBackendPipelineTest` | Medio-alta | Timeline finite, ordine e posizione degli eventi, cicli e pipeline dalla DSL agli eventi. |
| Player | `AudioPlayerTest` | Media | Ordine di esecuzione, conversione rispetto al tempo ed estensioni inoltrate al backend. |
| Backend MIDI | `MidiBackendTest`, `MidiBackendPipelineTest` | Alta sulla traduzione | Note, percussioni, pause, strumenti, gain, pan, room, LPF, valori di default, clamp e flusso completo parser–engine–backend. |
| Infrastruttura audio | `MidiNoteTest`, `GmInstrumentMapTest`, `GmDrumMapTest`, `ChannelAllocatorTest`, `MidiAudioEngineTest` | Medio-alta per la logica, bassa per l'ambiente reale | Conversioni MIDI, lookup, input errati, riuso e overflow dei canali, chiusura idempotente e smoke test del synth. |
| Editor | `SyntaxHighlighterSpec`, `CompletionProviderSpec`, `AutocompleteSupportSpec`, `AutoPairingSupportSpec`, `BlockEditorViewSpec` | Medio-alta sulla logica locale | Colorazione sintattica, precedenza degli stili, intervalli di riproduzione validi e fuori limite, completamento contestuale e inserimento degli snippet, delimitatori e selezioni, normalizzazione dei fine riga. |
| Vista principale e animazioni | `MainViewSpec`, `AnimatedViewSpec` | Media | Struttura della vista, configurazione della toolbar e del controllo del volume, messaggi prodotti da pulsanti e scorciatoie, avvio, arresto e idempotenza dell'animazione. |
| Coordinamento MVU | Nessuna suite diretta; osservato solo ai confini della UI | Bassa | Le suite della vista verificano la produzione di alcuni `Msg`, senza attraversare il ciclo di aggiornamento. |

### Lettura complessiva

La parte più protetta dalle regressioni è il percorso **DSL → AST → pattern → eventi temporali → parametri MIDI**. Numerosi casi verificano valori strutturali precisi, come intervalli, ordine degli eventi, estensioni e numeri MIDI: una modifica alle regole linguistiche o ai resolver che alteri la semantica prevista ha quindi buone probabilità di far fallire più suite.

La copertura è meno robusta nei punti dominati da stato mutabile, tempo reale o framework grafici: ciclo MVU completo, concorrenza del player, ancoraggio dei visualizzatori, rendering su canvas e sintetizzatore concreto. In queste aree un'esecuzione verde riduce il rischio, ma non esclude regressioni dipendenti da timing, sistema operativo o sequenze d'interazione non rappresentate.

## Impatto atteso delle modifiche

La seguente matrice indica dove ci si aspetta che una modifica venga intercettata e dove sono opportune prove aggiuntive.

| Tipo di modifica | Test che dovrebbero rilevare regressioni | Confidenza | Verifica aggiuntiva consigliata |
|---|---|---|---|
| Sintassi o messaggi di errore della DSL | `SoundCodeParserSuite` | Alta | Aggiungere casi positivi e negativi specifici della nuova produzione. |
| Traduzione di un nodo AST | `InterpreterFunSuite` e suite pipeline | Medio-alta | Testare il nodo isolato e almeno una composizione con estensioni. |
| Semantica di sequenza, parallelo o alternanza | `CorePatternsTest` e suite temporali | Alta | Includere finestre che attraversano il confine di ciclo. |
| Nuovo modificatore temporale | Suite `engine.pattern` | Alta se aggiunta una suite dedicata, bassa altrimenti | Verificare composizione, annidamento e parametro dinamico. |
| Calcolo di `Fraction` o `Interval` | Molte suite engine fallirebbero indirettamente | Media | Aggiungere test unitari e property test sulle leggi aritmetiche. |
| Politica dello scheduler infinito | `MidiBackendPipelineTest` e test engine solo in parte | Media-bassa | Testare laziness, ordinamento multiciclo e consumo limitato. |
| Threading, play/stop o update della timeline | `AudioPlayerTest` solo in parte | Bassa | Introdurre clock ed executor controllabili e test di concorrenza. |
| Mapping di note, strumenti, drum o effetti MIDI | Suite audio/backend | Alta | Mantenere almeno un test di pipeline dalla DSL. |
| Allocazione dei canali | `ChannelAllocatorTest` | Alta per l'algoritmo | Provare anche l'integrazione con note simultanee sul synth. |
| Logica di autocomplete, pairing o highlighting | Suite editor dedicate | Alta | Aggiungere un caso per ogni nuovo contesto editoriale. |
| Modello o transizione MVU | Copertura indiretta | Bassa | Creare una suite pura per ogni variante di `Msg`. |
| Layout o animazione dei visualizzatori | `AnimatedViewSpec` solo per il ciclo di vita | Bassa | Test JavaFX mirati e scenario manuale con resize, scroll e update. |

Questa matrice deve guidare la manutenzione: una modifica in un'area a confidenza bassa richiede test nuovi nello stesso cambiamento, mentre nelle aree ad alta confidenza occorre comunque aggiornare le aspettative quando il comportamento desiderato cambia intenzionalmente.

## Validazione

I test automatici verificano la conformità delle singole parti, ma non sostituiscono l'ascolto e l'uso del prodotto. La validazione finale deve essere svolta sull'applicazione completa mediante uno scenario ripetibile che includa:

- composizione di beat e melodia con note alterate;
- sequenze, paralleli, gruppi e alternanze;
- trasformazioni temporali ed effetti udibili;
- introduzione e correzione di un errore sintattico durante la sessione;
- aggiornamento del programma mentre la timeline precedente è in riproduzione;
- play, stop, highlight e piano roll;
- avvio sia con sintetizzatore disponibile sia con fallback silenzioso.

Per ogni passo vanno annotati input, risultato atteso, risultato osservato, revisione del software, sistema operativo, JDK e dispositivo MIDI. Le proprietà percettive — udibilità, correttezza apparente del ritmo e chiarezza del feedback grafico — richiedono infatti una valutazione umana e non sono deducibili dalla sola copertura del codice.


# Strategia dei Test Engine

La strategia di testing è stata progettata per validare l'integrità della geometria temporale dell'AST e il corretto consumo della timeline. Per mantenere i test puliti e focalizzati, l'architettura si avvale principalmente di due pattern di testing mirati:

## DSL Dichiarativo per la Verifica dell'AST

Per testare la correttezza geometrica dell'algebra dei pattern senza rumore (*boilerplate*), è stato sviluppato un Domain-Specific Language (DSL) interno basato su `AnyFunSuite`.

Sfruttando extension methods e la valutazione pigra (`LazyList`), i test valutano lo stesso *execution path* di produzione proiettando i complessi `ScheduledEvent` nel record semplificato `ExpectedEvent`:

```scala
check(seq(bd, hh).late(1\4)).inCycle(0)(
  ExpectedEvent("bd", part = (1\4) -> (1\2)),
  ExpectedEvent("hh", part = 0 -> (1\4), whole = (-1\4) -> (1\4))
)
```

Questo approccio verifica in modo sistematico l'invariante fondamentale tra **whole** (durata logica originaria) e **part** (porzione visibile nel ciclo), coprendo scenari di partizionamento, polifonia, estensioni e distorsioni geometriche complesse (*fast*, *slow*, *late* e *reverse*).

## Controllo Deterministico del Tempo nell'AudioPlayer

Per testare l'integrazione del player ed evitare test fluttuanti dovuti ai thread asincroni o al clock reale di sistema (`System.currentTimeMillis()`), l'architettura dell'`AudioPlayer` espone esplicitamente il metodo di avanzamento:

```scala
tick(now: AbsoluteTime)
```

Questo design consente al *test runner* di pilotare manualmente lo scorrere del tempo, millisecondo per millisecondo, in modo completamente deterministico.

```scala
// Simulazione manuale e deterministica del tempo nel test runner
for (t <- startMs to endMs) {
  audioPlayer.tick(AbsoluteTime(t))
}
```

In questo modo è possibile ispezionare direttamente il backend audio e validare con assoluta precisione la sequenza, il *timing* e la durata degli eventi emessi, garantendo la massima robustezza dell'intera suite di test.
