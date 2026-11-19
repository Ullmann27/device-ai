# „Hey Heinz, darf ich dich kurz stören?“ – Eingehende Nachrichten melden – Geräte-Checkliste (19.11.2026)

**Stand:** Einordnung nach Wichtigkeit, Bündelung, Gesprächsablauf (Frage → Vorlesen → „Möchtest du antworten?“ →
Diktat → Wiederholung → Sende-Lenkung), Ruhezeit, „Stör mich jetzt nicht“, Einstellungen und der Weg durch die echte
App-Oberfläche sind auf dem Host getestet – mit nachgebauten Benachrichtigungen, nachgebautem Telefon und ohne
Sprachausgabe. **Auf dem Fold7 ist nichts davon gelaufen: alles unten ist NOT_RUN**, bis du es probiert hast.

Der Kern, den nur das Gerät zeigen kann: **ob das Signal ohne Benachrichtigungsdienst überhaupt ankommt und was es
enthält.** Continuum bekommt die Nachricht nicht von WhatsApp oder Gmail, sondern von Android – als
„Benachrichtigungs-Ereignis“ an die Continuum-Bedienungshilfe, die du schon für den virtuellen Computer und das Senden
eingeschaltet hast. Es gibt dafür **kein neues Recht** und keinen Benachrichtigungsdienst (der bleibt Bauoption, bis
du einen Computer hast).

## Was ehrlich wann geht

| Zustand | Was Continuum tut | Was es nicht tut |
|---|---|---|
| **Continuum ist offen** (Vordergrund) | Fragt dich: bei eingeschalteter Sprache gesprochen (nur Offline-Stimme), sonst als Text im Gespräch und als Karte. Antwort im Gesprächsmodus per Stimme, sonst getippt oder per Tipp auf „Vorlesen“ / „Später“. | Es unterbricht nichts: keinen laufenden Auftrag, keine offene Rückfrage, nicht deine Stimme. Es öffnet außerhalb des Gesprächsmodus nicht von selbst das Mikrofon. |
| **Andere App vorne**, Bildschirm an | Eigene Continuum-Karte oben („Continuum · Nachricht – Eine Nachricht von … – vorlesen?“) mit „Vorlesen“ und „Später“. Wenn du es einschaltest: ein kurzer gesprochener Satz ohne Frage. | Kein Gespräch, kein Mikrofon, Continuum drängt sich nicht in den Vordergrund (Android erlaubt das nicht, und es wird nicht umgangen). Das Gespräch geht weiter, wenn du „Vorlesen“ tippst oder Continuum öffnest. |
| **Bildschirm aus / gesperrt** | Dasselbe wie im Hintergrund. **Aber:** bei gesperrtem Gerät gibt Android der Bedienungshilfe meist nur „in WhatsApp ist etwas eingegangen“ – ohne Absender und ohne Text. Dann meldet Continuum nur das und sagt es auch so. | Vorlesen und Antworten gehen dann nicht, weil Continuum den Inhalt nie bekommen hat. Du öffnest WhatsApp selbst („Öffne WhatsApp“). |

## 0. Einmal vorbereiten

1. **APK installieren.** Im Manifest ist **kein** neues Recht. Neu ist ein interner, nicht exportierter Empfänger für
   den Knopf „Später“ auf der Karte. Die Bedienungshilfe ist dieselbe wie bisher; ihre feste Konfiguration ist
   unverändert, nur ihr Beschreibungstext in den Android-Einstellungen nennt jetzt auch diese Funktion.
   **Ob Play Protect diese APK normal installieren lässt, ist nicht geprüft** – wenn es blockiert oder warnt, bitte
   den genauen Wortlaut melden.
2. **Bedienungshilfe prüfen:** Android-Einstellungen → Bedienungshilfen → Installierte Apps → **Continuum** → ein.
   (War sie schon ein, bleibt sie ein. Nach einem App-Update schaltet Android sie manchmal aus – dann wieder ein.)
3. **Funktion einschalten:** Continuum → Einstellungen → **Eingehende Nachrichten melden** → „EINSCHALTEN“. Es
   erscheint der Text, was Continuum dann sieht – lies ihn und bestätige mit „Ja, einschalten“.
   Erwartet danach: „Eingehende Nachrichten melden: EIN“ und darunter „Signal: Continuum-Bedienungshilfe ist ein und
   empfängt Benachrichtigungs-Ereignisse.“ Steht dort „noch nicht angefordert“ oder „AUS“ → melden (Screenshot).
4. **Apps:** vorgeschlagen sind WhatsApp, WhatsApp Business und Gmail (soweit installiert). Unter „APPS WÄHLEN“
   kannst du andere Messenger dazunehmen oder welche herausnehmen.
5. **Karte erlauben:** Android-Einstellungen → Apps → Continuum → Benachrichtigungen → erlaubt. Ohne das kann die
   Karte im Hintergrund nicht erscheinen (die offene Liste funktioniert trotzdem).
6. **Für das Antworten:** Einstellungen → „Selbst senden“ wie gehabt. Mit Dauerfreigabe WhatsApp sendet Continuum
   deine bestätigte Antwort selbst; ohne Freigabe bereitet es nur den Entwurf vor. Für eine Antwort per E-Mail braucht
   es die Gmail-Freigabe.
7. **Zweites Telefon** bereitlegen (oder jemanden bitten), das dir Testnachrichten schickt. Der Absender soll in
   deinen Kontakten stehen (für den Antwort-Test) – und einmal eine Nummer, die **nicht** in den Kontakten steht.
8. Für die Tests am besten **Ruhezeit ausschalten** („RUHEZEIT 22 BIS 7 UHR · aus“), sonst bleibt abends alles still.

## 1. Das Signal (zuerst testen – davon hängt alles ab)

Continuum offen, Sprache **an**, Gesprächsmodus **aus**, nichts läuft.

| Nr. | Tu | Erwartet | Wenn anders – bitte melden |
|---|---|---|---|
| S1 | Vom zweiten Telefon eine WhatsApp schicken: „Kommst du heute um fünf?“ | Nach etwa 4–6 Sekunden sagt und zeigt Continuum: „Hey, darf ich dich kurz stören? Du hast eine Nachricht von *(Name)* auf WhatsApp bekommen. Soll ich sie dir vorlesen?“ (Mit deinem Namen – „Hey Heinz, …“ –, sobald die App deinen Namen kennt.) Zusätzlich oben die Continuum-Karte mit „Vorlesen“ / „Später“. | **Nichts passiert** → wichtigster Befund. Bitte dazu: war das Gerät entsperrt? Hat WhatsApp selbst eine Benachrichtigung gezeigt (mit Ton)? Steht in den Einstellungen „empfängt Benachrichtigungs-Ereignisse“? |
| S2 | – | In der Frage steht der **Name** des Absenders. | Statt des Namens kommt „Auf WhatsApp ist etwas für dich eingegangen. Absender und Inhalt hat mir Android dazu nicht gezeigt …“ → dann liefert Android nur die Sammelmeldung von WhatsApp. Bitte genau so melden – dann geht Vorlesen auf diesem Weg nicht zuverlässig, und wir brauchen den Benachrichtigungsdienst als Bauoption. |
| S3 | „Ja“ tippen und senden (oder „Vorlesen“ auf der Karte) | „*(Name)* schreibt: „Kommst du heute um fünf?“ Möchtest du antworten?“ | Falscher oder abgeschnittener Text, fremder Absender, Text einer älteren Nachricht → Wortlaut melden. |
| S4 | „Nein“ | „In Ordnung.“ Es wird nichts gesendet, die Karte verschwindet. | – |
| S5 | Dasselbe mit einer **E-Mail** an dein Gmail von einer Person aus deinen Kontakten | „… Du hast eine E-Mail von *(Name)* in Gmail bekommen. Soll ich sie dir vorlesen?“ → „Ja“ → „E-Mail von *(Name)*. Betreff: … Der Anfang lautet: „…“ Die ganze E-Mail steht in Gmail. Möchtest du antworten?“ | Es kommt nichts (Gmail meldet nur den Posteingang „Allgemein“); Betreff und Absender vertauscht; nur „Mehr zeigt die Benachrichtigung nicht“ obwohl Gmail eine Vorschau zeigt → melden. |

**Wenn S1 und S2 nicht gehen, brauchst du die restlichen Abschnitte nicht zu testen – melde nur das.**

## 2. Gespräch per Stimme (Gesprächsmodus)

Gesprächsmodus starten (Mikrofon), nichts sagen, Nachricht schicken lassen.

| Nr. | Tu | Erwartet | Wenn anders |
|---|---|---|---|
| G1 | Warten, während das Mikrofon offen ist | Continuum spricht die Frage in einer Sprechpause (nicht in deinen Satz hinein). Danach hört das Mikrofon wieder zu. | Es redet dir ins Wort, oder die Frage kommt nie → melden, wie lange du gewartet hast. |
| G2 | „Ja“ sagen | Es liest vor und fragt „Möchtest du antworten?“ | „Ja“ wird als neuer Auftrag an das Modell geschickt → melden. |
| G3 | „Ja“ → auf „Gern. Was soll ich *(Name)* antworten?“ die Antwort diktieren | „Ich antworte *(Name)* auf WhatsApp: „…“. Soll ich das so senden?“ – erst nach deinem „Ja“ geht etwas hinaus. | Text falsch verstanden: „anders“ sagen, neu diktieren. Wird ohne dein Ja gesendet → **sofort melden**. |
| G4 | Bei einer neuen Nachricht gar nichts sagen | Nach 90 Sekunden ist die Frage vorbei; die Nachricht steht unter „Was ist noch offen?“. Nichts wird gesendet. | – |
| G5 | Bei einer neuen Nachricht „Später“ | „In Ordnung, ich merke sie mir. Frag mich einfach: Was ist noch offen?“ Später: „Was ist noch offen?“ → sie wird genannt und angeboten. | – |

## 3. Antworten über die Sende-Lenkung

| Nr. | Lage | Erwartet |
|---|---|---|
| A1 | WhatsApp-Dauerfreigabe **ein**, Absender in den Kontakten, Antwort bestätigt | Continuum öffnet den Chat, prüft Empfänger und Text und sendet einmal. Meldung der Sende-Lenkung: „Gesendet an *(Name)* (WhatsApp): „…““. Im Arbeitsfenster: Nachricht gemeldet → vorgelesen → Antwort gesendet. |
| A2 | Dauerfreigabe **aus** | Nur der Entwurf steht in WhatsApp; Meldung „Der Entwurf an … steht in WhatsApp bereit … Gesendet habe ich nichts“. |
| A3 | Zwei Kontakte mit demselben Vornamen wie der Absender | Rückfrage der Sende-Lenkung („Welchen meinst du?“) – deine Antwort entscheidet. |
| A4 | Absender ist eine unbekannte Nummer | Nach dem Vorlesen: „Der Absender steht nicht mit Namen in deinen Kontakten; antworte bitte direkt in WhatsApp.“ Keine Antwort-Frage. |
| A5 | E-Mail von einer Person, Gmail-Freigabe ein | „Ich schreibe … über Gmail eine neue E-Mail mit dem Betreff „Re: …“ …“ – das ist eine **neue** E-Mail an die Adresse aus deinen Kontakten, keine Antwort im selben Gmail-Verlauf. Ohne Gmail-Freigabe sagt Continuum das und sendet nichts. |
| A6 | E-Mail eines Instituts oder Gerichts | Wird vorgelesen; keine Antwort-Frage („Wenn du antworten möchtest, öffne bitte Gmail.“). |

## 4. Wichtig oder nicht (vor allem Gmail)

Schick dir (oder warte auf) je eine E-Mail dieser Art. Frag danach jeweils: **„Warum hast du das gemeldet?“** bzw.
**„Warum hast du das nicht gemeldet?“** – Continuum nennt Absender und Grund.

| Nr. | Nachricht | Erwartet |
|---|---|---|
| W1 | Werbung / Newsletter (Rabatt, Gutschein, „jetzt sichern“) | **Keine** Meldung. „Warum hast du das nicht gemeldet?“ → „… sieht nach Werbung aus („…“).“ |
| W2 | Bank, Versicherung, Schule, Ordination, Kanzlei | Meldung. Grund: „… ein Institut wie Bank, Versicherung, Schule, Arzt oder Kanzlei („…“).“ |
| W3 | Gericht, Finanzamt/FinanzOnline, Magistrat, BH, ÖGK, PVA, AMS | Meldung. Grund: „… nennen ein Gericht oder eine Behörde („…“).“ |
| W4 | Paket, Rechnung, Sicherheitswarnung (weder Person noch Institut noch Werbung) | Meldung mit dem Zusatz „Ich kann nicht einordnen, ob sie wichtig ist.“ Willst du solche gar nicht hören: „UNKLARE NACHRICHTEN KURZ MELDEN · aus“. |
| W5 | Eine Werbung, die du trotzdem hören willst: sag „Melde *(Absender)* immer“ | Ab der nächsten Nachricht dieses Absenders: Meldung. Umgekehrt: „Melde *(Absender)* nie“ oder nach einer Meldung „So etwas nie mehr melden“. Die Listen stehen auch in den Einstellungen. |

**Bitte melden:** jede wichtige Nachricht, die still blieb, und jede Werbung, die gemeldet wurde – mit Absender und
Betreff, wie Gmail sie in der Benachrichtigung zeigt. Die Wortlisten sind endlich; daraus werden sie ergänzt.

## 5. Bündel, Gruppen, Stummes, Eigenes

| Nr. | Tu | Erwartet |
|---|---|---|
| B1 | Drei WhatsApp kurz hintereinander vom selben Absender | **Eine** Frage: „… drei Nachrichten von … Soll ich sie dir vorlesen?“ – beim Vorlesen alle drei („… Dann: … Dann: …“). Kommt für jede Nachricht eine eigene Frage, oder fehlt eine → melden. |
| B2 | Eine Nachricht in einer WhatsApp-Gruppe | **Keine** Meldung (Gruppen sind aus). Nach „GRUPPEN-CHATS MELDEN · an“: „… von *(Name)* in der Gruppe *(Gruppe)* …“; Antworten in Gruppen gibt es nicht. Wird eine Gruppe als Einzelchat gemeldet oder umgekehrt → melden. |
| B3 | Einen Chat in WhatsApp stumm schalten, Nachricht schicken lassen | Keine Meldung. (Ob Continuum „stumm“ auf diesem Weg überhaupt erkennt, ist unsicher – bitte melden, wenn doch gemeldet wird.) |
| B4 | Selbst aus der WhatsApp-Benachrichtigung heraus antworten | Deine eigene Antwort wird **nicht** als neue Nachricht gemeldet. |
| B5 | WhatsApp-Anruf, Sprachnachricht abspielen, Backup, Medien-Upload | Keine Meldung. |
| B6 | Eine Nachricht mit einem Code („Dein Code ist 482913“) oder Link | Beim Vorlesen: „… eine Nachricht mit einem Code oder Passwort. Codes und Passwörter lese ich nicht vor …“; ein Link wird als „ein Link“ genannt, nicht vorgelesen. |

## 6. Beschäftigt, Ruhezeit, „Stör mich jetzt nicht“

| Nr. | Tu | Erwartet |
|---|---|---|
| R1 | Einen längeren Auftrag starten (z. B. PDF-Seite zusammenfassen) und währenddessen eine Nachricht bekommen | Während der Auftrag läuft: nichts. Danach: „Während du beschäftigt warst, kam eine Nachricht von … – soll ich sie dir vorlesen?“ |
| R2 | Während eine Rückfrage offen ist („Soll ich das so senden?“) eine Nachricht bekommen | Die Rückfrage bleibt allein; die Nachricht wird erst danach angeboten. Dein „Ja“ gilt der Rückfrage. |
| R3 | „Stör mich jetzt nicht“ | „In Ordnung, ich störe dich nicht …“ – neue Nachrichten werden nur gemerkt. „Was ist noch offen?“ geht trotzdem. „Du darfst wieder“ → „Während ich dich nicht stören sollte, kam …“ |
| R4 | Ruhezeit an, zwischen 22 und 7 Uhr | Keine Frage, keine Ansage; ist die App nicht offen, erscheint die Karte ohne Aufpoppen. Am Morgen (App offen): „Während der Ruhezeit kam …“ |

## 7. Hintergrund und Sperre

| Nr. | Tu | Erwartet | Bitte melden |
|---|---|---|---|
| H1 | Andere App öffnen (z. B. Browser), Nachricht schicken lassen | Nach einigen Sekunden die Continuum-Karte: „Continuum · Nachricht – Eine Nachricht von *(Name)* auf WhatsApp – vorlesen?“ mit „Vorlesen“ / „Später“. Kein Ton von Continuum. | Karte kommt nicht; Karte zeigt den Nachrichtentext (darf sie nie). |
| H2 | „Vorlesen“ auf der Karte | Continuum kommt nach vorne und liest vor (gesprochen, wenn Sprache an), dann „Möchtest du antworten?“ | App öffnet sich, aber es passiert nichts. |
| H3 | „Später“ auf der Karte | Karte weg, Continuum öffnet sich **nicht**; „Was ist noch offen?“ nennt sie später. | – |
| H4 | Einstellungen: „KURZE ANSAGE, WENN DIE APP NICHT OFFEN IST · an“, andere App vorne, Nachricht | Ein Satz, ohne Frage: „Hey, du hast eine Nachricht von … auf WhatsApp bekommen. Öffne Continuum, wenn ich sie vorlesen soll.“ Nur wenn das Telefon nicht stumm/auf Vibration steht und kein Anruf läuft. | Ob überhaupt etwas zu hören ist, über welchen Lautsprecher, und ob laufende Musik leiser wird. |
| H5 | Bildschirm aus und **gesperrt** (PIN/Fingerabdruck nötig), Nachricht | Auf dem Sperrbildschirm höchstens „Continuum – Eine Nachricht wartet.“ (kein Absender). Nach dem Entsperren und Öffnen: entweder die normale Frage mit Absender **oder** „Während du weg warst, ist auf WhatsApp etwas für dich eingegangen. Absender und Inhalt hat mir Android dazu nicht gezeigt …“ | **Welche der beiden Varianten kommt** – das ist die wichtigste Auskunft zum Sperrzustand. |
| H6 | Bildschirm aus, aber Gerät noch **nicht** gesperrt (direkt nach dem Ausschalten, oder „Entsperrt bleiben“/Smart Lock zu Hause) | Wie H1: Absender bekannt. | Ob es einen Unterschied zu H5 gibt. |
| H7 | Continuum-App über die Übersicht wegwischen, Nachricht | Solange die Bedienungshilfe eingeschaltet ist, sollte die Karte trotzdem kommen. | Wenn nicht: hält Samsung die App an (Akku-Optimierung)? |

## 8. Datenschutz – zum Nachsehen

- Im Gesprächsverlauf, den du später wieder öffnest, stehen deine eigenen Worte („ja“, deine diktierte Antwort) und die
  Meldung der Sende-Lenkung – **nicht** der vorgelesene Nachrichtentext und nicht die Frage von Continuum. Nach einem
  Neustart der App sind die offenen Nachrichten weg (sie liegen nur im Arbeitsspeicher, höchstens zwölf Stunden).
- Schaltest du die Funktion aus, ist die Liste sofort leer und die Karten verschwinden.
- Gesprochen wird der Nachrichteninhalt nur mit der Android-Offline-Stimme – auch wenn Maya als Stimme gewählt ist.
  Hat Android nur eine Netz-Stimme, wird der Inhalt gezeigt, nicht gesprochen. (Die Abschlussmeldung der
  Sende-Lenkung „Gesendet an …“ läuft wie bisher über die gewählte Stimme.)
- Mit dem Claude-Gehirn: stell danach eine beliebige Frage – Continuum darf vom Inhalt der Nachricht nichts wissen.

## 9. Was du bitte zurückmeldest

1. S1/S2: kommt das Signal, und mit Absender?
2. H5/H6: was kommt bei gesperrtem Gerät an?
3. B1: wird bei mehreren Nachrichten etwas doppelt gemeldet oder ausgelassen?
4. Abschnitt 4: falsch eingeordnete Absender und Betreffe (wörtlich).
5. Wie lange es von der WhatsApp-Benachrichtigung bis zur Frage dauert (gewollt sind rund 4 Sekunden Bündelzeit).
6. Ob die gesprochene Frage im Gesprächsmodus zum richtigen Zeitpunkt kommt, und ob das Mikrofon danach wieder zuhört.
7. Alles, was gesendet wurde, ohne dass du „Ja“ gesagt hast (darf nicht vorkommen).
8. Ob der virtuelle Computer und „Selbst senden“ wie vorher funktionieren (dieselbe Bedienungshilfe).

## 10. NOT_RUN und was an der Plattform unsicher ist

Nichts hiervon ist auf einem Gerät gelaufen. Unsicher – aus der Kenntnis des Android-Quelltexts abgeleitet, nicht
gemessen:

- **Ob und wann das Ereignis kommt.** Android schickt es für eine *neue* Benachrichtigung oberhalb der niedrigsten
  Wichtigkeit, die „Bitte nicht stören“ nicht unterdrückt; für eine *Aktualisierung* (zweite Nachricht im selben
  Chat) nur, wenn sie wieder Ton oder Vibration auslöst. Stumme Kanäle und „nur einmal benachrichtigen“ können also
  Folgemeldungen verschlucken.
- **Höchstens ein Ereignis je kurzem Zeitraum.** Die Bedienungshilfe ist auf 100 ms Sammelzeit eingestellt (für den
  virtuellen Computer; bewusst nicht geändert). Stellt eine App Nachricht und Sammelmeldung gleichzeitig ein, kommt
  nur das letzte der beiden Ereignisse an. Kommt nur die Sammelmeldung an, kennt Continuum keinen Absender (S2).
- **Sperrzustand.** Bei gesperrtem Gerät bekommt eine Bedienungshilfe für nicht öffentliche Benachrichtigungen nur
  die „öffentliche Fassung“ – meist nichts. Ob WhatsApp/Gmail auf deinem Gerät mehr liefern, weiß nur das Gerät.
- **Was in der Benachrichtigung steht.** Name statt Adresse, Betreff, Vorschau, Gruppen-Kennzeichen, der Aufbau bei
  mehreren ungelesenen Nachrichten, die Kanalnamen für stumme Chats – alles Annahmen über die aktuellen Versionen von
  WhatsApp und Gmail.
- **Schwärzung sensibler Inhalte (Android 15 und neuer).** Android kann Einmalcodes in Benachrichtigungen für nicht
  vertrauenswürdige Dienste schwärzen; ob das auch diese Ereignisse betrifft, ist offen. Continuum liest Codes
  ohnehin nie vor.
- **Arbeitsprofil / zweiter Benutzer.** Nicht betrachtet.
- **Android 16/17, One UI.** Ob Samsung oder „Erweiterter Schutz“ Bedienungshilfen ohne Hilfsmittel-Kennzeichnung
  einschränkt oder die App im Hintergrund anhält, ist offen.
- **Kein Signal, wenn eine Nachricht verschwindet.** Liest du die Nachricht inzwischen in WhatsApp, erfährt Continuum
  das auf diesem Weg nicht und bietet sie trotzdem noch an.
- **Sprache:** die gesprochene Frage, die Sprechpause im Gesprächsmodus, die Hintergrund-Ansage (Lautsprecher,
  Bluetooth, Lautstärke) und die Erkennung deiner kurzen Antworten („ja“, „später“) durch die Spracherkennung.
- **Play Protect** mit dieser APK; **Akku und Wärme** durch die zusätzlichen Ereignisse (erwartet: unbedeutend).
- **Antworten:** der Sendeablauf selbst ist die bestehende Sende-Lenkung (siehe
  `SELBST-SENDEN-GERAETECHECKLISTE-20261118.md`); eine Gmail-Antwort ist eine neue E-Mail, kein Verlauf.
