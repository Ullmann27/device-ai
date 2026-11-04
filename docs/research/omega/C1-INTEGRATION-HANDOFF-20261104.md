# C1 Integration — Implementation Plan und fortsetzbarer Handoff

> Für fortsetzende Bearbeiter: serieller Einbau durch C1; unabhängige Quellen-/Patchreviews können parallel laufen. Der direkte Besitzerauftrag autorisiert die Ausführung dieses Plans ohne weitere Zwischenfreigabe. Entwicklungs-Skills: using-git-worktrees, writing-plans, executing-plans, verification-before-completion. Bestehende Projektverträge und dieser Handoff bestimmen die Ablage.

**Goal:** Einen eindeutig identifizierten gemeinsamen App-Kandidaten aus abgeschlossenen Beiträgen herstellen, gezielt und anschließend vollständig prüfen und die echte APK-Vorbereitung dokumentieren.
**Architecture:** Vollständigen #513-Baum bewahren. Als ersten Slice ausschließlich den abgeschlossenen negativen ECHO-Rückweg aus #515 nach Quellen-/Abhängigkeitsprüfung übernehmen. Bestehende kanonische Worker-, Speicher- und Avatarpfade bleiben maßgeblich.
**Tech Stack:** Vorhandenes Kotlin/Android, Node.js, lokale JDK17/Gradle8.11.1/SDK36-Toolchain, GitHub als Quellablage.
**Spec:** Direkter Besitzerauftrag CONTINUUM C1 — INTEGRATIONSLEITUNG; AGENTS.md; docs/CONTINUUM-EXPERIMENTVERTRAG.md; docs/CONTINUUM-EXPERIMENTE.json; Issue #41 und zentrales ASTRA-NUCLEUS-Board.

## Global Constraints

Kein Main-Merge, Release, Installation, Signaturwechsel, bezahlter Dienst oder Actions-Lauf. Originalpaket at.heinz.deviceai.continuumlocal, Besitzerdateien, Updatekanal, Offline-Schalter, ToolGate und CCC1 erhalten. Eigene Kopie/Branch, keine fremden Arbeitsbestände verändern. Modellforschung bleibt experimentell. Signierte Besitzer-APK benötigt gesonderte Abschlussfreigabe und neu gelesenen Kanalstand.

## Review Focus

- Strict-model Ablehnung muss Ablehnung bleiben, auch wenn ein erlaubter negativer Report entsteht.
- Report und Snapshot müssen zu genau demselben kanonisch protokollierten ABSTAINED-Versuch gehören.
- Fehlende, fremde, veraltete oder uneindeutige Resultate dürfen kein positives Outcome erzeugen.
- Report-/Snapshot-Fehler dürfen den ursprünglichen Fehler nicht in Erfolg umdeuten.
- Gesamte Android-Verbindung, Offline-Schalter und reale Avatarreaktion sind getrennt von Node-Fixtures und synthetischem Audio zu prüfen.

## Quelle und Status

Task-ID: C1-INTEGRATE-001. Status bei diesem Startcheckpoint: **WIP / NOT_RUN**.
Remote-Basis #513: 28b228698aef8c52e46c72b71fdd5e1915d6e76c.
Lokale vollständige Basis: 2d5ce59b786f427eacb394484d81796568bb87b4.
Vollständige Baumidentität: e52acc146e66ae66a95429da2fb0f34242ad1791.
Arbeitszweig: integration/continuum-c1-candidate-20261104.
Der eigene Startcommit wird im Git-/PR-Verlauf exakt nachgewiesen; keine selbstbezügliche erfundene SHA im Dateiinhalt.

Gelesen: sechs angeforderte aktuelle PRs und Handoffs, #498-Empfangsprotokoll, #506-Reviewbefunde, Projektverträge. Drei echte C1-Unteragenten: source_audit, repair_review, apk_readiness. Externer C2 und C3–C10 wurden dadurch nicht gestartet; neue ACKs nicht beobachtet.

## Task 1: genau einen fehlenden abgeschlossenen ECHO-Slice übernehmen

**Files:** .github/continuum/coding-orchestrator.mjs, coding-service-worker.mjs, coding-service-reader.mjs und coding-orchestrator-failure-report.test.mjs; endgültiger Umfang erst nach unabhängigem Abhängigkeitscheck.
**Interfaces:** bestehender runCodingTask-Fehlerpfad, kanonisches Journal, vorhandener Report-/Snapshot-Rückkanal. Keine neue Autorität und kein neuer Scheduler.
**Experimente:** EXP-02, EXP-07, EXP-08, EXP-11; keine Statuspromotion des Gesamtversuchs.

- [x] Aktuelle Quellen einfrieren, vollständigen Kandidaten isolieren und Baumgleichheit mit #513 prüfen.
- [ ] Fehlende #515-Änderung und tatsächliche Abhängigkeiten unabhängig verifizieren.
- [x] Fertige Regression in eigener Kopie gegen unveränderten Kandidaten ausführen; erwartete fachliche RED-Ursache archivieren.
- [x] Genau den kompatiblen abgeschlossenen Slice seriell übernehmen.
- [x] Fokussierte Node-Verträge und unabhängige Negativkontrollen ausführen; keine Ausweitung bei rotem Ergebnis.
- [x] Quellen, tatsächliche Befehle/Exitcodes, Logs, Grenzen und Rollback committen und remote per exakter Tree-Identität sichern.

Geplanter Prüfeinstieg: node --test .github/continuum/coding-orchestrator-failure-report.test.mjs; **NOT_RUN** im Startcheckpoint. Weitere betroffene vorhandene Tests werden vor Ausführung im Befehlsmanifest benannt. Erwartung vor Reparatur: mindestens spezifischer Report-/Snapshot-Fall rot; nach Reparatur: vollständiger fokussierter Satz grün, ursprüngliche strenge Ablehnung unverändert.

## Task 2: gemeinsamen Kandidaten und APK-Vorbereitung prüfen

**Files:** bestehende Quellen unverändert einfrieren; docs/evidence/c1-integration-20261104/ und dieses Handoff/Board fortschreiben.
**Interfaces:** tatsächlicher AgentActivity-Eingabe-/Compiler-/Runtime-/Tool-/Antwort-/Avatarweg; bestehende QA-/Updateverträge.

- [ ] Erforderliche vollständige Node-, Backend-, Android-Unit- und konfigurierte Lint-Gates aus Quellen bestimmen.
- [ ] Gemeinsamen Build-Lock und verfügbaren Offline-Cache fixieren; schwere Läufe seriell ausführen.
- [ ] Ergänzende echte Activity-/Handler-/Sprachlistenerpfade auf demselben App-Baum ausführen.
- [ ] Paket-/Version-/Signaturkonfiguration und aktuelle Kanalbelege lesen; APK erst nach eindeutiger Manifestbindung.
- [ ] Unabhängige Gegenprüfung auf genauem Endstand; C2-Handoff mit Rückmeldestatus PENDING_ACK bis tatsächlicher Antwort.
- [ ] Ergebnis und negative Befunde sichern, nächsten begrenzten Schritt aus dem Board wählen.

Startstatus: vollständiges Node-/Android-Gate, Modelllauf, APK-Bau und Gerätetest **NOT_RUN**. Ein fremdes Debug-Paket ist kein Besitzerupdate. Quellenprüfung, Hosttests, echte lokale Inferenz und Fold7 werden getrennt berichtet.

## Fortschrittsledger und Entscheidungen

- Auswahlentscheidung: #513 ist die neuere zusammengeführte #512/#506-Linie; deren Baum bleibt erhalten. Gegen Kosten/riskante Doppelarbeit wird nur ein begrenzter #515-Slice übernommen.
- Quellenentscheidung: lokaler und veröffentlichter Commit haben verschiedene Git-Historien, aber denselben vollständigen Baum. Beide IDs werden angegeben; bei Veröffentlichung wird der resultierende vollständige Remote-Tree mit dem getesteten lokalen Tree verglichen.
- Dokumentationsentscheidung: vorhandenes ASTRA-NUCLEUS-Board wird unter identischem Pfad fortgeschrieben, historischer Teil erhalten. Keine zweite konkurrierende Aufgabenübersicht.
- Die direkte Ausführung, isolierte Branch und Commit/Push/Draft-PR sind durch den Besitzerauftrag autorisiert. Keine zusätzliche Zwischenfreigabe nötig; Veröffentlichung/Installation bleiben separat.

## Rücknahme und nächster Einstieg

Bei fehlschlagender Slice-Abnahme den ungeänderten #513-Baum als zuletzt übernommenen Kandidaten erhalten. Nur eigenen ECHO-Integrationscommit zurücknehmen, niemals andere Branches zurücksetzen. Negative Belege nicht löschen. Nächster Einstieg: unabhängigen #515-Minimalreview lesen, dann RED gegen die eingefrorene Basis ausführen. Vor Fortsetzung aktuelle PR-Heads und ACKs erneut lesen; keine Tests aus diesem Startcheckpoint als PASS ausgeben.

## C1-INTEGRATE-001 — gezielter Integrationscheckpoint

Der API-kompatible alte Aufrufer reproduzierte den echten Fehler: kanonischer Zustand ABSTAINED, aber error.botReport.status fehlte. Exit 1, eine fachliche Assertion rot; kein Import-/Syntaxfehler. Die unveränderte Regression wurde aus #515 abgeleitet, nur nicht vorhandene unbenutzte neue Imports entfernt und Importpfade auf den isolierten Kandidaten gerichtet. Original und Aufruf sind archiviert.

Danach exakt drei Produktionsdateien und die vorhandene 240-Zeilen-Regressionsdatei aus #515 übernommen; alle vier Git-Blobs stimmen mit 572e12e überein. Branch-Discovery, Scheduler-Preflight und repair-critic wurden nicht übernommen. Die drei vorhandenen Produktionsdateien waren vor Übernahme zu #515s Basis e101e39 bytegleich.

Eigene Prüfungen: 27 bestehende Node-Verträge vorher PASS; einzelne spezifische Regression vorher 0 PASS / 1 FAIL; kombinierter fokussierter Satz nach Übernahme **52 PASS / 0 FAIL / 0 SKIP, Exit 0**. Diese Gruppen überlappen und werden nicht addiert. Vier node --check und git diff --check Exit 0. Befehle, Rohlogs, Quellhashes und Grenzen: docs/evidence/c1-integration-20261104/echo-slice/.

Status: TARGETED_HOST_PASS / INDEPENDENT_REVIEW_PENDING. App-/Scripts-Bäume unverändert. Vollständiges Node-/Android-Gate, Modelllauf, APK und Gerätetest noch NOT_RUN. Keine Releasefreigabe. Nächster Schritt: unabhängigen Patchreview abgleichen, exakten Gesamtstand einfrieren und vollständige Gates unter vorhandenen Ressourcenlocks ausführen.

## C1-QA-SOURCE-002 — vollständige Quellidentität entblocken

Ausgangsstand: lokal `a15a2b771bb934d998b2f9c9552c30d30400f707`, remote `474a457f5fa6230d45fd4c773fe67e219df73984`, identischer vollständiger Baum `a457d368b13ec939f4f71438a3cfda3ef5a4c10d`. ECHO-Slice damit remote gesichert in Draft #519.

Das kanonische `critical`-Gate scheiterte vor dem ersten Test: `GIT_METADATA_UNAVAILABLE`, Exit 1. Die vollständige Git-Dateiliste überschreitet Nodes voreingestellten 1-MiB-Ausgabepuffer; direkter Prozessbefund `ENOBUFS` / SIGTERM. Dies ist kein ausgeführter Android-/Node-Testfehler und kein PASS. Beide vorhandenen Maschinenlocks wurden gehalten, Quelle blieb unverändert.

Begrenzte Änderung: ausschließlich `scripts/continuum-reliability-qa.mjs` erhält einen 16-MiB-Puffer im vorhandenen Git-Helfer; alle Dateien und Fehlerbedingungen bleiben erfasst. Neuer `scripts/continuum-reliability-source-identity.test.mjs` erzeugt eine echte isolierte Git-Dateiliste über 1 MiB, prüft jeden Pfad, Mutation hinter der früheren Puffergrenze, zusätzliche untracked Datei und weiterhin abgelehnte fehlende Git-Metadaten. Getestet wird der unveränderte private Produktionsfunktionskörper über eine nur für den Test ergänzte Fixture-Exportzeile.

Echter RED vor Korrektur: 1 PASS / 1 FAIL, Exit 1, fachlicher Metadatenfehler. GREEN zusammen mit bestehenden QA-/Evidence-/Architekturverträgen: **30 PASS / 0 FAIL / 0 SKIP, Exit 0**. Keine Addition zu den überlappenden ECHO-/Gesamtzahlen. Befehle, Rohbelege, Quellhashes und ursprüngliches fehlgeschlagenes Gate: `docs/evidence/c1-integration-20261104/source-identity/`.

Status: TARGETED_HOST_PASS / INDEPENDENT_REVIEW_PENDING. Nächster Schritt nach Commit: `critical` und `full-node` auf sauberem exakt identifiziertem Gesamtstand mit neuer Versuch-ID ausführen. Rücknahme: nur diesen QA-Quellfix und Test zurücknehmen, Belege erhalten; ECHO-Checkpoint bleibt unverändert verfügbar. Das rohe ECHO-Baselinelog wird als `.txt` ergänzt, da `.log` in diesem Repository ignoriert ist.

## C1-CONTRACTS-003 — vorhandene Quellverträge an tatsächliche Anschlüsse binden

Basis: lokal `8873030fdfe23bdd4153f96004b786bff2cddecf`, remote `1a5302c504a78a50d5e64ca727f2915529bf2b06`, Tree `d1a1b733ad37f21c155527fa0c9532f56fb1b934`. Auf diesem unveränderten Stand endete critical mit 131 PASS/4 FAIL und full-node mit 2280 PASS/32 FAIL/37 SKIP. Die vollständigen ursprünglichen Berichte, XMLs, echten Befehle/Exitcodes und Quellhashes liegen komprimiert in `host-gates-8873030/`; Manifest SHA-256 prüft jedes Archivmitglied. Keine nachträgliche Umdeutung dieser roten Resultate.

Herkunft des nächsten begrenzten Pakets: #517 `46e5fa6b09454ad6f6862e80478eb0f2d5545149`, Archiv SHA-256 `5ba29a696ac00bf528b4350d15cdf4013e5380833f5f1ecca551295159b4476a`. Alle 25 Mitglieder beim Lesen gegen Manifest geprüft. Vier fertige Patches übernehmen sieben bestehende Tests: android-integration-wiring, cognitive-trace-wiring, continuum-local-dialogue-quality-wiring, coding-agent-speech-wiring, aaa-cockpit-and-effects, android-avatar-wiring und continuum-learning-parity-readout. Keine App-Produktion geändert. Kein Gesamtmerge des roten #517-Zweigs.

Eigene Abnahme: `node --test --test-concurrency=1 --test-reporter=tap` mit exakt diesen sieben Dateien: **48 PASS / 0 FAIL / 0 SKIP, Exit 0**. Zusätzlich neun ausschließlich im Speicher mutierte Quellkontrollen: falscher ownerTurn, entfernte Bot-Memory-Schranke, Planungs-Toolbudget, verlorener turnTrace, falsche Sprachregion, entfernte Entwicklerroute, fehlender Audioframe, abgehängter Live-Sink und vertauschte Referenznavigation. Jede der neun erwarteten Verletzungen wird vom passenden vorhandenen Test abgelehnt, jeweils Exit 1. Das sind Negativkontrollen, keine neun zusätzlichen Produktionserfolge.

Unabhängiger read-only Review durch repair_review: sieben Änderungen entsprechen den tatsächlichen Aufrufern und erhalten Grenzen. Einschränkung: AvatarDirector.attach ersetzt den anfänglichen fanOut-Sink; der Test trägt einen Live-Hologramm-Verbindungsnachweis, keine dauerhafte Fan-out-Zusage. ECHO separat unabhängig erneut ausgeführt: neun gezielte Tests PASS, Exit 0; Teilmenge der vorherigen52, nicht addieren.

Belege und Reproduktionsprogramm: `docs/evidence/c1-integration-20261104/source-contracts/`. Rücknahme: ausschließlich diese sieben Testkorrekturen revertieren; negative ursprüngliche Gesamtbelege bleiben. Neue vollständige Abnahme erfolgt erst nach den nächsten seriellen Produktslices, nicht als Wiederholung zur Erhöhung von Testzahlen.

## APK-/Ressourcencheckpoint und Fortsetzung

Paket-, Varianten-, Versions- und Signaturkonfiguration ist in `apk-audit/configuration.json` getrennt vom historischen #475-Beleg dokumentiert. Tatsächlicher Livekanalabruf über das Web-Tool am 04.11.2026 20:14:10 UTC: URL für dieses Tool nicht zugänglich, weder HTTP-Status noch Manifest. Daraus wird weder aktueller Code41 noch ein Serverausfall abgeleitet. Eine Updateversion bleibt offen. QA kann einen ausdrücklich als QA bezeichneten Parameter verwenden, ohne Besitzerkanal oder Version zu ändern.

Toolchainpfade und beide vorhandenen Robolectric-JARs sind tatsächlich geprüft; diese JARs würden bei neuem UI-Testziel zusätzlich etwa291,5MiB benötigen. Platzbedarf des ganzen Builds ist damit nicht bewiesen. Ein einmaliger ENOSPC beim Anlegen eines Evidenzverzeichnisses ist protokolliert; nach frischer erfolgreicher Platz-/Schreibprüfung wurde die Sicherung fortgesetzt. Kein fremder Bestand entfernt.

Nächster serieller Schritt: C1-AVATAR-005 mit eigenem tatsächlichem Kotlin RED/GREEN und unabhängig geprüftem #517-Signed-zero-Patch; danach C1-ROUTING-006 als begrenzte Activity-Verbindung. Offene UI-Quellverträge werden aus fertig geprüften #517-Paketen übernommen. Danach sauberen gemeinsamen Commit pinnen, vollständige Gates erneut ausführen und erst bei erfolgreicher Abnahme APK-Vorbereitung fortsetzen. Externer C2 bleibt PENDING_ACK; keine fremde Sitzung als gestartet/erledigt ausgeben.

## C1-AVATAR-005 — tatsächlicher Kotlin-Randfall repariert

Voriger gesicherter Checkpoint C1-CONTRACTS-003: lokal `eb736544e0864f8f1a58a891d9cd0dce2904dcc2`, remote `8557ef2813b54654ff3a70d979e7ee8e8eefedd8`, identischer vollständiger Tree `f1dc7958b05f0bd819d4503c1b0079abbeae815a`. Beide ursprünglichen roten Gesamtgates und alle Negativkontroll-Rohlogs sind dort remote gesichert.

Abgeschlossener #517-Patch aus dem geprüften Archiv, SHA-256 `f689bb8cd5ffd999a16a2f15d97e2726bf6fc9b794a6e5cc527dca1a8932bb1d`: `AvatarIdleMotion.kt` normalisiert negative und positive Null vor der binären Suche. Negative ganze460-Sekunden-Zyklen können ebenfalls −0.0 liefern. Für endliche Reste ungleich null bleibt die bisherige Berechnung exakt erhalten. Einbau betrifft nur diese Produktionsklasse und den neuen `AvatarIdleMotionBoundaryTest.kt`.

Eigener echter Kotlin/JUnit-Lauf mit vorhandenem offiziellen2.4.0-Compiler und JDK17: **RED Compile0 / JUnit1, zwei Tests mit einem ArrayIndexOutOfBoundsException(-1)**; anschließend **GREEN Compile0 / JUnit0, zwei Tests bestanden**. Der erste Fall vergleicht alle fünf Ausgabefelder für beide Nullzeichen und ganze positive/negative Zyklen; der Subnormalfall prüft ausschließlich Endlichkeit. Beide vorhandenen Maschinenlocks wurden gehalten; tatsächliche Befehle und SHA-256 vor/nach jedem Lauf: `docs/evidence/c1-integration-20261104/avatar-idle/`.

Unabhängiger statischer Review durch repair_review befürwortet die begrenzte Korrektur. Keine Behauptung eines beobachteten Geräteabsturzes oder einer GPU-/Android-/Modellabnahme. Rücknahme: ausschließlich diese zwei Dateien revertieren, Belege erhalten. Nächster Schritt: begrenzter unbekannter-Bot-Select-Rückweg C1-ROUTING-006 sowie fertiges UI-Vertragspaket C1-UI-CONTRACT-007; danach neue Gesamtgates auf gemeinsamem Commit.

## C1-UI-CONTRACT-007 — fertige UI-Verträge seriell übernommen

Basis: lokal `0dac310728a84bd1adc296377ed7d124bc09692a`, remote `a54583816df92bcd0cdf4e75f06cdefffb5dafcc`, identischer Tree `3e9373b19dc0b7b3751ebfbdd2ac09cd7455b667`. Dies sichert den gezielt geprüften Signed-zero-Fix.

Das bestehende Team #517 übergab zusätzlich einen fertigen lokalen Neun-Dateien-Patch auf Basis `20f888660a967aa1d6b5446e892540538372d319`; Patch SHA-256 `c78066e2a6f0b3f1ff42add03eabcb26361ad10f33e1e49352d44bba8da49b50`. Dieses Paket war nicht Teil des früheren46e5fa6-Archivs. C1 hat Patch, Übergabe und vollständige positive/negative Belege separat eingefroren und archiviert. Alle neun Ausgangsblobs passen; alle neun resultierenden Testdateien stimmen SHA-256-genau mit dem fertigen Beitrag überein. Keine App-Produktion geändert. Der eigene vorherige Diagnosefreeze war beendet; kein fremder Checkout angefasst.

Eigener gezielter Lauf mit allen neun Dateien: **48 PASS / 0 FAIL / 0 SKIP, Exit0**. Die ursprünglichen24 Fehler dieser Dateien bleiben im full-node-01-Archiv nachgewiesen. Weitere17 negative Quellkontrollen stammen vom Lieferteam; deren Rohlogs wurden sowohl unabhängig als auch durch C1 dekomprimiert, gehasht und auf erwartete Fehler geprüft, aber nicht als eigene C1-Ausführungen gezählt. Unabhängiger statischer Review: Anforderungen folgen der dokumentierten neuen Referenz und den tatsächlichen Klick-/Layout-/Reflow-/Modellpfaden; keine materielle Absenkung festgestellt.

Roharchive/Dateihashes/Befehl: `docs/evidence/c1-integration-20261104/ui-contracts/`. Visuelle Referenzabnahme, 2D-Fallback und tatsächliche Android-/GPU-/Gerätefunktion bleiben gesonderte Gates. Ein anderer #517-Stand hat weiterhin sieben rote Avatar-UI-Tests trotz beendeter VSync-Hängerdiagnose; dieses unfertige Vier-Dateien-Paket wird nicht als fertig übernommen. Rücknahme: nur diese neun Testdateien revertieren, Belege erhalten.

## C1-QA-GUARD-008 — abgeschlossenen Signing-Ausschluss aus #505 ergänzen

Voriger UI-Checkpoint: lokal `1666b3c9fa9fa9fe360f0983213f3413de7d0218`, remote `6ac2e1506071c5782bc06e83d043f3c2e082fb23`, vollständiger identischer Tree `5a314dc09d2b623a0097819856a1e33c8e883f1e`.

Die Produktionszeile stammt aus abgeschlossenem #505 `84cd8cfc05dedef5d4feb28ecfa103530e95f7ff`. C1 las das Skript direkt am GitHub-Pin: Blob `9ea8eae2011504b5283a0cf4e62231f0aaa7c39b`. Das vollständige eigene Skript nach der einzigen zusätzlichen Variable ist genau blobgleich. Der bestehende QA-Guard erfasst nun auch `CONTINUUM_MENTOR_SIGNING_P12_BASE64`. Die erweiterte bestehende Testsuite stammt aus dem #517-Archiv; sechs Variablen in beiden Modi werden vor Logs/Buildverzeichnis/Download abgewiesen.

Eigener RED: 43 Tests,41 PASS/2 FAIL,Exit1 (die beiden vorher fehlenden Mentor-Base64-Fälle). Nach Produktionsfix: **43 PASS/0 FAIL/0 SKIP,Exit0**. Statisch unabhängig befürwortet. Ausschließlich synthetische `fixture-not-a-key`-Werte und lokaler Fake-curl; keine echten Schlüssel gelesen, kein Download, keine Signierung/Veröffentlichung. Echte Befehle/Dateihashes/Logs: `docs/evidence/c1-integration-20261104/qa-signing-guard/`.

Rücknahme betrifft nur die beiden benannten Guard-/Testdateien; negative Belege bleiben. Nächster Schritt: nach isoliertem Routing-GREEN und unabhängigem Review C1-ROUTING-006 seriell einbauen, gemeinsamen Stand einfrieren und aktuelle vollständige Hostgates ausführen.

## C1-ROUTING-006 — unbekannte natürliche Botwechsel weiterreichen

Basis: lokal `ced43fbe26cfc97c5c8fbbc3f429cd0845047c4f`, remote `9e84803cc1907d9618669ab039b1fd667ab5b207`, gleicher Tree `18806a9172380187f7485eaa0e3490b27fb8a79a`. Der durch C1-Unteragent source_audit gelieferte Patch SHA-256 `c47ab9c0c70ca271f47f190ec475f9d6ce02ffdd60774679249de730356830ed` ist vom zweiten Unteragent repair_review statisch unabhängig befürwortet und wird ausschließlich durch C1 eingebaut.

Nach erfolgreicher Profilauflösung ohne Treffer führen natürliche Formulierungen wie „wechsle zu dunklem Modus“ in den vorhandenen Eingabepfad. Ein bereits gewählter privater Bot bleibt zuständig. Bekannte Botwahl, direkte @-Nachricht sowie @-markiertes Select-Ziel, Mehrdeutigkeit, Speicherfehler und geänderte Eingabe behalten ihre Schranken. Router und Store werden produktiv nicht geändert. Activity-Basisblob `c1482ed3be0cb91734e10947613ec62d1a3c38cb` → Kandidat `bc1a08495f56696b2f0a6613260beb92bb3dbc62`; zwei bestehende Testklassen ergänzt.

Isolierter tatsächlicher Kotlin-Methodentest mit echtem Router und Profil-Metadatenspeicher: Autor-RED **25 Tests,22 PASS/3 FAIL, Compile0/JUnit1**. Nach zwei ergänzten expliziten @-Schutzkontrollen führte C1 selbst den aktuellen Kandidaten aus: **27 PASS/0 FAIL, Compile0/JUnit0**. Beide vorhandenen Locks wurden gehalten. Zuvor blockierte Compiler-Erwerbsversuche bleiben als NOT_RUN erhalten. Nach Einbau wurden die drei Dateien bytegenau gegen diesen geprüften Kandidaten verglichen.

Dieser Methodentest enthält explizite UI-/Queue-/Aufrufziel-Doubles und unbenutzte Conversation-Speicher-Compilegrenzen. Er ist keine reale Android-, Thread-, Conversation-Speicher-, Modell- oder Geräteabnahme. Die sechs neuen Activity-/Robolectric-Methoden sind bis zum nächsten Gradle-Lauf **NOT_COMPILED/NOT_RUN**. Vollständige ursprüngliche und neue Quellen, Befehle, Rohlogs und Hashmanifest: `docs/evidence/c1-integration-20261104/routing006/`.

Rücknahme: nur den kleinen null-Profil-Zweig und die beiden Testergänzungen revertieren; keine neuere Activity durch eine alte Gesamtdatei ersetzen. Nächster Schritt: gepinnten sauberen Gesamtstand zunächst mit tatsächlichen Router-/Activity-Tests samt Compilation/NewApi prüfen; danach volle Node-/Backend-/Android-Gates. Keine APK aus einem unvollständig abgenommenen Stand.

## C1-ROUTING-006-ANDROID — tatsächlicher Activity-Fokus abgeschlossen

Geprüfte Quelle: lokal `165fc9f58ae9e36295690df60145174fd5dc58e7`, remote `401a5d01a7b2e0e0f8c50531daf5fa24814fbbda`, identischer vollständiger Baum `4b3e47f30517cf952ab50c0a52b27bb96d281504`. Der saubere Stand blieb während des Laufs unverändert.

Der erste Aufruf mit CLI-`--tests` scheiterte vor Tests an der vorhandenen Aufgaben-Allowlist (Exit 1). Diese Schutzprüfung wurde nicht verändert. Der reguläre Gradle-Testfilter wird über ein gesichertes Init-Skript gesetzt; die angeforderten Aufgaben bleiben ausschließlich Compile/Test/Lint. Versuch 02 startete wegen belegter gemeinsamer Locks keinen Build (äußerer Exit 75). Erst nach neuer Freigabe-Beobachtung lief Versuch 03 unter beiden vorhandenen Locks. Alle Versuche bleiben mit tatsächlichen Befehlen und Ergebnissen erhalten.

Tatsächlicher erfolgreicher Befehl, über `qa/run_c1_gate.py android-routing-03` gestartet:

```bash
/workspace/scratch/b6ee64d418cf/toolchain/gradle-8.11.1/bin/gradle --offline --no-daemon --rerun-tasks --max-workers=2 --console=plain '-Dorg.gradle.jvmargs=-Xmx1600m -XX:MaxMetaspaceSize=512m' -Pkotlin.compiler.execution.strategy=in-process -PdeviceAiMentorPreview=true -PcontinuumUiWidgetTests=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=41 -PcontinuumAuditProgressFile=/workspace/scratch/174c84456ee3/qa/android-routing-03-active-test.json -PcontinuumAuditTestClasses=at.heinz.deviceai.chief.ChiefBotAddressRouterTest,at.heinz.deviceai.ContinuumBotTaskRoutingUiTest -I /workspace/scratch/174c84456ee3/qa/observe-tests.init.gradle :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
```

Ergebnis vom 04.11.2026, 20:46:04 UTC: **Exit 0, 29 PASS / 0 FAIL / 0 SKIP**, davon 18 `ChiefBotAddressRouterTest` und 11 `ContinuumBotTaskRoutingUiTest`. Vollständige Appquelle und aktivierte UI-Testquellen kompiliert. Konfigurierter **NewApi-Lint: 0 Issues**. Frische vollständige XML-Testfälle, Zeitgrenzen und Lint-XML durch C1 geprüft. Der vorherige isolierte 27-Test-Lauf überlappt und wird nicht addiert.

Archiv und eigenständiges Manifest: `docs/evidence/c1-integration-20261104/android-routing-165fc9/`, Archiv SHA256 `2815f76c19c903cf2cabf73ef69ac2eae66e70f5453fa45b4d59eb8a887976d6`. Diese Sicherung ändert ausschließlich Dokumentation. Sie behauptet keinen erneuten Lauf auf einem späteren Commit.

Grenzen: Zwei gezielt ausgeführte Klassen ersetzen keine vollständige Unit-/UI-Suite. Die Activity-Fixture ruft echte Intake-Methoden und Android-Queues auf, nicht den vollständigen onCreate-/geladenen Modellpfad. QA-Code41 ist ein historischer ausdrücklicher Compile-/Test-/Lint-Parameter; kein aktueller Updatecode wurde gewählt, keine APK erzeugt.

Zusätzlich gesichert: C1-PATH-010 mit separat gebundener Quellkette und ausdrücklich korrigierter Fan-out-Aussage; C1-GATES-012 mit vollständigem Pflichtumfang; frischer Fachzweig-Abgleich. Der neue #517-Fokus bei f17ee62 ist extern 53/53 grün, sein vollständiger Androidlauf endet jedoch mit Exit143 an einem anderen ReferenceCockpit-Testtimeout. C1 übernimmt daraus nur das unabhängig geprüfte Vier-Dateien-Paket und führt anschließend einen eigenen gezielten Lauf aus. Tagesgrenzen aus #505 bleiben ein eigener nachfolgender Drei-Dateien-Slice.

Rücknahme: reine neue Beleg-/Boardänderungen können separat revertiert werden; der geprüfte Quellcommit401a5d bleibt erhalten. Nächster Schritt: C1-AVATAR-009 seriell einbauen, tatsächliche drei Avatar-UI-Klassen auf dem neuen C1-Stand prüfen. Externer C2 bleibt PENDING_ACK.

## C1-AVATAR-009 — fertigen Fallback-/Fixture-Slice übernehmen

Basis: lokal `f976d81c4f38258143c82de56766aee4a073ee02`, remote `0e3d181ed36d2ffa4e77fda3aa8b72133fbb8cb0`, vollständiger identischer Tree `9e2b708925daa824b235cdb30a8cef50667560ba`. Die vorherige Dokumentationsscheibe sichert den echten Routing-Android-/NewApi-PASS auf165fc9/401a5d; sie führte selbst keine Tests erneut aus.

Die unabhängige Gegenprüfung bestätigt jetzt das abgeschlossene Vier-Dateien-Paket aus #517 `f17ee62f8cfda8914ed217fe2ed88882ba1fa8a4`. Patch SHA256 `3b0fcaa1a9d093bc26aa7e02fb7b262fcfaf7351f2a616761f223e139e350289`. C1 prüfte alle Basis-/Zielblobs und SHA256, `sha256sum --check --quiet SHA256SUMS` Exit0, `git apply --check` Exit0 und `git diff --check` Exit0. Keine weiteren #517-/#505-Dateien übernommen.

Produktionsumfang: `ContinuumHologramView.kt` rendert den vorhandenen2D-Kopf auch nach tatsächlichem `native3dFailed`; Initialisierungstext nur während ausstehender Initialisierung. Drei vorhandene UI-Klassen erhalten pausierte VSync-Steuerung, echten Fenster-Traversal, feste Viewportgröße und passende PCM-Zeitabstände. Alle13 vorhandenen Testmethoden und78 ursprünglichen Assertions bleiben erhalten;22 zusätzliche Assertions sichern Sichtbarkeit und Layout. Keine Produktionsuhr, Modell-, Tool- oder Freigabeänderung.

Externe Rohbelege: vorher46/53PASS mit7Avatarfehlern auf92f421, danach53/53auf f17ee62. Der ältere RED enthält bereits den Produktions-Fallback und beweist deshalb nicht allein dessen alten C1-Fehler. Der separate vollständige f17ee62-Lauf scheitert mit Exit143/180-Sekunden-Timeout in ReferenceCockpit; diese negative Evidenz bleibt ebenfalls im Paket. Aus allen externen PASS wird kein C1-PASS abgeleitet.

Genaue Pfade, Quellen, Roharchive und Rollback: `docs/evidence/c1-integration-20261104/avatar009/`. **Status OWN_ANDROID_FOCUS_PENDING.** Nächster Schritt: sauberen Commit pinnen und die tatsächlichen drei Avatar-UI-Klassen unter beiden Locks ausführen. Bei Fehler den separat erhaltenen geprüften Routing-Stand verfügbar halten und nur diesen begrenzten Slice korrigieren/zurücknehmen. Keine APK-Freigabe.

## C1-AVATAR-009 — eigener Android-Fokus bestanden

Tatsächlich geprüft: lokal `53ca8b6e3284c27c9c06f1462c469c7cff300e7d`, remote `5d7ddf0762455b92353ba9a559546e8683ad82fe`, gleicher Tree `39cbcfbd0b5fd0e1f95360aba42f2fa650f9deae`. `qa/run_c1_gate.py android-avatar-01` führte mit echtem Gradle, Offline-Cache und normalem Klassenfilter genau die drei übernommenen UI-Klassen aus. Vollständiger echter Befehl und unveränderte Rohlogs liegen in `avatar009-own-android/`.

Ergebnis20:51:19UTC: **Exit0,13 PASS/0 FAIL/0 SKIP** (4 Lifecycle,3 Native-UI,6 Motion), frische XML-Fallnamen und Zeitgrenzen unabhängig durch C1 geprüft. Beide Locks gehalten; Quellstand unverändert. App-/UI-Kotlin nach den Änderungen tatsächlich kompiliert; unveränderte übrige Buildaufgaben durften regulär wiederverwendet werden. Kein neuer Lint auf diesem Stand und keine Gesamtfreigabe. Die externen53 und eigenen13 überlappen und werden nicht addiert. Keine echte GPU-/TTS-/Modell-/Gerätebehauptung.

Archiv SHA256 `14d433df4ec69b5c31fd5c23c490f3123bcbb2eafc55aa24f146eae36ca5fd0a`. Der vorige Routing-Android-PASS ist weiterhin separat an165fc9/401a5d gebunden.

## C1-TEMPORAL-011 — abgeschlossene Tagesgrenzen reparieren

Basis ist der oben gezielt geprüfte Avatar-Stand53ca8b6/5d7ddf. Quelle #505 `b2bc510a59e29d4ece478109e12b9785be11ee7d`; aktueller #505-Head e83f6ac hat identische App-/Scripts-/Tools-Unterbäume. Genau drei Pfade aus dem unabhängig geprüften Paket: `ContinuumMemoryRetriever.kt`, neuer `TemporalCueDailyBoundaryTest.kt` und bestehender ausführbarer `tools/temporal-cue-host-check/run.sh`. Patch SHA256 `476f92efe30e27ba31b60e5718b25f1c04b93da2df646a54276b6197639d1351`. Alle C1-Basisblobs, Zielblobs, Dateimodi und Hashes geprüft; `git apply --check`/Anwendung Exit0.

Beide Tagesgrenzen werden separat aus dem jeweiligen lokalen Datum mit atStartOfDay(zone) bestimmt. Dadurch überträgt eine heutige Mitternachtslücke nicht deren verschobene Uhrzeit auf andere Tage. Wochen-/Monatsfenster, Ranking, Corpus, Trust und Hauptchat-Aktivierung bleiben erhalten. Zwölf feste Grenz-/Suchtests ergänzen17unveränderte Tests. Gelesene originale Quellenbelege:29Tests/8Fehler vor der Korrektur und29/0danach; bisher keine eigene C1-Ausführung dieses neuen Slices.

Status **OWN_HOST_RUN_PENDING**. Nächster echter Befehl unter beiden Locks: `bash tools/temporal-cue-host-check/run.sh /workspace/scratch/b6ee64d418cf/toolchain/gradle-8.11.1/lib` mit vorhandenem JDK17. Der vorhandene Runner nutzt Kotlin2.0.20, JUnit4.13.2 und eine bewusst fehlschlagende Compilegrenze für den hier nicht ausgeführten Event-Decorator. Android wird separat mit Projektcompiler2.4geprüft. Keine42/44-Rankinggrenze aufgehoben, keine Modellpromotion. Belege: `daily011/`; Rücknahme nur der drei genannten Dateien, negativer Bestand erhalten.

## C1-TEMPORAL-011 — eigener Hostlauf abgeschlossen

Geprüft: lokal `ce5bcf6d7c7e87e630b33df86d021dfd4bd52ccd`, remote `9c09a6add6c52f0be01a2e6b18e9cd2fdf4f3cc7`, identischer vollständiger Tree `d27f253b1b922c98c72acb8fc4bdf3b8bdf27e41`. Tatsächlicher Befehl unter `qa/run_c1_gate.py daily-host-01`: `bash tools/temporal-cue-host-check/run.sh /workspace/scratch/b6ee64d418cf/toolchain/gradle-8.11.1/lib`. Vorhandener JDK17, Kotlin2.0.20 und JUnit4.13.2.

Ergebnis20:54:02UTC: **Compiler0, JUnit0, Gesamt-Exit0,29 PASS/0 FAIL/0 SKIP**, vollständige originale Retrieverquelle, unveränderte vorhandene Tests und zwölf neue Grenztests. Quelle vor/nach unverändert, beide Maschinenlocks gehalten. Der ausdrücklich fehlschlagende Compile-Stand-in für den nicht ausgeführten Event-Decorator bleibt eine Host-Prüfgrenze.

Der tatsächliche Lauf meldet weiterhin die bekannten Retrieval-Misses; default Recall@3 bleibt42/44, Theater Rang5. Das ist keine reparierte Gesamtqualität und keine Promotion in den Hauptchat. Rohlog, tatsächlicher Befehl, Exitcode, Quellenstand und Hashes: `docs/evidence/c1-integration-20261104/daily011-own-host/`. Diese nachfolgende Sicherung ist ausschließlich Dokumentation; kein neuer Testlauf auf ihrem späteren Commit wird behauptet.

Nächster Schritt: abgeschlossene14-Dateien-Abschlusszustellung separat prüfen und seriell aufnehmen. Der andere ReferenceCockpit-/UI-Vollsuite-Beitrag ist um20:56UTC noch WIP, ohne finalen Handoff/Ergebnis. Sein bekannter Vollgate-Timeout wird nicht ohne neue Hypothese erneut ausgeführt. Vollständige gemeinsame Gates bleiben unverändert Pflicht. Externer C2 PENDING_ACK.

## C1-SEQUENCE-013 — empfangene Abschlüsse über Darstellungsgrenzen bewahren

Basis: lokal `54f89e8af0f9c1a4db0ca0df4a46c157392a07a4`, remote `0fcdc251147652831d5767ba1a772669a5ca2692`, gleicher vollständiger Tree `cc4a86b18d95fa007fcab60e95fc9e0788820086`. Diese Dokumentationsbasis sichert den separat an ce5bcf6/9c09a6 gebundenen 29-Test-Tagesgrenzenlauf.

Originalquelle #505 `b2bc510a59e29d4ece478109e12b9785be11ee7d`, unveränderlicher Träger #517 `f17ee62f8cfda8914ed217fe2ed88882ba1fa8a4`. Patch SHA256 `23c38bf141923ed53a9403f3502683ecd97584303f38feb54ced791eb41e2cf8`. Genau zwei App-Dateien (`JarvisSequenceView.kt`, `JarvisSequenceTurnLifecycleUiTest.kt`) plus zwölf Dateien des vollständigen vorhandenen `tools/sequence-turn-check`. Alle 14 Basis-/Zielblobs, Hashes und Modi durch C1 abgeglichen; Apply-Check und Anwendung Exit 0. Alle Zielmodi bleiben 100644, auch run.sh wird ausdrücklich mit bash aufgerufen. Temporal-Dateien sind ausgeschlossen.

Nur tatsächlich von dieser Subscriber-Instanz empfangenes TurnEnded wird vor Motion-/Detach-Grenzen historisiert; dessen Hintergrundzustellung nutzt den Main-Handler statt der attachabhängigen View-Queue. Normale visuelle Ereignisse behalten Motion-/Detach- und Generationsschutz. Unfertige Arbeit wird durch clear/detach nicht als abgeschlossen ausgegeben; Memory-Ereignisse behalten ihren getrennten Strom. Die Historie hält weiterhin höchstens 24 IDs. Keine Behauptung eines Replays für nie empfangene Ereignisse oder einer unbegrenzten Queue-/Leak-Garantie.

Die elf vorhandenen UI-Testmethoden bleiben erhalten, vierzehn ergänzen sie auf 25. Originale Rohbelege: Host-RED 25 Fälle/3 Fehler auf dem alten C1-Produktionsblob; Host-GREEN 25/0. Echter Queue-RED mit zwei Fehlern stammt ausdrücklich vom Zwischenstand d24ad51, nicht von C1. Externer echter Queue-GREEN 25/0 bindet einen anderen AvatarContract als C1; deshalb kein übertragener PASS. Alle 26 Originalbelege und ihr Hashindex sind im Intakearchiv gesichert.

Status **OWN_HOST_AND_ANDROID_PENDING**. Nächste Befehle unter beiden Locks: vollständiges vorhandenes Hostwerkzeug mit frischem Ausgabeordner, danach Gradle mit aktivierten UI-Quellen und gezielt `JarvisSequenceTurnLifecycleUiTest` sowie den drei Temporal-/Retriever-Testklassen. Diese Prüfungen ersetzen keine volle Endabnahme. Exakte Pfade, Originalbefehle, Limits und Rollback: `docs/evidence/c1-integration-20261104/sequence013/`. Rücknahme nur der 14 benannten Dateien; vorherige gezielte Kandidaten und negative Belege bleiben erhalten.

## C1-SEQUENCE-013 — eigene Host- und Android-Abnahme

Exakte Quelle für beide Läufe: lokal `38917560671b13795bf85d5a68d329658ba4eb7d`, remote `6c34db7be4068b52de0cef20a0c9142a08db27c0`, identischer Tree `687fe9a8267cb0cd8546acc7818f66fdbf3a1891`. Quellen vor/nach unverändert, beide vorhandenen Maschinenlocks gehalten.

Hostlauf21:00:57UTC: `env CONTINUUM_KOTLIN_COMPILER_LIB=/workspace/scratch/b6ee64d418cf/toolchain/gradle-8.11.1/lib bash tools/sequence-turn-check/run.sh /workspace/scratch/174c84456ee3/qa/sequence-host-01-output`, über eigenen Gate-Wrapper. **Compile0, JUnit0,25 PASS/0 FAIL, Gesamt-Exit0**. Handler/Painter sind ausdrücklich fehlschlagende Host-Fixtures und simulieren keine erfolgreiche Android-Queue.

Danach tatsächlicher Projekt-Gradle-/Robolectric-Lauf21:02:05UTC: `qa/run_c1_gate.py android-sequence-temporal-01`, vollständiger ausführbarer Befehl im Ergebnis-JSON. Aktivierte UI-Quellen mit Projektcompiler2.4 kompiliert; frische XML: **54 PASS/0 FAIL/0 SKIP, Exit0**, davon25 `JarvisSequenceTurnLifecycleUiTest`,15 `TemporalCueCalendarTest`,12 `TemporalCueDailyBoundaryTest` und2 `ContinuumRetrievalExperimentTest`. Die echten Hintergrundthread-/Handler-/Attach-Fälle sind Bestandteil der25Sequence-Tests. Der unterschiedliche ältere externe AvatarContract wird dadurch nicht als C1-Beleg verwendet.

Die54, der Host25 und frühere Temporal29 überlappen; keine Addition. Genaue Fallnamen, Quellen, Befehle, Zeitgrenzen, Rohlogs und XML: `sequence013-own-runs/`, Archiv SHA256 `ac6dfe06beadd62db25c8986cb08c3a5c353d8401fce98b17d5ce644c4f8608a`. Dieses Ergebnis ersetzt weder volle Android-Abnahme noch endgültigen NewApi-/Modell-/Gerätenachweis.

Backend-Vorbereitung: vorhandene Abhängigkeiten nur nach bytegleichem package.json und package-lock.json kopiert; alle633Dateien/Links gegen die Quelle geprüft, kein Netzwerkinstall und kein Backend-Test ausgeführt. Der ignorierte Abhängigkeitsordner verändert keine getrackte Quelle.

Nächster Schritt C1-UI-FIXTURE-015: C1 prüft den bisher extern unvollständigen Vier-Dateien-Entwurf selbst. Konkrete neue Hypothese gegen den belegten Timeout: pausierte VSync mit begrenzter Frame-Fortschaltung und realer Fensteranbindung, bei erhaltenen Assertions und unverändertem Produktionscode. Vier Dateien enthalten fünf Testklassen, einschließlich JarvisAvatarSlotUiTest; alle fünf sind auszuführen. Der WIP-Snapshot wird nicht als fremder abgeschlossener Beitrag oder C2-ACK ausgegeben. Bei unverändertem Fehlschlag keine blinde Wiederholung.

## C1-UI-FIXTURE-015 — bekannte Vollsuite-Blockade mit neuer Hypothese prüfen

Basis: lokal `d4c069038171dcdc6d486b901cd3ecf57c6fb214`, remote `a2659ef221397dcb2c8889046f3ca502e1fd3cbb`, gleicher Tree `cc86705bdc9dc32f45f10e540b1f3526e4810c10`. Die vorangegangene Dokumentationsscheibe sichert den echten 54-Test-Android- und 25-Test-Hostlauf auf 3891756/6c34db7.

C1 nimmt den externen, unveränderten WIP-Entwurf SHA256 `4897b424eec14a6c92ce87dfbc1d0b86cba868d362e3929558fd0cb0cdb1f575` selbst in eine begrenzte Prüfung. Er wird ausdrücklich nicht als extern abgeschlossen oder freigegeben ausgegeben. Neue Hypothese gegen den bereits belegten ReferenceCockpit-Timeout: pausierte virtuelle VSync und begrenzte 16-ms-Fortschaltung verhindern unendliches Leeren zukünftiger Frame-Callbacks. Reale Fensteranbindung und Viewportgrenzen werden dabei zusätzlich geprüft.

Genau vier UI-Testdateien, fünf Klassen: ContinuumReferenceCockpitUiTest, ContinuumTaskEffectsUiTest, ContinuumTaskEffectsCallerUiTest sowie JarvisShellStageUiTest und JarvisAvatarSlotUiTest in einer gemeinsamen Datei. Alle 20 ursprünglichen Testmethoden und vollständigen ursprünglichen Assertion-Ausdrücke bleiben erhalten; 136→147 Assertions. Keine Produktionsquelle oder Produktionsuhr geändert, keine Tests übersprungen oder entfernt. Explizite 2D-/Orb-Wahl ist eine Host-Fixture-Grenze und kein Filament-Nachweis.

Unabhängiger statischer Review durch repair_review und eigene Quellenprüfung ohne statischen Blocker. Alle vier Basis-/Zielblobs und SHA256 nach Anwendung geprüft; Apply-Check und Anwendung Exit 0. **Runtime noch PENDING:** alle fünf Klassen werden mit tatsächlichem Projekt-Gradle/Robolectric und frischen XML ausgeführt. Der eigene Gate-Wrapper v2 beendet einen weiterhin aktiven Test nach 180 Sekunden als FAIL und protokolliert den betroffenen Test sowie den tatsächlichen Prozesscode; nur seine eigene Prozessgruppe wird beendet. Beide Locks und Quellidentität bleiben Pflicht.

Snapshot, Vorzustand, Quellenhashes, unveränderter früherer Timeout und Scope: `docs/evidence/c1-integration-20261104/ui015/`. Rücknahme nur der vier UI-Testdateien. Bei erneutem Fehler neue Diagnose statt unveränderter Wiederholung. Nach erfolgreichem Fokus: gemeinsames Manifest einfrieren und sämtliche verpflichtenden vollen Gates ausführen. C2 extern weiterhin PENDING_ACK.

## C1-UI-FIXTURE-015 — eigener negativer Lauf und begrenzte Folgehypothese

Geprüft: lokal `4da039d55d44f7087a9e4cd7bd72dc5a0089029e`, remote `20d9dd8779c63452815a701b8f5956e426abf4aa`, identischer Tree `974e949a022a7774c6c5c702631217fffb8f8018`. Versuch01 endete vor Gradle mit BLOCKED / Wrapper75 wegen belegter erster Maschinenlock. Der anschließende einzelne asynchrone Versuch02 wartete mit einer protokollierten Obergrenze von900Sekunden; nach tatsächlicher Freigabe beider Locks startete er21:17:42UTC. Keine Sperre umgangen oder gelöscht.

Tatsächlicher Android-Fokus auf unveränderter Quelle: **20 Fälle,6 PASS/14 FAIL/0 SKIP, Exit1**, Ende21:18:18UTC. Alle fünf Klassen und20 START/END-Paare vorhanden, frische vollständige XML. Zwölf Fehler sind NullPointerException im Robolectric ActivityController.windowFocusChanged: die pausierte Uhr hat den ersten realen Fenster-Traversal noch nicht ausgeführt. Zwei Fehler sind NoSuchElementException bei Reflection auf den früheren dreiargumentigen createToolGate; die tatsächliche Produktionssignatur hat inzwischen den vierten optionalen botTask-Parameter. Der erste WIP-Fix ist damit nicht abgenommen. Keine Gesamtfreigabe aus sechs PASS.

Neue begrenzte Hypothese, ausschließlich drei der vorhandenen Testdateien: vor windowFocusChanged einen16-ms-Traversal ausführen und die tatsächliche Decor-Anbindung zusätzlich verlangen; beide Reflection-Aufrufe mit explizitem null als viertem botTask-Argument an die aktuelle Signatur binden. Sämtliche bisherigen Assertions und Testmethoden bleiben erhalten, keine Produktionsdatei und keine ToolGate-Erlaubnis geändert. Ein erneuter tatsächlicher Lauf aller fünf Klassen ist Pflicht.

Originale Rohlogs, alle fünf XMLs, Fallindex, Quellbindungen, beide Wrapperstände, Observer und separate BLOCKED-Beobachtungen: `ui015-own-negative/`; Archiv SHA256 `b6780ea0ae3428e5cca90aaefdf1053389b4e7dc4a5f5eff1e0057602883daed`. Der v3-Wrapper ergänzt nur beobachtbares begrenztes Ressourcenwarten und korrigiert den JSON-Exitcode bei Quellwechsel auf76; Timeouts bleiben FAIL. Der letzte vorherige gezielt geprüfte Stand3891756/6c34db7 mit54Androidfällen bleibt unverändert vorhanden. Rücknahme betrifft nur UI015-Testkorrekturen, negative Belege bleiben gesichert.

## Neue tatsächlich gelesene Fachübergaben / Empfangskorrektur

Die PR-Diskussion von #519 wurde um21:16UTC vollständig gelesen. Frühere Formulierungen über nicht beobachtete ACKs beruhten auf einem unvollständigen Sichtstand der PR-Beschreibungen. Jetzt beobachtet: Gedächtnis-Spezialisten-ACK20:05UTC, fertiger #522-Handoff20:38UTC; Layout-ACK20:38UTC und fertiger #524-Handoff21:03UTC. Externer C2 ist weiterhin PENDING_ACK. Unteragenten sind keine externen Teams.

- C1-MEMORY-017: #522 `eadc72bac95f8b1a554a59e5baff624977b09f73`, tatsächlich extern geprüfte Quelle `3704b62585c6ce6881ab0dfe0c9947cddd1531c2` / Tree `ed4984ba9ac873e081a66d7a7be871f029ef5be5`. Begrenzte Compiler-Contradiction-Privatsphäre und MemoryEventMapper-Zustellung/Zählung plus14neue Tests; eigenständige C1-Aufnahme läuft. Externer Gesamtlauf2638=2618PASS/1FAIL/19SKIP bleibt FAIL; separate Activity2PASS/NewApi0 überlappen. Nicht in C1 übernommen.
- C1-LAYOUT-018: #524 `235d764f790dee7685c6590cf0a5d07d1fafdce2`, Produktionsfix `652e6e30e63a427546c6a30988906c351406be25`. Reale48dp-Floatgrenze, unveränderte strenge Assertions, externer Host-RED/GREEN; Android noch NOT_RUN. Eigenständige C1-Aufnahme läuft, keine gemeinsame Activity-/Memorydatei dort geändert.
- #523 `7c1642af96ccf626c91a40a602c7127e02fb9eea` liefert drei abgeschlossene R1-Optimierungen (Compiler-Bytezähler, Backlog-Wahl, LM1-Auswahl) mit gezielten eigenen Belegen. Separate Aufnahme nach Fehlerkorrekturen; kein pauschaler Merge und keine Modellpromotion.
- #527 `ec7f532dcec57eb44db89dce3976912f2f03b7d0` ist ein neu beobachteter auf C1 gestapelter WIP-Anschluss mit überschneidender #522/#524-Arbeit. Keine zweite gemeinsame Hauptintegration anerkannt: C1 bleibt alleiniger serieller Einbauer, vorhandene Fachpatches werden genau einmal übernommen. WIP-Branch nicht gemergt; fremder Checkout unverändert.
- #528 `bab429e9b0be4c0c2b1705fe9145eb72abbea0cc` ist Forschungs-WIP auf C1, noch kein abgenommener Beitrag. #520s Signed-zero-Reparatur ist bereits funktional im C1-Slice enthalten; dessen übrige nicht ausgeführte Regressions-/Gerätegates bleiben separat. #526 bleibt WIP; keine neue Avatarasset-Promotion aus bloßer PR-Zusammenfassung.

Nächster Schritt bleibt erst UI015-Fokus; danach Memory017 und Layout018 einzeln prüfen/einbauen. Erst anschließend Quellenfreeze und vollständige neue gemeinsame Gates. Kein PASS des früheren oder fremden Quellstands wird auf den dann neuen Kandidaten übertragen.

## UI015 zweiter negativer Checkpoint und C1-MEMORY-017A

UI015 Lauf03 auf lokal `cdea744bc431d56cead71dc6dcbfa287d1903aeb` / remote `a8ebdd9ec935949f5a1aeb1b5c8a6bffd37e3fc4`, Tree `f3827a14469f15088bb1eb64e5d98494aebf850c`, endete21:21:45UTC mit **20Tests/12PASS/8FAIL/0SKIP, Exit1**, Quelle unverändert. Alle früheren Fensterfokus-/Reflectionfehler sind beseitigt; die verbleibenden acht sind ausschließlich ReferenceCockpit-Größen-/Sichtbarkeits-/Rail-Erwartungen. Roharchiv `ui015-followup-negative/`, SHA256 `e5fe963f802e8bf5c7477bab2dcb9edc012291605d82fa01cd274334925fa280`. Unabhängige aktuelle UI-Quell-/Hierarchiediagnose läuft; keine Assertion entfernt, keine unveränderte Wiederholung. Der bekannte negative Zustand ist kein akzeptierter Gesamtkandidat.

Während dieser getrennten Diagnose folgt seriell der erste abgeschlossene Memory017-Teil: **genau AiContextCompiler und zwei neue Frozen-Testklassen**, keine Mapper-/Activity-/Factory-Änderung. Basis cdea744/a8ebdd9 ist oben exakt benannt. Quelle #522 `3704b62585c6ce6881ab0dfe0c9947cddd1531c2`, finale reine Evidenz `eadc72bac95f8b1a554a59e5baff624977b09f73`; Patch SHA256 `1f20a41f828ac8bd5fdcca55e31ada9ebf4831a6f4f2eb70c895d7af8600286a`. Alle Basis-/Zielblobs und SHA256 geprüft, Apply-Check/Anwendung Exit0. Die zehn neuen Tests sind ganze Dateien identisch zu ihrem vor Reparatur publizierten Stand.

Der Compiler löst die ursprünglichen Speicheridentitäten vor beiden Textprojektionen auf. Verbotene oder uneindeutige Quellen werden auch im verpflichtenden Widerspruchsabschnitt zurückgehalten; zulässige Inhalte und IDs behalten vollständige Untrusted-Rahmung. Ein generischer Hinweis erhält den ungelösten Zustand ohne Inhalt/Identität. Exakt gleiche Originalinhalte und mitgelieferte Originalrecords sind Vertragsvoraussetzung; keine Provenienz für beliebig umgeschriebene Notizen erfunden. Bestehender Abschnitts-/Bytegrenzen-Code und Recall-Observer bleiben unverändert; kein #523-Tuning vorgezogen.

Unabhängiger source_audit und Root lasen die tatsächlichen Quellen, Holdouts und ursprünglichen negativen Belege. Extern10Fälle mit6FAIL vor Reparatur und23PASS inklusive13bestehender Tests; diese Zahlen sind keine C1-Ausführung. **Eigener nächster Lauf:** vier tatsächliche Android-Projektklassen AiContextCompilerTest, AiContextIntegrityTest, ContradictionBoundaryRegressionTest und ContradictionBoundaryHoldoutTest; alle neuen und ursprünglichen Assertions bleiben Pflicht. Belege/Hashes unter `memory017-compiler/`. Status OWN_ANDROID_FOCUS_PENDING.

Der Mapper-Teil bleibt eigener nachfolgender Zweischritt017B, danach die fertige Layout018-Korrektur. Die bekannten UI015-Fehler und externe volle2638/1FAIL bleiben getrennt negativ; keine Gesamt-PASS-Übertragung. Rücknahme017A nur der drei genannten Quellen/Testdateien, keine fremden Branches oder Besitzerdateien.

## C1-MEMORY-017A bestanden; C1-MEMORY-017B separat übernehmen

Eigener tatsächlicher Compiler-/Integrity-Lauf auf lokal `57656c6a5287c37a8f4357dabe6d066e63aec235` / remote `f13578bdab05279720fe4a3d6ae0126a208c8465`, identischer Tree `440f395289756ac6ec4f6c26f661c38895e24e12`: **23PASS/0FAIL/0SKIP, Exit0**, Ende21:26:34UTC. Vier ursprüngliche Compilerfälle, neun Integrityfälle und zehn neue Frozen-Fälle; vollständige echte Projektkompilierung der geänderten Quellen. Quellen unverändert, beide Locks gehalten. Alle23Fallidentitäten haben passende START/END und frischeXML. Archiv `memory017a-own-android/`, SHA256 `30fa945036395fa35708b094f4c21b6262708249f49ee1a682f0a83244f4dceb`. Kein voller Android-/Lint-PASS auf diesem Stand. Unabhängiger Quellabgleich bestätigt drei Dateien; Receiptgegenprüfung läuft separat.

Anschließend genau zwei Dateien aus demselben abgeschlossenen #522-Paket: MemoryEventMapper innerhalb `cockpit/MemoryEventSource.kt` und neue unveränderte `MemoryEventMapperLifecycleRegressionTest.kt`. Patch SHA256 `9dd1d877b0f24ce710118a3907728318482f910305dda60c4248da797a238d3b`; alle Basis-/Zielblobs/SHA256 passen, Apply-Check/Anwendung0. Compiler017A bleibt unverändert. Der vollständige extern geprüfte77-Dateien-Intake einschließlich ursprünglichem480-Dateien-Roharchiv wird unter `memory017-complete-intake/` gesichert; SHA256 `d56a8b3a541e02903a201bd26987b3662c8f4eb9d61f8985f541b47fb86c8d27`.

Der Mapper speichert letztes Ereignis und gebündelte Anzahl in einem unveränderlichen atomaren Wert. Vor jeder nachfolgenden Emission wird ein durch vorigen Callback ausgelöstes detach beachtet; kein Mapper-Lock über Bus-Callbacks. Sättigung bei Int.MAX_VALUE, kein globaler Rückrufstopp für bereits begonnene Emission und kein behaupteter Verlust gespeicherter Ledgerdaten. Factory, Interfaces, Hub und Activity bleiben erhalten.

**Eigene Prüfung PENDING:** neue vier Mapperfälle einschließlich80.000Writes sowie bestehende Source-/Factory-/Integration- und tatsächliche MemoryRecallActivityUiTest-Klassen; exakte Auswahl aus Quellinventar in `memory017-mapper/summary.json`. Diese gezielten Fälle ersetzen keine vollständige Endabnahme. UI015s acht ReferenceCockpit-Fehler bleiben separat negativ, deren Diagnose läuft. Rücknahme nur dieses Zweidateien-Slice; vorheriger Compilerprüfstand und Rohbelege bleiben verfügbar. Danach fertiger Layout018-Slice, keine Optimierungspromotion.


## C1-MEMORY-017B bestanden; C1-LAYOUT-018 seriell übernommen

Eigener tatsächlicher Mapper-/Source-/Factory-/Activity-Fokus auf lokal `d3487a83ea1cc61a34afcde4e17601c56def9ed2`, remote `4b6c38902c1ebb93598dd125f5ea0bcc3cddabf8`, identischer Tree `5c9745630cca44da3cb297761c8c59622e6c3a15`: **23 PASS / 0 FAIL / 0 SKIP, Exit 0**, 21:27:55–21:28:20 UTC. Vier neue Mapperfälle, zwei MemoryEventSource, drei RealMemoryEventSource, zwei FactoryIntegration, zehn SourceIntegration und zwei MemoryRecallActivityUiTest. Quelle während des Laufs unverändert, beide vorhandenen Locks gehalten. Der Activity-Fokus verwendet echte Konstruktion und Compiler-/Subscription-/Cleanup-Methoden sowie Android-Handler, ohne vollständiges onCreate/onDestroy oder geladenes Modell.

Alle 23 Quellmethoden, START-/SUCCESS-END-Paare und frischen XML-Fälle stimmen unabhängig überein. Archiv `memory017b-own-android/android-memory017b-01.tar.xz`, SHA256 `23135ea515b55f3e48aa7de976e36271fc7aeac382c4bd58bf19efdeab8ddd7e`; genaue ausführbare Befehle, Exitcodes, Zeitgrenzen und Fallidentitäten stehen im Archiv und `summary.json`. Der unabhängige Quell-/Receipt-Review samt vorheriger 017A-Gegenprüfung ist unter `memory017-independent/` gesichert. Keine neuen Lint-, Vollgate-, UI015- oder Geräteerfolge daraus abgeleitet.

Danach genau der abgeschlossene **C1-LAYOUT-018**-Slice aus #524 `235d764f790dee7685c6590cf0a5d07d1fafdce2`, Produktionscommit `652e6e30e63a427546c6a30988906c351406be25`: `app/src/main/java/at/heinz/deviceai/ui/ContinuumLandscapeCockpitLayout.kt` sowie die zwei neuen Testdateien `ContinuumLandscapeTargetPrecisionChecks.kt` und `ContinuumLandscapeTargetPrecisionTest.kt`. Patch SHA256 `eab1da6e6ed461b068ce90a4d652fc958a840e2c1441bd3c08918e0dba98023a`; Basisblob `6acb79eaf1b90d49037acdfd4a7245e1119a271c`, Ergebnisblob `245c8c94abdadbe065138561d2c9a2901456b3c2`. Vollständige Herkunft, alle Zielblobs, Originaltest und Review unter `layout018/`, Intakearchiv SHA256 `77f8c52f1a6572bdf4bb1c795ffebac743be323c96022a19a8b6baa9803fa301`.

Der Produktionspatch rundet nur einen rechnerisch zu kleinen gemeinsamen unteren Pill-Rand mit Math.nextUp nach außen. Der strenge Mindestwert 48 dp, alle ursprünglichen Tests und der HERO-Ausdruck bleiben unverändert. Eigene Hashprüfung, `git apply --check` und tatsächliche Anwendung jeweils Exit 0; neue und erhaltene Blobs nach Anwendung geprüft. Die fremden Host-RED/GREEN-Belege sind als fremd gekennzeichnet; der nicht archivierte 270-Box-Rohvergleich wird nicht als unabhängig nachgewiesen ausgegeben.

**Status OWN_ANDROID_FOCUS_PENDING.** Nächster Lauf auf sauber gepinntem Ergebniscommit: vier ursprüngliche `ContinuumLandscapeCockpitLayoutTest`-Fälle und drei neue `ContinuumLandscapeTargetPrecisionTest`-Fälle mit tatsächlichem Projekt-Gradle. Anschließend bleibt die vollständige konfigurierte Unit-/UI-Suite mit NewApi erforderlich. Rücknahme nur der drei Layout-/Testdateien; frühere gezielte Prüfstände und negative UI-Belege erhalten. Keine APK, Installation oder Veröffentlichung.

UI020-Diagnose: AgentActivity aktiviert die Jarvis-Shell; alte direkte ElementViews sind dort absichtlich verborgen. Ein Teil der ReferenceCockpit-Tests adressiert diese alte Hierarchie. Zugleich fehlen tatsächlich der Anschluss der Kompaktwahl an die aktive Shell und ein Mehr-Zugang im kurzen breiten Reflow. Diese zwei Funktionen werden nicht durch das Entfernen von Assertions verschwiegen. C1 lässt einen begrenzten Test-/Produktvorschlag mit unveränderter Standard-HERO-Geometrie vorbereiten; keine fremde Activity wird überschrieben.


## C1-LAYOUT-018 bestanden; Quellen- und Zuständigkeitsbelege gesichert

Tatsächliche geprüfte Quelle: lokal `59d8d7e25a5936403e8579373883fa04bec5c37f`, remote `a6135f24629ba9151bbc06888bc977325d95e6f1`, identischer Tree `90d68760d9a0533c169697902b61eda126e9b20b`. Eigener Gradle-Fokus **7 PASS / 0 FAIL / 0 SKIP, Exit 0**, 21:36:46–21:37:07 UTC. Vier ursprüngliche Layoutfälle und drei neue Precisionfälle wurden mit dem tatsächlichen Android-Projektcompiler ausgeführt; zehn eingefrorene HERO-Bounds werden bitgenau verglichen. Beide Locks, unveränderte Quellen und vollständige START/END-/XML-Identitäten geprüft. Archiv `layout018-own-android/android-layout018-01.tar.xz`, SHA256 `8eab7fdf35f9e9b7cddc54d0eb1dd774bac449db9b45db4c940642d7bbfcdbfa`. Befehle und Exitcodes vollständig im Receipt; keine volle Android-/Lint-/GPU-/Geräteabnahme daraus abgeleitet.

Dieser nachfolgende Checkpoint ist reine Dokumentation, keine nachträgliche Testbehauptung auf seinem eigenen späteren Commit. Er sichert zusätzlich die unabhängige SCOPE014-Prüfung bei 3891756 und ihren getrennten 4da039d-Nachtrag, die ausdrücklich nur vorbereitende FREEZE016-Vorlage und OWNERSHIP019. Die Vorlage pinnt einen älteren Quellenstand und Wrapper v2; der tatsächlich verwendete Wrapper ist v3. Für die endgültige Abnahme müssen Quelle, Quellmanifest und Wrapperhash neu gebunden werden.

OWNERSHIP019 vergleicht #523 `7c1642af96ccf626c91a40a602c7127e02fb9eea`, #527 `d2aedf2c272bfa20d4e1930a0f65277c3f02be0f` und #528 `bab429e9b0be4c0c2b1705fe9145eb72abbea0cc` mit C1 d3487a8. Vollständige 60-Pfad-Matrix unter `ownership019/`. #527 enthält Memory017 und Layout018 bereits identisch, aber ältere UI015-Fixtures; kein pauschaler Import. #523 wird als drei getrennte Optimierungen vorbereitet, Compiler nur als enger Hunk auf dem erhaltenen Datenschutzstand. #528 enthält bislang keine Produktionsdatei und keine konkrete Dateireservierung. #515 ist inzwischen auf `9423d5fb6978496ec843d0f7bcbe8285ee63677f` fortgeschritten; der neue Nacht-Handoff wird vor jeder Übernahme erneut geprüft. C1 allein integriert gemeinsame Einstiegspunkte.

Drei historische Boardlinks verweisen jetzt auf die unabhängig gelesenen Originaldateien in #498 `e95076c2a396a785ae80bceca0610890ff1af5d9`; historische Aussagen bleiben unverändert. AGENTS.md übernimmt ausschließlich die bereits auf #494/#499 veröffentlichte, bytegenau geprüfte Zweiparagraphen-Korrektur des bewusst öffentlichen Projektstatus: Blob `511396c8e88ef0b9811b791d6c8bccc10f868b26` → `219e2123b275b99872fa70315a486b61b9922ab1`. Provenienz im Memory017-Intake (Originalquellen 93f1f1130a3963062ad4c43c4a1edfe0cf7e2759 und 999225dc3823cd027c06adf2847e7fec9f772cf4). Daraus entsteht keine neue Autorisierung; der direkte aktuelle Besitzerauftrag autorisiert das Sichern der beauftragten Projektarbeit bereits. Geheimhaltungs-, Signatur-, CCC1-/ToolGate- und Main-Grenzen bleiben erhalten; Sichtbarkeit nicht verändert.

Rücknahme dieses Checkpoints betrifft nur Dokumentation und Belege. Geprüfte Produktquelle 59d8d7e/a6135f2 bleibt als eigener Commit erhalten. Nächster Einbauschritt ist UI020 mit eingefrorenen funktionalen Tests für die aktive Shell, anschließend begrenzter Produktfix und unabhängige Gegenprüfung. UI015 bleibt negativ; keine bestehende Anforderung senken. Externer C2 weiterhin PENDING_ACK.


## Kritischer Host-Bestand grün; C1-UI-020 Tests vor Produktänderung eingefroren

Eigener vollständiger critical-Lauf auf lokal `681bdec2527c6a5ef4c773e8211cf62e4ac4ad06`, remote `11ed97e237f4f939b38e61b53d8942d6b3e22b49`, Tree `1ab9ee719626c15a8510e10e478f5e7da4a41a2b`: **135 PASS / 0 FAIL / 0 SKIP, Exit 0**, 21:42:08–21:42:42 UTC. Tatsächlicher Befehl `node scripts/continuum-reliability-qa.mjs critical --out /workspace/scratch/174c84456ee3/qa/critical-preui020-01-output` unter Wrapper v3 und beiden Locks. Der Lauf wartete zuvor regulär auf die vorhandene erste Sperre; kein fremder Prozess verändert. Vier Pflichtgruppen: process 37, storage-agent 32, android-source-contracts 64, host-checker 2. Frische XML, Hashes, konkrete Fälle und Quellenbindung geprüft; `continuum-qa-evidence.mjs node ... critical ...` Exit 0. Archiv `critical-preui020/critical-preui020-01.tar.xz`, SHA256 `703ba8bb3d84230a251830fd346931ed6315af67f26868e6d65664f225a6a857`.

Dieser critical-PASS bewertet den Bestand vor UI020. Die alten 4 critical- und 32 full-node-Fehler bleiben mit ursprünglichen Quellen erhalten; kein späterer full-node- oder Android-PASS behauptet. Der erste reine Archivierungscode stoppte an einem NOT_RUN-Metadateneintrag mit KeyError, bevor ein Archiv entstand. Er wurde auf die vier ausdrücklich ausgeführten Pflichtgruppen begrenzt und erhält alle fünf NOT_RUN-Bereiche; Tests wurden dafür nicht wiederholt.

C1-UI-020: **nur** `app/src/uiTest/java/at/heinz/deviceai/ContinuumReferenceCockpitUiTest.kt` angepasst, kein Produktcode. Freeze des Lieferanten 21:40:57 UTC, Patch SHA256 `fdd1d7bccba6c400ccaa2edc0fad8224b83da9dfcbf1be9488a07327829f9b7e`; Basisblob `4fa09ef1fe3a6fe88b707e8150611a066c01c72d`, Testdatei-Ziel SHA256 `b162dda889669fb6818b9e35114a1874f7991d3df3d0d2f83da9b937e9748547`. Basis auf aktuellem 681bdec bytegleich zu 59d8d7e; Hash-/Apply-Check und Anwendung Exit 0. Vollständiges Vorher/Nachher, Patch und Testhypothese unter `ui020-tests-first/`. Produkt wurde am Freeze noch nicht vorbereitet.

Alle acht ursprünglichen Testmethoden bleiben. Aktuelle Shell-Tags ersetzen die unsichtbare Legacy-Hierarchie. Die funktionalen Anforderungen bleiben bestehen: alle vier Profile mit drei Schriftgrößen, reale Decor-Taps statt performClick, mindestens 48 dp und nicht überlappende Targets, alle elf aktuellen Rail-Ziele, tatsächlicher Mehr→Entwicklung→Coding-Entwurf, Original-Orb/2D/3D/Bewegung/Schließen, explizite Kompaktwahl mit wirklich kleinerem aktiven Slot und Inhalt, Persistenz/Neuerstellung und exakte Rückkehr zur normalen Geometrie, gleiche Avatarinstanz bei Navigation sowie Entwurf/Reflow/Rotation/Stop. Bestehender Inhalt wird nicht durch bloße Zähländerungen freigegeben.

**Status OWN_RED_PENDING.** Erwarteter RED am unveränderten Produkt: fehlende vierte Chooserwahl und fehlender sichtbarer System-Mehr-Eintrag. Andere echte Fehler werden anhand der tatsächlichen Ausgabe untersucht, nicht vorweg als Testfehler erklärt. Nächster Befehl: alle acht ReferenceCockpit-Fälle mit tatsächlichem Projekt-Gradle und aktivierten UI-Quellen. Unabhängiger Testreview läuft; danach begrenzter getrennt reviewter Produktpatch und dieselben eingefrorenen Tests. Rücknahme dieser Testscheibe nur der einen Testdatei; negative Originale und vorherige Produktquelle erhalten.

Zusätzlich ist der unabhängige Layout018-Receipt-/Scopebeleg unter `layout018-independent/` gesichert: zwei frische XML, sieben vollständige Fallidentitäten und Remote-/Local-Treegleichheit bestätigt. Externer C2 bleibt weiterhin PENDING_ACK.


## C1-UI-020 — echter erster RED vollständig gesichert

Unveränderte geprüfte Quelle: lokal `f31855138d77ecb5e05e7a17a620b1fd6e9d28c5`, remote `c65604c85855a8dfb2e17617feafe83b7d560115`, gleicher Tree `323227c1c05017b0733509e24f0aebc2cc9faa25`. Regulärer Lock-Warteplatz ab 21:45:39 UTC, tatsächlicher Start erst nach beiden Locks 21:51:17 UTC; Ende 21:51:51 UTC. **8 Fälle = 1 PASS / 7 FAIL / 0 SKIP, Exit 1.** Kein Timeout, keine veränderte Quelle. Alle acht START-/END-/XML-Fälle vollständig gesichert unter `ui020-own-red/`, Archiv SHA256 `191c9330db35639c1bb9bfea11f6dfaec0adfd25f683bb4d4105865de9ecc966`. Exakter ausführbarer Gradle-Befehl, Wrapper und Rohberichte liegen im Archiv. Produkt weiterhin unverändert gegenüber dem vorherigen Layoutstand.

Tatsächliche Befunde:

- Die native Darstellungsauswahl enthält nur die drei bisherigen Radiooptionen, nicht die geforderte zusätzliche Kompaktwahl. Dieser erwartete RED ist tatsächlich erreicht.
- Beide Fold-Profilmethoden finden die aktive anklickbare `jarvis-panel:STATUS`-Fläche mit nur 39 dp Höhe. Mindestwert 48 dp bleibt Pflicht; eigenständige begrenzte Hitboxkorrektur wird geprüft, normale gemalte Referenz-/HERO-Geometrie nicht dafür verschoben.
- Die beiden Phone-Profilmethoden scheitern vorher an einem Nulltag im neuen diagnostischen `tap`-Label. Das ist ein Testhilfsfehler, keine bestandene Navigation und kein Produktfehlernachweis.
- Die tatsächliche Activity-Neuerstellung endet im internen Robolectric `ActivityController.recreate` → `windowFocusChanged` vor dem neuen Fenster-Traversal. Der komplette Lifecycle und gespeicherte Entwurf bleiben Pflicht; eine begrenzte, anhand des Controllers geprüfte Fensterfokus-Fixturekorrektur folgt separat.
- Der kurze Wide-Test erreicht Chat, scheitert beim anschließenden Rückweg aber an einem noch 0 px breiten Home-Ziel. Ob dort im manuell dimensionierten Fixture eine erneute Decor-Messung fehlt, ist zunächst eine neue Hypothese und keine vorweggenommene Produktentlastung.
- Der schmale Read-only-Capability-/Stop-Darstellungsfall besteht. Das belegt keine echte lokale Inferenz oder ausgeführte Modellabbruchkette.

Der sichtbare System-Mehr-Fallback wurde durch diesen RED noch nicht bis zu seiner fehlenden Aktion erreicht; seine Abwesenheit bleibt an die Quellprüfung gebunden. Der unabhängige V1-Testreview ist unter `ui020-test-independent/` gesichert. Der getrennte fünfdateilige Produktvorschlag `fb3799bfc0b9045d72563dd03ac9e24c0ea262c9920b42bbd02af2f5965ff12d` liegt als **PROPOSED_NOT_APPLIED_NOT_RUN** unter `ui020-product-proposed/`. Er wurde vor Kenntnis dieses RED eingefroren. V1-Rohdaten und Testbytes werden nicht überschrieben; jede Folgeversion nennt die neue konkrete Hypothese. Kein PASS übertragen, keine Anforderungen entfernt.

Neue aktuelle Fachquellen: #527 ist auf `5410060fcd1558c899fc160c08232f9ed948b8f7` vorgerückt. Dessen vorheriger gesamter Androidstand `ca80459a3f6695c8fecff4a217fbe93318452121` ist mit 3011 Fällen, 2984 PASS / 8 FAIL / 19 SKIP weiterhin FAIL; der neue fremde UI-Fokus läuft laut Handoff. Seine Kompaktprüfung betrifft ausdrücklich nur die Legacy-Fixture und ersetzt den aktuellen Shell-Anschluss nicht. Die separat beobachtete Andockreparatur aus lokalem Ursprungscommit `a3f46349ad66db8362fee35a87338334c1838e83` wird als C1-AVATAR-SLOT-023 geprüft, ohne Gesamtbranchimport.

#529 `1070b10df50025828a9fd2b18bf1c187c094979b` ist ein tatsächlicher neuer spezialisierter Nachtauftrag auf gefrorenem #522. Reserviert sind ausschließlich `AiContextCompiler.redactSecrets`, `MemoryWritePolicy.decide` und eigene Regressionen. Fremde 20-Fälle-RED-/35-/53-Host-Ergebnisse sind beobachtet, ein zusätzlicher x-token-Holdout und die gemeinsame Android-/Event-/Orb-Kette noch nicht endgültig übergeben. Kein Import, kein eigener PASS. Die getrennte OPT021-Bytezähleroptimierung darf diesen künftigen Redaktionshunk nicht überschreiben. Bestehende Teams bearbeiten ihre eigenen Dateien weiter; C1 bleibt gemeinsamer serieller Einbauer.

Nächster Schritt: neue Fixturehypothese vor Produktänderung einfrieren, erneut exakt acht echte Fälle prüfen, dann den getrennt reviewten Produktfix einschließlich belegter Status-Zielgröße seriell anwenden. Die danach nötigen vollständigen gemeinsamen Gates bleiben unverändert. Rücknahme nur begrenzter UI-Slices; frühere gezielte Produktprüfstände und sämtliche negativen Belege erhalten. Externer C2 PENDING_ACK.


## C1-MISSING-022 — abgeschlossenen Scheduler-Anschluss mit echtem RED/GREEN übernehmen

Während die getrennten UI020-Fixture-/Statushypothesen quellengeprüft werden, folgt ein eigenständiger fertiger Worker-Slice. Ausgangs-C1: lokal `445807c4ee97c59122a6c98a08866fa932ca24d7`, remote `cff4bdc0963111c81c991d3cc38c7ca3ed64a103`, Tree `8df03efe1e56233cbb24a5f59057aa21a29efd54`. Der UI020-Produktvorschlag ist weiterhin nicht angewendet; dessen fünf App-Basisblobs bleiben unverändert.

Quelle ist ausdrücklich der abgeschlossene #515-Stand `5373ec7c1399cb34fb8896f7318fdfa5437fa283`, nicht der neuere aktive Nachtumbau. Genau zwei Pfade: `.github/continuum/local-teamleader-loop.mjs` und neue `.github/continuum/local-teamleader-loop-source-preflight.test.mjs`. Patch SHA256 `79b7beb43a946c839b93fa7fde6c4371bbc60d64a6eea264894938f6c66fb247`. Produktionsblob `3dc5d63077fce0244e2bdeeb20b310aa762d2cfa` → `f83e6a1dcd40e7841734f893895766c6c3b1ed29`; unveränderter neuer Testblob `13c5661432115c883fc2b64161757aad9d1128f4`. Quellhash-/Apply-Check und tatsächliche Anwendung Exit 0.

C1 führte zuerst die vier eingefrorenen Regressionen gegen die exakte alte Modulquelle aus: **4 Fälle, 0 PASS / 4 FAIL, Exit 1**, 21:59:53–21:59:54 UTC. Alle vier scheitern fachlich daran, dass die abgelehnten temporären fsmonitor-/Filter-Canarys tatsächlich ausgeführt werden. Danach dieselben Testbytes gegen das exakte Zielmodul: **4 PASS / 0 FAIL, Exit 0**, 22:00:07–22:00:08 UTC; jeweils keine Skips/Abbrüche/TODOs. Beide tatsächlichen Läufe hielten beide vorhandenen Locks und ließen den C1-Quellstand unverändert. Sie verwendeten vollständige minimale Node-Module mit realem Git und temporären Prozessen; keine simulierte erfolgreiche Quellenprüfung.

Exakte Befehle `node --test --test-concurrency=1 --test-reporter=tap <red-oder-green>/.github/continuum/local-teamleader-loop-source-preflight.test.mjs`, Eingabequellen, Node-/Git-abhängige Fixtures, Rohlogs und unmittelbare Exitcodes sind in `missing022/` gesichert. Archiv SHA256 `7b30398f74e4fee1e5ea73186c2c91582fa6a0b014c4694d989769d6208fa6b9`. Die historischen 183 Fälle enthalten diese vier und die bestehenden 22 bereits; keine Addition.

Die vorhandene dauerhafte Schleife akzeptiert optional einen vertrauenswürdigen asynchronen `sourcePreflight`. Sie wartet ihn vor erster Git-Statusprüfung sowie vor und nach jedem Zyklus ab; Ablehnung verhindert Quellhelfer und Erfolgsannahme. Ungültige Callbackwerte werden abgewiesen. Der bestehende Checkpoint-/Budget-/Lock-/Ergebnisvertrag bleibt erhalten. **Die heutigen vier C1-Aufrufer binden diesen Hook noch nicht:** dies ist ein fertiger Anschlussbaustein, keine Aktivierung eines Branchbots und keine allgemeine OS-Sandboxgarantie. Keine Branch-Discovery-/Cursor-/CLI-/Lock-Export-Datei übernommen.

**Nächster eigener integrierter Fokus PENDING:** dieselben vier plus die 22 unveränderten `local-teamleader-loop.test.mjs`-Fälle auf dem sauber gepinnten neuen Commit. Danach weiterhin vollständige gemeinsame Gates. Rücknahme nur der zwei ausgewählten Dateien; bevor später Caller gebunden werden, deren Abhängigkeit prüfen. Main, produktive Arbeitszweige, Ownerdateien, Offline-/CCC1-/ToolGate-Grenzen bleiben unverändert.


## C1-MISSING-022 integriert geprüft; C1-OPT-021-WORKER einzeln übernommen

Der eigene integrierte Lauf `missing022-integrated-02` prüfte lokal `9a442485f2569b39be2bcd8d6687da6f6c15c45d`, remote `0d8a3d46c409e5918c49b6f3407bb63f3519b846`, vollständiger identischer Tree `8fe54137e976c718c91a4b3bbcfc02d50ca52bdd`: **26 PASS / 0 FAIL / 0 SKIP / 0 CANCELLED / 0 TODO, Exit0**, 22:12:09–22:12:10UTC, Quelle unverändert. Tatsächlicher Befehl: `node --test --test-concurrency=1 --test-reporter=tap .github/continuum/local-teamleader-loop-source-preflight.test.mjs .github/continuum/local-teamleader-loop.test.mjs`, unter Wrapper v3 mit beiden vorhandenen Locks. Alle26 konkreten TAP-Fälle und die drei Quellenblobs sind gesichert. Archiv `missing022-integrated/missing022-integrated-02.tar.xz`, SHA256 `b12189e67bbdfcc77d7d983833052d60b88dfdf3ffc129cb8627c53f4facd87a`. Die vier neuen Fälle sind in26 enthalten, ebenso22 frühere; nicht mit vorherigem4er- oder fremdem183er-Lauf addieren. Vier vorhandene Caller liefern weiterhin keinen optionalen Callback; kein aktivierter Branchbot und kein kompletter Autonomie-/Modell-/Android-PASS. Unabhängige Quellen-/Receiptprüfung abgeschlossen: exakt4neue+22ursprüngliche Fallnamen, Original-TAP-/Receipt-Hashes und18Archivmitglieder geprüft; `missing022-independent/` enthält den vollständigen Review. Optionale Veto-Semantik ausdrücklich nur throw/rejectedPromise; null oder erfülltesfalse blockiert nicht.

Der erste Wartebeleg `missing022-integrated-01` bleibt unverändert archiviert: letzterHeartbeat22:04:31, kein gestarteter Prozess im Receipt, kein Exitcode; um22:10:17 meldete die Ausführungsverwaltung die Session41550 unbekannt. Kein Testergebnis ableitbar. Erst danach einmaliger neuer Lauf02 auf derselben sauberen Quelle; keine fachlich erfolglose Prüfung unverändert wiederholt. Quelle und vorheriger geprüfter Stand bleiben erhalten.

Als eigener nächster Schritt **C1-OPT-021-WORKER**: genau `.github/continuum/development-backlog.mjs`, zugehörige bestehende Testdatei und `tools/completed-connections-822e-r2/worker/selection-parity.mjs`. Quelle #523 getesteter Remote `972f7d46312885eeff41df7f771f4700b2619b0b`, finales Evidenzhandoff `7c1642af96ccf626c91a40a602c7127e02fb9eea`; enger Patch SHA256 `65abb18658fa30cd01a8e6c40fe1547c3aaa175198387a4881e0d50e173e045d`. Alle drei Basis-/Zielblobs geprüft; Apply-Check/Anwendung Exit0. Intakearchiv `opt021-worker/opt021-worker-intake.tar.xz`, SHA256 `8c64554eb8d041358db7594809cd59143aaf2e84d622777c55b36f55b813972d`.

`nextEligible` behält jetzt in einem Durchlauf das beste zulässige Originalobjekt. Reihenfolge unverändert: höhere Priorität, älterer Zeitstempel, kleinere ID. Kanonische validierte Dokumente erzwingen eindeutige IDs, sichere Zeitstempel und begrenzte Prioritäten. Eligibility, Filterreihenfolge, Abhängigkeiten, Completion-Evidenz, Budgets, Claims und Dateisperren bleiben erhalten. Vier eingefrorene Zusatztests ergänzen die ursprüngliche Datei ohne gelöschte Assertion; der originale Paritätsharness vergleicht40.960 Entscheidungen mit Basisblob `64f798d5cf86ec7b541bd5135972f30fef1be746`. Keine Benchmarks oder Performancezahl übernommen.

**OWN_FOCUS_PENDING:** zehn bestehende Suiten (historisch121 Fälle, neu tatsächlich zählen), dann separater Paritätslauf. Das Intake wurde vor Missing022 vorbereitet; dessen Loop-Dependency hat inzwischen Blob `f83e6a1dcd40e7841734f893895766c6c3b1ed29` statt `3dc5d63077fce0244e2bdeeb20b310aa762d2cfa`. Dieser Unterschied wird explizit mitgeprüft und nicht durch alte externe PASS verdeckt. Rücknahme nur der drei OPT021-Worker-Pfade; vorheriger26er-Prüfstand bleibt bestehen. Compiler-Bytezähler und LM-Sampler bleiben getrennte nicht angewandte Vorschläge.

UI020 V2 ist **BLOCKED / NICHT ANGEWANDT**: Die vorgeschlagene manuelle SavedState-Neuerstellung verliert `isChangingConfigurations`; AgentActivity verzweigt tatsächlich bei DialogueRetention und Modell-Unload darauf. Trotz gleicher Assertions wäre der Rotationsstimulus schwächer. Root und unabhängiger Reviewer lehnen diese Ersetzung ab; V3 wird gegen den tatsächlichen Robolectric4.14.1-Bytecode vorbereitet. Original-V1-RED, Produktvorschlag und STATUS-Reparatur bleiben erhalten, ohne vererbten PASS.


## C1-OPT-021-WORKER bestanden; UI020 V3 als unveränderte Abnahme eingefroren

Eigener Zehn-Suiten-Lauf auf lokal `6f7ca725261eb4df502ee868e94babc4e12afc15`, remote `4d74de6c5ca04582ff455356440046e9148acbb0`, vollständiger identischer Tree `d197c1eb872b7b01e78e3150c6eeb0b9381810f9`: **121 PASS / 0 FAIL / 0 SKIP / 0 CANCELLED / 0 TODO, Exit0**, 22:15:40–22:15:54UTC. Danach auf exakt derselben unveränderten Quelle der separate `node tools/completed-connections-822e-r2/worker/selection-parity.mjs`: **40.960 identische Entscheidungen**, Exit0, 22:16:25–22:16:26UTC. Beide Läufe hielten beide bestehenden Locks, sourceUnchanged=true. Vollständige Befehlsargumente, alle121 Fallnamen und Originalausgaben in `opt021-worker-own/`; ArchivSHA256 `28f6d1a2f1be18f7a8b57c39bd4bf0842c1cafe7a0b1adece934302b74a17ed8`.

Die40.960 Vergleiche sind synthetische Aufgabenidentitätsentscheidungen, keine weiteren40.960 Runtime-Tests; sie werden nicht zu121 addiert. Referenzblob64f798d5, Kandidat-SHA256d0cb6841 und Harness-SHA256be2cc8a0 stimmen mit eingefrorenen Originalen. Kein Benchmark, keine neue Laufzeit-/Geräteleistung behauptet. Unabhängiger Review prüfte beide Rohreceipts,121 eindeutige Fälle, sämtliche Zielblobs und17 Abhängigkeiten; Archiv `opt021-worker-independent/own-worker-review.tar.xz`, SHA256 `48bd257166d30109d682bd67a5e1b2c5e3842c94f4f34ee46fe5538249ec166e`. Die erwartete Missing022-Loopabweichung wurde tatsächlich über dessen bestehende Suite mitgetestet.

**UI020 nächster Hypothesenschritt, nur ein Testpfad:** `ContinuumReferenceCockpitUiTest.kt` V1-Blob `315f42d32518f0c8de4a73cb46c349bd8dbb04d6` → V3 `5059be0cd5f3d4e850eaac6d08faca357e1429fd`; PatchSHA256 `aa9433cd051d077eac9371f4f75d7c966b88b1dc0125cd22abd5f646fe92709c`. Apply-Check/Anwendung0. Produkt weiterhin vollständig unverändert. V2 wird nicht angewendet.

V3 behält `ActivityController.recreate()` vollständig und ergänzt eine einmalig registrierte öffentliche Lifecycle-Callback-Brücke: exakt die neue resumierte Activity stellt eine asynchrone Main-Handler-Nachricht zu. Im nachfolgenden nativen visible()-Drain wird die pausierte VSync einmal16ms weitergeschoben, ohne unpause oder nested-idle. Der echte Traversal muss den Decor anbinden, bevor originaler Fensterfokus folgt. Harte Guards verlangen den neuen Controllerinhalt, Parent vor Puls, genau einen Puls, tatsächliche Zerstörung, isChangingConfigurations, neue Instanz, Attachment und echten Fokus. Callback und Puls werden im finally entfernt. Nulltag-Fallback ändert nur die Assertion-Beschriftung; die neu sichtbare Chat-Rail wird vor echtem Decor-Tap neu gemessen. Alle acht Methoden, Profile,48dp-Grenzen und funktionalen Anforderungen bleiben bestehen.

**OWN_ANDROID_RED_PENDING:** Einmaliger neuer8er-Lauf gegen unveränderte App mit PNG-/Viewtree-Capture, nach Bytecode-Gegenprüfung. Er soll die Fixturehypothese prüfen und die tatsächlich fehlende Kompakt-/Mehr-/STATUS-Funktion weiter rot zeigen. Kein GUI-, Rotations- oder Produkt-PASS vor tatsächlicher Ausführung. Rücknahme nur der V3-Teständerung; V1 und alle negativen Belege bleiben verfügbar. Danach begrenzte Produktkorrektur und eigener erneuter Fokus. #529s fertiger Datenschutz-Slice wird unabhängig aufgenommen; keine gleichzeitige Compileränderung.


## UI020 V3 tatsächlich geprüft; begrenzte Produktanschlüsse seriell angewandt

Eigener `android-ui020-v3-red-01` auf lokal `5fec06d587dcceded3d066bf3eeded888255fd2f`, remote `08370482c609537b5b8d0d8370d2b4a933d4e35d`, Tree `0b31bb65445866f0bd0844ee84bdfb4a00a649d0`: **8 Fälle / 1 PASS / 7 FAIL / 0 SKIP, Exit1**, 22:21:52–22:22:25UTC, Quelle unverändert, beide Locks. Originale8 Fallidentitäten und START/END/frischeXML vollständig geprüft. Archiv `ui020-v3-own-red/android-ui020-v3-red-01.tar.xz`, SHA256 `d960f2882a4fedeceb9f61de06ddf01db0eeadf90bb03c7d612b1fe513c5362e`. Der erste Archivaufruf wurde wegen absolutem statt gefordertem Basename-Argument vor jedem Schreiben zurückgewiesen; korrigierter Archivaufruf0, kein Test erneut ausgeführt.

**Neues tatsächliches Ergebnis:** Der Recreate-NPE ist beseitigt. Der Originalpfad passiert jetzt wechselbedingte Zerstörung, Neuinstanz, genau einen Puls und echte Decor-Anbindung; die anschließende zusätzlich geforderte `hasWindowFocus`-Assertion ist noch negativ. Ein neuer, konkreter Fokus-Dispatch-Verdacht wird gegen echte SDK-/Robolectric-Quellen geprüft. Die Nulltag-Ausnahme ist ebenfalls beseitigt; Phone/Narrow erreichen jetzt die nächste negative Grenze: Tap auf Entwicklerwerkzeuge öffnet noch nicht das erwartete Entwicklungszentrum. Shortwide Home bleibt trotz Neuvermessung0dp breit. Diese drei Punkte werden nicht als bereits repariert ausgegeben. Die eigentlichen Produkt-RED bleiben fehlende vierte Kompaktwahl und zwei39dp-STATUS-Ziele.

Sechs aktuelle Host-Capturedateien sind unverändert archiviert (`host-captures.tar.xz`, SHA256 `289865d452e3f9c220965ff917e4bf1832a954d5b986a2e5e3460e9c0f4bf85f`); Root hat Phone-Home und Narrow-Stop visuell gelesen. Sie zeigen tatsächliche native Host-Views/2D-Rendering, keine native GPU oder physisches Gerät. Der unabhängige V3-Quellreview bestätigt alle125 ursprünglichen Assertions und12zusätzliche, acht Methoden und exakte ursprüngliche Rückrekonstruktion. ArchivSHA256 `9d859d8711bded599d985eb0ebe8001bd87c1aef1f72c019a0652634dbe42fec`; rein statisches Urteil wurde durch diesen negativen Lauf nicht in PASS umgedeutet.

Danach C1 alleiniger Einbau des bereits vorab geprüften **UI020-Produktvorschlags**: genau `ContinuumHologramView.kt`, `ui/ContinuumHomeView.kt`, `ui/JarvisShellView.kt`, `ui/JarvisShellLayout.kt` und `AgentActivity.kt`. PatchSHA256 `fb3799bfc0b9045d72563dd03ac9e24c0ea262c9920b42bbd02af2f5965ff12d`, sämtliche fünf Basis-/Zielblobs geprüft. Derselbe bestehende Darstellungsdialog erhält die ausdrückliche Kompakt-Kugelwahl; vorhandene Präferenzen und dieselbe Livefläche werden verwendet. Ein einzelner Activity-Callback aktualisiert die aktuelle Homeansicht und wird beim Destroy gelöscht. System erhält einen Navigationseintrag zum bestehenden Mehr-Dialog. Normale Referenz-/Phone-Geometrie bleibt unverändert; explizites Compact nutzt120dp Livefläche und zieht im Phone nachfolgenden Inhalt vor. Wide-Compact kann wegen Reflow mehr vertikalen Platz benötigen; keine universelle Platzersparnis behauptet.

Unmittelbar danach separat der zwei-Hunk-STATUS-Fix nur in Home: PatchSHA256 `d01d4f47a68f4a5951cae28262847f6eec119ebd1153ac249b31c5588a337f53`, Homezwischenblob `977f50f5304677aaaf32f48b4cd0e96794948e8a` → `8d643cff16d37c69ca93618cb464b4c94e0ae691`. Native Statusaktion mindestens48dp, Ausdehnung ausschließlich nach unten in vorhandenen Footerraum; gezeichnetes Grid, HERO und andere Ziele bleiben erhalten. Beide Apply-Checks und beide Anwendungen0. Herkunft und unabhängige Geometrieprüfung unter `ui020-product-applied/`, STATUS-Intakearchiv SHA256 `5502457ebd7360b184b0bcdffdc8920653ef5a0fd9d81674259e62f2e1389538`.

Der V3-Testblob `5059be0cd5f3d4e850eaac6d08faca357e1429fd` bleibt unverändert. **OWN_PRODUCT_FOCUS_PENDING:** Referenz8 plus bestehende ShellStage3, ShellLayout9, ShellContent4 und CockpitGrid7; tatsächliche Zähler und neue Fehler aus XML, kein vorweggenommenesGREEN. Die drei bekannten übrigen Grenzen bleiben offen und werden nicht durch Auslassen ihrer Tests verdeckt. Rücknahme nur dieser fünf Produktdateien beziehungsweise STATUS-Hunks; voriger negativer Prüfstand und sämtliche geprüften früheren Slices erhalten.
