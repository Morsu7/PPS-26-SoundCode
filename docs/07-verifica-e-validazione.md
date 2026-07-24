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

La valutazione seguente è **qualitativa** e deriva dal confronto tra casi implementati e comportamenti esplicitamente esercitati dalle suite.

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
| Coordinamento MVU | Suite diretta su `Update.update` (`VolumeUpdateTest`) e verifiche ai confini UI | Media | `VolumeUpdateTest` verifica `Update.update` (messaggio → modello + `Cmd`); le suite della vista controllano la produzione di alcuni `Msg`. |

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
| Modello o transizione MVU | Copertura diretta solo per `VolumeChangeRequested` (`VolumeUpdateTest`) | Bassa | Estendere la suite pura ad altre varianti di `Msg`. |
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

La verifica dell'engine è stata progettata con l'obiettivo di controllare che la timeline prodotta dal sistema sia sempre corretta dal punto di vista temporale. Poiché il comportamento dell'engine dipende dalla composizione dei pattern e dalle trasformazioni applicate, i test non si limitano a verificare la presenza degli eventi, ma controllano soprattutto la loro geometria nel tempo.

Per mantenere la suite di test leggibile e facilmente estendibile sono state adottate due strategie principali.

## DSL dichiarativo per la verifica dei pattern

Scrivere test per un motore di scheduling temporale può diventare rapidamente molto complesso. Senza strumenti dedicati sarebbe necessario costruire manualmente l'intero albero dei pattern e confrontare oggetti `ScheduledEvent` ricchi di informazioni, molte delle quali irrilevanti ai fini della verifica.

Per evitare questo problema è stato sviluppato un piccolo **DSL (Domain-Specific Language)** interno, che permette di descrivere i test con una sintassi molto vicina a quella utilizzata dall'utente durante il live coding.

Grazie a questo DSL, i pattern possono essere scritti direttamente in forma compatta, senza dover costruire manualmente l'albero sintattico. Ad esempio, invece di creare esplicitamente tutti i nodi che rappresentano una sequenza, è sufficiente scrivere:

```scala
val pattern = seq(bd, hh).late(1\4)
```

Anche la fase di verifica viene semplificata dal metodo check, che esegue esattamente lo stesso percorso utilizzato dall'engine in produzione, consumando la LazyList generata dallo scheduler. Gli eventi prodotti vengono convertiti in un oggetto ExpectedEvent, che mantiene le informazioni rilevanti per il test (part, whole, effetti e suono riprodotto), ma le presenta in una forma molto più leggibile. In questo modo il risultato del test descrive direttamente la "partitura" generata dall'engine, rendendo immediato capire se la geometria temporale è corretta senza dover analizzare la struttura completa di un ScheduledEvent.

Una semplice sequenza ritmica può quindi essere verificata in modo estremamente leggibile:

```scala
val pattern = seq(bd, hh)

check(pattern).inCycle(0)(
  ExpectedEvent("bd", part = 0 -> (1\2)),
  ExpectedEvent("hh", part = (1\2) -> 1)
)
```

L'obiettivo principale del DSL è verificare che tutte le trasformazioni temporali mantengano corretta la geometria della timeline.

Un caso particolarmente importante riguarda l'operatore `late`, che ritarda gli eventi nel tempo. Quando una nota attraversa il confine tra due cicli, il motore deve dividerne correttamente la parte visibile (`part`), mantenendo però la durata originale (`whole`).

Questo comportamento può essere verificato con un test come il seguente:

```scala
val pattern = seq(bd, sn).late(1\4)

check(pattern).inCycleUnordered(0)(
  ExpectedEvent("sn", part = 0 -> (1\4), whole = (-1\4) -> (1\4)),
  ExpectedEvent("bd", part = (1\4) -> (3\4)),
  ExpectedEvent("sn", part = (3\4) -> 1, whole = (3\4) -> (5\4))
)
```

Il DSL permette inoltre di testare facilmente pattern molto complessi, evitando lunghe fasi di preparazione.

Un esempio è rappresentato dall'operatore `jux`, che duplica un pattern applicando una trasformazione differente ai due canali stereo. Nel test seguente viene verificato che il canale sinistro riproduca la sequenza originale, mentre quello destro esegua la stessa sequenza invertita temporalmente:

```scala
val pattern = seq(bd, seq(hh, sn)).jux(rev)

check(pattern).inCycleUnordered(0)(
  // Canale sinistro
  ExpectedEvent("bd", part = 0 -> (1\2),     effects = List("0.0")),
  ExpectedEvent("hh", part = (1\2) -> (3\4), effects = List("0.0")),
  ExpectedEvent("sn", part = (3\4) -> 1,     effects = List("0.0")),

  // Canale destro
  ExpectedEvent("sn", part = 0 -> (1\4),     effects = List("1.0")),
  ExpectedEvent("hh", part = (1\4) -> (1\2), effects = List("1.0")),
  ExpectedEvent("bd", part = (1\2) -> 1,     effects = List("1.0"))
)
```

In questo modo i test assumono una forma molto vicina a una vera e propria partitura musicale. La lettura risulta immediata e qualsiasi modifica interna all'engine può essere validata semplicemente verificando che la geometria degli eventi rimanga invariata.

## Controllo deterministico del tempo nell'AudioPlayer

Per verificare il comportamento dell'`AudioPlayer` sarebbe poco affidabile utilizzare direttamente il clock del sistema (`System.currentTimeMillis()`), poiché i test diventerebbero dipendenti dal tempo reale e dall'esecuzione dei thread.

Per questo motivo il player espone il metodo:

```scala
tick(now: AbsoluteTime)
```

In questo modo il tempo può essere controllato direttamente dal test, avanzandolo manualmente millisecondo per millisecondo.

```scala
for (t <- startMs to endMs) {
  audioPlayer.tick(AbsoluteTime(t))
}
```

Questa soluzione rende i test completamente deterministici e riproducibili. È possibile verificare con precisione il momento in cui ogni evento viene riprodotto, la durata dei suoni emessi e la corretta sincronizzazione tra timeline logica, backend audio e aggiornamento dell'interfaccia.