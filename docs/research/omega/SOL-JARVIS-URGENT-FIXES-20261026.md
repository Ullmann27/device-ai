# Schnelle priorisierte Grobfehler-Reparatur nach Besitzer-Folgeauftrag

26.10.2026. Folgeauftrag bei gemeldeten 10% Restkontingent: nur die gröbsten
bekannten Fehler beheben, eng prüfen und im bestehenden Draft-PR #169 sichern.
Basis: `8261e57942f0e9d7b472b4e2dbac68ffb688f391`.

## 1. Höchste Priorität: keine Modellarbeit ohne dauerhafte Backlog-Buchung

Der bestehende Android-Pfad rief den Coordinator trotz fehlgeschlagenem
`devBacklog.startJob` auf. Der neue kleine Coordinator-Einstieg `runWithBacklog`
ordnet Buchung und Modellarbeit zusammen: fehlgeschlagene Buchung liefert einen
terminalen Fehler mit null Modellaufrufen. `AgentActivity.startDevelopmentJob`
verwendet diesen Einstieg; bestehende Ergebnis-/Busy-/Backlog-Abschlusslogik bleibt
angeschlossen. Erfolgreicher Token wird weiter an den bestehenden Finisher gereicht.

Native JVM-Regression vor Fix: 2 Tests, davon 1 FAIL; tatsächlich 1 unerlaubter
Modelladapteraufruf statt 0. Nach Fix: **2/2 PASS**. Der echte UI-Controller geht
nach dem Fehlermeldungsergebnis zurück in FAILED mit erneut erlaubtem Start.
Der Modelladapter ist eine deklarierte Testfixture; keine LiteRT-Inferenz und
keine Android-Geräte-/vollständige SDK-Kompilierungsbehauptung.

## 2. Zweite Priorität: keine grüne Teilmenge aus 401 Android-Testberichten

Der feste isolierte Profilrunner schnitt Berichte still auf 400 ab. Jetzt bricht
er vor der Ausgabe einer Teilmenge mit `ISOLATED_REPORT_COUNT_EXCEEDED` ab.
Bis zu 400 gültige Berichte werden weiterhin vollständig verarbeitet.

Reproduktion mit echtem vorhandenen Docker-Executor und festem Produktionsrunner:
vor Fix fehlte die erwartete Ablehnung bei 401 Berichten (FAIL), danach erfolgreiche
Ablehnung und positiver Kontrolllauf mit exakt 400 Berichten. Gradle/JDK/SDK-
Capabilities sind ausdrücklich synthetische Testfixtures; dies ist **kein**
Android-/Gradle-Build. Keine Sandboxgrenze gelockert, kein unisolierter Fallback.

## Belege und Wiederaufnahme

`docs/evidence/sol-jarvis-20261026/urgent/` enthält ursprüngliche RED-Ausgaben und
GREEN-Ausgaben. Gezielter Node-Lauf mit realem Reportlimit-Docker-Test und bestehenden
Executor-/CCC1-/Android-Wiring-Regressionsprüfungen: **47 PASS / 0 FAIL / 0 SKIP**.

Der reproduzierbare gemeinsame JVM-Runner enthält jetzt zusätzlich die zwei
Claim-Gate-Tests: **17 JVM-Tests und 9 tatsächliche JVM→Node-Vertragsprüfungen
bestanden, jeweils ohne Fehler oder Skip.** Geprüfter und bereits gepushter
Code-Commit: `3e8e0ef7341fe1886f160c53b1949a7f1ac763c7`.
Abschließender kompletter Node-Lauf auf genau diesem Code:
**1352 PASS / 0 FAIL / 7 SKIP**, 1359 gesamt, Node v24.19.0, Exit 0,
92,572 Sekunden. Der echte Docker-Reportlimit-Test war dabei eingeschaltet.
Die sieben Auslassungen sind wie zuvor sechs ältere `kotlinc`-PATH-Tests und
der separat tatsächlich ausgeführte gemeinsame JVM→Node-Test.
Exakter Befehl und Quell-SHA: `urgent/full-native.json`; vollständige Ausgabe:
`urgent/full-native.tap`. Keine verbleibenden fehlgeschlagenen Tests in diesem Lauf.
Bestehender Branch bleibt offen, keine
Änderung an Main, keine APK/Installation oder produktive Modellfreigabe.

Die bisher offenen Punkte 1 und 2 in der großen Abschlussübergabe sind damit auf
Quell-/Regressionsniveau repariert. Die übrigen Integrationslücken bleiben offen:
geschützte semantische Oracles, Chat→Coding, produktiver Registry-Aufrufer,
mobile Isolation, echte lokale Modellinferenz und Android-/Besitzer-APK-Abnahme.

Weiterentwicklung beginnt nun beim bislang dritten Punkt (geschützte semantische
Oracles und danach die fehlenden produktiven Verbindungen), nicht erneut bei den
beiden hier geschlossenen Reproduktionen. Die Owner-/Sandbox-/Review-Gates bleiben
maßgeblich. Vollständige Android-SDK-Kompilierung und Fold7-Abnahme sind weiterhin
NOT_RUN; der neue Coordinator-Einstieg und UI-Controller sind nativ JVM-geprüft.
