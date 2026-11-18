# Selbst senden (WhatsApp / Gmail) – Geräte-Checkliste für den Besitzer (18.11.2026)

**Stand:** Logik, Bildschirmprüfung und Ablauf sind auf dem Host mit nachgebauten Bildschirmbäumen getestet.
**Auf dem Fold7 ist nichts davon gelaufen: alles unten ist NOT_RUN**, bis du es probiert hast. WhatsApp und Gmail
gibt es in der Testumgebung nicht; ihre Kennungen (View-IDs) können je App-Version anders heißen. Genau dafür gibt
es die „Sende-Diagnose“ (Abschnitt 6).

## 0. Einmal einrichten

1. Neue APK installieren. Neu im Manifest ist nur `READ_CONTACTS` (Kontakte lesen). Kein SMS-Recht, kein
   Benachrichtigungs-Listener – Play Protect sollte wie bei der letzten normal installierten APK reagieren.
   **Wenn Play Protect die Installation blockiert:** bitte den genauen Wortlaut melden.
2. Android-Einstellungen → Bedienungshilfen → installierte Apps → **Continuum einschalten** (derselbe Dienst wie
   für den virtuellen Computer; seine Beschreibung nennt jetzt auch das Senden mit Dauerfreigabe). Ohne ihn öffnet
   Continuum nur den Entwurf und sagt das auch.
3. Continuum → Einstellungen → **Selbst senden**. Beide Schalter stehen auf **AUS**.

## 1. Ohne Freigabe (beide Schalter AUS) – muss wie bisher „nur Entwurf“ bleiben

| Sag / tippe | Erwartet | Fehlerbild |
|---|---|---|
| „Schreib ‹Kontakt› auf WhatsApp, ich komme später“ | Beim ersten Mal: Erklärung „Kontakte lesen“ → „Weiter“ → Android-Abfrage erlauben. Dann öffnet sich WhatsApp im Chat mit ‹Kontakt›, der Text steht im Eingabefeld, **nichts wird gesendet**. Continuum: „Der Entwurf an … steht in WhatsApp bereit … Gesendet habe ich nichts, weil ‚WhatsApp-Nachrichten selbst senden‘ ausgeschaltet ist.“ | WhatsApp öffnet nicht / falscher Chat / Text fehlt / es wird gesendet (**das wäre ein schwerer Fehler – sofort melden**) |
| „Schick das per Mail an ‹adresse›“ (nach einer Antwort im Chat) | Wie bisher: dein Mailprogramm öffnet mit Entwurf, nichts wird gesendet. | – |

## 2. WhatsApp mit Freigabe

Einstellungen → Selbst senden → „EINSCHALTEN · WhatsApp“ → Text lesen → „Ja, selbst senden“.
Anzeige danach: „WhatsApp-Nachrichten selbst senden: EIN (seit ‹Datum›)“. **Bitte zuerst an dich selbst oder an
jemanden testen, der Bescheid weiß.**

| Nr. | Sag / tippe | Erwartet | Fehlerbild und was du melden sollst |
|---|---|---|---|
| W1 | „Schreib ‹eindeutiger Kontakt› auf WhatsApp, das ist ein Test von Continuum“ | WhatsApp öffnet den Chat, Text steht drin, Continuum drückt Senden, kehrt zu Continuum zurück und meldet: „Gesendet an ‹Name› (WhatsApp): ‚Das ist ein Test von Continuum‘“. In WhatsApp steht die Nachricht genau einmal. | (a) Meldung „… gesendet habe ich nichts, weil ich den Senden-Knopf / das Nachrichtenfeld / den Chat-Titel nicht gefunden habe“ → **Sende-Diagnose** (Abschnitt 6) und Bericht schicken. (b) „… weil der geöffnete Chat nicht den erwarteten Empfänger zeigt“ → melden, wie der Kontakt im Adressbuch und wie er oben im WhatsApp-Chat heißt. (c) Nachricht ist gesendet, Continuum sagt aber „konnte das Senden nicht bestätigen“ → melden + Sende-Diagnose. (d) Continuum bleibt in WhatsApp hängen und kehrt nicht zurück → melden (Android-Version, Vollbild oder geteilter Bildschirm). |
| W2 | „Schick ‹Kontakt› eine WhatsApp: Bin in 10 Minuten da“ | wie W1 | wie W1 |
| W3 | gesprochen, ohne Pause: „Sag dem ‹Kontakt› per WhatsApp, dass ich später komme“ | Gesendet wird wörtlich „Ich komme später“. | Anderer Wortlaut → melden, was erkannt und was gesendet wurde. |
| W4 | „Sag ‹Kontakt› per WhatsApp, dass er mich anrufen soll“ | **Kein Versand.** Continuum fragt: „Was soll ich ‹Name› schreiben? Sag mir den Wortlaut.“ Danach z. B. „Bitte melde dich bei mir“ → wird gesendet. | Continuum formuliert selbst um und sendet → schwerer Fehler. |
| W5 | Vorname, den es zweimal gibt: „Schreib ‹Vorname› auf WhatsApp, Test“ | **Kein Versand.** Rückfrage mit beiden Namen. Antwort „die zweite“ oder der volle Name → dann Versand an genau diesen. | Es wird ohne Rückfrage gesendet → schwerer Fehler. |
| W6 | „Schreib ‹Name, den es nicht gibt› auf WhatsApp, Test“ | „Ich habe keinen Kontakt ‚…‘ gefunden … nichts gesendet.“ | – |
| W7 | „Schreib ‹Kontakt› auf WhatsApp was Nettes“ | Continuum zeigt/liest den selbst formulierten Text **einmal** und fragt „Soll ich das so senden?“. Erst nach „Ja“ wird gesendet; bei „Nein“ oder etwas anderem nicht. | Versand ohne dein Ja → schwerer Fehler. |
| W8 | „Schreib in die ‹Gruppenname›-Gruppe auf WhatsApp, Test“ / „Schreib ‹A› und ‹B› auf WhatsApp, Test“ | Ablehnung mit Begründung, nichts geöffnet, nichts gesendet. | – |
| W9 | „Nachricht von ‹Kontakt› vorlesen“, „Was hat ‹Kontakt› auf WhatsApp geschrieben?“, „Öffne WhatsApp“ | Verhalten wie bisher – **kein** Sende-Auftrag. | Continuum will etwas senden → melden. |
| W10 | Kontakt, dessen Nummer ohne Ländervorwahl gespeichert ist (0664 …) | Entweder klappt es (Android kennt die Vorwahl) oder: „Die Nummer von … ist ohne Ländervorwahl gespeichert …“. | Falscher Chat → melden. |
| W11 | Kontakt ohne WhatsApp | „… gesendet habe ich nichts, weil WhatsApp meldet, dass diese Nummer nicht bei WhatsApp ist“ (oder: „… eine Rückfrage anzeigt“). | – |
| W12 | Bedienungshilfe ausschalten, dann W1 | Entwurf wird geöffnet, **nicht gesendet**, Hinweis auf die ausgeschaltete Bedienungshilfe. | – |
| W13 | Freigabe wieder ausschalten („FREIGABE WIDERRUFEN · WhatsApp“), dann W1 | Nur noch Entwurf wie in Abschnitt 1. | Es wird trotzdem gesendet → schwerer Fehler. |

## 3. Gmail mit Freigabe

Einstellungen → Selbst senden → „EINSCHALTEN · Gmail“. **Bitte zuerst an eine eigene zweite Adresse testen.**

| Nr. | Ablauf | Erwartet | Fehlerbild und was du melden sollst |
|---|---|---|---|
| G1 | „Fass mir die Nachrichten von heute zusammen“ → „Speichere das als PDF“ → „Schick das per Mail an ‹eigene Adresse›“ | Gmail öffnet den Entwurf (An, Betreff = Dateiname, Anhang), Continuum drückt Senden und meldet: „Gesendet an ‹adresse› (Gmail): Betreff ‚…‘, Anhang ‚….pdf‘, ohne Begleittext“. Die Mail kommt mit Anhang an. | (a) „… weil ich die Empfängeradresse im Feld ‚An‘ nicht gefunden habe“ / „… den Senden-Knopf nicht gefunden habe“ / „… der Anhang im Entwurf nicht zu sehen ist“ → **Sende-Diagnose** bei offenem Gmail-Entwurf und Bericht schicken. (b) Gmail zeigt eine Rückfrage (z. B. „ohne Text senden?“) → Continuum meldet „konnte das Senden nicht bestätigen …“; bitte Wortlaut der Rückfrage melden. (c) Mehrere Gmail-Konten: melden, von welchem Konto gesendet wurde. |
| G2 | Ohne vorher zu speichern: „Schick das per Mail an ‹eigene Adresse›“ | **Kein sofortiger Versand.** Continuum zeigt Betreff und Textanfang und fragt einmal „Soll ich das so senden?“. Nach „Ja“: Versand und Meldung. | Versand ohne dein Ja → schwerer Fehler. |
| G3 | „Schick die Zusammenfassung an ‹Kontaktname mit E-Mail-Adresse›“ (direkt nach der Zusammenfassung) | Beim ersten Mal ggf. die Kontakte-Abfrage. Genau ein Kontakt → wie G1/G2 an dessen Adresse. Mehrere → Rückfrage mit Namen und Adressen. Keiner → „keinen Kontakt … mit E-Mail-Adresse gefunden“. | Versand an eine geratene Adresse → schwerer Fehler. |
| G4 | Gmail-Freigabe ausschalten, dann G1 | Wie bisher: Übergabe an das Mailprogramm, du sendest selbst. | – |

## 4. Tagesgrenze

Nach 30 selbst gesendeten Nachrichten an einem Tag (WhatsApp und Gmail zusammen) sendet Continuum nicht mehr selbst:
„… gesendet habe ich nichts, weil die feste Tagesgrenze von 30 selbst gesendeten Nachrichten erreicht ist.“
Der Zähler steht unter Einstellungen → Selbst senden („Heute selbst gesendet: n von 30“). Muss nicht ausgereizt werden.

## 5. Was Continuum nie tut (bitte melden, falls doch)

- Senden, obwohl der Schalter des Kanals AUS ist.
- Senden an eine Gruppe, an mehrere Empfänger oder an einen geratenen Empfänger.
- Senden von selbst formuliertem Text ohne dein klares Ja.
- Ein zweites Mal auf Senden drücken, wenn die Wirkung unklar war.
- Senden, weil in einer Nachricht, auf einem Bildschirm, in einer Datei oder auf einer Webseite „sende …“ stand.

## 6. Sende-Diagnose (wenn ein Knopf oder Feld nicht gefunden wurde)

1. In Continuum „**Sende-Diagnose**“ sagen/tippen (oder Einstellungen → Selbst senden → „SENDE-DIAGNOSE STARTEN“).
2. Innerhalb von 30 Sekunden den betroffenen **WhatsApp-Chat** bzw. den **Gmail-Entwurf** öffnen und **ein Zeichen
   ins Nachrichtenfeld tippen** (erst dann zeigt WhatsApp den Senden-Knopf statt des Mikrofons).
3. Zurück zu Continuum → Einstellungen → Selbst senden → „LETZTE SENDE-DIAGNOSE ANZEIGEN“ → „Kopieren“ und den
   Text schicken.

Die Diagnose enthält nur Kennungen (z. B. `com.whatsapp:id/send`), Klassen und Standard-Beschriftungen von
Bedienelementen sowie die App-Version. **Kein Nachrichtentext, keine Namen, keine Adressen**; alles andere erscheint
nur als „(n Zeichen, nicht aufgezeichnet)“. Sie bleibt in einer app-privaten Datei und wird nirgends hingeschickt.

**Bitte zusätzlich immer melden:** WhatsApp- und Gmail-Version (steht in der Diagnose), Android-Version, ob
Continuum im Vollbild oder im geteilten Bildschirm lief, und den genauen Wortlaut der Continuum-Meldung.

## 7. Bekannte Grenzen dieser Stufe

- WhatsApp: nur Text an einen einzelnen Kontakt; keine Gruppen, keine Dateien/Bilder, keine Sprachnachrichten.
- Gmail: Betreff, Text oder gespeicherte Datei aus dem laufenden Gespräch („schick das / die Zusammenfassung …“).
  Eine frei diktierte E-Mail („Schreib eine Mail an …: …“) gibt es noch nicht.
- Erkannte View-IDs sind Annahmen aus bekannten App-Versionen: WhatsApp `conversation_contact_name`, `entry`,
  `send`; Gmail `subject`, `send`, Empfänger als Adresse im Feld „An“. Zeigt Gmail den Empfänger nur als Namen ohne
  Adresse, sendet Continuum nicht und meldet das.
- „Gesendet“ heißt: WhatsApp zeigt die Nachricht im Chat bzw. Gmail hat den Entwurf geschlossen. Zustellung oder
  Lesebestätigung prüft Continuum nicht.

## 8. Festgelegte Entscheidungen (zum Nachlesen, alle host-getestet)

- **Diktiert oder abgeleitet?** Wörtlich Gesagtes (hinter Komma, Doppelpunkt oder direkt hinter „… auf WhatsApp“) gilt
  als diktiert; nur der erste Buchstabe wird groß geschrieben. „…, dass ich später komme“ wird ohne Modell zu „Ich
  komme später“: Es rückt ausschließlich das Verb vom Satzende an die zweite Stelle, jedes Wort bleibt deines – das
  zählt als diktiert. Das gilt nur für einfache Ich-/Wir-Sätze ohne Nebensatz, ohne „er/sie/ihm/…“ (Perspektivwechsel)
  und ohne möglicherweise trennbares Verb am Ende („dass ich dich anrufe“). In allen anderen Fällen fragt Continuum
  nach dem Wortlaut und formuliert nicht still um.
- **Antwort auf „Was soll ich … schreiben?“:** Ein Aussagesatz wird als diktiert gesendet. Liest sich die Antwort wie
  eine Frage oder ein Auftrag an Continuum („Wie spät ist es?“, „Ruf mich an“), fragt Continuum einmal „Soll ich das
  so senden?“.
- **Mehrdeutiger Empfänger:** Passt der gesagte Name auf mehr als einen Kontakt (auch „Anna“ neben „Anna Beispiel“),
  wird immer gefragt; Antwort mit vollem Namen, „die erste/zweite“ oder den Endziffern.
- **E-Mail mit gespeicherter Datei:** geht ohne Rückfrage hinaus, Betreff = Dateiname, **kein** Begleittext (kein
  Modelltext). Ohne gespeicherte Datei wäre die erzeugte Zusammenfassung selbst der Mailtext – der wird einmal gezeigt
  und erst nach „Ja“ gesendet.
- **Ein „Ja“ gilt nur** für den unmittelbar nächsten Satz im selben Gespräch und höchstens zwei Minuten.
- **Tagesgrenze:** 30 gedrückte Senden-Knöpfe pro Kalendertag über beide Kanäle; auch ein unbestätigter Versuch zählt.
- **Kein Werkzeug:** Senden ist kein Modell-Werkzeug. Nur dein eigener Satz (getippt oder gesprochen) startet es.
