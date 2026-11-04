# R2: gezielter nativer minGRU-Gradientencheck

## Ergebnis und Grenze

Der ausgeführte Prüfer findet bei **234 ausgewählten Koordinaten** eines kleinen, synthetischen minGRU keinen Widerspruch zwischen den Produktionsgradienten und einer unabhängig implementierten Double-Vorwärtsrechnung innerhalb der vor dem ersten Lauf festgelegten Toleranz. **Die direkten Float32-Verlustdifferenzen bestehen diese Toleranz nicht durchgehend.** Alle Schrittweiten und Abweichungen bleiben unverändert in den Rohdaten. Daraus folgt weder eine allgemeine Freigabe sämtlicher Ableitungen noch ein Nachweis von Lernqualität oder einer EOS-Ursache. Es wurde kein Produktionscode geändert und kein Modell trainiert.

Der erste Compilerlauf und der erste Auditlauf endeten erfolgreich (Exit 0). Nach Review wurden explizite `isFinite`-Prüfungen für jeden analytischen Gradienten, beide Double-Differenzen und alle drei Float-Differenzen ergänzt. Derselbe kleine Lauf wurde erneut erfolgreich ausgeführt (Exit 0); Rohlog und Koordinatenwerte sind gegenüber dem ersten Lauf bytegleich. `evidence-gradient/compile.log` ist leer. `evidence-gradient/run.log` enthält die tatsächliche Konsolenausgabe; die beiden TSV-Dateien enthalten jede geprüfte Koordinate. Der ursprüngliche Quelltext und sämtliche ersten Rohdaten bleiben unter `evidence-gradient/first-run/` erhalten.

## Fixture und Verfahren

- `LmArch.MINGRU`, Vokabular 258, Dimension 8, zwei Schichten, Kontext 8, MLP 12, Seed `490514`; insgesamt **3.056 Parameter**.
- Eingaben `4,7,4,12,7,4`; Ziele `7,4,12,7,4,1`. Alle Token sind synthetisch. Die vorhandene Initialisierung einschließlich Embedding-Skalierung `0.02` bleibt erhalten.
- Szenario `original_init`: unveränderte Initialisierung. Szenario `zero_l1_outputs`: ausschließlich `l1.wo`, `l1.w2`, `l1.b2` werden im neuen Fixture auf null gesetzt.
- Je Szenario 117 Koordinaten: alle acht Komponenten der drei vorkommenden Embedding-Zeilen, eine ausschließlich als Ausgabe verwendete Zeile und je vier repräsentative Koordinaten aller anderen Parametertensoren. Damit werden unter anderem beide rekurrenten Blöcke, RMS-Normalisierungen, Gates, dichte Matrizen und Biases berührt. Nicht sämtliche 3.056 Koordinaten werden differenziert.
- Die analytischen Gradienten kommen aus dem echten `LmModel.logits` und `Tape.crossEntropy`. Die direkte numerische Ableitung verwendet **`model.nll(ids, targets).average()`**, zentrale Differenzen und die tatsächlich darstellbare Float-Differenz `plus - minus` als Nenner. Feste Schritte: `1e-3`, `3e-4`, `1e-4`.
- Eine kompakte unabhängige Double-Rechnung implementiert Gather, RMS-Normalisierung, Matrixprodukte, minGRU-Rekurrenz, SiLU, GELU und das gebundene Ausgabe-Embedding ohne `Tape` oder `Kernels`. Konstanten entsprechen den als Float gespeicherten Produktionskonstanten. Ihre Differenzen verwenden `1e-5` und `5e-6`.
- Die unverändert verwendete Koordinatentoleranz lautet `3e-5 + 0.003 * abs(Double-Referenz)`. Entscheidend für den ausführbaren Referenzcheck ist der Double-Schritt `5e-6`; beide Double-Schritte und sämtliche Float-Schritte werden vollständig ausgegeben. Der Prüfer verwirft keine problematischen Koordinaten und wählt nicht nachträglich den günstigsten Float-Schritt aus.

## Tatsächliche Messwerte

| Messung | Original | Zweiter Block mit Nullausgängen |
|---|---:|---:|
| Produktions-NLL | 5.583504775174723 | 5.5536242554580975 |
| Unabhängige Double-NLL | 5.5835047669649205 | 5.553624250878492 |
| Double-Toleranzüberschreitungen | 0 / 117 | 0 / 117 |
| Größte absolute Gradientabweichung zur Double-Referenz | 3.383955743e-6 | 1.239682405e-6 |
| Größter Unterschied zwischen den beiden Double-Schritten | 1.457496346e-5 | 7.343459174e-7 |
| Float-Überschreitungen bei `1e-3` | 21 / 117 | 11 / 117 |
| Float-Überschreitungen bei `3e-4` | 11 / 117 | 1 / 117 |
| Float-Überschreitungen bei `1e-4` | 11 / 117 | 0 / 117 |

Die größten Float-Abweichungen im Originalfall sinken von `0.184010686463` über `0.0174171384948` auf `0.00187859016165`. Das zeigt eine starke Schrittweitenabhängigkeit; große Schritte erfassen bei kleinen ursprünglichen Embeddings merkliche Krümmung. Kleinere Schritte unterliegen zusätzlich den Rundungen des Float-Vorwärtslaufs. Die einzelnen verbleibenden Überschreitungen werden hier nicht abschließend einer Ursache zugeordnet. Die Double-Rechnung ist eine unabhängige Näherung an die glatte mathematische Funktion, keine exakte Ableitung der diskreten Float-Rundungsfunktion. `float-diagnostics.log` zeigt die jeweils größten Abweichungen; die vollständigen Kurven stehen in den TSV-Dateien.

## Gezielt geprüfte Eigenschaften

**Geteiltes Embedding und Negativkontrolle:** Der echte Forward wird einmal mit separaten Eingangs- und Ausgangsgradientpuffern für denselben `tok`-Wert ausgeführt. Der Prüfer sichert die erwarteten zwei `tok`-Zugriffe explizit ab. Die Summe stimmt mit dem gebundenen Gradienten bis maximal `1.192092896e-7` beziehungsweise `4.470348358e-8` überein. Wiederholte Eingabe-IDs sind enthalten.

Als absichtlich falscher Gradient wird nur der Ausgangsanteil verwendet. Im Originalfall ist bei `tok[39]` die Double-Referenz `-2.6488272682811953`, der falsche Gradient `0.07858184725046158`; Fehler `2.727409115531657`, Toleranz `0.007976481804843586`. Im Nullausgangsfall ist bei `tok[36]` die Referenz `-0.9332931801608878`, der falsche Gradient `-0.18875913321971893`; Fehler `0.7445340469411689`, Toleranz `0.0028298795404826634`. Beide falschen Gradienten werden mit deutlich mehr als zehnfacher Toleranz verworfen. Produktionsdateien werden für diese Kontrolle nicht verändert.

**Geteilte Akkumulation:** Zwei nacheinander ausgeführte Tapes derselben Sequenz akkumulieren in gemeinsame Gradientenpuffer. Gegenüber zweimal dem separat berechneten Gradienten beträgt die größte Abweichung `4.768371582e-7` beziehungsweise `1.192092896e-7`. Das prüft Akkumulation, keinen parallelen Worker-Scheduler.

**Nullausgänge:** Im zweiten Block sind sämtliche Gradienten von `n1/wz/bz/wc/wg/n2/w1/b1` exakt null. Zugleich sind die maximalen absoluten Gradienten von `wo`, `w2`, `b2` jeweils `0.09007281`, `0.20403051`, `0.09717421`. Die NLL-Werte sind exakt gleich denen eines aus denselben Parametern gebildeten Modells ohne den zweiten Block. Dieser Befund betrifft den ersten lokalen Ableitungsschritt am Nullblock; er besagt nichts über späteren Trainingsfortschritt.

## Reproduktion und Herkunft

Ausgeführter Einstieg:

```bash
bash /workspace/scratch/319f3e4563d9/agents-r2/gradient/experimental/weight-space-20261104/support-round2/evidence-gradient/replay.sh
```

Das Skript enthält die vollständigen Compiler- und Laufbefehle. Es kompiliert nur `LmTensor.kt`, `LmModel.kt`, `LmBpeTokenizer.kt` und den neuen Prüfer mit Kotlin **2.0.20**, `-no-stdlib -no-reflect -jvm-target 17`, expliziter Stdlib und Annotations. Ausgeführt wurde auf OpenJDK **21.0.13**. Die Warnungen in `kotlin-version.log` stammen vom separaten reinen Versionsaufruf ohne Compilerflags; der eigentliche Compile-Log ist leer.

Die sechs Produktionsdateien stimmen mit PR492-Commit `30cd3a89810ed14fa84eca95236d3177e016118d` überein. Tatsächlich ausgeführt, Exit 0 und leere Ausgabe in `production-basis-diff.log`:

```bash
git -C /workspace/scratch/319f3e4563d9/weight16m-worktree diff --exit-code 30cd3a89810ed14fa84eca95236d3177e016118d -- app/src/main/java/at/heinz/deviceai/learning/lm/
```

Vorheriger Supportstand: `e726deb96dc74bacd40c2fe0ef640c6db57903f5`. R2-Zuweisung: Draft-PR #514, Startcommit `dfc4c1088c240aea536955caaeaf4740e5aadd9c`. Kein alter Modell-Checkpoint und kein Holdout wurde benutzt. `AGENTS.md` und `experimental/weight-space-20261104/PROTOCOL.md` wurden vor der Implementierung gelesen.

| Produktionsdatei | SHA-256 |
|---|---|
| LmBpeTokenizer.kt | `fcff707a5a1679244cffbb6583e1594b6084e941c7e918f834d71499029b628e` |
| LmCheckpointStore.kt | `09bf68c1c765b6ed625d71672a2aa836fedc3f587546e9197d4b21ee0e446b17` |
| LmCorpus.kt | `f6b0d0bbb9ee0e082b82e60ab7a39113cbf7d996fe60b363ee25c39a1b4930b4` |
| LmModel.kt | `abb4b50cc443344aefe720844aad4a9ca3e3d967973cc31a4e45ca07b732f099` |
| LmRuntime.kt | `210606a253650d2268e642ab0edc876aedc90eba2adddc1ac7b8c35eb013e0c2` |
| LmTensor.kt | `a5497f18d09be50f7afbd201d54d97f012f7508f8c53c834ff6c10ef278290ab` |

Compiler-, Stdlib- und Annotations-Hashes stehen in `tool-sha256.txt`; Prüfer-, JAR-, Skript-, Log- und TSV-Hashes in `artifact-sha256.txt`. Der abschließende Prüfer-SHA-256 ist `3dae55568efae84dd3ca4ae70feb05d150fa0046c1f98f67717bc773ca9ad18a`. `final-manifest.sha256` umfasst den Bericht, Prüfer und sämtliche Evidenzdateien mit relativen Pfaden; ausgenommen ist allein die Manifestdatei selbst.

Nicht geprüft: vollständige Koordinatenabdeckung, reale 16M-Checkpoints, Optimierungsschritte, Trainierbarkeit, Qualitätsdaten, ausgelassene Targets, alternative Normalisierungsfaktoren, andere Sequenzlängen oder echte Parallelität. Ein separater rekurrenter Negativkontrollfall wurde nicht ergänzt; die sechs Zeitpositionen sind im End-to-End-Check enthalten.
