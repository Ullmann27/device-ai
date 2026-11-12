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

Opus A `f07d7e924`, B `1d8440b3b`, C `0b4e56dae` + `f04836790`, D `92027cea3`, E `f8df180a7` sind gesichert. Sie werden anhand ihrer tatsächlichen Änderungen übernommen und neu geprüft. In D fehlen die im Bericht beschriebenen nativen Speech-/Activity-Änderungen. E enthält bislang nur den nicht angeschlossenen Button-Renderer.

## Abnahme

1. Originalpaket und Originalassets bewahren.
2. Gebundene Bildquelle, frische Beobachtung nach jeder Aktion, Stop/Widerruf und unsichere Wirkung prüfen.
3. Fold7- und Phone-Layout tatsächlich rendern; Hostdarstellung ausdrücklich als solche kennzeichnen.
4. Gezielte Prüfungen, danach kombinierte Android-Suite und unsignierter Code55-Build.
5. Gerätetest separat: WhatsApp öffnen, Kontakt suchen, Ergebnis sehen; keine Nachricht ohne passenden Auftrag/Freigabe.

Anfangsstatus: **WIP / UNVERIFIED**. Auf diesem Integrationszweig noch keine eigenen Tests oder Builds. Fold7: **NOT_RUN**. Keine Signierung oder Installation. Kein Merge nach `main`.

## Reproduzierbarkeit

Gradle-Läufe seriell mit gemeinsamer `agent-gradle.lock`; Ergebnisse und Quell-SHA hier nachtragen. Bei Unterbrechung zuerst tatsächlichen Remote-HEAD, Arbeitsbaum und diese Datei prüfen. Rückfall ist die unveränderte Code55-Basis; bestehende Zweige werden erhalten.
