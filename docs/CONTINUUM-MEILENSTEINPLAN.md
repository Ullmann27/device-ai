# Device AI Continuum — Meilensteinplan M1–M8

**Kontrollstand:** 18. Oktober 2026  
**Arbeitsstand:** PR [#28](https://github.com/Ullmann27/device-ai/pull/28) · Branch `continuum/argon-safety-review-20261017` · überprüfter HEAD `eb15d3e1accb958439334590e59bfb3cd13a9bcf`  
**Verbindliche Regel:** Ein Commit, eine grüne Source-Level-Prüfung oder ein selbst erstellter Report bedeutet **nicht**, dass eine APK gebaut wurde, eine echte unabhängige End-to-End-Reparatur stattgefunden hat oder ein KI-Modell auf dem Smartphone generiert hat.

## Gesamtstatus

| Meilenstein | Ziel | Technischer Stand | Freigabe-/Abnahmezustand |
| --- | --- | --- | --- |
| **M1** | Startkern, lokale Identität, kontrollierbares Gedächtnis | Implementiert; ältere Version auf Android geöffnet; frühere CI-Nachweise | **BASIS VORHANDEN**; aktuelle APK nicht abgenommen |
| **M2** | ToolGate, bestätigtes Lernen, gesicherte Recherche, gemeinsames Backlog-Format | Quellcode und UI-Verkabelung vorhanden | **IMPLEMENTIERT**, aktuelle Gesamtsuite offen |
| **M3** | Gesamtreparatur, reproduzierbare Tests, neue Preview-APK und Samsung-Abnahme | Gezielte Teilprüfungen vorhanden, neue UI eingecheckt | **BLOCKIERT**: vollständig ausgeführte aktuelle CI-/lokale Build-Evidenz fehlt |
| **M4** | Autonomer lokaler Entwicklungsworker: Fehler → isolierter Patch → unabhängige Tests → Evidenz → Review | Teamleader, Repair-Loop, Lease/Backlog, zwei Worktrees, M4-Gate angeschlossen | **INTEGRIERT, NICHT END-TO-END ABGENOMMEN** |
| **M5** | Echter lokaler generativer Inferenzpfad auf Android (LiteRT-LM) | Import, SHA-256, Runtime, Developer/Critic/TestDesigner/Judge angeschlossen | **OFFEN**: kein nachgewiesenes kompatibles geladenes Modell und kein Gerätelauf |
| **M6** | Generative Vorschläge sicher in unabhängigen Patch-/Test-Zyklus einbringen | Datenbasierte Vorschläge vorhanden; Worker-Reparatur deterministisch | **FORSCHUNG/INTEGRATION OFFEN**; keine autonome Codegenerierung bewiesen |
| **M7** | Messbar verbesserter Lernkern / neuronale Forschung und Qualität unter Holdout-Tests | SWB1/CRE1-Forschung und Schutz gegen Regression sind vorhanden | **EXPERIMENTELL**, kein Leistungsversprechen |
| **M8** | Kontinuität, Benchmark, optionaler von Eigentümer freigegebener Geräteverbund | Teilkonzepte und Grundlagen vorhanden | **SPÄTER**, nicht Voraussetzung für erste nutzbare APK |

## Verbindliche nächste Arbeitsfolge — keine neuen Nebenbaustellen

### Gate A — M3: beweisbarer Build und vollständige Regressionen

- [ ] Exakten Arbeits-HEAD erfassen, `git status --porcelain` ist vor der Prüfung leer, Entwicklungsbranch ist nicht `main`.
- [ ] Node 20/22: alle relevanten Node- und Backend-Suites **ausführen**, Exitcodes, tatsächliche Anzahl durchgeführter/fehlgeschlagener/übersprungener Tests und Logs ablegen.
- [ ] Echter Repair-E2E-Gate-Lauf bei `CONTINUUM_REPAIR_DEPTH=0` mit zwingenden Fällen; gewollte rekursive Nested-Test-Skips nicht mit fehlenden Pflichtprüfungen verwechseln.
- [ ] JDK 17, Gradle 8.11.1, Android SDK 36: Kotlin-Unit-Tests, Cross-Runtime-Parität (Backlog, CRE1, Contrastive), `:app:lintDebug`, `:app:assembleDebug -PdeviceAiPreview=true` ausführen.
- [ ] Prüfen, dass die **Pflichtparitätsfälle tatsächlich liefen** (`skipped=0` im Android-Unit-Profil) und die Rückvergleichsprüfungen bestanden.
- [ ] Preview-APK zum **exakten** geprüften Commit mit `sha256sum`, Paketname `at.heinz.deviceai.preview`, Versionsangaben und dauerhaftem Downloadartefakt bereitstellen.
- [ ] Auf Samsung installieren; Bedienung, Layout/150%-Schrift, TalkBack, Modell-Fehlermeldung, Cancel, Recherche, Shizuku-Nichtverfügbarkeit, Neustart und lokaler Backlog werden **praktisch** getestet. Siehe [Android UX-Abnahme](CONTINUUM-ANDROID-UX-ABNAHME.md).

**Beobachteter CI-Blocker:** [Regression-Run 39140640773](https://github.com/Ullmann27/device-ai/actions/runs/39140640773) beendete den Job mit **0 ausgeführten Schritten**. [APK-Run 39140445551](https://github.com/Ullmann27/device-ai/actions/runs/39140445551) beendete den Build-Job ebenfalls mit **0 ausgeführten Schritten**. Das ist **kein** verifizierter Test- oder Compilerfehler und **kein** bestandener Build. Externe Runner-Freigabe/Abrechnung prüfen **oder** unabhängig mit lokaler Toolchain bauen. Nicht blind erneut starten.

### Gate B — M4: echter autonomer lokaler Worker

- [ ] `bash scripts/astra-max-execute.sh validate` auf sauberem echten Checkout erfolgreich, inklusive schriftlichem Testnachweis.
- [ ] `bash scripts/astra-max-execute.sh worker-once`: echten bewusst beschädigten, *isolierten* Test-Fall ausführen; keine Änderungen im produktiven Checkout.
- [ ] WORKTREE 1: reproduzierter Fehler, begrenzter Kandidat, tatsächlich ausgeführte Tests.
- [ ] WORKTREE 2: Patch-SHA stimmt, erneute Tests sind unabhängig, ausreichend und keine Schein-Evidenz (keine zusätzlichen Skips, keine geringere zulässige Abdeckung).
- [ ] `development-backlog.json`, `repair-ledger.json`, `report.json`, `review-summary.md` und `teamleader-state.json` sind konsistent; `inference.mode=DETERMINISTIC_ONLY` mit `modelInvocations=0` falls keine Modellaufrufe erfolgten.
- [ ] Stromausfall/Crash, Interrupt, doppelte Ausführung, bereits bearbeiteter Commit, Lease-Verfall und sauberer Wiederanlauf mit realen Testfällen geprüft.
- [ ] Erst danach `worker-loop` ressourcenbegrenzt im lokalen Rechner/Termux betreiben. Keine ungeprüften Writes nach `main`.

### Gate C — M5: echte lokale Inferenz auf Gerät

- [ ] Echte kompatible, rechtmäßig verwendbare `.litertlm`-Datei auf Testgerät verfügbar; SHA-256 und unterstütztes Format/Backend sind geprüft.
- [ ] Import → Laden → kurzer lokaler Text → Streaming/Cancel → Entladen → erneutes Laden → App-Neustart nachweislich erfolgreich.
- [ ] Developer/Critic/TestDesigner/Judge mit echten Modellaufrufen, Modell-Fingerprint und Ablaufbelegen; Ergebnis **VORSCHLAG/NICHT AUSGEFÜHRT**.
- [ ] Kein Shell-Aufruf aus Modelltext, kein ToolGate-Bypass und keine versteckte Cloud-Ausweichroute.

## Reproduzierbare Steuerung

Auf dem Branch `continuum/argon-safety-review-20261017`, mit sauberem Git-Checkout und vorhandenen Abhängigkeiten:

```bash
git rev-parse HEAD
git status --porcelain
bash scripts/astra-max-execute.sh validate
bash scripts/astra-max-execute.sh worker-once
bash scripts/astra-max-execute.sh android-release
```

`worker-loop` erst nach erfolgreichem einmaligen E2E-Lauf aktivieren. Diese Befehle sind **Abnahme-Anweisungen**, keine Behauptung, dass sie am oben genannten HEAD bereits bestanden haben.

## Freigaberegeln

- PR #28 bleibt **Draft**, bis M3-Abnahmebelege vollständig sind. `main` bleibt unverändert.
- Jede Statusmeldung muss unterscheiden: **Code vorhanden**, **gezielter Teiltest grün**, **echte volle Suite grün**, **APK gebaut**, **auf Gerät nachgewiesen**.
- Release-Freigabe ist nur bei vollständiger M3-Abnahme zulässig; autonomes generatives Entwickeln wird erst nach M4 + M5 + gesonderter M6-End-to-End-Probe behauptet.
- Forschungsmeilensteine M7/M8 dürfen M3–M5 nicht verdrängen.

**Nächster konkreter Meilenstein:** M3-Gate A schließen, dann M4-Gate B; für den sichtbaren KI-Kern parallel den realen M5-Gerätenachweis vorbereiten.
