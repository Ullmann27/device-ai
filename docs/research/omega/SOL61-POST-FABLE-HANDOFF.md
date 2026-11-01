# Sol6.1 nach Fable — Abschlussübergabe und nächster Einstieg

**Offizieller Branch:** `research/continuum-sol61-post-fable-20261101`, Draft-PR[#335](https://github.com/Ullmann27/device-ai/pull/335) gegen Fables Branch. **Letzter funktionaler App-Code:** `6e1825b09ffca1c811414f9eeb7790607ef4c448`. Danach QA-Shellreparatur `6906220e` und abschließende Unterlagen. Tatsächlichen finalen Remote-HEAD im PR und mit `git ls-remote origin refs/heads/research/continuum-sol61-post-fable-20261101` prüfen. Kein Main-Merge, keine Signierung/Installation, keine Versions-/Publisher-/Besitzerdaten-/Gewichtsänderung. Dateien normal editierbar, alle fertigen Änderungen im selben offiziellen Repository gepusht.

**PDF zum Weitergeben:** [SOL61-GPT6-FABLE-CONTINUATION.pdf](SOL61-GPT6-FABLE-CONTINUATION.pdf), unverschlüsselt; bearbeitbare Markdownquelle bleibt erhalten.

**Direkter Weiterarbeitsauftrag:** [SOL61-GPT6-NEXT-AGENT-INSTRUCTIONS.md](SOL61-GPT6-NEXT-AGENT-INSTRUCTIONS.md), für GPT-6 im Chat-/Codex-Modus oder Fable5.1. Vor einer erneuten Gesamtanalyse dort beim ersten konkreten Coding-Schritt einsteigen. [Historische Zwischenstände](SOL61-POST-FABLE-CHECKPOINT-HISTORY.md) erhalten die negativen Befunde; dortige OPEN-Markierungen sind keine Aussage über den aktuellen Stand.

## Abstammung und Zusammenführung

Fresh fetch und GitHub-PR-Abgleich327–335: Fable-Remote weiterhin **702b57ae5c2724a819048bcc3b7775272b48f2b3**. FableCode5a528e29 und vollständige Übergabe702b57ae wurden unterschieden. Alle Fable-Commits sind Vorfahren dieses Branches. PR327/328/329/330/332/333 sind ebenfalls vollständig geerbt, einschließlich Offline-Hardlock, OUROBOROS/EWM2, Releasevertrag36, echter Schema3Proof, Termux, natürliche Evidenzspannen, lokale Dialog-/Recherche-Trennung, Rollen und Fables Recherche-/RIS-/Gedächtnisverbesserungen.

PR331: produktiver Quellstand bis8417f08b ist geerbt; sein späterer Einzelcommitcc9a56b1 ergänzte nur CONTINUUM-V36-OWNER-UPDATE-HANDOFF.md. Genau diese Datei wurde bytegleich übernommen, ohne alte Quellen über neue332/334-Fixes zu schreiben. Der neu sichtbare Remote `research/continuum-live-dialog-routing-20261101` ist PR332/27bdfccd, bereits Vorfahre; keine neue offene Quellen-Divergenz. Kein ungeprüftes Zusammenmergen notwendig. Ältere isolierte Forschungsartefakte werden dadurch nicht zu produktiven Funktionen.

Belege: [finale DAG-Prüfung](../../evidence/sol61-post-fable-20261101/integration/final-dag-verification.json), [frische PR-HEADs](../../evidence/sol61-post-fable-20261101/integration/final-github-snapshot.json), [ausführliche Matrix](../../evidence/sol61-post-fable-20261101/integration/integration-matrix.json) und49tatsächliche Dateipatches in integration/. Die initiale Matrix war ein14:37Snapshot; restauriertes331Dokument und danach geschlossene S1/S3Befunde stehen in den FINALFIX-Belegen.

## Neue Commits

|Commit|Konkrete Änderung|
|---|---|
| [12152f94](https://github.com/Ullmann27/device-ai/commit/12152f94f9e4e6fdc614271bda7cb9aa64a34eb9) | fix(evidence): bind saved web context to current offline and consent state |
| [94f4a77c](https://github.com/Ullmann27/device-ai/commit/94f4a77cb3acae62d87359888a15027705410d7d) | fix(consent): reject stale native turns and preserve explicit ASK preferences |
| [849c19f3](https://github.com/Ullmann27/device-ai/commit/849c19f3d1eec24e7e9ebe5b3a880d190ee7927b) | fix(tools): bind web approval and dispatch to original owner veto and consent epoch |
| [fc9560bd](https://github.com/Ullmann27/device-ai/commit/fc9560bd032a268379cb3e8960afdd2c3632959b) | test(android): follow actual consent restoration path in release contract |
| [6e1825b0](https://github.com/Ullmann27/device-ai/commit/6e1825b09ffca1c811414f9eeb7790607ef4c448) | feat(memory): answer confirmed ages through existing scoped ledger evidence |
| [6906220e](https://github.com/Ullmann27/device-ai/commit/6906220ec86cd635a9a67181c9363361034d29ea) | fix(qa): avoid pipefail false negatives in host dependency classification |

## Erreichte neue Fähigkeiten und Runtime-Anschluss

|Komponente|Tatsächlicher Android-/Kernaufruf|Änderung und Grenze|Prüfung|
|---|---|---|---|
|B4-Quellenvertrag|AgentActivity.runLocal → LocalConversationContext.render → ResearchDialogueHandoff → JarvisUnifiedEvidenceContract.useForAnswer|Aktueller OFF-/ASK-Zustand und Besitzerverbot werden übergeben. Historischer Quellenkontext bleibt markiert/untrusted, erzeugt keine Netzwerk-/Owner-Autorität.|OFF/ASK/ALWAYS, grant/revoke/expiry/unknown-time/firewall/session regressions|
|Nativer Toolloop|AgentActivity → TurnScopedInternetMode → generateLocal/respondToTools/retry/final UI|Consent-Epoche vor/nach Inferenz und vor Veröffentlichung; frisch gerenderter Prompt wird bei alter nativer Konversation nicht als zugestellt angenommen.|Abbruch bei Widerruf und OFF→ALWAYS, auch ohne Websession|
|Web-Ausführung|Originaltext → approvalProvider/createToolGate → NetworkTurnConstraint.withOriginalTurn → bestehender ToolRunner/ApprovedSession|Modellumformulierung kann ursprüngliches Offline-Veto nicht löschen; Session-Capture prüft alte Epoche vor/nach Ticketübernahme.|Echte Policy/Dispatcher/Session-Tests, kein zusätzlicher HTTP-/Berechtigungsspeicher|
|Gespeicherter Internetmodus|AgentActivity SharedPreferences mode → InternetConsentPolicy.restoredMode|Explizites ASK wird nicht mehr beim Öffnen in ALWAYS_ON umgewandelt. OFF/ASK/ALWAYS bleiben erhalten; korrupt→OFF; vorhandener frischer Standard unverändert.|JVM-Datei-Roundtrip+neue Policy/Gate,31gezielte Consent-Tests; tatsächliches SharedPrefs-Wiring strukturell, Device-Neustart NOT_RUN|
|E7/EWM2-Altersangabe|AgentActivity bestehender Dialog → ContinuumLearningCore.replyFromMemory → ContinuumMemoryDialogue → OwnerAttributeEvidenceScope|Zusätzlich zum Ortsreflex exakte bestätigte Altersangabe mit Erinnerungs-ID/Originalzitat; vorhandener vollständiger Ledger-/Revisionsscope, keine neue DB. Alter gilt zum Zeitpunkt der Angabe, keine Hochrechnung.|8neue Altersregressionen,16gezielte Alters/Familien/OrtsTests; negative/positive Rohdaten|
|Familien-Invalidatoren|OwnerAttributeEvidenceScope.select → bestehende Chronologie/Negation/Revocation|Neuere Familien-Negation oder Angabe mit Jahre-alt bleibt demselben Namen zugeordnet; alter29Wert kann nicht aufgrund verlorenen Holders gewinnen.|Reviewer-Fund reproduziertRED2/2, gezieltGREEN, gleichzeitiger Konflikt/Widerruf/Identitäts-/Quellenisolation|
|Host-Gate|scripts/android-kotlin-compile-check.sh → vollständiger vorhandener Unit-Runner|Pipefail/SIGPIPE konnte einen bekannten Importpfad fälschlich als fremden Fehler markieren; direkter grep-Input behebt ausschließlich Klassifikation. Keine Quelle ausgeschlossen.|Deterministische echte Condition:REDexit1/GREENexit0; erster/letzter Treffer+Nichttreffer im Node-Test, in vorhandene Render-Pipeline aufgenommen|

Alle bestätigten Aussagen stammen weiter aus bestehenden OWNER_CONFIRMED/USER_CONFIRMED-Ledgereinträgen. Neue Antwortpfade schreiben weder Fakten noch Gewichte. Bestehende Dialog-History darf weiterhin gespeichert werden. E7-Sitzungsreflex/Ortsregel/Verifier bleiben erhalten; kein CDX1-Generator freigeschaltet.

## Tatsächliche Tests und Fehlschläge

- Baseline702:1511Host-JUnit dokumentiert, kein Androidbeleg.
- 12152:1514Host-JUnit/277Klassen+4Checks PASS; direkter Fable-Baseline-Offline-TestRED1/GREEN1. Erster unabhängiger kompletter RenderPASS.
- 94f4:1518Host-JUnit PASS. 849:finaler unveränderter Host1520/277+4PASS; zusätzlich20Policy/wrapperTests und35portable spanTestsPASS. Diagnose eines falschen Kotlin-Teilmodulnamens führte zu4NoSuchMethodError; Originalfehler erhalten, mit `-module-name app` behoben.
- Render849 **FAIL** vor Gradle: veralteter struktureller Node-Test erwartete inline `if(stored==OFF)`. Reproduziert3/4; minimal auf tatsächlichen restoredMode-Aufrufer und explizite OFF/ASK/ALWAYS/corrupt-Zuordnungen geändert, keine Sicherheitsanforderung gestrichen.
- **Renderfc9560bd PASS**, dep-dbjlevflk1mc739jqvo0/live01.11.2026 15:23:15UTC: vollständige echte LiteRT0.18.0-Kompilierung, Gradle-JUnit/lint,115Node+64Backend, unsignedAPK+AAB und Binäridentität. Kein Google-/LiteRT-Dependency-Patch oder Quelldatei-Auslassen nötig. Vollständige geprüfte Logs/Schema3Receipt unter s2/fc9560bd-render.
- S3 initialRED2/6(keine exakte Alterszeile), zusätzlicher Familien-InvalidatorRED2/2 vor Fix, danach16gezielte GedächtnistestsPASS. Tatsächlicher vollständiger Zwischenhost1527/278+4PASS vor Familien-Head-Fix, **keine Abnahme der späteren Reparatur**.
- Erster finaler Hostversuch auf6e **FAIL** im vorhandenen no-LiteRT-Vorcheck: bekannter LiteRT-Pfad irrtümlich klassifiziert. Deterministischer Pipefail-Gegenversuch reproduziert; korrigiert in6906220e, ursprünglicher Fehlbericht erhalten.
- **Finaler vollständiger Host auf unveränderten App-/Test-/Harness-Dateien von6906220e:1529JUnit/278Klassen+4Checks PASS.** Alle292Android-Kotlin-Dateien im bestehenden Hostpfad, vorhandener compile-only LiteRTStub; kein realer APK-/Devicebeleg. Manifest mit Quell-/Loghashes und vollständige Ausgaben unter final-host/. Checks enthalten ausdrücklich erwartete negative synthetische Modellfälle; kein Blindtest/Generatorerfolg daraus abgeleitet.
- **Final lokal116QA-Node-Tests PASS**, einschließlich zusätzlichem Klassifikations-Regressionsfall; Backend64Tests PASS und npm run check PASS. Vollständige Node-Liste und Rohresultate in final-host/. Der letzte vollständige Renderlauf muss alle116QA-Node+64Backend ebenfalls enthalten.

**Abschließender Render auf dem letzten gepushten Dokumentations-HEAD:** Der exakte Deploy/Commit und sein ausgelesenes Ergebnis werden im Abschlusskommentar von PR335 veröffentlicht. Ein vorangehendes PASS in dieser Datei überträgt sich nicht auf einen anderen SHA. Prüfe https://continuum-sol61-post-fable-qa-20261101.onrender.com/STATUS.txt und ARCHITECTURE-PROOF.json gegen den tatsächlichen PR-HEAD; spätere Deploys können dieselbe URL ersetzen, deshalb bleibt der PR-Kommentar mit SHA/Hashes maßgeblich. Bei FAIL bleibt FAIL sichtbar, keine automatische Merge-/Releasefreigabe.

Reproduktion: [ausführbarer Folgeauftrag](SOL61-GPT6-NEXT-AGENT-INSTRUCTIONS.md), vorhandene scripts/android-kotlin-unit-check.sh und scripts/render-continuum-m8-qa.sh. JVM verwendet compile-only LiteRT-/Android-Stubs; das ist klar von realer Gradle-/APK-Abnahme getrennt. Tests und Logs unter docs/evidence/sol61-post-fable-20261101/.

## Meilensteinmatrix und konkrete nächste Arbeiten

|Meilenstein|Abschlussstatus|Noch erforderlich|
|---|---|---|
|S1 B4 OFF/ASK/Veto|SOURCE_INTEGRATED; AndroidPASS auffc; zusätzliche Persistenz-Gegenprobe im finalen Gate|Reales Fold7 OFF/ASK-Restart-/Widerrufs-E2E|
|S2 Android-Gesamtheit|DAG/echte LiteRT/Gradle/JUnit/lint/unsignedAPK+AAB bestätigt auffc; abschließender HEAD separat im PR|Finalen SHA prüfen, anschließend Gerätetest/monotone Originalsignaturfreigabe|
|S3 E7/EWM2 Ausbau|SOURCE_INTEGRATED eng begrenzte Altersangabe, Konflikt/Negation/Widerruf/Identität geprüft|Allgemeine Beziehungen/zeitliche Semantik weiterhin nicht gelöst; nur nächste enge Relation aus echtem Belegvertrag ergänzen|
|S4 kontrolliertes Lernen|NOT_IMPLEMENTED in dieser Runde; vorhandene SWB/CRE-Gates unverändert|TraceFeedback ist Bewertung, ResearchFeedback isoliert; echte Erfolgs-/Owner-Prüfung vor Rückkopplung anbinden, keine selbstbestätigten Modelltexte|
|S6 RIS|READ_ONLY_PREFLIGHT_ONLY; offizielle strukturierte Antworten/Parameter gesichert|Artikel-Parser und begrenzte Paginierung zuerst; current-version/Artikel/Dokumenttyp präzise testen, Judikatur erst nach eigenem offiziellen Vertrag|
|S5 CDX2|NOT_IMPLEMENTED; Blind-C NOT_PREPARED, keine neuen Modellresultate|Unabhängigen neuen Korpus vor Kandidatenfreeze, positionsgebundener Copy/Pointer; alteA/B/Oracle nicht neu als blind ausgeben|
|S7 weitere B4|RESEARCH_ARTIFACT: TemporalRoleEvidenceLedger/ConstrainedResourceFabric ohne bestätigten Caller|Valid-time/Rollen- und reale Messdaten nötig; keine fiktiven Fabric-Klassen oder RAM-Erweiterung|
|S8 lokaler Worker|NOT_EXTENDED; bestehende Repair/Publish/TypedBridge/Watchdogs erhalten|Echter begrenzter Repair→Build→Test→Proof-Durchlauf und Geräteanbindung; kein neuer Executor oder Cloud-API-Zwang|
|Avatar/Sprachsync|DEFERRED nach Geräteabnahme|Keine Kosmetik an Stelle echter Gehirn-/Android-Abnahme|

## CDX1-Forschung und erhaltene Daten

FableOracleBound unverändert: SWB-Bag-Kollisionen verlieren Rolleninformation; Oracle kennt Goldantwort, ist kein nützliches Modell. Bekannte Spannen47/47 im synthetischen Versuch, zuvor ungesehene Orte0/29frei. Der alte Generator ist nicht repariert und bleibt unpromotiert. Sols frühere27Varianten mit0/20, Blind-A/Blind-B, Checkpoints und öffentliche csxAssets unverändert. Dieser Lauf hat keine zusätzlichen CDX1-Trainingsserien oder neuen Blindmessungen außerhalb der bestehenden Testsuites ausgeführt. Reviewer bestätigt: Blind-C nicht vorbereitet, keine verborgene Datei/Seed/Hash. Nächster Versuch braucht Rollen+Positionen und echte Copy-Ausgabe, RAW/Verifier/Regel-Baselines getrennt.

## Geräte-, Sicherheits- und Releasegrenzen

**DEVICE_NOT_RUN** für alle folgenden Punkte. Kein angeschlossenes Fold7/adb. Unsigned research build0.3.35/code36, Paket at.heinz.deviceai.continuumlocal. Originaler Publisher wurde nur gelesen: code36aus8417f08b ist ein anderer Quellstand; historische331Handoff-Beobachtungcode35 ist überholt. Kein erneuter Signatur-/Installationsbeleg behauptet. Originalzertifikat SHA256 `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23` unverändert. Vor nächstem Ownerupdate reale Binärdaten/Hash/Version/Zertifikat/aktuellen installierten Code prüfen und bestehenden Originalprozess+Ownerfreigabe verwenden; nicht stillschweigend mit derselben36überschreiben.

Geräte-Abnahmematrix: (1) echte Upgradeidentität+Backup/Rollback; (2) OFF nach Neustart null HTTP; (3) ASK ohne/mit/Ablehnung der Einzelfreigabe; (4) ALWAYS_ON-Modus speichern/neustarten, dann OFF; (5) Widerruf während Modell-/Web-Toolloop verhindert Veröffentlichung; (6) Original-Offline-Text trotz modellseitig geänderter Suchfrage gesperrt; (7) historische Quellen markiert, unbekannte/abgelaufene Zeit nicht frisch; (8) E7Person-Ort mit Namenswechsel/Rückfrage, keine fremde Sitzung; (9) vorhandene oder ausdrücklich freigegebene disposable Test-Altersangabe/Korrektur/Negation/Widerruf ohne Owner-Datenkontamination; (10) bestehende Model-/Archiv-/Lernprotokollbackups bytegleich, SWB-Messungen stabil; (11) nativer Gemma-Lade-/Antwortpfad+UI/Sprachstatus; (12) Job-Stopp bei niedriger Batterie/thermischer Belastung, PSS/Java-/Native-RAM/P50/P95/Netzverbrauch real messen. Keine dieser Messungen in dieser Sitzung erfunden.

Nicht überschritten: main, produktive Signatur-/Publisherpins, Owner-Wissensbestand, SWB/CDX-Gewichte, Backupformate, A/B-Testkorpora, Battery/Thermal-Schutz. Rücknahme nur gezielt auf eigenem Reviewbranch; keine fremden Branch-Resets/Forcepushes. Der Nutzer beauftragte zum Schluss Sicherung+Abschluss statt neuer Forschung; weitere Meilensteine sind bewusst offengelegt, nicht als erledigt erklärt.
