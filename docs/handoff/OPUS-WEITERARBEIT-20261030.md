# Continuum JARVIS – Gesamt-Weiterarbeitsauftrag von Opus 5.5 (30.10.2026)

**Für:** ChatGPT-6, Codex und andere Coding-KIs, spätere Opus-Sitzungen und Heinz.

**Zuerst lesen:**
1. Diese Datei.
2. Den Branch-Auftrag des Strangs, an dem du arbeitest (Tabelle unten).
3. `docs/research/exp20/EXP-20-M5-M6-STATUS-AND-NEXT-20261030.md` (Nachträge 1–3).

## 1. Offene Branches (alle gepusht, offen lassen, kein Merge nach `main`, kein Force-Push)

| Branch | Stand | Inhalt | Branch-Auftrag |
|---|---|---|---|
| `claude/continuum-jarvis-exp20-p2-kxrxy2` (Draft-PR #256) | siehe PR | EXP-20-Forschung P2–P4, M5/M5b/M5c, M6-B4-Belege; `app/src/main` unverändert | diese Datei |
| `claude/ewm-owner-subject-fix-kxrxy2` | `3ba1ba0` | EWM-Ersetzungsfix v2 (Produktivcode-Kandidat) | `docs/handoff/WEITERARBEIT-EWM-20261030.md` |
| `claude/m8-coding-agent-loop-kxrxy2` | `df20a18` | M8 Coding-Agent: Werkzeug-Loop, Befehl `scripts/continuum-agent.mjs`, Benchmark | `docs/handoff/WEITERARBEIT-M8-20261030.md` |

**Andere Forschungsstränge** (PR #244/#245, „M1–M16“) sind eine **eigene Nummerierung**, nicht unsere Produktmeilensteine. Ihre Zahlen hat Opus nicht nachgeprüft.

## 2. Meilensteinleiste (Produkt) – nur belegte Zustände

| Meilenstein | Zustand |
|---|---|
| M4 Host-Forschungslabor | **erreicht** |
| M5 Robuste Semantik | **nicht erreicht**. CRE1 kann Rollen nicht binden und unbekannte Wörter nicht ohne Vorwissen übertragen (gemessen). M5c ist vorregistriert. |
| M6 Kontextgedächtnis | **deterministische Basis als Patchkandidat** (EWM v2, Host-Belege). Neuronale Schicht gesperrt, bis M5 steht. |
| M7 Fold7-APK | **offen**: Geräteabnahme, Signatur, In-App-Updater nur durch Heinz |
| M8 Worker | **Loop und Befehl vorhanden, nur simuliert getestet**. Echte Modellmessung und Isolation fürs Telefon fehlen. |
| M9 JARVIS | **offen**: geschlossener, geprüfter Selbstverbesserungszyklus |

## 3. So würde ich (Opus) weitermachen – in dieser Reihenfolge
1. **EWM v2 abnehmen.** Review durch Heinz, fremder Ersetzungs-Holdout mit mindestens 200 Paaren, Gradle-Suite, Fold7-Fragen. Erst danach mergen, über einen eigenen PR in die Produktlinie. Grund: Das ist die einzige fertige, belegte Gedächtnisverbesserung.
2. **M8 echt messen.** Auf einem Docker-Host läuft der Benchmark mit Qwen2.5-Coder 1.5B, 3B und 7B, verglichen mit dem alten Einmal-Provider, vorregistriert. Grund: Ohne echte Inferenz ist jede Aussage über „Programmieren wie Opus“ unbelegt.
3. **M8 Isolation fürs Telefon**, über CI als Sandbox auf `continuum-agent/<id>`. Danach den Befehl an das CCC1-Journal anbinden und im App-Chat „Worker: <Auftrag>“ über `ContinuumWorkerChatRouter` → Typed-Bridge → Termux ermöglichen. Grund: Das ist der direkte Weg zu „Auftrag am Handy, Agent arbeitet“.
4. **M5c Spur G-role** mit den Armen B und C gegen Bag-Referenzen, auf neuem TRAIN/DEV/VAL. Danach die **Besitzerentscheidung Arm D**: ein vortrainierter lokaler Satz-Encoder, nach Prüfung von Lizenz, Größe, RAM und Fold7-Latenz. Grund: M5 ist ohne Vorwissen nicht erreichbar.
5. **M6 Gültigkeitsversionierung** in EWM, ohne zweiten Speicher. Erst danach Predictive Coding oder selektiver Reset gegen die deterministische Baseline.
6. **M7 Release-Zusammenführung**, nur Heinz: freigegebene Branches, Originalsignatur, In-App-Updater, Fold7-Abnahme.
7. **M9:** drei vom Besitzer angenommene, vom Agenten erzeugte und unabhängig geprüfte Verbesserungen, ohne Rechteausweitung.

## 4. Rollen

| Wer | Aufgaben |
|---|---|
| **ChatGPT-6** | Reviews; Vorregistrierungen; **fremde Holdouts schreiben und versiegeln** (EWM-Ersetzung, M8-Aufgaben, M5c-VAL); Ergebnisse gegen die Belege prüfen |
| **Codex und andere Coding-KIs** | Umsetzung mit Tests: Benchmark-Aufgaben (Kotlin, historische Fehler), CI-Sandbox-Workflow, CCC1-Anbindung des Befehls, Restfehler R2–R5 |
| **Opus** | schwierige Entwürfe und Verifikation: Isolation, Gültigkeitsmodell, Statistik, adversariale Tests |
| **Heinz** | Freigaben, Schlüssel, Geräte- und Fold7-Tests, Merges und Releases, Entscheidung zu Arm D und R7 |

## 5. Regeln (unverändert, verbindlich)
- Kein Merge nach `main` ohne Heinz, kein Force-Push, keine Änderung an anderen Repositories.
- Holdouts nur einmal öffnen; kein Tuning auf VAL oder SEALED; negative Ergebnisse bleiben negativ dokumentiert.
- Modellcode nur isoliert ausführen; keine Selbstfreigabe von Dateien oder Rechten.
- „Simuliert“ (`SIMULATED_SCRIPT`) **nie** als autonome Lösung berichten.
- Die CognitiveGenome-Invarianten, OwnerGate, ToolGate, CCC1 und EWM2 bleiben unverändert, außer mit Freigabe.
- Nach jedem fertigen Teil: Commit, Push, SHA-Readback; Status und Weiterarbeitsauftrag nachführen.
