# Continuum M5 / WP1 – tatsächlicher Host-Nachweis und Fehlerprotokoll (30.10.2026)

**Status: DEV/VAL-Labor reproduziert; CRE1-Medoid-Gate NOT_RUN; SEALED_HOLDOUT NOT_CREATED/UNOPENED; Android/Fold7 NOT_RUN.**

## Reihenfolge und Quellidentität

1. Vorregistrierung **vor** Datengenerierung: Commit `05a3833237b2b4889329fa7ab0190a957041e2a3`.
2. Forschungsdateien unter `app/src/test/java/at/heinz/deviceai/research/exp20/m5/` angelegt; keinerlei `app/src/main`-Eingriff.
3. Host-Kompilation **lokal tatsächlich ausgeführt** mit `kotlinc-jvm 1.9.0`, OpenJDK 21.0.12.1. Abweichung zum früheren Opus-Labor (kotlinc 2.0.20) ausdrücklich vermerkt; keine pauschale Gleichsetzung.
4. Defekte Vorstufen protokolliert und vor Freeze korrigiert:
   - **232** exakte Memory-Texte kamen sowohl in DEV als auch VAL vor. Korrektur: disjunkte Namensmengen, harte Split-Overlap-Validierung.
   - Gleichmäßig zufällige Positionen waren **nicht exakt gleichverteilt**: ein einfaches positionsbasiertes C1-Heuristikmodell hätte VAL `23/128 = 17,97 %` erzielen können und damit die registrierte Blindschwelle von 17,5 % verletzt. Korrektur: orthogonales, deterministisches 8×8-Stratifikationsdesign mit achtmal jedem Zielpositionseintrag pro Typ/Split.
5. Nach der Korrektur: 12/12 eigenständige Host-Selbstprüfungen erfolgreich; anschließend die vier Jaccard-Referenzgruppen erfolgreich auditiert.

## Exakte Reproduktionsbefehle

Auf PR #256 mit Kotlin/JDK auf einem Host:

```sh
M5=app/src/test/java/at/heinz/deviceai/research/exp20/m5
mkdir -p /tmp/continuum-m5
kotlinc "$M5/M5Data.kt" "$M5/M5DataSelfTest.kt" "$M5/M5LexicalAudit.kt" -include-runtime -d /tmp/continuum-m5/m5.jar
java -cp /tmp/continuum-m5/m5.jar at.heinz.deviceai.research.exp20.m5.M5DataSelfTest
java -cp /tmp/continuum-m5/m5.jar at.heinz.deviceai.research.exp20.m5.M5Data generate /tmp/continuum-m5/dev-val.tsv
java -cp /tmp/continuum-m5/m5.jar at.heinz.deviceai.research.exp20.m5.M5LexicalAudit /tmp/continuum-m5/dev-val.tsv
sha256sum /tmp/continuum-m5/dev-val.tsv
```

Kompilierung und alle vier Java-/SHA-Läufe im lokalen Arbeitscontainer mit Exit **0**. Keine vollständige Gradle-/Android-Suite ausgeführt. Das Material im GitHub-Commit stammt von den *bitgenau lokal getesteten Kotlin-Dateien*; die Git-Blobs wurden nach dem Push zurückgelesen.

## Ergebnis DEV/VAL (kein unabhängiger Holdout)

- Gruppen: **256**, davon DEV L 64, DEV S 64, VAL L 64, VAL S 64; **2304** TSV-Datensätze (8 Memories + Query pro Gruppe).
- TRUE-Positionen: in jedem (Split, Typ) **acht Treffer pro Kandidatenposition**; eine positionsblinde Auswahl ergibt strukturell genau 1/8.
- DEV/VAL: **0** identische Memory-Texte nach Korrektur.
- JACCARD Top-1 bei wörtlichen L-Abfragen: **DEV 64/64; VAL 64/64**.
- JACCARD Top-1 bei synonymbasierten S-Abfragen: **DEV 0/64; VAL 0/64**.
- Frage-blinde lexikalische Medoid-Kontrolle (strikte Top-1, Gleichstand = Fehler): **DEV 0/128; VAL 0/128**. Das ist nur eine schwache Vorabprüfung. Insbesondere darf dieses Nullergebnis infolge vieler Gleichstände **nicht** als CRE1-Medoid-Gate oder als Gütesiegel für einen Datensatz bezeichnet werden.
- **12/12** Selbsttests: Gruppenzahl; deterministische Generierung; Balancierung; DEV/VAL-Disjunktheit; keine P3-Formulierungen; Zielposition; einzigartige Memories; lexikalische Blindprüfung; Roundtrip; stabile Datei-SHA; beschädigtes Gold wird verworfen; fehlerhafter TSV-Header wird verworfen.

`dev-val.tsv` SHA-256 (lokal generiert, nicht als riesige Datei committed):
`7d70d20f79f03e10ffb4cef76292cd52201e66299aca42c8a1287ebf2b327dd2`

Verifizierte Git-Blob-SHAs der Kotlin-Quellen:
- `M5Data.kt`: `5f2951be61754cd2ccfe0564ddaa8738636b9b53`
- `M5DataSelfTest.kt`: `ec00111f20fc62faec4a9b1b1aa9bdb88aa5bd50`
- `M5LexicalAudit.kt`: `ffa17f40922ce1e83bd5ab5e0017eb5308dc750e`

## Gates vor WP3

1. Mit einem **genau versionierten CRE1-Checkpoint** die vorgeschriebene QUERY_BLIND_MEDOID-Prüfung auf **VAL** durchführen; bisher: **NOT_RUN**. Ein Ausfall macht den Datensatz ungültig. Ein positiver Lauf ist noch kein Semantikbeleg.
2. Unabhängige Partei/andere getrennte KI muss den **fremden SEALED-Holdout** erstellen. Nicht diesen Generator als unabhängigen Test deklarieren.
3. `HYBRID_CRE1` mit fixierten Parametern zuerst gegen `HYBRID_LEX`, `JACCARD` und Query-blind abgleichen. Kein Retuning oder Prüfregelwechsel nach Holdout.
4. Keine Produktivänderung, kein Main-Merge, kein Shadow-Anschluss und keine neuen Gewichte ohne positive externe Evidenz und Freigabe.

**Forschungslektion:** Das erste interne WP1-Dataset selbst hatte zwei systematische Verzerrungen. Die Korrektur erfolgte *vor* Kandidaten-Freeze und vor jeder SEALED-Öffnung; die gescheiterten Vorstufen bleiben hier explizit nachvollziehbar.
