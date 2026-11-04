# Drei Reparatur-Bots — zweite Runde

Stand: 04.11.2026, Europe/Vienna. Besitzerauftrag: „Weiter“.

## Ausgangspunkt und Auftrag

Diese Runde setzt den vorhandenen isolierten [PR #505](https://github.com/Ullmann27/device-ai/pull/505) fort. Gepinnter veröffentlichter Ausgangsstand ist `4ddbcb092d955226bb83c2a10f7ac52bb34f59eb`, Baum `b9b256d7280f28f4ff750557e26b9cbe596f04bd`; der lokale, inhaltsgleiche Commit ist `63dec3b1296b1d4257310296de3781d75b7a266a`. Der ursprüngliche gemeinsame Integrationsstand bleibt #491 bei `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`.

Der Koordinator hat PR #505, die aktuell offenen PRs und Issue #41 frisch gelesen. #512 übernimmt bereits die Reparaturen der ersten Runde auf dem neueren Integrationszweig #506; #513 bearbeitet weitere Anschlüsse. #511 optimiert andere vorhandene Funktionen. Diese Arbeiten werden nicht überschrieben oder als eigene Ergebnisse gezählt. Die Prüfung ihrer Kombination bleibt getrennt von unseren Quellprüfungen.

## Verteilte Arbeit

| Bot | Prüfauftrag | Schreibbereich |
| --- | --- | --- |
| Memory/Ranking | Den dokumentierten Rückgang von Recall@3 43/44 auf 42/44 nach der Kalenderkorrektur erklären und, falls sachlich begründet, allgemein reparieren. Kalendergrenzen, unveränderte Corpusdaten und bisherige Schwellen bewahren; keine fallbezogenen Ranking-Hacks oder Hauptchat-Aktivierung. | Bestehender `ContinuumMemoryRetriever`, passende Tests/Hostrunner und eigene Belege. |
| Android-QA/Build | Echte lokale SDK-/Abhängigkeitsverfügbarkeit frisch prüfen und die blockierte Android-Abnahme möglichst ermöglichen. Nur bereits erlaubte Ressourcen verwenden; andernfalls genaue fehlende Komponenten und einen überprüfbaren Fortsetzungsweg festhalten. | Build-/Prüfskripte, zugehörige Tests und eigene Belege. |
| UI/Ereignisse | Konkrete Anschlussfälle bei Bewegungsabschaltung, Detach und Wiederaufnahme untersuchen. Verspätete Ereignisse dürfen erledigte Aufträge nicht wieder öffnen; legitime laufende Aufträge dürfen nicht künstlich als erledigt gelten. Nur reproduzierte Fehler korrigieren. | `JarvisSequenceView`, zugehörige Tests/Hostrunner und eigene Belege. |

Drei echte Unteragenten wurden in getrennten Arbeitsbäumen gestartet. Es werden keine dauerhaften Android-Worker oder externen Modellaufrufe eingerichtet.

## Prüf- und Kompatibilitätsvertrag

- Status dieses Startcheckpoints: **WIP / UNVERIFIED**. Die Testergebnisse der ersten Runde sind historische Belege und keine neue Ausführung.
- Vor jeder Reparatur: konkrete Ursache und reproduzierender fehlschlagender Fall. Danach relevante bestehende sowie neue Negativtests; abschließend unabhängige Prüfung und kombinierter Quellstand.
- Keine Änderung bestehender Testdaten oder Mindestanforderungen, um einen Fehler verschwinden zu lassen. Beobachtete Corpusfragen sind keine unangetasteten Holdouts. Neue allgemeine Negativfälle werden getrennt ausgewiesen.
- Bisheriger Android-Blocker: `com.android.application:8.10.0` im Offline-Cache nicht verfügbar. Ein Hostcheck mit Android-Ersatzkomponenten ist kein Gradle-/Robolectric-/Fold7-Nachweis.
- Betroffene Architekturverträge: EXP-00/08 (echte Toolchain und gebundene Nachweise), EXP-06 (belegter Abruf), EXP-07 (unabhängige Prüfungen), EXP-11/12 (bestehende Auftragsidentität und Wiederaufnahme). Keine zweite Memory-DB, Worker-Pipeline oder Geräteverbindung.
- Paket, Version, Signatur, Updatekanal, Besitzerwissen, Modellgewichte, Offline-Steuerung und ToolGate bleiben erhalten. Kein Main-Merge, keine Signierung, Installation, Veröffentlichung, GitHub Actions oder kostenpflichtige API/Runner.
- Fremde Arbeitskopien und aktive Zweige bleiben unberührt. Wegen knappen gemeinsamem Plattenspeicher wurden ausschließlich saubere eigene Arbeitskopien der abgeschlossenen ersten Runde entfernt; ihre Commits und veröffentlichten Belege sind erhalten.

## Abschluss und Rücknahme

Die nächsten Commits müssen konkrete Änderungen, Quellidentitäten, Testbefehle/Exitcodes, unerreichte Prüfungen und verbleibende Nachteile nennen. Nutzbare Schritte werden früh in diesem offiziellen PR gesichert. Jede Produktionsteiländerung bleibt einzeln rücknehmbar; der veröffentlichte Ausgangsstand ist die Rückfallreferenz. Ohne neue echte Gerätebelege bleibt die Fold7-Abnahme offen.

## Verifizierter Zwischenstand: Android-Belege aus der Integration

Der Koordinator hat #512 bei `f9267a3743a87a77bf6421011c014b2ca2d6b0d6` direkt gelesen. Dessen Quellcheckpoint `52fbfc624c9a2846c1da571d330327c7874b1639` enthält alle drei Produktionsdateien der ersten Reparaturrunde byte-identisch. Der Bericht nennt 305 bestandene Tests in 45 Klassen auf diesem kombinierten Stand sowie NewApi-Lint ohne Befund.

Für unsere übernommenen Regressionen wurden die originalen XML-Dateien persönlich überprüft: **11/11 `JarvisSequenceTurnLifecycleUiTest` in echtem Robolectric und 15/15 `TemporalCueCalendarTest` bestanden**. Testfallzahlen, null Fehler/Übersprünge, Quellidentitäten und die Hashes von XML, Zusammenfassung und Gradle-Protokoll stimmen mit dem veröffentlichten Manifest überein. Der ursprüngliche `NOT_RUN`-Status dieser elf UI-Fälle ist damit auf dem genannten kombinierten Integrationsstand überholt.

Das ist ein Abgleich fremder veröffentlichter Laufbelege, keine neue Ausführung unseres Koordinators. Die Gesamtzahl 305 wurde mit dem Receipt abgeglichen; die beiden eigenen importierten XML-Suiten wurden einzeln geprüft. Neue Quellen der zweiten Runde, Fold7, Filament/GPU, hörbare Sprache und APK-Abnahme sind dadurch nicht geprüft. [Maschinenlesbarer Abgleich](../../evidence/three-repair-bots-round2-20261104/coordinator/integration-512-reconciliation.json).
