# Abschlussbericht „Jarvis Next“ – Stand Code73 (19.11.2026)

Bericht zu den drei Besitzeraufträgen vom 19.11.2026 (Jarvis Next, Cinematic & Intelligent System mit Deep Clean,
autonomer Masterbefehl). Begriffe: **PASS (Host)** = automatischer Test am Rechner grün · **NOT_RUN** = nicht
ausgeführt · **FAIL** = ausgeführt und fehlgeschlagen. Am Fold7 wurde von der Entwicklung nichts ausgeführt; kein
Gerätetest gilt als bestanden.

## 1. Neu implementierte Funktionen

**Code72** (veröffentlicht): persönlicher Gesprächston mit Begrüßung nach echter Pause; „Continuum“ statt „Claude“ im
Gespräch; Geräte-Werkzeuge ohne Shizuku (43 Werkzeuge, darunter Gerätesuche, Datei lesen, PDF/Word erstellen und
öffnen); Coding-Agenten; Ansage eingehender Nachrichten; Systemcheck und Tagesbriefing (Wetter, Termine, offene
Nachrichten); Speicher & Bereinigung (Deep Clean) mit Schutzstufen Grün/Gelb/Rot; Websuche, Seitenabruf und
Code-Ausführung über die Cloud als zwei Schalter; taktische Bühne (Lagezeile, Auftragsleiste, Start-Systemcheck,
Lagekarten); bestätigte Beziehungen fürs Senden („mein Vater“).

**Code73** (gebaut, wartet auf die Signatur des Besitzers): Gmail-Verbindung über das Google-Konto mit getrennten
Rechten und die Seite „Verbindungen“; mehrere Aufträge in einem Satz als Auftragsfolge mit Schritten für Senden, App
öffnen, Mail, Kalender, Recherche, Dokument, Export, Speicher – mit typisiertem Datenfluss, Auftragsprotokoll und
Fortsetzen nach Neustart; Kalenderwoche mit Terminprüfung; Seitenzusammenfassung ohne Speichern;
Gesprächspersönlichkeit (Arbeitsstand, „Wo waren wir?“, Begrüßung nach Lage, Eigeninitiative in drei Stufen);
Stimmprofile und Sprechweisen; Avatar ohne Schnittkante und Arbeitsszenen (Speicher, Mail, Senden, Recherche);
Internetzugang im Gespräch einstellbar.

## 2. Tatsächlich behobene Fehler

| Fehler | Behebung |
|---|---|
| „Eine PDF kann ich nicht erstellen“ (Feld, 19.11.) | Werkzeug `create_document`, Fähigkeitenkatalog fürs Cloud-Gehirn |
| „Werkzeugausgang UNKNOWN … bis zur Besitzerprüfung blockiert“ bei reiner Suche (Feld) | lesbare Meldung für lesende Werkzeuge, nichts gesperrt |
| „Tippe WEB RECHERCHE … untrusted data“ auf die Frage nach dem Internetzugang (Feld, Screenshot) | `cognitive/OwnerInternetAccess`: echter Stand, Angebot, Umstellen nach Ja |
| „meinem Vater“ traf jeden Kontakt mit „Vater“ im Namen; mit Dauerfreigabe Sendung ohne Rückfrage möglich | nur noch bestätigte Beziehungen; erste Sendung immer mit Bestätigung |
| Empfängerwörter im gesendeten Text | Trennung über Beziehung/Kontakt, sonst einmal bestätigen |
| „Speichere das als PDF“ nahm Absagen oder vorgelesene Nachrichten | typisierte Chatzeilen; „das“ meint nur ein Ergebnis |
| Antwort auf eine Rückfrage wurde als Gruß verstanden | offene Fragen einer Lenkung zuerst (`OwnerOpenQuestions`) |
| „Öffne WhatsApp und schreib …“ endete als Sackgasse | Computer-Lenkung reicht weiter; Sendeschritt der Auftragsfolge |
| Modellplan ließ Teile stillschweigend weg oder fügte Schritte hinzu | Abdeckungs- und Verbprüfung; ehrliche Teilantwort |
| Zwei rote Tests im signierten Code71 | repariert bei der Zusammenführung |

Befunde aus Code71, **nicht** behoben: altes Vorlese-Angebot verschluckt späteres „Lies vor“; bei beschäftigter Stimme
wird nichts vorgelesen; von mehreren wartenden Nachrichten wird nur die letzte angesagt.

## 3. Was Gmail, WhatsApp und der Computer nachweislich können

„Nachweislich“ heißt hier: in automatischen Tests am Rechner mit Attrappen für Telefon, Google und Kalender.

- **Gmail:** verbinden/trennen, neue Mails, Suche, Lesen, Verlauf, Zusammenfassung (lokal; Cloud nur mit eigenem
  Schalter), Fristen wörtlich, Entwurf, Antwort im Verlauf, Senden nur mit Senderecht und Bestätigung im nächsten Zug;
  kein Token in Logs, Prompts, Speicher. Gegen das echte Google: NOT_RUN (braucht das Google-Cloud-Projekt des
  Besitzers).
- **WhatsApp:** zusammengesetzter Auftrag als ein Sendeschritt; Empfänger über Name oder bestätigte Beziehung;
  Korrektur „nicht Papa, Mama“; ohne Freigabe nur Entwurf; mit Freigabe ein Druck nach Bildschirmprüfung; nach
  Neustart kein zweiter Druck. Am echten WhatsApp: NOT_RUN.
- **Computer / Bühne:** Monitor mit echten Eingabemarken, Auftragsleiste aus echten Schritten, Arbeitsszenen aus
  Snapshot-Daten, Regisseur für genau eine Szene. Am Gerät (WebView, FPS, Wärme): NOT_RUN.

## 4. Abnahmetests des Auftrags (15)

| Nr. | Test | Host | Gerät | Nachweis |
|---|---|---|---|---|
| 1 | Zusammengesetzter WhatsApp-Befehl | PASS | NOT_RUN | `OwnerMissionPhase2FlowTest` (Satz 2) |
| 2 | Empfängerkorrektur setzt fort | PASS | NOT_RUN | `OwnerRelationsTest`, `OwnerSendDirectorTest` |
| 3 | Ohne Freigabe kein Versand | PASS | NOT_RUN | Sendeschritt endet als „Entwurf vorbereitet“ |
| 4 | Mit Freigabe Versand versucht und geprüft | PASS (Attrappe) | NOT_RUN | ein Druck, Bildschirmprüfung der Attrappe |
| 5 | Gmail über offiziellen OAuth-Dialog | NOT_RUN | NOT_RUN | Code vorhanden; echter Dialog nie gelaufen |
| 6 | Echte Posteingangsabfrage | NOT_RUN | NOT_RUN | nur gegen Attrappe (`OwnerMailLaneTest`) |
| 7 | Mail-Zusammenfassung als PDF gespeichert und geöffnet | PASS (Attrappe) | NOT_RUN | Anwalt-Satz in `OwnerMissionPhase2FlowTest` |
| 8 | Zusammengesetzter Kalender- und Gmail-Auftrag | NOT_RUN | NOT_RUN | Kalender- und Mailschritte je einzeln in Folgen getestet; ein Satz mit beiden nicht eigens |
| 9 | Arbeitsmonitor bildet echte Ereignisse ab | PASS | NOT_RUN | `Command03*`-Tests, `VisualWorkstationDirectorTest`, Chromium |
| 10 | Avatar reagiert auf Zustände | PASS (Blickereignisse) | NOT_RUN | jsdom-Tests der Bühne |
| 11 | Kontext übersteht Rückfragen und Themenwechsel | PASS | NOT_RUN | `OwnerOpenQuestionsTest`, Dialog-Benchmark (Korrekturen 22/22, 9/9) |
| 12 | Bei App-Abbruch keine wiederholte externe Aktion | PASS | NOT_RUN | `OwnerMissionJournalTest`: Neustart an jeder Schrittgrenze, genau ein Druck |
| 13 | Offline vorgesehene Funktionen ohne Internet | NOT_RUN | NOT_RUN | feste Lenkungen brauchen kein Netz; kein eigener Offline-Gesamttest |
| 14 | Cloud respektiert Datenschutzgrenzen | PASS | NOT_RUN | `ClaudeLocalOnlyGuard`-Tests, Mail-Cloud-Schalter, Abweisung vorgelesener Nachrichten |
| 15 | Fold7 bedienbar und thermisch stabil | – | NOT_RUN | nur am Gerät messbar |

Deep-Clean-Abnahmekriterien: neun von zehn PASS (Host); „echte Bereinigungsvorgänge am Fold7“ NOT_RUN.

## 5. Experimente

| Experiment | Ergebnis |
|---|---|
| Intent-Graph (E1 / B) | umgesetzt als Satzlesung in Teilsätzen mit Schrittplan; zurückgehaltene Prüfsätze: Planung 21/22, Briefing 29/29, Computer 16/16 im ersten Lauf; Ausführung 17/18 im ersten Lauf |
| Vorausschauende Hilfe (E2 / G) | Regel für Eigeninitiative und drei Vorschlagsarten umgesetzt; drei weitere scheitern an fehlenden Datenquellen |
| Dialog-Benchmark (E3 / A) | 262 Situationen; Entwicklung 184/184, zurückgehalten 77/78 |
| Sichtbare Aktionen (E4 / D) | Live-Monitor mit Eingabemarken und Arbeitsszenen umgesetzt; ein nachträgliches Abspielen vergangener Aktionen nicht gebaut |
| Zwei-Modell-Schluss (E5 / F) | **nicht als Messung ausgeführt**: keine echten Modellaufrufe in dieser Umgebung; entschieden nach Codeanalyse: kein Kritiker-Modell, stattdessen geprüfter Planaufruf |
| Soziale Initiative (E6) | drei Stufen mit Obergrenzen und Ruhezeiten nach einem Nein; Wirkung am Besitzer NOT_RUN |
| Selbständige Erholung (E7) | teilweise: Fehlerabbildung und begrenzte Wiederholung bei Gmail, Fortsetzen nach Neustart; eine Folge endet aber am gescheiterten Schritt |
| Filmische Darstellung (E8 / E) | taktische Bühne und Arbeitsszenen; Videos 1 und 2 der Referenz nicht auswertbar (über 10 MB), Video 3 ausgewertet |
| Langzeit-Auftragsgedächtnis (E9) | Auftragsprotokoll und Arbeitsstand; Host-Tests grün |
| Lernen neuer Fähigkeiten (E10) | nicht gebaut |
| Deep Clean E1–E7 | E1, E2, E4, E5, E6, E7 umgesetzt; E3 auf Continuums eigene Last beschränkt; ähnliche Fotos nicht gebaut |
| Eigene Experimente | Wärmemessung mit Vergleichsläufen (Code69); Prüfsätze vor dem Code; unabhängige Architekturkritik durch ein zweites Modell; Eigenschaftstests „keine Aussage ohne Beleg“; Neustarttest an jeder Schrittgrenze |

**Gescheitert oder nicht geliefert:** Messung des Modell-Routings (Latenz, Tokens, Energie); Sichtung von
Open-Source-Technik mit Lizenz und Wartungsstand; fotorealistische Haut (bewusst nicht, wegen Wärme und weil die
Referenz einen holografischen Kopf zeigt); Lichtbahnen vom Blick zur Karte, fliegende Dokumente, Kamerafahrt;
Wake-Word; Bedienen beliebiger Apps durch das Cloud-Gehirn; Drive; Kalendereintrag aus Mail-Frist; Karten-App aus
Adresse; Erinnerungen.

## 6. Fehlende Gerätetests

Alle. Vorrangig: Installation ohne ADB (Play Protect), Wärme und Reaktionszeit, Gmail-Verbindung gegen das echte
Google, Senden über WhatsApp, Signal bei eingehenden Nachrichten, Stimme nach Gehör, Bühne im WebView,
Speicherbereinigung mit Androids Dialogen, Systemcheck beim Start. Checklisten: `docs/handover/*GERAETECHECKLISTE*`.

## 7. Erzeugte Bauten

| Code | Quelle | Lauf | Stand |
|---|---|---|---|
| 72 (0.3.71) | `a4c7d8ed4` | 44501121738 | veröffentlicht: `continuum-einfach-code72-44501121738`; APK geprüft (Prüfsumme, Zertifikat `6354e91d…3ee23`, Version) |
| 73 (0.3.72) | `5f7ccf298` | 44549818595 | gebaut; wartet auf die Signaturfreigabe des Besitzers |

## 8. Commits und Pull Requests

- Code72: PR #874, Merge-Commit `a4c7d8ed49e85d44952f18cd38f2f5122e8b5a1f`; vollständige Prüfung Lauf 44494090710
  auf `37c7e44c5`.
- Code73: PR #875, Merge-Commit `5f7ccf298460e23c3786dee61215adb757ef4869`; vollständige Prüfung Lauf 44541301455
  auf `89649faf4` (7699 Tests, 0 Fehler, 7 übersprungen).
