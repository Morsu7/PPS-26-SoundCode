# Implementazione

## Struttura del codice

| Package | Elementi principali | Ruolo |
|---|---|---|
| `soundcode.parser` | `SoundCodeParser`, AST, trasformazioni e visualizer sintattici | Grammatica e rappresentazione sorgente. |
| `soundcode.interpreter` | `Interpreter` | Traduzione AST → dominio. |
| `soundcode.domain` | `Fraction`, `Interval`, `Pattern`, payload, modificatori, `Tempo` | Modello puro e immutabile. |
| `soundcode.engine` | resolver, `Scheduler`, `AudioPlayer`, backend | Risoluzione temporale ed esecuzione. |
| `soundcode.audio` | motore MIDI e mappe General MIDI | Adattamento a Java Sound. |
| `soundcode.mvu` | modello, messaggi, update, comandi e runtime | Stato e coordinamento degli effetti. |
| `soundcode.ui` | frame, vista, editor, errori e visualizzazioni | Interfaccia ScalaFX/RichTextFX. |

## Tecnologie

Il progetto usa Scala 3.8.4 e SBT. FastParse 3.1.1 implementa il parser; ScalaFX 21 e RichTextFX 0.11.7 implementano l'interfaccia; ScalaTest 3.2.18 supporta i test. La CI configura Temurin JDK 21 e usa Xvfb per i test JavaFX su Linux.

Queste versioni descrivono lo stato del repository al momento della redazione e dovranno essere aggiornate se cambia `build.sbt`.

## Aspetti implementativi rilevanti

`Fraction` normalizza segno e divisore e definisce operazioni aritmetiche, confronto, floor e ceil. La conversione da `Double` è confinata ai casi che ricevono configurazioni numeriche; la temporizzazione composta usa frazioni.

I resolver implementano separatamente atomi, sequenze, parallelismo, alternanza, estensioni e time warp. Il dispatcher di risoluzione seleziona il caso del tipo somma `Pattern`, evitando che parser e player conoscano gli algoritmi dei singoli combinatori.

`AudioPlayer` consuma eventi secondo `Tempo`, invoca `AudioBackend` e pubblica l'insieme delle `TextPosition` attive. `MidiBackend` traduce note e sample usando `MidiNote`, `GmInstrumentMap` e `GmDrumMap`; `MidiAudioEngine` è l'adapter concreto verso il sintetizzatore JVM.

La GUI è avviata da `SoundCodeFrame`. `BlockEditorView` concentra il comportamento dell'editor e delega funzioni ortogonali a supporti dedicati. `PianoRollView` e `OscilloscopeView` sono separate dietro `AnimatedView`.

## Build ed esecuzione

```text
sbt compile
sbt test
sbt run
```

La modalità di packaging e distribuzione non è ancora definita nel repository; non viene quindi dichiarata una release installabile.

