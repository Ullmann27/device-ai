# Wärme- und Zeitmessung · Anleitung für das Fold7 (18.11.2026)

**Stand: Auf keinem Gerät gelaufen. Jeder Messwert ist NOT_RUN, bis der Besitzer die Messung auf seinem Fold7
startet und die beiden Berichtsdateien zurückschickt.** In diesem Dokument steht keine Temperatur, keine Bildrate
und keine Antwortzeit eines Geräts – es gibt noch keine. Zahlen, die hier vorkommen, sind Wartegrenzen und
Abtastabstände der Messung selbst.

Besitzerauftrag: „Reproduziere mit Code68 die gemeldete Erwärmung beim Start eines lokalen Auftrags. Miss mit
identischen Sprachaufträgen und gleichen Startbedingungen die Thermalstufen, Abbrüche, Zeit bis zum ersten sichtbaren
Text, Gesamtzeit und Bildzeit. Vergleiche Bühnenlast, Gemma E4B und E2B sowie Gesprächswiederverwendung jeweils
einzeln. Optimiere zuerst den größten gemessenen Kostentreiber.“

Die App misst das selbst, auf dem Handy, ohne Rechner und ohne Internet. Optimiert wird erst, wenn Zahlen da sind.

## 1. Vorbereitung – immer gleich

Die Messung vergleicht Läufe miteinander. Das geht nur, wenn sie gleich starten.

1. **Ladekabel abziehen.** Gemessen wird ohne Ladegerät (Laden erwärmt den Akku selbst). Der Akku sollte gut gefüllt
   sein, weil die volle Messung lange dauert.
2. **Energiesparmodus aus.**
3. **Helligkeit fest einstellen:** „Adaptive Helligkeit“ aus, Regler etwa in die Mitte – und bei jeder weiteren
   Messung genauso. (Der Bericht schreibt Modus und Wert mit.)
4. **Gerät aufgeklappt auf den Tisch legen**, nicht in der Hand halten, nicht in die Sonne, nicht auf eine Decke.
   Während der Messung nicht auf- oder zuklappen und die App nicht verlassen.
5. **Gerät abkühlen lassen:** mindestens zehn Minuten nicht benutzen, bis es sich nicht mehr warm anfühlt.
6. **Continuum frisch starten:** App aus der Übersicht wischen, neu öffnen, warten, bis Gemma 4 E4B geladen ist
   (Modellzentrum zeigt „lokal geladen“).
7. **Sprachausgabe** so lassen, wie du sie normal benutzt – aber bei jeder Messung gleich. Der Bericht schreibt mit,
   ob sie an war.
8. Optional für den Modellvergleich: **Gemma 4 E2B vorher importieren** (Modellzentrum → „KLEINER · GEMMA-4-E2B-IMPORT
   VORAUSFÜLLEN“). Ohne Import wird die E2B-Konfiguration als NOT_RUN mit diesem Grund eingetragen; es wird kein
   anderes Modell ersatzweise genommen.

Die Messung startet nicht, wenn Claude (Cloud) als Gehirn gewählt ist, kein Modell bereit ist, ein anderes Modell
als Gemma 4 E4B geladen ist, das Modell auf der GPU läuft oder Android schon starke Erwärmung meldet. Sie sagt dann,
warum.

## 2. Starten

Zwei gleichwertige Wege:

- Im Chat schreiben oder sprechen: **„Continuum Wärmemessung kurz“** (oder „… voll“, oder nur „Continuum
  Wärmemessung“, dann fragt die App nach der Größe).
- Modellzentrum → Abschnitt „MESSEN“ → **„6 · WÄRME- UND ZEITMESSUNG · KURZ ODER VOLL“**.

Es erscheint ein Fenster mit Umfang und Dauer. Erst **STARTEN** beginnt die Messung.

| Größe | Was läuft |
|---|---|
| kurz | der erste feste Auftrag (Erstfrage + Folgefrage), einmal je Konfiguration A, B, D, G, H, E, F: 7 Läufe geplant, bis zu 14 Modellantworten |
| voll | alle drei festen Aufträge, zweimal je Konfiguration A bis H: 48 Läufe geplant, bis zu 96 Modellantworten |

„Geplant“, weil F nie läuft und B, C, G und E nur laufen, wenn die Bühne die sichtbare Ansicht ist bzw. E2B importiert
ist. Was nicht laufen kann, steht als NOT_RUN mit Grund im Bericht. Tatsächlich sind es in der Kurzmessung also
höchstens 6 Läufe mit 12 Modellantworten (mit importiertem E2B) oder 5 Läufe mit 10 Antworten (ohne E2B).

**Die Kurzmessung lässt C aus.** C (volle Bühnenbewegung wie in Code67) wiederholt, was Code68 schon abgestellt hat;
dafür misst die Kurzmessung jetzt G und H, also die beiden Änderungen nach Code68. C läuft in der vollen Messung und
steht im Kurzbericht als NOT_RUN mit diesem Grund.

**Dauer:** Es gibt keine gemessene Dauer. Das Fenster rechnet mit dem Median deiner letzten Antwortzeiten dieser
Sitzung (wenn es welche gibt) und sagt sonst ausdrücklich „unbekannt“. Die Kurzmessung ist begrenzt durch: 10 bis 12
Modellantworten in der Geschwindigkeit des Geräts (zwei davon, in H, mit allen Werkzeugschemas), vor jedem der 5 bis
6 Läufe mindestens 20 s Ruhe und 3 s Vorlauf (zusammen rund zwei Minuten) und höchstens 3 Minuten Abkühlen, dazu
einmal E2B laden und E4B zurückladen. Das frühere Ziel „ungefähr zehn Minuten“ galt für 10 Antworten ohne G und H;
mit G und H statt C ist es ein Lauf mit zwei Antworten mehr. Ob das Fold7 in dieser Größenordnung bleibt, ist
NOT_RUN. **Zuerst die Kurzmessung machen und schicken.**

## 3. Während der Messung

- Oben steht eine Zeile „Wärmemessung · Lauf … · Konfiguration …“ mit dem Knopf **STOPP**. STOPP (oder „Stopp“ im
  Chat, oder der normale Stopp-Knopf der Bühne) beendet die Messung jederzeit. Was bis dahin gemessen ist, steht im
  Bericht.
- Vor jedem Lauf wartet die App, bis Android wieder die Thermalstufe des Starts meldet und die Akkutemperatur höchstens
  1 °C von der ersten Starttemperatur entfernt ist – mindestens 20 s, höchstens 3 Minuten (kurz) bzw. 5 Minuten
  (voll). Wird das nicht erreicht, läuft der Lauf trotzdem, ist aber im Bericht markiert und zählt nicht als
  vergleichbar.
- In der Konfiguration B ist der Bildschirm bis auf die Zeile oben **dunkel**. Das ist Absicht: die Bühne ist aus.
- Andere Eingaben nimmt die App währenddessen nicht an.
- Für jeden Lauf legt die Messung einen eigenen neuen Chat an (damit jeder Lauf ohne Vorgeschichte beginnt). Diese
  Chats kannst du danach löschen. Am Ende steht das Ergebnis in dem Chat, aus dem du gestartet hast.

## 4. Danach: Dateien schicken

Die App schreibt zwei Dateien in den **Download-Ordner** des Handys und nennt die echten Namen im Chat:

- `continuum-waermemessung-kurz-JJJJMMTT-HHMMSS.json` – alle Rohwerte,
- `continuum-waermemessung-kurz-JJJJMMTT-HHMMSS.md` – derselbe Bericht lesbar.

Danach fragt sie „Möchtest du die beiden Dateien jetzt teilen?“ → **TEILEN** öffnet Androids Teilen-Menü (Mail,
Messenger). Bitte **beide Dateien** schicken. Später findest du sie in „Eigene Dateien → Downloads“.

Wurde die App mitten in der Messung geschlossen oder von Android beendet, gibt es keinen Bericht. Dann nach dem
nächsten Start im Chat **„Continuum Wärmemessung Teilbericht“** schreiben: Die bis dahin gemessenen Läufe werden als
unvollständiger Bericht gespeichert.

## 5. Was verglichen wird

Jede Konfiguration unterscheidet sich von der Basis A in genau **einem** Punkt.

| | Konfiguration | Wie die App sie schaltet | Wozu |
|---|---|---|---|
| A | Basis = diese App-Version: Bühne ruht beim Rechnen und zeigt den Antwortentwurf, Textfragen ohne Werkzeugschemas, Gemma 4 E4B, Gespräch nicht behalten | nichts wird verändert; „Gespräch im Modell behalten“ ist für die Messung aus | Bezugswert |
| B | Bühne aus | die Bühnenseite wird unsichtbar gemacht; ihr eigener Bildzähler muss 0 melden | Kosten der ruhenden Bühne |
| C | Bühne mit voller Bewegung beim Rechnen (nur in der vollen Messung) | die Bühne bekommt das Kennzeichen, das sonst ein Cloud-Gehirn sendet, und ruht deshalb nicht | Rückschritt von Code67 |
| D | Gespräch im Modell behalten | der Schalter gilt nur im Speicher für diesen Lauf als eingeschaltet | Wirkung auf die Folgefrage |
| E | Gemma 4 E2B statt E4B | E4B entladen, E2B laden, danach E4B wieder laden (Ladezeiten stehen getrennt, nicht in den Antwortzeiten) | Modellgröße |
| F | GPU statt CPU | **wird nie geschaltet – immer NOT_RUN**: dafür müsste eine gespeicherte Einstellung geändert werden, und der GPU-Start ist auf dem Gerät ungeprüft | – |
| G | Bühne ohne Antwortentwurf (wie Code68) | nur im Speicher: für diese Läufe bekommt die Bühnenseite keinen Entwurf der laufenden Antwort; sonst alles wie A | Vorher/Nachher der Entwurfszeile |
| H | Auftrag mit allen Werkzeugschemas (wie Code68 bei gesprochenen Erklärfragen) | nur im Speicher: für diese Läufe geht derselbe Auftrag auf dem Weg mit allen Werkzeugschemas an das Modell statt als reine Textfrage; sonst alles wie A | Vorher/Nachher der werkzeugfreien Route |

Nach jedem Ausgang – fertig, STOPP, Fehler – stellt die Messung alles zurück (Bühne sichtbar und ruhend, Antwortentwurf
an, Modellroute wie immer, Schalter wie vorher, E4B geladen, Bildschirm-an-Kennzeichen weg, dein Chat). Der Bericht
listet jeden Schritt unter „Wiederherstellung“. Der gespeicherte Schalter „Gespräch im Modell behalten“ wird von der
Messung nie geschrieben; die Umschaltungen für G und H gibt es nur als Merker im laufenden Programm.

### Vorher und nachher gegenüber Code68 in einer Messung: A gegen G und A gegen H

Nach Code68 sind zwei Dinge geändert worden. Eine Messung liefert für beide den Vergleich, weil G und H je genau
eines davon auf den Stand von Code68 zurückstellen – „nachher“ ist A, „vorher“ ist G bzw. H:

- **A gegen G – Antwortentwurf in der Bühne.** In Code68 stand in der Bühne während des Rechnens kein Antworttext;
  der erste sichtbare Text war die fertige, geprüfte Antwort. Jetzt zeigt die Bühne die laufende Antwort als
  Entwurfszeile. Verglichen wird der **erste sichtbare Text**: in A das Ereignis `ANSWER_DRAFT_HANDED_TO_STAGE_PAGE`,
  in G `ANSWER_HANDED_TO_STAGE_PAGE`. Erstes Token und Gesamtzeit sollten sich nicht unterscheiden; tun sie es doch,
  steht es in denselben Tabellen.
- **A gegen H – Werkzeugschemas.** In Code68 ging eine gesprochene Erklärfrage („Erklär mir …“) mit allen
  Werkzeugschemas an das Modell; jetzt geht sie als reine Textfrage. Die festen Aufträge sind in A Textfragen; H
  schickt genau dieselben Aufträge mit allen Schemas. Verglichen werden **erstes Token, erster sichtbarer Text,
  Gesamtzeit und Wärme** (Thermalstufe, Headroom, Akku-Anstieg, Abbrüche). Der Bericht schreibt je Antwort
  `toolFreeRoute` mit: in H muss das `false` sein, überall sonst `true`. Die Eingabe an das Modell ist in H
  absichtlich anders; deshalb gilt die 10-%-Regel für H nicht, der Unterschied des Prompttexts steht als Zahl dabei.
  „Prompttext (Zeichen)“ zählt nur den Text – die Schemas gibt die Laufzeit dem Modell zusätzlich mit, sie stehen
  nicht in dieser Zahl.

Der lesbare Bericht hat dafür den Abschnitt „Gegenüber Code68: G und H gegen die Basis A“. G braucht die Bühne als
sichtbare Ansicht (sonst NOT_RUN), H nicht.

Einschränkung zu C: C stellt die Bühnen**bewegung** von Code67 wieder her. Code67 ließ im Profil „Volle Qualität“
zusätzlich 120 Hz zu; die Messung ändert die Bildwiederholrate nicht (Code68 fragt 60 Hz an).

## 6. Die festen Aufträge

Quelle: `docs/evaluation/continuum-heat-v1/prompts.json` (Version 1, unverändert als Asset in der App). Je Auftrag
eine Erstfrage und eine Folgefrage im selben neuen Chat:

| Auftrag | Erstfrage | Folgefrage |
|---|---|---|
| kurz | Erkläre mir in zwei Sätzen, warum der Himmel blau ist. | Erkläre mir jetzt in einem Satz, warum der Himmel am Abend rot wird. |
| mittel | Erkläre mir in fünf kurzen Punkten, wie ein Kühlschrank funktioniert. | Erkläre mir den dritten Punkt noch einmal mit einem Beispiel aus dem Alltag. |
| lang | Erzähle mir eine Geschichte mit ungefähr zweihundert Wörtern über einen Leuchtturmwärter und seinen Hund. | Erzähle mir in drei Sätzen, wie die Geschichte am nächsten Morgen weitergeht. |

**Eingabe: identischer Text an der Sprachübergabe, kein Mikrofon.** Die App kann dem Spracherkenner kein Tonsignal
einspielen. Sie gibt deshalb den Text genau dort ein, wo ein erkannter gesprochener Satz in den Auftrag übergeht
(Text ins Eingabefeld, dann der normale Sendeweg `sendCurrentInput`) – ab da läuft derselbe Weg wie bei jedem
Auftrag: Antwortrouten, Gedächtnis- und Kontextstufe, Prompt, lokales Modell, Antwortprüfung, Anzeige, Sprachausgabe.

Die Aufträge sind so formuliert, dass sie als reine Textfrage ohne Werkzeugschemata an das Modell gehen und keine
feste Antwortroute sie vorher abfängt (`HeatMeasurementPlanTest` prüft das auf dem Host). Der Bericht schreibt je
Antwort mit, ob das so war (`toolFreeRoute`). In Code68 erkannte `LocalTextOnlyInferenceRoute` „Erklär mir …“ (ohne
e) nicht als Textfrage; seit der Änderung nach Code68 gehen auch die gesprochenen Formen ohne Werkzeugschemas an das
Modell. Was das auf dem Gerät ausmacht, misst Konfiguration H (siehe Abschnitt 5).

## 7. Was jede Zahl bedeutet

| Angabe im Bericht | Bedeutung | Quelle |
|---|---|---|
| Thermalstufe | Androids Stufe 0–6 (0 normal, 1 leicht, 2 mäßig, 3 stark erhöht – ab hier hält der Hitzeschutz der App das Modell an). Start, Ende, Höchstwert und jede Änderung mit Zeit. **Kein Temperaturwert.** | `PowerManager.currentThermalStatus` und Thermal-Listener |
| Headroom | Androids Abstand zur Stufe „stark erhöht“ (1,0 = Schwelle). „–“, wenn das Gerät nichts meldet. | `PowerManager.getThermalHeadroom(0)`, alle 2 s |
| Akkutemperatur, Akku-Anstieg | Temperatur des **Akkus**, nicht des Prozessors. Anstieg = höchster Wert eines Laufs minus Wert an seinem Start. | Batteriestatus (`EXTRA_TEMPERATURE`), alle 2 s |
| Abbrüche | Antworten, die vom Hitzeschutz angehalten oder gar nicht gestartet wurden, das Zeitlimit der Messung (240 s) erreichten, stockten, am Ausgabelimit endeten, von der App abgebrochen wurden oder fehlschlugen. Ein STOPP von dir zählt getrennt. | Abschluss jedes Modellaufrufs im normalen Antwortweg |
| Erstes Token | Zeit von der Übergabe des Auftrags bis zum ersten Textstück, das das Modell an den Antwortweg liefert. | Inferenz-Callback des Zuges |
| Erster sichtbarer Text | Zeit bis zum ersten Mal Antworttext an die **sichtbare** Oberfläche übergeben wird. Der Bericht nennt das Ereignis: `ANSWER_DRAFT_HANDED_TO_STAGE_PAGE` (Antwortentwurf an die Bühnenseite – das ist der Normalfall in der Bühne, sobald das erste Textstück da ist), `NATIVE_STREAM_PREVIEW_TEXT_SET` (Antwortentwurf in der nativen Ansicht, nur wenn diese Ansicht sichtbar ist), `ANSWER_HANDED_TO_STAGE_PAGE` (fertige Antwort an die Bühnenseite) oder `ANSWER_BUBBLE_ADDED_TO_NATIVE_CHAT`. Ein Entwurf zählt nur, wenn die Bühne wirklich sichtbar ist. In Konfiguration G (ohne Antwortentwurf, wie Code68) und B (Bühne aus) ist es deshalb die fertige Antwort. | Hooks im Antwortweg |
| Gesamtzeit | Übergabe des Auftrags bis zur endgültigen Freigabe durch die App (alle Arbeitsphasen des Zuges). Modell laden/entladen zählt nicht dazu. | Belegt-Kennzeichen der App |
| Zeichen/s | erzeugte Zeichen geteilt durch die Zeit vom ersten Token bis zum Ende des Modellaufrufs. | Antwortweg |
| Fensterbild p50/p90/p99 | Gesamtdauer der Bilder des App-Fensters, in Klassen von 0,5 ms (obere Klassengrenze). „Bilder > 2 Vsync“: Anteil der Bilder, die länger als zwei Bildperioden dauerten. | `Window.addOnFrameMetricsAvailableListener`, `FrameMetrics.TOTAL_DURATION` |
| Bühnenbilder/s, Bühnenabstand | Bilder, die der Bildbegrenzer der Bühne wirklich an Szene, Kugel und Avatar weitergegeben hat, und die Abstände dazwischen. | `ContinuumNative.frameProbe` in `native-bridge.js` |
| NOT_RUN / NOT_AVAILABLE / „–“ | nicht gelaufen / von Android oder der Bühne nicht geliefert / nicht gemessen. Nirgends steht ein Schätzwert. | – |
| „nicht vergleichbar“ | Startbedingung nicht erreicht, andere Anzahl Läufe, Laden oder Energiesparen anders, Frage ohne Modellaufruf beantwortet, Lauf gestoppt, Eingabe an das Modell mehr als 10 % anders groß (gilt für jede Konfiguration außer H – dort ist die Eingabe der Faktor, der Unterschied steht als Zahl im Bericht), oder der geänderte Faktor hat nicht gewirkt (B: Bühne lieferte trotzdem Bilder; D: Folgefrage wurde nicht im behaltenen Gespräch fortgesetzt; G: die Bühne zeigte doch einen Entwurf; H: eine Antwort ging doch ohne Werkzeugschemas an das Modell). | Berichtsauswertung |

**Größter gemessener Kostentreiber** – Regel, die auch im Bericht steht: Jede mit A vergleichbare Konfiguration wird
über die Mediane je Lauf mit A verglichen. Geprüft wird der Reihe nach: 1. Abbrüche (mindestens 1), 2. höchste
Thermalstufe (mindestens 1 Stufe), 3. Headroom (mindestens 0,05), 4. Anstieg der Akkutemperatur (mindestens 0,5 °C),
5. Gesamtzeit je Lauf (mindestens 10 %). Die erste Kennzahl, bei der eine Konfiguration die Mindeständerung erreicht,
entscheidet; genannt wird die Konfiguration mit der größten Änderung. Erreicht keine eine Mindeständerung oder ist
keine vergleichbar, wird kein Kostentreiber genannt. Bei der Kurzmessung ist jeder Wert eine Einzelmessung; der
Bericht sagt das dazu.

## 8. Was die Messung anders macht als ein gesprochener Auftrag

- Kein Mikrofon, keine Spracherkennung, kein Gesprächsmodus: der Text wird an der Übergabe eingegeben. Die Wärme von
  Mikrofon und Erkennung vor dem Auftrag wird nicht mitgemessen; der Gesprächsmodus wird beim Start beendet.
- Über der Bühne liegt während der Messung eine schmale, unbewegte Statuszeile mit STOPP.
- Jeder Lauf beginnt in einem neuen, leeren Chat. Gedächtnis und Erfahrungsspeicher der App arbeiten wie immer; was
  sie aus früheren Läufen kennen, kann den Prompt eines späteren Laufs verändern. Der Bericht schreibt die
  Promptgröße je Antwort mit und markiert Abweichungen über 10 %.
- Nach der letzten Arbeitsphase wartet die Messung eine Sekunde, ob die App noch eine Phase desselben Zuges beginnt.
  Die Gesamtzeit ist der Moment der Freigabe selbst; die Bildzähler werden zu diesem Moment gelesen.
- Eine gesprochene Antwort (Sprachausgabe an) wird nach der Textantwort abgewartet und getrennt gezeitet
  (`speechOutputMs`); ihre Messwerte stehen mit der Phase `SPEECH` in den Rohdaten und zählen nicht zum Höchstwert
  der Antwort.
- Ob Androids Fenster-Bildzeiten auf dem Fold7 auch die Bilder der Bühnenseite abbilden, ist NOT_RUN. Deshalb zählt
  die Bühne ihre Bilder zusätzlich selbst.

## 9. Auswertung am Rechner

```
node tools/heat-report/heat-report.mjs <bericht.json>                 # ein Bericht als Tabellen
node tools/heat-report/heat-report.mjs <vorher.json> <nachher.json>   # Vorher/Nachher mit Differenzen
node --test tools/heat-report/heat-report.test.mjs                    # Tests (nur synthetische Dateien)
```

Das Werkzeug rechnet die Zusammenfassung aus den Rohläufen neu (gleiche Definitionen wie die App), meldet, wenn die
gespeicherte Zusammenfassung abweicht, und nennt zwei Berichte „nicht vergleichbar“, wenn Umfang, Auftragssatz,
Gerät, Startbedingung (Thermalstufe, Akkutemperatur außerhalb des Bands), Laden, Energiesparen, Sprachausgabe,
Anzeige oder das Modell der Basis verschieden sind. Die Dateien in `tools/heat-report/fixtures/` heißen
`SYNTHETIC-…`, tragen `"synthetic": true` und sind keine Messungen.

## 10. Stand der Prüfung

| Teil | Stand |
|---|---|
| Protokoll, Zugaufzeichnung, Statistik, Bericht, Chat-Befehl | Hosttests (JVM) mit geskriptetem Gerät und Uhr |
| Echter Antwortweg der App mit eingehängter Messung, STOPP, Wiederherstellung, Dateien | Hosttests (Robolectric) mit Ersatzmodell; Robolectric meldet weder Thermalstufe noch Akkutemperatur |
| Bühne aus / volle Bewegung / ohne Antwortentwurf (G), Abfrage des Bildzählers | Hosttest (Robolectric) mit Ersatzseite: in G bekommt die Seite kein Entwurfsskript |
| Alle Werkzeugschemas (H) | Hosttests (Robolectric) mit Ersatzmodell: die Aufträge von H nehmen den Modellweg mit Werkzeugen, alle anderen den ohne; danach wieder ohne |
| Bildzähler der Bühne | jsdom und echter Chromium auf dem Host |
| Vergleichswerkzeug | Node-Tests mit synthetischen Berichten |
| Thermalstufe, Headroom, Akkutemperatur, Fenster-Bildzeiten, Bühnenbilder im WebView, E2B-Wechsel, Dauer, jede Zahl | **NOT_RUN – nur auf dem Fold7 messbar** |
