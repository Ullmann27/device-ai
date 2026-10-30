# EXP-20 M5 – echter CRE1-Host-Runner, Wiederholungsprüfung und Grenzen (30.10.2026)

## Status – unbedingt unterscheiden

**IMPLEMENTED, local-script-syntax PASS, M5-Host-Logik 20/20 PASS, SHA-NEGATIVE 2/2 PASS.**

**NOT_RUN:** tatsächlicher Node-generierter CRE1-Parity-Checkpoint, echter CRE1-VAL-Blind-Score, alte 42er-JUnit-Gesamtregression auf diesem Stand, Android-Gradle/APK, Fold7 und fremder SEALED-Holdout. **Kein neuraler oder semantischer Qualitätsgewinn behauptet.**

Der neue Einstiegspunkt `scripts/exp20-m5-cre1-real-gate.sh` ist ein voll lokaler, abhängigkeitsoffener Host-Runner. Er reproduziert vor jedem echten VAL-Lauf zunächst den historischen P3-CRE1-Parity-Checkpoint über `.github/continuum/cre-parity.mjs`, prüft dessen SHA-256 und baut anschließend **die echten Kotlin-CRE1-Quellen** aus `app/src/main` zusammen mit den Forschungstests unter `app/src/test`. Das historische Android-Produktivmodul wird dabei nicht verändert.

## Feste SHA-256-Gates

| Objekt | Erwarteter SHA-256 |
|---|---|
| DEV/VAL-TSV aus `M5Data` | `7d70d20f79f03e10ffb4cef76292cd52201e66299aca42c8a1287ebf2b327dd2` |
| P3-Parity-CRE1 (rekonstruiert in Scratch, nicht im Repository) | `2290194d25cc98cf6cc42e6b57f5ddcb2105ba14115e2e5295ad50c906f10db6` |
| tatsächlich verwendetes Runner-Skript (Git-Blob-SHA1) | `07349adca4e47a101bd131f51a711192a729fd87` |

Erst nach der Hashprüfung startet das Modell-VAL-Gate; der SEALED-Holdout wird weder erzeugt noch geöffnet. Fail-Closed: das Skript verwendet `set -euo pipefail` und setzt keine Pass-Markierung nach fehlerhaftem Zwischenschritt.

## Lokale Ausführung und tatsächliche Befunde

- `bash -n` auf byteidentischem Skript (vor GitHub-Push gegen Blob-SHA geprüft): Exit **0**.
- Eigenständiges Shell-Negativszenario A: Mock-Node erzeugt absichtlich falschen CRE1-Checkpoint; Runner meldet `SHA_MISMATCH`, Exit **1**, **kein** Gate-Ergebnisfile.
- Eigenständiges Shell-Negativszenario B: `M5_OUT` zeigt in die Repository-Wurzel; Runner verweigert mit `M5_OUT must be outside the source repository`, Exit **2**.
- Wiederholte echte `kotlinc-jvm 1.9.0`-Kompilation der Git-Blob-identischen M5-Quellen; nur für die CRE1-Signaturverknüpfung wurde ein **temporärer Stub** benutzt. Damit keine falsche Modellbehauptung.
- `M5DataSelfTest`: **12/12 PASS** (Exit 0).
- `M5BlindGateSelfTest`: **8/8 PASS** (Exit 0).
- `M5Data generate`: **256 Gruppen**, 2.304 Zeilen, DEV/VAL SHA-256 wie oben (Exit 0).
- `M5LexicalAudit`: DEV-L 64/64, VAL-L 64/64, DEV-S 0/64, VAL-S 0/64 (Exit 0). Dies ist ein bewusst synthetischer **lexikalischer** Referenztest, kein semantischer Nachweis.
- Die ursprünglichen M4-42 JUnit-Tests wurden hier nicht neu gestartet; diese Belegkette bleibt gesondert.

## Ausführung auf einem vollständigen, lokalen Repository-Checkout

Auf dem unveränderten Branch `claude/continuum-jarvis-exp20-p2-kxrxy2`:

```bash
export KOTLINC=/PFAD/ZU/kotlinc/bin/kotlinc
export JUNIT_JAR=/PFAD/ZU/junit-4.13.2.jar
export HAMCREST_JAR=/PFAD/ZU/hamcrest-core-1.3.jar
export M5_OUT=/tmp/continuum-m5-authentic
bash scripts/exp20-m5-cre1-real-gate.sh
```

Die drei Umgebungsvariablen müssen auf tatsächlich installierte, geeignete lokale Dateien zeigen; die angeführten `/PFAD/ZU/`-Einträge sind **nur Dokumentation**, keine behauptete lokale Installation. Der Runner prüft die Pfade, ehe er Modelle vorbereitet. `M5_OUT` muss außerhalb des Git-Checkouts liegen. Das Skript ist mit `bash` aufzurufen, auch wenn GitHub Contents API beim Anlegen keinen Ausführungsmodus setzt.

**Abnahme nach echter Ausführung:** vollständige Console-Logs, Node-Checkpoint-SHA, Host-Build-SHA, alle Regressionen, DEV/VAL-SHA sowie `$M5_OUT/results/cre1-blind-gate.txt` als unveränderte Forschungsbelege sichern. Ist das VAL-Gate INVALID, wird WP3/SEALED ausdrücklich **nicht** gestartet. Ist es PASS, ist lediglich die Datensatz-Gültigkeitsprüfung erfüllt; der unabhängige Semantikgewinn bleibt offen.

## Unveränderte Sicherheitsgrenzen

`main` und `app/src/main` nicht verändert, kein automatischer Merge, keine Gewichtsübernahme, kein neuer Shadow-Hook, keine echte Android-Fold7-Behauptung, kein unsichtbares Training und keine Rückwirkung auf ToolGate/OwnerGate. Der Runner nutzt ausschließlich lokale, bereits im Projekt vorhandene Werkzeuge; keine externen kostenpflichtigen KI-APIs und keine CI-Pipeline.

Siehe die [erste M5-Registrierung](../../research/exp20/EXP-20-M5-PREREGISTRATION-20261030.md), das [vor dem echten Lauf registrierte Tie-Gate](../../research/exp20/EXP-20-M5-BLIND-GATE-PREREG-ADDENDUM-20261030.md) sowie den [M5-Code-/Leakage-Audit](M5-WP1-HOST-AND-LEAKAGE-AUDIT.md).

## Wiederholbarer Fail-Closed-Selbsttest (nachträglich ergänzt, vor echtem Modelllauf)

Neu im gleichen Forschungsbranch: `scripts/exp20-m5-runner-selftest.sh`, Git-Blob-SHA1 `12fc0a1cd092be72b516a747378cd0269617956c`. Das Skript verwendet für drei Negativfälle ein temporäres Mock-Repository und Mock-Node; es wird weder CRE1 noch ein versiegelter Datensatz geöffnet. Auf dem lokalen Host wurde `bash -n scripts/exp20-m5-runner-selftest.sh` mit Exit 0 geprüft und `bash scripts/exp20-m5-runner-selftest.sh` mit Exit 0 ausgeführt:

1. Manipulierter CRE1-Checkpoint: `SHA_MISMATCH`, kein Gate-Bericht.
2. Scratch innerhalb des Repositorys: Ausführung abgewiesen.
3. Fehlender Kotlin-Compiler: Abbruch vor Checkpoint-Erzeugung.

Ergebnis `M5_RUNNER_FAIL_CLOSED_TESTS=3/3` (Host), eindeutiger Git-Blob-Readback. Befehl zur Wiederholung im vollständigen Repository: `bash scripts/exp20-m5-runner-selftest.sh`. Dieses Selbsttestergebnis ersetzt **nicht** den tatsächlichen Node/Kotlin-CRE1-VAL-Lauf.
