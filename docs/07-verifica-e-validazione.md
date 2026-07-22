# Verifica e validazione

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

Nello stato corrente del repository sono presenti **181 test dichiarati**, distribuiti in **22 suite**, oltre a quattro file di fixture e DSL di supporto. Il conteggio indica l'ampiezza della suite, non la percentuale di codice eseguita.

La pipeline `.github/workflows/scala.yml` esegue `xvfb-run -a sbt test` su Ubuntu con Temurin JDK 21. Xvfb fornisce il display virtuale necessario ai componenti JavaFX. Il risultato della pipeline associato alla revisione di consegna deve essere considerato l'evidenza definitiva dell'esito, perché un conteggio statico non dimostra che tutti i test siano stati completati con successo.

## Stima della copertura per area

Il repository non configura scoverage o un altro misuratore di code coverage. Non è quindi possibile riportare onestamente percentuali di line o branch coverage. La valutazione seguente è **qualitativa** e deriva dal confronto tra casi implementati e comportamenti esplicitamente esercitati dalle suite.

| Area | Suite principali | Copertura stimata | Comportamenti coperti | Lacune e rischio residuo |
|---|---|---|---|---|
| Parser | `SoundCodeParserSuite` | Alta | Generatori, sequenze, paralleli, alternanze, annidamenti, trasformazioni, visualizzatore, silenzi, accidenti, spazi ed errori sintattici, incluso `delay` con arità valida e non valida. | Non sono presenti fuzz test; combinazioni arbitrarie di input malformato e casi Unicode restano poco esplorati. |
| Interprete | `InterpreterFunSuite` | Medio-alta | Note, sample, silenzi, pattern annidati, effetti, trasformazioni, catene complesse e juxtaposition. | Meno capillare del parser: non ogni nodo AST e non ogni combinazione/ordine di estensioni possiede un test unitario dedicato. |
| Dominio e resolver | Suite `engine.pattern` | Alta sui casi funzionali | Sequenza, parallelo, alternanza, reverse, fast/slow, early/late, repetition, estensioni, juxtaposition e offset, anche annidati e con parametri dinamici. | `Fraction` e `Interval` sono verificati soprattutto indirettamente; finestre vuote, tempi negativi e valori numerici estremi non hanno una suite sistematica. |
| Scheduler | Suite `engine.pattern`, `MidiBackendPipelineTest` | Medio-alta | Timeline finite, ordine e posizione degli eventi, cicli e pipeline dalla DSL agli eventi. | La laziness e il consumo prolungato della timeline infinita non sono misurati direttamente in termini di memoria o scalabilità. |
| Player | `AudioPlayerTest` | Media | Ordine di esecuzione, conversione rispetto al tempo ed estensioni inoltrate al backend. | Thread reale, stop/start ripetuti, update concorrente, scadenza degli highlight e jitter non sono verificati con clock controllabile. |
| Backend MIDI | `MidiBackendTest`, `MidiBackendPipelineTest` | Alta sulla traduzione | Note, percussioni, pause, strumenti, gain, pan, room, LPF, valori di default, clamp e flusso completo parser–engine–backend. | Effetti non supportati e combinazioni multiple dello stesso effetto sono meno esplorati; non viene validata la qualità percettiva del risultato. |
| Infrastruttura audio | `MidiNoteTest`, `GmInstrumentMapTest`, `GmDrumMapTest`, `ChannelAllocatorTest`, `MidiAudioEngineTest` | Medio-alta per la logica, bassa per l'ambiente reale | Conversioni MIDI, lookup, input errati, riuso e overflow dei canali, chiusura idempotente e smoke test del synth. | Il comportamento varia fra soundbank e sistemi operativi; scheduling reale dei `noteOff`, indisponibilità del synth e concorrenza non sono completamente osservati. |
| Editor | `SyntaxHighlighterSpec`, `AutocompleteSupportSpec`, `AutoPairingSupportSpec`, `BlockEditorViewSpec` | Alta sulle funzioni locali | Stili, range fuori limite, multilinea, completamento contestuale, pairing, selezioni, normalizzazione newline e highlight di playback. | Editing durante la riproduzione e riposizionamento degli ancoraggi grafici sono coperti solo parzialmente. |
| Vista principale e animazioni | `MainViewSpec`, `AnimatedViewSpec` | Media | Composizione della vista, pulsanti, scorciatoie, dispatch, avvio/arresto e idempotenza dell'animazione. | Rendering del piano roll, resize/scroll, layout degli overlay, banner di errore e sincronizzazione visiva prolungata non hanno copertura approfondita. |
| Coordinamento MVU | Coperto indirettamente dalle suite UI e pipeline | Bassa | Alcuni messaggi sono osservati attraverso i controlli della vista. | Mancano test diretti di `Update`, `Cmd` e `SoundCodeRuntime`, in particolare la conservazione della timeline precedente dopo un errore e la revisione della timeline. |

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

## Miglioramenti prioritari della verifica automatica

Per rendere la stima più oggettiva e chiudere le lacune a maggiore impatto sono prioritari:

1. integrare scoverage e pubblicare line e branch coverage per package, senza usare la sola percentuale globale come criterio di qualità;
2. aggiungere test puri di tutte le transizioni MVU e verificare che un parsing fallito non sostituisca la timeline valida;
3. rendere iniettabili clock ed executor del player per verificare update, stop/start, highlight e deadline senza dipendere dal tempo reale;
4. introdurre property test per frazioni, intervalli e invarianti dei resolver;
5. aggiungere fuzz test del parser, controllando che ogni input produca un AST valido oppure un errore gestito;
6. verificare piano roll e ancoraggi dell'editor durante inserimenti, cancellazioni, scroll e ridimensionamento;
7. separare lo smoke test MIDI dipendente dall'ambiente dalla suite deterministica, riportandone chiaramente l'eventuale esclusione.

Con scoverage configurato, il comando di riferimento diventerebbe:

```text
sbt clean coverage test coverageReport
```

Il report di consegna dovrebbe riportare copertura di righe e rami almeno per parser/interprete, dominio/engine, backend/audio e MVU/UI, accompagnandola con le lacune qualitative sopra descritte.

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
