---
layout: default
title: Metodologia di sviluppo
---

In questa sezione si descriverà l'organizzazione del team di sviluppo.

## Scrum

La metodologia di sviluppo applicata al progetto è stata di tipo *Agile*; in particolare, è stato adottato *Scrum*.

Scrum è un processo *iterativo* e *incrementale*, nel quale ogni iterazione aggiunge nuove funzionalità al sistema o perfeziona quelle esistenti.

All'inizio del progetto è stato redatto il *Product Backlog*, contenente le funzionalità da realizzare, organizzate in *item*.

Le iterazioni del processo, chiamate *sprint*, sono state svolte settimanalmente. Ogni sprint ha previsto le seguenti attività:

- *Sprint Planning*: riunione iniziale in cui sono stati selezionati gli item da implementare durante lo sprint e sono stati scomposti in attività più piccole. Il team ne ha stimato la complessità e ha definito, quando necessario, il design di dettaglio. Il risultato è stato lo *Sprint Backlog*, utilizzato per organizzare e distribuire le attività.
- *Daily Scrum*: breve riunione giornaliera in cui il team si è aggiornato sul progresso del progetto e ha adattato lo Sprint Backlog.

Al termine di ogni sprint sono state svolte tre ulteriori attività:

- *Product Backlog Refinement*: il team ha riesaminato gli item e concordato gli aggiornamenti da apportare al Product Backlog.
- *Sprint Review*: è stato valutato l'incremento ottenuto al termine dello sprint rispetto agli obiettivi stabiliti.
- *Sprint Retrospective*: è stato valutato il processo di sviluppo, discutendo i cambiamenti utili a migliorare l'efficacia del team.

L'organizzazione del team ha previsto tre ruoli:

- *Product Owner*: si è occupato di redigere il *Product Backlog* e di verificare l'adeguatezza del sistema realizzato;
- *Scrum Master*: ha supportato l'applicazione del processo Scrum e il coordinamento del team;
- *Development Team*: si è occupato di progettare soluzioni adeguate ai task definiti dal Product Owner, stimando i tempi di realizzazione e proponendo modifiche al sistema. Il team di sviluppo era composto da:
  - Remedi Tommaso
  - Biagioni Giacomo
  - Morsucci Federico
  - Morbidelli Cristian

Le riunioni sono state svolte periodicamente su *Microsoft Teams*, mentre le funzionalità e le attività del progetto sono state organizzate su Trello.

## Test-Driven Development

Durante lo sviluppo del sistema è stato scelto di applicare il più possibile il *Test-Driven Development (TDD)*, il cui scopo è quello di anticipare il prima possibile la fase di testing per minimizzare i costi di manutenzione e il rischio di fallimento del progetto.

Il processo seguito durante il TDD è un processo iterativo chiamato *Red-Green-Refactor (RGR)*, che prevede a ogni iterazione le seguenti fasi:

1. *Red*: scrivere un test che fallisca per una certa funzionalità da implementare.
2. *Green*: scrivere il codice di produzione che soddisfi il test definito precedentemente.
3. *Refactor*: ristrutturare sia il codice di testing che quello di produzione.

A supporto di questo processo è stato adottato *ScalaTest* per la definizione dei test automatici.

Le suite utilizzano lo stile *AnyFunSuite*. I test coprono le principali componenti del progetto, tra cui parser, interprete, motore di esecuzione, sottosistema audio, aggiornamento del modello MVU e interfaccia grafica. Per i test dei componenti JavaFX viene inizializzato l'ambiente grafico necessario all'esecuzione.

## Build Automation

Il progetto utilizza **sbt** per rendere ripetibili le attività di compilazione, verifica e assemblaggio. La configurazione è dichiarata in `build.sbt`, mentre `project/build.properties` fissa la versione di sbt impiegata: in questo modo gli sviluppatori e l'ambiente di Continuous Integration eseguono gli stessi task con la medesima configurazione.

Per l'esecuzione e la build è richiesto **JDK 21**. Le librerie necessarie, tra cui ScalaTest, FastParse, ScalaFX e RichTextFX, sono dichiarate esplicitamente nel file di build e vengono risolte automaticamente da sbt. Ciò evita configurazioni manuali delle dipendenze e rende possibile ricostruire il progetto a partire dal repository.

Per la generazione del pacchetto è configurato il plugin **sbt-assembly**, che permette di produrre un unico JAR eseguibile contenente il codice dell'applicazione e le dipendenze necessarie. Il task assegna come entry point `soundcode.main`, genera un file con nome `soundcode-<versione>.jar` e applica strategie di fusione esplicite per i metadati che potrebbero entrare in conflitto durante l'assemblaggio.

L'automazione della build costituisce anche il confine tra sviluppo locale e Continuous Integration: il workflow remoto non introduce una procedura di compilazione alternativa, ma prepara JDK e sbt e richiama gli stessi task disponibili agli sviluppatori. In particolare, i test sono eseguiti con `sbt test` all'interno di un display virtuale, necessario per rendere verificabili su Ubuntu anche i componenti che dipendono da JavaFX.

## Continuous Integration

Per verificare l'integrità del codice a ogni aggiornamento del progetto è stato configurato un workflow di *GitHub Actions*. La CI viene eseguita su `ubuntu-latest`, prepara JDK 21 e sbt e avvia la suite con `xvfb-run -a sbt test`; il display virtuale consente di eseguire in modalità headless anche i test che dipendono da JavaFX. La suite viene inoltre eseguita localmente su Windows dagli sviluppatori, fornendo una verifica aggiuntiva del comportamento del progetto sui due sistemi operativi.

## Versioning

Per il versionamento del sistema, è stato adottato lo standard *Semantic Versioning*, il quale prevede che una versione sia caratterizzata da:

- *Major*: numero identificativo da aggiornare a ogni cambiamento non-retrocompatibile.
- *Minor*: numero identificativo da aggiornare a ogni aggiunta o modifica retrocompatibile di una funzionalità del sistema.
- *Patch*: numero identificativo da aggiornare a ogni correzione dei difetti del sistema.

Il numero di versione segue il formato `MAJOR.MINOR.PATCH` ed è dichiarato manualmente in `build.sbt`.
