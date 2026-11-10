# Übergabe an ChatGPT (Sol) – Continuum Code 52 / APK 0.3.51 (versionCode 52)
Stand: 10.11.2026 ~14:25 Europe/Vienna · Verfasser: Grok Chef HAUPTBOT · Grund: Nutzungslimit fast erreicht. Dies ist KEIN Abschluss, sondern eine Übergabe zum Weitermachen.

## Letzte Anweisungen von Heinz (Eigentümer), verbindlich
1. 13:58: Original signierte Update-APK 0.3.51 / versionCode 52 bauen, Paket `at.heinz.deviceai.continuumlocal`, Originalzertifikat SHA-256 `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`, Update über die installierte App (keine Deinstallation, keine Datenlöschung).
2. 14:01: Die APK MUSS die volle Cinema enthalten: Bei jedem Arbeitsauftrag fliegt alles (Panels, Dock, die drei oberen Kästchen) filmisch weg und baut sich als lebendiges 3D-Hologramm mit Tiefe neu auf; danach Rückflug. Layout nach ref-a/b/c, Bildwirkung (Schärfe, Tiefe, Bokeh, Glasvolumen, Licht) nach ref-d. Fold7-Innendisplay UND normale Handys (S26, Xiaomi). Keine feste linke Leiste. Grafik darf keine Arbeit vortäuschen.
3. 14:03/14:18: APK 52 ist der vollständige Nullpunkt. ALLES, was je entwickelt wurde (ChatGPT, Opus, Grok, Astra, egal wann), wird angeschlossen, wenn es weiterbringt; Kaputtes reparieren und trotzdem anschließen. Nichts darf liegen bleiben.
4. Internet: bei Neuinstallation standardmäßig ALWAYS_ON, nur die Anzeige korrigiert (erledigt in #703).
5. Verboten: fremder Signierschlüssel, zweiter Paketname, Datenlöschung, Main-Merge, Force-Push, Branches löschen, Publish/Release ohne Heinz, Secrets in Git, erfundene Testwerte. Signieren nur auf exakt grünem SHA.

## Live-Stand (gh, 14:25)
| PR | Branch @SHA | Inhalt | QA |
|---|---|---|---|
| #698 | code52/final-integrator-cinema-20261110 @a0a69263 | Haupt-Integrator (anderer Agent). Nicht darauf pushen. | 42817133312 **failure** (Ursache noch zu lesen; vorher 4 Gaze-Tests rot) |
| #699 | code52/cinema-native-qa-review-fixes-20261110 @d013476c | P0-Reparaturen | Node rot (2 alte Regexes) → behoben durch #702 |
| #702 | code52/grok-bot15-node-reliability-20261110 @3f108742 | Verträge auf #701-DocumentLearned-Unit-Isolation | 42816307098 **success** (vollständig) |
| #703 | grok/bot13-expert-mode-toolgate-20261110 @753516eb | Expertenmodus an ToolRegistry-Consent, Doppelbestätigung destruktiv, ALWAYS_ON-Label, *.p12 | nur Node 5/5; Android über #706 |
| #704 | grok/bot13-signer-contract-cinema-20261110 @77d998ff | Signer-Vertragstest Cinema | 4/4 lokal |
| #706 | code52/grok-release-candidate-0351-20261110 @e6db897f | **Release-Kandidat** = #698 + #702 + #703 | 42821055622 lief (Node/Safety/36 Frames grün; Android/ARM64 offen) |
| #707 | code52/grok-bot16-signer-rc-binding-20261110 @49ad7767 | Signer (#700+#704) auf Kandidat gebunden, Environment `continuum-original-signing` (Reviewer Ullmann27), nur workflow_dispatch | nicht gemerged, nicht dispatched |
| #708 | code52/grok-bot06-cinema-depth-glass-20261110 @f979775c | ref-d-Bildwirkung: JarvisVisualDepth, JarvisGlassRenderer (AGSL), ContinuumBrainOrbRenderer | 42822255062 lief (Node/Host grün) |
| #709 | code52/grok-bot01-legacy-integration-20261110 @2822d77b | Altbestand-Inventur + Anschluss (siehe docs/handover/GROK-LEGACY-INTEGRATION-20261110.md auf dem Branch) | prüfen |
| #710 | code52/grok-bot04-workorder-flyaway-20261110 @b2f0006b | Wegflug/Neuaufbau/Rückflug bei Arbeitsauftrag (ggf. WIP) | prüfen |
| #711 | code52/grok-bot07-compact-phone-layout-20261110 @0e9cf845 | Compact-Layout 412×915 dp (S26/Xiaomi) ohne Überlauf (ggf. WIP) | prüfen |

SHAs der WIP-Branches können sich durch letzte Pushes noch erhöht haben: immer `git ls-remote` prüfen.

## Bekannte offene Punkte (belegt)
- `JarvisShellRenderer.draw()` ruft `GlassBackend.drawBackground`/`drawPanelGlass` nicht auf → AGSL-Shader wirken auf dem Gerät nicht. Anschluss war Bot 07 zugewiesen; prüfen, ob in #711 erledigt.
- Phone-Overflow: `referenceComposition` schloss COMPACT aus (Reflow ~1318 dp bzw. 6896 px Roh-Höhe). Ziel von #711.
- Rückflug ins Cockpit, gleichzeitige Avatar-Ereignisverarbeitung, Zuordnung fremder Gedächtnisereignisse zu offenen Dokumenten: als Risiken genannt; Rückflug an #710, die anderen zwei NICHT begonnen.
- Commit 2581382b (#698) hatte Hologramm auf currentTimeMillis umgestellt (Uhren-Split zu AvatarDirector elapsedRealtime); a0a69263 korrigiert das. Lauf auf a0a69263 trotzdem failure → Log lesen.
- QA-Workflow `continuum-code52-cinematic-qa.yml` läuft nur bei Push auf `code52/**` mit paths-Filter (app/**, .github/continuum/** …); ein bekannter SHA auf neuem Ref startet keinen Lauf. grok/**-Branches haben keine Android-QA.
- Nicht auf dem Gerät nachweisbar ohne Heinz: Sprache (weibliche de-AT-Stimme, Freisprechen), Android-Bedienung/WhatsApp, reale Thermik, Filament-Kopf, GPU-Shader.

## Nächste Schritte (in dieser Reihenfolge)
1. Ergebnisse 42821055622 (#706), 42822255062 (#708), 42817133312 (#698) und die Läufe auf #709/#710/#711 lesen; Rote reparieren (auf dem jeweiligen Branch, echte Commits).
2. #708, #709, #710, #711 mit normalen Merges in `code52/grok-release-candidate-0351-20261110` (#706) zusammenführen; Dateihoheiten: Bot04 Choreografie (Airbus2100CockpitOrchestrator, JarvisCinemaEntrance), Bot06 Tiefe/Glas/Orb, Bot07 Layout. Konflikte sorgfältig lösen, nichts zurückdrehen (#701-Isolation, elapsedRealtime-Uhr, Thermal-Floor, ToolGate).
3. Bot-01-Tabelle abarbeiten, bis keine Klasse-(b)/(d)-Einträge mehr offen sind.
4. Offene Risiken (Avatar-Ereignis-Nebenläufigkeit, Dokument-/Gedächtnis-Zuordnung) reparieren.
5. Exakten QA-Lauf auf finalem Kandidaten-SHA vollständig grün (Node, Backend, Android Unit/UI/Lint, ARM64).
6. Heinz: #707 in `chatgpt/code52-owner-signing-20261109` mergen, Signer per workflow_dispatch mit `source_sha` = `owner_approved_sha` = finaler SHA und `qa_run_id` = grüner Lauf starten; Environment-Freigabe nur durch Heinz.
7. Signierte APK prüfen (Paket, versionCode 52, versionName 0.3.51, Zertifikat-SHA-256), Heinz zur Installation geben. Kein Publish.

Referenzbilder liegen privat auf Heinz' Grok-Box (/workspace/continuum-code52-ref/ref-a,b,c,d) und sind nicht im Repo.

## Nachtrag 14:30 – Endberichte der Bots (alles gepusht, kein Bot läuft mehr)
- #709 Bot 01 @2822d77b: Tabelle lückenlos in docs/handover/GROK-LEGACY-INTEGRATION-20261110.md (767 Branches, 661 PRs klassifiziert). Code51-Kern ist bereits in #698. Wirklich fehlende Quellen: 54 Dateien (§5). Noch zu portieren: `feature/continuum-dual-control-profiles-20261030` (ContinuumDualControlPolicy.kt), M8-Dateien (Presence/Initiative, MemoryQuestionResolver, MemoryTimeline, LocalModelClaimAudit, SelfInspection), `code52/final-integrator-ui-fixes-20261110` (fbe35a7de, 3ba8fc4f6) und #598 für das Layout, #487 restliche video3-Szenen. QA 42822470897 lief.
- #710 Bot 04 @b2f0006b: Wegflug der drei Kästchen, des Docks und der Idle-Panels mit Hologramm-Neuaufbau; Rückflug, Abbruch und Doppelauftrag. Host 27/27, Node 18/18. QA 42823681775 lief. Offen: COCKPIT_SECONDARY fliegt nicht mit; Stagger auf dem Phone.
- #711 Bot 07 @0e9cf845: Compact-Layout 412×915 ohne Überlauf (914,9 dp statt 2632 dp, Host-Java2D); GlassBackend-Hooks in JarvisShellRenderer.draw() verdrahtet (Test JarvisShellRendererGlassHookTest). QA 42823322682 lief.
- **Merge-Hinweis:** #710 und #711 ändern beide `JarvisShellRenderer.kt` → Konflikt sorgfältig von Hand lösen (Wegflug-Choreografie von #710 plus Layout und Glas-Hooks von #711 behalten).
- #703-Inhalte sind sowohl in #706 als auch in #709 (Cherry-Picks); beim Zusammenführen nur einmal übernehmen.

## Nachtrag 14:35 – Kandidat zusammengeführt
- `code52/grok-release-candidate-0351-20261110` (#706) jetzt @`a21f2bed8e06c81267897bf99abdeae4c348cad5` = e6db897f + #708 + #711 + #710 (normale Merges). Einziger Konflikt: JarvisShellRenderer.draw()-Signatur, gelöst durch Kombination (Bot-04-Parameter fromWorkingScene/interruptedAfterMs plus Bot-07-GlassBackend-Hook mit Fallback).
- Lokal `node --test .github/continuum/*.test.mjs`: 1601 bestanden, 9 fehlgeschlagen, 19 übersprungen. Alle 9 Fehler liegen in `coding-orchestrator-failure-report.test.mjs` (Backend, von den Merges nicht berührt). Vermutlich Box-Umgebung (vgl. die chroot/toolchain-Artefakte, die Bot 15 gemeldet hat); im CI prüfen.
- Exakter QA-Lauf auf a21f2bed wurde durch den Push gestartet: Ergebnis lesen und Rotes reparieren. #709 (Altbestand) ist noch NICHT gemerged; seine #703-Cherry-Picks sind bereits im Kandidaten.

## Nachtrag 14:32 Europe/Vienna – BOT 15: welcher QA-Lauf zählt
- Maßgeblich für den zusammengeführten Kandidaten ist Lauf [42824622921](https://github.com/Ullmann27/device-ai/actions/runs/42824622921) auf SHA `a21f2bed8e06c81267897bf99abdeae4c348cad5` (#706 = e6db897f + #708 Tiefe/Glas + #711 Phone-Layout/Glas-Hooks + #710 Wegflug). Stand 14:31: Safety-Checks und 36 Host-Frames grün; Full Node/Regression in Arbeit; Backend, Android Compile/Unit/UI/Lint und ARM64 ausstehend.
- Lauf [42821055622](https://github.com/Ullmann27/device-ai/actions/runs/42821055622) prüft den **alten** SHA `e6db897f622f4d728bc79b571b0073c16701ade4` (Kandidat **ohne** die Grafik-Merges). ChatGPT hat ihn neu angestoßen und in #706 „Entwicklungsstopp, nur Build“ kommentiert. Dieser Lauf zählt **nicht** für die Original-Signatur und ersetzt 42824622921 nicht.
- Die 9 lokalen Fehler in `coding-orchestrator-failure-report.test.mjs` sind auf den Einzel-Branches #708/#710/#711 in CI nicht aufgetreten (Node-Schritt jeweils success). Vermutlich Box-Umgebung; abwarten, ob 42824622921 sie zeigt. Keine Neuentwicklung, nur Reparatur bei Rot aus dem exakten Log.
