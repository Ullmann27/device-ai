# GROK Phase A – No-Orphans-Matrix und Integrationsplan

**Status:** Analyse (kein Produktcode geändert).  
**Bots:** BOT 01 (Code Archaeology & Orphan Hunter) und BOT 02 (Integration Dependency Engineer).  
**Auftrag:** Heinz t131u / Phase A.  
**Repository:** Ullmann27/device-ai.  
**Ausgangs-SHA (Basis dieses Dokuments und des Arbeitsbranches):** `d013476c1f5a34ee809e1dd1a3421a333bc8f1e2`  
(= Live-HEAD von PR #699, Branch `code52/cinema-native-qa-review-fixes-20261110`, Stand 10.11.2026 13:16 Europe/Vienna).  
**Analysezeit:** 10.11.2026, 13:16–13:30 Europe/Vienna (CET, UTC+1).  
**Methode:** `gh` als Ullmann27; `git merge-base --is-ancestor`; `git cherry -v`; `git patch-id`; Datei-/Symbolsuche im ausgecheckten #699-Baum; lokale Probe-Merges in `/tmp` (nicht gepusht).  
**Einschränkung:** Der Arbeitsclone war shallow. Ancestor-Negativbefunde älterer Linien ohne gefundenen `merge-base` sind als **nicht gesichert** markiert, nicht als Beweis fehlender Verwandtschaft.

Nichts in diesem Dokument ist ein Merge-, Signier- oder Release-Auftrag. Der Cinema-Hauptintegrator (PR #698) bleibt für das Zusammenführen in den Release-Kandidaten zuständig.

---

## 1. Live-Inventur (Remote-HEADs, geprüft 13:16–13:23 Europe/Vienna)

| PR | Branch | HEAD (vollständig) | Basis-Branch @ Base-OID | PR-Stand | QA (validate-source-and-build) |
|---|---|---|---|---|---|
| #698 | `code52/final-integrator-cinema-20261110` | `49c0f67e25e999e3c7c1e26799f320f3ae32c35e` | `integration/code52-swarm-20261110` @ `99a0ca1a4b9c6e795c0e1d10a64b6a110cffa73a` | Draft, MERGEABLE | Lauf [42809973686](https://github.com/Ullmann27/device-ai/actions/runs/42809973686) auf genau diesem SHA: **in_progress** (Stand 13:23 Europe/Vienna) |
| #699 | `code52/cinema-native-qa-review-fixes-20261110` | `d013476c1f5a34ee809e1dd1a3421a333bc8f1e2` | `code52/final-integrator-cinema-20261110` @ `eaaa787a08df2e789e967e8422bd618608cbdc94` | Draft, MERGEABLE | Lauf [42810068910](https://github.com/Ullmann27/device-ai/actions/runs/42810068910): **FAILURE** |
| #700 | `code52/cinema-original-signer-qa-binding-20261110` | `5901845568572e83e96f8e1a28bb62f471064bd1` | `chatgpt/code52-owner-signing-20261109` @ `d34f3ee9c2e4127a868792368f5775f69394ec7d` | Draft, MERGEABLE | kein Cinema-QA-Job |
| #701 | `chatgpt/code52-cinema-document-learning-isolation-20261110` | `32bb6e6e04a7af07fada01f77f89c3aa4ad6cbd6` | war #699 @ `de951cdcc51c8dfd037b6aab1806a642f0e12c9d` | **MERGED** in #699 via `d013476c1f5a34ee809e1dd1a3421a333bc8f1e2` | — |
| #686 | `integration/code52-swarm-20261110` | `99a0ca1a4b9c6e795c0e1d10a64b6a110cffa73a` | `chatgpt/code52-cinema-branch-safe-qa-20261110` @ `322c0c1d080b3f99262c6a97ce15c7a7b685688f` | Draft, MERGEABLE | SUCCESS (Lauf 42782461626) |
| #697 | `code52/final-integrator-thermal-20261110` | `070ed1f44a5600b0ce18f7305fa0b560bd67876b` | swarm @ `99a0ca1a…` | Draft, MERGEABLE | SUCCESS |
| #690 | `chatgpt/code52-swarm-document-lifecycle-fix-20261110` | `1e8086b34622c98e4c255a8062e5455d749e6dfa` | swarm (älterer Punkt `1c3929c43861…`) | Draft | FAILURE (isolierter Branch) |
| #691 | `code52/cockpit-fidelity-20261110` | `9383520ddb38bf3af1154416b7e0876d84cd10e9` | swarm (älterer Punkt) | Draft | FAILURE (isolierter Branch) |
| #692 | `code52/cinema-flyin-20261110` | `4ceed5a678df48c56e5641c66c336a179f3ff1fb` | swarm (älterer Punkt `24a28f8ab9c4…`) | Draft | FAILURE (isolierter Branch) |
| #693 | `code52/visual-style-20261110` | `5066ec8c8a9f21c7aa9936d5975d68d4954ab323` | #691 (älterer Punkt `7fba7c7af6d6…`) | Draft | FAILURE (isolierter Branch) |
| #694 | `code52/avatar-gaze-light-20261110` | `fb2ab6bc029a0a3d21acf543cdfc816e012c3b28` | swarm @ `24a28f8ab9c4…` | **MERGED** 10.11.2026 09:25 Europe/Vienna | SUCCESS (Lauf 42777874490) |
| #695 | `chatgpt/code52-fold7-thermal-budget-guard-20261110` | `5ae101357675e8e278c506b52ddf87412c39416c` | swarm @ `24a28f8ab9c4…` | Draft | kein validate-source-Job sichtbar |
| #696 | `code52/visual-depth-ref-d-20261110` | `4f6e5adb4e15d5789f50053c4c15ada6df71de20` | #693 (älterer Punkt `7a7e9c58d04a…`) | Draft | FAILURE (isolierter Branch; vormittägliche Angabe „13 Failures“ nicht in dieser Runde neu gezählt) |
| #687 | `code52/fui-worlds-20261110` | `ea976247292989a46790bc0fa87670a3feb08fe7` | swarm | **MERGED** | — |
| #688 | `code52/cinema-avatar-20261110` | `dffccc87eda547d7d3b8332b727ead6d1df20336` | swarm | **MERGED** | — |
| #689 | `code52/cockpit-20261110` | `f9383ba814f885ed7ecda3bb597f0d4fd04cd10a` | swarm | **MERGED** | — |
| — | `code52/final-integrator-ui-fixes-20261110` | `3ba8fc4f6cb6c5d9d737d149b6b90b8e5ed502dd` | kein offener PR gefunden | — | — |
| — | `wip/checkpoint-20261110-render-combo` | `9b16e9f215b914206e213fb6504f8d083256d2f4` | kein PR | Checkpoint eines ungelösten Probe-Merges | — |

Weitere geprüfte offene `chatgpt/code52*`-PRs der Cinema-Linie vor dem Swarm: #677, #680, #681, #683, #684, #685 (siehe Matrix). #682 ist der ältere Signer-Kandidat (`a7125a06af7281a7b32564a9b32e5b9f4e0230a8`). Offene `grok/*`-PRs: #488, #479, #377 plus Branch `grok/e2e-erster-denkdurchlauf` ohne passenden aktuellen Cinema-PR.

Wichtige frühere QA-Punkte:

- Lauf [42803682785](https://github.com/Ullmann27/device-ai/actions/runs/42803682785) auf #699 @ `de951cdcc51c8dfd037b6aab1806a642f0e12c9d` (vor dem Merge von #701): **SUCCESS** (Android-JUnit laut Auftraggeber 4.346 Tests, 0 Fehler, ARM64-Prüfbuild). Dieser grüne Stand beweist **nicht** den späteren SHA `d013476c…`.
- Lauf 42810068910 auf #699 @ `d013476c…`: Step „Full Node and critical regression gates“ rot. Artefakt `Code52-Cinema-QA-Evidence` ausgewertet (siehe Abschnitt 5). Critical-Node: PASS. Full-Node: **2446 PASS / 2 FAIL / 37 SKIP**. Gradle, Lint und ARM64-APK auf diesem SHA **NOT_RUN**, weil der Job nach dem Node-Fehler abbrach.

---

## 2. Topologie: #698 und #699 sind auseinandergelaufen

`git merge-base` von #698 HEAD und #699 HEAD ist `eaaa787a08df2e789e967e8422bd618608cbdc94` (`test(code52): inspect intentionally hidden native card without requiring it to be visible`).

`git rev-list --left-right --count #698...#699` = **2** einzigartige Commits auf #698, **12** einzigartige Commits auf #699.

### 2.1 Nur auf #698 (`49c0f67e…`), nicht auf #699

- `2581382b08a6d4600b8b0f685b620b21f23078c3` — `fix(code52): keep AvatarDirector and native render pose on one wall-time clock`  
  Ändert `ContinuumHologramView.kt` und `JarvisHoloAvatar.kt` von `SystemClock.elapsedRealtime()` zurück auf `System.currentTimeMillis()`.
- `49c0f67e25e999e3c7c1e26799f320f3ae32c35e` — `test(code52): assert the Ref-B 15sp tile-label design contract without relaxing tolerances`  
  HEAD-Diff gegen #699 betrifft in `JarvisShellLayoutTest.kt` praktisch nur den Kommentar; die Assertion `JarvisDesign.Type.tileLabel.size == 15f` ist auf beiden HEADs vorhanden.

### 2.2 Nur auf #699 (`d013476c…`), nicht auf #698

Reparaturen ab `eaaa787a…` plus Merge von #701, darunter:

- `0b17f7fa71443efc9aa10302da8290feebdb1c5a` — Native-Avatar-Test auf monotoner Produktionsuhr
- `f9d0822d5cedce175e763cb4a925725a708af440` — Ref-B 15sp-Kacheltexte „without reducing readability“
- `da290c5d852787b2ca47b16ed1c11b274ad185de` — Native-Control-Sichtbarkeit
- `3cc95ec76f21879d23a2698159c8c420bc1bffa4` — Avatar-Fenstermotion und Turn-Ende serialisieren
- `45cb2579e092f63423c5446cb737097b766fe77d` — Settled-Cockpit nach Outbound-Animation wiederherstellen
- `4da5a71d6f9063f6739531498bb6ce876e32dc4a` — Stress-Test paralleler Window-Dispatch
- `de951cdcc51c8dfd037b6aab1806a642f0e12c9d` — Blickfenster bei gated exit schließen (letzter **grün** nachgewiesener #699-SHA)
- `9184d4a293c94a2d92f39b681f39f930b68f8a34`, `a327c969c0f8e15baa19ac5abb848b9fcd3035bc`, `cac56510fb6d75de3987e6fb84070d959501bb6a`, `32bb6e6e04a7af07fada01f77f89c3aa4ad6cbd6` — Dokumentenisolation (#701)
- `d013476c1f5a34ee809e1dd1a3421a333bc8f1e2` — Merge-Commit von #701

Weder `49c0f67e…` noch `d013476c…` ist Ancestor des anderen.

---

## 3. Uhrenvertrag (wichtigster semantischer Konflikt)

Auf **beiden** HEADs übergibt `AgentActivity` dem `AvatarDirector` `android.os.SystemClock::elapsedRealtime` (Kommentar: Glance- und Work-TTLs sollen dieselbe monotone Uhr nutzen wie Observed-Gaze-Alter). Commit `1e8086b34622c98e4c255a8062e5455d749e6dfa` (#690-Spitze) ist genau diese Director-Umstellung; der resultierende Code steht bereits in #698 und #699.

Danach gehen die Renderer auseinander:

- **#699** (`d013476c…`): `ContinuumHologramView` und `JarvisHoloAvatar` nutzen `elapsedRealtime()`. Das passt zum Director und zum Test `0b17f7fa…` („production monotonic clock“). Der Swarm-Quellvertrag `.github/continuum/code52-final-swarm-avatar-cinema-merge.test.mjs` prüft explizit `android.os.SystemClock::elapsedRealtime` in `AgentActivity`.
- **#698** (`49c0f67e…`): Commit `2581382b…` stellt Hologramm und `JarvisHoloAvatar` auf `System.currentTimeMillis()` um, lässt den Director aber auf `elapsedRealtime`. Das erzeugt auf #698 selbst eine **geteilte Uhr**. Ein sauberer Git-Merge von #698 in #699 würde diese Umstellung konfliktfrei anwenden und den #699-Renderer ebenfalls spalten.

`git cherry` markiert `2581382b…` und `1e8086b34…` als **nicht** gleichwertig (`patch-id` verschieden, verschiedene Dateien).

Empfehlung: #698-Commit `2581382b…` **nicht** übernehmen. #699-Uhrenvertrag beibehalten.

---

## 4. No-Orphans-Matrix

Legende: **integriert** = SHA ist Ancestor von #699 und/oder inhaltlich nachweisbar im Produktionspfad von #699. **teilweise integriert** = Kerninhalt übernommen, Spitze oder Begleitcommits nicht. **ersetzt** = jüngere Cinema-Linie hat den Zweck übernommen. **nicht angeschlossen** = Inhalt liegt nicht in #698/#699. **blockiert** = darf nicht ohne Eigentümerentscheidung oder nicht als Produktmerge behandelt werden.

„Im Produktionspfad“ bedeutet hier: Klasse nicht nur vorhanden, sondern von `AgentActivity` / Cockpit-Bus / View aufgerufen (siehe Abschnitt 6).

### 4.1 Cinema-Swarm und Integrator

| Entwicklung | Quelle / SHA | in #698? | in #699? | Produktionspfad auf #699 | Klasse |
|---|---|---|---|---|---|
| Swarm-Basis #686 | `99a0ca1a4b9c6e795c0e1d10a64b6a110cffa73a` | ja (Ancestor) | ja (Ancestor) | gesamte Cinema-Linie sitzt darauf | integriert |
| FUI-Welten / Document Blueprint #687 | `ea976247292989a46790bc0fa87670a3feb08fe7` | ja | ja | `DocumentBlueprintProjection`, `DocumentBlueprintEvents`, Aufrufe in `AgentActivity` (`documentReceived` / `documentPipelineStep` / `documentDraftReady`) | integriert |
| Cinema-Avatar-Verträge #688 | `dffccc87eda547d7d3b8332b727ead6d1df20336` | ja | ja | EXP-Verträge über Swarm in Cinema | integriert |
| Holographisches Dock #689 | `f9383ba814f885ed7ecda3bb597f0d4fd04cd10a` | ja | ja | Dock/Status über Swarm in `JarvisShellLayout` | integriert |
| Thermal-Integrator #697 | `070ed1f44a5600b0ce18f7305fa0b560bd67876b` | ja | ja | übernimmt #690/#695 per Re-Apply (siehe unten) | integriert |
| Cinema-Hauptintegration #698 | `49c0f67e25e999e3c7c1e26799f320f3ae32c35e` | ja | **nein** | zwei Spitzencommits fehlen in #699; siehe 2.1 und Abschnitt 3 | teilweise integriert (gemeinsame Basis `eaaa787a…`; Spitze nicht) |
| Cinema-Reparaturen #699 | `d013476c1f5a34ee809e1dd1a3421a333bc8f1e2` | **nein** | ja | aktueller Reparaturstand inkl. #701 | integriert in sich; fehlt in #698 |
| Dokumentenisolation #701 | `32bb6e6e04a7af07fada01f77f89c3aa4ad6cbd6` | **nein** | ja (Ancestor + Merge `d013476c…`) | `Airbus2100CockpitOrchestrator`: `DocumentLearned -> Unit`; `DocumentBlueprintProjection.onLearned = Unit` | integriert in #699; nicht in #698 |

### 4.2 Heutige Feature-PRs #690–#696

| Entwicklung | HEAD | Ancestor von #699 / #698? | `git cherry` gegen #699 | Produktionspfad | Klasse |
|---|---|---|---|---|---|
| #690 Dokument-Lifecycle | `1e8086b34622c98e4c255a8062e5455d749e6dfa` | nein / nein | Feature-Patches `-` (gleichwertig über #697: `7dcf0ad6f…` / `cfc5cbcd2…` und Tests). `+` nur CI-Commits (`add9ad69f`, `bc4eb0837`, `8aacdd527`) und `1e8086b34` (Director-Uhr; Code bereits in #699 vorhanden) | Document-Lifecycle und Memory-Hijack-Schutz über #697+#701 im Orchestrator; `CinemaWindowGazeBridge` bleibt in #699 abonniert (ein Merge der #690-Spitze würde diese jüngeren #699-Stellen gefährden) | teilweise integriert (Produktkern ja, Branch-Spitze/CI nein) |
| #691 Cockpit-Fidelity | `9383520ddb38bf3af1154416b7e0876d84cd10e9` | ja / ja | — | `JarvisShellLayout` packed Fold7/Tablet; Phone darf weiter scrollen (in #698-Beschreibung genannt, hier nicht neu vermessen) | integriert |
| #692 Fly-in inkl. Wegflug-WIP `98f415a6775f41eeb57b20755a3b63f7eb75fc84` | `4ceed5a678df48c56e5641c66c336a179f3ff1fb` | ja / ja | — | `JarvisCinemaEntrance` reagiert auf echte `CommandFlyIn`/`DocumentQuestion`/`ActionStarted`/`TurnEnded`; Recede der Idle-Kacheln ist im Source verdrahtet | integriert |
| #693 Visual Style | `5066ec8c8a9f21c7aa9936d5975d68d4954ab323` | nein / nein | nur zwei Merge-Commits (`5066ec8c8`, `fc02385fa`) „Merge branch cockpit-fidelity into visual-style“, kein eigener Inhalt | Visual-Inhalt steckt in #696, und #696-HEAD **ist** Ancestor | integriert (Inhalt); Spitze = leere Merges |
| #694 Avatar-Blick/Licht | `fb2ab6bc029a0a3d21acf543cdfc816e012c3b28` | ja / ja (auch Ancestor des Swarms) | — | `AgentActivity` abonniert `CinemaWindowGazeBridge(avatarDirector)`; 80-ms-Lookahead in `TtsHologramSignal` (`LOOK_AHEAD_MS = 80L`) wird vom Swarm-Quellvertrag geprüft | integriert |
| #695 Thermik EXP15 | `5ae101357675e8e278c506b52ddf87412c39416c` | nein / nein | beide Commits `-` (gleichwertig über #697: `2cc58787e…`, `31906902b…`) | `JarvisRenderQuality` darf EXP15 nicht ungedrosselt HIGH erzwingen (Swarm-Vertrag prüft das) | integriert (gleichwertige Patches, nicht dieselben SHAs) |
| #696 Bildwirkung / Tiefe | `4f6e5adb4e15d5789f50053c4c15ada6df71de20` | ja / ja | 0 Commits voraus | Orb/HUD/Tiefenlook über Integrator-Merge `9314bc4995361f28dc657b45e0b2679221eaba89` in Cinema | integriert |

Die roten QA-Läufe der isolierten Feature-PRs beweisen nicht, dass ihr Inhalt in Cinema fehlt. Umgekehrt beweist das grüne `de951cdc…` nicht den SHA `d013476c…`.

### 4.3 Ältere Cinema2030-`chatgpt/code52*`-Linie

Nach zusätzlichem `git fetch --deepen=80` gilt:

| PR | HEAD | Ancestor von Swarm `99a0ca1a…` und damit von #698/#699? | Klasse |
|---|---|---|---|
| #685 Branch-safe QA | `322c0c1d080b3f99262c6a97ce15c7a7b685688f` | ja | integriert |
| #684 Grounded document question | `09635722e060b95603baa1095d5a5b010bb162b6` | ja | integriert |
| #683 Graphics audit | `01bdbadb1242339f908f7282cd53d0b8df2d6a02` | ja | integriert |
| #681 Prism pixel fix | `0df55d7c8d5cae8a53bf1a43e26651f902881e78` | ja (nach Deepen) | integriert |
| #680 Switch contract | `3fb0a731935fa156f9217602c21510980241c620` | ja (nach Deepen) | integriert |
| #677 Argon4 holographic rim | `bbcb61b9a451cb07a6e15847cce2ccd821afcaf9` | ja (nach Deepen) | integriert |
| #671 Unified integration | `fed197963453e0202584b5b162ce593fd2e5eef2` | **nein** (auch nach Deepen kein Ancestor von #683/Swarm) | teilweise integriert / nicht gesichert: Inhalt, den #677 mitgenommen hat, ist über #677 in Swarm; spätere #671-only-Commits nicht als SHA in Cinema nachgewiesen. Nicht blind mergen. |
| #669 Fold7 inference truth | `60d83acac26c16048c2138d0415dc9fe6fe594d1` | nein | nicht gesichert / nicht angeschlossen als SHA; Paralleltrack auf `copilot/code51-master-integrator-20261108` |
| #672 Project isolation | `c402b046c55987e68fc79be610b979e14a8e2faf` | nein | nicht gesichert; ein #671-Commit erwähnt „ported PR #672“, SHA-Ancestor fehlt |

### 4.4 Signer, UI-Fixes, WIP, historische Grok-Branches

| Entwicklung | SHA | in #698/#699? | Probe-Merge in #699 | Klasse |
|---|---|---|---|---|
| #700 Original-Signer-Bindung | `5901845568572e83e96f8e1a28bb62f471064bd1` | nein | `fatal: refusing to merge unrelated histories` (eigene Signer-Basis `chatgpt/code52-owner-signing-20261109`) | **blockiert** — eigener Signierpfad; nicht in den Produktbranch mergen; SHA-Bindung erst nach grünem Cinema-HEAD und Eigentümerentscheidung |
| #682 älterer Signer-Kandidat | `a7125a06af7281a7b32564a9b32e5b9f4e0230a8` | nein | unrelated histories | **blockiert** / ersetzt durch #700 als aktuellen Signer-Vorbereitungs-PR |
| `code52/final-integrator-ui-fixes-20261110` | `3ba8fc4f6cb6c5d9d737d149b6b90b8e5ed502dd` | nein (2 Commits über gemeinsamem `9314bc4995361f28dc657b45e0b2679221eaba89`) | CONFLICT in `app/src/uiTest/java/at/heinz/deviceai/JarvisWorkWindowHostViewportUiTest.kt`; `JarvisShellLayout.kt` würde `dialogTop` von `8f` auf `0f` setzen (`fbe35a7de4c3b029a9f483859833c375656420ef`) | **nicht angeschlossen** (kleiner Layout-/Test-Nachzug, kein PR) |
| `wip/checkpoint-20261110-render-combo` | `9b16e9f215b914206e213fb6504f8d083256d2f4` | nein (1 Commit über `7a7e9c58d04a…`) | CONFLICT in 7 Dateien inkl. `JarvisTiles.kt`, `JarvisCinemaEntrance.kt`, `CockpitAnimationPresenter.kt` | **blockiert** als Merge-Kandidat — absichtlich ungelöster Probe-Merge-Checkpoint, nicht integrieren |
| `grok/jarvis-closeout-20261104` #488 | `2375aea3d55a058516d0f1ff3d9e376172fc2124` | nein | unrelated histories in diesem Clone | nicht angeschlossen / ersetzt; **nicht gesichert**, ob die Historie nur wegen Shallow-Clone unverbunden wirkt. Nicht in Cinema mergen. |
| `grok/jarvis-design-system` #479 | `3fc7edf4a5d2988b879d4e571db97a18c873283b` | nein | unrelated histories | wie oben |
| `grok/chief-e2e-tests` #377 | `72c77e1e26ca3ae70199a3b1d6a2abe1651855aa` | nein | unrelated histories | wie oben |
| `grok/e2e-erster-denkdurchlauf` | `189fca8098917f42f52b02be8d4f36dda2debe4e` | nein | unrelated histories | wie oben |
| `wip/stop-20261104-integration-nav` | `69b823133e132c5535626edb34eb26327bebb86c` | nein | unrelated histories | Stopp-Snapshot 04.11., nicht Cinema-Merge-Kandidat |
| `wip/stop-20261104-sicherheit-m5fg` | `d1da1d1dbf31e25d4816d0df0362195e5e282a67` | nein | unrelated histories | wie oben |
| `wip/stop-20261104-welten-optik-design` | `c7fc330d911af82dbb0baff4c2a56ed0e074b05d` | nein | unrelated histories | wie oben |

Checkpoint-Dokument `docs/handover/CHECKPOINT-CODE52-20261110-0905.md` (`1258a2888d1926723f46c773d32a45725869ab70` auf `docs/code52-handover-chatgpt-20261110`) ist **kein** Ancestor von #699. Es ist Dokumentation, kein Produktorphan.

---

## 5. Aktueller Hauptblocker: Node-Quellvertrag nach #701

Artefakt von Lauf 42810068910, ausgewertet aus `_temp/code52-full-node/report.md` und `full-node.xml`:

- Critical-Node: **PASS**
- Full-Node: **FAIL**, 2446 / 2 / 37
- Genau diese zwei Source-Contract-Tests erwarten noch den alten, schwächeren `DocumentLearned`-Guard:

1. `.github/continuum/code52-document-lifecycle-contract.test.mjs`, Test `memory and cancelled turns cannot create false document world`  
   erwartet  
   `is CockpitAnimationEvent.DocumentLearned -> { … if (!document.hasDocument) return`  
   und  
   `fun onLearned(event: CockpitAnimationEvent.DocumentLearned) { if (!hasDocument) return`
2. `.github/continuum/code52-final-swarm-avatar-cinema-merge.test.mjs`, Test `thermal and document protections must survive visual branch merges`  
   erwartet dieselbe `onLearned { if (!hasDocument) return }`-Form.

#701 hat bewusst härter isoliert:

- `Airbus2100CockpitOrchestrator`: `is CockpitAnimationEvent.DocumentLearned -> Unit`  
  (Kommentar: Memory-`docId` ist kein verifizierter Schlüssel der geöffneten PDF-Intake; das Ereignis darf die Dokumentwelt nicht übernehmen. Andere Bus-Teilnehmer behalten es für Orb/Gedächtnis.)
- `DocumentBlueprintProjection.onLearned(...) = Unit`

Das ist kein Nachweis eines Produktregressionsfehlers in Android-JUnit (diese rannten auf `d013476c…` nicht mehr, weil der Job vorher abbrach). Es ist ein **nachgezogener Quellvertrag**, den BOT 15 an die stärkere Isolation anpassen sollte. #701 nicht zurückdrehen, nur um die alten Regexes zu erfüllen.

---

## 6. Event-Verträge und fehlende Verbindungen

### 6.1 `CockpitAnimationEvent` (Produktionsbus)

Datei: `app/src/main/java/at/heinz/deviceai/cockpit/CockpitAnimationEvent.kt`.

Vertrag im Dateikopf: Events entstehen nur auf echten App-Pfaden (TurnLifecycle, ToolGate, ChiefWorkerPool, Dokument-Intake). Renderer erzeugen keine Events. Sprache und Lip-Sync gehören **nicht** auf diesen Bus, sondern auf `AvatarDirector` / `AvatarSinkRegistry`.

Bekannte Typen und nachgewiesene Produktions-Emitter auf #699:

| Event | Emitter | Hinweis |
|---|---|---|
| `CommandFlyIn` | `CockpitAnimationTriggers.commandAcknowledged` ← `AgentActivity` nach `TurnLifecycle.acknowledge()` | Produktionspfad ja |
| `ActionStarted` / `ProcessTimeline` | dieselben Triggers; ToolStart/-Ende aus `createToolGate` (`toolStarted` / `toolFinished`) | Produktionspfad ja |
| `DocumentQuestion` | `cockpitTriggers.documentReceived` in `AgentActivity` | Produktionspfad ja |
| `DocumentPipelineStepEvent` (`kind = "document-pipeline"`) | `cockpitTriggers.documentPipelineStep` | Produktionspfad ja |
| `DocumentDraftReadyEvent` (`kind = "document-draft"`) | `cockpitTriggers.documentDraftReady` | Produktionspfad ja |
| `TurnEnded` | `cockpitTriggers.turnEnded` nach `chiefTurn.deliver()` | Produktionspfad ja |
| `BootStarted` / `CockpitEntered` | `bootOnce` / `CockpitTimeline` | Sitzungsstart, keine vorgetäuschte Arbeit |
| `AlarmRaised` / `AlarmCleared` | Thermik- und Speicherdruckpfade in `AgentActivity` | Produktionspfad ja |
| `AreaChanged` | u. a. Chat→Home vor lokaler Planung | Produktionspfad ja |
| `RecallHits` / `MemoryStored` / `DocumentLearned` | `MemoryEventSource` und direkte `cockpitBus.emit` in `AgentActivity` | Memory-Bus lebt; #701 verhindert, dass `DocumentLearned` die PDF-Arbeitswelt übernimmt |
| `CalculationShown` | `CalculationEmittingRetriever` über echte Retrieval-Scores (`score = cos(q, d) × w_zeit`) | Nur bei mindestens einem echten Treffer; feste deutsche Schritt-Labels, kein Gedächtnistext |
| `AccountCardFlyIn` | `CockpitAnimationTriggers` und `ApprovedAccountEventSource` | **Quelle fehlt in Produktion:** `accountTools` ist leer, daher „never emits“. Klasse existiert, Event wird nicht vorgetäuscht. |
| `WindowProjectedMotion` (`kind = "window-projected-motion"`) | `WindowProjectedMotionSampler` in `JarvisShellView` → `CockpitAnimationPresenter` setzt `onProjectedMotion = { bus.emit(it) }` → `CinemaWindowGazeBridge` | Produktionspfad ja. Phasen `FLYING` / `SETTLED` / `CLOSING` / `CLOSED`. `CockpitAnimationPresenter` ignoriert dieses `kind` bewusst (`return`), `CockpitTimeline` liefert für jedes `Extension` eine leere Schrittliste. Unbekannte Extension-Kinds werden ignoriert. |
| `Extension("math")` | nur Testdouble | im Event-Kommentar „Not implemented yet“ |

`WindowProjectedMotion.atMillis` und `CockpitAnimationEvent.atMillis` sind im Event-Kommentar als Wanduhr der realen Begebenheit beschrieben. Der Avatar-Director nutzt trotzdem `elapsedRealtime`. Das ist der oben beschriebene Uhrenvertrag; er ist auf #699 intern konsistent, auf #698 nach `2581382b…` nicht.

### 6.2 Abhängigkeiten zwischen Cinema-Modulen

Empfohlene Richtung (bereits weitgehend so verdrahtet):

1. `TurnLifecycle` / `ToolGate` / Dokument-Intake → `CockpitAnimationTriggers` → `CockpitAnimationBus`
2. Bus → `CockpitAnimationPresenter` (Karten/Fly-in) + `JarvisCinemaEntrance` (Wegflug/Einflug, Recede) + `Airbus2100CockpitOrchestrator` (Arbeitswelt, ehrliches Document Blueprint) + `CinemaWindowGazeBridge` (Blick)
3. `JarvisShellView` publiziert projizierte Fenstermitten zurück auf den Bus (`WindowProjectedMotion`)
4. `AvatarDirector` bleibt auf eigener, monotoner Uhr; Lip-Sync über TTS-PCM mit 80 ms Vorlauf, nicht über den Animationsbus
5. `JarvisRenderQuality` / EXP15 darf Thermik-Böden nicht aushebeln
6. #701: Memory-`DocumentLearned` darf eine offene PDF-Intake nicht kapern

Ein erneuter Ganzbranch-Merge von #690 in #699 kollidiert genau in `Airbus2100CockpitOrchestrator.kt` und `DocumentBlueprintProjection.kt` — das sind die #701-Dateien. Deshalb Feature-Spitzen nicht noch einmal als Ganzes mergen.

---

## 7. Probe-Merges gegen #699 HEAD `d013476c…` (lokal in `/tmp`, nicht gepusht)

| Quelle | Ergebnis | Konfliktdateien / Bemerkung |
|---|---|---|
| #698 `49c0f67e…` | **CLEAN** | würde 3 Dateien ändern: `ContinuumHologramView.kt`, `JarvisHoloAvatar.kt`, `JarvisShellLayoutTest.kt`. Semantisch gefährlich (Uhren-Split, Abschnitt 3). Nicht übernehmen. |
| #693 `5066ec8c…` | CLEAN | nur leere Merge-Commits |
| #695 `5ae10135…` | CLEAN | Inhalt bereits gleichwertig vorhanden |
| #690 `1e8086b34…` | CONFLICT | `Airbus2100CockpitOrchestrator.kt`, `DocumentBlueprintProjection.kt` |
| ui-fixes `3ba8fc4f…` | CONFLICT | `JarvisWorkWindowHostViewportUiTest.kt` |
| wip-combo `9b16e9f21…` | CONFLICT | `CockpitAnimationPresenter.kt`, `JarvisCinemaEntrance.kt`, `JarvisShellRenderer.kt`, `JarvisShellView.kt`, `JarvisTiles.kt`, `JarvisCinemaEntranceTest.kt`, `JarvisWorkWindowHostViewportUiTest.kt` |
| #700, #682 | CONFLICT | unrelated histories (Signer-Linie) |
| grok/* und `wip/stop-20261104-*` | CONFLICT | unrelated histories **in diesem Clone** (shallow möglich); nicht mergen |

---

## 8. Kette: spricht → versteht → plant → Werkzeug → visualisiert → Avatar → geprüft → Antwort

Nachverfolgt auf #699 `d013476c…`. Kein Fold7-Lauf in dieser Runde; Aussagen zum Gerät bleiben **nicht gesichert**.

1. **Benutzer spricht.** `AndroidOnDeviceSpeechInput` (On-Device-`SpeechRecognizer`) liefert Text an `AgentActivity`. Route über `LocalSpeechInputPolicy` (Mikrofonrecht, Offline-Veto).  
   - Diktat: `applyLocalSpeechText` füllt nur den Composer. Kommentar im Source: „Recognition only fills the composer. Sending/actions remain an explicit owner gesture.“ Das ist kein Kettenbruch, sondern bewusste Freigabegrenze.  
   - Freisprechen: `applyConversationSpeech` → bei `Decision.SEND` `sendCurrentInput()`.
2. **Versteht / plant.** `sendCurrentInput`-Pfad erzeugt `TurnLifecycle("local-"+…)` , ruft `acknowledge()` auf und danach `cockpitTriggers.commandAcknowledged(...)`. Lokales Modell über `BoundedAgentOrchestrator`. Pronomen/Follow-ups über `LocalFollowUpTaskFrame` / Sitzungskontext; Schreib- und Werkzeugrechte bleiben am unveränderten Originaltext.
3. **Werkzeug.** `createToolGate` → `cockpitTriggers.toolStarted` / `toolFinished` nur für wirklich gestartete bzw. beendete Calls. Neuraler Text darf laut Kommentar nicht in die Device-`ToolGate`. Web nur über `ApprovedWebToolGate` und Eigentümerfreigabe.
4. **Cinema visualisiert.** `CommandFlyIn` startet `JarvisCinemaEntrance` (Idle-Kacheln weichen seitlich und in Z zurück) und `CockpitAnimationPresenter.flyInCards()` / `playWorkStage()`. Dokumentwelt nur aus echten `DocumentQuestion` / Pipeline- / Draft-Events. `DocumentBlueprintProjection`: „Missing facts stay absent: no placeholder steps, draft headings, buttons or progress.“
5. **Avatar reagiert.** `CinemaWindowGazeBridge` ist in `AgentActivity` auf den Bus gelegt und setzt `AvatarDirector.windowMoved`. PCM-Lip-Sync mit 80 ms Vorlauf bleibt verdrahtet (Quellvertrag). Blick-TTL hängt an `elapsedRealtime`.
6. **Ergebnis geprüft.** Tool-Szenen dürfen laut Source keinen eigenen Erfolg behaupten; verifiziertes Ergebnis bleibt in Dialog und Receipts. #701 verhindert, dass fremde Memory-Events eine offene PDF als „gelernt“ verbuchen.
7. **Antwort.** TTS getrennt vom Animationsbus; nach `turnEnded` / `chiefTurn.deliver()`.

### 8.1 Unterbrechungen und ehrliche Lücken

- **Account-Karten:** Eventklasse vorhanden, Produktionsquelle leer → keine Karte, keine Täuschung.
- **Math-Extension:** nicht implementiert.
- **Diktat startet keinen Auftrag** ohne Senden.
- **Leere Glasflächen** im Layout (Document/Process/Draft-Slots) können visuell nach „Arbeit“ aussehen, auch wenn der Inhalt laut Code leer/ehrlich bleibt. Das ist eine Gestaltungs-, keine Ereignislücke. Ob das Fold7-Bild so wirkt, ist hier **nicht gesichert** (kein Geräterender).
- **Grafik täuscht keine Tool-Arbeit vor**, soweit die geprüften Emitter an echte Triggers gebunden sind. Idle-Boot-Animation (`BootStarted`/`CockpitEntered`) ist Sitzungsstart.
- **CalculationShown** visualisiert echte Retrieval-Score-Teile, nicht eine erfundene Rechenaufgabe des Nutzers. Labels sind fest („Kosinus“, „Zeitgewicht“, „Treffer“).
- **#698-Uhren-Split** könnte Blicke zu früh ablaufen lassen (genau das Problem, das `1e8086b34` beschrieben hat). Auf #699 nicht vorhanden.
- Geräte-, TTS- und lange Dialogstabilität: in dieser Runde nicht ausgeführt.

---

## 9. Empfohlene Reihenfolge für den Cinema-Hauptintegrator

1. **P0 / BOT 15:** Node-Quellverträge in `.github/continuum/code52-document-lifecycle-contract.test.mjs` und `.github/continuum/code52-final-swarm-avatar-cinema-merge.test.mjs` an #701 anpassen (`DocumentLearned -> Unit`, `onLearned = Unit`). Isolierter Fix-Branch, Basis aktuelles #699-HEAD. #701-Verhalten behalten.
2. Exakte Cinema-QA auf dem **neuen** #699-SHA erneut laufen lassen (Node full + Android-JUnit + ARM64-unsigned). Ein grünes `de951cdc…` gilt nicht als Beweis.
3. **Nicht** #698-Spitze `2581382b…` nach #699 mergen (Uhren-Split). Den Kommentar-Commit `49c0f67e…` braucht #699 nicht.
4. **Nicht** die Feature-PRs #690, #693, #695, #696 als Ganzes erneut nach #699 mergen. Inhalt ist drin; #690 würde mit #701 kollidieren.
5. Optional, nach grünem #699: ui-fixes `fbe35a7de…` (`dialogTop = 0f`) manuell und konfliktarm übernehmen, Testdatei nicht blind mischen. Kein eigener PR vorhanden.
6. Danach #698 auf den grünen #699-Stand bringen (Fast-Forward oder Merge durch den Hauptintegrator, nicht durch parallele Bots). #698 verliert dabei bewusst `2581382b…`.
7. #700 erst nach dem eingefrorenen, grün nachgewiesenen Cinema-SHA neu binden. Kein Signieren, kein main-Merge, kein Force-Push.
8. `wip/checkpoint-20261110-render-combo`, `wip/stop-20261104-*` und historische `grok/*`-Branches als Archive stehen lassen, nicht löschen, nicht mergen.
9. Offene Feature-PRs als Draft/Provenienz offen halten.

---

## 10. Was der Hauptintegrator noch übernehmen muss — und was nicht

**Übernehmen (nach grüner Node-Reparatur auf einem Nachfolger von `d013476c…`):** den gesamten #699-Stand inkl. #701 in die Cinema-Hauptlinie #698.

**Nicht übernehmen:**

- #698-Commit `2581382b08a6d4600b8b0f685b620b21f23078c3`
- Ganzbranch-Merges von #690 / #693 / #695 / wip-combo / grok/* / `wip/stop-20261104-*` / #700 / #682
- #671-HEAD `fed197963453e0202584b5b162ce593fd2e5eef2` ohne gesonderten Cherry-Vergleich der späteren only-Commits

**Offen / BOT 15:** die zwei Node-Regexes.  
**Blockiert:** Originalsignierung (#700), Fold7-Gerätebeweis, Merge nach `main`.

---

## 11. Risiken vor APK-Freigabe

- #699 HEAD `d013476c…` ist **nicht** QA-grün (Node full-node rot; Android/ARM64 auf diesem SHA nicht gelaufen).
- #698 und #699 divergieren; ein naiver Merge ist git-sauber und trotzdem falsch (Uhr).
- Feature-PR-CI bleibt auf isolierten Spitzen rot; das ist kein zweites Integrationsdefizit, darf aber nicht als „Feature unintegriert“ missverstanden werden.
- Visuelle Nähe zu Referenz A/B/C/D und Fold7-Innendisplay ist in dieser Runde nicht bildlich geprüft.
- Phone-Layout (normale Höhen wie S26 / Xiaomi) wurde hier nicht neu ausgemessen; #691 steckt in Cinema, ui-fixes (`dialogTop`) nicht.
- Shallow-Clone: historische `grok/*` und `wip/stop-*` ohne nachweisbaren merge-base; Klassifikation dort bewusst konservativ.

---

## 12. Rückgabezeile

BOT 01+02 · Phase-A No-Orphans-Matrix, Eventverträge, Probe-Merges, Produktionskette · Ausgangs-SHA `d013476c1f5a34ee809e1dd1a3421a333bc8f1e2` · Status **Analyse**.
