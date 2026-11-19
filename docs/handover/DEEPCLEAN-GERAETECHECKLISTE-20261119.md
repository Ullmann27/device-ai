# Speicher & Bereinigung („ContinuumDeepClean“) – Gerätecheckliste Fold7

Stand 19.11.2026. Alles in dieser Liste ist **am Gerät NOT_RUN**. Auf dem Host getestet sind nur die Regeln, Abläufe und
Texte (siehe Abschnitt „Was auf dem Host getestet ist“). Nichts davon ist am Gerät bestätigt.

Paket: `at.heinz.deviceai.continuumlocal`, Gerät: Samsung Galaxy Z Fold7, Android 16 (oder 17).

## 0. Vorbereitung (NOT_RUN)

1. Vor dem Test eine Sicherung des Telefons (Samsung Smart Switch oder Google) – auch wenn die Bereinigung nach Plan
   nichts Persönliches anfasst.
2. Testdateien anlegen (über den Dateimanager oder per USB):
   - `Download/test-alt.apk` (eine beliebige alte APK, mindestens ein paar MB),
   - `Download/bericht.pdf` und eine **identische** Kopie `Download/bericht (1).pdf`,
   - `Download/film.mkv.crdownload` (eine beliebige Datei mit dieser Endung), Änderungsdatum älter als 24 h – eine
     ganz neue Datei dieser Art muss dagegen **ausgeschlossen** bleiben,
   - ein Foto in `DCIM/Camera` und eine identische Kopie in `Download`.
3. Notieren: freier Speicher laut Einstellungen › Akku und Gerätewartung › Speicher.

## 1. Erreichbarkeit (NOT_RUN)

| Schritt | Was zu sehen sein muss |
|---|---|
| Einstellungen-Übersicht („Alle Einstellungen“) › Gruppe „Gerät & Kontrolle“ | Eintrag „Speicher & Bereinigung“ |
| Antippen | Dialog „Speicher & Bereinigung“ mit Zugriffsstand, freiem Speicher, sechs Schaltflächen, Protokoll |
| „Mehr“ bzw. „Werkzeuge“ | Eintrag „Speicher & Bereinigung“ öffnet denselben Dialog |
| Sprache: „Öffne die Speicherbereinigung“ | Antwort „Ich öffne „Speicher & Bereinigung““ und der Dialog |
| Jede Schaltfläche einmal | Keine tote Schaltfläche: jede startet einen Auftrag oder öffnet eine Android-Seite |

Hinweis: Die Command03-Bühne (Web) bekommt die Daten unter `deepClean` im Snapshot. Eine eigene Schaltfläche auf der Bühne
gibt es erst, wenn das Bühnen-Team sie mit der Route `storage-clean` anlegt (die native Route existiert).

## 2. Zugriff (NOT_RUN)

1. „Zugriff auf alle Dateien“ für Continuum **aus**: „Schau, was unnötig Speicher verbraucht“ sagen.
   - Muss sagen: gemeinsamer Speicher nicht durchgesehen, „Zugriff auf alle Dateien“ ist nicht eingeschaltet; nur
     Continuums eigene Dateien geprüft.
   - Im Dialog: Schaltfläche „ZUGRIFF AUF ALLE DATEIEN“ öffnet die Android-Seite.
2. Zugriff einschalten, Satz wiederholen: jetzt mit Kategorien (Fotos, Videos, Downloads …) und echten Größen.
3. Muss in der Analyse stehen: „Wie viel Platz andere Apps belegen, zeigt Android nur in den Einstellungen …“ – es gibt
   keine erfundenen Zahlen für fremde Apps.

## 3. Nur prüfen löscht nie (NOT_RUN)

Für jeden Satz: danach im Dialog-Protokoll **kein neuer Eintrag**, die Testdateien sind alle noch da.

- „Schau, was unnötig Speicher verbraucht“
- „Schau, warum mein Speicher so voll ist“
- „Bereinige mein Handy, aber lösch nichts“
- „Mach eine Tiefenbereinigung, nur schauen“ – Liste wird gezeigt, „alle“ und „ja“ danach löschen **nichts**, Antwort:
  „Du hattest gesagt, dass ich nur prüfen soll …“
- „Optimier mein Handy“ / „Kannst du mein Handy wieder schneller machen?“ – nur Temperatur, Speicher, eigene Last;
  „Andere Apps beende ich nicht“.

## 4. Sichere Bereinigung (NOT_RUN)

1. „Continuum, bereinige mein Handy“.
2. Muss sagen (bei bekanntem Namen): „Alles klar, Heinz. Ich habe deinen Speicher geprüft und nur entfernt, was eindeutig
   entbehrlich ist. Deine persönlichen Dateien habe ich nicht angefasst.“
3. Zahlen prüfen: „Entfernt habe ich N Dateien, zusammen X (Summe der Dateigrößen)“ **und** „Android misst: vorher …
   frei, jetzt …“. Beide Zahlen werden getrennt genannt.
4. „Gegenprobe: … geschützte Dateien vorher und nachher verglichen – alle unverändert.“
5. Testdateien in `Download` und `DCIM` sind **alle** noch da. Das Modell lädt danach weiterhin, Gedächtnis und Gespräche
   sind unverändert (Gedächtniszentrum öffnen, ein Fakt muss noch da sein).
6. Protokoll im Dialog: nur Einträge aus „Continuum-Cache/…“ oder „Continuum-Daten/…tmp“.

## 5. Tiefenbereinigung mit Auswahl (NOT_RUN)

1. „Mach eine Tiefenbereinigung“.
2. Erwartet: Grünes wird wie in 4. entfernt; danach nummerierte Liste mit Pfad, Größe, Herkunft, Grund und
   „im Android-Papierkorb wiederherstellbar“. Darin `test-alt.apk`, `bericht (1).pdf` („Wiederholter Download … SHA-256“),
   die Foto-Kopie aus `Download` (das Original in `DCIM` steht **nicht** in der Liste), der alte `.crdownload`.
3. „Fotos behalten“ → Foto-Kopie verschwindet aus der Liste.
4. „die 1 und die 2“ → „Probelauf: Ich würde … verschieben … Soll ich?“ – noch nichts passiert.
5. „ja“ → **Android-eigener Dialog** „In den Papierkorb verschieben?“ erscheint. Er wird nicht automatisch bestätigt.
6. Im Android-Dialog „Abbrechen“ → Antwort „Android hat nichts in den Papierkorb verschoben … Gelöscht ist davon nichts.“
   Dateien sind noch da.
7. Wiederholen, im Android-Dialog „Zulassen“ → Antwort „In den Android-Papierkorb verschoben: …“; die Dateien sind in
   der Galerie/Dateien-App unter Papierkorb sichtbar.
8. „Stell die gelöschten Dateien wieder her“ → Android-Dialog → Dateien wieder am alten Ort.
9. Falls angeboten: Punkt „Zwischenspeicher aller Apps leeren“ → Android-Dialog. Bei „Abbrechen“ muss die Antwort sagen,
   dass Android nichts geleert hat.

## 6. Gezieltes Löschen und Sicherheit (NOT_RUN)

- „Lösch die doppelten Dateien“ → nur Duplikate (gleicher SHA-256), das Original bleibt.
- Datei zwischen Vorschau und „ja“ verändern → Antwort nennt sie als „verändert – nicht angefasst“.
- Einen laufenden Download starten (Chrome) und „Lösch die abgebrochenen Downloads“ → die laufende Datei steht nicht in
  der Liste.
- „Lösch alles“ → „Alles auf einmal lösche ich nicht …“, nichts passiert.
- „Lösch meine Fotos“ → Rückfrage, nichts passiert.

## 7. Stopp und Neustart (NOT_RUN)

1. Viele Dateien in den eigenen Cache legen ist am Gerät schwer; ersatzweise „Mach eine Tiefenbereinigung“ mit vollem
   Speicher starten und sofort „Stopp“ sagen → „Ich stoppe sofort …“, danach ein ehrlicher Bericht.
2. App während einer Bereinigung aus dem Speicher werfen, neu starten, „Schau, was unnötig Speicher verbraucht“ →
   genau **einmal** „Die letzte Bereinigung wurde unterbrochen … Fortgesetzt habe ich nichts.“; beim nächsten Satz nicht
   mehr.

## 8. Ein Satz – drei Schritte (NOT_RUN)

„Prüf zuerst meinen Speicher, bereinige die sicheren temporären Dateien und sag mir danach, wie viel Platz ich wieder
habe“ → angekündigter Plan mit drei Schritten, drei Antworten der Speicher-Lenkung, Schlussbericht „Fertig – alle drei
Schritte sind erledigt …“ mit denselben gemessenen Zahlen.

## 9. Wärme (NOT_RUN)

Unter Last (Fold7 warm, Thermalstufe ≥ 3) „Mach eine Tiefenanalyse“ → die Duplikatsuche endet früher, die Antwort sagt
„wegen der Gerätetemperatur früher beendet; sie ist unvollständig“.

## Was auf dem Host getestet ist

`DeepCleanCommandTest`, `DeepCleanClassifierTest`, `DeepCleanLaneTest`, `DeepCleanMissionTest`,
`DeepCleanRegressionTest` (Korpus `docs/evaluation/continuum-eval-v1/regression-deepclean*.jsonl`) – auf einem
temporären Dateibaum mit echten Dateien, echten symbolischen Links und einem nachgebauten Android-Papierkorb. Der
Android-Teil (`AndroidDeepCleanHost`, `DeepCleanPanel`, MediaStore, StatFs, DownloadManager, Systemdialoge) ist nur
kompiliert, **nicht** ausgeführt.
