# FABLE51 GENESIS – Gehirnintegration, CDX1-Ursache, Live-Intelligence (Handoff)

Branch: `research/continuum-fable51-genesis-20261101` (Basis: PR #333 HEAD `cc878fb9e2e8374805648a12455f6b08fbce00e0`).
Agent: Claude Fable 5.1 (High Reasoning). Dieses Dokument wird mit jedem Commit fortgeschrieben; der
Abschnitt "Commit-Log" ist die verbindliche Liste, nicht die Prosa.

## Arbeitsregeln dieses Branches
- Kein Schreiben auf `main`, kein Force-Push, keine Umbenennungen bestehender Kerndateien.
- Jede Änderung: kleiner Commit + gezielter JUnit-Test + Eintrag im Commit-Log.
- Host-Prüfung: `scripts/android-kotlin-unit-check.sh` (JDK 21, kotlinc 2.0.21, Maven-Central-Jars).
  Ergebnis ist KOTLIN_UNIT_TESTS_CHECKED, nie APK_BUILT oder DEVICE_VERIFIED.
- Paket `at.heinz.deviceai.continuumlocal`, Version 0.3.35 / Code 36, Signatur, Besitzerwissen: unverändert.

## Bestandsaufnahme (verifiziert am Code, nicht an Berichten)

### Laufzeitpfad eines Chat-Turns (AgentActivity.sendCurrentInput, ~Zeilen 2415–2850)
Eine Kette von ~20 String-Matchern entscheidet sequenziell: Erinnerungs-Lookups (`LocalMemoryAnswer`,
`LocalGroundedRecall`, `CertifiedLocationReflex`), CDX-Prompt-Test, Workspace/Coding-Routen, Web-Tool-Request,
dann `runContinuumDialogue` → `runLocal` (Gemma, `LocalFirstPolicy`-Prompt, `LocalDialogueQualityGate`,
`LocalConversationContext`). Recherche (`runResearch`) ist ein eigener Pfad, der nur bei
ALWAYS_ON + `PublicKnowledgeTopicRouter.topic()` oder bei explizitem Web-Kommando/Armierung erreicht wird.

### Festgestellte Integrationsbrüche (Stand cc878fb9)
| # | Bruch | Beleg im Code | Wirkung |
|---|-------|---------------|---------|
| B1 | Recherche-Ergebnis erreicht nie den Dialogkontext | `runResearch` ruft weder `localConversationContext.record` noch setzt es `lastLocalReply`; `runLocal` rendert nur `localConversationContext` | Folgefrage "Warum ist das so?" nach einer Recherche landet bei Gemma ohne Kenntnis der gerade gelesenen Quellen → Recherche und lokales Reasoning sind zwei getrennte Benutzerfunktionen |
| B2 | Kein Wissensbedarfs-Detektor; Routing ist modus-, nicht bedarfsgesteuert | `PublicKnowledgeTopicRouter.topic()` prüft nur Fragewort+Länge; in ASK wird nie gefragt, in ALWAYS_ON geht jede W-Frage ins Netz | "Wer ist aktuell Bundeskanzler?" in ASK → stille Offline-Antwort aus veraltetem Modellwissen; "Was ist Photosynthese?" in ALWAYS_ON → unnötige Websuche statt lokaler Gemma-Erklärung |
| B3 | Zeitliche Gültigkeit der Quellen fehlt in Prompt und Antwort | `ResearchPromptBoundary.build` enthält weder Abrufzeit noch `dateApplicability`; die Quellenliste in `runResearch` zeigt nur Titel+URL | Gemma kann Aktualität nicht einordnen; Nutzer sieht keine zeitliche Einordnung |
| B4 | Isolierte Module ohne Laufzeitaufrufer | `TemporalRoleEvidenceLedger`, `JarvisUnifiedEvidenceContract`, `ConstrainedResourceFabric`, `MemoryEcology`, `ComputeFabric`, `VirtualModelFabric` haben 0 Aufrufer in `app/src/main` außerhalb der eigenen Datei (rg-Zählung) | Matrix-Status "implementiert" ≠ im Android-Pfad verwendet |
| B5 | RIS-Live-Abnahme bleibt offen | `curl` auf `ris.bka.gv.at` liefert aus dieser Umgebung HTTP 503 mit Myra-Bot-Wall ("Security Check") | Reale RIS-Extraktion kann hier nicht abgenommen werden; der Extraktor muss diese Bot-Wall als "keine Evidenz" erkennen (Negativtest) |

### CDX1 – Ursachenstand
Dokumentiert in `experimental/brain/CDXA1-RESUME.md`, `docs/evidence/sol314-20261101/CDX1-ABLATION.md`,
`docs/evidence/jarvis-evolution-20261101/CDX1-CAUSAL-COLLISION.md`. Code: `learning/TinyContextDecoder.kt`
(Byte-LM, CONTEXT=8, EMBED=8, HIDDEN=16, CONDITION=16, 7.746 Parameter), Konditionierung über SWB1
(`TrainableSubwordEncoder`, tanh-Mittelwert über Char-n-Gramm-Hash-Buckets).
Eigene Analyse folgt im Abschnitt "CDX1-Experimente".

## Commit-Log
| Commit | Änderung | Tests |
|--------|----------|-------|
| (Basis) cc878fb9 | PR #333 HEAD | Baseline-Lauf siehe unten |

## Offene Aufgaben
- siehe Abschnitte oben; wird fortgeschrieben.
