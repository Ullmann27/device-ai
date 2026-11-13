# Code55 – gezielter Fold7-Test

Quellstand: `889e06d47fffd2230fa5b062dd1f31b65992e728`.
Paket: `at.heinz.deviceai.continuumlocal`, VersionCode 55.
Geräteabnahme: **NOT_RUN**. Dieses Protokoll enthält Prüfschritte, keine
bereits erzielten Geräteergebnisse.

Nach erfolgreicher vollständiger QA und Originalsignierung die APK aus dem
unveröffentlichten Testentwurf verwenden. Die unsigned CI-Datei ist kein
installierbares Update. Bestehende App-Daten und das lokale Modell erhalten;
eine Deinstallation gehört nicht zu diesem Test.

## Gespräch und Korrektur

1. „Warum funktioniert meine Kamera nicht?“ eingeben. Erwartet: eine normale
   Gesprächsantwort; kein App-Start und kein Computer-Freigabedialog allein
   wegen des Wortes „Kamera“.
2. „Schreibe mir einen Text über den Kalender.“ Erwartet: Textantwort im
   Gespräch, kein Kalenderstart und kein Nachrichteneingabeversuch.
3. „Öffne Kamera nicht.“ Erwartet: keine Aktion. Eine Erklärung des
   verneinten Auftrags ist zulässig.
4. Während eines laufenden Auftrags „Stopp, das meine ich nicht.“ sagen oder
   eingeben. Erwartet: steuerbare Arbeit und Sprache stoppen, Blick zum
   Besitzer, kurze Rückfrage. Ein bewusst pausierter Sprachmodus darf nicht
   von selbst neu aktiviert werden.

## Sichtbarer Android-Computer

Continuum und **Eigene Dateien** in unterstützten, nicht überlappenden
Split-Screen-Fenstern gleichzeitig sichtbar lassen. Die vorhandenen
Besitzer-, Accessibility- und Android-Bildschirmfreigaben über ihre regulären
Dialoge erteilen. Eine abgelehnte Freigabe muss den Auftrag beenden.

| Auftrag | Was konkret zu prüfen ist |
| --- | --- |
| „Öffne Eigene Dateien.“ | Tatsächliche App sichtbar; aktueller Bildinhalt im zentralen Monitor; Ergebnis erst nach Beobachtung |
| „Öffne den Download-Ordner.“ | Tatsächlich erreichte Ordneransicht; passende Plan-/Ergebnisanzeige |
| „Suche in Eigene Dateien nach Argon4.“ | Echte Suchfeldeingabe und beobachtete Suchansicht; kein erfundener Dateifund |
| Während der Arbeit Continuum verlassen | Kontrollierter Abbruch; einmalige stille Information über unterbrochene Arbeit; keine Hintergrundfortsetzung |
| Continuum erneut öffnen | Kein automatisches Wiederholen bereits ausgelöster Eingaben; neue Arbeit benötigt eine aktuelle Sitzung |

Der Suchauftrag belegt nicht automatisch das Öffnen einer Trefferdatei oder
die Analyse sämtlicher PDF-Seiten. Der vollständige Argon4-Ablauf mit
quellengebundener Zusammenfassung und gezieltem Seitenwechsel ist noch
separat abzunehmen. Nachrichtenversand und Datenlöschung sind kein Teil
dieses ersten Gerätetests.

## Sprache und Darstellung

Die vorhandene Sprachsitzung bewusst aktivieren. Prüfen, ob Mikrofonbereitschaft
erst beim tatsächlichen ASR-Start erscheint, kurze Unterbrechungen den Kontext
erhalten und STOP auch die Ausgabe unterbricht. Maya nur mit dem eingerichteten
und freigegebenen Sprachdienst beurteilen; einen Online-Dienst nicht als
Offline-Sprache protokollieren.

Fold offen und schmale Ansicht prüfen: Originalavatar und Kugel bleiben
sichtbar; acht Navigationen, Eingabe und Freigabedialoge sind erreichbar.
Seitenmonitore dürfen bei wenig Platz ausgeblendet werden. Der bekannte
Hostbefund bei 200 % Schriftgröße — überlappender Kopfbereich und gekürzte
Navigationsbeschriftung — ist ausdrücklich offen und auf dem Gerät zu prüfen.

## Tatsächliche Messwerte

Systemdiagnose → **NEUE MESSREIHE STARTEN** → **JETZT ANALYSIEREN**.
Ein Punkt darf nur aus einer frischen erfolgreichen Diagnose stammen.
**MESSREIHE STOPPEN** oder Verlassen der Ansicht beendet die Aufzeichnung;
Rückkehr startet sie nicht automatisch neu. Diese Verbindung erfasst
Android-Thermalstatus und Akkutemperatur; sie misst keine Filament-GPU-Zeit.

Für jedes Ergebnis festhalten: Gerätezustand, Android-Version, Modell-SHA,
Schrift-/Displaygröße, Auftrag, tatsächlich sichtbarer Ausgang und Uhrzeit.
Für einen Fehler möglichst den ersten abweichenden Schritt und einen kurzen
Bildschirmmitschnitt festhalten. Private Inhalte bleiben lokal, bis der
Besitzer ihre Weitergabe ausdrücklich wünscht.

60 FPS, geringere Wärmeentwicklung und zuverlässige mehrstufige Autonomie
sind Ziele; dieses Protokoll trägt dafür noch keine Messwerte ein.
