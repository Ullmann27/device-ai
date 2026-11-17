# Vier unabhängige State-/Checkpoint-Heads

**Vergleichsquelle:** `5d91442e98f78a2af623b8a3c74920805f670cb9` · Tree `ae79915e7b6df784f0ba78ead4a44b44dedec08a`.

**Ergebnis: 4/4 lokal vollständig anhand von Commit-/Tree-/Pfadmetadaten geklärt.** Kein fehlendes Commit-/Treeobjekt. Alle vier Heads besitzen eine eigene, vom App-Stand getrennte Root-Historie; `git merge-base --all` liefert jeweils Exit 1 ohne Fehlerausgabe. Das erklärt die vorherige Ancestry-Lücke.

| Branch / Head | Tree | Nur Pfadmetadaten | Einordnung |
|---|---|---|---|
| `continuum-agent-state` · `bdeb0ec8e39b54e072e95df551a39ca6766ae46f` | `debc67794877d1126bbf956dedfc7cddae43c206` | 3 Einträge: README.md, development-backlog.json, repair-ledger.json. | **Eigenständiger Agent-Statezweig.** Kein App-/Skriptcode im Head; Zustands-/Ledgerdaten nicht als Quellcode integrieren. |
| `continuum-state` · `627294bf9726932eb2e7d6d0d81b3291377e15cc` | `584c03adc1079929fbebba77128a15e34899bc8c` | 3 Einträge: 1 .bin und 2 .json. | **Eigenständiger Daten-/Statezweig.** Kein App-/Skriptcode im Head; binären und privaten State-Inhalt nicht bewertet. |
| `opus/lm1-checkpoints-20261022` · `d2304a7658e1256b160c8012e1bac28268ff64a5` | `31390ae7796c9e288c6af80e7ec1a6d6c1dcbeeb` | 134 Einträge: 130 .lm1, 1 .lmbp-Tokenizer, MANIFEST.tsv, README.md und select.log. | **Eigenständiger LM1-Checkpointzweig.** Kein App-/Trainingsskriptcode im Head; Checkpoints/Tokenizer weder geöffnet noch auf Eignung geprüft. |
| `opus/nf-checkpoints-20261022` · `fab239d38f2e820b93eb3f869e20ada895d7e4dc` | `9278648a2e6e2f06eb257f90f8fc536af84aaab1` | 254 Einträge: 252 .lm1 sowie MANIFEST.tsv und README.md. | **Eigenständiger Checkpointzweig.** Kein App-/Trainingsskriptcode im Head; Checkpointinhalte nicht gelesen oder als freigegebene Gewichte bewertet. |

## Historienbeleg

| Branch | Vollständig sichtbare Commits | Eigener Root | Codepfade im Head |
|---|---:|---|---:|
| `continuum-agent-state` | 67 | `ceb07a0c038a3b5a33f9e1b3e6beecdf64082129` | 0 |
| `continuum-state` | 68 | `bf952380642ad6f620a22846870a9c002e049029` | 0 |
| `opus/lm1-checkpoints-20261022` | 2 | `977293a7d89a81fc39a88cb43b0a56024191e38c` | 0 |
| `opus/nf-checkpoints-20261022` | 3 | `5dbaa4bef3d0824eebf0890a0c78616cc823cba1` | 0 |

Die rein metadatenbasierte Historienlesung war auf 1000 Commits je Head begrenzt; keiner erreichte die Grenze. Das Repository ist nicht shallow. Alle vier Historien wurden ohne fehlende Elternobjekte bis zum jeweiligen Root gelesen.

## Grenzen und Belege

- **Keine Blob-Inhalte gelesen:** keine Gewichte, Checkpoints, Tokenizer, privaten JSON-/BIN-State-Dateien; auch README-, Manifest- und Log-Inhalte blieben ungeöffnet.
- Kein bisher ungelesener App-/Skriptcodezweig unter diesen vier Heads belegt. Dateitypen und Pfade begründen die State-/Checkpoint-Klassifikation; sie beweisen weder Modellqualität noch Kompatibilität oder eine Freigabe zur Nutzung von Gewichten.
- Git wurde mit `--no-lazy-fetch` und `GIT_NO_LAZY_FETCH=1` verwendet. Kein Fetch, keine Projektprogramme, Tests, Builds oder Provideraufrufe; keine Source-, Ref- oder Konfigurationsänderung.
- `metadata.json`: Commit-/Tree-/Parentmetadaten, Pfadlisten, Merge-Base-Ausgänge und begrenzte Historienzählung.
- `<Head-SHA>/tree-paths.json`: ausschließlich Pfad, Modus, Objekttyp und Objekt-ID; kein Dateiinhalt.
- `closure-receipt.json`: Ergebnis, exakte Vergleichsquelle und Hashes dieser Auswertung.
