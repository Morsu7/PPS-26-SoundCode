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

## Modellazione del dominio

Invece di considerare la musica come una semplice lista di note da suonare una dopo l'altra, il sistema la descrive come l'insieme di piccoli blocchi che possono essere combinati tra loro. In questo modo anche pattern complessi, poliritmie e variazioni nascono semplicemente dalla composizione di elementi più semplici.

### I pattern

Il tipo `Pattern[T]`, che rappresenta un qualsiasi pattern musicale.

Un pattern può assumere diverse forme, ognuna con un significato preciso:

- **`Atom[T]`**: rappresenta l'elemento più semplice, ad esempio una singola nota, un sample o un valore numerico.
- **`Sequence[T]`**: esegue più pattern uno dopo l'altro, permettendo di costruire melodie e ritmi.
- **`Parallel[T]`**: esegue più pattern contemporaneamente, utile per creare accordi o polifonie.
- **`Alternation[T]`**: alterna pattern diversi a ogni ripetizione del ciclo, rendendo semplice creare variazioni.
- **`TimeWarp[T]`**: applica modifiche temporali al pattern, come accelerazioni, rallentamenti, ritardi o inversioni.
- **`WithExtensions`**: aggiunge effetti audio al pattern, mantenendo separata la struttura musicale dagli effetti applicati.

Questa organizzazione rende il codice più semplice da estendere e permette al motore di sapere sempre come interpretare ogni tipo di pattern.

## Tipi del dominio

Anche i concetti fondamentali della musica sono rappresentati con tipi dedicati.

Ad esempio:

- `Note` rappresenta una nota musicale.
- `Sample` identifica un campione audio.
- `AbsoluteTime` rappresenta un istante preciso del clock del sistema.

Avere tipi distinti evita molti errori durante lo sviluppo. Ad esempio, non è possibile confondere un istante assoluto del clock con una durata musicale o passare un valore nel formato sbagliato a una funzione.

Internamente questi tipi rimangono molto leggeri e non introducono costi aggiuntivi durante l'esecuzione, ma rendono il codice più leggibile e riducono la possibilità di errori.

### Il Dominio Temporale

La gestione del tempo è il problema più critico in un software musicale. Affidarsi ai classici numeri con la virgola (Double) per dividere i battiti porterebbe inevitabilmente a errori di arrotondamento (es. dividere un ciclo in terzine creerebbe periodi di **0.3333...** che non si ricongiungono mai esattamente a **1.0**).

Per garantire una precisione ritmica assoluta ed evitare la propagazione di queste deviazioni matematiche, l'architettura modella il tempo suddividendolo in quattro strati di astrazione, ognuno con una precisa responsabilità:

* **Tempo Logico (`Fraction`):** La timeline musicale è calcolata tramite una classe custom implementata come numero razionale. L'uso esclusivo di frazioni esatte evita completamente la deriva aritmetica e i difetti di arrotondamento tipici dei numeri in virgola mobile (`Double`), che altrimenti corromperebbero i calcoli di terzine, tuple irregolari o combinazioni di distorsioni temporali. Ogni frazione viene normalizzata all'istanziazione (denominatore positivo e riduzione matematica tramite Massimo Comune Divisore), garantendo confronti e operazioni algebricamente perfetti.
* **Tempo Musicale (`Interval`):** Costruito componendo due frazioni (inizio e fine), modella una precisa porzione della timeline. Costituisce la "finestra geometrica" che viene propagata e partizionata ricorsivamente dai resolver durante la discesa lungo i Pattern.
* **Tempo Fisico (`Tempo`):** Rappresenta il livello di traduzione. Terminata la risoluzione logica dei Pattern, la classe `Tempo` mappa gli intervalli metrico-frazionari in durate fisiche (millisecondi) applicando il parametro di velocità CPS (*Cycles Per Second*).
* **Tempo Assoluto (`AbsoluteTime`):** É isolato tramite un *Opaque Type* (`opaque type AbsoluteTime = Long`) che incapsula il valore di `System.currentTimeMillis()`. Questa astrazione *a costo zero* previene, già a tempo di compilazione, la possibilità di mescolare accidentalmente timestamp di sistema con durate relative.


Questa stratificazione impone una rigorosa *Separation of Concerns* (separazione delle responsabilità). Il dominio matematico e compositivo (Frazioni e Intervalli) può essere calcolato, testato e manipolato in modo puro, rimanendo totalmente de-contestualizzato sia dalla velocità di riproduzione fisica, sia dalla sincronizzazione con il clock del sistema operativo.

---

## Il Dispatcher e i Pattern di Risoluzione

Invece di implementare la logica di valutazione direttamente all'interno dei nodi (violando il principio di singola responsabilità e accoppiando i dati al comportamento), l'algoritmo di risoluzione è centralizzato nel modulo `PatternResolver`, che funge da **dispatcher**.

Sfruttando le capacità di pattern matching di Scala su strutture ADT chiuse, il dispatcher instrada l'esecuzione verso resolver specifici. L'utilizzo di parametri contestuali (`using`) consente di propagare in modo trasparente la finestra temporale da analizzare:

```scala
object PatternResolver:
  def resolve[T](pattern: Pattern[T])(using timeWindow: Interval): List[ScheduledEvent[T]] =
    // Ottimizzazione preventiva: scarta i cicli vuoti o malformati
    if timeWindow.start >= timeWindow.end then Nil
    else pattern match
      // Il compilatore verifica che tutti i rami del sealed trait Pattern[+T] siano gestiti
      case a @ Pattern.Atom(_)          => AtomResolver.resolve(a)
      case s @ Pattern.Sequence(_)      => SequenceResolver.resolve(s)
      case p @ Pattern.Parallel(_)      => ParallelResolver.resolve(p)
      case alt @ Pattern.Alternation(_) => AlternationResolver.resolve(alt)
      case tw @ Pattern.TimeWarp(_, _)  => TimeWarpResolver.resolve(tw)
      case p: Pattern.WithExtensions    => WithExtensionsResolver.resolve(p)
```

Per rendere l'API più ergonomica e favorire una scrittura fluida del codice, viene inoltre esposto un *extension method* che nasconde il passaggio esplicito del parametro contestuale:

```scala
extension [T](pattern: Pattern[T])
  def resolve(using timeWindow: Interval): List[ScheduledEvent[T]] =
    PatternResolver.resolve(pattern)
```

Questo approccio architetturale garantisce un'estrema modularità: ogni resolver implementa esclusivamente la geometria di un singolo problema (composizione, traslazione, inversione), rendendo il motore facilmente estensibile in futuro senza modificare le strutture dati esistenti.

### AtomResolver
Costituisce il caso base della ricorsione. Non applica trasformazioni: riceve la finestra temporale (`timeWindow`) e genera un singolo evento atomico in cui la durata originale (`whole`) e la porzione visibile (`part`) coincidono esattamente con l'intervallo ricevuto.

### SequenceResolver
Realizza la suddivisione ritmica lineare dividendo il ciclo in *n* slot. Il funzionamento è lo **zoom geometrico**: anziché passare semplicemente la finestra tagliata, proietta l'intervallo nello spazio temporale del nodo figlio. Questo permette al pattern innestato di calcolare la sua durata logica completa (`whole`). Successivamente, le coordinate vengono riportate nel sistema del padre (`mapTime`) e tagliate sulla finestra effettivamente osservabile (`clipTo`).

### ParallelResolver e AlternationResolver
* **ParallelResolver:** Gestisce la polifonia inoltrando la medesima finestra temporale a tutti i figli, senza alterazioni spaziali, concatenandone i risultati.
* **AlternationResolver:** Crea variazioni cicliche selezionando un ramo tramite la funzione modulo sull'indice assoluto (`cycle.abs % elements.size`). Applica poi un offset temporale per riallineare metricamente il pattern generato sulla timeline globale.

### TimeWarpResolver

Gestisce tutte le distorsioni geometriche del tempo (`fast`, `slow`, `late`, `early`, `reverse`). Una particolarità architetturale dell'engine è che i modificatori temporali sono a loro volta dei pattern dinamici (ad esempio, è possibile alternare ciclicamente la velocità di esecuzione tra 1 e 2).

Per gestire questa dinamicità in modo pulito ed evitare duplicazioni, il concetto di distorsione è astratto in un unico flusso geometrico. Il motore non altera i singoli eventi *a posteriori*, ma deforma lo spazio di osservazione *a priori*, operando in due fasi simmetriche:

1. **Proiezione (`zoomIn`):** Converte la finestra reale in una "finestra distorta" (compressa, dilatata o traslata). Il nodo figlio viene interrogato come se si trovasse in questo nuovo spaziotempo.
2. **Ripristino (`zoomOut`):** Applica la trasformazione geometrica inversa agli eventi generati, ricollocandoli sulle coordinate della timeline globale.

Questo approccio è riassunto dal *core loop* del resolver (qui semplificato omettendo i dettagli implementativi sui limiti dei cicli):

```scala
for
  // 1. Calcola il valore puntuale del modificatore (es. la velocità corrente)
  paramEvent <- parameter.resolve
  currentValue = Fraction(paramEvent.value)
  
  // 2. Proiezione: deforma la finestra temporale di esplorazione
  warpedWindow = zoomIn(timeWindow, currentValue)
  
  // 3. Risoluzione: valuta il pattern figlio nello spazio temporale alterato
  innerEvent <- innerPattern.resolve(using warpedWindow)
  
yield
  // 4. Ripristino: riallinea l'evento prodotto sulla timeline globale
  val finalEvent = zoomOut(innerEvent, currentValue)
  
  // 5. Traccia l'origine testuale per l'highlighting dinamico
  finalEvent.trackModifierPositions(paramEvent)
```

Lo stesso rigore geometrico viene applicato ai modificatori statici privi di parametri, come il **reverse**. Non potendo usare l'astrazione dinamica, il reverse applica direttamente la specchiatura matematica rispetto ai confini del ciclo:

\[
t' = cycleStart + cycleEnd - t
\]

Il resolver inietta questa funzione \( f(t) \) sia per deformare la finestra di interrogazione iniziale, sia per mappare al contrario le coordinate (**whole** e **part**) degli eventi restituiti dal figlio, garantendo una simmetria perfetta.

Infine, come evidenziato dalla tracciatura delle posizioni, il resolver accumula in background l'origine sintattica dei parametri. Questo metadato è essenziale per l'interfaccia grafica: consente alla UI di evidenziare (**highlighting**) l'esatto blocco di codice in esecuzione a runtime, mantenendo l'engine temporale rigorosamente isolato e indipendente dal livello di presentazione.

#### Composizione e Ricorsione Strutturale (Repetition, Juxtaposition, Offset)

Ci sono dei modificatori di natura strutturale, che non applicano semplici deformazioni ma alterano la topologia stessa del pattern:

* **Repetition (`ply`):** Anziché deformare la finestra di interrogazione, intercetta l'evento generato e ne suddivide dinamicamente la durata originale (`whole`) in **n** frammenti equi-spaziati. Il risultato è una sequenza ritmica perfettamente isocrona, incastonata nello spazio logico della nota di partenza.
* **Juxtaposition (`jux`) e Offset (`off`):** Dimostrano la potenza ricorsiva dell'engine operando attraverso la riscrittura dell'albero sintattico. Anziché manipolare i singoli eventi matematicamente, essi trasformano il pattern a runtime creando nuovi nodi. `Offset`, ad esempio, costruisce un nodo `Parallel` che accoppia il pattern originale con una sua versione traslata tramite `Late(offset)`. Delegando ricorsivamente la risoluzione a questo nuovo sotto-albero, il sistema ottiene "gratuitamente" la corretta traslazione geometrica e la propagazione delle `modifierPositions`, garantendo un'assoluta coerenza architetturale senza alcuna duplicazione logica.

### WithExtensionsResolver

Gestisce l'associazione e la propagazione degli effetti audio e dei parametri di configurazione. Per attuare questa logica, il resolver opera in tre fasi distinte:

1. **Risoluzione primaria:** Valuta il pattern musicale di base all'interno della finestra temporale corrente.
2. **Valutazione delle estensioni:** Risolve i pattern associati alle estensioni limitatamente alla porzione attiva (`part`).
3. **Fusione e risoluzione dei conflitti:** Unisce i risultati applicando una politica di sovrascrittura gerarchica (tramite il metodo `overriddenBy`).

In caso di parametri duplicati (ad esempio due effetti `gain` concorrenti nello stesso intervallo), l'ultimo valore sovrascrive il precedente in base a regole di precedenza strutturate. Questo design mantiene l'engine temporale completamente agnostico rispetto al backend di sintesi audio: il resolver si limita a produrre un set strutturato e ordinato di estensioni, demandando ogni interpretazione sonora al livello sottostante.

---

## Scheduling e Concorrenza nel Player

Riprendendo la distinzione tra le modalità di generazione della timeline, l'implementazione tecnica adotta strategie differenti a seconda del contesto d'uso:

### 1. Timeline Limitata (Bounded)
Utilizzata principalmente dalla GUI per la rappresentazione statica, calcola la finestra temporale minima sfruttando il **minimo comune multiplo** delle periodicità dei pattern, restituendo una lista finita di eventi.

### 2. Timeline Infinita (`LazyList`)
Per il live coding e la riproduzione in tempo reale, lo scheduler sfrutta la valutazione pigra di Scala per produrre una sequenza infinita valutata un ciclo alla volta:

```scala
def generateInfiniteTimeline(...): LazyList[...] =
  def loop(nCycle: Int): LazyList[....] =
    given Interval = Interval(Fraction(nCycle), Fraction(nCycle + 1))
    val cycleEvents =patterns
        .flatMap(_.resolve)
        .sortBy(_.part.start.toDouble)
    LazyList.from(cycleEvents) #::: loop(nCycle + 1)

  loop(0)
```

Grazie alla concatenazione differita (`#:::`), in memoria risiede unicamente il ciclo corrente, garantendo un'occupazione di memoria costante anche durante sessioni di riproduzione prolungate.

## L'AudioPlayer e la Sincronizzazione in Tempo Reale

L'`AudioPlayer` rappresenta il componente incaricato dell'esecuzione effettiva della timeline. L'esecuzione avviene su un thread in background a bassa latenza che esegue un ciclo di polling continuo, invocando ad ogni iterazione il metodo `tick` con risoluzione al millisecondo.

### Gestione dello Stato e Thread Safety

Per supportare il *live coding* (ovvero la sostituzione a caldo dei pattern in esecuzione senza interrompere o bloccare il flusso audio) l'architettura adotta un pattern di **stato immutabile racchiuso in un blocco di sincronizzazione (`lock.synchronized`)**.

Tutti i dati critici di riproduzione (la `LazyList` degli eventi, il timestamp di avvio, la mappa degli highlight attivi) sono racchiusi all'interno di una case class privata (`PlayerState`) e aggiornati in modo atomico:

```scala
private case class PlayerState(
    eventStream: LazyList[ScheduledEvent[AudioPayload]] = LazyList.empty,
    firstTickTimeMs: Option[AbsoluteTime] = None,
    activeHighlights: Map[TextPosition, AbsoluteTime] = Map.empty,
    currentHighlightSet: Set[TextPosition] = Set.empty
)
```
## Il Ciclo di Elaborazione (*tick*)

Questo approccio previene in modo rigoroso qualsiasi **condizione di gara** (*race condition*) tra il thread che ricompila il codice (`updateTimeline`) e il thread che consuma gli eventi audio.

Ad ogni *tick* del player, lo stato viene valutato eseguendo tre macro-operazioni sequenziali.

### 1. Pulizia degli Highlight Scaduti

Vengono filtrate e rimosse tutte le evidenziazioni testuali il cui termine temporale (`expireAtMs`) è inferiore al tempo corrente (`now`), ripulendo la UI in modo incrementale.

### 2. Consumo degli Eventi e Filtraggio Anti-Duplicazione

Lo stream viene partizionato tramite lo `span` in base al tempo reale calcolato (`firstPerformance + tempo.offsetMs(...)`).

Per impedire che una nota venga riprodotta due volte quando attraversa il confine tra due cicli consecutivi, viene applicato il seguente filtro strutturale:

```scala
toProcess.filter(e => e.part.start == e.whole.start)
```

L'evento viene così inviato al backend solamente nell'istante della sua effettiva nascita, pur mantenendo disponibile la sua durata completa (`whole`).

### 3. Dispatch al Backend e Notifica della UI

Il player inoltra il payload al generico `AudioBackend`, insieme alla durata calcolata e alle eventuali estensioni.

Parallelamente:

- raccoglie tutte le posizioni testuali associate (`position` dei payload e `modifierPositions`);
- calcola la scadenza temporale degli highlight;
- notifica asincronamente la GUI tramite la callback `onHighlightChange` soltanto quando il set delle evidenziazioni risulta effettivamente modificato.

## Disaccoppiamento Architetturale

Questa organizzazione garantisce un isolamento completo tra:

- l'engine di scheduling e calcolo logico;
- il backend di sintesi e riproduzione audio;
- il livello di presentazione e interazione grafica.

La logica musicale risulta così completamente indipendente sia dalla tecnologia audio sottostante sia dal framework grafico impiegato.

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

La velocity non fa parte dell'identità della voce, essendo una proprietà della singola nota: così note con gain diverso condividono il canale senza modificarsi a vicenda.

L'invio di `noteOn` è sincrono e breve; il corrispondente `noteOff` viene programmato su un `ScheduledExecutorService` daemon. Il thread del player può così continuare a estrarre eventi senza restare bloccato per la durata delle note. Per evitare che una nota ribattuta venga troncata, il motore mantiene un solo `noteOff` pendente per coppia (canale, nota): se la stessa nota riparte prima della fine, il `noteOff` precedente viene annullato e riprogrammato (in MIDI un `noteOff` spegne l'intero numero di nota sul canale). Alla chiusura, executor e sintetizzatore sono rilasciati in modo tollerante agli errori.

# Interfaccia utente — Giacomo Biagioni

## Contributo personale e collaborazioni

La progettazione e l'implementazione dell'interfaccia utente sono state curate
principalmente da Giacomo Biagioni. Il contributo comprende la struttura delle
viste, l'editor basato su RichTextFX, gli strumenti di assistenza alla
scrittura, la gestione del tema, il feedback degli errori, gli highlight
durante la riproduzione e l'integrazione dei visualizzatori nell'editor, insieme
ai relativi test della UI.

Le parti di collegamento con gli altri sottosistemi sono state realizzate in
collaborazione con i rispettivi responsabili: con Cristian Morbidelli per
l'integrazione di parser e interprete, con Federico Morsucci per timeline,
scheduling e notifiche degli elementi in esecuzione, e con Tommaso Remedi per
il collegamento del controllo del volume al backend MIDI.

La UI aggiorna i visualizzatori soltanto quando l'analisi del codice termina con
successo. Il valore `timelineRevision` consente di riconoscere una nuova
timeline, evitando sia rendering duplicati sia il ripristino di una
visualizzazione precedente. Durante la riproduzione, le callback del player
possono provenire dal thread audio; `SoundCodeFrame` le trasferisce quindi sul
thread JavaFX tramite `Platform.runLater`, così che i nodi grafici siano
modificati in sicurezza. `SoundCodeFrame` gestisce inoltre l'avvio e l'arresto
del backend insieme al ciclo di vita dell'applicazione.

## Editor e operazioni di modifica

`BlockEditorView` si basa su una singola `GenericStyledArea` di RichTextFX e ne
riutilizza le funzionalità native per annullamento, ripristino, copia, taglio e
incolla. In questo modo rimangono disponibili anche le consuete scorciatoie da
tastiera e la gestione standard di cursore e selezione. La cronologia registra
soltanto le modifiche al testo: la colorazione della sintassi e le
evidenziazioni della riproduzione non introducono quindi operazioni da
annullare. Al termine dell'inizializzazione la cronologia viene azzerata, così
il primo annullamento riguarda sempre un'azione dell'utente.

Attorno all'area di testo sono integrate alcune funzionalità aggiuntive:

- `LineNumberFactory` fornisce a RichTextFX la funzione Java
  `IntFunction[Node]` necessaria per generare, su richiesta, il numero associato
  a ciascuna riga;
- `SyntaxHighlighter` riconosce stringhe e chiamate tramite espressioni regolari
  e applica al testo esclusivamente la relativa colorazione;
- le richieste di ricolorazione prodotte da modifiche ravvicinate vengono
  accorpate e una sola operazione viene inserita nella coda degli eventi
  JavaFX;
- prima di essere inviato al parser, il testo viene normalizzato per mantenere
  coerenti le posizioni dei caratteri sulle diverse piattaforme.

## Highlight degli elementi in esecuzione

`PlaybackHighlightOverlayManager` traduce ogni `TextPosition` ricevuta dal
player in un rettangolo collocato nell'overlay dell'editor. Posizione e
dimensioni sono calcolate a partire dai bounds forniti da RichTextFX; durante
scroll, resize e modifiche del layout i rettangoli vengono riposizionati
rispetto alle nuove coordinate dell'editor.

Ogni aggiornamento confronta il nuovo insieme di posizioni con quello già
visualizzato, aggiungendo e rimuovendo solamente le differenze. I rettangoli
rimossi vengono conservati in un pool e riutilizzati per gli highlight
successivi, evitando continue allocazioni durante la riproduzione. Quando
l'utente modifica il testo dopo un aggiornamento valido, gli highlight sono
temporaneamente disabilitati perché le coordinate ricevute dal player fanno
riferimento alla precedente versione del sorgente.

`AutoPairingSupport` gestisce l'inserimento dei delimitatori. Parentesi tonde,
quadre e angolari, insieme alle virgolette, vengono completate automaticamente;
se è presente una selezione, questa viene racchiusa tra i due delimitatori.
Quando il carattere di chiusura è già presente, il cursore lo oltrepassa senza
inserirne un duplicato. Nel caso delle virgolette viene considerato anche il
contesto precedente al cursore, in modo da distinguere l'apertura di una coppia
dalla chiusura di una stringa.

Il completamento automatico mantiene separate la ricerca dei suggerimenti e la
loro visualizzazione. `CompletionProvider` analizza il contesto corrente e
propone costrutti diversi all'inizio di una riga o dopo un punto, senza
intervenire all'interno delle stringhe. Nomi, firme, descrizioni e frammenti di
codice provengono da `SoundCodeLanguage`, condiviso con il parser.
`AutocompleteSupport` presenta i risultati in un popup navigabile tramite
tastiera o mouse, sostituisce soltanto il prefisso corrente e usa il marcatore
`$0` per collocare il cursore nel punto previsto dal frammento inserito.

## Visualizzazioni incorporate

Le visualizzazioni condividono il trait `AnimatedView`, che espone solamente il
nodo radice e i controlli `play`/`stop`. `CanvasAnimatedView` implementa la
parte comune: canvas responsivo, pulizia del frame e registrazione presso un
timer condiviso. Un unico `AnimationTimer` mantiene l'insieme delle
`CanvasAnimatedView` in esecuzione e, a ogni frame, inoltra a ciascuna lo stesso
timestamp. Il timer viene avviato quando si registra la prima vista e arrestato
quando l'insieme torna vuoto.

Ogni visualizzatore conserva autonomamente il tempo accumulato. `stop` mette in
pausa l'animazione e il successivo `play` la fa riprendere dal punto in cui era
stata interrotta, senza influenzare le altre viste. Quando viene aggiornata la
timeline, invece, i visualizzatori vengono ricreati e le animazioni ripartono
da zero.

Il tempo trascorso viene misurato in nanosecondi tramite `System.nanoTime()`,
un clock monotono che non risente delle modifiche all'orologio di sistema, e
convertito in cicli tramite `Tempo.cps`. Il timestamp fornito da
`AnimationTimer` viene utilizzato per limitare la frequenza di ridisegno; le
sottoclassi devono implementare soltanto `draw`.
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

## Vista principale, feedback e tema

`MainView` compone toolbar, editor ed `ErrorBanner` e traduce pulsanti e
scorciatoie (`Shortcut+Enter`, `Shortcut+.` e `Shortcut+U`) in messaggi, senza
invocare direttamente parser o player. Durante il rendering memorizza l'ultimo
stato di riproduzione per avviare o fermare le animazioni soltanto quando il
valore cambia. Il banner degli errori gestisce visibilità e layout insieme e
usa transizioni di fade; la chiusura genera un messaggio solo al termine
dell'animazione.

Il controllo del volume è realizzato con uno `Slider` compreso fra 0 e 100. Per
evitare aggiornamenti ridondanti durante il trascinamento, `MainView` invia la
richiesta quando l'utente rilascia il mouse; il comando `SetVolume` inoltra
quindi il valore ad `AudioPlayer`, che lo propaga al backend audio.

Colori, tipografia, dimensioni e stringhe CSS sono raccolti in `UITheme`. Dal
punto di vista implementativo, il tema carica `CascadiaCode-Regular.ttf` dalle
risorse e ne inserisce la famiglia nelle regole CSS. Se il caricamento fallisce
viene selezionata la famiglia di sistema `Monospaced`.

## Test dell'interfaccia

I test della UI usano ScalaTest e condividono il supporto `UITestSupport`.
Poiché i componenti JavaFX possono essere creati e interrogati soltanto sul
JavaFX Application Thread, il metodo `onFxThread` avvia il toolkit quando
necessario, accoda il corpo del test tramite `Platform.runLater` e ne attende il
completamento. Il risultato o l'eventuale eccezione vengono quindi riportati
al thread che esegue la suite.

I componenti verificati sono istanze reali dei controlli JavaFX, mentre vengono
simulati gli input dell'utente e osservati gli effetti prodotti. Nei test di
`MainView`, per esempio, i pulsanti della toolbar vengono attivati tramite
`fire()` e una funzione di dispatch registra i messaggi emessi; sono inoltre
verificati lo slider del volume e gli shortcut installati nella scena. Per le
funzionalità di editing, helper locali come `installedEditor` costruiscono una
`InlineCssTextArea`, installano il comportamento da verificare e impostano testo
e cursore iniziali. 

`InlineCssTextArea` è una specializzazione di
`GenericStyledArea` e ne conserva l'API e i comportamenti rilevanti per
l'editing, utilizzando stringhe CSS inline come rappresentazione degli stili.
Può quindi sostituire l'editor configurato dall'applicazione nei test isolati,
senza richiedere la costruzione dell'intera vista. L'autopairing viene
esercitato tramite eventi di tastiera sintetici, mentre il completamento viene
invocato direttamente sull'editor.
