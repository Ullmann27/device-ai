# M5 – gesicherte Host-/SDK-Belege, kein Gerätelauf

Start 331e2d25, endgültiger Code-Freeze **fc1d996135d98165a7102d0a4e67b9ff34a0b1f0**.
[Draft-PR109](https://github.com/Ullmann27/device-ai/pull/109), kein Merge/Signieren/Installieren.

- `summary.json`: echte Endzahlen; `tests/`: wirkliche rote Gegenbeispiele und grüne Abschlüsse.
- **1085/1085 Node**, **60/60 Backend**, **913/913 Android-JUnit**, keine Fehler/Skips.
- Davon **12 Robolectric-Widgetfälle**; zusätzlich vier vorhandene Kotlin-Checks, SWB2/CRE1-Parität und
  erneute echte Signer-/aapt2-/CCC1-Prüfung der unveränderten M4-APKs bestanden.
- `android-junit-xml.tar.gz`: alle tatsächlichen JUnit-XML-Ergebnisse; `android-lint.xml`: Haupt-App-Lint,
  gemäß bestehendem Gradleprofil `NewApi`, kein umfassendes Sicherheitsaudit.
- CTX3: unabhängiger einmaliger TEST3, **FAIL**, keine Promotion. Report/Freeze/Korpus in `docs/research/`.
- Bekannte ursprüngliche CTX1-Regression erneut920/0 (B1),80 Rückfragen/80 Enthaltungen; alte Originalberichte unverändert.
- Bekannte TEST2-Fehler: alle27 exakt reproduziert, Taxonomie und ausführbare Diagnose unter `diagnostics/`.
- Sitzungstest zusätzlich in zwei echten getrennten JVMs: `tests/process-restart.log`; ON-Schalter bleibt,
  Entitätsfokus geht verloren und verlangt eine Rückfrage. Keine Android-Prozessabnahme daraus ableiten.

Reproduktion im Repository, mit JDK17, Gradle8.11.1, SDK36/build-tools35+36, Kotlin2.4 und vorhandenen Abhängigkeiten:

```sh
npm --prefix backend ci --ignore-scripts --no-audit --no-fund
env -u NODE_TEST_CONTEXT node --test --test-reporter=tap --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
env -u NODE_TEST_CONTEXT node --test --test-reporter=tap --test-concurrency=2 backend/test/*.test.js
node .github/continuum/contrastive-parity.mjs prepare /tmp/continuum-swb-m5
node .github/continuum/cre-parity.mjs prepare /tmp/continuum-cre-m5
export CONTINUUM_CONTRASTIVE_PARITY_DIR=/tmp/continuum-swb-m5
export CONTINUUM_CRE1_PARITY_DIR=/tmp/continuum-cre-m5
scripts/continuum-m5-research-checks.sh /tmp/new-m5-output --opened-test3
node .github/continuum/contrastive-parity.mjs verify /tmp/continuum-swb-m5
node .github/continuum/cre-parity.mjs verify /tmp/continuum-cre-m5
```

Der Ausgabeordner muss neu sein, damit Originalberichte nicht versehentlich überschrieben werden. Kein Code ist
verschlossen; Wiederholungen des jetzt geöffneten TEST3 sind Regressionen, keine neuen blinden Nachweise.
Die komplette Toolchain muss zunächst installiert sein; die obenstehenden SDK-Läufe verwenden danach offline
Gradle. Offizielle Download-SHAs und Toolversionen stehen im vorhandenen M4-Bundle; dieselbe JDK-/SDK-Installation.
Keine maschinengebundenen privaten Skripte, Zugangsdaten oder kompilierten Stubs sind erforderlich.

Der Helfer exportiert den echten Testclasspath nach `classpath.txt`. Damit lassen sich die vier Checks und der
Prozessnachweis starten; öffentliche Ressourcen müssen zusätzlich auf dem Classpath liegen:

```sh
kotlinc docs/evidence/sol-m5-20261024/diagnostics/ShadowProcessProbe.kt -cp "$(cat /tmp/new-m5-output/classpath.txt)" -d /tmp/m5-probe-classes
probe_dir=$(mktemp -d)
java -cp "/tmp/m5-probe-classes:$(cat /tmp/new-m5-output/classpath.txt):app/src/test/resources:app/src/main/resources" ShadowProcessProbeKt "$probe_dir" prime
java -cp "/tmp/m5-probe-classes:$(cat /tmp/new-m5-output/classpath.txt):app/src/test/resources:app/src/main/resources" ShadowProcessProbeKt "$probe_dir" resume
```

Baseline:1085 Node und889 SDK-JUnit bestanden. Der erste End-Node-Lauf hatte zwei veraltete statische Assertions
zur Runtime-Signatur; nach Prüfung der tatsächlichen Gesprächs-ID-Verkabelung sind20/20 im Modul und1085/1085
insgesamt grün. Original- und reparierter alter CTX2-Gesamtrunner können auf TEST2 im unveränderten B1 abstürzen;
B1 wurde nicht verändert, um eine günstige Zahl zu erhalten. Repariertes B2 separat ausgewertet.

Die Kotlin-Checks enthalten die bereits vorhandene synthetische DEV-Trainingsdemo. Deren temporäre Gewichte sind
keine Besitzergewichte und kein neues M5-Lernergebnis. In dieser Runde kein neues Modelltraining, kein 30-Sekunden-
Übungscontroller und kein ungeprüfter Gewichtswechsel.
