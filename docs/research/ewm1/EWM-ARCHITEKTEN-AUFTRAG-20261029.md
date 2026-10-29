# Bauauftrag des Architekten – Continuum-Gehirn anschließen und weiterbauen (29.10.2026)

Von: Opus 5.5 (Forschung, PR #224, Zweig `opus/continuum-brain-research-20261029`).
An: Codex (Haupt-App `at.heinz.deviceai.continuumlocal`, PR #223; Gemma-Profiling PR #226), Astra (UI/Manifest/Gradle),
Sol bzw. nächste Opus-Instanz (Forschung), Heinz (Freigaben, Fold7).

Dieser Auftrag ist verbindlich gegliedert: **Bauplan → Arbeitspakete (AP) in fester Reihenfolge → Abnahme je AP →
Bauordnung**. Jedes AP ist klein, einzeln mergebar und hat einen Prüfbefehl. Kein AP darf ein späteres vorwegnehmen.

## 1. Was fertig und gemessen ist (Fundament)

| Baustein | Datei (heute im Test-Quellset `app/src/test/java/at/heinz/deviceai/intelligence/ewm1/`) | Nachweis |
|---|---|---|
| Diskurs-Gedächtnis (Pronomen, Rollen/Aliase, Aktualität, Enthaltung, Rückfrage) | `Ewm1Lexicon`, `Ewm1EvidenceWorkspace`, `Ewm1Workspace` | TEST4: falsch 11 vs B0 58 (H1) |
| Aussagenprüfer für Modellantworten | `Ewm1ClaimCheck` | TEST4: 48/49 erkannt, 0 Fehlalarme (H4) |
| Quellenindex über das Wissensarchiv | `Ewm1SourceIndex` | Gen16-Archiv: 98,4 % vs 72,5 % (H5) |
| **Gehirn-Router Gedächtnis ↔ Quellen** | `Ewm1Brain` | **TEST5: 39 richtig / 0 falsch vs heutiger Chatpfad 30 / 18 (H6)** |
| Reparaturen der TEST4-Fehlerklassen | Workspace/Lexikon | TEST5: 20/20 vs EWM1 13/20 (H7) |

Ergebnisse: `EWM1-RESULT-20261029.md`, `EWM2-RESULT-20261029.md`. **Nichts davon ist im Produktpfad, nichts auf dem Fold7 bestätigt.**

## 2. Bauplan (Zielbild)

```
Frage ─► Ewm1Brain.ask(frage)                       [eine Instanz je conversationId]
          ├─ persönliches Thema / eigene Person / eigene Definition ─► Ewm1Workspace ─► B0-Rang (ContinuumMemoryDialogue.rank, SWB1 unverändert)
          ├─ allgemeine Definitionsfrage ─► Ewm1SourceIndex ─► ContinuumKnowledgeArchive (Gen16-Archiv nur nach Import, optional)
          └─ sonst ─► ABSTAIN („weiß ich nicht“) / CLARIFY (Rückfrage)
       ─► Answer.render()  {owner_memory | public_sources | rule}   ← einzige Evidenz für Gemma 4
       ─► Gemma-4-Antwort (nur wenn generiert wird)
       ─► Ewm1ClaimCheck.check(antwort, belege) ─► unbelegte Details markieren oder 1 begrenzter Korrekturlauf
       ─► LocalDialogueQualityGate (unverändert) ─► UI
Rückfall jederzeit: heutiger Pfad (B0 → Archiv-Fallback, AgentActivity.kt ~Z. 2796–2822 auf Basis b47f4fac).
```

## 3. Arbeitspakete

**AP-1 · Umzug in den Produktcode (Codex)** – Ziel: dieselbe Logik als Produktionsklassen, ohne Verhaltensänderung.
- Neues Paket `at.heinz.deviceai.intelligence.ewm` in `app/src/main`: `Ewm1Lexicon`, `Ewm1EvidenceWorkspace`,
  `Ewm1Workspace` (+`Ewm1Pack`), `Ewm1ClaimCheck`, `Ewm1SourceIndex`, `Ewm1Brain`. Nur Paket/Sichtbarkeit ändern; die
  `EWM1_TRACE`-Umgebungsabfrage entfernen. Runner (`Ewm1Experiment`, `Ewm1SourceExperiment`, `Ewm1Gen16ImportCheck`,
  `Ewm2Experiment`) und Korpora bleiben im Test-Quellset.
- Abnahme: 19 Unit-Tests grün; `Ewm2Experiment DEV ewm2-dev5-20261029.json` → K1 12 richtig/0 falsch/7 enthalten,
  `ewm2-dev5b` → 20/0/6; DEV4 EWM 42/0, TEST3 145/7. Ein TEST5-Lauf ist hier **nur Reproduktion** (identischer Bericht
  erwartet), nie Abstimmung. AP-1 wird erst mit AP-2 gemergt (kein unerreichbarer Code in `main`).

**AP-2 · Schalter und Schattenbetrieb (Codex, Hook in `AgentActivity` mit Astra abstimmen)**
- `ContinuumBrainMode { OFF, SHADOW, ON }`, gespeichert in den Einstellungen, **Standard OFF**.
- Je `requestSessionId` ein `Ewm1Brain`; neu aufbauen, wenn `core.syncOwnerFacts(...)` die Faktenmenge ändert.
  Fakten = genau die bestätigten Besitzerfakten, die B0 sieht (`id → content`), `b0Rank = { q, s -> ContinuumMemoryDialogue.rank(swb1, q.take(1000), exps.filter { it.id in s }).map { it.id } }`.
- SHADOW: nach `cognitive.dialogue`, vor `archiveHit` berechnen; nur Entscheidung/Route/Grund/Latenz/Fakt-IDs lokal
  protokollieren (keine Texte), Antwort unverändert. Ausnahme in `runCatching` → heutiger Pfad.
- Abnahme: JVM-Test „SHADOW ändert keine Antwort“; Latenz p95 auf dem Gerät gemessen (Ziel ≤ 25 ms).

**AP-3 · Lese-Schnittstelle des Archivs (Codex)**
- `ContinuumKnowledgeArchive` braucht einen begrenzten Lesezugriff (≤ 4 096 Sätze) oder einen beim `importZip`
  aufgebauten Index; `Ewm1SourceIndex` einmal je Archivstand bauen. Leeres Archiv = gültiger Zustand.
- Abnahme: 556-Satz-Archiv (synthetischer Nachbau im Test, keine Gen16-Inhalte im Repo) → Index fertig, Abruf p95 gemessen;
  App startet und antwortet ohne Archiv.

**AP-4 · ON-Betrieb (Codex, erst nach Heinz' Freigabe aus AP-7)**
- `MEMORY` → belegte Antwort mit zitierten Fakten; `SOURCE` → vorhandene, klar gekennzeichnete Quellen-Antwort
  (Titel, Lizenz, „ungeprüfte Quelle“); `ABSTAIN` → ehrliches „Das weiß ich noch nicht“ + Angebot zu lernen; `CLARIFY` → Rückfrage.
- Abnahme: DEV5/DEV5b über den Produktionsadapter ergeben dieselben Zahlen wie im Runner.

**AP-5 · Gemma 4 mit Evidenzpaket und Aussagenprüfung (Codex, mit PR #226)**
- Prompt-Evidenz = `Answer.render(factText)` statt `LocalGroundedRecall.render()`; danach `Ewm1ClaimCheck.check`.
- Abnahme: eigene Vorregistrierung **EWM3-G** mit versiegeltem Test **vor** der Messung; Kennzahlen auf dem Fold7:
  falsche Details je Antwort, Fehlalarme, Latenz. Ohne Gerätelauf keine Behauptung über Gemma.

**AP-6 · Generation 16 auf dem Fold7 (Heinz + Codex)**
- Archiv über „Wissensarchiv · Backup importieren“ (`processArchiveBackup` → `importZip`, transaktional). **SWB1 nicht
  einspielen** (Produktions-Gate verwirft es; bleibt Forschungsreferenz außerhalb des Repos). Quellen bleiben
  `SOURCE_OBSERVATION_UNVERIFIED`, werden nie Besitzerfakten; Widerrufe bleiben widerrufen.
- Abnahme: Importmeldung mit 556 Sätzen (Screenshot/Log); erst dann Status „auf dem Fold7 bestätigt“.

**AP-7 · Fold7-Validierung im Schatten (Heinz/Tester)**
- SHADOW einige Tage; zusätzlich die 45 DEV-Fragen aus `ewm2-dev5` und `ewm2-dev5b` (nie TEST-Sätze) mit passenden
  Fakten durchspielen; Tabelle K0 vs K1 (richtig/falsch/enthalten, Latenz).
- Freigabe für ON nur, wenn K1 nicht mehr Falsche hat als K0 und kein Quellentext als Besitzerfakt erscheint.

**AP-8 · Forschung EWM3 (Sol/Opus)** – offene Punkte nach Priorität:
1. H2 Zwei-Schritt-Verknüpfung (EWM1 nicht bestanden, 14/17).
2. Quellenabruf über Flexion/Plural und Weiterleitungen („Thunfische“ → „Thunfisch“).
3. Genusprüfung bei Ellipsen („auch einen?“ → passendes Nomen statt letztes Nomen).
4. Merkmals-Paraphrasen, „dort“/Ortsdeixis.
5. Gemma im Kreislauf (AP-5) als eigentlicher Intelligenzgewinn messen.
- Regel: neue Vorregistrierung + versiegelter **TEST6, geschrieben von einer anderen Instanz als der Implementierenden**
  (behebt die Ein-Autor-Grenze). TEST4, H5-TEST und TEST5 sind verbraucht und dürfen nie zur Abstimmung dienen.

## 4. Bauordnung (gilt für alle)

- Kein Force-Push, kein Merge nach `main` ohne Heinz, keine Änderungen an Besitzerzertifikat/Signaturschlüsseln.
- Keine persönlichen Rohdaten, Gen16-Inhalte oder Modellgewichte im Repository; Tests nur mit synthetischen Daten.
- Generation 16 wird nie zurückgesetzt, gelöscht oder überschrieben; SWB1 sind keine Gemma-Gewichte.
- Die App muss ohne die historischen Dateien vollständig funktionieren.
- CTX3 bleibt OFF/SHADOW; keine Beförderung von SWB1/CDX1/CRE1/I1/I2/CTX3 ohne Tests.
- Status immer getrennt: **analysiert · implementiert · getestet · auf dem Fold7 bestätigt**; fehlt das SDK: `SDK_UNAVAILABLE`.
- Kleine Commits, früh pushen, Draft-PR, Übergabe in `docs/research/omega/<AGENT>-HANDOFF.md`.

## 5. Prüfbefehle

```bash
# Gesamter Kotlin-Unit-Harness ohne Gradle/SDK (bewiesener Weg in der Cloud-Umgebung)
LANG=C.UTF-8 CONTINUUM_ANDROID_DEPS=<jar-ordner> CONTINUUM_JUNIT_JARS=<junit-4.13.2.jar>:<hamcrest-core-1.3.jar> \
  scripts/android-kotlin-unit-check.sh out/kunit
# EWM-Runner (Klassenpfad wie der Harness: out/kunit/main, out/kunit/test, Android-/JUnit-Jars, app/src/main/assets)
java -Dfile.encoding=UTF-8 -cp <klassenpfad> at.heinz.deviceai.intelligence.ewm1.Ewm2Experiment DEV docs/research/ewm1/ewm2-dev5-20261029.json
EWM2_DIFF=1 java … Ewm2Experiment DEV docs/research/ctx3-independent-test3-20261024.json   # Router darf 0 Züge ändern
java … Ewm1Experiment DEV docs/research/ewm1/ewm1-dev4-20261029.json
# Mit Android-SDK (hier nicht ausführbar: SDK_UNAVAILABLE)
./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.intelligence.ewm1.*'
```

Rückfall für jedes AP: Schalter auf OFF bzw. Commit zurücknehmen; der heutige Pfad bleibt unverändert erhalten.
