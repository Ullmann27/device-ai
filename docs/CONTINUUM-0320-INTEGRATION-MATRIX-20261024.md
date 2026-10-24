# Integrationsmatrix · 24.10.2026

Vergleichsziel PR115 `9794761067e6bd1f8f2188426288f8134de53e4e`; signierte 0.3.19 `afd8ea3f0ff6e73c646b71b60d1157977abbcd62`. Alle HEADs direkt von GitHub gelesen und per Fetch geprüft. Die vollständigen 40-stelligen IDs, gemeinsame Vorfahren, Textbelege aus den PRs und Dateivergleiche stehen in [pr-metadata.json](evidence/sol-0320-20261024/pr-metadata.json). Kein alter PR wurde blind gemergt.

| PR | HEAD | Gemeinsamer Vorfahr mit115 | HEAD-Vorfahr? | Identische/geänderte/fehlende Quellen |
|---|---|---|---|---|
| #75 | `6919264d91aa` | `66e6b0d81a13` | nein | 590/66/0 |
| #82 | `14702b0da94f` | `66e6b0d81a13` | nein | 599/71/0 |
| #84 | `f23934e2b38c` | `f23934e2b38c` | ja | 639/71/0 |
| #92 | `efcf4e3a5bbf` | `9fb4361c8963` | nein | 679/68/0 |
| #93 | `adee374c59cb` | `9fb4361c8963` | nein | 651/67/0 |
| #95 | `83f01067155a` | `83f01067155a` | ja | 730/65/0 |
| #99 | `8f88cc445b22` | `b35fed417996` | nein | 787/47/0 |
| #108 | `e4fd67c3e833` | `e4fd67c3e833` | ja | 830/33/0 |
| #109 | `144ab574b4c0` | `144ab574b4c0` | ja | 861/25/0 |
| #110 | `f382aeda2612` | `f382aeda2612` | ja | 861/25/0 |
| #111 | `ec4fa6db4e57` | `ec4fa6db4e57` | ja | 865/24/0 |
| #112 | `f65181fe8724` | `f65181fe8724` | ja | 875/17/0 |
| #113 | `5be8b5c9521e` | `5be8b5c9521e` | ja | 887/8/0 |
| #114 | `5859ec5dc72d` | `5859ec5dc72d` | ja | 890/6/0 |
| #115 | `9794761067e6` | `9794761067e6` | ja | 901/0/0 |

Dateibereich: `app/src`, `scripts`, `.github/continuum`, `backend/src`, `backend/test`. Keine fehlenden Quellpfade aus75–114; auch die nicht in der Abstammung liegenden Zweige sind dateiweise vertreten. Abweichungen liegen überwiegend in inzwischen reparierten Core-/Lifecycle-/Scheduler-Dateien. Ein vorhandener Pfad ist kein Funktionsbeweis; der folgende Anschlussstatus und die echten Tests entscheiden. Die fünf gegenüber0.3.19 zusätzlichen PR115-Pfade sind zwei produktiv verpackte Forschungsquellen, Curriculum-Test, statischer Dashboard-Test und Inventurskript.

## Übernahmeentscheidung

- #75/#82: LM1-/NF-Quellkern und Tests bereits vorhanden. LM1-Runtime/Tokenizer/Checkpoint-Funktionen geprüft; keine produktive Aktivierung bei dokumentiert schwacher Wortvorhersage, Wiederholungen und fehlender Anweisungsqualität. Keine neuen Checkpoints oder Architekturversuche in dieser Runde.
- #84/#92/#99: existierende Reasoner-, Codeindex-, Worker-, Lease- und CCC1-Kette erhalten. Kein paralleler Worker oder zweiter Reparaturkanal.
- #93: GGUF-/Kontextskripte bereits vorhanden; sie bleiben ein eigenständiger Node-/Worker-Pfad und werden nicht als Android-GGUF-Import bezeichnet.
- #95: Verifier-/Provenienz-/CDX1-Grenzen erhalten.
- #108–112: M4/M5/Termux/CCC1 samt Lifecycle-, Freigabe- und Abbruchreparaturen bereits in der Abstammung. Wieder durch Node-/SDK-Gesamtsuite geprüft; aktuelle physische Android→Termux→APK-Abnahme bleibt offen.
- #113/#114: neue Hologramm-Sichtbarkeit, Aktivitätsübergaben, Frameuhr und Sitzungs-Promptgrenzen bereits enthalten; beibehalten und mit echten zeitgesteuerten Canvas-Bildern zusätzlich geprüft. #114 ist gemergt; APK0.3.19 tatsächlich heruntergeladen und verifiziert.
- #115: automatisches Dialogcurriculum, Scheduler-/Opt-in-Anschluss und Archivzähler übernommen, kompiliert und erstmals in dieser Runde wirklich ausgeführt/geprüft. Cursorfehler repariert. Kein automatischer Fakten- oder Chatmodell-Promotionpfad.

## Aktueller Modulstatus

Maschinenlesbarer Nachweis: [ModuleConnectionManifest.json](evidence/sol-0320-20261024/ModuleConnectionManifest.json). `TESTED` bezieht sich auf die benannten Hosttests; es bedeutet keine allgemeine Sprachkompetenz oder Gerätefreigabe. `DEVICE_VERIFIED`/`RELEASED` sind für den neuen gemeinsamen Stand überall falsch. Frühere APK-Funktionen und Besitzerberichte werden getrennt angegeben.

| Baustein | Anschluss im aktuellen Code | Nachweis / Grenze |
|---|---|---|
| SWB1/Gedächtnis/CRE1-Beobachter | Hauptchat → CognitiveDecisionBridge → CognitiveRuntime → gesperrter Core → ID-gebundene Historie/aktuelle Belege | JUnit/Provenienz-/Widerrufs-/Reranking-/Kontexttests; Gewichte unverändert |
| LiteRT-LM/Reasoner | Unbeantwortete Frage → bestehendes LocalFirstPolicy.afterMemory → runLocal/ChatReasoningRouter | Runtime-/Adapter-/Tool-/Identitäts-/Abbruchtests; kein echtes Modell in dieser Sitzung geladen |
| CognitiveDecisionBridge mit null | Belegantwort bleibt deterministisch; nachgelagertes Modellrouting bleibt erreichbar | Bewusst erhalten: bloße lexikalische Anker beweisen keine erlaubte Paraphrase; kein zusätzlicher konkurrierender Modellaufruf |
| LM1 | Lokale Inferenz-API/Research, nicht Hauptchat | LmRuntimeTest5 und LmCoreTest; unabhängige ältere Qualitätskriterien verfehlt; keine Gewichtsänderung |
| TinyNN/NeuralDialogue | Vorhandene Neuron-Befehle/Labor und PracticeScheduler | Unit-/Scheduler-Tests; keine allgemeine generative Sprach-KI |
| CRE1-Autodialoge | Lernen/Experimentell → Opt-in → bestehender Offlinejob → Lab → vorhandenes CRE1-Kandidatengate |32 echte synthetische Schritte mit strikter Train-/Auditverbesserung; nur Forschung, keine produktive Übernahme |
| CDX1 | Vorhandene isolierte Decoder-/Curriculum-/Offline-Jobpfade | Tests/Parität, kein neues produktives Training oder Chat-Routing |
| Wissensarchiv/Journal | Archivinhalt → Dashboard-Zähler/30s-Cache; Titelabfrage → gekennzeichneter Quellenbeleg | Echte Collector-/Archivtests; Journal und bestätigte Besitzerfakten bleiben getrennt. Semantische Archivsuche nicht neu integriert |
| M5/M4/CCC1/Termux | Gespräch/Plan/Bestätigung → bestehender Builder/Job-/Receipt-/Repair-Vertrag | SDK-/Node-/Lifecycle-Regressionen; kein neuer on-device BUILD in dieser Sitzung |
| CTX1/2/3, I1/I2/NF | Forschung/Test/CTX3 OFF oder SHADOW | Keine Produktpromotion, keine produktiven Gewichte verändert |
| Hologramm | Activity-Vordergrund + echte Sichtbarkeit + ein Framecallback → gemeinsamer Canvasrenderer | 3 zeitlich getrennte native Canvasbilder je Formfaktor; Pausieren/Resume; physisches Fold7 weiterhin ungeprüft |
