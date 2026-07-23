---
layout: default
title: Implementazione
---

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

### Implementazione del parser

Il parser di SoundCode è implementato tramite **FastParse**, utilizzando parser combinator definiti direttamente in Scala.

L'implementazione segue la struttura dell'AST definito nel progetto, con una corrispondenza diretta tra le funzioni del parser e i costruttori sintattici del linguaggio.

Le principali produzioni implementate sono:

```text
Program
 └── Block
      ├── StreamBlock
      │    ├── GenerativeBlock
      │    ├── ExtensionBlock
      │    └── VisualizerBlock
      └── SettingBlock
```

Ogni funzione del parser ha quindi il compito di riconoscere una specifica categoria sintattica e produrre direttamente il relativo nodo AST tramite operazioni di trasformazione `.map`.

---

### Gestione degli spazi nella grammatica

Il parser utilizza `NoWhitespace`, disabilitando la gestione automatica degli spazi fornita da FastParse.

Questa scelta richiede che ogni regola specifichi esplicitamente dove gli spazi sono ammessi tramite un parser dedicato:

```scala
private def ws(using P[?]): P[Unit] =
    P(CharsWhileIn(" \t", 0))
```

In questo modo è possibile controllare il comportamento degli spazi nei diversi contesti della grammatica.

In particolare:

- gli spazi tra elementi di un pattern vengono interpretati come separatori della Mini Notation;
- gli spazi interni alle chiamate dei comandi vengono accettati in modo opzionale;
- i caratteri di nuova linea vengono gestiti esclusivamente a livello di blocco.

---

### Costruzione diretta dell'AST

Le regole del parser costruiscono direttamente gli oggetti AST senza passaggi intermedi.

Ad esempio, il parsing di uno stream combina:

```scala
generativeBlock ~
("." ~ extensionBlock).rep ~
("." ~/ visualizerBlock).?
```

e il risultato viene trasformato in:

```scala
StreamBlock(
    baseBlock,
    extensionSeq.toList,
    visualizerSeq
)
```

Lo stesso approccio viene utilizzato per generatori, trasformazioni e pattern.

---

### Parsing parametrico dei pattern

La gestione dei pattern utilizza funzioni generiche parametrizzate sul tipo di atomo.

La funzione:

```scala
pattern[T <: Atom](atom: => P[T])
```

viene riutilizzata per:

- pattern di note;
- pattern di sample;
- pattern numerici utilizzati dalle trasformazioni.

Questo evita di implementare versioni duplicate della stessa grammatica mantenendo il vincolo sul tipo di elemento ammesso.

Le strutture ricorsive:

```text
Pattern
 └── Sequence
      └── Element
```

vengono costruite tramite parser riutilizzabili:

- `pattern`;
- `sequence`;
- `element`;
- `baseElement`.

---

### Gestione degli elementi ricorsivi

Gli elementi annidati vengono gestiti attraverso parser ricorsivi.

La produzione di un elemento può riconoscere:

```scala
AtomElement
SubPatternElement
AlternationElement
```

attraverso la funzione:

```scala
baseElement
```

Questo permette di supportare pattern arbitrariamente annidati senza introdurre regole separate per ogni livello di profondità.

---

### Conservazione delle posizioni nel sorgente

Durante il parsing degli elementi vengono utilizzati i combinatori `Index` per acquisire le posizioni nel testo originale.

Le informazioni raccolte vengono salvate direttamente nei nodi AST.

Esempio:

```scala
Note(
    name,
    accidental,
    octave,
    startIndex,
    endIndex
)
```

La stessa tecnica viene applicata a:

- sample;
- configurazioni numeriche;
- silenzi;
- comandi di visualizzazione.

Le posizioni vengono poi utilizzate dall'interprete per costruire oggetti di dominio contenenti riferimenti al testo sorgente.

---

### Gestione degli errori FastParse

Il metodo pubblico del parser converte il risultato di FastParse in:

```scala
Either[String, ProgramAST]
```

In caso di errore viene utilizzata una funzione dedicata per trasformare il risultato di FastParse in un messaggio leggibile.

L'errore contiene:

- posizione del problema;
- riga e colonna;
- elemento atteso;
- indicazione del punto del sorgente.

Le regole utilizzano inoltre il metodo `opaque` per sostituire descrizioni generiche con messaggi specifici del linguaggio.

---

### Implementazione dell'interprete

L'interprete riceve un `ProgramAST` e costruisce gli oggetti appartenenti al dominio musicale.

L'implementazione utilizza principalmente:

- pattern matching sulle gerarchie dell'AST;
- trasformazioni ricorsive;
- accumulo dello stato tramite fold sulle liste.

---

### Conversione ricorsiva dei pattern

La conversione dei pattern è implementata tramite tre funzioni principali:

```scala
interpretPattern
interpretSequence
interpretElement
```

La prima gestisce il livello superiore del pattern, la seconda converte le sequenze, mentre la terza interpreta i singoli elementi.

La conversione dei nodi AST produce direttamente oggetti del dominio:

```text
AST.Element
      |
      v
Pattern dominio
```

Per evitare strutture inutilmente profonde sono presenti funzioni di costruzione "smart":

```scala
smartSequence
smartParallel
```

che evitano di creare wrapper superflui come:

```text
Sequence(List(  Sequence(List(elemento))  ))
```

quando il risultato può essere rappresentato direttamente dall'elemento.

---

### Interpretazione degli stream e delle estensioni

L'elaborazione degli stream avviene nella funzione:

```scala
applyExtensions
```

La lista delle estensioni viene attraversata tramite:

```scala
foldLeft
```

mantenendo uno stato composto da:

```scala
(
    currentBase,
    accumulatedEffects,
    foundGenerator
)
```

Questo permette di elaborare progressivamente lo stream mantenendo informazioni sulle estensioni già applicate.

---

### Implementazione degli effetti audio

Gli effetti audio vengono trasformati tramite funzioni dedicate:

```scala
interpretAudioEffect
interpretDelay
```

Ogni trasformazione viene convertita in uno o più:

```scala
Pattern[AudioEffect]
```

Il caso particolare di `Delay` viene gestito separatamente perché può produrre fino a tre effetti distinti:

- volume del delay;
- tempo del delay;
- feedback.

L'interprete accumula questi pattern di effetti durante il processamento delle estensioni.

Al termine vengono inseriti nel dominio tramite:

```scala
Pattern.WithExtensions
```

---

### Implementazione delle trasformazioni temporali

Le trasformazioni temporali vengono convertite tramite:

```scala
interpretTimeWarp
```

che produce oggetti:

```scala
PatternModifier
```

Successivamente vengono applicate costruendo:

```scala
Pattern.TimeWarp(
    modifier,
    pattern
)
```

A differenza degli effetti audio, queste trasformazioni modificano immediatamente il pattern corrente durante l'attraversamento delle estensioni.

---

### Gestione delle trasformazioni composte

Le trasformazioni `Juxtaposition` e `Offset` vengono risolte tramite una funzione comune:

```scala
interpretInnerTransformation
```

Questa funzione può restituire alternativamente:

```scala
List[Pattern[AudioEffect]]
```

oppure:

```scala
PatternModifier[AudioPayload]
```

Gli elementi ottenuti vengono poi trasformati nei corrispondenti modificatori composti:

```scala
PatternModifier.Juxtaposition
PatternModifier.Offset
```

Questo permette di riutilizzare la stessa logica di interpretazione anche all'interno delle trasformazioni annidate.

---

### Estrazione dei visualizzatori

L'estrazione dei visualizzatori è implementata separatamente tramite:

```scala
extractVisualizerRequests
```

La funzione attraversa gli stream presenti nel programma e converte ogni:

```scala
VisualizerBlock
```

nella relativa:

```scala
VisualizerRequest
```

Attualmente viene gestito:

```scala
PianoRollBlock
```

che viene convertito in una richiesta contenente l'identificatore dello stream associato.


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

### Ciclo MVU e integrazione con i sottosistemi

Il runtime conserva un solo `AppModel` e tratta la vista come una funzione di
rendering dello stato. `Update.update` è una funzione basata su pattern matching
che restituisce la coppia `(AppModel, Cmd)`: le transizioni rimangono quindi
separate dall'esecuzione degli effetti. Ogni `Cmd` implementa un trait sigillato
e riceve due dipendenze esplicite, la funzione `dispatch` e l'`AudioPlayer`.
Questo permette a un comando di reinserire il proprio risultato nel ciclo senza
conoscere la vista.

In particolare, parsing e interpretazione producono un nuovo messaggio; solo in
caso di successo vengono aggiornate insieme la timeline infinita del player e
le timeline finite dei visualizzatori. Una revisione numerica
(`timelineRevision`) identifica ogni aggiornamento accettato e impedisce
rendering duplicati o il ripristino di visualizzazioni più vecchie. Le callback
del player possono arrivare dal thread audio: `SoundCodeFrame` le inoltra al
thread JavaFX con `Platform.runLater`, evitando accessi concorrenti ai nodi
grafici. Nello stesso punto sono gestiti costruzione e arresto del backend, in
modo che il ciclo di vita delle risorse coincida con quello dell'applicazione.

### Editor e operazioni di modifica

`BlockEditorView` usa una sola `GenericStyledArea` di RichTextFX. La scelta
consente di appoggiarsi alla cronologia testuale nativa per **undo/redo** e alle
operazioni standard della text area per **copia, taglia e incolla**, mantenendo
compatibili scorciatoie, selezione e cursore. Gli stili non entrano nella
cronologia, come stabilito nella configurazione della `GenericStyledArea`: una
ricolorazione o un highlight di riproduzione non genera quindi passi di undo.
Dopo il caricamento del testo
iniziale la cronologia viene dimenticata, così il primo undo riguarda una
modifica dell'utente e non l'inizializzazione dell'editor.

Le responsabilità aggiuntive sono installate come componenti indipendenti:

- `LineNumberFactory` adatta una funzione Scala a `IntFunction[Node]`, richiesta
  dall'API Java di RichTextFX, e costruisce il gutter in modo lazy per ogni
  paragrafo;
- `SyntaxHighlighter` applica prima lo stile sintattico, riconoscendo stringhe e
  chiamate tramite espressioni regolari, poi sovrappone le posizioni attive
  ricevute dal player. Gli intervalli vengono limitati alla lunghezza corrente
  del documento per tollerare coordinate calcolate su una versione precedente;
- le modifiche ravvicinate non causano una ricolorazione per ogni carattere:
  un flag accorpa le richieste e pianifica un solo aggiornamento sul successivo
  turno JavaFX;
- il testo esposto al parser normalizza i newline, rendendo stabili gli offset
  fra piattaforme diverse.

`AutoPairingSupport` intercetta gli eventi prima dell'inserimento standard.
Parentesi, quadre, angolari e virgolette vengono inserite a coppie; se esiste
una selezione, questa viene racchiusa e resta selezionata all'interno dei nuovi
delimitatori. Digitando una chiusura già presente, il cursore la oltrepassa
senza duplicarla. Per le virgolette viene inoltre controllata la parità di
quelle precedenti al cursore, così una citazione aperta può essere chiusa
normalmente.

Il completamento è diviso fra logica pura e presentazione.
`CompletionProvider` ricava dal testo un `Context` e propone costrutti diversi
all'inizio della riga o dopo un punto, escludendo l'interno delle stringhe. Le
descrizioni provengono da `SoundCodeLanguage`, condiviso con il parser, per non
duplicare nomi, firme e snippet della DSL. `AutocompleteSupport` realizza il
popup navigabile da tastiera o mouse, mostra argomenti, overload e descrizione,
sostituisce soltanto il prefisso corrente e interpreta il marcatore `$0` per
posizionare il cursore nel punto utile dello snippet.

### Visualizzazioni incorporate

Le visualizzazioni condividono il trait `AnimatedView`, che espone solamente il
nodo radice e i controlli `play`/`stop`. `CanvasAnimatedView` implementa la
parte comune: canvas responsivo, pulizia del frame e `AnimationTimer`. Il tempo
trascorso è misurato con il clock monotono in nanosecondi e convertito in cicli
tramite `Tempo.cps`; le sottoclassi devono implementare soltanto `draw`.
`VisualizerViewFactory` effettua il pattern matching sul `VisualizerKind` e
isola così dall'editor la scelta della vista concreta.

`PianorollView` trasforma la timeline in una rappresentazione grafica più
compatta (`VisualEvent`). Note e sample sono distribuiti in corsie determinate
da etichette distinte e ordinate. A ogni frame viene calcolata soltanto la
porzione di loop visibile attorno alla testina centrale; gli eventi vengono
ripetuti nei loop necessari, traslati rispetto al ciclo corrente e disegnati
con larghezza proporzionale alla durata. L'evento attivo cambia resa grafica e
le etichette vengono limitate allo spazio disponibile.

`VisualizerOverlayManager` mantiene per ogni vista un offset sorgente. A
inserimenti e cancellazioni applica il delta agli ancoraggi successivi e
ricolloca quelli contenuti nel testo rimosso. Lo spazio verticale è riservato
tramite uno stile di paragrafo, mentre i canvas sono collocati in un overlay
separato e ritrasformati dalle coordinate schermo a quelle locali durante
scroll e resize. In questo modo i visualizzatori sembrano appartenere
all'editor senza essere caratteri del documento e senza interferire con
selezione, copia-incolla o undo.

### Vista principale, feedback e tema

`MainView` compone toolbar, editor ed `ErrorBanner` e traduce pulsanti e
scorciatoie (`Shortcut+Enter`, `Shortcut+.` e `Shortcut+U`) in messaggi, senza
invocare direttamente parser o player. Durante il rendering memorizza l'ultimo
stato di riproduzione per avviare o fermare le animazioni soltanto quando il
valore cambia. Il banner degli errori gestisce visibilità e layout insieme e
usa transizioni di fade; la chiusura genera un messaggio solo al termine
dell'animazione.

Colori, tipografia, dimensioni e stringhe CSS sono raccolti in `UITheme`. Oltre
a mantenere coerenti editor, gutter, popup, scrollbar, visualizzatori ed
errori, questa centralizzazione evita che le classi comportamentali conoscano
valori grafici specifici.
