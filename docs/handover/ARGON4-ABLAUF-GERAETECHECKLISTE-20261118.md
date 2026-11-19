# „Öffne das PDF Argon4“ → „Fass Seite 2 zusammen und speichere es als Word“ – Geräte-Checkliste (18.11.2026)

**Stand:** Auftragserkennung, Namensvergleich, Ablauf (Suche → Rückfrage → Öffnen → Seite → Modell → Word-Datei →
Zurücklesen), Korrekturen und Stopp sind auf dem Host getestet – mit nachgestelltem Speicher, nachgestelltem
PDF-Leser und einem Stellvertreter für das Modell. **Auf dem Fold7 ist nichts davon gelaufen: alles unten ist
NOT_RUN**, bis du es probiert hast. Nur das Gerät kann zeigen: ob Play Protect die APK mit dem neuen Zugriff
installieren lässt, wie lange die Suche im echten Speicher dauert, ob Android den Text deiner PDF-Seiten liefert, wie
gut und wie schnell dein Modell die Seite zusammenfasst, wie warm das Gerät dabei wird und ob Word die Datei öffnet.

## 0. Einmal vorbereiten

1. **APK installieren.** Neu im Manifest ist genau ein Eintrag: `MANAGE_EXTERNAL_STORAGE` („Zugriff auf alle
   Dateien“). Den Zugriff bekommt die App **nicht** durch die Installation – du schaltest ihn in Schritt 2 selbst in
   den Android-Einstellungen ein und kannst ihn dort jederzeit wieder ausschalten. Kein SMS-Recht, kein
   Benachrichtigungs-Listener. **Ob Play Protect diese APK normal installieren lässt, ist nicht geprüft.** Wenn Play
   Protect blockiert oder warnt: bitte den genauen Wortlaut melden – dann bauen wir den Dateizugriff als Bauoption.
2. **Noch nichts freigeben.** Der erste Test (Abschnitt 1) braucht den Zustand „Zugriff nicht erlaubt“.
3. **Testdateien** in den internen Speicher legen (Dateimanager), PDFs **mit echtem Text** (kein Scan), mindestens
   drei Seiten:
   - `Download/Argon4.pdf`
   - `Documents/Argon4 Datenblatt.pdf` (irgendein zweites PDF, umbenannt)
   - für die Mehrdeutigkeit zusätzlich `Download/Argon4 Messprotokoll.pdf`
4. **Modell laden** (Modellverwaltung), dann **Flugmodus ein** (WLAN und Mobilfunk aus). Alles Folgende läuft ohne
   Netz.
5. Du kannst jeden Auftrag sprechen oder tippen. Gesprochen: Mikrofon antippen (Gesprächsmodus) oder diktieren und
   senden.

## 1. Verweigerte / fehlende Freigabe (zuerst testen)

| Nr. | Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|---|
| F1 | „Öffne das PDF Argon4“ | Arbeitsfenster: „Dateizugriff prüfen“ → „Dateizugriff fehlt“ (blockiert). Continuum: „Dafür fehlt mir der Zugriff auf deine Dateien: In Android ist ‚Zugriff auf alle Dateien‘ für Continuum nicht erlaubt. Ich habe nichts durchsucht und nichts verändert. Soll ich die Android-Einstellung dafür öffnen?“ | Es wird trotzdem etwas gefunden/geöffnet, oder Android-Dateiauswahl geht sofort auf → melden |
| F2 | „Nein“ | „In Ordnung – ich öffne nichts und habe nichts durchsucht. Du kannst die Datei auch selbst auswählen. Soll ich die Android-Dateiauswahl öffnen?“ Es öffnet sich **nichts**. | Einstellung oder Auswahl öffnet sich trotzdem → melden |
| F3 | „Ja“ (direkt danach) | Android-Dateiauswahl öffnet sich (der bisherige Weg). Zurück ohne Auswahl ist in Ordnung. | – |
| F4 | noch einmal „Öffne das PDF Argon4“ → „Ja“ | Continuum: „Ich öffne die Android-Einstellung. Schalte dort ‚Zugriff auf alle Dateien zulassen‘ für Continuum ein und komm zurück – dann suche ich ‚Argon4‘ sofort weiter.“ Die Android-Seite „Zugriff auf alle Dateien“ für Continuum öffnet sich. | Falsche Einstellungsseite / gar keine → melden (Text der Antwort) |
| F5 | **Ohne** einzuschalten zurück zu Continuum | „Der Zugriff auf alle Dateien ist weiterhin nicht erlaubt. Ich habe nichts durchsucht. Soll ich die Android-Dateiauswahl öffnen?“ | Keine Meldung, oder es wird gesucht → melden |
| F6 | „Öffne das PDF Argon4“ → „Ja“ → Schalter **einschalten** → zurück zu Continuum | Ohne weiteres Wort: „Der Zugriff auf alle Dateien ist jetzt erlaubt. Ich suche ‚Argon4‘.“ Dann läuft Abschnitt 2 von selbst weiter. | Es passiert nichts nach der Rückkehr → melden, ob Vollbild oder geteilter Bildschirm; danach „Öffne das PDF Argon4“ noch einmal sagen |

## 2. Lokale Suche und Öffnen (ein eindeutiger Treffer)

Dafür vorübergehend nur `Argon4.pdf` und `Argon4 Datenblatt.pdf` auf dem Gerät lassen.

| Nr. | Sag / tippe | Erwartet | Fehlerbild und was du melden sollst |
|---|---|---|---|
| S1 | „Öffne das PDF Argon4“ | Arbeitsfenster, Schritt für Schritt: „Dateizugriff erlaubt“ → „Speicher durchsuchen · N PDFs geprüft“ → „Speicher durchsucht · N PDFs geprüft“ → „2 Treffer · einer genau“ → „PDF öffnen“ → „PDF geöffnet · X Seiten“ → „Text gelesen · Y Zeichen“. Continuum: „Gefunden: ‚Argon4.pdf‘ – Ordner Download · ‹Größe› · geändert am ‹Datum›. Ich öffne es. Es gibt einen weiteren Treffer: 2. ‚Argon4 Datenblatt.pdf‘ (Documents). …“ Danach die bekannte Dokumentansicht („PDF gelesen: …“). | (a) Suche dauert länger als ~12 s oder meldet „… habe ich die Suche beendet; nicht alle Ordner wurden geprüft“ → melden: N, Dauer, ungefähre Zahl deiner Dateien. (b) N ist offensichtlich zu klein → melden, in welchem Ordner ein PDF fehlt. (c) „Kein auslesbarer Text (Scan)“ obwohl die PDF Text hat → Dateiname und Android-Version melden. (d) Größe/Datum/Ordner stimmen nicht mit dem Dateimanager überein → melden. |
| S2 | Varianten, jede einzeln: „Öffne die PDF-Datei Argon 4“ · „Zeig mir das PDF Argon4“ · „Öffne Argon4.pdf“ · „Such das PDF Argon4 und öffne es“ · gesprochen „Öffne das PDF Argon vier“ | Jedes Mal dieselbe Datei wie S1. | Android-Dateiauswahl öffnet sich stattdessen, oder „nicht gefunden“ → den **erkannten Wortlaut** aus dem Chat melden |
| S3 | „Ich meinte die andere Datei“ (direkt nach S1) | „Ich öffne ‚Argon4 Datenblatt.pdf‘ – Ordner Documents …“; die Dokumentansicht wechselt. Ebenso „Nein, Argon4 Datenblatt“ oder „Ich meinte die zweite“. | Es bleibt bei der ersten Datei → melden |
| S4 | „Such das PDF Argon4“ (ohne „öffne“) | Liste der Treffer, **nichts** wird geöffnet, Frage „Welches soll ich öffnen?“ → „das erste“ öffnet. | Es wird sofort geöffnet → melden |
| S5 | „Öffne eine PDF“ · „Lies diese PDF und erstelle mir eine Zusammenfassung als Entwurf.“ | Wie bisher: Android-Dateiauswahl. | Lokale Suche startet → melden |

## 3. Mehrdeutigkeit

`Argon4.pdf` vorübergehend umbenennen oder verschieben, sodass `Argon4 Datenblatt.pdf` und
`Argon4 Messprotokoll.pdf` bleiben (keine Datei heißt genau „Argon4“). Hinweis: Solange eine Datei **genau** so heißt,
wird sie geöffnet und die anderen werden nur genannt (S1) – das ist Absicht.

| Nr. | Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|---|
| M1 | „Öffne das PDF Argon4“ | „Zu ‚Argon4‘ habe ich 2 PDFs gefunden:“ nummerierte Liste mit Name, Ordner, Größe, Änderungsdatum; „Welches soll ich öffnen? …“. Arbeitsfenster: „2 Treffer“; die Trefferliste zeigt die beiden Dateinamen. **Nichts ist geöffnet.** | Eine Datei wird ohne Frage geöffnet → schwerer Fehler, melden |
| M2 | eine der Antworten: „die zweite“ · „Nummer 2“ · „2“ · „das Messprotokoll“ · „die neueste“ | Genau diese Datei wird geöffnet („Ich öffne ‚…‘ …“). | Falsche Datei → melden, was du gesagt hast und was erkannt wurde |
| M3 | nach M1 etwas ganz anderes fragen („Wie spät ist es?“), danach „die erste“ | Die Zwischenfrage wird normal beantwortet, „die erste“ öffnet danach noch die erste Datei (die Frage bleibt 10 Minuten offen). | – |
| M4 | nach M1: „die fünfte“ · „keines davon“ | „Diese Auswahl gibt es nicht. Ich habe 2 Treffer genannt – welchen meinst du?“ bzw. „In Ordnung, ich öffne keines davon.“ | – |
| M5 | Datei absichtlich falsch aussprechen/tippen: „Öffne das PDF Organ 4“ | „Ein PDF namens ‚Organ 4‘ habe ich nicht gefunden. … Ähnlich heißen: … Meinst du eines davon?“ – **nichts** wird von selbst geöffnet. | Es wird ohne Frage geöffnet → schwerer Fehler |

## 4. Fehlende Datei

| Nr. | Sag / tippe | Erwartet |
|---|---|---|
| N1 | „Öffne das PDF Xenon99“ | „Ein PDF namens ‚Xenon99‘ habe ich nicht gefunden. Geprüft habe ich N PDFs im internen Speicher (Ordner: Download, Documents, …). Wenn die Datei woanders liegt oder anders heißt, kannst du sie selbst auswählen. Soll ich die Android-Dateiauswahl öffnen?“ Arbeitsfenster: „Kein Treffer“. Nichts wird geöffnet oder erfunden. |
| N2 | „Nein“ bzw. „Ja“ | „Alles klar, ich öffne nichts.“ bzw. die Android-Dateiauswahl. |

## 5. Seite zusammenfassen und als Word speichern

`Argon4.pdf` wieder herstellen und mit S1 öffnen.

| Nr. | Sag / tippe | Erwartet | Fehlerbild und was du melden sollst |
|---|---|---|---|
| W1 | „Fass Seite 2 zusammen und speichere es als Word“ | Arbeitsfenster: „Seite 2 lesen“ → „Seite 2 gelesen · Y Zeichen“ → „Zusammenfassen“ (zählt Zeichen hoch; im Chat erscheint der Antwortentwurf, während das Modell schreibt) → „Zusammengefasst“ → „Word-Datei aufbauen“ → „In Downloads gespeichert“ → „Datei geprüft · ‹Größe›“. Continuum: „Seite 2 von ‚Argon4.pdf‘ – Zusammenfassung: … Gespeichert als Word-Datei: ‚Zusammenfassung Argon4 Seite 2 ‹Datum›.docx‘ in deinem Download-Ordner (Interner Speicher › Download), ‹Größe›. Soll ich sie öffnen?“ | (a) Die Zusammenfassung enthält Dinge, die nicht auf Seite 2 stehen, oder stammt von einer anderen Seite → Seite, Zusammenfassung und Modellname melden. (b) „… keine brauchbare Zusammenfassung geliefert (…)“ → Text in der Klammer melden. (c) „Zurücklesen konnte ich die Datei danach nicht“ → melden. (d) Zeit bis zum ersten sichtbaren Text und Gesamtzeit, wenn es dir zu lang vorkommt; ebenso spürbare Wärme. |
| W2 | „Ja“ | Die Word-Datei öffnet sich in deiner Word-/Office-App: Titel, Datum, Zeile „Quelle: ‚Argon4.pdf‘, Seite 2 von N · zusammengefasst vom Sprachmodell auf dem Gerät am … · vor der Verwendung prüfen.“, darunter die Zusammenfassung. | Datei lässt sich nicht öffnen oder ist leer → melden, mit welcher App |
| W3 | Im Dateimanager unter **Download** nachsehen | Die Datei liegt dort mit genau dem genannten Namen. | Name oder Ort weichen ab → melden |
| W4 | Varianten: „Fasse die zweite Seite zusammen und speichere sie als Word-Datei“ · „… als PDF“ · „… in meinen Downloads als Word“ · „Fass Seite 2 zusammen und speicher das als Word unter dem Namen Argon Kurzfassung“ | Jeweils eine neue Datei; nie wird eine vorhandene überschrieben („… (2).docx“). Mit Namen: `Argon Kurzfassung.docx`. | Auftrag wird nur im Chat beantwortet und **nicht** gespeichert → den erkannten Wortlaut melden (häufige Fehlhörung „als Wort“ ist abgedeckt) |
| W5 | „Fass Seite 2 zusammen“ (ohne Speichern) | Wie bisher: Antwort im Chat, keine Datei. | – |
| W6 | „Fass Seite 99 zusammen und speichere es als Word“ | „‚Argon4.pdf‘ hat nur N Seiten – eine Seite 99 gibt es nicht. Ich habe nichts zusammengefasst und nichts gespeichert.“ | – |
| W7 | Mit einer gescannten PDF (ohne Text) dasselbe | „Seite … hat keinen auslesbaren Text – zum Beispiel ein Scan. Ich habe nichts zusammengefasst und nichts gespeichert.“ | Eine erfundene Zusammenfassung → schwerer Fehler |
| W8 | Modell entladen, dann W1 | „Seite 2 … habe ich gelesen (Y Zeichen). Für die Zusammenfassung brauche ich aber das Sprachmodell, und im Moment ist keines geladen. Ich habe nichts zusammengefasst und nichts gespeichert. …“ – keine Datei. | Datei ohne Modell → schwerer Fehler |

## 6. „Ich meinte Seite 3“ – die Korrektur ändert die nächste Aktion

| Nr. | Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|---|
| K1 | nach W1: „Ich meinte Seite 3“ (auch: „Nein, Seite 3“ · „Nicht Seite 2, sondern Seite 3“ · „Seite drei meinte ich“) | Der ganze Auftrag läuft noch einmal für Seite 3. Antwort beginnt mit „Seite 3 von ‚Argon4.pdf‘ – statt Seite 2 – Zusammenfassung:“ und endet mit „… Die Datei von vorhin (Seite 2, ‚…Seite 2….docx‘) gibt es weiterhin. Soll ich sie öffnen?“ In Download liegen jetzt **beide** Dateien. | Nur eine Antwort im Chat ohne neue Datei, oder Seite 2 wird noch einmal gespeichert → melden |
| K2 | **während** W1 noch läuft (Schritt „Zusammenfassen“): „Nein, Seite 3“ tippen und senden – oder Mikrofon antippen und sagen | „Verstanden – Seite 3 statt Seite 2. Den laufenden Auftrag breche ich dafür ab.“ Danach läuft Seite 3; die Antwort sagt „Der abgebrochene Auftrag (Seite 2) hat nichts gespeichert.“ In Download liegt **keine** Datei für Seite 2 aus diesem Lauf. | Zwei Dateien, oder der Auftrag stoppt nur → melden |
| K3 | nach W1: „Ich meinte als PDF“ | Ohne neue Rechenzeit: „Erledigt – dieselbe Zusammenfassung von Seite 2 jetzt als PDF: ‚….pdf‘ … Die Datei von vorhin (Word-Datei, ‚….docx‘) gibt es weiterhin.“ | Das Modell rechnet noch einmal lange → melden |
| K4 | Ohne vorherigen Auftrag: „Ich meinte als PDF“ | Eine kurze Rückfrage: „Was soll ich als PDF speichern? Ich habe gerade nichts gespeichert, das ich ändern könnte.“ | – |
| K5 | „Nein, ich meinte den letzten Entwurf“ (bestehende Korrektur) | Verhalten wie bisher (Arbeitsverlauf). | Die Dokumentenablage antwortet stattdessen → melden |

## 7. „Stopp“

| Nr. | Ablauf | Erwartet | Fehlerbild |
|---|---|---|---|
| T1 | W1 starten, während „Zusammenfassen“ läuft die **STOP**-Taste (Senden-Knopf) drücken | Das Modell hört auf, die Schritte enden als fehlgeschlagen/gestoppt, Continuum: „Gestoppt. Seite 2 wurde nicht fertig; es wurde nichts gespeichert.“ In Download liegt keine neue Datei. | Datei trotzdem da, oder das Modell rechnet weiter (Gerät bleibt warm, STOP-Taste bleibt) → melden |
| T2 | W1 starten, „Stopp“ tippen und senden (auch „Halt“, „Abbrechen“) | Wie T1; „Stopp“ steht als dein Zug im Chat, das Eingabefeld ist leer. | – |
| T3 | Im Gesprächsmodus: W1 sprechen, dann **Mikrofon antippen** und „Stopp“ sagen | Wie T1. Zusätzlich endet – wie bisher bei „Stopp“ – der Gesprächsmodus („Gesprächsmodus beendet.“). Während das Modell rechnet, hört das Mikrofon nicht von selbst zu; deshalb das Antippen. | Auftrag läuft weiter → melden |
| T4 | „Öffne das PDF Argon4“ und sofort STOP | „Gestoppt. Die Suche ist abgebrochen; ich habe nichts geöffnet.“ (Bei wenigen Dateien ist die Suche meist schneller als dein Finger – dann ist das PDF einfach offen.) | – |
| T5 | Nach jedem Stopp denselben Auftrag noch einmal geben | Läuft normal von vorn. | Continuum bleibt „beschäftigt“ → melden |

## 8. Danach: öffnen und per Mail weitergeben (zweiter Ablauf)

| Nr. | Sag / tippe | Erwartet |
|---|---|---|
| D1 | nach W1: „Öffne die gespeicherte Datei“ | Die Word-Datei öffnet sich. |
| D2 | nach W1 (Flugmodus darf an bleiben, es wird nichts gesendet): „Schick das per Mail an ‹eigene Adresse›“ | Ohne Gmail-Freigabe: dein Mailprogramm öffnet einen Entwurf mit Betreff, Text der Zusammenfassung und der Word-Datei als Anhang; gesendet wird nichts. Mit Gmail-Freigabe gilt die Checkliste SELBST-SENDEN-GERAETECHECKLISTE-20261118.md (G1). |
| D3 | Flugmodus aus, Internet in Continuum erlaubt: „Fass mir die Nachrichten von heute zusammen“ → „Speichere das als PDF“ → „Schick das per Mail an ‹eigene Adresse›“ | Wie in der Selbst-senden-Checkliste (G1/G4): PDF in Download, Mail-Entwurf bzw. Gmail-Versand mit der PDF im Anhang. |
| D4 | nach D3-Schritt 2: „Ich meinte als Word“ | Dieselbe Nachrichten-Zusammenfassung zusätzlich als Word-Datei. |

## 9. Was Continuum hier nie tut (bitte melden, falls doch)

- Dateien durchsuchen, bevor „Zugriff auf alle Dateien“ eingeschaltet ist.
- Eine Datei öffnen, deren Name nur ähnlich klingt, ohne zu fragen.
- Eine Zusammenfassung speichern, die nicht vom geladenen Modell aus dem Text genau dieser Seite stammt.
- Eine vorhandene Datei überschreiben, verschieben oder löschen (es wird nur gelesen und eine **neue** Datei in
  Download angelegt).
- Für Suche, Öffnen, Zusammenfassen oder Speichern das Netz benutzen.
- Eine Datei als gespeichert melden, die es nicht gibt.

## 10. Wenn etwas nicht stimmt – bitte zurückschicken

1. Nummer des Schritts (z. B. „W1“) und ob gesprochen oder getippt.
2. Den **erkannten Wortlaut** deines Auftrags, wie er im Chat steht, und Continuums Antwort (kopieren oder Foto).
3. Foto/Screenshot des Arbeitsfensters mit den Schritten.
4. Die Zeilen aus dem Aktivitätsverlauf, die mit `DOKUMENTE ·` beginnen.
5. Dateiname, Ordner, ungefähre Seitenzahl der PDF; ob sie Text hat oder ein Scan ist.
6. Bei Zeit/Wärme: Sekunden bis zum ersten sichtbaren Text, Gesamtzeit, Modellname, ob das Gerät am Ladegerät hing.
7. Bei Play Protect oder der Freigabe: genauer Wortlaut der Android-Meldung.

## 11. Was auf dem Host geprüft ist – und was nur das Gerät zeigen kann

| Teil | Host (automatische Tests) | Gerät |
|---|---|---|
| Auftrag erkennen, Name herausnehmen, Abgrenzung zu den anderen Lenkungen | ja – `regression-library.jsonl` (160) und Prüfsatz (74), `OwnerLibraryCommandTest` | was die Spracherkennung wirklich schreibt: NOT_RUN |
| Namensvergleich, Rang, eindeutig/mehrdeutig/ähnlich/nichts | ja – `OwnerLibraryMatcherTest` | – |
| Gang durch den Speicher (Grenzen, Stopp, übersprungene Ordner) | ja, auf einem echten Verzeichnisbaum des Hosts – `OwnerLibraryWalkerTest` | Dauer und Vollständigkeit im echten Speicher mit „Zugriff auf alle Dateien“: NOT_RUN |
| Ablauf: Suche → Rückfrage → Wahl → Öffnen; nichts; Freigabe fehlt → Ja/Nein → Rückkehr; Seitenauftrag; Korrektur danach und währenddessen; Stopp; ohne Modell; Seite ohne Text; Seite gibt es nicht | ja, mit nachgestellter Gerätegrenze – `OwnerDocumentDeskTest` (30 Fälle) | NOT_RUN |
| Dieselben Haken in der echten `AgentActivity` (Eingabefeld, Senden/STOP-Knopf, eine Arbeitsspur, Dokumentansicht und Bindung an den Chat, echter Erzeugungsweg mit Stellvertreter-Modell, MediaStore mit Zurücklesen, Rückkehr aus den Einstellungen) | ja, Robolectric – `OwnerDocumentDeskActivityUiTest` (5 Fälle) | NOT_RUN |
| Word-Datei lesbar (Zip, `word/document.xml` wohlgeformt, Titel, Quellenzeile, Zusammenfassung) | ja – `OwnerPageSummaryTest`, `OwnerDocumentDeskTest` | Öffnen in Word/Office auf dem Gerät: NOT_RUN |
| PDF-Variante der Zusammenfassung | nur der Ablauf (Robolectric hat kein `PdfDocument`) | Schreiben der PDF: NOT_RUN |
| PDF lesen über den Dateipfad, einzelne Seite aus der Datei lesen (`PdfRenderer`, Text ab Android 15) | nein | NOT_RUN |
| Zusammenfassung durch das geladene Modell (Güte, Zeit bis zum ersten Text, Wärme) | nein – nur der Weg dorthin mit einem Stellvertreter | NOT_RUN |
| Play Protect und der neue Manifest-Eintrag; die Android-Seite „Zugriff auf alle Dateien“ | nur: genau ein neuer Eintrag, sonst nichts (`NormalInstallManifestTest`) | NOT_RUN |
| Zweiter Ablauf: Nachrichten → PDF → Mail (Übergabe mit Anhang; mit Gmail-Freigabe geprüfter Entwurf und Versand) | ja – `OwnerDeskAndWorkToolsChainTest`, `OwnerWorkToolsExportFlowTest` | Gmail, Mailprogramm, echte PDF: NOT_RUN |

## 12. Für die Integration (kurz)

- Logik: `OwnerDocumentDesk.kt` (Ablauf, Wortlaut, Schritte), `cognitive/OwnerLibraryCommand.kt` (Aufträge,
  Korrekturen, Antworten, Stopp), `cognitive/OwnerLibraryMatcher.kt` (Namensvergleich, Rang),
  `cognitive/OwnerPageSummary.kt` (Seitenaufgabe, Annahme, Dokument), `OwnerDeviceLibrary.kt` (Freigabe, begrenzter
  Gang durch den Speicher, PDF lesen).
- `AgentActivity.kt`: nur Haken (Eingang zweimal, Senden-Knopf, Command03-Eingabe, „Stopp“ im Gesprächsmodus,
  `onResume`) und der Adapter `OwnerDocumentDeskAdapter`; Tests ersetzen die Gerätegrenze über
  `ownerDeskFilesOverride`.
- Sätze: `docs/evaluation/continuum-eval-v1/regression-library.jsonl` (Entwicklung) und
  `regression-library-check.jsonl` (Prüfsatz, vor der Umsetzung geschrieben, wird nicht zum Abstimmen benutzt).
