# Astra55 – virtuelle Arbeitsfläche

## Verbindlicher Einstieg

- Basis: `1b90611388cc2b1978ddfe32064a25dd3a08cee1`, Code55 / PR #791.
- Integrator: Astra, `work/astra55-cockpit-integration-20261112`.
- Ziel: reale, sitzungsgebundene Android-App-Bilder auf dem vorhandenen Cockpit-Monitor; geprüfte Aktionen treiben Avatar und Ton.
- Experimente: EXP-05, EXP-06, EXP-11, EXP-12. Bestehende Modell-, Aufgaben-, Ereignis- und Freigabepfade bleiben maßgeblich.

## Arbeitsaufteilung

| Verantwortung | Entwicklungszweig |
|---|---|
| Bildquelle, Fensterbindung, Android-Aktionen | `work/astra55-live-20261112` |
| Raum, Originalavatar/Kugel, Monitor, responsive Navigation | `work/astra55-scene-20261112` |
| Auftragsablauf, gemeinsame Ereignisse, Bewegung/Ton | `work/astra55-workflow-20261112` |
| Integration, Activity, Dialog-Reparaturen, Gesamtprüfung | `work/astra55-cockpit-integration-20261112` |

## Vorhandene Kandidaten

Opus A `f07d7e924`, B `1d8440b3b`, C `0b4e56dae` + `f04836790`, D `92027cea3`, E `f8df180a7` sind gesichert und in die Integration übernommen. Die in D ursprünglich fehlenden Speech-/Activity-Änderungen wurden ergänzt; der RED-PROBE-Retrywert wurde korrigiert. Der ursprünglich isolierte Button-Renderer aus E ist an echte Navigation angeschlossen.

## Implementierter Stand

- Originalavatar und Originalkugel erhalten; eigener Raum-/Monitorrahmen, Stirnposition der Kugel, transparenter Avatar, freier Kinn-/Schulterbereich.
- Acht farbige echte Navigationsziele: Fold 1×8, Phone 2×4. Zusätzliche Ziele bleiben erreichbar.
- MediaProjection mit eigenem GPU-Fensterausschnitt; alternativ einzelne Accessibility-Fensterbilder. Neueste Bilder, Sitzungs-/Fenster-/Geometriebindung und kein Modellaufruf pro Frame.
- Auftragsparser, existierender App-Finder/ToolGate, konkrete Freigabe, Startnachweis und temporäre exakte Eingabeberechtigung durchgängig verbunden.
- Reale Such-, Kontakt-/Entwurfs- und Download-Schritte mit Nachprüfung. Suchfelder werden über tatsächlich beobachtete Rolle/ID erkannt.
- Gemeinsame Ereignisse treiben Blick, Körperimpulse, leisen Klick und bildgebundenen Klickring. Kein künstliches Tippen während Wartezeiten.
- Aufnahme-OK bleibt beim ursprünglichen Auftrag und startet erst in sichtbarem `onResume`. Abbruch, Stop und verspätete Antworten sind gebunden.
- PDF-Zusammenfassung verwendet das bereits geöffnete Dokument. Ein App-Treffer führt ohne überflüssige Mehrfachauswahl zur Freigabe. Gerätezeit wird direkt gelesen; typische unerbetene Mundart wird am fertigen Text geprüft.

Die vollständige technische Bauanleitung und tatsächliche Funktionsgrenzen stehen in [CODE55-VIRTUELLER-COMPUTER.md](../architecture/CODE55-VIRTUELLER-COMPUTER.md).

## Prüfstand während Integration

Erster abgeschlossener kombinierter Fokuslauf auf Quellstand `779b7459ad01424020f8c4797d47173d97d6f71f`: **278 Tests, 270 bestanden, 8 fehlgeschlagen, 0 übersprungen**. Fünf Fehler stammen aus alten Navigationserwartungen des gemeinsamen UI-Testhelpers, drei aus alten Einzeltreffer-Erwartungen. Die nachfolgenden Korrekturen werden erneut im Gesamtstand geprüft. Maschinenlesbarer Nachweis: `docs/evidence/astra55-20261112/focused-initial.json`.

Grün in diesem Lauf: AndroidLiveNodePort, LiveComputerFrames/Actions, LiveWindowSafety, ProjectionCropGeometry, Such-Klickvertrag, Parser, Workflow, TaskGrant, CaptureConsent, PDF-Dialog, Speech-Recovery, Geräteuhr, Mundartprüfung, Cockpit-Ereignisse, Layout, Monitor-Pixel und drei Android-Canvas-Ansichten. Diese Tests verwenden synthetische Daten und bestätigen keine echte App auf einem Gerät.

Einzelne zusätzliche JVM-Prüfungen der Agenten sind in ihren Übergaben dokumentiert. Sie ersetzen keinen erneuten kombinierten Lauf auf dem integrierten Stand.

Zwei frühere Gradle-Versuche endeten mit unerwartetem Daemon-Ende ohne Testergebnis. Der erste vollständige Lauf gelang mit 1 GiB Gradle-Heap, 768 MiB Test-Heap, einem Worker und regelmäßig neuem Testprozess. Eine Betriebssystem-OOM-Ursache ist damit nicht bewiesen. Der Lauf wurde mit Buildphasen und Speicherwerten protokolliert.

Der Native-Budgetreview hat zusätzlich eine doppelte Preface-Zählung und eine unpraktisch strenge Byte=Token-Schranke gefunden. Die laufende Korrektur muss gegen den echten LiteRT-LM-0.18-Eingabepfad bewertet werden; keine exakte Tokenizer-API behaupten, die das SDK nicht anbietet.

## Abnahme

1. Originalpaket und Originalassets bewahren.
2. Gebundene Bildquelle, frische Beobachtung nach jeder Aktion, Stop/Widerruf und unsichere Wirkung prüfen.
3. Fold7- und Phone-Layout tatsächlich rendern; Hostdarstellung ausdrücklich als solche kennzeichnen.
4. Gezielte Prüfungen, danach kombinierte Android-Suite und unsignierter Code55-Build.
5. Gerätetest separat: WhatsApp öffnen, Kontakt suchen, Ergebnis sehen; keine Nachricht ohne passenden Auftrag/Freigabe.

Integrationsstatus: **WIP mit ersten Hostnachweisen**. Vollsuite und unsigned Code55-Build werden auf dem abschließenden Quellstand nachgetragen. Fold7: **NOT_RUN**. Keine Signierung oder Installation. Kein Merge nach `main`.

## Verbleibende Grenzen

- Android-Filament, reale Aufnahme, OEM-Split-Screen, konkrete App-Ansichten, Wärme und Laufzeit sind auf dem Fold7 noch nicht geprüft.
- WhatsApp-Entwurf ist implementiert; Versand ist nicht angeschlossen. Facebook/Word/Excel können angezeigt werden, aber ein allgemeiner Editor für beliebige Word-/Excel-Inhalte ist nicht implementiert.
- PDF-Bezug über Activity-Neuerstellung ist abgedeckt. Nach vollständigem Wegwischen ist die Dokumentbindung noch nicht dauerhaft im Chat gespeichert. Der konkrete Anschluss an den bestehenden TranscriptStore steht in `docs/research/omega/ASTRA55-PDF-FOLLOWUP-HANDOFF.md`.
- Ausgabeprüfung erkennt ausgewählte häufige Mundartmuster; sie ist keine vollständige deutsche Grammatikprüfung und keine sachliche Wahrheitsprüfung.
- Host-GLB-Render und Android-Canvas-Bilder werden ausdrücklich als Hostnachweise beschriftet. Keine erzeugten Beispiel-App-Bilder werden als reale Aufnahme ausgegeben.

## Reproduzierbarkeit

Gradle-Läufe seriell mit gemeinsamer `agent-gradle.lock`; Ergebnisse und Quell-SHA hier nachtragen. Bei Unterbrechung zuerst tatsächlichen Remote-HEAD, Arbeitsbaum und diese Datei prüfen. Rückfall ist die unveränderte Code55-Basis; bestehende Zweige werden erhalten.
