# Metodologia di sviluppo

In questa sezione si descriverà l'organizzazione del team di sviluppo.

## Scrum

La metodologia di sviluppo applicata su questo progetto è di tipo *Agile*, in particolare si è adottata la variante *Scrum*.

Il processo di sviluppo Scrum è un processo *iterativo* e *incrementale*, in cui a ogni iterazione si aggiungono nuove funzionalità al sistema o si raffinano delle funzionalità pre-esistenti.

La prima attività all'interno del processo di sviluppo Scrum è quella di redarre il *Product Backlog*, ovvero una lista di funzionalità, dette *items*.

Le iterazioni del processo, chiamate *sprint*, saranno svolte settimanalmente. Ogni sprint prevede le seguenti attività:

- *Sprint Planning*: riunione iniziale in cui si selezionano gli item da implementare durante lo sprint e si scompongono in sotto-funzionalità, di cui il team di sviluppo fornisce stime sulla loro complessità e alcuni design di dettaglio. Al termine dello Sprint Planning si produce uno *Sprint Backlog* che assegna a ogni membro del team dei *task* da eseguire.
- *Daily Scrum*: una breve riunione giornaliera, in cui il team di sviluppo si aggiorna sul progresso del progetto, riadattando lo Sprint Backlog.

Al termine dello sprint si prevedono tre ulteriori attività:

- *Product Backlog Refinement*: una riunione in cui il team di sviluppo si accorda sulle migliorie che devono essere apportate al Product Backlog.
- *Sprint Review*: si valuta il progresso del progetto ottenuto al termine dello sprint, verificando che sia un *PSPI (Potentially Shippable Product Increment)*.
- *Sprint Retrospective*: si valuta il processo di sviluppo adottato, discutendo possibili cambiamenti che potrebbero aumentare l'efficacia del team.

L'organizzazione del personale all'interno del processo Scrum prevede tre ruoli:

- *Product Owner*: si occupa di redarre il *Product Backlog* e di verificare l'adeguatezza del sistema realizzato;
- *Scrum Master*: agisce da esperto del processo Scrum e da mediatore tra gli altri due ruoli;
- *Development Team*: si occupa di progettare soluzioni adeguate ai task definiti dal Product Owner, stimando tempi di realizzazione e proponendo modifiche sul sistema al Product Owner. Il team di sviluppo sarà composto da:
  - Remedi Tommaso
  - Biagioni Giacomo
  - Morsucci Federico
  - Morbidelli Cristian

Le riunioni saranno svolte periodicamente su *Microsoft Teams*, mentre l'organizzazione delle funzionalità del progetto sarà tenuta su [*Trello*](https://trello.com/b/2mgr8e1j/pps).

## Test-Driven Development

Durante lo sviluppo del sistema è stato scelto di applicare il più possibile il *Test-Driven Development (TDD)*, il cui scopo è quello di anticipare il prima possibile la fase di testing per minimizzare i costi di manutenzione e il rischio di fallimento del progetto.

Il processo seguito durante il TDD è un processo iterativo chiamato *Red-Green-Refactor (RGR)*, che prevede a ogni iterazione le seguenti fasi:

1. *Red*: scrivere un test che fallisca per una certa funzionalità da implementare.
2. *Green*: scrivere il codice di produzione che soddisfi il test definito precedentemente.
3. *Refactor*: ristrutturare sia il codice di testing che quello di produzione.

A supporto di questo processo, sono stati adottati i seguenti strumenti:

- *ScalaTest*: framework per la definizione di unit test per Scala.
- *SCoverage*: strumento per valutare la qualità dei test come percentuale di codice di produzione analizzato.

Poiché si è deciso di utilizzare il testing solo per quanto riguarda il modello, è stato scelto di utilizzare lo stile di testing *FlatSpec*, in quanto questo stile è un buon compromesso rispetto alla semplicità di *FunSuite* e alla organizzazione dei test di *FunSpec*. Inoltre, permette di realizzare dei test in modo dichiarativo e quindi più vicini all'utente finale.

## Quality Assurance

Per il controllo della qualità del sistema sono stati adottati i seguenti strumenti:

- *Scala Formatter*: controlla lo stile del codice Scala.
- *Wart Remover*: individua possibili difetti nel codice Scala.
- *Ktlint*: controlla lo stile del codice Kotlin.
- *Detekt*: individua possibili difetti nel codice Kotlin.

## Build Automation

Per automatizzare i processi di compilazione, testing e release del codice sviluppato si è deciso di utilizzare *Gradle* come strumento di *Build Automation*.

È stato preferito Gradle al posto di Sbt, perché è una tecnologia più matura e gli sviluppatori hanno più esperienza con tale strumento.

## Continuous Integration

Per fare in modo che il codice rimanga integro e corretto durante lo sviluppo, è stato utilizzato un workflow che attraverso *GitHub Actions* permette di eseguire i test del progetto su diverse configurazioni di sistemi operativi, a ogni aggiornamento del progetto.

## Continuous Delivery

Analogamente, un altro workflow permette di automatizzare la release su *GitHub Releases* e *Maven Central*.

## Automated Evolution

Per mantenere aggiornate le dipendenze del progetto in maniera automatica è stato utilizzato *Renovate Bot*, che osserva le dipendenze del progetto e in caso di necessità apre delle *pull request* sul repository GitHub per aggiornarle.

## Versioning

Per il versionamento del sistema, è stato adottato lo standard *Semantic Versioning*, il quale prevede che una versione sia caratterizzata da:

- *Major*: numero identificativo da aggiornare a ogni cambiamento non-retrocompatibile.
- *Minor*: numero identificativo da aggiornare a ogni aggiunta o modifica retrocompatibile di una funzionalità del sistema.
- *Patch*: numero identificativo da aggiornare a ogni correzione dei difetti del sistema.

Adottando lo standard *Conventional Commits* durante lo sviluppo tramite DVCS, è stato possibile automatizzare l'assegnamento della versione al sistema sulla base dei commit effettuati. Per fare ciò, è stato sfruttato *Semantic Release Bot* per GitHub.
