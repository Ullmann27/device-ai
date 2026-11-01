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

## CDX1-Experimente
Ergebnis in `docs/evidence/fable51-genesis-20261101/CDX1-ORACLE-BOUND.md` (+ JSON):
- SWB1-Bag-Konditionierung kollabiert auf **eine** Ausgabe für alle Eingaben (0/35 Tausch-Paare),
  Oracle-Konditionierung desselben Decoders erreicht 47/47 → Architekturproblem der Kopplung, nicht Budget.
- Selbst Oracle-Konditionierung generiert **0/29** ungesehene Orte → ohne Kopierpfad unerreichbares 16/20-Ziel.
- Negativbefund: Regelkern enthielt sich bei koordinierten Satzteilen („Anna ist in Perg, Emma ist in Berg.“);
  behoben durch Satzteil-Grenzen an Komma/„und“ (nur vor vollständiger neuer Aussage), Verifier unabhängig angepasst.
- Keine Checkpoints verändert; kein Fold7-Lauf.

## Commit-Log
| Commit | Änderung | Tests |
|--------|----------|-------|
| (Basis) cc878fb9 | PR #333 HEAD | Baseline-Lauf siehe unten |
| 723adcdd | Handoff-Dokument (Bestandsaufnahme, Brüche B1–B5) | – (nur Doku) |
| 59e26bcc | B1+B3: `ResearchDialogueHandoff`, Recherche-Digest im Dialogkontext, Abrufdatum/TODAY im Prompt, Canary-Frames im Qualitätsgate | `android-kotlin-unit-check.sh` OK |
| 24445118 | B2: `FreshKnowledgeNeed` (CURRENT_FACT/EXPLANATION/STATIC), ASK fragt bei Gegenwartsfragen, ALWAYS_ON lässt Erklärungen lokal | `android-kotlin-unit-check.sh` OK (1492 Tests) |
| f617a6d3 | CDX1 Oracle-Bound-Experiment + Regelkern-Koordination (Komma/„und“) | `android-kotlin-unit-check.sh` OK (1493 Tests, 275 Klassen) |
| dd68d5c7 | B5: Negativtest – reale Myra-Bot-Wall-Seite (HTTP 503, `app/src/test/resources/research/ris-myra-botwall-503.html`) liefert keine RIS-Evidenz; Provider gibt leere Dokument-/Zitatliste zurück | `LegalRisEvidenceProviderTest` 3/3 grün |
| 1269b9c5 | Argon E7 (Platz 1): Sitzungs-Spezialist `CertifiedLocationReflex.answerFromSession` – „Wo ist X?“ wird vor Gedächtnis/Gemma/Web aus den eigenen früheren Nachrichten der Sitzung per Regelkern+Verifier beantwortet (nur eindeutiger Treffer, 0 Tokens); `LocalConversationContext.priorUserTurns`; Shadow-Adapter-Satzteilgrenzen angeglichen | `android-kotlin-unit-check.sh` OK (1495 Tests) |
| 7700313f | Doku: Alt/Neu, Nicht-Erreichtes, Meilensteine | – |
| b5202ade | RIS-Abschluss: offizieller Open-Data-Host `ogd.ris.bka.gv.at` (via `data.bka.gv.at/ris/api/v2.6`) liefert echte Normtexte ohne Bot-Wall; zwei reale Fixtures (§ 1151 in Kraft, § 678 außer Kraft). Behoben: `§\u00A0N` (NBSP) scheiterte am Extraktor → mit echtem RIS-Text 0 Treffer; Screenreader-Duplikate im Fetcher entfernt; Paragraphenbindung über Metadaten; Gültigkeitsvermerk (in Kraft/AUSSER KRAFT) im Evidenztext | `android-kotlin-unit-check.sh` OK (1498 Tests) |
| f712546f | E7 + EWM2: `CertifiedLocationReflex.answerFromConfirmedFacts` in `ContinuumMemoryDialogue.replyInConversation` – Ortsfrage wird aus bestätigten Besitzerfakten exakt beantwortet (Regelkern+Verifier über ALLE aktiven bestätigten Fakten zur Person, Konflikt/Widerruf/Negation/Zeit → Enthaltung, >4 Fakten → Enthaltung), Herkunft = Erinnerungs-ID; Trefferliste bleibt erhalten | `android-kotlin-unit-check.sh` OK (1502 Tests) |

| ac6530e6 | M2 Aktualitätsprüfung: `FreshKnowledgeNeed.horizonDays/freshness` (FRESH/POSSIBLY_STALE/SUPERSEDED/UNKNOWN_AGE; Horizonte 1/7/30/365 Tage nach Fragetyp), `ResearchDialogueHandoff.render` mit „Aktualität:“-Zeile, `LocalConversationContext.researchFreshness`, AgentActivity: veralteter Recherchestand löst in ASK nur den bestehenden Freigabedialog aus, sonst sichtbare Kennzeichnung | `android-kotlin-unit-check.sh` OK (1506 Tests) |
| 5a528e29 | M3 RIS-Discovery: `LegalRisDiscoveryProvider` über offizielle API `data.bka.gv.at/ris/api/v2.6/Bundesrecht` (BrKons, Suchworte) → OGD-Dokument-URLs; Buchstaben-Paragraphen (364a), geltende + außer Kraft getretene Fassungen im Titel; Fallback auf allgemeine Websuche mit sichtbarem `lastIssue`; in `AgentActivity.runResearch` verdrahtet. B4: `JarvisUnifiedEvidenceContract` erstmals im Laufzeitpfad (`ResearchDialogueHandoff.unifiedEvidence`, Ablauf = Aktualitätshorizont) | `android-kotlin-unit-check.sh` OK (1511 Tests, 277 Klassen) |

## Alt vs. Neu (nur host-verifiziert, kein Fold7-Lauf)
| Fähigkeit | Vorher (cc878fb9) | Nachher (dieser Branch) |
|-----------|-------------------|-------------------------|
| Folgefrage nach Webrecherche | Gemma kennt die gerade gelesenen Quellen nicht | Recherche-Digest (Frage, Kernaussagen, Quellen mit Abrufdatum) liegt als getrennt gerahmter, als ungeprüft markierter Block im Dialogkontext; Canary-Frames im Qualitätsgate |
| Zeitliche Einordnung | weder TODAY noch Abrufdatum im Prompt; Zitate ohne Datum | TODAY + `retrieved=` je Quelle im Prompt; Zitatzeilen mit Abrufdatum |
| Routing ASK/ALWAYS_ON | modusgesteuert: ASK fragt nie, ALWAYS_ON schickt jede W-Frage ins Netz | bedarfsgesteuert: Gegenwartsfragen in ASK lösen eine Freigabe-Rückfrage aus (Ablehnung → lokale Antwort ohne aktuelle Quellen); Erklärungsfragen bleiben auch in ALWAYS_ON lokal; Offline-Verbote unverändert |
| Belegte Ortsfrage mit koordinierten Satzteilen („Anna ist in Perg, Emma ist in Berg.“) | Regelkern enthält sich (sicher, aber stumm) | Regelkern, Verifier und Shadow-Adapter binden jede Person an ihren Satzteil; Negation/Zeit/„in Graz und Wien“ enthalten sich weiterhin |
| „Wo ist Mama?“ nach „Mama ist in Graz.“ in derselben Sitzung | Gedächtnis → Gemma (≈28 s auf Fold7 laut Argon-Messung) oder in ALWAYS_ON sogar Webroute | Sitzungs-Spezialist antwortet deterministisch aus der eigenen Nachricht (<50 ms Host), eindeutig als Sitzungsbeleg gekennzeichnet, keine Modell-/Webanfrage |
| RIS-Bot-Wall | nur HTTP-Statusprüfung im Fetcher | zusätzlich Negativtest: Challenge-Seite liefert nie Paragraphentext |
| CDX1 | „Nullleistung“ ohne Ursachennachweis | Ursache experimentell belegt (Kopplungskollaps + fehlender Kopierpfad), Checkpoints unverändert |

## Nicht erreicht / bewusst nicht behauptet
- Kein Fold7-Release, kein APK-Build, kein Lint, kein Gerätelauf; alle Testaussagen sind Host-JUnit (`KOTLIN_UNIT_TESTS_CHECKED`).
- CDX1 erreicht das 16/20-Ziel in freier Generierung weiterhin nicht; der Erfolg des Regelkerns gilt ausdrücklich nicht als Reparatur von CDX1.
- B4: `JarvisUnifiedEvidenceContract` hat seit 5a528e29 einen Laufzeitaufrufer (Recherche-Digest → typisierte PUBLIC_WEB-Evidenz mit Ablauf). `TemporalRoleEvidenceLedger` und `ConstrainedResourceFabric` haben weiterhin keinen Laufzeitaufrufer (`rg` am 01.11.2026: 0 Aufrufer außerhalb der Definition); `MemoryEcology`, `ComputeFabric`, `VirtualModelFabric` existieren im `app/`-Quellbaum nicht als Klassen.
- CDX2 (M4) wurde nicht begonnen: Budgetgrenze erreicht; Design/Hypothese in `FABLE51-GENESIS-CONTINUATION.md` Abschnitt E.
- Live-RIS über das Portal `www.ris.bka.gv.at` weiterhin durch Bot-Wall blockiert; API und OGD-Host sind erreichbar (Host-Test mit echten Antworten vom 01.11.2026, nicht Fold7). Offen: Judikatur-Dokumente (Applikation Justiz), Artikel-Normen („Art 7 B-VG“), Landesrecht, mehrseitige API-Ergebnisse (>10 Treffer werden nicht nachgeladen).
- Argon U5 (RIS-DOM-Parser), U1 (spekulatives Drafting), U2 (SWB1-Surprise), U3 (Negationsachse), U6 (bitemporale Fakten) nicht begonnen.

## Nächste priorisierte Meilensteine
(Detailliert mit Dateien/Methoden/Abnahmekriterien in `FABLE51-GENESIS-CONTINUATION.md`; Einstieg über `CONTINUUM-NEXT-AGENT-ENTRYPOINT.md`.)
1. Fold7-Abnahme dieses Branches: Dialog nach Recherche, ASK-Rückfrage bei Gegenwartsfrage, Sitzungs-Spezialist, Offline-Verbot; Paket `at.heinz.deviceai.continuumlocal` 0.3.35/36 unverändert.
2. E7-Ausbau auf bestätigte EWM2-Erinnerungen über den bestehenden `grounded`-Recall-Pfad (kein neuer Speicher), mit Herkunftskennzeichnung und Revisionsprüfung.
3. CDX2-Kandidat isoliert: Kopier-/Pointer-Pfad über Belegbytes + rollenbindender Konditionierer; Blind-C-Korpus neu erzeugen (Blind-A/B sind konsumiert).
4. RIS-Discovery über die offizielle API (`data.bka.gv.at/ris/api/v2.6/Bundesrecht`, Suchworte → OGD-Dokument-URL) im bestehenden Discovery-Pfad; Paragraphen mit Buchstabenzusatz.
5. B4-Module entweder an einen echten Aufrufer binden oder als Forschungsartefakte markieren.
