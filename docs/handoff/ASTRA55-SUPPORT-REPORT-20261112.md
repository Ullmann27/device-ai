# Code55 – Unterstützung des virtuellen Cockpits

## Ergebnis

Drei unabhängige Unterstützungsagenten haben die drei laufenden Implementierungsbereiche geprüft. Opus' Weiterbearbeitungsauftrag bleibt die Grundlage. Es wurde keine zusätzliche Produktfunktion entwickelt und keine aktive Produktionsdatei des Hauptteams verändert.

Hauptintegration: [PR #799](https://github.com/Ullmann27/device-ai/pull/799), `work/astra55-cockpit-integration-20261112`, danach Code55 / #791. Die [Reviewübergabe](https://github.com/Ullmann27/device-ai/pull/799#issuecomment-6822730856) wurde erfolgreich veröffentlicht. Eine Antwort des Hauptteams auf die Empfangs- und Zuständigkeitsabstimmung liegt beim Erstellen dieses Berichts noch nicht vor.

## Drei getrennte Zulieferungen

| Bestehender Bereich | Unterstützer | Geprüfter Reparaturvorschlag | Ergebnis |
|---|---|---|---|
| Live-Bild und Android-Bedienung | support_live | Fensteridentität bei Wirkungsprüfung; unsichere Aktionen bei vollem Journal weiterhin sperren | 32/32 ausgewählte Tests grün; separater Scroll-Gegenfall bleibt rot |
| Arbeitsereignisse und Bewegung | support_workflow | Keine alten Beobachtungen für neue Schritte, keine rückläufige Revision, keine Pause durch verspätetes Fremdergebnis | 8/8 Gegenproben plus 10/10 vorhandene Nachbartests grün |
| Filament und Cockpit-Layout | support_scene | Mindesthöhe breiter Szenen an Schriftgröße binden | 28/28 Geometrie-/Navigationsprüfungen grün; sieben Standardschrift-Geometrien identisch |

Die Zahlen werden nicht zu einer Gesamtsuite addiert: Ein Teil der Nachbartests läuft in mehreren Prüfbereichen. Alle Ergebnisse sind begrenzte JVM-/Kotlin-Nachweise. Kein neuer Gradle-Gesamtbuild, kein Android-Filament-Render und kein Fold7-Test wurden dadurch ausgeführt.

## 1. Live-Bild und Bedienung

Ausgangsquelle `5574282e9f2e6a8e726be81ec257073d4c723f77`. Der unveränderte Kandidat wurde anschließend auch auf dem eingefrorenen Folgecommit `8a71c46a1aeecc76ab9bb97076a6e6a39c11545b` geprüft: wieder 32/32 ausgewählte Tests grün, Scroll-Gegenprobe separat weiterhin rot.

Der Reparaturkandidat verändert nur `LiveComputerActions.kt`:

- Ein Wechsel der Fenster-ID kann keinen Erfolg im ursprünglichen Fenster mehr bestätigen.
- Eine unsicher beendete Aktion wird nicht durch Verdrängung aus einer begrenzten Sperrmenge erneut ausführbar. Bei erschöpfter Unsicherheitskapazität bleibt der Auftrag gesperrt.

Ausgangslauf mit Nachbarn: 33 Tests, drei Fehler. Kandidatenlauf: 32 ausgewählte Tests bestanden. Der ausdrücklich separat ausgeführte Scroll-Gegenfall bleibt fehlgeschlagen. Die vorhandenen Tests wurden nicht abgeschwächt: Frames 11, Actions 10 und VerifiedNodeClick 6 bestanden unverändert.

**Weiter offen:** Ein geändertes Badge im Knotenbaum bestätigt derzeit einen Scroll, obwohl der Container sich nicht bewegt haben muss. Der bestehende Port benötigt einen an Container, Fenster, Sitzung und Ausführungszeit gebundenen Bewegungsbeleg. Der Kandidat deaktiviert die Scrollfunktion nicht pauschal.

Ein zusätzlicher Quellbefund betrifft die Ereignisanmeldung: Der betrachtete Accessibility-Service und seine XML-Konfiguration berücksichtigen nicht `TYPE_VIEW_SCROLLED` und `TYPE_VIEW_TEXT_CHANGED`. Wenn eine App ausschließlich diese passenden Ereignisse liefert, kann der bestehende Wartepfad seine neue Revision nicht erhalten. Dieser Befund wurde nicht auf Android ausgeführt.

Aktuelle Frame-Gegenproben bestanden: geschütztes Bild trotz Bildratenbegrenzung, Zeitreihenfolge nach Pause, genau einmalige Ressourcenfreigabe nach Stop. Diese vorhandenen Verbesserungen werden dem Hauptimplementierer zugerechnet.

Belege: [Live-Bericht](../evidence/astra55-support-20261112/live/README.md), [Reparaturdiff](../evidence/astra55-support-20261112/live/repair.patch).

## 2. Arbeitsereignisse und Bewegung

Ausgangsquelle `bf7a52ceaccc85ecc3ac5df8ed6b7af9af33b6a6`.

Der kleine Kandidat verändert ausschließlich `CockpitWorkEvent.kt`, fünf ergänzte und zwei ersetzte Zeilen. Er verhindert:

- Schritt B wird mit der früheren Beobachtung von Schritt A als bestätigt dargestellt.
- Ein bereits angenommener Beobachtungsstand 12 wird durch Stand 11 ersetzt.
- Ein verspätetes unklares Ergebnis von A pausiert den bereits laufenden Schritt B.

Vor Reparatur: acht Gegenproben, drei Fehler. Mit Reparatur: acht bestanden; zehn vorhandene Nachbartests bleiben grün. Stop, fremde Aufgabe/Sitzung, doppelte Schrittausführung und reine Suche ohne Sendeschritt sind enthalten.

Der zunächst gefundene Satzzeichenverlust im Nachrichtenentwurf wurde inzwischen vom Hauptimplementierer korrigiert. Die Gegenproben für `!`, `?` und `.` bestehen auf der eingefrorenen Quelle. Dafür wurde kein weiterer Patch erstellt.

**Zusätzlicher WIP-Hinweis:** `ComputerWorkAvatarBridge` bildet Bewegungs-IDs aus Sitzung und Schritt, ohne Aufgaben-ID. Da ein neuer Auftrag seine Schritte erneut nummeriert, kann der zweite Auftrag in derselben Sitzung den ersten Bewegungsimpuls verlieren. Die Aufgaben-ID gehört in diese vorhandene Identität. Dieser Hinweis ist quellbasiert, noch nicht ausgeführt und kein Bestandteil des geprüften Kandidaten.

Belege: [Workflow-Bericht](../evidence/astra55-support-20261112/workflow/README.md), [Reparaturdiff](../evidence/astra55-support-20261112/workflow/repair.patch).

## 3. Szene und Layout

Ausgangsquelle `080d7f4df9a7b355b1edbe5f876a35bc0d834338`.

Bei 884 × 760 dp und 200 Prozent Schrift verschwindet das Sekundärfeld durch negative Höhe; Dialog und Dokumentfläche überlappen um 70,7 dp. Der Kandidat passt nur die Mindesthöhe breiter Szenen an die Schriftgröße an.

Geometriematrix: 28 Prüfungen, vor Reparatur zwölf Fehler, mit Kandidat null Fehler. Die Matrix betrachtet 320/360/600/640/700/720/884 dp und Schriftgrößen 100/150/200 Prozent. Sieben ausgegebene Standardschrift-Geometrien bleiben identisch. Es wurden vollständige Layoutklassen und unveränderte Dock-Geometriemethoden verwendet; Zeichenmethoden und Android-Touchfolge wurden nicht ausgeführt.

**Weitere Befunde:**

- Der direkte Mehr-Header fehlt zwischen 600 und 703 dp. Die ausgelagerten Ziele bleiben über den vorhandenen Status-/Mehr-Weg erreichbar; vollständiger Funktionsverlust ist nicht belegt.
- Im 2D-Ersatzpfad wird die überlappende Kugel nach dem Kopf gezeichnet; dessen Transparenz folgt noch nicht dem nativen Kopfpfad. Reparatur durch den vorhandenen Darstellungsbesitzer, kein ungeprüfter Grafikpatch dieses Supportlaufs.
- Vorhandene UI-Tests erwarten noch elf Dockziele bzw. das entfernte `bottom:system`. Sie müssen auf acht direkte und drei Mehr-Ziele angepasst werden, unter Erhalt der bisherigen Schutzprüfungen.
- Originalavatar, Runtime-Pin und Kugelrenderer wurden geprüft und sind im betrachteten Vergleich erhalten.
- Neue Monitor-/Filament-Bausteine entstehen bereits im Hauptteam. Ihre unfertige Zwischenverdrahtung wird nicht als gescheiterte Fertigimplementierung gewertet.

Belege: [Szenenbericht](../evidence/astra55-support-20261112/scene/REPORT.md), [Reparaturdiff](../evidence/astra55-support-20261112/scene/repair.patch).

## Übernahme und Arbeitsgrenzen

Die drei Diffs sind Zulieferungen gegen bezeichnete Quellen. Alle drei bestanden einen lesenden `git apply --check` auf den zuletzt beobachteten jeweiligen Arbeitsständen. Live- und Workflow-Diff bestanden diesen Check auch auf dem gemeinsamen Integrationsstand `c2079299b0f04e523196bb08cca1e64a9e527828`. Dieser Check ändert keine Datei und ist kein Buildnachweis.

Der Hauptintegrator übernimmt nur nach Abgleich mit seinem inzwischen erreichten Stand. Neue Änderungen im betroffenen Quelltext erfordern erneute gezielte Prüfung; keine fremden Arbeitsbäume überschreiben. Die abgegrenzten Reparaturen lassen sich einzeln zurücknehmen. EXP-05/06/11/12 und bestehende Freigabepfade bleiben erhalten.

## Speicher- und Veröffentlichungsstatus

**Supportpaket: LOCAL_ONLY / NOT_PUSHED.** Der automatische Freigabeprüfer hat den Push eines Koordinationsdokuments wegen interner Arbeitsbereichspfade und öffentlicher Veröffentlichung abgelehnt. Die Ablehnung wird nicht durch einen anderen Agenten oder Übertragungsweg umgangen. Dieser Bericht und die Tests/Patchkandidaten sind lokal vorbereitet; eine Veröffentlichung des Pakets benötigt die ausdrückliche Freigabe des Besitzers.

Die oben verlinkten bestehenden PR-Diskussionen enthalten die auf Projektbefunde begrenzte Aufgabenabstimmung und wurden erfolgreich veröffentlicht. Eine gegenseitige Empfangsbestätigung, Integration der Kandidaten, volle Android-Prüfung und Gerätebestätigung werden nicht behauptet.
