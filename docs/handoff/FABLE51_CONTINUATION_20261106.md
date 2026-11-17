# Fable 5.1 – Fortsetzungsprotokoll JARVIS-Gesamtentwicklung (06.11.2026, Sitzungsabschluss)

Branch `claude/continuum-fable51-continuation-20261106`, Draft-PR #574, Basis = Opus' `claude/continuum-jarvis-cockpit-z7bo60`
@ `4d84caa8` (PR #565). **Letzter Code-Commit: `6f1444d645999bb1af439c1e95ecf1a51ecf3f37`; dieses Protokoll selbst ist der darauf folgende Commit `baf3ba5c27c1b81b287fc86470c1e0b9ca2ccad0`** (Remote = lokal, Arbeitsbaum sauber).

Alles in diesem Dokument ist HOST-Stand (JDK 21, kotlinc 2.0.21, `scripts/android-kotlin-unit-check.sh` außerhalb Gradle).
**Fold7 / One UI: REAL_DEVICE_NOT_RUN** für jede Zeile. Kein Main-Merge, keine Änderung an Signatur, Paket-ID
`at.heinz.deviceai.continuumlocal`, Publisher, Updatekanal, Version, Modellgewichten, Gedächtnisformaten; keine APK.
Die weitere Entwicklung beginnt erst mit dem ausdrücklichen Startbefehl des Besitzers.

## 1. Commit-Kette (alle gepusht)

| Commit | Inhalt | Teststatus (HOST) |
|---|---|---|
| `96cb70cf4`, `533c91597`, `27d343cc9` | Host-Toolchain: optionale classes.jar (Filament/gltfio 1.56, OpenCV 4.13, android-all-15), Annotation-Stub, Checks-Programme ohne `main` werden übersprungen statt als Fehler gezählt | Compile 0 Fehler außerhalb LiteRT-LM; Checks 4 PASS + 1 übersprungen |
| `fddc94e79` (M1) | Stimmenauswahl `cognitive/JarvisVoiceSelection`, Langdruck TON = Stimmen-/Sprachwahl, Persistenz `voice_name`/`voice_language`, Pitch 1.0 / Rate 0.96; Recherche `docs/research/omega/JARVIS_VOICE_ENGINES_20261106.md` | `JarvisVoiceSelectionTest` 7/7; Suite OK (3032) |
| `984fef542` (M2) | Lippen: `TEXT_PACE_FALLBACK` in `AvatarDirector` (nach 250 ms ohne Wortgrenze Text-Taktung 360 ms/Wort, Visemes + Satzenden, als Schätzung markiert; echte Wortgrenze übernimmt sofort) | `AvatarTextPaceFallbackTest` 6/6; Suite OK (3038 / 546 Klassen) |
| `fd2f1aa10` | Handoff-Zwischenstand | – |
| `be0e2b3c3`, `5e2e3aa87` (M3, Priorität 1) | `cognitive/WorkDecisionRecall` (lesende Sicht auf `CockpitWorkJournal` + Ledger-`ownerStatementHistory`), `Episode.correctedAt`, Abfang in `AgentActivity` vor `WorkRecall`, `RecallHits` an die Kugel, Nicken nur bei verstandener Korrektur/Weiterarbeit | `WorkDecisionRecallTest` 8/8 (RED: Komponente fehlte); Suite OK (3046 / 547) |
| `d9a53d339`, `385e4ab83` (M4, Priorität 2) | Verbundene Gespräche: `WorkDecisionRecall.parse` satzweise; `LocalConversationFollowUpPolicy` (Definition ohne Demonstrativ, strenge Folgefrage „Was bedeutet das für …?“, Modalverb-Anaphern, `benefitsFromPreviousOwnerTurn`), `LocalFollowUpTaskFrame` (Rückfrage-Hinweis bei Mehrdeutigkeit) | `ConnectedConversationCatalogTest` 7/7 (RED 5/7); Suite OK (3053 / 548); Schicht 3 (Modellantwort) NOT_RUN |
| `1c7e5071a` (Merge visual_scene_engineer `f97e33ae4`) | Arbeitsfenster-Tiefe: `ui/JarvisWorkWindowDepth` (neu, rein), `JarvisWorkWindow.open(depthLayers, recession)` + `drawDepth`, `JarvisWorkWindowHost.show(depthLayers, handover)`, `AgentActivity.presentDocumentScene/presentDraftScene` übergeben Lagenzahl (nach `doc.pageCount`) und Quellen-ImageView | `JarvisWorkWindowDepthTest` 6/6 (RED: 44 Compile-Fehler, Komponente fehlte); Suite erst nach Merge vollständig gelaufen (s. §3) |
| `6f1444d64` (Merge voice_engine_researcher `10af83621`) | Nur Dokument: `docs/research/omega/JARVIS_VOICE_QUALITY_20261106.md` (Zwischenstand, Vergleichstabelle, Host-Messung, Fold7-Protokoll, Quellen-URLs) | keine Codeänderung |

Die beiden Bot-Branches `claude/continuum-fable51-visual-20261106` (@ `f97e33ae4`) und `claude/continuum-fable51-voice-20261106`
(@ `10af83621`) bleiben unverändert auf dem Remote erhalten.

## 2. Heute abgeschlossene Meilensteine

- **M1 Stimmenauswahl** (Besitzer wählt Stimme und Deutsch/English per Langdruck auf TON; de-AT bevorzugt, stabil bei Gleichstand).
- **M2 Lippen-Text-Taktung** als dritter Fallback hinter PCM-Hüllkurve und Wortgrenzen (Mund bleibt nicht mehr geschlossen,
  wenn die Engine weder PCM noch `onRangeStart` liefert).
- **M3 WorkDecisionRecall** (Priorität 1): „Was haben wir gestern entschieden?“, „Nein, ich meinte die Entscheidung davor.“,
  „Was davon haben wir schon umgesetzt?“, „Dann mach beim letzten offenen Punkt weiter.“ werden aus Arbeitsjournal + bestätigten
  Besitzeraussagen beantwortet (kein drittes Gedächtnis, nur lesend; Weiterarbeit nur über bestehende Pfade).
- **M4 Verbundene Gespräche** (Priorität 2, Schichten 1–2 und 4): Beispielfrage des Besitzers („Wir hatten uns gestern für die zweite
  Möglichkeit entschieden, aber die Voraussetzung hat sich heute geändert. Was bedeutet das für unseren Plan?“) wird nicht mehr von
  WorkDecisionRecall gekapert, sondern als Schlussfolgerungsaufgabe mit vorigem Besitzer-Turn gerahmt; Rahmen verlangt Rückfrage statt Raten.
- **Visual (Bot, Teil a)**: PDF-Seite tritt beim Wechsel Dokument → Entwurf hinter den Entwurf zurück (bis zu 3 echte Seitenlagen, Glaslagen
  mit fallendem Alpha, Rückflug der Seite in den Quellen-Slot, Übergabe an die Body-ImageView, STOP räumt sauber).
- **Voice (Bot)**: Recherche-Dokument mit Host-Messungen (Piper eva_k/kerstin/ramona über sherpa-onnx 1.13.8 x86: RTF 0,048–0,068 bei
  1 Thread, Laden 0,6–0,7 s, RSS ~130 MB; keine Wortgrenzen bei sherpa-Engines, belegt aus Code) – **keine Fold7-Werte, keine Hörbewertung**.

## 3. Tatsächliche Testergebnisse (Abschlusslauf auf `6f1444d64`)

Lauf: `scripts/android-kotlin-unit-check.sh /home/user/out/f5` mit `LC_ALL=C.UTF-8`, `JAVA_OPTS=-Xmx4g -Xss4m`
→ `classes=549 checks=5 junit: OK (3059 tests)`.

| Kategorie | Ergebnis |
|---|---|
| **Bestanden** | 3059 JUnit-Tests in 549 Klassen (darin: `JarvisVoiceSelectionTest` 7, `AvatarTextPaceFallbackTest` 6, `WorkDecisionRecallTest` 8, `ConnectedConversationCatalogTest` 7, `JarvisWorkWindowDepthTest` 6; alle avatar.*, cognitive.*, journal-/recall-/korrektur-Tests unverändert grün); Checks-Programme 4 PASS |
| **Fehlgeschlagen** | keine im Abschlusslauf. Der frühere Einzelfehler `ShellExecutionTest` im Handlauf des Visual-Bots war ein Kodierungsartefakt (Lauf ohne `LC_ALL=C.UTF-8`) und tritt im Skriptlauf mit UTF-8 nicht auf |
| **Übersprungen** | 1 Checks-Programm (`ContinuumLandscapeTargetPrecisionChecks`, kein `main`) |
| **Nicht ausgeführt** | Robolectric-UI-Tests aus `src/uiTest` (ohne Gradle am Host nicht lauffähig); Schicht 3 „Modellantwort“ aller Verständnis-Tests (kein LiteRT-Modell am Host → **Antwortqualität ungeprüft**); Android-Lint; APK-Build; sämtliche Fold7-Prüfungen (Stimme hören, Lippen, Avatar, Kugel, Fenster-Animationen, Journal am Gerät, Leistung/RAM/Akku) |

Ein grüner Host-Lauf ist kein Nachweis eines installierten Fold7-Releases.

## 4. Betroffene Dateien und Schnittstellen

Kognition: `cognitive/WorkDecisionRecall.kt` (neu; `Ask`, `Item`, `Focus`, `Reply`, `Resume`, `items()`, `parse()` satzweise),
`cognitive/CockpitWorkJournal.kt` (`Episode.correctedAt`, JSON-Schlüssel `correctedAt`, Altzeilen ohne), `cognitive/WorkRecall.kt`
(`window()` sichtbar), `cognitive/JarvisVoiceSelection.kt` (neu), `LocalConversationFollowUpPolicy.kt` (`consequence`, erweiterte
Anaphern, `benefitsFromPreviousOwnerTurn()`), `LocalFollowUpTaskFrame.kt`.
Avatar: `avatar/AvatarDirector.kt` (`TEXT_PACE_FALLBACK`, `TEXT_PACE_GRACE_MS/WORD_MS/TAIL_MS`; Diagnose in `AvatarEvent.AudioLevel.source`).
UI: `ui/JarvisWorkWindowDepth.kt` (neu), `ui/JarvisWorkWindow.kt`, `ui/JarvisWorkWindowHost.kt`.
`AgentActivity.kt`: Stimmenauswahl-Dialog, `presentDecisionRecall` / `resumeWorkEpisode` / `decisionFocus` (vor dem `WorkRecall`-Block),
`presentDocumentScene` / `presentDraftScene` (Lagenzahl, Übergabe). Verweigerungs-Gate `LOCAL_FOLLOWUP_HISTORY_UNAVAILABLE` unverändert.
Tests: `cognitive/WorkDecisionRecallTest`, `cognitive/ConnectedConversationCatalogTest`, `cognitive/JarvisVoiceSelectionTest`,
`avatar/AvatarTextPaceFallbackTest`, `ui/JarvisWorkWindowDepthTest`.
Dokumente: `docs/research/omega/JARVIS_VOICE_ENGINES_20261106.md`, `docs/research/omega/JARVIS_VOICE_QUALITY_20261106.md`, dieses Protokoll.

## 5. Stand je Bereich (implementiert / getestet / Forschung / offen am Fold7)

| Bereich | Implementiert | Host-getestet | Forschungsansatz | Offen (Fold7 / nicht erledigt) |
|---|---|---|---|---|
| Langzeitgedächtnis | `TemporalMemory`, `CockpitWorkJournal`, Ledger (Opus-Stand) + lesende Verbindung über WorkDecisionRecall | Journal-/Recall-/Ledger-Tests grün | – | Entscheidungen aus Journal/Ledger erreichen das **Modell-Prompt nicht** (nur die Recall-Antworten); Gerätetest |
| WorkDecisionRecall | vier Besitzersätze, Focus sitzungsgebunden, Korrekturzeit, Kugel-Treffer, Nicken | 8/8 | – | Echtes Journal am Gerät, Kugel-/Nick-Reaktion sichtbar prüfen |
| Freies Kontextverständnis | Schicht 1 (Regeln, keine Kaperung), Schicht 2 (Rahmen mit vorigem Turn), Schicht 4 (Rückfrage-Hinweis) | 7/7 | – | **Schicht 3 (Modellantwort) NOT_RUN** – Antwortqualität ungeprüft; mehr Regex ist kein Ersatz für echte Schlussfolgerung |
| Logische Schlussfolgerungen | nur Rahmenbildung für das lokale Modell | Rahmen-Tests | – | Modellqualität am Gerät |
| Autonome Aufgabenbearbeitung (Priorität 3) | **nicht begonnen**. Befund: `AgentToolLayer` (ToolGate/ToolRegistry, `ToolExecutionStatus`, READ_ONLY-Werkzeuge `get_device_info`, `get_battery_info`, `get_storage_info`, `list_files`, `find_files`…) vorhanden; `run.taskEvidence?.calls` liefert Status/`freshOutput`/`outcomeUncertain`; `CockpitWorkJournal.Kind` kennt nur `DOCUMENT` → Werkzeugläufe werden **nicht journaliert** | – | – | Werkzeugläufe ins Journal (neue `Kind`, OFF/ASK/ALWAYS_ON respektieren), Statusanzeige, Gerätetest |
| Stimmenauswahl | M1 | 7/7 | Engine-Vergleich (zwei Dokumente) | echte `Voice.name`/Qualitätsfelder von Samsung/Google unbekannt; Hörprobe |
| Natürliche weibliche Stimme | keine eigene Engine; Auswahl aus System-Engines | – | Empfehlung des Bots: 1. Samsung „Deutsch Stimme 1“ (`lang_de_de_l01`, weiblich), 2. Google `de-de-x-nfh` (einzige Aussicht auf Wortgrenzen), 3. SherpaTTS mit kerstin-low/eva_k; de-AT offline nirgends; Supertonic 3 ungeprüft | Hörbewertung durch Besitzer; Fold7-Latenz/RAM |
| Lippen-Synchronisation | PCM-Hüllkurve → Wortgrenzen → Text-Taktung | 6/6 | keine Engine liefert Phonemzeiten (Android-API hat keinen Callback) | **keine präzise Phonemsynchronisation** behauptet; `source` am Gerät ablesen, `TEXT_PACE_WORD_MS` ggf. anpassen |
| Original-3D-Avatar | unverändert (FilamentAvatarRenderer, AvatarDirector, PoseReducer, MorphMapper) | avatar.* grün | – | Augen-zuerst/Kopf-folgt am Gerät sichtbar prüfen |
| Continuum-Kugel | `RecallHits` nur bei echten Treffern (M3) | Tests | – | Reaktion nur auf reale Ereignisse am Gerät prüfen |
| Holographische Fenster | Teil (a) Dokument hinter Entwurf, Seitenlagen, Rückflug, Übergabe | 6/6 | – | **(b) `JarvisAppCrystal`-Lichtbahn-Erweiterung nur im Bot-Sandbox entworfen, nicht im Repo**; Schließ-/Rückkehr-Animation mit Kamera-Reset; STOP bei späten Ereignissen; echte Codierschritte statt Matrix-Zeichen |

## 6. Unverändertes filmisches Zielbild des Besitzers (für die nächste Sitzung)

App-Kristalle mit Lichtbahnen und Spiegelungen; mehrlagige transparente Glasfenster mit Fresnel-Kanten und Parallaxe; die PDF-Seite fliegt in das
Dokumentfenster, Seitenlagen dahinter; das Ergebnis entfaltet sich aus der Dokumentoberfläche (PDF nach hinten, Entwurf vorn); erst die Augen,
dann folgt der Kopf; die Kugel reagiert nur auf echte Gedächtnis-/Arbeitsereignisse; Schließ-/Rückkehr-Animation mit Kamera-Reset; STOP verwirft
späte Ereignisse; echte Codierschritte statt Matrix-Zeichen; ehrliches „keine Verbindung“, wenn keine Datenverbindung besteht (z. B. Gmail).
Farben Cyan/Blau/Violett/Magenta unverändert, kein neuer Avatar. Die Referenzvideos (Drive-Ordner) waren nicht zugänglich und wurden nicht gesehen.

## 7. Offene Fehler und unfertige Implementierungen

1. Visual-Teil (b) `JarvisAppCrystal` nicht im Repo (nur Entwurf im Sandbox des Bots, verloren bei Sandbox-Ende); Rückkehr-/Kamera-Reset und STOP-Spätereignisse offen.
2. Priorität 3 (lesende Werkzeugoperation) nicht begonnen; Werkzeugläufe nicht journaliert.
3. Entscheidungen aus Journal/Ledger fließen nicht als Kontext ins Modell-Prompt (nur WorkDecisionRecall-Antworten).
4. Schicht-3-Antworten (LiteRT) aller Verständnis-Tests ungeprüft.
5. Robolectric-UI-Tests am Host nicht ausführbar; Lint und APK-Build nicht ausgeführt.
6. Sprachengine: Fold7-Protokoll aus `JARVIS_VOICE_QUALITY_20261106.md` nicht durchgeführt; keine Hörbewertung; sherpa-Warnung „Skip unknown phonemes U+0327“ (ich-Laut) am Gerät prüfen.
7. Priorität 4 (natürliche weibliche Stimme) nur Forschung, Priorität 5 (kontrolliertes Lernen) nicht begonnen.

## 8. Priorisierte Fortsetzungsreihenfolge

1. Fold7-Abnahme genau dieses Stands (`6f1444d64`): Opus-Liste `FOLD7_ABNAHME_JARVIS_20261106.md` + Stimmenauswahl (TON lang), `AudioLevel.source` ablesen, Entscheidungsfragen gegen echtes Journal, Dokument→Entwurf-Tiefe beobachten.
2. Visual (b) App-Kristall/Lichtbahn, Schließ-/Rückkehr-Animation mit Kamera-Reset, STOP-Spätereignisse (Komponenten laut Mandat, keine neuen Avatare/Farben).
3. Priorität 3: Werkzeugläufe ins `CockpitWorkJournal` (neue `Kind`), OFF/ASK/ALWAYS_ON, ehrliche Statusanzeige.
4. Entscheidungs-Digest aus Journal/Ledger in den Modellkontext (lesend, kein zweites Gedächtnis).
5. Sprachengine-Protokoll am Fold7 (Samsung l01 / Google nfh / SherpaTTS), Hörbewertung durch Besitzer.
6. Priorität 5 kontrolliertes Lernen.

Thinking-Stufe: MEDIUM reichte für alle heutigen Schritte; kein Wechsel empfohlen.
