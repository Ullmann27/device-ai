# EXP-20 M5 / WP1b – reproduzierbare CRE1-Blind-Gate-Implementierung (30.10.2026)

**Status:** `IMPLEMENTED / HOST-API-SMOKE_PASS`, aber `REAL_CRE1_CHECKPOINT_NOT_RUN`, `SEALED_UNOPENED`, `ANDROID_NOT_RUN`, `FOLD7_NOT_RUN`.

## Warum diese Prüfung notwendig ist

Der frühere P4-Hopfield-Gewinn beruhte überwiegend auf einem abfrageblinden Medoid-/Konstruktionsartefakt. Für M5 prüft `M5Cre1BlindGate` deshalb unabhängig von jeder Benutzerfrage, wie häufig der wahre Kandidat allein wegen der **Geometrie der acht CRE1-Memory-Embeddings** gewinnt.

Pro Kandidat `j` gilt exakt `score_j = sum_i dot(v_i, v_j)`. `rank()` akzeptiert nur `List<DoubleArray>`; `embedMemory` erhält ausschließlich den Memory-Text, nicht Query, Gold, Rolle oder Candidate-ID. Die Gold-Markierung wird erst nach dem vollständig abgeschlossenen Ranking ausschließlich zur Ergebniszählung gelesen.

Der ursprünglichen Vorregistrierung wurde **vor realem CRE1-Lauf** das unabhängige [Tie-/Positional-Addendum](../../research/exp20/EXP-20-M5-BLIND-GATE-PREREG-ADDENDUM-20261030.md) hinzugefügt:

- Striktes query-blind Top-1 auf VAL <=17,5 %;
- Query-blind Top-1 **mit deterministischer Tie-Auflösung nach Slotposition** ebenfalls <=17,5 %;
- Daten- und CRE1-Modell-SHA-256 müssen vor jeglichem Gate-Lauf mit den vom Auftrag vorgegebenen eingefrorenen 64-stelligen Prüfsummen übereinstimmen;
- `maxTies` dokumentieren. Scheitert ein Gate, `INVALID`, **kein SEALED-Lauf**.

## Neue Quellen (nur app/src/test)

- `app/src/test/java/at/heinz/deviceai/research/exp20/m5/M5Cre1BlindGate.kt`
- `app/src/test/java/at/heinz/deviceai/research/exp20/m5/M5BlindGateSelfTest.kt`

Git-Blob-SHAs, bitgenau gegen die lokal untersuchten Dateien abgeglichen:
- Gate: `c52dd6f85dfe8ba17c1ff6723863f38348a406de`
- Negativtests: `dcdaf29f59408a5212324b75fdc0af3663e41b8f`

## Tatsächlich ausgeführte lokale Prüfungen

Mit Kotlin/JVM 1.9.0 und JDK 21, ohne voll ausgechecktes privates Repository:

1. Kotlin-Kompilation `M5Data.kt + M5Cre1BlindGate.kt + M5BlindGateSelfTest.kt` mit einem **temporären, ausdrücklich nicht produktiven CRE1-API-Stub** in einem lokalen Scratch-Verzeichnis: Exit 0. Der Stub hatte nur dieselben sichtbaren Methodensignaturen (`restore, embed, steps`). Keine Übereinstimmung der echten CRE1-Vektoren wurde behauptet.
2. **8/8 echte unabhängige geometrische/API-Negativtests** grün: identische Embeddings ergeben Tie, NaN wird abgewiesen, ungleiche Dimensionen werden abgewiesen, query-blind Medoid-Problem wird erkannt, Zielpositionen bleiben verteilt, strikter und forced Gate-Schwellenwert werden erzwungen, `embedMemory` erhält keine Query, und ein ausgetauschter Fragetext verändert die Blindwertung nicht.
3. Beide SHA-256-Fail-Closed-Gegenproben der CLI **Exit 1**, bevor das vorbestehende Audit-Ausgabeziel überschrieben wurde:
   - Falscher CRE1-Checkpoint-Hash → `CRE1 checkpoint changed since freeze`.
   - Falscher DEV/VAL-Datensatz-Hash → `M5 DEV/VAL dataset changed since freeze`.
4. Frühere eigenständige M5-Generator-Suite erneut ausgeführt: **12/12** (Exit 0). Jaccard-DEV/VAL L je `64/64`, S je `0/64`; separater lexikalischer Audit Exit 0.
5. Bekannte DEV/VAL-Datei: SHA-256 `7d70d20f79f03e10ffb4cef76292cd52201e66299aca42c8a1287ebf2b327dd2`. Keine Datenänderung.

**NICHT getan:** Original-CRE1-Checkpoint aus einem echten Node-/Kotlin-Lauf im lokalen Scratch erstellt, Modell `restore()` gegen echte Bytes aufgerufen, CRE1-Blind-Top-1 auf VAL gemessen, unabhängigen SEALED-Holdout generiert/geöffnet, gesamte frühere 42er-JUnit-Regression erneut ausgeführt, Gradle-/APK-/Fold7-Test.

## Exakter nächster Prüfablauf im vollständigen Repository

```sh
# Vom verifizierten PR-#256-HEAD; Compiler/JUnit-Umgebung wie in AGENTS.md.
mkdir -p /tmp/continuum-m5/cre1 /tmp/continuum-m5/results
node --input-type=module -e "import('./.github/continuum/cre-parity.mjs').then(m=>m.prepare('/tmp/continuum-m5/cre1'))"
sha256sum /tmp/continuum-m5/cre1/node-cre1.cre1
# P3-Parity-Checkpoint ist historisch mit SHA-256
# 2290194d25cc98cf6cc42e6b57f5ddcb2105ba14115e2e5295ad50c906f10db6 belegt.
# Nicht fortsetzen, wenn der im vorab fixierten Vertrag erwartete SHA abweicht!
WITH_ARGON_TEST=1 scripts/exp20-p2-host-run.sh build
scripts/exp20-p2-host-run.sh main at.heinz.deviceai.research.exp20.m5.M5Data generate /tmp/continuum-m5/dev-val.tsv
scripts/exp20-p2-host-run.sh main at.heinz.deviceai.research.exp20.m5.M5Cre1BlindGate \
 /tmp/continuum-m5/dev-val.tsv \
 /tmp/continuum-m5/cre1/node-cre1.cre1 \
 7d70d20f79f03e10ffb4cef76292cd52201e66299aca42c8a1287ebf2b327dd2 \
 2290194d25cc98cf6cc42e6b57f5ddcb2105ba14115e2e5295ad50c906f10db6 \
 /tmp/continuum-m5/results/blind-gate.txt
```

**Abnahmestatus dieser Befehle:** `NOT_RUN` in dieser Sitzung; erst nach echter Ausführung des letzten Befehls dürfen `CRE1_BLIND_VAL=PASS` und ein gemessener CRE1-Score behauptet werden.

## Sicherheits-/Integrationsgrenzen

Keinerlei `app/src/main`-Änderung, keine Modellgewichte verändert, keine Produktionsentscheidung, kein ToolGate-/OwnerGate-Bypass, kein `main`-Merge. Ergebnis ist ein auditiert vorbereiteter Forschungsschritt und **noch kein M5-Semantikerfolg**. Für WP3 sind nach bestandenem Gate ein eingefrorener Kandidat und ein *fremd verfasster* versiegelter Holdout erforderlich.
