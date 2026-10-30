# EXP-20 P2 · Übergabe (Opus, 30.10.2026)

**Status: WIP / START-CHECKPOINT.** P2 ist noch nicht ausgeführt. Dieses Dokument wird nach jedem Meilenstein fortgeschrieben.

- **Branch:** `claude/continuum-jarvis-exp20-p2-kxrxy2` (im selben Repo `Ullmann27/device-ai`).
- **Basis:** PR #253, `research/jarvis-voice1-stale-recognizer-session-20261030`, `ed408a7d921e47b5730b06f1f514268e249bd5f7`.
- **Draft-PR:** gegen die #253-Basis (Link im PR-Verlauf).
- **Auftrag:** `docs/research/exp20/CONTINUUM-JARVIS-ULTIMATE-EXP20-P2-AUFTRAG-20261030.md` auf PR #255 (gleichlautend mit dem Chat-Auftrag).

## 1. Live-Readback der PR-Linien (30.10.2026, GitHub-API + `git merge-base`)

| PR | Branch | HEAD | Bezug zu #253 |
|---|---|---|---|
| #251 | `research/continuum-owner-resilient-jarvis-charter-20261030` | `397b25ecd23a004ab203e0e476d9df3425a67f33` | **nicht** Vorfahre von #253. Merge-Base `7a01138466e102bff46d95d262a9da5d23d851fd`; #251 hat 5 spätere Commits, nur Doku (AGENTS.md, Register, Vertrag, Charta: Dauerregel BIO-NEURONAL-4). Gelesen, nicht übernommen. |
| #252 | `opus/jarvis-voice1-fix-20261030` | `2747f1a0600cba386defeb1a4641338fe8519d18` | direkter Vorfahre von #253 (8 Commits dazwischen) |
| #253 | `research/jarvis-voice1-stale-recognizer-session-20261030` | `ed408a7d921e47b5730b06f1f514268e249bd5f7` | **Basis dieses Branches** |
| #245 | `opus/continuum-m9-m16-20261030` | `29c1e1e1bef289cddf1e0c7854f2511923b93c0f` | separat. Merge-Base `0f6aa264…`, 5 eigene Commits (M9/M13-Doku). Berührt weder Router, `research/argon4/`, `research/exp20/`, `AgentActivity.kt` noch `LocalConversationContext.kt`. Nicht übernommen. |
| #249 | `research/continuum-self-evolution-shadow-20261030` | `d6a666322db225586b03ddc51f5285c10f39dfe3` | separat. Merge-Base `0f6aa264…`, 43 eigene Commits. Berührt dieselben Pfade ebenfalls nicht. Nicht übernommen. |
| #254, #255 | Auftragsdokumente auf #253 | `5698ed59…`, `411e2d98…` | reine Doku, keine Messung, keine Implementierung |

Default-Branch `main` wird nicht angefasst.

## 2. Call-Graph am realen Entscheidungsweg (Blobs auf `ed408a7`)

- `ChatReasoningRouter.kt` Blob `f21a114c9ed810cc0ac52cf10fe00b5d56ff2a5e`, `ComputeAllocationPolicy.kt` Blob `4628ba00cb98e4c6abdc3e637837dc4ca1fc8aa0` (per `git hash-object` bestätigt).
- **Einziger Aufrufer** von `advise()`: `AgentActivity.kt:6146-6150`, nur bei `observations.isEmpty() && reflection == null`.
- **Operative Entscheidung heute:** `ReasoningStrategySelector` → DEEP/MULTI_CANDIDATE startet bis zu 4 Zusatz-Gemma-Pässe, sonst `null`. Das ist **nicht** dieselbe Entscheidung wie `ComputeAllocationPolicy.choose()` (8 Aktionen). `choose()` läuft nur im SHADOW.
- `argonShadowChoice` wird in `app/src/main` **nirgends gelesen** (write-only).
- `ComputeAllocationPolicy.Signals.freshnessNeeded` ist in `choose()` **wirkungslos**: weder in `bin`, `feasible` noch `utility` verwendet.
- Der einzige Kanal, über den ein Kontextsignal die `choose()`-Entscheidung ändern kann, ist `unresolvedReference`: Bin-Suffix `:REF`, `CLARIFY` wird zulässig, `MODEL_*` unzulässig.
- **Bereits vorhanden:** `research/argon4/DialogStateKernel.kt` läuft produktiv im SHADOW (`LocalConversationContext` → `Argon4ConversationShadow`) und liefert `wouldClarify`. Er zählt **Turns, keine Zeit**. Sein Ergebnis ist **nicht** mit `unresolvedReference` verbunden; der Router setzt dort fest `false`.
- **Keine Ereigniszeit am Router:** `advise()` erhält Text, Kontext, `groundedRecall`, `modelReady`. `nowMillis = System::currentTimeMillis` ist eine Wanduhr und dient nur der Deadline. Ein monotones Δt zwischen Turns kommt am Entscheidungspunkt nicht an.

### Signalherkunft am Entscheidungspunkt

| Signal | Codequelle | Status | Zeitbasis | Evidenz-Version | Datenschutzklasse |
|---|---|---|---|---|---|
| `evidence` | Router :49-51 (`groundedRecall == null` → NONE, sonst QUERY) | gemessen, aber grob (keine EWM2-CLARIFY/ABSTAIN-Unterscheidung) | Turn | `f21a114` | intern, kein Inhalt |
| `coverage` | :54 `0.0` | **Ersatzwert** | – | `f21a114` | – |
| `claimFlags` | :54 `0` | **Ersatzwert** | – | `f21a114` | – |
| `unresolvedReference` | :54 `false` | **Ersatzwert** (DialogStateKernel existiert, unverdrahtet) | – | `f21a114` | – |
| `freshnessNeeded` | :55 `false` | **Ersatzwert, zudem wirkungslos** | – | `f21a114`/`4628ba0` | – |
| `toolIntent` | `ContinuumTaskClassifier.classify(task).expectedToolUse` | gemessen (lexikalisch) | Turn | `ContinuumIntelligence.kt` | abgeleitet aus Nutzertext |
| `personal` | `groundedRecall != null` | Proxy | Turn | `f21a114` | Hinweis auf Besitzerfakt |
| `modelReady` | `localRuntime.state == READY` | gemessen | Turn | AgentActivity | Gerätezustand |
| `internetAllowed` | :57 `false` | **Ersatzwert** (`internetConsent` existiert, nicht übergeben) | – | `f21a114` | – |
| `lowBattery`, `throttled` | Default `false` | **Ersatzwert** | – | `4628ba0` | – |
| `remainingModelCalls` | `config.maxExtraCalls = 4` | Konfiguration | – | `f21a114` | – |
| Ereigniszeit / Δt | – | **nicht vorhanden** | – | – | – |

**Folge für P2:** Ein zeitlicher Arm braucht eine Eingabe, die der Produktivpfad heute nicht hat, nämlich die monotone Ereigniszeit pro Turn. P2 läuft deshalb als Host-Labor auf demselben realen `choose()`-Code. Für alle Arme gilt Informationsparität: Δt steht allen zur Verfügung, Arm A nutzt es per Definition nicht. Ein Anschluss im Produktivpfad würde eine Signaturänderung an `advise()` verlangen. Die wird nur bei bewiesenem Nutzen erwogen und ist nicht Teil dieses Checkpoints.

## 3. Toolchain dieses Hosts

- Linux x86_64, 4 vCPU (Intel Xeon @ 2.80 GHz), OpenJDK 21.0.12.1.
- `kotlinc-jvm 2.0.20` aus dem offiziellen JetBrains-GitHub-Release; ZIP-SHA-256 `5f5d2a8ad6a718a002acd0775b67a9e27035872fdbd4b0791e3cb3ea00095931` = veröffentlichte `.sha256`.
- JUnit 4.13.2 (SHA-1 `8ac9e16d…` = Maven Central), Hamcrest 1.3 (SHA-1 `42a25dc3…`), jeweils aus `/opt/gradle-8.14.3/lib`.
- **Android-SDK / `dl.google.com`: per Proxy gesperrt (HTTP 403).** Deshalb `testDebugUnitTest`, `lintDebug` und `assembleDebug` = **NOT_RUN (UNAVAILABLE)**. Keine APK, kein Fold7 (**DEVICE_NOT_RUN**).
- Der reale `choose()`-Pfad kompiliert host-seitig aus 23 unveränderten `app/src/main`-Dateien plus einem mechanisch erzeugten Shim. Der Shim enthält `enum Risk` und zwei Datenklassen, wörtlich aus `CommandInterpreter.kt` Z. 1–25, nur ohne `import android.content.Context`. Er liegt nicht unter `app/src`, damit der Android-Build keine Doppelklasse sieht.

## 4. Nächste Schritte

1. Aliasing-Falsifikation an `BoundedCtrnn`/`LeakyIntegrators`: erst roter Test, dann Fix.
2. P2-Harness bauen, Vorregistrierung mit SHA-256 committen, **erst danach** der erste Arm-Lauf.
3. A/B/C plus Kontrollen auf DEV/VAL, dann Freeze, dann Holdout genau einmal öffnen.
4. Numerik (dense/sparse/expm1 gegen ODE-Referenz) und Vollkosten getrennt.

## 5. Sicherheit und Rückbau

- Keine Änderung an `app/src/main`, ToolGate, OwnerGate, `CognitiveGenome`, EWM2, Gemma oder NTW1.
- Invarianten `allowSelfReplication=false`, `allowHiddenPersistence=false`, `allowShutdownEvasion=false` unberührt.
- Rückbau: Draft-PR nicht mergen, oder die Commits dieses Branches in umgekehrter Reihenfolge per `git revert` zurücknehmen.
