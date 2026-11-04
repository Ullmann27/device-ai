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
