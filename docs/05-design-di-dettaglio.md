---
layout: default
title: Design di dettaglio
---

## Parser e interprete — Cristian Morbidelli

### Dalla sintassi alla semantica

Il sottosistema linguistico è diviso in due passaggi. Il **parser** riconosce la grammatica e produce un albero sintattico astratto (AST); l'**interprete** elimina i dettagli puramente grammaticali e costruisce pattern del dominio.

```mermaid
flowchart LR
    SRC[Testo DSL] -->|analisi sintattica| AST[AST con posizioni sorgente]
    AST -->|interpretazione| PAT[Pattern musicali]
    AST -->|estrazione| VR[Richieste di visualizzazione]
```

La separazione tra AST e dominio evita che l'engine debba conoscere parentesi, virgolette, nomi dei comandi o altre convenzioni testuali. Allo stesso tempo, l'AST conserva gli indici degli elementi nel sorgente: durante l'interpretazione tali coordinate vengono trasferite ai payload, consentendo alla UI di evidenziare ciò che è in riproduzione senza analizzare nuovamente il testo.

### Modello sintattico

Un programma è composto da stream. Ogni stream contiene:

- un generatore di note o suoni;
- una sequenza ordinata di estensioni;
- un'eventuale richiesta di visualizzazione.

I pattern sintattici rappresentano sequenze, sovrapposizioni, gruppi, alternanze e modificatori di velocità. Le trasformazioni sono modellate separatamente dai generatori, così il parser può verificare quali forme sono ammissibili in ogni punto della DSL.

### Regole di interpretazione

L'interprete applica le seguenti regole concettuali:

- una nota, una percussione o un silenzio diventa un atomo del dominio;
- elementi consecutivi diventano una sequenza;
- sequenze separate da virgole diventano livelli paralleli;
- gli elementi tra parentesi angolari diventano un'alternanza ciclica;
- le trasformazioni temporali avvolgono il pattern al quale si applicano;
- generatori aggiuntivi ed effetti diventano estensioni del pattern base;
- il comando di visualizzazione genera una richiesta associata allo stream, ma non modifica il suo contenuto musicale.

Il risultato dell'interpretazione è quindi una lista di pattern eseguibili e una lista indipendente di richieste grafiche. Un errore sintattico interrompe questa trasformazione e viene restituito al coordinamento applicativo; la timeline valida precedente può così rimanere invariata.

## Engine — Federico Morsucci

### Algebra dei pattern

Il nucleo dell'engine opera su un'algebra ricorsiva di pattern. I casi essenziali sono:

- **atomo**, che contiene un singolo valore;
- **sequenza**, che divide l'intervallo disponibile fra i figli;
- **parallelo**, che assegna lo stesso intervallo a più figli;
- **alternanza**, che sceglie il ramo in funzione del ciclo corrente;
- **estensione**, che associa al pattern principale effetti o configurazioni;
- **trasformazione temporale**, che modifica la collocazione o l'ordine degli eventi.

Questa rappresentazione esprime *che cosa* deve essere suonato senza fissare anticipatamente istanti reali o tecnologia audio. Inoltre permette di comporre i costrutti in modo uniforme: un pattern complesso è sempre ottenuto combinando pattern più piccoli.

### Risoluzione temporale

La risoluzione valuta un pattern rispetto a una finestra temporale espressa in cicli. Un dispatcher individua la natura del pattern e delega la regola temporale al resolver corrispondente. Tutti i resolver rispettano lo stesso contratto: ricevono un pattern e una finestra, restituiscono eventi collocati o intersecati con quella finestra.

```mermaid
flowchart LR
    P[Pattern] --> R[Resolver specializzato]
    W[Finestra in cicli] --> R
    R --> E[Eventi schedulati]
    E --> B[Timeline finita]
    E --> I[Timeline ciclica lazy]
```

Il tempo musicale è rappresentato con frazioni, evitando errori cumulativi quando intervalli e trasformazioni vengono composti. Ogni evento schedulato distingue:

- l'intervallo completo a cui appartiene;
- la porzione che cade nella finestra osservata;
- il payload da eseguire;
- le estensioni applicate.

La distinzione fra intervallo completo e porzione visibile è necessaria quando un evento attraversa il confine della finestra: la visualizzazione può mostrarne solo la parte pertinente, mentre la durata musicale resta quella originaria.

### Scheduling e riproduzione

Lo scheduler offre due viste dello stesso risultato. Le timeline finite coprono la lunghezza naturale dei pattern e sono adatte alle visualizzazioni; la timeline lazy genera invece i cicli progressivamente ed è adatta alla riproduzione continua senza materializzare una sequenza infinita.

Il player converte il tempo logico in millisecondi solo al confine con l'esecuzione. Durante la riproduzione consuma gli eventi maturi, li inoltra al backend audio e mantiene l'insieme delle posizioni sorgente attive. L'aggiornamento del programma sostituisce la timeline e ne reimposta il riferimento temporale; play e stop controllano il consumo della timeline senza coinvolgere parser e resolver.

## Audio e MIDI — Tommaso Remedi

### Separazione tra scheduling e produzione sonora

L'engine non invia direttamente messaggi MIDI. Il player comunica con un **backend audio** mediante eventi già risolti; il backend traduce invece payload ed estensioni in richieste sonore. Un ulteriore **motore audio** incapsula il sintetizzatore e il ciclo di vita delle sue risorse.

```mermaid
flowchart LR
    EV[Evento e durata] --> BE[Backend audio]
    BE -->|nota configurata| AE[Motore audio]
    BE -->|percussione| AE
    AE --> SYN[Sintetizzatore MIDI]
```

Questa doppia astrazione permette di provare la temporizzazione con un backend controllato e di provare la traduzione MIDI senza avviare l'intera applicazione.

### Traduzione musicale

Il backend distingue tre casi:

- le note vengono convertite in numeri MIDI e associate a uno strumento General MIDI;
- i nomi dei suoni percussivi vengono convertiti nelle note del canale percussionistico;
- i silenzi non producono comandi sonori.

Le estensioni udibili vengono trasformate in parametri della nota o controlli di canale. Il guadagno determina la velocity; pan, riverbero e filtro passa-basso sono approssimati tramite controlli MIDI. Gli effetti privi di una corrispondenza MIDI adeguata restano nel modello, senza introdurre dipendenze nell'engine.

I canali melodici sono assegnati per voce, dove una voce identifica una combinazione compatibile di strumento e controlli. In questo modo note simultanee con configurazioni diverse non si sovrascrivono a vicenda, mentre il canale General MIDI dedicato alle percussioni resta riservato.

L'avvio di una nota è immediato, mentre la sua terminazione è pianificata in modo non bloccante. Se il sintetizzatore non è disponibile, un motore silenzioso mantiene operativo il resto dell'applicazione: parsing, scheduling, interfaccia e test non dipendono quindi dalla presenza di un dispositivo audio.

## Interfaccia utente — Giacomo Biagioni

### Coordinamento tramite Model–View–Update

Come descritto nell'architettura, la UI viene coordinata tramite MVU. I
messaggi usati nel progetto si possono dividere in tre gruppi:

- richieste dell'utente, ad esempio aggiornare il codice, avviare la
  riproduzione o fermarla;
- risultati delle operazioni, come l'esito del parsing e le nuove timeline;
- aggiornamenti che arrivano durante la riproduzione, in particolare le
  posizioni del codice da evidenziare.

In questo modo la vista invia solo messaggi e non deve chiamare direttamente il
parser o il player. Anche i risultati ritornano nello stesso flusso e vengono
gestiti dalla funzione di aggiornamento. I casi principali sono:

| Messaggio | Variazione del modello | Operazione richiesta |
|---|---|---|
| Aggiornamento del codice | Nessuna variazione immediata | Analisi del testo |
| Analisi riuscita | Rimozione dell'errore precedente | Costruzione e sostituzione delle timeline |
| Analisi fallita | Aggiornamento della diagnostica | Nessuna |
| Play o stop | Aggiornamento dello stato di riproduzione | Avvio o arresto del player |
| Nuove timeline | Timeline, visualizzazioni e revisione vengono aggiornate insieme | Nessuna |
| Nuove posizioni attive | Sostituzione delle evidenziazioni correnti | Nessuna |

Il testo presente nell'editor non coincide necessariamente con lo stato
musicale in esecuzione. L'utente può continuare a scrivere, ma timeline e
visualizzazioni vengono sostituite solo quando preme Update e il nuovo codice è
valido. Se il parsing fallisce viene mostrato l'errore e la timeline precedente
rimane disponibile. Il modello contiene anche un numero di revisione, utile
alla vista per capire che è arrivato un nuovo aggiornamento anche quando le
timeline ottenute sono uguali alle precedenti.

Le timeline per il player e quelle finite usate dai visualizzatori vengono
prodotte nello stesso aggiornamento. Questo evita che audio e piano roll si
riferiscano a due versioni diverse del programma.

### Editor come composizione di responsabilità

L'editor è il componente principale della UI, ma non contiene direttamente
tutte le funzionalità. La parte di base gestisce testo, cursore, selezione e
cronologia delle modifiche. Numeri di riga, syntax highlighting, autocomplete
e inserimento automatico delle parentesi sono aggiunti da componenti separati.
Anche i visualizzatori vengono mantenuti separati dal testo: sono mostrati
sopra l'editor e collegati alla posizione del comando che li ha richiesti.

```mermaid
flowchart TB
    EDITOR[Editor del sorgente]
    EDITOR --- TEXT[Testo e selezione]
    EDITOR --- AID[Supporto alla scrittura]
    EDITOR --- FEEDBACK[Feedback visivo]

    AID --> SYNTAX[Colorazione sintattica]
    AID --> COMPLETE[Completamento]
    AID --> PAIR[Delimitatori bilanciati]

    FEEDBACK --> ERROR[Diagnostica]
    FEEDBACK --> ACTIVE[Elementi in riproduzione]
    FEEDBACK --> ROLL[Piano roll incorporato]
```

La separazione è utile perché queste parti si aggiornano in momenti diversi. La
colorazione segue subito le modifiche al testo, gli errori dipendono dall'ultimo
Update, mentre gli highlight cambiano durante la riproduzione. Il piano roll
riceve invece una timeline già calcolata dall'engine e si occupa solamente di
disegnarla.

### Coerenza del feedback visivo

Il parser salva le posizioni degli elementi nel testo e queste coordinate
arrivano fino alla UI. Servono sia per evidenziare la nota o il sample in
riproduzione, sia per inserire il piano roll vicino al relativo comando.

Il problema è che le coordinate appartengono alla versione di codice usata per
creare la timeline. Se l'utente modifica il testo prima di fare un nuovo
Update, gli highlight precedenti vengono quindi nascosti, perché potrebbero
indicare caratteri sbagliati. Per i visualizzatori viene invece aggiornato
l'offset dell'ancoraggio quando vengono inseriti o rimossi dei caratteri, in
modo che restino vicino alla riga corretta.

I diversi tipi di feedback hanno quindi compiti distinti:

- la colorazione aiuta a leggere il codice, ma non dice se è valido;
- il banner mostra gli errori senza modificare il testo;
- gli highlight mostrano quali elementi stanno suonando;
- il piano roll mostra la disposizione degli eventi nel tempo e segue lo stato
  di play e stop.

Questa divisione si vede anche nei package: `soundcode.mvu` contiene stato,
messaggi e comandi, mentre `soundcode.ui` contiene i componenti grafici. In
questo modo i dettagli di ScalaFX e RichTextFX rimangono nella UI e non entrano
nella gestione dello stato.

## Contratti tra i sottosistemi

| Confine | Dati scambiati | Vincolo di design |
|---|---|---|
| UI → parser | Testo completo del programma | Il parsing avviene solo su richiesta di aggiornamento. |
| Parser → interprete | AST e coordinate sorgente | L'AST non contiene decisioni di scheduling o dettagli MIDI. |
| Interprete → engine | Pattern e payload del dominio | Il dominio è indipendente dalla sintassi concreta. |
| Engine → audio | Evento, durata ed estensioni risolte | Il backend non conosce AST, editor o combinatori di pattern. |
| Engine → UI | Timeline finite e posizioni attive | La UI osserva risultati, ma non calcola la semantica temporale. |
| Runtime → vista | Stato applicativo completo | Il rendering deriva dal modello e non avvia direttamente effetti. |

Questi contratti limitano la propagazione dei cambiamenti: una nuova resa sonora richiede principalmente un adattamento del backend; un nuovo costrutto temporale interessa AST, interpretazione e relativo resolver; una nuova visualizzazione può consumare timeline esistenti senza modificare la produzione audio.
