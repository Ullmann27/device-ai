# Fortsetzung KI-Entwicklung (Opus, 23.10.2026) – Stand für jede nachfolgende Instanz

Dieses Dokument reicht zum Wiedereinstieg allein aus GitHub. Nichts hier hängt an einer lokalen Sitzung.

## Arbeitsteilung

- **Astra:** Android-Oberfläche, Hologramm, Design.
- **Opus (dieser Strang):** KI, also LM1, CCC1, Coding-Agent, Worker und Forschung.
- Opus bearbeitet nicht: `AgentActivity.kt`, `ContinuumHologramView.kt`, `app/src/main/java/at/heinz/deviceai/ui/**`, `AndroidManifest.xml`, `app/build.gradle.kts`, Release- und Render-Skripte.
- Die Schnittstellen für Astra stehen in `docs/CONTINUUM-OPUS-CCC1-INTERFACES-20261023.md`
  (`docs/CONTINUUM-OPUS-ASTRA-HANDOFF-20261023.md` ist seit PR #97 die gemeinsame Übergabe der Integration).
- **Aktueller Stand und Übergabe:** `docs/CONTINUUM-OPUS-FIRST-BREAKTHROUGH-20261023.md`.

## Zweige (alle auf GitHub)

| Zweig | PR | Inhalt | letzter Stand |
|---|---|---|---|
| `opus/ccc1-e2e-20261023` | Draft gegen #97 (`integration/continuum-current-review-20261023`) | **aktueller Arbeitszweig**: CCC1-Reparatur Ende zu Ende über die Termux-Brücke, Android-Verdrahtung, Belege, Testfixes | siehe `git log` |
| `opus/continuum-intelligence-next-20261023` | #92 (Draft, Basis `astra/render-mentor-apk-builder-20261019`) | früherer Arbeitszweig: `9fb4361c` (signierte Quelle 0.3.16 `6706da14` plus Release-Protokoll) und kontrolliert übernommene KI-Forschung; in #97 enthalten | siehe `git log` |
| `opus/continuum-language-core-20261022` | #75 | LM1, Schritt 7, LM1-Runtime, CCC1 | `6919264d` |
| `opus/continuum-neural-frontier-lab-20261022` | #82 | NFC1-Experimente NF-1 bis NF-6b-lite | `14702b0d` |
| `opus/lm1-checkpoints-20261022` | – (Datenzweig, nie mergen) | 128+2 LM1-Checkpoints, `tokenizer/tokenizer-v512-94375c73….lmbp` | `d2304a76` |
| `opus/nf-checkpoints-20261022` | – (Datenzweig, nie mergen) | 198 NFC1- und 54 NF-7-Checkpoints (NFC2) mit `MANIFEST.tsv` | `fab239d3` |

## Was real läuft (geprüft)

| Baustein | Ort | Prüfung |
|---|---|---|
| LM1-Runtime: prüfendes Laden, schrittweises minGRU (bitgleich), Grenzen, Abbruch | `learning/lm/LmRuntime.kt` | JUnit 5/5; echter Checkpoint 0,41 ms/Token (x86_64, ein Thread) |
| LM1-Forschungskern und Schritt 7 | `learning/lm/*` (Haupt- und Testquellen) | JUnit |
| CCC1 Reasoner ↔ Coding-Agent mit Journal, Gate, Verifier, Recovery | `.github/continuum/ccc1-*.mjs` | Node 14/14, davon 9 mit echten Coding-Jobs |
| CCC1-Worker auf `runTeamleaderLoop`: Erfahrungsprotokoll, CLI mit JSON-Status, Task-Compiler `compile` | `ccc1-worker.mjs`, `ccc1-cli.mjs`, `ccc1-experience.mjs` | Node-Tests, zwei echte Läufe auf einem Repository-Klon (LM1-Bericht, Abschnitt 10) |
| Vorprüfung v2: Repository-Lesezugriff, Netz- und Signalsperre, nicht prüfbare Alternativen werden gemeldet | `behaviour-probe*.mjs` | Node-Tests |
| Code-Index mit Verhaltens-Fingerabdrücken, Clustern und Fast-Dubletten; Cache je Import-Hülle | `code-index.mjs` | Node-Tests; Messung am echten Repository (LM1-Bericht, Abschnitt 10) |
| NF-7: Restmodell gegen KN-7 auf NFC2 | `learning/nf/NfResidual.kt` (Testquellen) | JUnit 20/20; gelaufen, **widerlegt** (NF-Bericht, Abschnitt 10.11); 54 Checkpoints auf dem Datenzweig (`fab239d3`) |
| Research-Radar (Werkzeug) und Radar-Stand 23.10. | `research-radar.mjs`, `docs/CONTINUUM-RESEARCH-RADAR-20261023.md` | Node-Tests; Quellen von Hand geprüft |

## Was noch nicht mit der App verbunden ist

- **LM1-Runtime:** keine Android-Verdrahtung. Die Textqualität reicht für Antworten an den Besitzer nicht.
- **CCC1-Worker, Task-Compiler, Code-Index:** laufen als Node-Werkzeuge. Die Termux-Brücke der App kennt sie noch nicht; das Muster dafür steht in der Übergabe an Astra.
- **Auf dem Gerät gemessen:** nichts aus diesem Strang. Es gibt weder ARM64-Messungen noch eine APK.

## Messergebnisse (Kurzfassung, Details in den Berichten)

**LM1**
- TEST: LM1-GM 2,439 BPB, KN-7 2,150, CDX1 4,295.
- Schritt 7: Anweisungen auf Zufallsniveau, Schleifen.

**NFC1**
- NF-4 erfüllt seine Regel.
- NF-5b ist widerlegt: 1 falsche Bestätigung.
- NF-6b-lite verfehlt die Schwelle knapp (Mischung 1,93 BPB).

**NF-5c:** keine ungewollte Bestätigung; 12 von 13 Mehrdeutigkeiten erkannt; alle vier Regeln gestützt (NF-Bericht, Abschnitt 10.10).

**NF-7:** beide Regeln widerlegt. Die Residual-Mischung ist 0,010–0,017 BPB schlechter als die unabhängige, KN-7 liegt bei 1,8856. Der NFC2 TEST wurde **nicht** ausgeführt und darf nur nach einer neuen Vorregistrierung benutzt werden.

**Worker**
- Erster echter Lauf: 2 bestätigte Aufträge, 1 Rückfrage, 0 Fehler.
- Dabei gefunden: eine unsichtbare Alternative. Behoben in Vorprüfung v2.

## Laut Besitzer (23.10.2026, von Opus nicht geprüft)

- Die bevorzugte gemeinsame Integrationslinie ist PR #97. Sie verbindet PR #95 (Cognitive Spine u. a.) mit PR #96 (Fold7-Chat-Eingabe).
- Der Auftrag „Operation First Breakthrough“ setzt CCC1 End-to-End über App → Termux → CCC1 → Verifier → Entwicklungszentrum an die erste Stelle. Das ist der nächste Schritt für diesen Strang.
- Zuerst die Einstiegsdokumente des Auftrags lesen. Sie liegen auf der Integrationslinie, nicht auf diesem Zweig.

## Nächste Schritte (in dieser Reihenfolge)

1. **NF-8:** ∞-Gramm mit Suffix-Array gegen KN-7 und als Mischpartner vorregistrieren (Radar, Idee a).
2. **Ja/Nein-Testvorschläge statt freier Rückfrage:** ein Feld `proposedTests` (Radar, Idee c). Die Knöpfe baut Astra.
3. **Selbst erzeugte, prüfbare Aufgaben aus dem Code-Index:** eine lernende Kandidatenrangfolge nur aus Quittungen (Radar, Idee d).
4. **Zusammen mit Astra:** Termux-Operationen für `compile`, `submit` und `status --format json`. Danach Messungen auf dem Fold.

## Sicher wieder einsteigen

- Arbeitszweig auschecken und nie auf `main` schreiben. Keine Force-Pushes, keine Besitzergewichte verändern, keine APK installieren.
- **LM1/NF-Build:**
  - kotlinc mit `learning/LearningSnapshot.kt`, `learning/TinyContextDecoder.kt`, allen `.kt` aus `learning/lm` (Haupt- und Testquellen) und, für NF, `learning/nf`;
  - JUnit 4.13.2 und hamcrest 1.3 im Klassenpfad.
- **Checkpoints:** Datenzweig auschecken und `checkpoints/` als `store=` übergeben. Der Tokenizer liegt unter `tokenizer/`.
- **NFC2 nur mit `corpus=nfc2`:** Der Code bricht ab, wenn VALIDATION oder TEST vom Pin abweichen.
- **NFC2 TEST:** höchstens einmal, nur nach der Regel in Abschnitt 8f und nur mit `hybrid-test … once=NF7`. Die Sperrdatei im Checkpoint-Ordner verhindert einen zweiten Lauf.
- **GitHub Actions** bekommen auf diesem Konto keinen Runner. Alle Prüfungen laufen lokal; ihre Ergebnisse stehen in Commits und Berichten.
