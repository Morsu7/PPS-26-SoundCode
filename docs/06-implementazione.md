# Implementazione

Questa sezione approfondisce soltanto le soluzioni realizzative che concretizzano decisioni rilevanti del design. Non costituisce una descrizione esaustiva di ogni classe: nomi e frammenti di codice sono riportati quando chiariscono l'uso di Scala, gli algoritmi adottati o l'integrazione con librerie esterne.

## Organizzazione del codice

Il codice è suddiviso in package che seguono i confini definiti nel design di dettaglio.

| Package | Contenuto principale | Contributo prevalente |
|---|---|---|
| `soundcode.parser` | Grammatica FastParse, AST e descrizione dei costrutti della DSL | Cristian Morbidelli |
| `soundcode.interpreter` | Conversione dall'AST ai pattern del dominio | Cristian Morbidelli |
| `soundcode.domain` | Tempo, pattern, payload, eventi e richieste di visualizzazione | Trasversale, con uso prevalente nell'engine |
| `soundcode.engine` | Resolver, scheduler, player e backend audio | Federico Morsucci |
| `soundcode.audio` | Conversioni General MIDI e accesso al sintetizzatore | Tommaso Remedi |
| `soundcode.mvu` | Modello, messaggi, transizioni, comandi e runtime | Giacomo Biagioni |
| `soundcode.ui` | Finestra, editor, banner e visualizzazioni | Giacomo Biagioni |

Questa organizzazione mantiene FastParse confinato nel parser, Java MIDI nell'infrastruttura audio e ScalaFX/RichTextFX nella presentazione. Il package di dominio non importa nessuna di queste tecnologie.

## Modello di dominio in Scala

Il dominio sfrutta i tipi algebrici di Scala 3 per rendere espliciti i casi ammessi. `Pattern` è un trait sigillato covariante i cui casi rappresentano atomo, sequenza, parallelo, alternanza, trasformazione temporale ed estensioni. Analogamente, `Sound`, `AudioEffect` e `PatternModifier` sono enumerazioni: il pattern matching può quindi distinguere esaustivamente i casi senza ricorrere a codici o stringhe convenzionali.

Note e sample sono esposti come **opaque type**. All'esterno si comportano come concetti del dominio, mentre la loro rappresentazione interna rimane nascosta. Le posizioni nel sorgente sono modellate da una coppia di indici con estremo finale escluso, la stessa convenzione usata dall'editor.

### Tempo razionale

`Fraction` normalizza numeratore e denominatore alla costruzione, porta il segno al numeratore e riduce la frazione tramite massimo comune divisore. Implementa aritmetica, confronto, `floor` e `ceil`; `Interval` aggiunge durata, intersezione e trasformazione degli estremi.

Le operazioni dei resolver rimangono quindi razionali. La conversione in `Double` è usata per configurazioni provenienti dalla DSL, ordinamento o rendering, mentre `Tempo` converte cicli e durate in millisecondi soltanto nel player. In questo modo suddivisioni annidate come terzi, quinti o trasformazioni successive non accumulano approssimazioni durante lo scheduling.

## Parser e interprete — Cristian Morbidelli

### Implementazione della grammatica

`SoundCodeParser` usa FastParse senza gestione automatica degli spazi (`NoWhitespace`). Gli spazi ammessi sono dichiarati nelle singole produzioni, rendendo esplicita la distinzione fra separatori della Mini Notation, spazi facoltativi e newline che separano gli stream.

Le funzioni del parser rispecchiano la struttura grammaticale: programma, stream, generatore, estensione, pattern, sequenza, elemento e atomo. I combinatori `rep`, `.?`, alternative e `map` costruiscono direttamente i nodi dell'AST. Il parser pubblico converte il risultato di FastParse in:

```scala
Either[String, ProgramAST]
```

Il ramo destro contiene l'albero valido; il ramo sinistro contiene un errore già formattato con riga, colonna, aspettative e indicatore della posizione. L'uso di `Either` impedisce che un normale errore dell'utente venga trattato come un'eccezione applicativa.

I combinatori `Index` acquisiscono gli offset prima e dopo note, sample, configurazioni, silenzi e comandi di visualizzazione. Tali valori vengono memorizzati nell'AST e successivamente trasferiti ai tipi di dominio. Le descrizioni `opaque` di FastParse rendono inoltre più leggibili gli elementi attesi nei messaggi di errore.

I pattern di note, sample e valori numerici riusano funzioni generiche parametrizzate sul tipo di atomo. Questa scelta evita tre grammatiche quasi identiche per sequenze, gruppi, alternanze e modificatori `*` e `/`, pur impedendo che un tipo di atomo compaia in un contesto non compatibile.

### Traduzione dell'AST

`Interpreter` è implementato principalmente mediante pattern matching e trasformazioni di liste. La conversione procede dal basso verso l'alto:

- gli atomi sintattici diventano `Pattern.Atom` contenenti note, sample, silenzi o numeri;
- una sequenza con più elementi diventa `Pattern.Sequence`;
- le sequenze parallele diventano `Pattern.Parallel`;
- le alternative diventano `Pattern.Alternation`;
- gli operatori temporali producono `Pattern.TimeWarp`;
- gli effetti e i generatori aggiuntivi producono `Pattern.WithExtensions`.

Le funzioni di costruzione evitano wrapper inutili quando una sequenza o un parallelo contiene un solo elemento. Le estensioni vengono elaborate con un `foldLeft`, preservandone l'ordine: questo è importante perché una trasformazione può racchiudere il risultato delle estensioni precedenti anziché essere semplicemente accodata.

Le richieste `_pianoroll()` sono estratte separatamente dai pattern audio. L'indice dello stream e l'offset nel sorgente formano il collegamento necessario alla UI senza inserire elementi grafici nell'algebra musicale.

## Engine — Federico Morsucci

### Dispatch e resolver specializzati

`PatternResolver` è l'unico punto che effettua il pattern matching sulla forma generale di `Pattern`. Ogni caso viene inoltrato a un resolver dedicato, mentre un extension method offre al resto del codice l'operazione uniforme `resolve` con la finestra temporale fornita come parametro contestuale `using`.

L'implementazione dei casi più significativi è la seguente:

- **atomo:** produce un evento che occupa l'intera finestra ricevuta;
- **parallelo:** risolve tutti i livelli nella stessa finestra e ne concatena gli eventi;
- **sequenza:** suddivide ogni ciclo in intervalli uguali, porta la finestra del figlio nelle sue coordinate locali e rimappa gli eventi nel relativo slot;
- **alternanza:** usa il numero del ciclo per scegliere il ramo e trasla la finestra nelle coordinate del pattern selezionato;
- **estensioni:** risolve i pattern di configurazione sulla finestra dell'evento base e allega i valori attivi nell'istante iniziale;
- **time warp:** trasforma la finestra in ingresso, risolve il pattern interno e applica agli eventi la trasformazione inversa.

La sequenza esemplifica la tecnica generale. Se contiene `n` elementi, ogni slot misura `1/n`; l'intersezione con la finestra richiesta evita di valutare porzioni non osservabili. La finestra viene prima ingrandita nelle coordinate del figlio e gli eventi risultanti vengono poi ridotti e traslati nello slot originale.

Reverse scambia gli estremi rispetto al ciclo corrente. Fast, slow, early e late usano invece un helper comune che risolve il pattern dinamico del parametro e applica due funzioni: una per trasformare la finestra interrogata e una per riportare l'evento nelle coordinate esterne. Repetition suddivide la durata dell'evento nel numero richiesto di copie; juxtaposition e offset compongono pattern già esistenti invece di introdurre un secondo meccanismo di scheduling.

### Timeline finite e infinite

Lo scheduler riusa i resolver in due modalità:

- `generateBoundedTimelines` calcola, per ciascun pattern, i cicli necessari a mostrarne una ripetizione completa;
- `generateInfiniteTimeline` produce una `LazyList`, risolvendo un ciclo alla volta e ordinando gli eventi per istante iniziale.

La lunghezza ciclica è calcolata ricorsivamente sull'algebra dei pattern. La timeline infinita non cresce tutta in memoria in anticipo: i cicli vengono generati man mano che il player consuma la lista.

### Player e concorrenza

`AudioPlayer` mantiene la timeline residua, l'istante di riferimento e le posizioni attualmente evidenziate. Un thread dedicato esegue un tick con risoluzione nominale di un millisecondo. Ogni tick:

1. rimuove gli highlight scaduti;
2. separa dalla `LazyList` gli eventi ormai maturi;
3. esegue soltanto gli eventi che iniziano nella loro porzione completa, evitando di riattivare un suono iniziato prima della finestra;
4. calcola la durata in millisecondi e chiama il backend;
5. associa alle posizioni sorgente la rispettiva scadenza.

Timeline e flag di esecuzione sono `volatile` perché possono essere aggiornati dal thread applicativo mentre il player opera in background. La callback degli highlight rientra nel runtime sotto forma di messaggio MVU, mantenendo l'engine indipendente dalla GUI.

## Audio e MIDI — Tommaso Remedi

### Adattamento dei payload

`MidiBackend` realizza `AudioBackend` e traduce i payload del dominio in operazioni più vicine a MIDI. Per le note combina nome, alterazione e ottava, quindi `MidiNote` calcola il numero MIDI. Per i sample, `GmDrumMap` associa gli identificatori supportati alle note del canale General MIDI 10. `GmInstrumentMap` converte invece i nomi degli strumenti in program number.

Gli effetti allegati all'evento vengono raccolti in una `NoteSpec`:

- `gain` modifica la velocity, limitata all'intervallo MIDI;
- `pan` è convertito nel Control Change 10;
- `room` è convertito nel Control Change 91;
- `lpf` è approssimato con il controllo di brightness 74, usando una scala logaritmica fra le frequenze limite;
- effetti senza una resa MIDI disponibile non generano comandi.

Il backend dipende dal trait `AudioEngine`, non dal sintetizzatore concreto. Nei test può quindi ricevere un fake; in esecuzione, la factory prova ad aprire il sintetizzatore JVM e ripiega su `NoopAudioEngine` se l'operazione fallisce.

### Gestione dei canali e delle durate

`MidiAudioEngine` usa i canali melodici disponibili escluso l'indice 9, riservato alle percussioni. `ChannelAllocator` identifica una voce mediante programma, pan, riverbero e brightness. Una voce già nota riusa il proprio canale; una nuova voce riceve il canale successivo secondo una politica round-robin. Quando i canali terminano, il più vecchio viene riutilizzato: il sistema degrada con una possibile variazione udibile invece di interrompere la riproduzione.

La velocity non fa parte dell'identità della voce perché è una proprietà della singola nota. Questa scelta permette a note con gain diverso di condividere il canale senza modificarsi reciprocamente.

L'invio di `noteOn` è sincrono e breve; il corrispondente `noteOff` viene programmato su un `ScheduledExecutorService` daemon. Il thread del player può così continuare a estrarre eventi senza restare bloccato per la durata delle note. Alla chiusura, executor e sintetizzatore sono rilasciati in modo tollerante agli errori.

## Interfaccia utente — Giacomo Biagioni

### Runtime MVU

Il package `mvu` implementa esplicitamente i quattro elementi descritti nel design:

- `AppModel` è una case class immutabile;
- `Msg` è un enum chiuso di eventi applicativi;
- `Update.update` è una funzione che restituisce la coppia nuovo modello/comando;
- `Cmd` descrive ed esegue parsing, scheduling e controllo del player;
- `SoundCodeRuntime` conserva il modello corrente, esegue il rendering e interpreta i comandi.

Il comando di aggiornamento analizza e interpreta il testo. In caso di successo arma il player con la timeline lazy e invia alla UI timeline finite e richieste di visualizzazione. In caso di errore invia soltanto il messaggio diagnostico: poiché non viene emesso alcun comando di sostituzione, il player conserva la timeline precedente.

`timelineRevision` viene incrementato a ogni aggiornamento valido. La UI lo usa per riconoscere una nuova versione delle timeline anche quando gli altri campi osservabili potrebbero risultare uguali.

### Editor RichTextFX

`BlockEditorView` usa una `GenericStyledArea` RichTextFX all'interno di uno scroll pane virtualizzato. Le funzionalità ortogonali sono isolate in moduli dedicati:

- `SyntaxHighlighter` applica stili distinti a stringhe e nomi di funzione, poi sovrappone lo stile delle posizioni in riproduzione;
- `AutocompleteSupport` determina il contesto del cursore e propone costrutti compatibili;
- `AutoPairingSupport` gestisce parentesi, virgolette e selezioni;
- `LineNumberFactory` costruisce il gutter con i numeri di riga.

L'evidenziazione viene accodata con `Platform.runLater`, evitando modifiche grafiche fuori dal thread JavaFX e raggruppando più richieste ravvicinate. Quando l'utente cambia il testo dopo un update, gli highlight della vecchia timeline vengono temporaneamente nascosti perché i relativi offset non descrivono più necessariamente gli stessi caratteri.

### Visualizzazioni incorporate

I visualizzatori sono nodi JavaFX sovrapposti all'editor e ancorati all'offset del comando che li ha richiesti. Lo stile di paragrafo riserva lo spazio verticale necessario; a ogni modifica del testo gli offset degli ancoraggi successivi vengono traslati della differenza tra caratteri inseriti e rimossi. Scroll e ridimensionamento provocano un nuovo calcolo della posizione sullo schermo.

`CanvasAnimatedView` implementa il comportamento comune su un canvas ridimensionabile. Un `AnimationTimer` usa il tempo monotono in nanosecondi e il valore `cps` per ricavare il ciclo corrente. `PianorollView` converte la timeline in eventi grafici, assegna una corsia a ogni nota o sample e replica visivamente la timeline finita per simulare il ciclo continuo attorno a un playhead centrale.

`MainView` non chiama direttamente parser o player: pulsanti e scorciatoie producono messaggi. Durante il rendering aggiorna editor e banner e avvia o arresta le animazioni soltanto quando cambia lo stato di riproduzione.
