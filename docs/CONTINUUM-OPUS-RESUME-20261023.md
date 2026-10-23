# Fortsetzung KI-Entwicklung (Opus, 23.10.2026) – Stand für jede nachfolgende Instanz

Dieses Dokument reicht zum Wiedereinstieg allein aus GitHub. Nichts hier hängt an einer lokalen Sitzung.

## Arbeitsteilung

- **Astra:** Android-Oberfläche, Hologramm, Design.
- **Opus (dieser Strang):** KI, also LM1, CCC1, Coding-Agent, Worker und Forschung.
- Opus bearbeitet nicht: `AgentActivity.kt`, `ContinuumHologramView.kt`, `app/src/main/java/at/heinz/deviceai/ui/**`, `AndroidManifest.xml`, `app/build.gradle.kts`, Release- und Render-Skripte.

## Zweige (alle auf GitHub)

| Zweig | PR | Inhalt | letzter Stand |
|---|---|---|---|
| `opus/continuum-intelligence-next-20261023` | #92 (Draft, Basis `astra/render-mentor-apk-builder-20261019`) | **aktueller Arbeitszweig**: signierter Stand 0.3.16 (`6706da14`) plus kontrolliert übernommene KI-Forschung | siehe `git log` |
| `opus/continuum-language-core-20261022` | #75 | LM1, Schritt 7, LM1-Runtime, CCC1 | `6919264d` |
| `opus/continuum-neural-frontier-lab-20261022` | #82 | NFC1-Experimente NF-1 bis NF-6b-lite | `14702b0d` |
| `opus/lm1-checkpoints-20261022` | – (Datenzweig, nie mergen) | 128+2 LM1-Checkpoints, `tokenizer/tokenizer-v512-94375c73….lmbp` | `d2304a76` |
| `opus/nf-checkpoints-20261022` | – (Datenzweig, nie mergen) | 198 NFC1-Checkpoints mit `MANIFEST.tsv` | `6af3f19a` |

## Was real läuft (geprüft)

| Baustein | Ort | Prüfung |
|---|---|---|
| LM1-Runtime: prüfendes Laden, schrittweises minGRU (bitgleich), Grenzen, Abbruch | `learning/lm/LmRuntime.kt` | JUnit 5/5; echter Checkpoint 0,41 ms/Token (x86_64, ein Thread) |
| LM1-Forschungskern (Autodiff, BPE, minGRU/Transformer, Checkpoints) | `learning/lm/*` | JUnit 7/7 |
| LM1-Schritt 7 (Generierung/Anweisungen auf neuen Daten) | `LmStep7.kt` (Testquellen) | 7/7; Lauf dokumentiert |
| CCC1 Reasoner ↔ Coding-Agent mit Journal, Gate, Verifier, Recovery | `.github/continuum/ccc1-*.mjs` | Node 14/14 (davon 9 mit echten Coding-Jobs) |
| Ausführungsgeleitete Vorprüfung (NF-5b) und Mehrdeutigkeits-Rückfrage (NF-5c) | `behaviour-probe*.mjs` | 5/5 und 5/5 (inkl. echtem Coding-Job) |
| Research-Radar (Import, Dubletten, Vorschläge nur PROPOSED) | `research-radar.mjs` | 6/6 |

## Was noch nicht mit der App verbunden ist

- **LM1-Runtime:** Es gibt eine Schnittstelle, aber noch keine Android-Verdrahtung. Ein Worker-Aufruf ist geplant; Astra bekommt dafür eine API-Beschreibung, keine Änderung seiner Dateien.
- **CCC1:** läuft als Node-Laufzeit, noch ohne Anzeige in der App.
- **Sprachqualität:** LM1 ist für Antworten an den Besitzer nicht brauchbar (Schritt 7: Schleifen, keine Anweisungsverarbeitung).

## Messergebnisse (Kurzfassung, Details in den Berichten)

**LM1**
- TEST: LM1-GM 2,439 BPB, KN-7 2,150, CDX1 4,295.
- Neue Daten: LM1 2,695, KN-7 2,489, CDX1 4,69.
- Nächstes Wort 0,5 % gegen 2,3 % (KN-7). Anweisungen: Zufallsniveau.

**NFC1**
- NF-4 erfüllt seine Regel (ReLU-Sparsity, schneller).
- NF-5b: 12/14 im ersten Versuch, aber 1 falsche Bestätigung, also nach Regel widerlegt.
- NF-6b-lite: Mischung 1,93 BPB, die Schwelle knapp verfehlt.
- Die übrigen Hypothesen wurden zum Teil widerlegt; siehe NF-Bericht, Abschnitt 10.

## Laufende oder unterbrochene Versuche

- **NF-5c-Studie:**
  - Vorregistrierung: NF-Bericht, Abschnitt 8e.
  - Aufgaben: `docs/evidence/nf-20261022/nf5c-tasks.json`.
  - Lauf: `node scripts/nf5c-study.mjs <tasks> <out>`.
  - Ist Abschnitt 10.10 noch leer, ist der Lauf nicht dokumentiert. Dann gilt: neu starten, nie Ergebnisse rekonstruieren.

## Nächste Schritte (in dieser Reihenfolge)

1. NF-5c auswerten und dokumentieren.
2. **Residualmodell (Meilenstein D):**
   - Das neuronale Modell lernt den Rest von KN-7.
   - Dafür wird ein neuer Datensplit eingefroren. Der verbrauchte LM1-TEST wird nie wieder benutzt.
3. **Coding-Agent mit Worker verbinden (Meilenstein E):**
   - zuerst den vorhandenen Teamleiter, Scheduler und CCC1 prüfen; keine zweite Architektur;
   - dazu ein typisierter Task-Compiler und ein Code-Index.
4. Research-Radar aktualisieren (Meilenstein F).

## Sicher wieder einsteigen

- Arbeitszweig auschecken und dabei nie auf `main` schreiben. Keine Force-Pushes, keine Besitzergewichte verändern, keine APK installieren.
- LM1-Build: kotlinc mit `learning/LearningSnapshot.kt`, `learning/TinyContextDecoder.kt` und allen `.kt` aus `learning/lm` (main und test). JUnit 4.13.2 und hamcrest 1.3 im Klassenpfad.
- Checkpoints: Datenzweig auschecken und `checkpoints/` als `store=` übergeben. Der Tokenizer liegt unter `tokenizer/`.
- GitHub Actions bekommen auf diesem Konto keinen Runner. Alle Prüfungen laufen lokal; ihre Ergebnisse stehen in Commits und Berichten.
