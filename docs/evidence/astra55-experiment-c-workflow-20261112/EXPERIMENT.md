# Experiment C · Beobachtete Aktionswirkung

Vorregistrierung vor dem ersten Messlauf, 12.11.2026. Status: RESEARCH_SHADOW / NOT_RUN.
Produktionsbasis: `5f87bdc610944b93d3173a83c5beeea315abeb66`.
Lokaler Start: `ee1268982ebffa096b4d5243212ff5fc204c2366`;
öffentlicher Start: `42c226cffe5279e185a29f8cf6d5f8d6848731a4`, gleicher Baum.
Integration: Experiment-C-PR #807 → Sammel-PR #804 → #799. Integrator: Astra root.
Registerbezug: bestehende EXP-07/11/12; Registeränderungen nur durch den Koordinator.

## Frage und vorhandene Kette

Bleibt der vorhandene Computerauftrag bei verzögerter oder widersprüchlicher Rückbeobachtung
ehrlich, einmalig und abbrechbar, während seine bestehenden Avatar- und Tonsignale reagieren?

Tatsächlicher Quellpfad: `OwnerComputerWorkflow` → `VerifiedNodeAction` →
`LiveActionCockpitAdapter` → `CockpitAnimationBus` → `ComputerWorkAvatarBridge` →
`AvatarDirector`/`AvatarPoseReducer` und `ComputerWorkCuePlayer`.
`OwnerComputerSessionController` instanziiert diese Komponenten und abonniert die Bridge direkt.
Der Bus ist synchron; lediglich der vorhandene Cue-Post wird hier verzögert.
Die aktuelle Aufgabenfreigabe wird mit `OwnerComputerTaskGrant` geprüft.

Der OS-Node-Port liefert synthetische Knoten und genau einen synthetischen Eingabeaufruf.
Alle genannten Workflow-, Prüf-, Bus-, Freigabe-, Avatar- und Cue-Klassen laufen aus Originalquellen.
Die Uhr ist monoton und kontrolliert; Zeitwerte sind injizierte Ereigniszeiten, keine gemessene Gerätelatenz.

## Vorab festgelegte Messfolge

Ein einziger Auftrag setzt einen synthetischen Suchtext in ein bereits sichtbares eindeutiges Suchfeld.
Keine Kontaktöffnung, Composeränderung, Nachrichtenübermittlung oder Geräteaktion.

| Faktor | Festgelegte Werte |
|---|---|
| Nachbeobachtungsdelay | 0, 70, 500, 1500 ms |
| Vorhandener Cue-Post | 0, 149, 150, 151 ms |
| A | Exakter Text, 0 ms Nachbeobachtungsdelay |
| B | Identischer Auftrag/Text, 70/500/1500 ms Nachbeobachtungsdelay |
| Negativkontrolle 1 | Gleiche 16 Kombinationen; frische Rückbeobachtung enthält anderen Text |
| Negativkontrolle 2 | Vier Cue-Werte; Stop und Grant-Widerruf bei 50 ms, korrekte späte Beobachtung bei 500 ms |

Insgesamt 36 deterministische Abläufe: 16 gültige, 16 widersprüchliche, vier Stop-Abläufe.
Drei JUnit-Methoden führen diese Abläufe aus; 36 Abläufe sind nicht 36 JUnit-Methoden.
Der Ausgangsstempel liegt eine Nanosekunde vor der Eingabe; dadurch bleibt auch A mit 0 ms
eine streng neuere Rückbeobachtung. Ereignisreihenfolge wird zusätzlich zur Zeit geprüft.

## Harte Erfolgsgates

1. Jeder Ablauf führt genau eine Eingabe aus; keine Wiederholung im Warten oder nach unklarer Wirkung.
2. `VERIFIED`/`COMPLETED` dürfen erst nach einer frischen richtigen Nachbedingung auftreten.
3. Gültige Fälle liefern genau einen verifizierten Schritt und genau ein `COMPLETED`.
4. Widersprüchliche Fälle liefern kein `VERIFIED`/`COMPLETED`, enden pausiert und löschen Arbeitsbewegung.
5. Stop verhindert späteres `VERIFIED`/`COMPLETED`; der Reducer bleibt `STOPPED`.
   Ein späteres korrektes Ergebnis darf weder Bewegung noch einen wartenden Ton neu auslösen.
6. Eingabesignale sind an den einen tatsächlichen Portaufruf gebunden. Ein Klickton ist ein Eingabesignal,
   keine Erfolgsmeldung. Während bestätigter Arbeitsdauer kann die bestehende kurze Pose sichtbar werden.
7. Die bestehende Cue-Grenze bleibt inklusive 150 ms. Bei 151 ms oder vorherigem Ende/Stop wird ein
   noch wartender Ton verworfen. Für gültige/widersprüchliche Fälle wird genau dann ein Ton erwartet,
   wenn Cue-Delay ≤ 150 ms und Cue-Delay ≤ Nachbeobachtungsdelay.
8. Nach Abschluss/Pause/Stop bleibt kein aktiver WorkInput und kein Arbeits-Leseblick bestehen.

Die Stop-Gates gelten für Ereignisse und Darstellung; sie verlangen keinen bestimmten
Rückgabe-Statusnamen des Workflows nach gleichzeitigem Grant-Widerruf.

## Messwerte

Pro Ablauf: Arm/Kontrolle, beide Delays, Eingabeanzahl, Anzahl DISPATCHED/OBSERVED/VERIFIED/COMPLETED,
frühe/falsche Erfolgsereignisse, Ergebnisstatus, terminaler Reducerzustand, Avatar-Inputereignisse,
Tonanzahl, erste Eingabe→Avatar-/Ton-Latenz, Eingabe→Verifikation/Abschluss-Latenz,
maximaler Schulterimpuls in Avatar- und WorkReducer, verbleibende Bewegung/Leseblick nach Ende.
Poseabtastung alle 10 ms während kontrollierter Wartezeit; keine Behauptung über GPU-Frames oder Pixel.
Rohdaten als CSV, verdichtete Messwerte als JSON, Original-Compiler-/JUnit-Ausgaben und Quellhashes.

## Abbruch und Entscheidung

Ein gebrochenes Gate stoppt die positive Abnahme. Messwerte und Fehler werden erhalten;
Produktionskorrektur erst nach reproduziertem Befund und zusätzlichem Dateiclaim.
Composer, Grant-Implementierung und Dispatcher bleiben bei Audit #803.
Besteht der Bestand, wird dieser Nachweis in die gemeinsame Testlinie integriert und zusätzliche
Produktlogik ausdrücklich verworfen: kein zweiter Planner, kein zweiter Bus, keine automatische Wiederholung.
Keine nachträgliche Gateänderung zur Herstellung eines grünen Ergebnisses.

## Forschungsgrundlage und Grenzen

Primärquellen mit Daten und Übertragungsgrenzen: `sources.json`.
ReAct motiviert die Verbindung von Handlung und anschließender Information; AndroidWorld trennt
Aufgabenerfolg durch eigene Zustandsprüfungen. Hier wird dieses Prüfprinzip auf vorhandenen Code angewandt.
Die Android-Referenz beschreibt `performAction` als Aktionsausführung; die Beurteilung des konkreten
Besitzerziels ist eine zusätzliche lokale Invariante dieses Experiments.

Kein ReAct-/M3A-Modell wird eingebaut, trainiert oder evaluiert. Kein AndroidWorld-Score.
Der vorhandene Workflow bleibt ein begrenztes Rezept, kein freier visueller Planer.
Android-Capture, Monitorpixel, GPU, APK und Fold7: NOT_RUN. Darstellung hier: tatsächliche Pose-/Ereigniszustände.
Eigenständiger Issue-#41-Webabruf ergab DisabledError; der Koordinator hat #41 in dieser Runde direkt
über GitHub gelesen (updated_at 02.11.2026) und seine Vorgaben weitergegeben.
Android-first, ein Backlog/Repair/Audit, Baseline/Messung/Negativkontrolle/Abbruch,
isolierter Versuch, exakte Quellen und keine Berechtigungs-/Gedächtnispromotion bleiben verbindlich.
