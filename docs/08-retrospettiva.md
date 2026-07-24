---
layout: default
title: Conclusioni
---

## Risultato del progetto

SoundCode ha portato alla realizzazione di un ambiente desktop per il live coding musicale, sviluppato interamente in Scala 3. L'applicazione consente di descrivere pattern ritmici e melodici attraverso una DSL testuale, interpretarli come eventi temporali ciclici e riprodurli mediante il sintetizzatore MIDI locale. L'utente può modificare il programma durante la sessione, controllare la riproduzione e osservare gli eventi sia attraverso l'evidenziazione del codice sia mediante il piano roll.

Il risultato finale rispetta l'obiettivo iniziale di costruire una versione circoscritta e didatticamente significativa dei sistemi ispirati a Strudel e TidalCycles. Il progetto non tenta di riprodurne l'intero insieme di funzionalità, ma approfondisce gli aspetti centrali per il corso: definizione di un linguaggio, modellazione funzionale dei pattern, trasformazione dei dati attraverso componenti indipendenti, gestione del tempo e coordinamento tra logica applicativa, audio e interfaccia grafica.

La pipeline principale attraversa in modo esplicito tutte le rappresentazioni del sistema: il testo viene trasformato in AST, l'AST in pattern di dominio, i pattern in timeline di eventi e gli eventi in comandi MIDI e aggiornamenti grafici. Questa separazione si è rivelata importante sia per l'evoluzione del software sia per la possibilità di verificare ogni fase con test mirati.

## Retrospettiva sullo sviluppo

Lo sviluppo è stato organizzato in iterazioni settimanali, con attività raccolte e aggiornate nel Product Backlog. Il backlog iniziale conteneva macro-funzionalità ampie — editor, DSL, engine temporale, riproduzione audio e visualizzazione — che durante gli sprint sono state progressivamente scomposte in attività più piccole e verificabili. Questa scomposizione ha permesso ai membri del gruppo di lavorare in parallelo mantenendo confini sufficientemente chiari tra parser e interprete, dominio ed engine, audio, runtime MVU e interfaccia.

L'andamento complessivo può essere ricondotto alle seguenti fasi:

1. **Impostazione del progetto.** Nella prima iterazione sono stati definiti obiettivi, requisiti iniziali, struttura del repository, build sbt e Continuous Integration. In parallelo sono stati creati lo scheletro dell'applicazione e le prime componenti dell'editor. Questa fase ha permesso di concordare una base comune, ma ha anche evidenziato la necessità di mantenere la documentazione strettamente collegata alle scelte effettivamente implementate.
2. **Definizione del linguaggio e del dominio.** Il backlog si è concentrato sulla grammatica della DSL, sull'AST e sulle strutture del dominio. Il linguaggio è cresciuto incrementalmente: prima generatori e sequenze, poi raggruppamenti, parallelismi, alternanze, concatenazione di chiamate, effetti e trasformazioni temporali. I test scritti insieme alle nuove produzioni hanno aiutato a chiarire casi ambigui e vincoli sintattici.
3. **Costruzione dell'engine.** Le iterazioni successive hanno introdotto interprete, scheduler e AudioPlayer. Questa è stata la fase con il maggior numero di revisioni progettuali. La prima rappresentazione del dominio non era sufficiente a descrivere in modo uniforme pattern annidati, trasformazioni e finestre temporali; è stata quindi rielaborata più volte, separando i resolver e introducendo frazioni e intervalli esatti. Il costo del refactoring è stato significativo, ma ha prodotto un modello più coerente e più facilmente verificabile.
4. **Integrazione audio e interfaccia.** Una volta stabilizzata la pipeline logica, il backlog si è spostato sulla riproduzione MIDI, sulla selezione degli strumenti, sugli effetti supportati dal backend e sul collegamento con la GUI. Nello stesso periodo sono stati integrati highlighting durante la riproduzione, piano roll, messaggi di errore, completamento automatico, scorciatoie e controllo del volume. L'integrazione ha fatto emergere problemi non visibili nei componenti isolati, soprattutto nella sincronizzazione fra timeline, note attive e aggiornamenti JavaFX.
5. **Consolidamento.** Le ultime iterazioni sono state dedicate a correzioni, prestazioni, riorganizzazione dei test e allineamento della documentazione. Sono stati affrontati casi come eventi che attraversano i confini di ciclo, note ribattute, sequenze di play e stop, concorrenza nell'AudioPlayer e rallentamenti causati da più visualizzatori. Il backlog finale ha quindi dato priorità alla stabilità del percorso principale rispetto all'aggiunta di nuove funzionalità.

Il Product Backlog non è rimasto invariato durante il progetto. Alcune attività inizialmente considerate secondarie, come la propagazione delle posizioni nel testo o il controllo deterministico del tempo nei test, sono diventate necessarie per integrare correttamente editor, engine e visualizzazioni. Al contrario, funzionalità più ampie — persistenza dei progetti, esportazione, collaborazione remota e compatibilità completa con Strudel — sono rimaste fuori dallo scope. Anche alcuni effetti riconosciuti dalla DSL non hanno una resa sonora completa nel backend MIDI. La revisione continua delle priorità ha evitato che questi obiettivi estendessero eccessivamente il progetto, consentendo di concentrare il lavoro su una pipeline utilizzabile dall'inizio alla fine.

### Definition of Done

Un item è stato marcato `DONE` soltanto quando il suo risultato era integrato nel ramo principale e soddisfaceva i criteri applicabili fra i seguenti:

- il comportamento richiesto era implementato e coerente con i requisiti e con i contratti dei componenti coinvolti;
- il codice compilava con la versione di JDK e Scala stabilita dal progetto, senza introdurre errori nelle altre componenti;
- erano presenti test automatici significativi per il comportamento nuovo o modificato e l'intera suite risultava verde localmente;
- il workflow di Continuous Integration completava con successo build e test, inclusi quelli JavaFX eseguiti tramite display virtuale;
- documentazione, grammatica, esempi e diagrammi interessati dal cambiamento erano stati aggiornati;
- gli errori conosciuti e i limiti intenzionali erano esplicitati, senza presentare come supportato un comportamento disponibile soltanto nel modello o nella sintassi;
- per le proprietà non verificabili in modo affidabile automaticamente, come resa MIDI, sincronizzazione percepita e comportamento grafico, era stato eseguito uno scenario manuale ripetibile.

Per gli item puramente documentali non erano richiesti test software, mentre per refactoring privi di cambiamenti osservabili era necessario mantenere verde la suite esistente. La Review verificava infine che il risultato fosse dimostrabile come incremento del prodotto; il solo completamento del codice non era sufficiente per assegnare lo stato `DONE`.

## Valutazione del processo

L'approccio incrementale è stato efficace perché ha reso disponibile molto presto una struttura eseguibile sulla quale integrare le diverse parti. La presenza della CI su Ubuntu e l'esecuzione locale dei test su Windows hanno aiutato a individuare problemi legati all'ambiente grafico e alla portabilità. L'introduzione di Xvfb ha inoltre reso possibile eseguire in CI i test che richiedono JavaFX.

La strategia di testing è stata particolarmente utile nell'engine. L'impiego di tempo razionale, intervalli espliciti e un DSL interno per descrivere gli eventi attesi ha consentito di verificare la geometria temporale dei pattern senza dipendere dal tempo reale. Anche la separazione dell'AudioPlayer dal backend concreto ha permesso di controllare in modo deterministico l'ordine e il momento di emissione degli eventi.

La principale difficoltà organizzativa è derivata dalle dipendenze tra attività sviluppate in parallelo. Modifiche al dominio hanno richiesto adattamenti coordinati in interprete, scheduler, audio, UI e test.

## Limiti e sviluppi futuri

La versione realizzata privilegia la correttezza del modello e l'esecuzione locale, ma presenta margini di evoluzione. Fra gli sviluppi più significativi rientrano:

- salvataggio e caricamento dei programmi;
- esportazione delle sequenze in MIDI o in altri formati;
- ampliamento della DSL e della libreria di trasformazioni;
- backend audio più ricco, capace di rendere tutti gli effetti descritti dal modello;
- controllo più completo di tempo e parametri musicali dall'interfaccia;
- ulteriori visualizzazioni e miglioramento delle prestazioni grafiche;

Queste estensioni possono essere introdotte senza modificare il principio fondamentale dell'architettura: mantenere separati linguaggio, semantica dei pattern, scheduling, riproduzione e presentazione.

## Considerazioni finali

Il progetto ha mostrato come un dominio apparentemente immediato, quale la riproduzione di brevi pattern musicali, richieda decisioni precise su grammatica, composizione, rappresentazione del tempo, concorrenza e sincronizzazione dell'interfaccia. Le revisioni affrontate durante gli sprint non sono state soltanto correzioni, ma hanno contribuito a rendere più chiari i confini del sistema e le responsabilità dei componenti.

 SoundCode costituisce una base funzionante ed estendibile per ulteriori esperimenti di composizione algoritmica e rappresenta l'esito di un processo nel quale backlog, test e refactoring hanno guidato progressivamente il passaggio dall'idea iniziale a un sistema integrato.
