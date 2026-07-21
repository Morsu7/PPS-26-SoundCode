# Matrice di tracciabilità

La matrice collega requisiti critici, decisioni architetturali e prove. Va aggiornata insieme al codice; “manuale” non equivale a “non verificato”, ma richiede un verbale ripetibile nella sezione di validazione.

| Requisito | Componenti responsabili | Evidenza principale |
|---|---|---|
| RF-01–RF-03 | GUI/editor, MVU, player | `BlockEditorViewSpec`, `MainViewSpec`, test update da completare |
| RF-04–RF-12 | parser, AST, interprete, dominio, resolver | `ProgramParserSuite`, `InterpreterFunSuite`, `CorePatternsTest` |
| RF-13–RF-16 | interprete, modificatori, resolver | suite dei pattern temporali ed estensioni |
| RF-17 | parser, formatter errori, MVU, banner | test parser + test vista/errore da confermare |
| RF-18, RF-20 | AST, payload, player, editor | test parser/interpretazione + `AudioPlayerTest` + test UI |
| RF-19 | supporti editor | suite syntax/autocomplete/auto-pairing |
| RF-21–RF-22 | scheduler finito, visualizzazioni | `AnimatedViewSpec` e prove specifiche da completare |
| RF-23 | `AudioEngine`, backend MIDI/no-op | `MidiAudioEngineTest`, `MidiBackendTest` |
| RNF-01 | dominio, resolver | suite pattern con intervalli esatti |
| RNF-02 | MVU, UI, parser/scheduler | benchmark di reattività da realizzare |
| RNF-03 | player, tempo, backend fake | benchmark jitter da realizzare |
| RNF-04 | MVU, player | test con clock controllabile da realizzare |
| RNF-05 | parser, MVU, backend | casi limite + fuzz/property test da realizzare |
| RNF-06 | scheduler lazy, resolver | benchmark carico/memoria da realizzare |
| RNF-07 | confini parser/domain/audio | test unitari senza JavaFX/MIDI reale |
| RNF-08 | build e CI | esecuzione GitHub Actions + prova OS demo |
| RNF-09 | intera documentazione | questa matrice e revisione finale |

## Stato dei requisiti

Prima della consegna aggiungere una colonna o tabella con stato **soddisfatto**, **parziale**, **escluso** o **non verificato**, numero/versione della build e collegamento al risultato CI. Nessun requisito opzionale deve essere descritto come completato soltanto perché esiste una classe omonima: conta il criterio di accettazione osservato.

