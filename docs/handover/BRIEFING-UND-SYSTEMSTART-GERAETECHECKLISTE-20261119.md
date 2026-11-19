# Systemcheck, Tagesbriefing, „Ruf Anna an“ und Warnhinweise – Geräte-Checkliste (19.11.2026)

**Stand:** Auftragserkennung, Prüfliste und Wortlaut des Systemchecks, Zusammenbau des Briefings, Termin- und
Wettertexte, das tägliche Angebot, die Folgeangebote, die Auflösung im Adressbuch und die Warnhinweise sind auf dem Host
getestet – mit nachgestelltem Kalender, nachgestellter HTTPS-Verbindung, nachgestelltem Adressbuch und einer
nachgestellten Uhr. Die echte Activity läuft dabei unter Robolectric. **Auf dem Fold7 ist nichts davon gelaufen: alles
unten ist NOT_RUN**, bis du es probiert hast. Nur das Gerät kann zeigen: ob Play Protect die APK mit dem neuen
Kalender-Recht installieren lässt, was dein Kalender-Anbieter wirklich liefert (Google-, Samsung-, Exchange-Konten),
ob Open-Meteo von deinem Netz aus antwortet, was die Spracherkennung aus deinen Sätzen macht, wie das Briefing mit
deiner Stimme klingt und wie lange es dauert.

Die Antworten unten sind wörtlich aus den Tests übernommen. Wo „‹…›“ steht, setzt die App den echten Wert ein.

## 0. Was neu ist – in einem Absatz

Continuum sagt beim Systemcheck nur, was es in diesem Moment wirklich gelesen hat. „Alles läuft“ gibt es nur, wenn
jede einzelne Prüfung in Ordnung ist. Das Briefing liest deinen Kalender (nur lesen), holt das Wetter für einen Ort,
den du nennst (kein Standortrecht), und nennt offene Arbeit. Anrufen heißt: die Wähltastatur wird mit der Nummer
geöffnet – auf „Anrufen“ tippst du selbst. Nichts wird gesendet, geändert oder eingetragen. Das Netz wird nur für das
Wetter benutzt und nur über deine Internet-Freigabe („Aus“ · „Nachfragen“ · „Immer“).

## 1. Einmal vorbereiten

1. **APK installieren.** Neu im Manifest ist genau ein Eintrag: `READ_CALENDAR` („Kalender“). Das ist ein
   Laufzeitrecht: Android fragt dich danach – erst, wenn du zum ersten Mal nach Terminen fragst, und erst nach einer
   Erklärung der App. Kein Kalender-Schreibrecht, kein Anruf-Recht (`CALL_PHONE`), kein Standort, kein SMS, kein
   Benachrichtigungs-Listener. **Ob Play Protect diese APK normal installieren lässt, ist nicht geprüft.** Wenn Play
   Protect blockiert oder warnt: bitte den genauen Wortlaut melden.
2. **Noch nichts freigeben.** Abschnitt 3 braucht zuerst den Zustand „Kalender nicht erlaubt“.
3. **Zwei Testtermine** im Kalender für heute anlegen (einer mit Ort, z. B. „Zahnarzt, 14:00, Praxis …“), einen für
   morgen Vormittag, und einen ganztägigen Eintrag.
4. Du kannst alles sprechen oder tippen.

## 2. Systemcheck („Ich fahre die Systeme hoch …“)

| Nr. | Sag / tippe | Erwartet | Fehlerbild und was du melden sollst |
|---|---|---|---|
| S1 | „Systemcheck“ (oder „Wie ist dein Status?“, „Statusbericht“, „Läuft alles?“, „Fahr die Systeme hoch“) | Ein kurzer gesprochener Satz und darunter im Chat die Liste aller Prüfungen, je eine Zeile: `✓` in Ordnung · `!` auffällig · `✗` Fehler · `–` ausgeschaltet. Geprüft werden: Gehirn, Spracheingabe, Sprachausgabe, Internet, Dateizugriff, Bedienungshilfe, Shizuku, Akku, Wärme, Speicher, Offenes. | Ein Wert stimmt nicht mit dem Gerät überein (Akku, Speicher, Shizuku, Modell) → Zeile und echten Wert melden |
| S2 | Alles in Ordnung (Modell geladen, Internet „Nachfragen“ und verbunden, alle Freigaben an, Shizuku verbunden) | „Alles läuft, Heinz: Gemma ist geladen, die Spracheingabe steht und der Akku ist bei ‹82› Prozent.“ | „Alles läuft“ obwohl in der Liste ein `!`, `✗` oder `–` steht → **sofort melden** (das darf nie passieren) |
| S3 | Shizuku beenden und das Gerät warm werden lassen, dann „Systemcheck“ | „Zwei Dinge, Heinz: Shizuku läuft nicht, und das Gerät ist schon warm. In Ordnung: Gemma ist geladen, die Spracheingabe steht und der Akku ist bei ‹82› Prozent.“ | Probleme stehen nicht am Anfang → melden |
| S4 | Internet in der App auf „Aus“, Ton aus, dann „Systemcheck“ | „Heinz, was eingeschaltet ist, läuft: … Ausgeschaltet sind die Sprachausgabe und der Internetzugang.“ (Ausgeschaltetes ist kein Fehler, aber auch kein „Alles läuft“.) | – |
| S5 | Kein Modell geladen, dann „Systemcheck“ | Beginnt mit „Ein Punkt, Heinz: Es ist kein lokales Modell geladen.“ (bzw. „Zwei Dinge …“) | Es wird ein geladenes Modell behauptet → melden |
| S6 | Claude als Gehirn gewählt | Zeile „Gehirn: Claude gewählt · Schlüssel hinterlegt“. Es wird **nicht** behauptet, dass Claude erreichbar ist – nur Auswahl und Schlüssel. Ohne Schlüssel: `✗` „Claude gewählt · kein Schlüssel“. | – |
| S7 | Speicher fast voll (unter 10 % frei), dann „Systemcheck“ | Am Ende: „Soll ich eine Systemdiagnose machen?“ – erst bei „ja“ startet die vorhandene Systemdiagnose. Bei „nein“: „In Ordnung.“ | Die Diagnose startet ohne dein Ja → melden |

Über laufende Arbeit sagt der Bericht nur etwas, wenn wirklich etwas läuft („Gerade läuft eine Auftragsfolge.“). Sätze
wie „Ich koordiniere die Agenten“ oder „alle Systeme optimal“ kommen nicht vor.

## 3. Kalender-Freigabe (zuerst im Zustand „nicht erlaubt“ testen)

| Nr. | Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|---|
| K1 | „Welche Termine habe ich heute?“ | Fenster „Kalender lesen“ mit dem Text: „Für deine Termine lese ich den Kalender dieses Geräts – nur lesen, nichts wird geändert oder eingetragen. Die Termine bleiben auf dem Gerät. Android fragt dich gleich einmal nach der Berechtigung „Kalender“.“ Knöpfe „Nicht jetzt“ und „Weiter“. Android hat bis hierher **nicht** gefragt. | Android-Dialog erscheint sofort ohne Erklärung → melden |
| K2 | „Nicht jetzt“ | „Deine Termine kann ich nicht nennen: Der Kalenderzugriff ist nicht erlaubt. Du kannst ihn in den Android-Einstellungen unter Apps → Continuum → Berechtigungen einschalten.“ | Es werden trotzdem Termine genannt → melden |
| K3 | „Was steht heute an?“ (Briefing, Kalender weiter nicht erlaubt) | Beim allerersten Briefing dieselbe Erklärung wie K1; danach fragt das Briefing **nicht** wieder, sondern sagt: „Termine kann ich dir nicht nennen, der Kalenderzugriff ist nicht erlaubt.“ | Das Briefing fragt jedes Mal neu → melden |
| K4 | „Welche Termine habe ich heute?“ → „Weiter“ → in Android „Zulassen“ | „Heute hast du ‹zwei Termine›: um 9 Uhr ‹Titel› und um 14 Uhr ‹Titel› (‹Ort›).“ Ganztägiges extra: „Ganztägig: ‹Titel›.“ Leerer Tag: „Heute steht nichts im Kalender.“ | Ein Termin fehlt, steht am falschen Tag oder zur falschen Uhrzeit → Titel, Kalenderkonto (Google/Samsung/Exchange) und echte Zeit melden. Abgesagte und von dir abgelehnte Termine werden absichtlich nicht genannt. |
| K5 | „Was steht morgen im Kalender?“ | „Morgen hast du …“ bzw. „Morgen steht nichts im Kalender.“ | – |

In den Kalender wird nie geschrieben. „Trag einen Termin ein“ ist kein Auftrag dieser Lenkung.

## 4. Wetter (Ort festlegen, Freigabe)

| Nr. | Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|---|
| W1 | „Wie wird das Wetter?“ (ohne Ort) | „Für das Wetter fehlt mir dein Ort – sag zum Beispiel: Mein Ort ist Linz.“ | – |
| W2 | „Mein Ort ist Linz“ | Direkt nach W1: der Ort wird gespeichert und das Wetter sofort geholt. Sonst: „Gut, Linz ist jetzt dein Ort für das Wetter. Nachgeschlagen wird er erst, wenn du nach dem Wetter fragst.“ – es wird **nichts** gesendet. | – |
| W3 | Internet „Aus“ → „Wie ist das Wetter?“ | „Der Internetzugang ist AUS, deshalb habe ich kein Wetter abgerufen. Stell ihn unter „Datenschutz & Freigaben“ auf „Nachfragen“ oder „Immer“, dann hole ich es dir.“ | Es kommt trotzdem Wetter → **sofort melden** |
| W4 | Internet „Nachfragen“ → „Wie wird das Wetter morgen?“ | Der bekannte Freigabe-Dialog; im Text: „Für das Wetter frage ich den freien Wetterdienst Open-Meteo. Übertragen wird nur dein Ort „Linz“ beziehungsweise seine Koordinaten – kein Konto, kein Schlüssel und nichts aus deinem Auftrag.“ Nach „Ja“: „Morgen in Linz: ‹8 bis 12› Grad, ‹leichte Regenschauer›, Regenwahrscheinlichkeit ‹65› Prozent.“ Darunter „Quelle: Open-Meteo · Linz, Oberösterreich, Österreich“. Nach „Nein“: „Wetter nicht abgerufen: Der Internetzugriff wurde für diese Anfrage nicht freigegeben.“ | Falscher Ort (anderes Linz, anderes Land) → gefundenen Ort aus der Quellenzeile melden. Dienst antwortet nicht → Wortlaut melden („Das Wetter konnte ich nicht abrufen: …“) |
| W5 | Internet „Immer“ → „Wie ist das Wetter?“ | „In Linz sind es gerade ‹9› Grad, ‹überwiegend klar›. Heute ‹6 bis 12› Grad, ‹leichte Regenschauer›, Regenwahrscheinlichkeit ‹78› Prozent.“ | Werte weichen stark von einer Wetter-App ab → beide Werte melden |
| W6 | „Wie wird das Wetter in Wien?“ | Wetter für Wien, **ohne** deinen gespeicherten Ort zu ändern. | – |
| W7 | „Mein Ort ist Xyzabc“ → „Wie ist das Wetter?“ | „Den Ort „Xyzabc“ kennt der Wetterdienst nicht. Ich habe kein Wetter abgerufen.“ | Es wird ein „ähnlicher“ Ort genommen → melden |

Open-Meteo ist für nicht-kommerzielle Nutzung frei und braucht keinen Schlüssel (geprüft am 19.11.2026, Bedingungen:
unter 10 000 Abrufe pro Tag). Erreichbar sind nur `geocoding-api.open-meteo.com` und `api.open-meteo.com`, ohne
Weiterleitung.

## 5. Tagesbriefing

| Nr. | Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|---|
| B1 | „Was steht heute an?“ (oder „Gib mir mein Briefing“, „Guten Morgen, gib mir mein Briefing“, „Wie sieht mein Tag aus?“) | Gesprochen, etwa 20–30 Sekunden: „Heinz, heute ist ‹Donnerstag, der 19. November›. Heute hast du zwei Termine: um 9 Uhr Zahnarzt und um 14 Uhr 30 Besprechung mit Anna. Morgen früh: um 8 Uhr Flug nach Wien. In Linz sind es gerade 9 Grad, überwiegend klar. Heute 6 bis 12 Grad, leichte Regenschauer, Regenwahrscheinlichkeit 78 Prozent. Offen ist noch eine Arbeit: ‹Quelle: Schritt›. Soll ich die offene Arbeit zeigen oder die Nachrichten von heute zusammenfassen?“ Im Chat darunter dieselben Daten als Karten-Text (Termine heute · Wetter · Offene Arbeit). | Dauer deutlich über 40 Sekunden → Dauer und Zahl der Termine melden. Etwas wird genannt, das nicht stimmt → **genau** melden, was |
| B2 | Leerer Tag, kein Ort, Internet aus | „Heinz, heute ist ‹…›. Heute steht nichts im Kalender. Für das Wetter fehlt mir dein Ort – sag zum Beispiel: Mein Ort ist Linz. Offen ist nichts.“ Keine Frage am Ende (es gibt nichts zu wählen). | – |
| B3 | Ort gesetzt, Internet „Aus“ | Statt Wetter: „Wetter gibt es keines: Der Internetzugang ist ausgeschaltet.“ | – |
| B4 | Zwei Termine, die sich überschneiden | Zusatz: „Achtung: ‹Titel A› und ‹Titel B› überschneiden sich.“ | – |
| B5 | Abends, alle Termine vorbei | „Für heute steht nichts mehr an – ‹drei Termine sind› schon vorbei.“ | – |
| B6 | „Gib mir mein Briefing mit Nachrichten“ | Briefing endet mit „Die Nachrichten folgen gleich.“ Danach startet von selbst der bekannte Nachrichtenüberblick (eigene Freigabe, eigene Antwort). Internet aus: „Nachrichten gibt es keine: Der Internetzugang ist ausgeschaltet.“ | Nachrichten starten, obwohl du inzwischen etwas anderes gesagt hast → melden |
| B7 | „Nimm die Nachrichten ins Briefing auf“ / „Lass die Nachrichten im Briefing weg“ | „Gut, die Nachrichten gehören ab jetzt zu deinem Briefing. …“ / „Gut, dein Briefing kommt ab jetzt ohne Nachrichten.“ | – |

**Karten:** Die Daten liegen für die Bühne bereit (`ContinuumBriefingBoard.cards()`); gezeichnet werden sie von der
Bühne. Bis dahin siehst du sie als Text im Chat.

## 6. „Entscheide du die Aktion“ – Folgeangebote

Nach dem Briefing bietet Continuum höchstens drei Schritte an, die es wirklich ausführen kann. Ohne deine Wahl passiert
nichts.

| Nr. | Antwort auf die Frage | Erwartet |
|---|---|---|
| F1 | „die erste“ / „das zweite“ / „Nummer drei“ | Der gewählte Auftrag erscheint als dein Satz im Chat (z. B. „Welche Termine habe ich heute?“) und wird ausgeführt. |
| F2 | ein Stichwort: „den Termin“, „die offene Arbeit“, „die Nachrichten“ | wie F1 |
| F3 | „ja“ bei **einem** Angebot | wird ausgeführt |
| F4 | „ja“ bei mehreren Angeboten | „Welchen Schritt meinst du – den ersten, den zweiten oder den dritten?“ |
| F5 | „nein“, „später“, „jetzt nicht“ | „In Ordnung.“ – nichts wird ausgeführt |
| F6 | irgendein anderer Satz | Das Angebot ist vorbei; dein Satz wird ganz normal behandelt. |

Die Angebote: Termine mit Ort vorlesen · eine offene Nachricht öffnen (erst, wenn die Liste „Offene Nachrichten“
angeschlossen ist) · die offene Arbeit zeigen · die Nachrichten von heute zusammenfassen · nach einer Speicherwarnung
die Systemdiagnose.

## 7. „Ruf Anna an“

| Nr. | Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|---|
| A1 | „Ruf ‹eindeutiger Kontakt› an“ | Die Wähltastatur öffnet sich mit der Nummer. Continuum: „Die Wähltastatur ist offen, mit der Nummer von ‹Name›. Auf Anrufen tippst du selbst – ich wähle nicht.“ | Es wird **gewählt** (Rufaufbau ohne dein Tippen) → **sofort melden**. Falsche Nummer → Kontakt melden (mehrere Nummern? Mobil/Festnetz?) |
| A2 | „Ruf Anna an“ (zwei Anna im Adressbuch) | „Zu „Anna“ gibt es mehrere passende Nummern: ‹Anna A›, ‹Anna B›. Welche meinst du?“ → „die zweite“ → Wähltastatur. „nein“ → „In Ordnung, ich habe niemanden angerufen.“ | Es wird geraten → melden |
| A3 | „Ruf ‹unbekannt› an“ | „Im Adressbuch finde ich „‹unbekannt›“ nicht. Angerufen habe ich niemanden.“ | – |
| A4 | „Ruf 0664 1234567 an“ | Wähltastatur mit dieser Nummer, ohne Adressbuch. | – |
| A5 | Kontakte-Recht fehlt | Erst die Erklärung („Um die Nummer zu finden, lese ich einmal dein Adressbuch …“), dann Androids Frage. Bei Nein: „Ohne Zugriff auf die Kontakte finde ich die Nummer nicht. Angerufen habe ich niemanden. …“ | – |
| A6 | „Ruf Anna an und sag ihr, dass ich später komme“ | **Kein** Anruf-Auftrag (die App spricht nicht im Telefonat) – der Satz geht seinen bisherigen Weg. | Wähltastatur öffnet sich trotzdem → melden |

| A7 | „Ruf die Rettung an“ / „Ruf den Notruf an“ (auch Polizei, Feuerwehr, Notarzt) – **bitte nur ansehen, nicht wählen** | Kein Blick ins Adressbuch: „Die Wähltastatur ist offen, mit dem Euro-Notruf 112. Auf Anrufen tippst du selbst – ich wähle nicht.“ | Stattdessen „finde ich nicht“ oder eine andere Nummer → melden |

Bei einem Kontakt mit Mobil- und Festnetznummer wird die Mobilnummer genommen; zwei Mobilnummern sind eine Rückfrage.

## 8. Warnhinweise („Achtung …“)

Einmal pro Anlass, ruhig, nie mitten in einen laufenden Auftrag, eine Freigabe-Frage oder eine Auftragsfolge hinein.
Geprüft wird einmal pro Minute, solange Continuum im Vordergrund ist; der erste Blick frühestens eine Minute nach dem
Öffnen.

| Nr. | Anlass | Erwartet | Fehlerbild |
|---|---|---|---|
| H1 | Gerät wird warm (Android-Thermalstufe 2), Leistungsprofil „Automatisch“ | „Heinz, das Gerät wird warm – ich schalte einen Gang zurück.“ – nur wenn die Bühne ihre Effekte wirklich reduziert hat. In den Profilen „Sparsam“ und „Volle Qualität“ (oder im Energiesparmodus) ändert diese Wärmestufe nichts an den Effekten, dann nur: „Heinz, das Gerät wird warm.“ Ab Stufe 3: „Heinz, das Gerät ist stark erwärmt. Gönn ihm bitte eine Pause.“ | Der Satz kommt mehrmals hintereinander (öfter als alle 30 Minuten) → melden |
| H2 | Akku ≤ 20 % und lädt nicht | „Heinz, der Akku ist bei ‹18› Prozent und lädt nicht.“ (≤ 5 %: „… ist fast leer – nur noch ‹4› Prozent.“) | Hinweis kommt, obwohl geladen wird → melden |
| H3 | Speicher fällt **während der Nutzung** unter 10 % frei (z. B. große Datei kopieren, während Continuum offen ist) | „Heinz, der Speicher wird knapp: noch ‹7,5› Gigabyte frei. Soll ich eine Systemdiagnose machen?“ War der Speicher schon beim Öffnen knapp, kommt **kein** Hinweis (sonst käme er bei jedem Start) – das steht dann im Systemcheck und, wenn er fast voll ist, im Briefing („Noch etwas: …“). | Hinweis bei jedem Öffnen → melden |
| H4 | Shizuku war verbunden und bricht ab | „Heinz, die Verbindung zu Shizuku ist abgebrochen.“ (War Shizuku nie verbunden, kommt kein Hinweis – das steht im Systemcheck.) | – |
| H5 | Internet bricht während eines Web-Auftrags ab und ist danach noch weg | „Heinz, während des Abrufs ist die Internetverbindung abgebrochen. Sie ist noch nicht wieder da.“ | – |

Filmsätze wie „Datenüberlastung erkannt“ oder „Bedrohungsanalyse“ gibt es nicht.

## 9. Das tägliche Angebot und „Jeden Morgen um 7“

| Nr. | Sag / tippe | Erwartet |
|---|---|---|
| R1 | „Jeden Morgen um 7 das Briefing anbieten“ | „Gut, ich biete dir das Briefing einmal am Tag an – ab 7 Uhr, sobald du dich das erste Mal meldest. Von selbst wecken oder starten kann ich mich nicht: Android lässt eine App im Hintergrund nicht ungefragt sprechen.“ |
| R2 | „Biete mir kein Briefing mehr an“ / „Biete mir das Briefing wieder an“ | „In Ordnung, ich biete dir das Briefing nicht mehr von selbst an. Auf Wunsch bekommst du es jederzeit.“ / Angebot wieder an (die Uhrzeit bleibt). |

**Wichtig, damit keine falsche Erwartung entsteht:** Das Angebot („Soll ich dir dein Briefing für heute geben?“) wird
von der Begrüßung gesprochen, wenn du Continuum das erste Mal am Tag öffnest oder ansprichst – diese Verkettung wird
beim Zusammenführen angeschlossen und ist erst dann am Gerät sichtbar. Ein Briefing, das um 7 Uhr **von selbst** aus
dem Standby losspricht, gibt es nicht und wird nicht nachgebaut: Android erlaubt einer App im Hintergrund weder, sich
in den Vordergrund zu holen, noch ungefragt zu sprechen; die vorhandenen Routinen laufen als Hintergrund-Job ohne
Netz, ohne Stimme und nur für Lese-Werkzeuge. Ohne Tricks geht deshalb nur: einmal am Tag beim ersten Kontakt anbieten.

## 10. Sätze, die ihren bisherigen Weg behalten müssen

Bitte je einmal probieren – keiner davon darf ein Briefing, Wetter, einen Systemcheck oder die Wähltastatur auslösen:
„Was steht in der PDF?“ · „Wie wird das Wetter in dem Film dargestellt?“ · „Fass mir die Nachrichten von heute
zusammen“ (Nachrichtenüberblick wie bisher) · „Wie spät ist es?“ (Geräteuhr) · „Guten Morgen“ (nur Gruß) ·
„Systemdiagnose“ (ausführliche Diagnose wie bisher) · „Was ist noch offen?“ (Arbeitsjournal) · „Schreib Anna auf
WhatsApp, ich komme später“ (Sendeauftrag) · „Ruf Anna nicht an“ · „Wer hat angerufen?“ · „Ich habe morgen einen Termin
beim Zahnarzt“.

## 11. NOT_RUN – offen bis zum Gerätetest

- Installation mit `READ_CALENDAR` unter Play Protect.
- Der echte Kalender-Anbieter: Konten, wiederkehrende Termine, Zeitzonen-Termine, ganztägige Einträge über mehrere
  Tage, sehr viele Einträge (gelesen werden höchstens 200 je Abfrage).
- Die echte Wähltastatur (Samsung Telefon) und ob sie die Nummer vollständig übernimmt.
- Open-Meteo aus deinem Netz: Antwortzeit, Ortsfindung für deinen Ort, Ausfall.
- Spracherkennung der neuen Sätze am Fold7 („Systemcheck“, Ortsnamen, „Ruf … an“).
- Sprechdauer und Klang des Briefings mit deiner Stimme; Verhalten im Gesprächsmodus.
- Die Warnhinweise unter echten Bedingungen (Thermalstufe 2, Akku, voller Speicher, Shizuku-Abbruch).
- Karten und Angebote auf der Bühne (werden von der Bühne gezeichnet), Tippen auf ein Angebot.
- Die Verkettung Begrüßung → Systembericht → Briefing-Angebot.
