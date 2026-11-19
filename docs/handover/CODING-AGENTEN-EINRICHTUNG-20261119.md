# Coding-Agenten (Claude, Cloud) – Einrichtung am Telefon (19.11.2026)

Die Coding-Agenten arbeiten selbstständig in einem deiner GitHub-Repositories und liefern einen **Entwurfs-Pull-Request**.
Sie laufen bei Anthropic („Claude Managed Agents“, dort als **Beta** gekennzeichnet) – nicht auf dem Telefon. Die App ist
die Fernbedienung: Auftrag bestätigen, Fortschritt sehen, Rückfragen beantworten, stoppen, Ergebnis prüfen.

**Stand: Am Host geprüft, am echten Dienst NICHT ausgeführt.** Es wurde kein einziger Aufruf an Anthropic oder GitHub
gemacht (kein Schlüssel, kostenpflichtig). Was das heißt, steht unten unter „NOT_RUN“. Der erste echte Lauf ist deiner.

## 1. Voraussetzungen (einmalig)

1. **Claude-Schlüssel und Zustimmung** wie bisher unter „Gehirn“: API-Schlüssel gespeichert, Datenübertragungs-Hinweis
   bestätigt. Claude muss dafür **nicht** als Gehirn ausgewählt sein.
2. **Managed Agents** muss für dein Anthropic-Konto verfügbar sein (Beta). Ob das so ist, zeigt Schritt 5 unten ohne Kosten.
3. **Empfohlen, auf GitHub:** Schutz für den Standardzweig einschalten (Repository → Settings → Branches → Regel für
   `main`: „Require a pull request before merging“). Grund, offen gesagt: Ein Token mit „Contents: Read and write“ darf
   technisch auch direkt auf `main` pushen und Pull Requests zusammenführen. Dagegen stehen die festen Regeln des Agenten,
   die Prüfung jedes Werkzeugaufrufs bei Anthropic und die Prüfung der App – technisch unmöglich macht es nur der
   Zweigschutz auf GitHub.

## 2. GitHub-Token erzeugen (im Browser am Telefon)

1. github.com → Profilbild → **Settings** → ganz unten **Developer settings** → **Personal access tokens** →
   **Fine-grained tokens** → **Generate new token**.
2. Name frei wählen (z. B. „Continuum Coding-Agent“), **Expiration** setzen (z. B. 30 Tage).
3. **Repository access:** „Only select repositories“ → nur die Repositories, in denen der Agent arbeiten soll (zuerst
   `Ullmann27/device-ai`).
4. **Repository permissions:**
   - **Contents: Read and write**
   - **Pull requests: Read and write**
   - Metadata: Read-only (setzt GitHub selbst)
5. **Nicht vergeben:** Administration, Secrets, Workflows, Actions, Environments, Webhooks – nichts weiter. Ohne
   „Workflows“ lehnt GitHub selbst jede Änderung unter `.github/workflows` ab.
6. **Generate token**, das Token (`github_pat_…`) kopieren.

Ein klassisches Token (`ghp_…`) nimmt die App nicht an: Dort lässt sich der Zugriff nicht auf einzelne Repositories und
Rechte begrenzen.

## 3. In der App eintragen

Bereich öffnen: **Agenten → „Coding-Agenten (Claude, Cloud)“** (im Team-Dialog unter „NICHT LOKAL · CLOUD“) oder sagen /
schreiben: **„Öffne die Coding-Agenten“**.

1. **Zustimmung:** „Hinweis lesen und zustimmen“ – der Hinweis sagt, was dein Gerät verlässt. Erst „ZUSTIMMEN“ speichert sie.
2. **GitHub-Token:** einfügen → „Token speichern“. Es wird verschlüsselt auf dem Gerät gespeichert (Android-Keystore),
   nie wieder angezeigt und beim Speichern nirgendwohin gesendet.
3. **Repository freigeben:** `Ullmann27/device-ai` eintragen → „Repository freigeben“. Nur Repositories dieser Liste
   können je ein Ziel sein.
4. **Grenzen** (Vorgaben): Budget je Lauf 2,00 $, höchstens 5,00 $, Laufzeit 30 Minuten, Modell `claude-opus-5-5`. Für den
   ersten Test genügt 1,00 $.
5. **„Bei Anthropic einrichten“** (optional, kostet keine Modell-Token): legt in deinem Anthropic-Konto einmal an
   – einen Agenten „Continuum Coding-Agent“ (feste Regeln, Werkzeuge), eine Sandbox-Umgebung
   `continuum-coding-agents-…` und einen Tresor „Continuum Coding-Agenten“ mit deinem GitHub-Zugang. Meldet die App hier
   „Beta nicht erreichbar“ oder „Berechtigung“, ist Managed Agents für dein Konto nicht freigeschaltet. Ohne diesen
   Schritt passiert dasselbe beim ersten Lauf.

## 4. Erster kleiner Testauftrag

Sagen oder schreiben, zum Beispiel:

> Coding-Agent: ergänze in device-ai in der Datei docs/handover/CODING-AGENTEN-EINRICHTUNG-20261119.md am Ende die Zeile
> „Testlauf bestanden“ und mach einen Pull Request

Was du siehst:

1. Die App liest bei GitHub den Standardzweig und zeigt **eine Bestätigung**: Repository, Ausgangszweig, dein Wortlaut,
   Budget, Laufzeit, Modell und „arbeitet auf dem neuen Zweig `continuum-agent/…`, öffnet einen Entwurfs-PR und führt
   nichts zusammen“. Bis hierher ist nichts bei Anthropic.
2. Nur **„Ja“** (oder der Knopf „Ja, starten“ im Dialog) startet. Jedes andere Wort startet nichts.
3. „Coding-Agent gestartet …“. Im Arbeitsfenster und im Dialog unter „Läufe“ laufen die echten Schritte mit (Befehle,
   geänderte Dateien, Ergebnisprüfung).
4. **Rückfrage:** Hält Anthropic einen Schritt zur Freigabe an, fragt die App: „Erlauben“ oder „Ablehnen“ (gesprochen
   oder per Knopf). Ist der angehaltene Schritt einer gegen die festen Regeln (Push auf `main`, Force-Push,
   Zusammenführen, Workflows, Secrets, Releases), lehnt die App ihn ab, ohne zu fragen. Führt Anthropic so einen Schritt
   ohne Rückfrage aus, kann die App ihn nicht verhindern – sie erkennt ihn am Befehl und nennt ihn im Bericht unter
   „Auffälligkeiten“; deshalb der Zweigschutz aus Abschnitt 1.
5. **Bericht** am Ende: Die App fragt GitHub selbst, was wirklich existiert – Zweig, Pull Request (Entwurf ja/nein, Link),
   Zahl der Commits und Dateien, ob `main` unverändert ist – dazu Kosten laut Anthropic, Dauer und, deutlich getrennt,
   der eigene Bericht des Agenten („seine eigenen Angaben, von der App nicht geprüft“).
6. Den Pull Request prüfst und führst **du** auf GitHub zusammen. Der Agent tut das nie.

Zweite Auftragsart: **„Coding-Agent: Sicherheitsprüfung für device-ai“** – Durchsicht des Codes dieses Repositories,
Fundstellen als Bericht im Entwurfs-PR, kleine eindeutige Korrekturen mit Test. Kein Scan fremder Hosts, kein Angriff auf
laufende Systeme.

## 5. Kosten und Budget

- Ein Lauf kostet Guthaben deines Anthropic-API-Kontos: **Token des Modells** und **Laufzeit der Sitzung** (laut
  Anthropics Dokumentation zum Listenpreis; Laufzeit dort mit 0,08 $ je Stunde angegeben).
- Jeder Lauf hat ein **hartes Budget bei Anthropic**: Vor jeder Modellanfrage prüft Anthropic den Verbrauch und hält die
  Sitzung an, wenn es erreicht ist. Die Anfrage, die die Grenze überschreitet, läuft noch zu Ende – der Endbetrag kann
  also um höchstens eine Modellanfrage über dem Budget liegen.
- Zusätzlich gilt dein **Monatslimit für Claude** („Gehirn“): Ein Lauf, dessen Budget es überschreiten würde, startet
  nicht. Läufe, deren Endkosten die App noch nicht lesen konnte, zählen mit ihrem ganzen Budget.
- „Budget erreicht“ ist in dieser Version ein Ende: Was auf GitHub existiert, steht im Bericht; Fortsetzen mit höherem
  Budget ist nicht eingebaut.

## 6. Stoppen, Verlauf löschen, Token entfernen

- **Stopp:** „Stopp den Coding-Agenten“ oder der Knopf „Stopp“ beim Lauf. Die App sendet die Unterbrechung; der Bericht
  nennt danach, was existiert. Kommt der Stopp nicht an (kein Netz), sagt die App das – die Sitzung läuft dann
  möglicherweise weiter, ihr Budget bleibt die Obergrenze.
- **Verlauf löschen:** „Läufe“ → „Verlauf bei Anthropic löschen“. Löscht dort die Sitzung mit Auftrag, Ereignisverlauf
  und Sandbox endgültig. Zweig und Pull Request auf GitHub bleiben.
- **Token entfernen:** löscht es auf dem Gerät und den Eintrag im Tresor bei Anthropic. Sitzungen, deren Verlauf du nicht
  gelöscht hast, tragen ihren eigenen Zugang weiter; ganz ungültig wird ein Token erst durch Widerruf auf GitHub.
- **App geschlossen oder Verbindung weg:** Die Sitzung arbeitet bei Anthropic weiter. Beim nächsten Öffnen liest die App
  sie einmal nach (Verlauf zuerst, dann live). Zeigt ein Lauf „Verbindung unterbrochen, Stand unbekannt“: „Aktualisieren“.
  Die Laufzeitgrenze prüft die App nur, solange sie läuft; das Budget gilt immer.

## 7. Was technisch eingestellt ist

| Punkt | Einstellung | Warum |
|---|---|---|
| Modell | `claude-opus-5-5` (änderbar unter „Grenzen“) | deine Vorgabe; die App wählt nie selbst ein anderes |
| Werkzeuge | Agent-Toolset (Shell, Dateien lesen/schreiben/ändern/suchen); Websuche und Web-Abruf **aus** | Arbeit in einem Repository braucht kein Web |
| Freigaberichtlinie | `auto` für alle Werkzeuge: Anthropic prüft jeden Aufruf, führt aus, lehnt ab oder hält zur Freigabe an | strengste Stufe, mit der der Agent noch arbeiten kann; `always_ask` würde jeden Befehl einzeln abfragen |
| Netz der Sandbox | `limited`: GitHub, die öffentlichen Paketquellen, Gradle-/Maven-/Google-Artefakte; keine MCP-Server, sonst nichts | ohne Paketquellen ließe sich nichts bauen oder testen |
| GitHub-Token | (a) im Tresor als Umgebungsvariable `GITHUB_TOKEN`, nur für `api.github.com`, nur im Anfragekopf eingesetzt; (b) je Lauf als Zugang des eingebundenen Repositories | laut Anthropic liegt es nicht in der Sandbox und das Modell sieht es nicht |
| Budget | je Sitzung, in Cent, von Anthropic durchgesetzt | harte Obergrenze |
| Kein Werkzeug ins Telefon | keine eigenen Werkzeuge, keine MCP-Server, keine Unteragenten | der Cloud-Agent kann am Gerät nichts bedienen |

## 8. NOT_RUN – ehrlich

Nicht ausgeführt und deshalb nicht belegt:

- **Jeder Aufruf an Anthropic:** Anlegen von Agent, Umgebung, Tresor; Start einer Sitzung; Ereignisstrom; Freigabe-Runde;
  Unterbrechung; Budget-Pause; Löschen. Geprüft ist nur, dass der echte SDK-Client die dokumentierten Anfragen sendet
  und die dokumentierten Antwortformen verarbeitet – gegen einen lokalen Stellvertreter.
- **Ob Managed Agents für dein Konto freigeschaltet ist** und welche Meldung Anthropic sonst liefert.
- **Der Lauf selbst:** Klonen, Arbeiten, Pushen des Zweigs, Anlegen des Entwurfs-PR über die GitHub-API mit dem
  Tresor-Platzhalter, ob sich ein Android-Build in der Sandbox ausführen lässt, wie gut die Ergebnisprüfung von Anthropic
  das Raster anwendet, wie oft `auto` zur Freigabe anhält.
- **Kosten:** kein gemessener Wert. Die 2 $ sind eine Vorgabe, keine Erfahrung.
- **GitHub mit deinem Token:** jede echte Antwort (Rechte, Zweigschutz, Pull Request).
- **Gerät:** Android-Keystore für das Token, der Dialog auf dem Fold7, Spracherkennung von „Coding-Agent“, Verhalten,
  wenn Android die App im Hintergrund beendet.

Am Host geprüft (JVM und Robolectric): Auftragserkennung (Entwicklungs- und Prüfsatz), jede Sperre vor dem Start,
Budget- und Monatslimit-Logik, Abbildung der Ereignisse auf Fortschritt, Freigabe, Stopp, Wiederverbinden und Neustart,
Wortlaut des Berichts, dass kein Geheimnis in Texten, Protokoll oder gespeichertem Stand landet, der Dialog und ein
vollständiger nachgestellter Lauf durch die echte Activity.

## 9. Offene Fragen an dich

1. Zweigschutz für `main` einschalten (Abschnitt 1, Punkt 3)? Ohne ihn ist „nie auf den Standardzweig“ eine Regel, keine
   technische Sperre.
2. Paketquellen in der Sandbox erlaubt lassen? Ohne sie kann der Agent kaum bauen oder testen und meldet „nicht geprüft“.
3. Erster Lauf mit 1,00 $ Budget – einverstanden?
4. Soll ein Lauf nach „Budget erreicht“ mit höherem Budget fortsetzbar werden?
5. Mehr als ein Lauf gleichzeitig ist nicht eingebaut. Brauchst du das?
