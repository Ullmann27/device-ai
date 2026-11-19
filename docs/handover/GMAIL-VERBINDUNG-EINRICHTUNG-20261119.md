# Gmail mit Continuum verbinden – Einrichtung (19.11.2026)

Diese Anleitung ist für dich, Heinz, und für das Handy gedacht: alles geht im Browser des Fold7.
Einmal einrichten (etwa 15 Minuten), danach verbindest du Gmail in der App mit einem Tipp.

> **Wichtig vorab**
> - Continuum fragt **nie** nach deinem Google-Passwort. Du meldest dich nur auf Googles eigener Seite an.
> - In die App kommt **kein** Schlüssel, keine Client-ID und kein Geheimnis. Google erkennt die App am
>   Paketnamen und am Fingerabdruck (SHA-1) ihrer Signatur.
> - Deshalb funktioniert die Verbindung **nur in der original signierten Continuum-App**. Eine Test- oder
>   QA-Fassung („NICHT INSTALLIEREN“) hat eine andere Signatur – dort meldet die App
>   „Google kennt diese App-Fassung nicht“. Das ist dann kein Fehler deiner Einrichtung.
> - Die Bezeichnungen in der Google-Konsole **können in der Konsole leicht anders heißen** – Google benennt
>   Menüs öfter um. Gemeint ist immer der Punkt mit der ähnlichsten Bezeichnung.

## Die Werte, die du brauchst

| Wert | Eintrag |
|---|---|
| Paketname | `at.heinz.deviceai.continuumlocal` |
| SHA-1-Zertifikatsfingerabdruck | `E7:73:6C:2D:A5:4A:F0:30:D9:C8:3B:AA:80:B2:30:9D:DB:88:F8:42` |
| Rechte (Scopes) | `https://www.googleapis.com/auth/gmail.readonly`, `https://www.googleapis.com/auth/gmail.compose`, `https://www.googleapis.com/auth/gmail.send` |

Der SHA-1 stammt aus der veröffentlichten, original signierten APK. Er ist ein öffentlicher Fingerabdruck,
kein Geheimnis.

## Schritt 1 – Google-Cloud-Projekt anlegen

1. Öffne im Handy-Browser **console.cloud.google.com** und melde dich mit **dem Google-Konto an, dessen Gmail
   Continuum lesen soll**. (Wichtig für „Persönlich“ weiter unten: nur dieses Konto kann sich später verbinden.)
2. Oben links auf die Projektauswahl tippen → **„Neues Projekt“** (kann in der Konsole leicht anders heißen).
3. Name: zum Beispiel `Continuum Gmail`. Organisation: keine. **Erstellen**.
4. Warte, bis oben das neue Projekt ausgewählt ist.

## Schritt 2 – Gmail-API einschalten

1. Menü (☰) → **APIs & Dienste → Bibliothek** (kann in der Konsole leicht anders heißen).
2. Suche „Gmail API“ → antippen → **Aktivieren**.

Ohne diesen Schritt meldet die App später: „Im Google-Cloud-Projekt ist die Gmail-API nicht aktiviert.“

## Schritt 3 – Google Auth Platform: Branding

1. Menü (☰) → **Google Auth Platform** (früher „OAuth-Zustimmungsbildschirm“; kann in der Konsole leicht
   anders heißen). Beim ersten Öffnen: **„Jetzt starten“**.
2. **Branding / App-Informationen:** App-Name `Continuum`, Support-E-Mail: deine Adresse.
3. Kontaktdaten des Entwicklers: deine Adresse. Den Nutzerbedingungen zustimmen. **Erstellen**.

## Schritt 4 – Zielgruppe (Audience) – diese Wahl ist wichtig

Google bietet je nach Konto verschiedene Arten an:

- **„Persönlich“ / „Personal“ – wenn angeboten, nimm diese.** Dann kann sich nur das Konto verbinden, mit dem du
  das Projekt angelegt hast. Keine Prüfung durch Google, kein Warnbildschirm, kein Ablauf nach 7 Tagen.
  **Achtung: Diese Wahl ist einmalig** und lässt sich später nicht ändern.
- Wird „Persönlich“ nicht angeboten: **„Extern“** wählen und danach unter **Zielgruppe → Veröffentlichungsstatus**
  auf **„In Produktion“ / „App veröffentlichen“** stellen (kann in der Konsole leicht anders heißen).
  - Google zeigt beim Verbinden dann einen Hinweis **„Google hat diese App nicht überprüft“**. Das ist bei einer
    App nur für dich normal: Tippe auf **„Erweitert“ → „Weiter zu Continuum (unsicher)“**. Eine Prüfung durch
    Google ist nicht nötig, solange nur du die App verwendest (Google: „if you are the only user of your app“).
    Ohne Prüfung gilt eine Grenze von 100 Nutzern insgesamt – für dich allein spielt das keine Rolle.
  - **Nicht „Testen“ lassen:** Im Status „Testen“ laufen Freigaben nach **7 Tagen** ab. Continuum würde dann jede
    Woche „Berechtigung widerrufen“ melden und du müsstest neu verbinden.

## Schritt 5 – Datenzugriff (Data Access): die drei Rechte

1. **Google Auth Platform → Datenzugriff** (kann in der Konsole leicht anders heißen) → **„Bereiche hinzufügen
   oder entfernen“**.
2. Diese drei Bereiche auswählen (Suchfeld „gmail“ hilft):
   - `.../auth/gmail.readonly` – Mails lesen
   - `.../auth/gmail.compose` – Entwürfe anlegen
   - `.../auth/gmail.send` – Mails senden
3. **Aktualisieren**, dann **Speichern**.

Google stuft „readonly“ und „compose“ als *eingeschränkt*, „send“ als *sensibel* ein. Bei „Persönlich“ oder bei
nur dir als Nutzer ist trotzdem keine Prüfung nötig.

## Schritt 6 – Clients: den Android-Client anlegen

1. **Google Auth Platform → Clients** (kann in der Konsole leicht anders heißen) → **„Client erstellen“**.
2. Anwendungstyp: **Android**.
3. Name: `Continuum Android`.
4. Paketname: `at.heinz.deviceai.continuumlocal`
5. SHA-1-Zertifikatsfingerabdruck: `E7:73:6C:2D:A5:4A:F0:30:D9:C8:3B:AA:80:B2:30:9D:DB:88:F8:42`
6. **Erstellen**. Die angezeigte Client-ID musst du **nirgends** eintragen – sie gehört nicht in die App.

Es kann einige Minuten dauern, bis Google den neuen Client kennt.

## Schritt 7 – In der App verbinden

1. Continuum öffnen → **Einstellungen → Verbindungen → Google Gmail → Öffnen → „Konto verbinden“**.
   (Oder einfach sagen: „Verbinde Gmail“.)
2. Google zeigt **seine eigene Seite**: Konto wählen, ggf. den Hinweis „nicht überprüft“ wie oben bestätigen,
   dann das Recht **„E-Mails lesen“** erlauben.
3. Das musst du danach sehen:
   - in der App: „Gmail ist verbunden mit *deine Adresse*. Freigegeben: Lesen.“
   - unter Verbindungen: „Google Gmail · Verbunden“, darunter dein Konto.
4. **Entwürfe** und **Senden** gibst du **einzeln** frei – entweder auf derselben Seite („Entwürfe erlauben“,
   „Senden erlauben“) oder wenn du zum ersten Mal einen Entwurf verlangst; Google fragt dann nur nach diesem Recht.
5. Hinweis: Googles Entwurfsrecht erlaubt technisch auch das Senden. Continuum sendet trotzdem **nur** mit dem
   eigenen Senderecht und **erst nach deinem „Ja, senden“**, nachdem es dir Empfänger, Betreff und ganzen Text
   gezeigt hat.

## Was du dann sagen kannst

- „Zeig mir neue Mails“ · „Was ist neu in meinem Postfach?“
- „Suche Mails von Anna“ · „Suche Mails zu Rechnung“ · „Suche die letzte Mail von Peter“
- „die zweite“ · „Lies die Mail vor“ · „Lies den Verlauf vor“
- „Fass sie zusammen“ · „Fass meine wichtigsten Mails zusammen“ · „Warum sind die wichtig?“
- „Was steht da wegen Fristen?“
- „Schreib einen Entwurf an Anna: …“ · „Antworte ihm, dass …“ · „Schick die Mail ab“ → „Ja, senden“
- „Mach daraus ein PDF“ (nach einer Zusammenfassung)
- „Stopp“

**Mailinhalte und die Cloud:** Ist Claude als Gehirn gewählt, geht Mailtext **nur** an Claude, wenn du auf der
Gmail-Seite „Mailinhalte an die Cloud senden“ einschaltest (Standard: aus). Sonst fasst das Modell auf dem Gerät
zusammen – oder du bekommst nur Absender, Betreff und Datum.

## Trennen und Widerrufen

- In der App: „Trenne Gmail“ oder Verbindungen → Google Gmail → **Trennen**. Continuum widerruft bei Google alle
  Rechte und löscht Konto und Freigaben aus der App.
- Bei Google selbst: **myaccount.google.com/permissions** → Continuum → **Zugriff entfernen**. Die App zeigt danach
  „Berechtigung widerrufen“, sobald sie Gmail das nächste Mal braucht.

## Wenn es nicht klappt

| Meldung in der App | Ursache | Abhilfe |
|---|---|---|
| „Google kennt diese App-Fassung nicht …“ | Test-/QA-Fassung installiert, SHA-1 oder Paketname im Client falsch, oder Client noch nicht aktiv | Original signierte App verwenden; Schritt 6 prüfen; 10 Minuten warten |
| „… die Gmail-API nicht aktiviert“ | Schritt 2 fehlt | Gmail API aktivieren |
| „Die Google-Freigabe … gilt nicht mehr“ | widerrufen, oder Status „Testen“ (7 Tage) | Schritt 4 prüfen, neu verbinden |
| „Die Google-Freigabe wurde abgebrochen“ | Googles Seite geschlossen | noch einmal „Konto verbinden“ |
| „Der Internetzugang von Continuum ist ausgeschaltet …“ | Internetfreigabe AUS | auf „Nachfragen“ oder „Immer“ stellen |
| „… fehlen aktuelle Google-Play-Dienste“ | Play-Dienste alt/aus | Play-Dienste im Play Store aktualisieren |
| Warnbildschirm „nicht überprüft“ | Zielgruppe „Extern“ ohne Prüfung | „Erweitert → Weiter zu Continuum“ |

## Geräte-Checkliste (Fold7) – alles NOT_RUN

Nichts davon wurde auf einem Gerät oder gegen das echte Google ausgeführt. Bitte nach dem Einrichten prüfen:

| Nr. | Prüfung | Status |
|---|---|---|
| G1 | Original signierte App: „Konto verbinden“ zeigt Googles Seite, danach „Verbunden“ mit richtiger Adresse | NOT_RUN |
| G2 | QA-/Testfassung: klare Meldung „Google kennt diese App-Fassung nicht“, nichts verbunden | NOT_RUN |
| G3 | „Zeig mir neue Mails“ listet nur ungelesene Mails des Posteingangs, nummeriert | NOT_RUN |
| G4 | „die zweite“, „Lies den Verlauf vor“ lesen die richtige Mail; Umlaute korrekt | NOT_RUN |
| G5 | Zusammenfassung mit lokalem Modell; mit Claude ohne Cloud-Schalter geht kein Mailtext hinaus (nur Kopfzeilen) | NOT_RUN |
| G6 | „Schreib einen Entwurf an …“ fragt beim ersten Mal nur nach „Entwürfe“; Entwurf erscheint in Gmail unter Entwürfe | NOT_RUN |
| G7 | „Antworte ihm …“: Entwurf hängt im selben Verlauf, Betreff „Re: …“ | NOT_RUN |
| G8 | „Schick die Mail ab“ ohne Senderecht → Frage nach Google-Recht; mit Recht → volle Vorschau, gesendet nur nach „Ja, senden“ | NOT_RUN |
| G9 | Nach Widerruf unter myaccount.google.com/permissions zeigt die App „Berechtigung widerrufen“ | NOT_RUN |
| G10 | Flugmodus: „Verbindung unterbrochen“, kein „erledigt“ | NOT_RUN |
| G11 | Nach einer Stunde: weiterhin ohne neue Anmeldung nutzbar (Google erneuert still) | NOT_RUN |
| G12 | Verbindungen-Seite: jede Zeile zeigt den echten Zustand; jede Taste öffnet die richtige Stelle | NOT_RUN |
| G13 | Bühne: `mailWork` zeigt Phase, Zahl und Karten passend zu den echten Abrufen | NOT_RUN |
| G14 | Nach einem App-Neustart steht im Gesprächsverlauf kein Mailinhalt | NOT_RUN |
