# Code55: geschlossener Auftrags- und Ergebnisweg

## Auftrag und gemeinsame Linie

Besitzerauftrag vom 12.11.2026: vorhandene Komponenten verbinden, Rückwege prüfen,
dann die gemeinsame APK bauen. Verantwortlich: Astra; PR #809 auf #799,
`integrate/code55-apk-fable-20261112`. Kein zusätzlicher Eventbus, Renderer,
Agentenlaufzeit oder Gedächtnisspeicher.

## Tatsächliche Verbindung

| Ablauf | Bestehender Hinweg | Ergebnisweg |
|---|---|---|
| Computerauftrag | AgentActivity → App-Suche/Freigabe → OwnerComputerSessionController → OwnerComputerWorkflow | Typisiertes Resultat → LocalConversationContext → Chat/Maya → nächster Modellprompt |
| Android-Bedienung | OwnerComputerTaskGrant → gebundene Node-/Klickaktionen | Beobachtungsepoch, Revision und tatsächlicher Wirkungsbeleg |
| Sichtbares Arbeiten | ScreenObservation/MediaProjection → AndroidLiveComputer → LiveMonitorFeed → FilamentCockpitStage | Renderbereitschaft begrenzt Eingaben; CockpitAnimationBus verbindet Avatar und Arbeitsereignisse |
| Zentraler Teamauftrag | Vorhandenes ChiefBotTeam → Analyse → Gegenprüfung → gespeicherter Teamstatus | Einmalige, auf Ursprungssitzung gebundene Anzeige und Gesprächskontext |

## Computer-Rückweg

Vorher erschien das Resultat als Chatmeldung, fehlte aber im Kontext späterer
Rückfragen. Der Controller gibt jetzt sein vorhandenes typisiertes Resultat mit
Beobachtungsepoch an den Callback des konkreten Plans zurück. Der ursprüngliche
Owner-Auftrag und seine Maya-Erlaubnis bleiben gebunden.

`recordToolResult` bezeichnet das Resultat ausdrücklich als Werkzeugbeobachtung
und setzt `modelResponse=false`. Nur der ursprüngliche Owner-Auftrag fließt in
Owner-Situation/Objektzustand ein; Ergebnisse sind keine neuen Owner-Fakten,
Gerätefreigaben oder Rohbild-/Nodeimporte.

Die unabhängige Prüfung fand und korrigierte eine Lifecycle-Falle: Der Android-
Aufnahmedialog erhöht beim Pausieren die App-Lookup-Generation. Diese Generation
schützt weiterhin einen noch ausstehenden Start, sperrt aber keinen bereits
gültigen Computerplan. Dessen asynchroner Callback wird durch `plan === current`
und den Controller-Lifecycle gebunden; Activity/Sitzung/Bot und Beobachtungsepoch
begrenzen die Zustellung zusätzlich.

STOP, neuer Plan und geschlossener Controller verwerfen verspätete Antworten.
Verlorene Bildschirmfreigabe erzeugt bei noch aktuellem sichtbarem Auftrag eine
feste negative Diagnose. Alte VERIFIED-/Entwurfsbehauptungen werden dabei nicht
übernommen. Bewusstes PAUSED und eine inzwischen neue Owner-Nachricht verhindern
nachträgliches Vorlesen; Anzeige und gebundener Kontext bleiben möglich.

## Zentraler Team-Rückweg

Vorher endete der zentrale Teamauftrag nur im getrennten Teamverlauf. Jetzt
bindet die Activity eine neue zentrale Einreichung an Task-ID, Sitzung,
Teamrevision und vorhandenes Admission-Token. Nach tatsächlich gespeichertem
Abschluss erhält der Hauptdialog einmalig die Gegenprüfung. Sie bleibt
ausdrücklich als nicht unabhängig geprüfter Modellinhalt bezeichnet.

Private Bot-/Teamdialoge werden nicht kopiert. Alte Tasks werden nicht erneut
zugestellt; Sitzungswechsel, Widerruf und geändertes Team verwerfen die Bindung.
Fehler-/Blockadestatus werden als Nicht-Modellantworten behandelt. Der bestehende
Haupt-STOP-Knopf erhält nun auch während eines Teamauftrags die tatsächliche
Request-Lane; nach dem Ende werden Lane und Busy gemeinsam freigegeben.

## Prüfung beim Einfrieren

- Zwei produktive Dateien und die bestehende `OwnerAppPlanningContextUiTest`
  erweitert; keine separate Test-/Produktarchitektur.
- Unabhängige Quellprüfung beider Rückwege: PASS.
- Patch-Anwendung mit `--whitespace=error-all`, `git diff --check`: PASS.
- Neue Regressionen: echter Consent-Start/Activity-Pause/-Resume/OS-Rückgabe,
  Haupt-STOP mit verspätetem Consent, widerrufene Beobachtung, Controller-
  Startfehler, beobachteter Kontext im Folgeprompt, echte gespeicherte
  Team-Gegenprüfung, einmalige Zustellung, privater Dialog, Sitzung/Widerruf
  und Haupt-STOP während des Teams.
- Neue Kotlin-/Android-Regressionsausführung: beim Einfrieren NOT_RUN.
  Der Integrator ergänzt den tatsächlichen Build-/Testbeleg separat.
- Fold7, echte Bildschirmaufnahme, Mikrofon/Maya und Filament-GPU: NOT_RUN.

Status: PORTIERT; vollständige gemeinsame Verifikation ausstehend.
