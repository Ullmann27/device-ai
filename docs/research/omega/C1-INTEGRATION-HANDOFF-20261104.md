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
- [ ] Fertige Regression in eigener Kopie gegen unveränderten Kandidaten ausführen; erwartete fachliche RED-Ursache archivieren.
- [ ] Genau den kompatiblen abgeschlossenen Slice seriell übernehmen.
- [ ] Fokussierte Node-Verträge und unabhängige Negativkontrollen ausführen; keine Ausweitung bei rotem Ergebnis.
- [ ] Quellen, tatsächliche Befehle/Exitcodes, Logs, Grenzen und Rollback committen und remote per exakter Tree-Identität sichern.

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
