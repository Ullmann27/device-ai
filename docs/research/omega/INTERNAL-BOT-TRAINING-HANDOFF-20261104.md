# Interne Continuum-Bots: fachliche Übungen und Korrekturrückmeldung

Stand 04.11.2026, Europe/Vienna. Status: WIP / UNVERIFIED.

## Korrigierter Besitzerauftrag
Heinz meint die bereits vorhandenen internen Bots seiner Continuum-Architektur. Sie sollen Reparatur-, Implementierungs- und Avatar-Aufgaben üben und aus überprüfbaren Ergebnissen lernen. Die frühere Interpretation als Verbindungs-/Geräteproblem war zu eng.

## Ausgangsstand und begrenzter Entwurf
Basis ist Bot-PR #493, ee2fa0ee5567e5d34bcdfe229425a71df9818183. Eigener Branch: codex/continuum-internal-bot-training-20261104. Drei Helfer prüfen beziehungsweise bearbeiten getrennt Reparatur-/Implementierungsfälle, den vorhandenen Botdialog-Anschluss und Avatarfälle.

Die vorhandenen Profile teilen eine Modelllaufzeit und haben private Gesprächskontexte. Die kleinste Ergänzung verwendet den existierenden prepare → streamThinking → complete-Pfad. Sie liefert versionierte, begrenzte Fachübungen, bewertet erst die akzeptierte terminale Modellantwort und gibt eine klar als Programm-Auswertung gekennzeichnete Korrektur in denselben privaten Gesprächskontext zurück.

Der erste Umfang sind fachliche Entscheidungs-/Ereignisübungen für Kora, Link und Iris. Es ist kein Nachweis, dass der Bot eine echte Quellcode-Reparatur oder ein Rendering ausgeführt hat. Für solche Aktionen bleiben die vorhandenen Auftrags-, CCC1-, Prüfer- und Receipt-Pfade maßgeblich.

## Abnahme
- Genaues Eingabe-/Antwortschema, getrennte Übungs- und Transferfälle; keine Musterlösung im aktuellen Modellprompt.
- Tatsächliche bestehende Botdialog-Aufrufer verwenden; keine zweite Warteschlange oder Laufzeit.
- Abbruch, Doppelabschluss, Modell-/Profil-/Sessionwechsel dürfen keine Auswertung verbuchen.
- Originale Modellantwort und deterministische Rückmeldung unterscheidbar speichern; bestehende Gespräche lesen und private Daten bewahren.
- Rückmeldung nur beim zugeordneten Bot und erst nach erfolgreicher bestehender Abschlussprüfung.
- Keine Gewichtsänderung, neue verifizierte Fähigkeit, Owner-Fakten oder technischen Reparaturerfolge aus einer Übungsantwort ableiten.
- Entwicklerprüfungen und echte Modellversuche getrennt zählen. Ohne echten Modellversuch bleibt der Trainingsstand NOT_RUN.

## Grenzen und Rückfall
Keine Änderung an Main, Paket/Signatur, Release/Installation, Modellpromotion, Offline-Schalter oder historischen Versuchssperren. Keine bezahlte KI-API und keine Actions. Der vorhandene negative ECHO-Fehlerpfad wird parallel in #515 bearbeitet und hier nicht doppelt geändert.

Dieser erste Commit enthält ausschließlich den überprüfbaren Arbeitsauftrag. Implementierung, Tests und Ergebnis-SHA werden nachgetragen. Rückfall: diesen isolierten Anschluss nicht übernehmen; vorhandene Botdialoge und Daten bleiben die Ausgangsbasis.
