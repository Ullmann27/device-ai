# Auftragsfolge: ein Satz, mehrere Schritte – Geräte-Checkliste (18.11.2026)

**Stand:** Planung (ein Satz → geordnete Schritte), Ausführung Schritt für Schritt durch die bestehenden Lenkungen,
Pause bei Rückfragen, Korrekturen, Stopp, Fehler- und Abschlussbericht sind auf dem Host getestet – mit den echten
Lenkungen (Dokumentenablage, Arbeitswerkzeuge, Sende-Lenkung, Aufgabenketten), aber nachgestelltem Speicher,
PDF-Leser, Modell, Download-Ordner, Mailprogramm und Gmail-Bildschirm; dazu vier Läufe in der echten Activity unter
Robolectric. **Auf dem Fold7 ist nichts davon gelaufen: alles unten ist NOT_RUN**, bis du es probiert hast. Nur das
Gerät kann zeigen: wie die Spracherkennung deinen langen Satz schreibt (Kommas, „und dann“), wie lange Suche und
Zusammenfassung dauern, wie warm das Gerät wird, ob Vorlesen und nächster Schritt sich ins Wort fallen und ob
Mailprogramm und Gmail die Datei annehmen.

Eine Auftragsfolge tut nichts Eigenes: Jeder Schritt läuft durch dieselbe Lenkung wie der Einzelauftrag, mit
denselben Freigaben und Rückfragen. Für die Einzelschritte gelten deshalb weiter
[ARGON4-ABLAUF-GERAETECHECKLISTE-20261118.md](ARGON4-ABLAUF-GERAETECHECKLISTE-20261118.md) und
[SELBST-SENDEN-GERAETECHECKLISTE-20261118.md](SELBST-SENDEN-GERAETECHECKLISTE-20261118.md).

## 0. Einmal vorbereiten

1. Die Einzelabläufe funktionieren schon (Checklisten oben): „Zugriff auf alle Dateien“ ist erlaubt, das Modell ist
   geladen.
2. **Testdateien** (PDFs mit echtem Text, mindestens drei Seiten): `Download/Argon4.pdf`; für die Rückfrage
   zusätzlich `Documents/Argon4 Datenblatt.pdf` und `Download/Argon4 Messprotokoll.pdf` (dann vorübergehend
   `Argon4.pdf` umbenennen).
3. **Eine eigene Testadresse** als Empfänger, z. B. deine zweite Adresse. Unten steht dafür `<Adresse>`.
4. Abschnitt 1 bis 4 und 6: **„E-Mails über Gmail selbst senden“ ausgeschaltet** (Einstellungen → Selbst senden). Dann
   wird nur das Mailprogramm mit dem Entwurf geöffnet, gesendet wird nichts. Abschnitt 5 schaltet es ein.
5. Für Abschnitt 2 und 3 (Nachrichten): Internetzugang auf „Nachfragen“ oder „Immer“.
6. Du kannst sprechen oder tippen. Wenn die Spracherkennung einen Satz anders schreibt als unten: **den Wortlaut aus
   der Sprechblase melden** – das ist die wichtigste Rückmeldung.

## 1. Erster Ablauf: PDF → Seite → Word → Mail

| Nr. | Sag / tippe | Erwartet | Fehlerbild und was du melden sollst |
|---|---|---|---|
| A1 | „Öffne das PDF Argon4, fass Seite 2 zusammen, speichere es als Word und schick es per Mail an `<Adresse>`“ | **Eine** Sprechblase mit deinem Satz. Sofort: „Ich mache das in drei Schritten: 1. Das PDF ‚Argon4‘ suchen und öffnen. 2. Seite 2 zusammenfassen und als Word-Datei speichern. 3. Per E-Mail an `<Adresse>` schicken.“ Dann nacheinander, ohne weiteres Wort von dir: „Gefunden: ‚Argon4.pdf‘ … Ich öffne es.“ (Dokument im Arbeitsfenster) → „Seite 2 von ‚Argon4.pdf‘ – Zusammenfassung: … Gespeichert als Word-Datei: ‚Zusammenfassung Argon4 Seite 2 `<Datum>`.docx‘ …“ (**ohne** „Soll ich sie öffnen?“) → dein Mailprogramm öffnet sich mit Empfänger, Betreff, Text und der Word-Datei als Anhang → zurück in Continuum: „Die E-Mail ist vorbereitet … Gesendet habe ich nichts …“ → „Fertig – alle drei Schritte sind erledigt. 1. ‚Argon4.pdf‘ geöffnet (Ordner Download). 2. ‚Zusammenfassung Argon4 Seite 2 `<Datum>`.docx‘ gespeichert (Interner Speicher › Download). 3. E-Mail an `<Adresse>` im Mailprogramm vorbereitet, diese Datei hängt an – gesendet habe ich nichts.“ | Kein Plan, sondern nur der erste Teil läuft oder das Modell antwortet frei → **Wortlaut der Sprechblase** melden. Schritte in falscher Reihenfolge, ein Schritt doppelt, eigene Sprechblasen für Teilaufträge, Mail ohne Anhang, „Fertig“ obwohl etwas fehlt → Screenshot des Gesprächs. |
| A2 | Arbeitsfenster während A1 beobachten | Abwechselnd die echten Schritte der Lenkung (z. B. „Speicher durchsuchen“, „Seite 2 lesen“, „Zusammenfassen“, „In Downloads speichern“, „Gespeicherte Datei prüfen“) und die Übersicht der Folge („1/3 … erledigt“, „2/3 … läuft“). Nichts erscheint, bevor es gestartet ist. | Schritte, die nie gelaufen sind; Übersicht bleibt auf „läuft“ stehen → Screenshot |
| A3 | Varianten, jede einzeln: „Öffne das PDF Argon4 und dann fass Seite 2 zusammen und speichere es als Word“ · „Öffne das PDF Argon4. Fass Seite 2 zusammen und speichere es als Word.“ · gesprochen ohne Pausen | Plan mit zwei Schritten, am Ende: „Fertig – beide Schritte sind erledigt. … Soll ich die Datei öffnen?“ – „Ja“ öffnet die Word-Datei. | Wortlaut der Sprechblase melden, wenn kein Plan entsteht |
| A4 | Einzelauftrag wie bisher: „Öffne das PDF Argon4“ · „Fass Seite 2 zusammen und speichere es als Word“ | **Kein** „Ich mache das in … Schritten“. Verhalten wie in der Argon4-Checkliste. | Ein Plan wird angesagt → melden |

## 2. Zweiter Ablauf: Nachrichten → PDF → Mail-Entwurf mit Anhang

| Nr. | Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|---|
| N1 | „Fass mir die Nachrichten von heute zusammen, speichere sie als PDF und schick sie per Mail an `<Adresse>`“ | „Ich mache das in drei Schritten: 1. Die Nachrichten von heute zusammenfassen. 2. Als PDF speichern. 3. Per E-Mail an `<Adresse>` schicken.“ Bei „Nachfragen“: der gewohnte Freigabe-Dialog für die Feeds (einmal). Dann Briefing mit Quellen → „Erledigt. ‚Nachrichten von heute `<Datum>`.pdf‘ liegt jetzt in deinem Download-Ordner …“ → Mailprogramm mit PDF im Anhang → „Fertig – alle drei Schritte sind erledigt. 1. Nachrichten zusammengefasst (N Meldungen). 2. ‚Nachrichten von heute `<Datum>`.pdf‘ gespeichert (Download-Ordner). 3. E-Mail an `<Adresse>` im Mailprogramm vorbereitet, diese Datei hängt an – gesendet habe ich nichts.“ | PDF fehlt im Anhang; Mail geht ohne dein Zutun hinaus (darf bei ausgeschaltetem Selbst-Senden **nie** passieren) → sofort melden |
| N2 | Freigabe-Dialog in N1 mit „Nicht jetzt“ ablehnen | „Die Auftragsfolge endet bei Schritt 1 von 3. Erledigt war noch nichts. Nicht geklappt: 1. Die Nachrichten von heute zusammenfassen – Der Internetzugriff wurde für die Nachrichten nicht freigegeben. Nicht begonnen: 2. Als PDF speichern. 3. Per E-Mail an `<Adresse>` schicken.“ Keine Datei, kein Mailprogramm. | Datei oder Mail trotz Ablehnung → melden |
| N3 | Internetzugang AUS, dann N1 | Wie N2, Grund: „Internetzugang ist AUS, deshalb habe ich keine Nachrichten abgerufen.“ | – |
| N4 | „Fass mir die Nachrichten von heute zusammen und speichere sie als PDF“ (nur zwei Teile) | **Kein Plan** – diesen Satz liest der Export heute als einen Auftrag („speichere die Nachrichten als PDF“). Gibt es noch kein Briefing im Gespräch, kommt der bisherige Hinweis. Das ist bekannt und so festgelegt; sag stattdessen den Satz aus N1 oder „Gib mir die Schlagzeilen und dann speichere das als PDF“. | – (bitte melden, wenn dich das stört) |

## 3. Dritter Ablauf: neuestes PDF suchen und öffnen

Braucht zusätzlich den Gerätezugriff der Aufgabenketten (Shizuku), wie „Such das neueste PDF in Downloads“ allein.

| Nr. | Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|---|
| D1 | „Such das neueste PDF in Downloads und öffne es“ | „Ich mache das in zwei Schritten: 1. Die neueste Datei suchen (Ordner Downloads lesen). 2. Die gefundene Datei öffnen.“ → Bericht der Kette („Die neueste PDF in Downloads ist ‚…‘ …“) → „Gefunden: ‚…‘ … Ich öffne es.“ → „Fertig – beide Schritte sind erledigt. 1. ‚…‘ gefunden. 2. ‚…‘ geöffnet (Ordner Download).“ | Andere Datei geöffnet als genannt → beide Namen melden |
| D2 | Shizuku aus, dann D1 | „Die Auftragsfolge endet bei Schritt 1 von 2. … Nicht geklappt: 1. … – Der Gerätezugriff für die Dateisuche ist nicht bereit; es wurde nichts gesucht. Nicht begonnen: 2. Die gefundene Datei öffnen.“ | – |

## 4. Pause, fremder Zug, Wiederaufnahme

`Argon4.pdf` vorübergehend umbenennen, sodass nur `Argon4 Datenblatt.pdf` und `Argon4 Messprotokoll.pdf` passen.

| Nr. | Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|---|
| P1 | Satz aus A1 | Plan, dann die Rückfrage der Ablage: „Zu ‚Argon4‘ habe ich 2 PDFs gefunden: … Welches soll ich öffnen?“ Die Folge wartet. | Es wird geraten und eine Datei geöffnet → melden |
| P2 | „das zweite“ | Die Datei wird geöffnet, **danach laufen Schritt 2 und 3 von selbst**, Abschluss wie A1 mit dem Namen dieser Datei. | Nach dem Öffnen passiert nichts mehr → melden |
| P3 | P1 wiederholen, dann statt einer Antwort: „Wie spät ist es?“ | Uhrzeit, und einmal: „Die Auftragsfolge wartet noch bei Schritt 1 von 3 (Das PDF ‚Argon4‘ suchen und öffnen). Mit ‚weiter‘ mache ich dort weiter, mit ‚abbrechen‘ beende ich sie.“ Bei einer zweiten fremden Frage **kein** zweiter Hinweis. | Folge läuft trotzdem weiter oder ist weg → melden |
| P4 | „weiter“ | „Ich mache bei Schritt 1 von 3 weiter: …“ → die Ablage fragt noch einmal → „das erste“ → Rest läuft. | – |
| P5 | P1 wiederholen, dann „abbrechen“ | „Die Auftragsfolge ist gestoppt. Erledigt war noch nichts. Abgebrochen: 1. … Nicht begonnen: 2. … 3. …“ Danach ist „weiter“ kein Befehl der Folge mehr. | – |
| P6 | „Zugriff auf alle Dateien“ in den Android-Einstellungen ausschalten, Satz aus A1 → „Ja“ → Schalter einschalten → zurück | Hinweis auf die fehlende Freigabe → Einstellung öffnet sich → nach der Rückkehr ohne weiteres Wort: Suche, Öffnen, Schritt 2 und 3, Abschluss. | Nach der Rückkehr läuft nur die Suche, der Rest nicht → melden (auch, ob Continuum zwischendurch beendet war – nach einem Neustart der App ist die Folge weg, nur die Suche läuft weiter; das ist bekannt) |
| P7 | P1, dann zehn Minuten warten, dann „das zweite“ | Die Ablage öffnet die Datei nicht mehr (ihre Frage ist abgelaufen) und die Folge ist aufgegeben; nichts läuft von selbst. | Folge startet nach langer Pause von selbst → melden |

## 5. Korrekturen

| Nr. | Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|---|
| K1 | Satz aus A1, **während „Zusammenfassen“ läuft**: „Ich meinte Seite 3“ | „Verstanden – Seite 3 statt Seite 2. Den laufenden Auftrag breche ich dafür ab.“ → Seite 3 wird zusammengefasst und gespeichert → die Mail bekommt **diese** Datei → „Fertig – mit deiner Korrektur sind alle drei Schritte erledigt. … 2. ‚Zusammenfassung Argon4 Seite 3 `<Datum>`.docx‘ …“ Keine Datei für Seite 2. | Mail mit der Seite-2-Datei; zwei Dateien → melden |
| K2 | P1 (Folge wartet auf die Auswahl), dann „Ich meinte Seite 3“ und „An `<zweite Adresse>`, nicht an `<Adresse>`“ | Je: „Verstanden – … Das gilt für Schritt 2 (bzw. 3); er ist noch nicht gelaufen.“ Nach „das erste“ läuft der geänderte Plan. | – |
| K3 | A1 ganz durchlaufen lassen, **direkt danach**: „Nicht als Word, als PDF“ | „Verstanden – als PDF statt als Word-Datei. Ich mache Schritt 2 damit neu.“ → dieselbe Zusammenfassung als PDF (ohne neue Wartezeit für das Modell), „Die Datei von vorhin (… .docx) gibt es weiterhin.“ → **Frage**: „Schritt 3 war schon erledigt: E-Mail an `<Adresse>` im Mailprogramm vorbereitet … Soll ich ihn mit dem neuen Ergebnis noch einmal ausführen?“ | Mail wird ohne Frage noch einmal vorbereitet → melden |
| K4 | „Ja“ | Mailprogramm mit der PDF im Anhang → „Fertig – mit deiner Korrektur … Von vorher gibt es weiterhin: ‚… .docx‘ gespeichert …; E-Mail an `<Adresse>` im Mailprogramm vorbereitet, Anhang ‚… .docx‘ – gesendet habe ich nichts (das lässt sich nicht zurücknehmen).“ | – |
| K5 | K3 wiederholen, dann „Nein“ | „Fertig bis auf das, was du nicht wiederholt haben wolltest. … Nicht wiederholt: 3. Per E-Mail an `<Adresse>` schicken.“ Kein zweites Mailfenster. | – |
| K6 | A1 durchlaufen lassen, direkt danach: „An `<zweite Adresse>`, nicht an `<Adresse>`“ | Mailprogramm mit der zweiten Adresse und derselben Datei; Bericht nennt die erste Mail als „von vorher“. | – |
| K7 | A1 durchlaufen lassen, **erst etwas anderes sagen** („Danke“), dann K6 | Keine Korrektur der Folge mehr (sie gilt nur für den Zug direkt danach). | Mail wird trotzdem neu vorbereitet → melden |

Namen ohne Adresse („an Bernd, nicht an Anna“) werden nur erkannt, wenn sie großgeschrieben in der Sprechblase stehen.

## 6. Fehler und Stopp

| Nr. | Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|---|
| S1 | Satz aus A1 mit „Seite 99“ | „… hat nur N Seiten – eine Seite 99 gibt es nicht …“ → „Die Auftragsfolge endet bei Schritt 2 von 3. Erledigt: 1. ‚Argon4.pdf‘ geöffnet (Ordner Download). Nicht geklappt: 2. Seite 99 zusammenfassen und als Word-Datei speichern – ‚Argon4.pdf‘ hat nur N Seiten – eine Seite 99 gibt es nicht. Nicht begonnen: 3. Per E-Mail an `<Adresse>` schicken.“ Kein Mailfenster. | Mailfenster trotz Fehler → melden |
| S2 | Satz aus A1 mit einem Namen, den es nicht gibt | Ende bei Schritt 1, nichts zusammengefasst, nichts gespeichert, kein Mailfenster; die Ablage bietet wie bisher die Android-Auswahl an. | – |
| S3 | Satz aus A1, während „Speicher durchsuchen“ läuft: „Stopp“ (oder die Stopp-Schaltfläche) | „Gestoppt. … ich habe nichts geöffnet.“ → „Die Auftragsfolge ist gestoppt. Erledigt war noch nichts. Abgebrochen: 1. … Nicht begonnen: 2. … 3. …“ | Schritt 2 startet trotzdem → melden |
| S4 | Satz aus A1, während „Zusammenfassen“ läuft: „Stopp“ / Stopp-Schaltfläche | „Gestoppt. Seite 2 wurde nicht fertig; es wurde nichts gespeichert.“ → „Die Auftragsfolge ist gestoppt. Erledigt: 1. ‚Argon4.pdf‘ geöffnet (Ordner Download). Abgebrochen: 2. … Nicht begonnen: 3. …“ Keine Datei im Download-Ordner, kein Mailfenster. | Datei oder Mail nach dem Stopp → melden |
| S5 | Satz aus N1, während die Nachrichten gelesen werden: Stopp-Schaltfläche | „Die Auftragsfolge ist gestoppt. Erledigt war noch nichts. Abgebrochen: 1. Die Nachrichten von heute zusammenfassen. Nicht begonnen: 2. … 3. …“ | – |
| S6 | Modell entladen, Satz aus A1 | Ende bei Schritt 2 mit dem Grund „kein Sprachmodell geladen“, keine Datei, kein Mailfenster. | – |

## 7. Mit „E-Mails über Gmail selbst senden“ (eingeschaltet)

Hier wird wirklich gesendet. Nur mit deiner eigenen Testadresse.

| Nr. | Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|---|
| G1 | Satz aus N1 | Schritt 3 übernimmt die Sende-Lenkung: Gmail öffnet sich mit Empfänger und PDF, der Entwurf wird geprüft, **einmal** gesendet → „Gesendet an `<Adresse>` (Gmail): …“ → „Fertig – … 3. E-Mail an `<Adresse>` über Gmail gesendet, diese Datei hängt an.“ | Zweimal gesendet; gesendet ohne Anhang; „gesendet“ gemeldet, aber nichts im Gesendet-Ordner → sofort melden, „Sende-Diagnose“ aufzeichnen |
| G2 | „Fass mir die Nachrichten von heute zusammen und schick sie per Mail an `<Adresse>`“ (ohne Speichern) | Der Mailtext wäre vom Modell: Die Sende-Lenkung zeigt ihn und fragt „… Soll ich das so senden?“ – die Folge wartet. „Ja“ sendet; „abbrechen“ beendet ohne Senden. | Gesendet ohne Frage → sofort melden |

## 8. Modellpläne (optional, Schalter aus)

Standard: **aus**. Dann passiert bei einem frei formulierten Ziel nichts Neues.

| Nr. | Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|---|
| M1 | „Aufgabenketten mit Modellplan an“, dann: „Mach mir aus Seite 2 vom PDF Argon4 eine Word-Datei, die dann per Mail an `<Adresse>` geht“ | Das Modell schlägt Schritte vor; nach der Prüfung: „Ich würde das in drei Schritten machen: 1. … 2. … 3. … Soll ich das so machen?“ **Nichts läuft.** | Etwas läuft vor deinem Ja → sofort melden |
| M2 | „Ja“ | „In Ordnung, ich fange an.“, dann Ablauf wie A1. „Nein“ oder jeder andere Satz: „In Ordnung, ich mache nichts davon.“ bzw. der Plan verfällt. | – |
| M3 | Wenn stattdessen eine normale Antwort kommt | Der Vorschlag wurde verworfen (falsches Format, etwas Erfundenes, kein Plan). In der Aktivitätszeile steht „AUFTRAGSFOLGE · Modellplan verworfen · `<Grund>`“. | Grund und deinen Satz melden – das zeigt, was dein Modell aus der Planaufgabe macht |
| M4 | „Aufgabenketten mit Modellplan aus“ | Wieder wie vorher. | – |

Mit Claude als Gehirn wird **kein** Modellplan angefragt (der Planauftrag geht nicht in die Cloud).

## 9. Was du bei einem Fehler zurückschickst

1. Den **Wortlaut deiner Sprechblase** (so wie die Spracherkennung ihn geschrieben hat).
2. Screenshot des Gesprächs vom Plan bis zum Bericht – oder bis dorthin, wo es hängt.
3. Die Aktivitätszeilen, die mit „AUFTRAGSFOLGE ·“ beginnen (Schrittnummer und Ende).
4. Was wirklich da ist: Dateien im Download-Ordner (Namen), ob das Mailprogramm oder Gmail offen war, was im
   Gesendet-Ordner steht.
5. Bei Wartezeiten: wie lange ungefähr je Schritt, und ob das Gerät warm wurde.
6. App-Version (Einstellungen → Über) und welches Modell geladen war.

## Bekannte Grenzen (kein Fehler)

- Höchstens sechs Schritte; mehr ergibt keinen Plan, der Satz geht seinen bisherigen Weg.
- Schritte können sein: PDF nach Namen öffnen oder suchen, eine Seite zusammenfassen und speichern, Nachrichten
  zusammenfassen, als PDF/Word speichern, per Mail schicken, neueste Datei bzw. Datei nach Namen suchen und öffnen,
  die gespeicherte Datei öffnen. **Nicht**: WhatsApp, Löschen, Installieren, Computer-Aufträge, „fass Seite 2
  zusammen“ ohne Speichern.
- „Schick es an Anna“ ohne „Mail“ oder Adresse ist in einer Folge kein Schritt (der Kanal ist nicht gesagt).
- Ein Satz, den eine Lenkung heute als **einen** Auftrag liest, bleibt ihr Auftrag (N4).
- Verneinungen, Bedingungen („wenn …“), Alternativen („oder“), Anführungszeichen und Doppelpunkte ergeben keinen
  Plan.
- Die Folge lebt im Arbeitsspeicher der App: Wird Continuum beendet, ist sie weg; schon Gespeichertes bleibt.
- Vorlesen: Jede neue Meldung unterbricht die vorige (wie überall in der App). Der gesprochene Plan kann deshalb vom
  Ergebnis des ersten Schritts überholt werden, eine lange Zusammenfassung vom nächsten Schritt; im Gespräch steht
  alles vollständig. Bitte melden, wenn dich das stört – dann wartet die Folge auf das Ende der Ansage.
- „Ich meinte die andere Datei“ / „die zweite“ ändert während einer laufenden Folge nichts; als Antwort auf die
  Rückfrage der Ablage funktioniert es wie bisher. „Nein, ich meinte das PDF Xenon5“ stellt die Folge um.
