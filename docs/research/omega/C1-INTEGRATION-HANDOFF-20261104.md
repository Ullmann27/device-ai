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
