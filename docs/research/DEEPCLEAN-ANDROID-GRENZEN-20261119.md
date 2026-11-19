# Speicher & Bereinigung – was Android 16/17 einer normalen App erlaubt

Stand 19.11.2026. Grundlage sind die offiziellen Android-Seiten, die für diese Arbeit tatsächlich gelesen wurden (Links
unten, jeweils mit dem gelesenen Inhalt). Für Android 17 wurde **keine** eigene Verhaltensänderungs-Seite gelesen; die
Aussagen gelten für API 36 (Android 16, `targetSdk = 36` der App) und sind für Android 17 **nicht geprüft**.

Continuum ist eine normale, vom Besitzer signierte App: keine System-App, kein Root, kein Geräteadministrator.

## Was jede Schnittstelle wirklich kann

| Schnittstelle | Was sie einer normalen App erlaubt | Wie Continuum sie nutzt |
|---|---|---|
| `Context.getCacheDir()` / `getExternalCacheDir()` | Eigener Zwischenspeicher; Android darf ihn jederzeit selbst leeren (deshalb gilt er als neu erzeugbar). | Einzige Quelle für automatisches GRÜN – außer `litertlm/` (Modell-Kernels), `owner-update/` (Update-Download), `system-diagnostics/` (eigene Diagnoseablage) und Dateien, die jünger als 15 Minuten sind. |
| `Context.getFilesDir()` | Eigene dauerhafte Daten (Gedächtnis, Modelle, Gespräche). | Nur übrig gebliebene `createTempFile`-Zwischendateien (`<präfix><ziffern>.tmp`), älter als 24 h, sind GRÜN. Alles andere ROT. |
| `StorageStatsManager` | **Ohne Berechtigung nur für das eigene Paket/UID** (`queryStatsForPackage`, `queryStatsForUid`). Für andere Apps, `queryStatsForUser` und `queryExternalStatsForUser` ist `PACKAGE_USAGE_STATS` nötig – „a system-level permission that will not be granted to normal apps“; der Besitzer müsste sie in den Einstellungen gewähren, nachdem die App sie deklariert. `getFreeBytes()` ist für die Anzeige gedacht und zählt zurückholbaren Cache mit. | Nicht verwendet. `PACKAGE_USAGE_STATS` wurde **nicht** deklariert (siehe unten). Der Bericht sagt ausdrücklich, dass der Speicher anderer Apps nur in den Android-Einstellungen sichtbar ist. |
| `StatFs` | Freie und gesamte Bytes eines Datenträgers, ohne Berechtigung. | Die „Android-Messung“ vorher/nachher (`Environment.getDataDirectory()`). |
| `StorageManager.getAllocatableBytes(uuid)` | Höchstmenge, die **die eigene App** anlegen könnte; meist größer als `getUsableSpace()`, weil das System dafür Caches löschen würde. | Nicht als Zahl ausgegeben – sie beschreibt keinen gemessenen Zustand, sondern eine Möglichkeit für Continuum selbst. |
| `StorageManager.ACTION_MANAGE_STORAGE` (API 25) | Öffnet Androids eigene Speicherverwaltung; Ergebnis `RESULT_OK` nur, wenn der angeforderte Platz frei wurde. | Schaltfläche „ANDROID-SPEICHERVERWALTUNG“ im Dialog. |
| `StorageManager.ACTION_CLEAR_APP_CACHE` (API 30) | Zeigt einen **System-Dialog**, der die externen Cache-Ordner aller Apps leert; „Requires MANAGE_EXTERNAL_STORAGE“. Ergebnis `RESULT_OK` nur, wenn geleert wurde; `EIO` bei Fehler; sonst `RESULT_CANCELED`. | Wird in der Tiefenbereinigung als eigener Auswahlpunkt angeboten (nur mit „Zugriff auf alle Dateien“). Nur `RESULT_OK` gilt als geleert; jede andere Antwort wird als „nicht geleert“ gemeldet. |
| `MediaStore.createTrashRequest` (API 30) | Erzeugt einen `PendingIntent`; der **Besitzer** bestätigt im System-Dialog. Danach ist `IS_TRASHED` gesetzt. Jede URI muss ein MediaStore-Eintrag mit `_ID` sein. Ab `targetSdk` 36 höchstens 2000 URIs pro Anfrage. Derselbe Aufruf mit `false` holt zurück. | GELB wird ausschließlich so entfernt (Papierkorb, wiederherstellbar). Die App automatisiert den Dialog nicht. Danach zählt eine Datei nur als verschoben, wenn ihr ursprünglicher Pfad tatsächlich weg ist. |
| `MediaStore.createDeleteRequest` (API 30) | Dauerhaftes Löschen nach Bestätigung im System-Dialog. | Bewusst **nicht** verwendet: Papierkorb ist umkehrbar. |
| `MANAGE_MEDIA` (API 31, `canManageMedia`) | Spezialzugang, den der Besitzer in den Einstellungen gewähren muss. | Nicht deklariert, nicht verwendet (er würde die Bestätigung im Papierkorb-Dialog verändern – nicht gewollt). |
| `MANAGE_EXTERNAL_STORAGE` („Zugriff auf alle Dateien“) | Lesen/Schreiben im ganzen gemeinsamen Speicher, Zugriff auf `MediaStore.Files`, **aber nicht** auf `Android/data/` und die App-Ordner anderer Apps. Muss vom Besitzer über `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` eingeschaltet werden; Prüfung mit `Environment.isExternalStorageManager()`. Google-Play-Richtlinie beschränkt die Nutzung. | Schon vorher deklariert (Dokumentenablage). Ohne Freigabe wird nur Continuums eigener Bereich geprüft, und der Bericht sagt das. `Android/` wird beim Durchsuchen nicht betreten. |
| SAF / `DocumentFile` | Zugriff nur auf vom Besitzer gewählte Ordner; mit `MANAGE_EXTERNAL_STORAGE` laut Doku nicht mehr Rechte als ohne. | Nicht verwendet: alle-Dateien-Zugriff ist bereits der Weg der App; SAF hätte für Bereinigung keinen Mehrwert. |
| `DownloadManager` | Abfrage laufender/ausstehender/pausierter Downloads. | Diese Dateien gelten als „in Benutzung“ und werden nie vorgeschlagen. |
| `PowerManager.getCurrentThermalStatus` (API 29) | Thermalstufe 0..6. | Ab Stufe 3 bricht die Duplikatsuche ab und meldet das; die Optimierung schlägt bei Wärme vor, das eigene Modell zu entladen. |

Android-16-Änderungen, die gelesen wurden und hier relevant sind: MediaStore-Version ist pro App eindeutig (keine
Auswirkung), Begrenzung auf 2000 URIs pro Papierkorb-/Lösch-Anfrage (berücksichtigt: `MAX_URIS = 2000`). Sonst keine
Speicher-Änderung auf der gelesenen Seite.

## Bewusst nicht gebaut – und warum

- **`PACKAGE_USAGE_STATS`** (Speicher anderer Apps): nicht deklariert. Gründe: (1) Es ist eine Systemberechtigung, die
  der Besitzer in den Einstellungen freischalten müsste; (2) Play Protect und Play-Richtlinien bewerten Apps mit
  Nutzungsstatistik-Zugriff zusätzlich zur bereits vorhandenen alle-Dateien-Berechtigung strenger – das Risiko einer
  Warnung bei der Installation der Besitzer-APK steigt; (3) die Zahlen würden nichts löschbar machen, weil Daten anderer
  Apps ohnehin ROT sind. Der Bericht verweist stattdessen auf Androids eigene Speicherseite.
- **Fremde App-Caches einzeln leeren**: für eine normale App unmöglich (nur Systemrechte). Angeboten wird nur Androids
  eigener Dialog `ACTION_CLEAR_APP_CACHE`.
- **Shizuku-Modus**: Mit Shizuku liefe ein Shell-Befehl als `shell`-Benutzer. Mehrwert wäre `pm trim-caches <Größe>`
  (leert die Caches *aller* Apps systemweit) und `du`/Auflisten in `Android/data` anderer Apps. Beides widerspricht den
  Vorgaben: „keine Veränderung geschützter Daten anderer Apps“ und „keine Systemdateien“; `pm trim-caches` wäre zudem
  eine pauschale Aktion ohne Vorschau, Vorher-Prüfung je Datei und Wiederherstellung. Der bestehende geprüfte
  Shell-Weg (`CheckedShellCallDispatcher`, Werkzeug-Gates) hat dafür keinen freigegebenen Befehl. **Nicht gebaut.**
- **Ähnliche Fotos**: nicht gesucht. Duplikate sind nur Dateien mit exakt gleichem SHA-256 (nach Größen-Vorfilter).
- **Automatisches Fortsetzen nach Neustart**: nicht gebaut. Ein unterbrochener Lauf wird einmal berichtet und nicht
  fortgesetzt (keine doppelte Löschung möglich).
- **Logs anderer Apps / Systemlogs**: nicht zugänglich ohne Systemrechte; nicht gebaut.
- **Bühnen-Darstellung**: nur die Daten (`deepClean` im Command03-Snapshot); die Darstellung baut das Bühnen-Team.

## Gelesene Quellen

- MediaStore-Referenz (createTrashRequest, createDeleteRequest, canManageMedia, 2000-URI-Grenze ab BAKLAVA):
  https://developer.android.com/reference/android/provider/MediaStore
- StorageManager-Referenz (ACTION_CLEAR_APP_CACHE inkl. Berechtigung und Ergebniscodes, ACTION_MANAGE_STORAGE,
  getAllocatableBytes, getCacheQuotaBytes/getCacheSizeBytes, getManageSpaceActivityIntent):
  https://developer.android.com/reference/android/os/storage/StorageManager
- StorageStatsManager-Referenz (eigenes Paket ohne Berechtigung, sonst PACKAGE_USAGE_STATS):
  https://developer.android.com/reference/android/app/usage/StorageStatsManager
- Alle-Dateien-Zugriff (was MANAGE_EXTERNAL_STORAGE gewährt und was nicht, Play-Hinweis):
  https://developer.android.com/training/data-storage/manage-all-files
- Verhaltensänderungen Android 16 für Apps mit targetSdk 36:
  https://developer.android.com/about/versions/16/behavior-changes-16
