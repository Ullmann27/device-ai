# Astra55 – gemeinsame Unterstützungs-Pipeline

## Auftrag und Ziel

Bestehende virtuelle Arbeitsfläche nach Opus' Übergabe unterstützen. Keine neue Produktlinie, kein zweiter Renderer, keine weitere Aufgabenverwaltung. Grundlage: `CODE55-UEBERGABE-CHATGPT-ASTRA-20261112.md` und `ASTRA55-COCKPIT-INTEGRATION-20261112.md`.

Hauptintegrator bleibt der Chat **Cockpit 3D Arbeit**, Zielzweig `work/astra55-cockpit-integration-20261112`, [PR #799](https://github.com/Ullmann27/device-ai/pull/799); übergeordnete Code55-Linie #791. Dieser Support-Zweig ist ausschließlich Zulieferung und Prüfung.

## Zuständigkeiten

| Hauptteam | Unterstützer | Zulieferung |
|---|---|---|
| `work/astra55-live-20261112` | `support_live` | Frame-/Sitzungsbindung, Stop und Rechteentzug, Fensterziel und Aktionsnachweis |
| `work/astra55-scene-20261112` | `support_scene` | Filament-Anschluss, Originalassets, Layout und Rendernachweise |
| `work/astra55-workflow-20261112` | `support_workflow` | Auftragsparser, Ereignisvertrag, Stop und Bewegungsanschluss |
| Hauptintegrator | Support-Koordinator | Befunde priorisieren, Datei-Verantwortung abgleichen, reproduzierbare Prüfungen zuliefern |

## Ablauf

1. Implementierer behält seine Produktionsdateien und seinen aktiven Arbeitsbaum.
2. Unterstützer prüft einen benannten Commit oder ausdrücklich gekennzeichneten WIP-Snapshot; Befund nennt Pfad, Wirkung und Reproduktion.
3. Zuweisung einer Reparatur nennt genau einen Dateibesitzer. Ohne bestätigte Übergabe kein paralleles Bearbeiten derselben Produktionsdatei.
4. Unterstützer liefert einen reproduzierbaren Test und kleinen Reparaturdiff gegen einen eingefrorenen Quellstand. Der Hauptintegrator prüft und übernimmt ihn in seine gemeinsame Linie.
5. Alle schweren Gradle-Läufe teilen die bereits verwendete Build-Sperre. Der genaue Pfad wird zuerst mit dem Hauptteam bestätigt; bis dahin keine konkurrierenden Gradle-Läufe.
6. Nach Prüfung wird der Befund als `OPEN`, `REPRODUCED`, `FIXED_SOURCE`, `HOST_VERIFIED` oder `DEVICE_VERIFIED` fortgeschrieben, mit Quell-SHA und tatsächlichem Testbeleg.

Die Grenzen `PORTIERT`, `HOST-VERIFIZIERT` und `GERÄTEBESTÄTIGT` bleiben getrennt. Ein Hostbild ist kein Filament-/Fold7-Nachweis. Nur der Hauptintegrator führt Gesamtprüfung und APK55-Abschluss zusammen.

## Austausch und Handshake

- Veröffentlicht: bestehende Kommentare in [PR #791](https://github.com/Ullmann27/device-ai/pull/791) und [PR #799](https://github.com/Ullmann27/device-ai/pull/799).
- Die lokale technische Mailbox enthält die drei getrennten Review-Zulieferungen. Rechnerbezogene Pfade bleiben außerhalb dieses versionierten Dokuments.
- Gegenseitige Bestätigung ist erst nach tatsächlicher Antwort vermerkt. Eine lokale Mailbox allein sendet keine Chatnachricht.
- Erster veröffentlichter Auftrag: [Unterstützungs-Pipeline](https://github.com/Ullmann27/device-ai/pull/791#issuecomment-6822614294).
- Konkrete Gegenproben: [Review am Hauptintegrations-PR](https://github.com/Ullmann27/device-ai/pull/799#issuecomment-6822730856).

## Erster Prüfabschluss

Die drei Unterstützungsagenten haben ihren ersten Review samt reproduzierbaren Patchkandidaten geliefert. Opus' Übergabe und die vorhandenen Zweige sind gelesen. Dateiübernahme und genauer Gradle-Lock-Pfad: Bestätigung ausstehend. Gegenproben liefen direkt mit dem Kotlin-Compiler auf der JVM, ohne Gradle. Ergebnisse und offene Integrationsfehler: [Supportbericht](ASTRA55-SUPPORT-REPORT-20261112.md). Noch keine eigene neue Gesamtprüfung, APK oder Fold7-Abnahme.

**Veröffentlicht mit ausdrücklicher Besitzerfreigabe vom 12.11.2026:** [Support-PR #802](https://github.com/Ullmann27/device-ai/pull/802), Ziel #799. Der veröffentlichte Commit `f24c56c067512e1084abc2644de56b177346add0` besitzt exakt denselben Dateibaum `e3bc9cd38d669775acd8cc18ecae770776f1765e` wie der geprüfte lokale Paketstand `8ef308b678d5b51d89d96717cdf1a5f3c5d8af0d`.

Die frühere automatische Ablehnung betraf die Veröffentlichung vor dieser ausdrücklichen Freigabe. Nach Freigabe scheiterte nur noch der CLI-Push an fehlenden Zugangsdaten; die Veröffentlichung erfolgte über den verbundenen GitHub-Zugang. Historische `NOT_PUSHED`-Angaben in den einzelnen Prüfberichten bezeichnen ihren damaligen Übergabestand. Patchübernahme und Empfangsbestätigung bleiben eigenständige, noch nicht behauptete Schritte.

Aktive Experimente bleiben EXP-05, EXP-06, EXP-11 und EXP-12; dieser organisatorische Anschluss ändert keine Laufzeitarchitektur und keine Freigaberechte. Rücknahme: Support-Commits nicht übernehmen bzw. einzeln revertieren. Hauptteam-Zweige bleiben erhalten.
