# Continuum Code 52 – Swarm-Integrationsjournal (Phase A)

Stand: 10.11.2026, 06:42 Europe/Vienna (CET).  
Integrationsbranch: `integration/code52-swarm-20261110`  
Basis: PR #685 HEAD `322c0c1d080b3f99262c6a97ce15c7a7b685688f` (`chatgpt/code52-cinema-branch-safe-qa-20261110`).  
Dieser Lauf ändert **keinen Produktcode**. Nur Inventur, Abgleich und Arbeitspakete.

Sicherheitsgrenzen (unverändert): kein Merge nach `main`, kein Force-Push, keine Branch-/Code-Löschung, kein Release, keine Originalsignierung, keine Workflow-Dispatches für #675/#682, PRs nur als Draft, keine Secrets oder privaten Referenzoriginale in Git.

---

## 1. Live-HEADs gegen den verifizierten Ausgangsstand

Live geprüft am 10.11.2026 (Europe/Vienna) mit `gh pr view` und `git ls-remote`.

| Bereich | Ausgangsstand (Heinz) | Live-HEAD | Abweichung |
| --- | --- | --- | --- |
| Code 51 Release | `6c23d295e2fca2d4e5950a866547733ec5ff22b0` | unverändert als Release-SHA genannt; `main` zeigt zusätzlich `0ab778c5f3f6d5178352ffae8948a25757d94e4e` (Merge von #675, Signier-Registrierung) | `main` hat sich seit der Branch-Inventur vom 09.11. weiterbewegt; das **signierte Code-51-Paket** bleibt `6c23d295…`. Kein Dispatch ausgeführt. |
| #671 | `fed197963453e0202584b5b162ce593fd2e5eef2` | identisch, Draft, Branch `chatgpt/code52-unified-integration-20261109` | keine |
| #677 | `bbcb61b9a451cb07a6e15847cce2ccd821afcaf9` | identisch, Draft | keine; Vorfahre von #685 |
| #678 | bereits in Cinema-Linie | MERGED `2c1a70bb6a75ee1fd5f8f8fc16853e17a592e83a` | nicht erneut übernehmen; `GazeBlinkPolicy.kt` Blob `4ebd4fb30813` identisch in #685 |
| #679 | bereits in Cinema-Linie | MERGED, HEAD = #677 `bbcb61b9…` | nicht erneut übernehmen |
| #680 | `3fb0a731935fa156f9217602c21510980241c620` | identisch, Draft | keine; Vorfahre von #685 |
| #681 | `0df55d7c8d5cae8a53bf1a43e26651f902881e78` | identisch, Draft | keine; Vorfahre von #685 |
| #682 | Signiervorbereitung auf altem SHA | `a7125a06af7281a7b32564a9b32e5b9f4e0230a8`, gebunden an **#681** `0df55d7c…` und CI-Lauf `42742886395` | bestätigt älter als #685; **BLOCKED** für den aktuellen QA-Kandidaten |
| #683 | `01bdbadb1242339f908f7282cd53d0b8df2d6a02` | identisch, Draft | keine; Vorfahre von #685 |
| #684 | `09635722e060b95603baa1095d5a5b010bb162b6` | identisch, Draft | keine; Vorfahre von #685 |
| #685 | `322c0c1d080b3f99262c6a97ce15c7a7b685688f` | identisch, Draft | keine |

PRs mit Nummer **> 685: keine**. PR **#674 existiert nicht**.

Neuere Grafik-/Cinema-PRs nach der Inventur vom 09.11. (Inventur-Maximum war #671): #672, #673 (MERGED), #675 (MERGED nach `main`, nur Signier-Registrierung), #676 (MERGED, CI-Coverage), #677–#685 wie oben. Zusätzlich weiter offen und grafisch relevant, aber nicht neuer als #685: #666 (`f1a5a2b250414c14f772178498dcc0d3327b7542`), #659, #661, #651, Grok-Jarvis-Serie #479/#481/#482/#487/#489.

### QA zu #685

- GitHub Actions Lauf **42757104497**: `completed/success`, Event `push`, Branch `chatgpt/code52-cinema-branch-safe-qa-20261110`, SHA **exakt** `322c0c1d080b3f99262c6a97ce15c7a7b685688f`.
- Job `validate-source-and-build`: success. Logzeile: `CODE52_ANDROID_JUNIT_PASS {'tests': 4257, 'failures': 0, 'errors': 0, 'skipped': 18}`.
- Artefakte vorhanden: `Code52-Cinema-QA-Evidence`, `Code52-CINEMA-UNSIGNED-NOT-INSTALLABLE`.
- Damit existiert eine grüne QA **für den aktuellen #685-HEAD**. Ein späterer Commit auf diesem Swarm-Branch erbt diesen Nachweis **nicht**.

---

## 2. Inventur gegen den aktuellen Remote-Stand

Gelesen aus #685: `docs/integration/CODE52_ALL_PRS_20261109.jsonl` (627 PRs, Maximum #671), `CODE52_ALL_BRANCHES_20261109.jsonl` (727 Branches), `CODE52_AUDIT_INDEX.md`, `CODE52_NO_ORPHANS_LEDGER.json`, `CODE52_GRAPHICS_FINAL_AUDIT_20261110.md`.

Live `git ls-remote --heads`: **740** Branches (+13), 0 entfernte Inventur-Branches.

**Neue Branches seit 09.11.-Inventur:**

| SHA | Branch |
| --- | --- |
| `bbcb61b9…` | `chatgpt/code52-argon4-holographic-rim-20261109` (#677/#679) |
| `fed19796…` | `chatgpt/code52-ci-coverage-20261109` (#676) |
| `2c1a70bb…` | `chatgpt/code52-cinema-avatar-finesse-20261109` (#678) |
| `322c0c1d…` | `chatgpt/code52-cinema-branch-safe-qa-20261110` (#685) |
| `01bdbadb…` | `chatgpt/code52-cinema-final-graphics-audit-20261110` (#683) |
| `09635722…` | `chatgpt/code52-cinema-grounded-document-question-20261110` (#684) |
| `a7125a06…` | `chatgpt/code52-cinema-owner-signer-candidate-20261110` (#682) |
| `0df55d7c…` | `chatgpt/code52-cinema-prism-pixel-fix-20261110` (#681) |
| `bbcb61b9…` | `chatgpt/code52-cinema-prism-shader-20261109` (#679, gleicher SHA wie #677) |
| `3fb0a731…` | `chatgpt/code52-cinema-switch-contract-20261109` (#680) |
| `b57c6374…` | `chatgpt/code52-intent-regression-20261109` (#673) |
| `d34f3ee9…` | `chatgpt/code52-owner-signing-20261109` (#675) |
| `c402b046…` | `chatgpt/code52-project-isolation-20261109` (#672) |

**SHA-Änderungen bestehender Inventur-Branches:**

| Branch | Inventur-SHA | Live-SHA | Hinweis |
| --- | --- | --- | --- |
| `chatgpt/code52-unified-integration-20261109` | `f8697bf6…` | `fed19796…` | #671 fortgeschrieben |
| `main` | `5271fa03…` | `0ab778c5…` | Merge #675 (Signier-Workflow-Registrierung), 09.11.2026 20:43 Europe/Vienna |
| `continuum-state` | `bd11fe1e…` | `ae711d97…` | Inhalt in diesem Lauf **nicht** diff-geprüft (`AUDIT_REQUIRED`) |
| `research/continuum-nvidia-automation-ledger-20261030` | `112bf479…` | `d51a4b03…` | Inhalt in diesem Lauf **nicht** diff-geprüft (`AUDIT_REQUIRED`) |

Kein Branch und kein PR wurde gelöscht oder als vollständig abgehakt markiert, nur weil er fehlt oder alt ist.

---

## 3. Grafik-Kandidaten gegen #685

Methode: Live-HEAD, `git merge-base --is-ancestor`, Blob-Vergleich (`git rev-parse SHA:Pfad`) produktiver Quellen, stichprobenartige Aufrufpfade. Ein grüner Test von Commit A beweist nichts für Commit B.

| Kandidat | Remote-HEAD | Status | Beleg |
| --- | --- | --- | --- |
| #479 `grok/jarvis-design-system` | `34d3488b3444a34e08f46f858b099adcff9cfd17` | SUPERSEDED | `JarvisDesign.kt` Blob `2379e22a10` ≠ #685 `4889fbbfa5`. Motion-Kerne später bytegleich über #489/#482. Nicht zurückkopieren. |
| #481 `design/jarvis-cockpit-home-20261104` | `9db93c2e9e62aad7e2bcd9b67bdbbf52412d3b3c` | PARTIAL_SOURCE_CONNECTED | Raster/Header existieren in `JarvisCockpitGridContent.kt` (#685 Blob `a40820e918` ≠ #481 `35b9aafb43`). Layout-Idee übernommen, Dateien weiterentwickelt. |
| #482 `jarvis-grafik/avatar-ref-match` | `e9659ff553ce90e1248123767930e3447e63380c` | PARTIAL_SOURCE_CONNECTED | `DampedSpring.kt`/`Easings.kt`/`MotionPresets.kt` identisch; `JarvisMotion.kt` `aafe2aad55` ≠ #685 `51fd5a5d26`. Wireframe-Ersatzkopf nicht als Original-GLB verwenden. |
| #487 `jarvis-luecke/video3-alarm-warp-ganzkoerper` | `85a0562d02542ff3cea020690d4d7f38bece2783` | PARTIAL_SOURCE_CONNECTED | In #685 vorhanden: `Video3WarpScene.kt` (differt), `Video3MemoryScene.kt`, `Video3FileTunnel.kt`. **Fehlen:** `JarvisGreetingCard.kt`, `JarvisUserCard.kt`, `Video3AlarmScene.kt`, `Video3CalmNotice.kt`, `Video3Contracts.kt`, `Video3Curves.kt`, `Video3Pills.kt` plus Tests. |
| #489 `jarvis-mathematik/motion-math` | `bc2416abcb7f0a5d05219902588180b9b01c1313` | ALREADY_PRESENT | `DampedSpring.kt` `bd86f2c7c7` und `Easings.kt` `cd7d3bbff8` und `MotionPresets.kt` `bf8d9c4e74` identisch. `Staggers.kt`/`TrackTimeline.kt` in #685 neuer (`5144a759bf` / `96cb497bb2`); #489-Varianten nicht zurückspielen. `JarvisMotion.kt` weiterentwickelt. |
| `wip/stop-20261104-integration-nav` | `69b823133e132c5535626edb34eb26327bebb86c` | AUDIT_REQUIRED | 24 produktive Dateien seit Merge-Basis, 17 differieren (u. a. `JarvisDesign.kt`, `JarvisShellRenderer.kt`). Kein Blind-Merge. |
| `wip/stop-20261104-sicherheit-m5fg` | `d1da1d1dbf31e25d4816d0df0362195e5e282a67` | AUDIT_REQUIRED | 4 differierende Dateien; Sicherheits-WIP vom 04.11. |
| `wip/stop-20261104-welten-optik-design` | `c7fc330d911af82dbb0baff4c2a56ed0e074b05d` | AUDIT_REQUIRED | 3 differierende Dateien inkl. `JarvisDesign.kt`/`JarvisShellRenderer.kt`. |
| #535 `ccr-4f80075a-ycl67y` | `4bbf2863dc3c7debe74ac81f3d16521926c8ae0a` | SUPERSEDED | `avatar3d/AvatarIdleMotion.kt` `31801578f47f` ≠ #685/`#565` `23e9c0229f5e`. Nachfolger #565 ist Vorfahre von #685. |
| #540 `codex/continuum-avatar-natural-blink-20261105` | `1419e0f738accec7eb9c8916e481688125bbe37b` | SUPERSEDED | `AvatarIdleMotion.kt` `e6188eac3691` ≠ `23e9c0229f5e`. |
| #551 `bot1/avatar-blink-kinematics-20261105` | `27df89c5e54b8581e46f8d21576daf82c50e3689` | SUPERSEDED | `AvatarIdleMotion.kt` `5b89c17443ca` ≠ `23e9c0229f5e`. |
| #565 `claude/continuum-jarvis-cockpit-z7bo60` | `cd1c072f412e722ac4933275ef0b06512d5d22b3` | ALREADY_PRESENT | Vorfahre von #685. `AvatarIdleMotion.kt` `23e9c0229f5e` und `AvatarFraming.kt` `367dc713b1aa` byteidentisch. |
| #549 `bot2/isolated-neon-tile-energy-20261105` | `ea315130b990f4a1610d8efbc3f68f32d34162da` | ALREADY_PRESENT | `JarvisTileEnergy.kt` Blob `43dd016796` identisch; Aufruf `JarvisShellView` → `JarvisTiles.drawSurface` → `JarvisTileEnergy.sample` laut bestehendem Intake-Test. |
| #550 `qa/orb-phase-finite-idle-20261105-b3-r2` | `e247e1b51bf74880ddfcee6b81596bc36654aaf1` | ALREADY_PRESENT | Datei nicht bytegleich (`33c2306ae7` ≠ `23e0be27b4`), aber #683 hat den Guard wiederhergestellt: `ContinuumBrainOrbRenderer.kt:29` `phase in -1_000_000f..1_000_000f` plus `ContinuumOrbNonFiniteSignalsTest.kt:20-22`. |
| #594 `codex/continuum-code49-orb-render-performance-20261107` | `88dfd45139471463618673a1f827ee062e9bf6d5` | INTEGRATED | Vorfahre von #685; Renderer danach durch #683-Guard verändert (`ad652ff81f` → `23e0be27b4`). Allokations-Puffer nicht durch ältere Kopie ersetzen. |
| #563 Originalorb-Forschung | `17ee574abc021b8f1cf1edf93e96ef484ac0ec4d` | INTEGRATED | Vorfahre von #685. |
| #617 Airbus-2100 | `599ac783f06e9e201a29c91c0c06ea2e9457e0e3` | INTEGRATED | MERGED; Vorfahre von #685. `Airbus2100CockpitOrchestrator.kt` existiert in #685 (`7a96116ece`). |
| `codex/airbus2100-hollywood-fui-20261108` | `2b195acc0f64adf9a78a7c2383b239fb020fd0a1` | SUPERSEDED | Nicht Vorfahre; `HollywoodFuiConstruct.kt` älter. Orchestrator-Datei auf diesem SHA nicht am späteren Pfad. Durch #617+Cinema ersetzt. |
| #614 Hollywood-FUI-Foundation | `ca0bf6e5d8468df585891fd998963cddd195d0a5` | SUPERSEDED | `HollywoodFuiConstruct.kt` `7a4af9a67d` ≠ aktuelles Superset `6ced375126`. Nicht zurückkopieren. |
| #651 Fable-FUI | `09722adf6c88453ac7607a2a484ae97f00a8fc00` | INTEGRATED | Vorfahre von #685; Construct später durch Cinema weiterentwickelt (`889a672619` → `6ced375126`). Overlay ruft `HollywoodFuiConstruct.frame`. |
| #659 Holographic V2 | `69e9b3986d9fad86bda64b3e47c43073e1c76d3a` | PARTIAL_SOURCE_CONNECTED | `avatar3d/AvatarSurfaceRadiance.kt` `55524f3762dd` und `JarvisWindowGaze.kt` `5ba6ea57ff` identisch. FUI-Dateien differieren (Cinema-Superset). Fold7-Filmmessung offen. |
| #661 VIS-REF-FIDELITY | `58c545aaefefb233371c92948ad1a9a002c7c004` | RESEARCH_ONLY | Host-Messwerkzeug, ausdrücklich nicht geräteverifiziert. `HollywoodFuiReferenceFidelity.kt` existiert in #685, Blob `811c588851e1` ≠ #661 `4a39a6e10c0c`. Kein Filament-PASS. |
| #666 Sustained hologram | `f1a5a2b250414c14f772178498dcc0d3327b7542` | PARTIAL_SOURCE_CONNECTED | Radiance/Gaze identisch; FUI/Hologramm-Dateien weiterentwickelt. Satzgesten-Kern in `AvatarDirector`/`AvatarContract` vorhanden, EXP10 nicht eigenständig verdrahtet. |
| #671–#681, #683, #684 Cinema-Stapel | siehe Abschnitt 1 | INTEGRATED | Alle genannten SHAs sind Vorfahren von #685. #678/#679 nicht nochmals cherry-picken. |
| #682 Signer-Prep | `a7125a06af7281a7b32564a9b32e5b9f4e0230a8` | BLOCKED | An #681 `0df55d7c…` gebunden, nicht an `322c0c1d…`. Kein Dispatch. |
| #685 QA-HEAD | `322c0c1d080b3f99262c6a97ce15c7a7b685688f` | ALREADY_PRESENT | Aktuelle Integrationsbasis dieses Journals. |

Zählung dieser Grafik-/Cinema-Serie (33 Einträge, genau ein Status je Zeile):  
**ALREADY_PRESENT 5** (#489, #565, #549, #550, #685) · **INTEGRATED 12** (#594, #563, #617, #651, #671, #677, #678, #679, #680, #681, #683, #684) · **PARTIAL_SOURCE_CONNECTED 5** (#481, #482, #487, #659, #666) · **RESEARCH_ONLY 1** (#661) · **SUPERSEDED 6** (#479, #535, #540, #551, #614, Airbus-Zweig) · **BLOCKED 1** (#682) · **AUDIT_REQUIRED 3** (die drei `wip/stop-20261104-*`).

Ältere Ledger-Einträge außerhalb dieser Serie (#630, #636, #644, #650, #653–#658, #668 u. a.) bleiben im bisherigen Ledger `AUDIT_REQUIRED`/`RESEARCH_ONLY` und wurden hier nicht stillschweigend geschlossen.

---

## 4. Cinema-Experimente EXP01–EXP15 im #685-HEAD

Quelle: `app/src/main/java/at/heinz/deviceai/ui/JarvisCinematicSettings.kt:15-30` und `CinemaExperimentWiringTest.kt:9-18` (15 Einträge, 11 `wired=true`).  
Zusätzlicher Masterschalter: `experimentEnabled` verlangt `wired && enabled(KEY) && depthExperimentEnabled(DEPTH_EXPERIMENT_KEY)` (`JarvisCinematicSettings.kt:33-35`). Cinema-Opt-in bleibt standardmäßig aus (`DEPTH_EXPERIMENT_KEY`, Default `false`).

**11 verdrahtet** (`wired = true`) mit Aufruf von `experimentEnabled`:

| EXP | Fundstelle Schalter | Laufzeitpfad |
| --- | --- | --- |
| EXP01 Z-Glas | Enum `:16` | `JarvisShellView.kt:107-108`, `JarvisWorkWindowHost.kt:207-208` |
| EXP02 Rim | `:17` | `JarvisWorkWindowHost.kt:205-206` |
| EXP04 Fensterflug | `:19` | `JarvisWorkWindowHost.kt:197-198` |
| EXP05 Wings | `:20` | `JarvisShellView.kt:240-241` |
| EXP06 Gesichtslicht | `:21` | `ContinuumHologramView.kt:1043-1044` |
| EXP08 Pre-Speech | `:23` | `AgentActivity.kt:290-291` |
| EXP09 Blick-Lidschlag | `:24` | `ContinuumHologramView.kt:1045-1046` |
| EXP11 Orb-Brücke | `:26` | `ContinuumHologramView.kt:987-988` |
| EXP12 Receipts | `:27` | `JarvisWorkWindowHost.kt:201-202` |
| EXP13 Luxury-Glas | `:28` | `JarvisShellView.kt:238-239` |
| EXP14 AGSL-Kante | `:29` | `JarvisWorkWindowHost.kt:203-204` |

**4 nicht eigenständig verdrahtet** (Default `wired = false`; `experimentEnabled(…)` wird für sie nirgends aufgerufen). Ein bloßes Umlegen auf `wired=true` gilt **nicht** als Fertigstellung.

1. **EXP03 Geschützte Tiefenflächen** – Enum `JarvisCinematicSettings.kt:18`; Test `CinemaExperimentWiringTest.kt:17`. Es gibt nur die generische Fensterfüllung `DEPTH_GLASS_FILL` in `JarvisWorkWindow.kt:60` und `:674`. **Fehlt:** eigener Experiment-Vertrag, Schalterpfad und nachweislich geschützte Tiefenflächen (nicht nur das normale Fenster-Backing).
2. **EXP07 Augen-Hals-Kopplung** – Enum `:22`. Technik existiert bereits: `JarvisWindowGaze.kt`, genutzt in `JarvisWorkWindowHost.kt:313`, plus `AvatarDirector`/`AvatarContract` (Fensterblick, TTL). **Fehlt:** unabhängiger, schaltbarer EXP07-Effekt **ohne** zweite Blick-Engine und ohne Verdopplung der vorhandenen Policy.
3. **EXP10 Natürliche Satzgesten** – Enum `:25`. Satzende-Gesten bereits in `AvatarContract.kt:325-330` (`SENTENCE_END_DURATION_MS`) und `AvatarDirector.kt:360` (`SENTENCE_MARKS`). **Fehlt:** eigenständiger EXP10-Vertrag an reale Sprachdaten gebunden, ohne die bestehende Gesten-Policy zu kopieren.
4. **EXP15 Thermisch adaptive Animation** – Enum `:30`; Test `:18`. Budget existiert: `JarvisRenderQuality.kt:13-23`, angewendet in `ContinuumHologramView` (`setFrameBudget` / `renderBudget`) und `JarvisWorkWindowHost.kt:219-222`. **Fehlt:** Bewertung als eigener schaltbarer Experiment-Vertrag (keine zweite Uhr, kein zweites Qualitätsmodell).

---

## 5. Referenzbilder gegen das #685-Cockpit (Code-Ebene)

Verglichen wurden die drei Eigentümerbilder unter `/workspace/continuum-code52-ref/` (ref-a PDF-Analyse, ref-b aktiver Auftrag, ref-c Entwurf fertig). Die PNG-Originale wurden **nicht** ins Git übernommen.

### Entscheidung Heinz (10.11.2026, Europe/Vienna)

**Keine feste linke Taskleiste.** Die Referenzbilder bleiben verbindlich für alles andere: Kopf+Kugel gleichzeitig, holografische Arbeitsfenster, Verarbeitung, Entwurf, Sockel, untere Werkzeugleiste, Telemetrie-Kacheln, Statuszeile – modern, 3D, Hologramm, flüssig berechnete Animationen. Navigation über die **untere Werkzeugleiste** und holografische Elemente, nicht über eine Seitenleiste.

Die vorhandene linke Leiste im Code ist als **zu ersetzen** markiert: nicht löschen, bevor ein Ersatz (unten/holografisch) dieselben Ziele erreicht. Betroffene Stellen: `JarvisShellLayout.Slot.NAV_RAIL` (`JarvisShellLayout.kt:13`, Zuweisung um `:249` im Layout), Zeichnung `JarvisShellRenderer.kt:129` → `JarvisTiles.drawRail`, Einträge `JarvisTiles.kt:197-202` (`railNav`: Start, Gespräch, Gedächtnis, Denken und Planen, Forschung, Mein KI-Team, Aufgaben, Internet, Lernen, Gerätesteuerung, Systemstatus). `bottomNav` (`JarvisTiles.kt:204`) ist derzeit nur Start/Gespräch/Gedächtnis/Mein KI-Team/Systemstatus – das ist **kein** Ersatz für die Referenz-Werkzeugleiste.

### Was im #685-HEAD als View/Renderer existiert

| Element | Code-Fundstelle | Abgleich |
| --- | --- | --- |
| Kopfzeile Uhr/Datum | `JarvisCockpitGridContent.kt:139-156` | vorhanden |
| Logo Device AI / Continuum | `:130-137` | vorhanden |
| JARVIS-Chip mit Krone | `:168-175` | vorhanden |
| „Online nach Freigabe“ | `:177-185`, datengebunden an `s.presentation.localOnly` | vorhanden |
| Kamerawahrnehmung | `:228-236` | vorhanden (ref-b/c rechts oben) |
| Moduliste Gedächtnis/Denken/Assistenten/Lernen/Lokales Modell | `:239-254` | vorhanden als rechte Statusliste (ähnlich ref-b/c, nicht das Auftragsstatus-Checklist von ref-a) |
| JARVIS-Karte | `:256-275` zeichnet „JARVIS“ + Badge (Hört zu/Spricht/Denkt nach/Bereit) | **unvollständig** gegenüber „JARVIS Live“ mit Wellenform und Auftragstext; `audioLevel` wird in dieser Karte nicht gezeichnet |
| Kopf + Kugel gleichzeitig | `Slot.AVATAR` besitzt beide Renderobjekte (`JarvisCockpitGridContent.kt:94`); `JarvisShellLayout.kt:42-43,249` `headBounds`/`orbBounds`; `ContinuumHologramView` `headEnabled` + Orb | vorhanden als Architektur; Fold7-Bildbeleg NOT_RUN |
| Ablauf-Liste | `JarvisCockpitPanels.drawTaskFlow` Titel „Aktueller Ablauf“ (`:68`), echte `ProcessStep`s | vorhanden, aber nicht als „Auftragsstatus“-Karte von ref-a |
| Dokument-FUI | `HollywoodFuiStageOverlay.drawDocument` `:334-364`: gestapelte Seitenumrisse, Dateiname, echte `question` (#684), Eintragszahl | **schematisch**; kein lesbares PDF-Layout, keine Verarbeitungs-Checkliste, keine Entwurfskarte |
| Sockel | `ContinuumHologramRenderer.drawPedestal` `:319`, `ContinuumBrainOrbRenderer.drawPedestal` `:298` | vorhanden |
| Statuszeile | `JarvisCockpitPanels.drawStatusBar` `:111-148` | teilweise: `statusSegments` `:94-108` definiert System/Netz/Lokale KI/Sync/Privacy/CPU·RAM·Temp, gezeichnet werden aber nur LOCAL_AI, PRIVACY, SYSTEM (`:113`) |
| Telemetrie-Daten | u. a. `ContinuumCockpitPresentation.kt:97-103`, `ContinuumSystemGaugesView` (in `AgentActivity.kt:3647` beim Systemstatus, nicht als permanente untere Kachelreihe) | Daten vorhanden, Referenz-Kachelreihe fehlt |

### Top-Lücken zu den Referenzbildern

1. Feste linke `NAV_RAIL` ist im Code aktiv – laut Heinz zu ersetzen durch untere holografische Navigation, nicht ersatzlos zu löschen.
2. Untere Werkzeugleiste der Bilder (ref-b: Dateien, PDF, Bilder, Web, Analyse, Code, Modelle, Agenten, Tools; ref-a: Dateien, Dokumente, Analyse, Entwurf, Ergebnis, Zurück) existiert so nicht; `bottomNav` ist eine reduzierte Telefon-Navigation.
3. Aktiver-Auftrag-Leiste mit Timer und Stopp (ref-b „Aktiver Auftrag … 00:01:24“) im produktiven UI-Text **nicht gefunden**.
4. Dokumentfenster + Verarbeitungsschritte + „Zusammenfassung – Entwurf“ mit Ziel/Vorgehen/Offene Punkte und Buttons „Entwurf öffnen“, „Weiterbearbeiten“, „Speichern“ fehlen; `drawDocument` zeigt nur Umrisse, Dateiname und Fragetext.
5. JARVIS-Live-Karte ohne Wellenform/`audioLevel` und ohne den Auftragstext der Referenz.
6. Telemetrie-Kachelreihe (Systemauslastung, Modell, Aktive Agenten, Gedächtnis, Lernfortschritt, Energie/Gerät) nicht als unteres holografisches Band verdrahtet; CPU/RAM/Temp stecken im ausgefilterten LOAD-Segment der Statuszeile.
7. Volle Statuszeile der Referenz (Netz, Continuum-Sync, Sicherheit, App-CPU, RAM, Akku, Temperatur) wird nur teilweise gezeichnet.

Host-Vorschau oder Java2D (#661) ist kein Fold7-GPU-Nachweis.

---

## 6. Arbeitspakete der 16 Rollen

Nur belegte Lücken. Keine allgemeinen Empfehlungen. Gemeinsame Dateien nicht parallel unkoordiniert schreiben.

1. **01 Archaeologist** – Offene SHA-Änderungen `continuum-state` (`ae711d97…`) und `research/continuum-nvidia-automation-ledger-20261030` (`d51a4b03…`) sowie die drei `wip/stop-20261104-*` dateigenau gegen #685 listen; Inventur-JSONL fortschreiben. Keine Produktfiles. Abhängigkeit: keine.
2. **02 Master Integrator** – Cinema-Stapel (#677–#684) nicht erneut mergen. Nächste isolierte Slices nur nach Blob-Beweis: fehlende #487-Video3-Dateien (nicht `Video3WarpScene.kt` überschreiben ohne Diff), Cockpit-Navigation nur zusammen mit Rolle 07. Konfliktdateien: `JarvisShellLayout.kt`, `JarvisShellRenderer.kt`, `JarvisTiles.kt`.
3. **03 Filament Avatar Engineer** – Original-GLB behalten. `#565`-Blobs sind bereits kanonisch (`AvatarIdleMotion.kt` `23e9c0229f5e`, `AvatarFraming.kt` `367dc713b1aa`). Aus #535/#540/#551 höchstens nachweislich bessere Algorithmen, keine älteren Dateikopien. Konflikte: `ContinuumHologramView.kt` mit 04/06/09.
4. **04 Cinematic Motion Director** – EXP07 nicht durch `wired=true` fertigmachen. Ist-Zustand: `JarvisWindowGaze.kt` + `AvatarDirector`. Ziel: eigenständige Augen-Hals-Kopplung ohne zweite Uhr und ohne Policy-Klon. Abhängigkeit: 03. Konflikte: `AvatarDirector.kt`, `avatar/AvatarContract.kt`.
5. **05 GPU Shader Engineer** – EXP14 ist verdrahtet (`JarvisWorkWindowHost.kt:203-204`). EXP03 braucht echte geschützte Tiefenflächen über `DEPTH_GLASS_FILL` (`JarvisWorkWindow.kt:60,674`) hinaus. Konflikte: `JarvisWorkWindow.kt`, `HollywoodFuiStageOverlay.kt` mit 08.
6. **06 Orb & Neural Visuals** – Phase-Guard und #594-Abstammung erhalten (`ContinuumBrainOrbRenderer.kt:29`). EXP11 bereits verdrahtet. Keine ältere Orb-Datei aus #550/#594 über den #683-Stand legen. Konflikte: `ContinuumBrainOrbRenderer.kt`, `ContinuumHologramRenderer.kt` mit 03/12.
7. **07 Cockpit & UX Architect** – **Heinz 10.11.: keine feste linke Taskleiste.** `NAV_RAIL`/`drawRail`/`railNav` durch untere holografische Werkzeugleiste ersetzen, Navigation vorher sicherstellen (Ziele aus `railNav` müssen erreichbar bleiben). Zusätzlich: JARVIS-Live-Karte mit echtem `audioLevel` (`JarvisCockpitGridContent.kt:256-275`), Aktiver-Auftrag-Leiste, Telemetrie-Kacheln, vollständige Statuszeile (`JarvisCockpitPanels.kt:111-113` zeichnet LOAD/Netz/Sync derzeit nicht). Dateien: `JarvisShellLayout.kt`, `JarvisShellRenderer.kt`, `JarvisTiles.kt`, `JarvisCockpitGridContent.kt`, `JarvisCockpitPanels.kt`, `ContinuumHomeView.kt`. Nicht löschen ohne Ersatz. Konflikte: 02, 08, 14, 15.
8. **08 FUI World Engineer** – `HollywoodFuiStageOverlay.drawDocument` (`:334-364`) um echte Dokumentseiten, Verarbeitungsschritte und Entwurfskarte mit belegten Buttons erweitern; #684-Fragetext bleibt ein realer Fragetext, keine erfundenen Antwort-Pillen. Sieben Welten weiter über `CockpitAnimationEvent` → `Airbus2100CockpitOrchestrator` → `JarvisWorkWindowHost` → Overlay. `HollywoodFuiConstruct.kt` nicht auf #614/#651 zurücksetzen. Konflikte: 05, 07, 14.
9. **09 Speech & Avatar Synchronization** – EXP08 ist verdrahtet (`AgentActivity.kt:290-291`). EXP10 nicht verdoppeln: Satzgesten schon in `AvatarContract.kt:325-330` und `AvatarDirector.kt:360`. Zehn zusammenhängende Sprachturns und Fold7-TTS bleiben NOT_RUN. Konflikte: 03, 04.
10. **10 Cognitive & Memory Engineer** – Keine Grafik-Umbauten in `cognitive/` ohne Bedarf. Bestehende Dialogregressionen (Nebel 731, Kiesel 482) und Recall-Evidenz bei Cockpit-Änderungen grün halten. Geringe Dateiüberschneidung, außer `CockpitWorkJournal`.
11. **11 Android Device Control** – `clickCandidate()` bleibt experimentell; kein `click`/`type_text` in `ToolRegistry`. WhatsApp nur `ACTION_SEND`-Entwurf. Lücke dokumentieren, keinen autonomen Versand bauen, Internet nicht stillschweigend öffnen.
12. **12 Thermal & Performance Engineer** – EXP15 nicht per Flag „fertig“ schalten. Ist-Zustand: `JarvisRenderQuality.kt:13-23` plus `ContinuumHologramView`/`JarvisWorkWindowHost.kt:219-222`. Eigenen Vertrag bewerten, keine zweite Qualitätsuhr. Konflikte: 05, 06.
13. **13 Security & Consent Auditor** – „Online nach Freigabe“ bereits datengebunden. ToolGate, STOP, Web-Consent unverändert. #682/#675 nicht dispatchen. Signer nur nach neuer SHA-exakter QA und Eigentümerfreigabe.
14. **14 Visual Fidelity Lab** – ref-a/b/c gegen Host-Preview messen; #661-Werkzeug liegt in `tools/continuum-jvm-preview/.../HollywoodFuiReferenceFidelity.kt` (Blob differiert). Kein Fold7-PASS behaupten. Abhängigkeit: 07/08-Zeichnung. Konflikte: Overlay-Dateien mit 08.
15. **15 Test & CI Engineer** – Grüne QA 42757104497 gilt nur für `322c0c1d…`. Jeder neue Integrations-SHA braucht eigenen Lauf. Tests für: Navigation nach Rail-Ersatz, Document-Frage ohne Fake-Pills (#684), EXP-Wiring unverändert bis echte Verdrahtung, Orb-Phase-Guard. Dateien: `CinemaExperimentWiringTest.kt`, `ContinuumDocumentSceneUiTest.kt`, `JarvisShellLayoutTest.kt`.
16. **16 Release & Update Engineer** – #682 nicht auf `322c0c1d…` anwenden. Nach späterer SHA-exakter QA neue Signer-Bindung vorbereiten und vor Dispatch stoppen. Paket `at.heinz.deviceai.continuumlocal`, versionCode 52, Originalzertifikat. Kein `main`-Merge.

---

## 7. Risiken

- Gemeinsame Dateien `JarvisShellLayout.kt`, `JarvisShellRenderer.kt`, `JarvisTiles.kt`, `JarvisCockpitGridContent.kt`, `HollywoodFuiStageOverlay.kt`, `ContinuumHologramView.kt`, `ContinuumBrainOrbRenderer.kt`.
- #678/#679 erneut zu übernehmen würde die Cinema-Linie verdoppeln.
- #682 an den falschen SHA zu binden würde eine nicht zum HEAD passende APK vorbereiten.
- Rail entfernen ohne Ersatz verliert die Navigation (Start bis Systemstatus).
- #487-Dateien dürfen `Video3WarpScene.kt` nicht ungesehen überschreiben.
- `Staggers.kt`/`TrackTimeline.kt` aus #489 sind älter als #685.
- `continuum-state` und das NVIDIA-Ledger sind unklare SHA-Wechsel.
- Java2D/#661 ist kein Gerätebeleg.

---

## 8. Zustände und NOT_RUN

Erreicht für den #685-HEAD `322c0c1d…` (nicht für nachfolgende Journal-Commits): Quellstand der Cinema-Linie vorhanden; `ANDROID_QA_GREEN` durch Lauf 42757104497 (4257 Tests, 0 Fehler, 18 Skip).

**NOT_RUN / nicht erreicht:** `HOST_TESTED` dieses Swarm-Commits, `ANDROID_QA_GREEN` jedes SHA ≠ `322c0c1d…`, `UNSIGNED_APK_READY` dieses Branches, `ORIGINAL_SIGNED_DRAFT`, `RELEASE_APPROVED`, `FOLD7_DEVICE_VERIFIED`, 10-Turn-Sprache am Gerät, thermische FPS-Messung, vollständiger Blob-Vergleich aller 740 Branches, Inhaltsprüfung von `continuum-state` und des NVIDIA-Ledgers.

Aktueller Swarm-Zustand nach diesem Dokument-Commit: **SOURCE_INVENTORY_RECORDED** (Phase A). Kein Produktumbau, kein Signieren, kein Fold7-Anspruch.
