# CONTINUUM → JARVIS 0.4.0 · Forschungs- und Integrationsmatrix

Stand 2026-11-01. Eingabe: 10-seitiger Argon-Architektur- und Forschungsplan (E1–E11, U1–U10, sechs Stufen), bezogen auf den Quellstand von PR #332 (`27bdfccd`) und die experimentellen Span-Bausteine aus PR #331. **Dieses Dokument ist eine Implementierungs- und Abnahmeakte, kein AGI-/Superintelligenznachweis.** Eingangsangaben aus dem PDF sind Ziele/Hypothesen, keine automatisch bestätigten Fähigkeiten.

## Ausführung: tatsächliche Codebeiträge

Ausgangszustand: Originalsignierte Besitzer-App 0.3.35/code36 und direktes Gemma-4-E4B-Modell 27.986 ms (eine Gerätemessung). Funktionierender Evidenz-Span-Lab-Pfad für Ylva/Bad Ischl und Anna/Perg. PR #332: Android-Host-QA erfolgreich; noch kein Besitzer-Release. Der hier dokumentierte Forschungsbranch wird **nicht** automatisch auf den signierten Publisher, `main`, produktive SWB1-Gewichte oder private EWM2-Daten übertragen.

- `CertifiedLocationReflex` + `AgentActivity`: expliziter `Beleg:\nFrage:`-Dialog erzeugt in einem eng begrenzten, vollständig verifizierten Personen-Ort-Fall eine deterministische Antwort **ohne LLM-Aufruf**. Quelle, Textoffset und Snapshot-SHA sichtbar, keine Besitzer-Faktenübernahme. Andere Fragen bleiben im bisherigen Pfad.
- `TurnScopedInternetMode`: ein während des aktiven Turns von OFF auf ALWAYS_ON geänderter Schalter hebt das ursprüngliche Veto im Modellprompt nicht auf. Bereits vorhandene ToolGate-Capabilities/Revocation werden beibehalten.
- `TemporalRoleEvidenceLedger`: bitemporale, **read-only** Prototypansicht; gültige Zeit und Beobachtungszeit getrennt, spätere Korrekturen überschreiben historische Sicht nicht; gleichzeitige Quellenkonflikte führen zu Enthaltung.
- `ConstrainedResourceFabric`: reiner Planer mit tatsächlichen RAM-/Thermalwerten; ohne Messwerte lautet der Befund `NO_MEASUREMENT`, bei OOM-/Thermal-Risiko `REFUSE_*`. Kein künstliches „virtuelles RAM“ und kein tatsächliches mmap-/KV-Cache-Paging.
- `LocalConversationPromptFirewall` und `AgentActivity`: zuvor durchgesickerte interne Forschungsschablonen dürfen nicht unbemerkt wieder in den nächsten Modellkontext gelangen. Konservativer Drop nur des flüchtigen Gesprächsverlaufs, keine dauerhafte Gedächtnislöschung.
- `runResearch`: ohne verwertbare HTTPS-Quelle keine LLM-Recherchesynthese; fehlerhafter Modelloutput wird über das bestehende Dialogqualitäts-Gate abgelehnt. UI meldet den tatsächlichen Synthesepfad statt einen möglicherweise später geänderten READY-Zustand.
- `LegalRisEvidenceProvider` + `LegalRisParagraphExtractor`: an die vorhandene HTTPS-/Wikipedia-Research-Provider-Kette angeschlossener **fail-closed** Fachquellenadapter für exakte HTTPS-RIS-ELI-/NormDokument-Paragraphen. Verlangt eine nachweisbare Paragrafenüberschrift und normativen Text; Portalseiten sind keine Belege. Synthetische Parserfixture vorhanden; **reale RIS-DOM-/HTTP-Abnahme steht aus**.
- `JarvisUnifiedEvidenceContract`: typisierte, hashgebundene Online-/Offline-Beobachtungen mit Quelle, Revision, Sichtbarkeitsfenster und Privacy-Klasse. Noch kein automatischer produktiver Persistenz- oder Toolpfad.
- `JarvisCounterfactualShadowMatrixTest`: 100 deterministisch erzeugte, synthetische Personen-Ort-Gegenfälle, bewusst kein unabhängiger Generierungsbenchmark.

## Technische Prüfgates (niemals mit Zielwerten verwechseln)

Für jeden Commit: reproduzierbare Kotlin-JVM-Testläufe, Android-Lint, unveränderter Paketname, unsigned APK/AAB, unabhängiger Architektur-Proof mit SHA-Bindung; PR bleibt Draft bis geprüft. Für einen späteren signierten Owner-Release: Originalzertifikat, monotone Version, HTTPS-Byte-Readback und echte Fold7-In-Place-Abnahme. **Keine** automatische Main-Promotion oder Signierung.

Präzise Testansprüche:
- E1/E7: exakte Quellspannen und explizite Enthaltung; auf realem Fold7 Latenz und Gerätethermik messen. Der eine direkte Gemma-Smoketest (27.986 ms) begründet keine allgemeine Tokenrate.
- E2/U6: Swaps, Duplikate, zeitliche Korrektur und widersprüchliche Quellen testen. HRR/Holographic Representations können bei endlicher Dimension kollidieren; **verlustfreie** Reversibilität wird nicht als gegeben behauptet. Das explizite Rollen-Ledger ist die sichere Basis.
- E3/U10: pro Aussagenfragment Beleg-ID, Digest, Revision und Textspan prüfen; ein Hash beweist Integrität, nicht Tatsachenwahrheit. Keine globale juristische „Wahrheitsgarantie“.
- E4/U4: erst vollständig gerenderte Modell-Ausgabe ist heute mit dem lexikalischen Qualitätsgate überprüfbar. Token-ID-Frühabbruch ist ohne nachgewiesene LiteRT-LM-Streaming-/Tokensteuerung **nicht implementiert**.
- E5/U1: zero-token **ist nicht** „speculative decoding“. Echtes spekulatives Sampling setzt Draft-/Ziel-Logits und eine Ziel-Modell-Verifikationsschnittstelle voraus; nicht nachgewiesen. Ein Prompt-Prefill allein glättet keine Wörter.
- E6/U8: `mmap`, KV-Prefix-Snapshots und `madvise` brauchen native Runtime-Unterstützung, Gerätemessungen und Lebensdauerprüfung. Kein 128-MB-/35-%-Versprechen ohne Referenzmessung.
- E8/U9: Mutation, Audit und Reparatur nur in isolierter Sandbox; CI-Tests dürfen kein automatisch vertrauenswürdiges Patch-/Release-Artefakt behaupten.
- E9/U7: maskierte Daten sind **nicht automatisch Zero Knowledge**. Offload nur mit gesonderter, überprüfbarer Owner-Erlaubnis, Endpunktidentität, Privacy-Policy und tatsächlichem Paketmitschnitt.
- E10: Lern- und Gedächtniswrites nur nach vorhandenen unabhängigen Qualitäts-/Besitzer-Gates; Shadow-Forschung speichert keine neuen Owner-Fakten.
- E11: multimodale Sensor-/Audio-/Bildpfade nur mit Berechtigung, Ressourcenmessung und konkretem lokalen Modell; kein nicht verifizierter Video-/NPU-Bypass.

## Vollständiges Experimentregister

| Code | Verfahren | Zuordnung | Reifestatus / konkrete Falsifikation |
|---|---|---|---|
| E1 | Zero-Token Reflex | `CertifiedLocationReflex` | **In Chatpfad implementiert**, nur für explizite eng geprüfte Belege; Geräte-Timing offen |
| E2 | Temporal Role-Filler | `TemporalRoleEvidenceLedger` | **Read-only-Prototyp** mit Zeiten und Konfliktverzicht; keine produktive DB-Migration |
| E3 | Unified Evidence | `JarvisUnifiedEvidenceContract` | **Typkontrakt**; EWM2- und Live-RIS-Brücken noch vollständig verbinden |
| E4 | Typed Context Firewall | `LocalConversationPromptFirewall` + Prompt-Gate | **Teilimplementiert**, offener umfassender Injection-/Streaming-Test |
| E5 | Budgeted Meta-Controller | Intent-, Turn- und Ressourcengates | **Vorhandene Bausteine**, adaptive Qualitäts-/Latenzentscheidung noch experimentell |
| E6 | Demand-Paged Model | `ConstrainedResourceFabric` | **Messwertgebundener Planer**, kein natives Paging |
| E7 | Cascaded Specialist Council | Regel/Span/Verifier/Reflex | **Enge echte Chatintegration**, genereller Rat/Verallgemeinerung offen |
| E8 | Differential Ouroboros | `.github/continuum/repair-loop.mjs` | **Bestandsfunktionen**, vollständiger autonomer Gerätezyklus offen |
| E9 | Zero-Trust Compute Bridge | `ComputeFabric`, `VirtualModelFabric` | **Forschungsarchitektur**, Device-PC-Nachweis offen |
| E10 | Counterfactual Active Learning | `JarvisCounterfactualShadowMatrixTest` | **100 synthetische Fälle**, noch kein autonomer Trainingsentscheid |
| E11 | Early-Exit Multimodal | vorhandene Sensor-/Modell-Gates | **Offen**, zuerst sichere Datenschnittstellen |

| Code | Argon-Idee | Sicherheits-/Experimentiervoraussetzung |
|---|---|---|
| U1 | Spekulatives Dual-Drafting | echtes kompatibles Target-/Draft-API und verblindeter Latenztest; kein Fake-Beschleunigungswert |
| U2 | SWB1-Surprise Gate | echte SWB1-Score-Schnittstelle, getrennte unbekannte Daten; AUROC separat messen |
| U3 | Negation Fingerprints | `NetworkTurnConstraint` + polaritätsgetrennte Minimal-Paare; niemals nur Embedding-Kosinus |
| U4 | Kryptografische Prompt-Canaries | native Token-Pipeline prüfen; derzeit robustes lexikalisches Post-Output-Gate |
| U5 | Juristischer RIS-Ast-Schnitt | `LegalRisEvidenceProvider` im Webresearch-Pfad, echter 20-Quellen-Lauf offen |
| U6 | Git-for-Facts | `TemporalRoleEvidenceLedger`, später nur additive EWM2-Migration mit Backups |
| U7 | Masked Compute Offload | kein „Zero Knowledge“ durch Pseudonyme; DLP-Tests, Owner-Consent, Peer-Zertifikate |
| U8 | Thermal KV Prefix Caching | natives KV-Restore API und PSS-/TTFT-Messung nachweisen, sonst nicht aktivieren |
| U9 | Counterfactual Shadow Arena | deterministische 100-Fälle-Testmatrix, keine Hintergrundlast ohne Thermal-Gate |
| U10 | Proof-Carrying Responses | der Reflex zeigt exakte SHA/Spans; klickbare UI-Quellinspektion noch offen |

## Sechs Entwicklungsstufen – Abnahmereihenfolge

**I – Isolation und Qualität:** alle bisherigen Besitzer-Fold7-Regressionen plus neue E1/E3/E4/U5-Gates auf unabhängigem HEAD. Netzwerkmodus-Wechsel, Prompt-Leak, beschädigte Hashes und private Daten im Web explizit provozieren. Fehlende Evidenz = Enthaltung.

**II – Spezialistenintegration:** SWB1, EWM2, Regel, trainierter Span-Ranker und Gemma über *einen* end-to-end Answer-Contract verbinden. Keine Generalisierung von 37/37 auf offene Fragen. TTFT/P95/PSS/Thermal auf Fold7 messen.

**III – Fachliche Recherche:** offizielle RIS-Passage wirklich als EvidenceSpan extrahieren und mit Fundstelle/versioniertem Inkrafttreten kombinieren. Nicht nur Treffer-URL oder Navigationsinhalt ausgeben. Tests mit konsolidierten und historischen Fassungen.

**IV – Hybrid-Compute:** identitäts- und berechtigungsgebundene Fold7-PC-Verbindung, lokal-first Fallback; keine privaten Klartextdaten ohne explizite Erlaubnis.

**V – Selbstreparatur:** FAIL→isolierter Patch→unabhängiger PASS→Rollback-Gate→explizite Owner-Freigabe; kein Auto-Release.

**VI – Langzeitforschung:** multimodale Sensorik, echtes Gegenbeispiel-Lernen, wiederverwendete Reflexskills, verifizierte externe Forschungsresultate. **AGI bleibt eine offene Forschungsfrage**, nicht ein Prozentziel.

## Forschungsquellen und Grenzen

- Yaniv Leviathan et al., ICML 2023: *Fast Inference from Transformers via Speculative Decoding* https://proceedings.mlr.press/v202/leviathan23a.html
- OWASP 2025/26, RAG Security Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/RAG_Security_Cheat_Sheet.html
- Bundeskanzleramt RIS, täglicher Normtext: https://www.ris.bka.gv.at/NormDokument.wxe?Abfrage=Bundesnormen&Gesetzesnummer=10001622&Paragraf=1295
- Smolensky (1990) / Plate (1995) role binding and holographic reduced representations (keine verlustfreie Garantiezusage).
- Im beigefügten Argon-PDF erwähnte „<2ms Reflex“, „<128MB Leerlauf-RAM“, „35% schnelleres TTFT“, „60-s Repair Loop“ und AGI-Prozentzahlen sind **Hypothesen beziehungsweise Zielwerte**, keine aktuelle Messung.

## Trennung von Test und Freigabe

`PASS` ist nur der vom unabhängigen Renderer tatsächlich geprüfte SHA. `NOT_RUN` bleibt `NOT_RUN`. Selbst ein grüner Host-Build ist weder nachgewiesene lokale Modellqualität noch Besitzer-APK, Installationsbestätigung oder allgemeine Intelligenz. Neue Speicher-, Modell- und Besitzerfunktionen erfordern ihre eigenen Gate-Protokolle.
