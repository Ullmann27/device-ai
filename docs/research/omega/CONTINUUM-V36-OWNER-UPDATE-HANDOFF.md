# Continuum 0.3.35 / code36 — Originalsigniertes In-App-Update: technische Übergabe

Auftrag: [PR331-Kommentar6556196173](https://github.com/Ullmann27/device-ai/pull/331#issuecomment-6556196173). Prüfung ausschließlich lesend am 01.11.2026; Ausgangsquellstand `8417f08b8c926c7b643c6ded979d0c501e3a306f`. Dieses Dokument ist das einzige neue Arbeitsergebnis. Kein Release, Build, Merge, Publisher-/Secret-/Modellwechsel und keine Installation wurden ausgeführt. Ein nachfolgender Dokumentationscommit ist kein neu gebauter Android-Quellstand.

## 1. Nachprüfbarer Ist-Stand und feste Identitäten

|Gegenstand|Tatsächlich gelesen|
|---|---|
|PR328|Offener Draft; `release/continuum-local-0.3.35-20261101`, HEAD `081065b7d3628bf6a51239360f1e377f20df3fff`; bereits Vorfahr von PR331. Nicht nochmals blind mergen.|
|PR331-Codekandidat|`research/continuum-jarvis-evolution-20261101`, `8417f08b8c926c7b643c6ded979d0c501e3a306f`; ursprünglicher geprüfter Code vor dieser Dokumentation.|
|Forschungs-QA|`continuum-jarvis-evolution-qa-20261101`, Dienst `srv-dbjfjcfb9u1s73dt1sgg`, Deploy `dep-dbjg97lg1s2s73flo71g`, live01.11.2026 09:29:02UTC; exakter8417-HEAD, vollständige unsigned Host-QA PASS.|
|Originaler Publisher|`continuum-owner-v23-release-preflight`, Dienst `srv-dbf1tr2vcj2c73ehpfog`; statische Site, autoDeploy=no, Buildkommando `bash scripts/render-continuum-m8-owner-release.sh`, Publishpfad `continuum-m8-owner-release`.|
|Aktueller Publisher-Branch|`research/continuum-m8-render-signed-release-20261031`; Remote-HEAD `97b5b9006f8c60f1a6fc4fb10981894330c55fe4`; letzter live Deploy `dep-dbj4liu7bikc73edkhl0` vom31.10.2026. Dessen Script ist noch auf code35/0.3.34 festgelegt.|
|Unveränderlicher Updatekanal|`https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/latest.json`|
|Paket / Zielversion|`at.heinz.deviceai.continuumlocal`, `versionCode=36`, `versionName=0.3.35`|
|Originales Zertifikat SHA256|`6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23` — öffentlicher Fingerabdruck, kein Schlüssel.|

Öffentliches Manifest am01.11.2026 09:40:57UTC per HTTPS GET gelesen: HTTP200,641Bytes, finale URL unverändert; es bietet **0.3.34/code35** aus `97b5b900…`,54521884APK-Bytes, SHA256 `6e68b9ce867b683a7c2350c1589269796a8166c3a9f1696fa4cb12da54cc7de6`, Metadatenfeld approved=true. Die darin genannte APK-URL ist der feste Host plus `/continuum-local/Continuum-Lokal-6e68b9ce867b683a7c2350c1589269796a8166c3a9f1696fa4cb12da54cc7de6.apk`. Das ist **keine v36-Freigabe und kein neuer Zertifikats-/APK-Binäraudit**. APK-Download und Gerätestand wurden in diesem Mini-Auftrag nicht erneut geprüft.

Zusätzlicher lesender Aufruf `node scripts/continuum-owner-render-readback.mjs --manifest-only` scheiterte hier mit Exit69/`AggregateError`; der getrennte Python-HTTPS-GET lieferte die obigen Metadaten. Ursache des Node-Transportfehlers nicht untersucht; kein Beleg für einen Android-Fehler und kein vorgetäuschtes Readback-PASS. Nächster Ingenieur benötigt den vollständigen Node-Readback auf einem funktionierenden Netzpfad und zusätzlich einen echten APK-Zertifikatsaudit.

Die unsigned8417-QA-APK hat54553583Bytes, SHA256 `1ce90adfb5927e1ed478e8e575f29e93647e41dc601b40316afde90af3949e3f`. Sie ist **KEIN installierbares Owner-Update**. Signierung/neuer Build erzeugen einen separat zu messenden Digest; weder dieser unsigned Hash noch der alte v35-Hash darf ins neue signierte Manifest kopiert werden.

## 2. Tatsächliche App-Kette und erreichbarer Knopf

Die folgenden Dateien sind im geprüften Quellstand vorhanden; Zeilen beziehen sich auf8417 und können nach späteren Änderungen wandern:

1. `AgentActivity.kt`: System-Navigation öffnet `showSystemCenter()` (u.a.940/992/1353); dort1953 **„UPDATES PRÜFEN UND LADEN“** → expliziter Intent zu `ContinuumOwnerUpdateActivity`. Zusätzlich System → **„GERÄTE-SCHNELLZUGRIFFE“** → `showQuickActionsDialog()` → `buildQuickActions()` (2021/2032/2244): Chip **„Updates“** startet dieselbe Activity (2268). Damit ist der Knopf im Code tatsächlich verdrahtet, keine bloße Klassenexistenz. Sichtbarkeit/Bedienung auf dem aktuellen Fold7: NOT_RUN. Die Activity ist in `app/src/main/AndroidManifest.xml` registriert; `AgentActivity` ist Launcher.
2. `ContinuumOwnerUpdateActivity.kt`: Button **„Updates prüfen und laden“** ruft `checkForUpdate()` → `ContinuumOwnerUpdater.check`. Ein erfolgreicher Available-Befund startet nach diesem expliziten Klick Download/Vorbereitung. Android behält seine Installationsbestätigung. Ohne Installationsfreigabe zeigt die Activity den Weg **„Android-Installationsfreigabe“**.
3. `intelligence/ContinuumOwnerUpdateCatalog.kt`: fest kompilierter Host/Pfad oben, maximal8192Manifest-Bytes, exakt bekannte JSON-Felder/Typen; `schema=1`, Kanal OWNER_RELEASE, Versionsname aus Versionscode. Platzhalter `schema=1,available=false` bedeutet kein Release.
4. `ContinuumSignedUpdatePolicy.kt`: installiertes Paket und dessen **tatsächlich ausgelesenes** Originalzertifikat; Kanal/Paket, ausdrücklich approved, gültige SHA-/Größen-/Commitfelder; fest gepinnte HTTPS-APK-URL ohne Credentials/Query/Fragment, Digest im APK-Dateinamen, keine fremde Domain. Code muss strikt höher als der installierte sein. Gültiges altes/gleiches Release wird als Current behandelt; ungültige Metadaten dürfen nicht als Current kaschiert werden.
5. `ContinuumOwnerUpdater.kt`: erneuter Policycheck direkt vor Download, `canRequestPackageInstalls`, freier Flash größer APK-Größe+64MiB; temporärer Cache `cache/owner-update`. HTTPS GET mit deaktivierten Redirects, Zeit-/Größenlimits; gestreamte Byteanzahl und tatsächlicher SHA256 müssen exakt passen. `verifyArchive` liest das APK-Paket, tatsächlichen Versionscode sowie genau einen aktuellen Signierer und vergleicht Archiv **und installierte App** mit Originalzertifikat. Der tatsächliche Versionsname wird serverseitig per aapt2 geprüft; `verifyArchive` prüft ihn nicht separat.
6. Danach `PackageInstaller`-Session `MODE_FULL_INSTALL`, explizites Paket, APK-Länge, `base.apk`, fsync, session-spezifischer PendingIntent → `ContinuumOwnerUpdateReceiver`. Dieser öffnet bei `STATUS_PENDING_USER_ACTION` den Android-Dialog und bestätigt bei OS-Erfolg Paketversion+Originalzertifikat nochmals: erst dann `INSTALLED_AND_VERIFIED`; sonst `OS_SUCCESS_BUT_READBACK_FAILED`/konkreter Fehler. Uninstall ist kein Teil dieser Kette.

## 3. Quell-Promotion — erst bei späterer ausdrücklicher Freigabe

Der Publisher läuft **nicht** auf PR331. Gelesener DAG zum8417-Codekandidaten: Publisher97b5 ist Vorfahr, `rev-list --left-right --count` ergibt0Publisher-exklusive/34Kandidaten-exklusive Commits; PR328 liegt bereits darin. Der geprüfte331-Release-Scriptstand akzeptiert36/0.3.35, der momentan auf dem Publisher laufende alte Stand ausschließlich35/0.3.34. Nur Umsetzen einer Versions-Env ohne Quell-Promotion reicht deshalb nicht.

Für den nächsten Ingenieur: zuerst Remote-HEADs/DAG und fremde parallele Arbeiten neu vergleichen. Konkreten, reviewten Release-SHA wählen;8417 ist der nachgewiesen unsigned-geprüfte Codekandidat, während der Nachfolgecommit dieses Handoffs nur Dokumentation enthält. Die Owner-Freigabe muss den **tatsächlich später gebauten SHA** benennen. Den unveränderten gepinnten Publisher-Branch danach nur kontrolliert/fast-forward auf genau diesen abgenommenen Stand bringen; bei Divergenz abbrechen und separat auflösen/reviewen, kein Force-Push, kein ungeprüfter Main-Merge. Aktuelle feste Service-Identität/Host/Publishpfad behalten. Keine neue Site als Ersatzkanal: die installierte App würde ihren Host ablehnen. In diesem Auftrag wurde nichts promotet oder umkonfiguriert.

## 4. Vorhandene Variablen und Buildpfade — ausschließlich Namen

Keine Werte aus Secret-Stores wurden gelesen oder angefordert. Existenz/Gültigkeit aktueller Signier-Secrets ist UNVERIFIED. Der bestehende Release-Script prüft bzw. setzt:

|Kategorie|Exakte Variablennamen / Rolle|
|---|---|
|Owner-/Host-/Versionsgates|`CONTINUUM_RELEASE_APPROVED_SHA`, `CONTINUUM_RELEASE_HOST`, `CONTINUUM_RELEASE_VERSION_CODE`; `RENDER_SERVICE_NAME` muss der originale Publisher sein. SHA wird bytegenau mit Git-HEAD verglichen.|
|Originale geschützte Signierinputs|`CONTINUUM_LOCAL_SIGNING_P12_BASE64`, `CONTINUUM_LOCAL_SIGNING_PASSWORD` — nur im vorhandenen geschützten Publisher. Niemals in Chat, Issue, Commit oder Log kopieren.|
|Temporäres Signierfile|`CONTINUUM_LOCAL_SIGNING_FILE` wird vom Script in privatem Temp-Verzeichnis angelegt, danach gelöscht. Nicht vorher extern belegen: die vorgelagerte unsigned-QA lehnt Signierfile/Passwort ab.|
|Ausdrücklich ausgeschlossene Mentor-Credentials|`CONTINUUM_MENTOR_SIGNING_FILE`, `CONTINUUM_MENTOR_SIGNING_PASSWORD`, `CONTINUUM_MENTOR_SIGNING_P12_BASE64` müssen leer bleiben.|
|Toolchain, intern gesetzt|`JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `GRADLE_USER_HOME`, `PATH`|
|Nach Binärprüfung intern erzeugte Manifestinputs|`CONTINUUM_RELEASE_SHA`, `CONTINUUM_RELEASE_DIGEST`, `CONTINUUM_RELEASE_SIZE`, `CONTINUUM_RELEASE_VERSION_NAME`|

`scripts/render-continuum-m8-owner-release.sh` läuft ausschließlich auf dem exakt benannten Publisher. Es führt zuerst `scripts/render-continuum-m8-qa.sh` ohne P12-/Passwortinputs aus, prüft denselben HEAD, decodiert dann Original-P12 privat und prüft mit keytool den PKCS12-PrivateKeyEntry/Originalfingerabdruck. Bestehender Alias: `continuum-local`; Gradle-Konfiguration `continuumLocal`, Typ PKCS12. Keine alternative Schlüsselerzeugung.

Vorhandene Gradle-Properties: `deviceAiMentorPreview`, `continuumLocalStandalone`, `continuumStandaloneVersionCode`; Signierbau führt `:app:lintRelease :app:assembleRelease` aus. Die unsigned-QA verwendet getrennt `continuumLocalRenderUnsigned`; dessen APK nicht installieren. Die drei Standalone-/QA-/Unsigned-Modi sind gegenseitig beschränkt.

Nach Signierung: `app/build/outputs/apk/release/app-release.apk`, `apksigner verify --verbose --print-certs` einschließlich v2-Signatur, aapt2-Paket/Code/Name und SHA/Länge. Script staged nach `continuum-m8-owner-release/continuum-local/Continuum-Lokal-<SHA256>.apk`, erzeugt dort `latest.json` und `BUILD-INFO.txt`. `SIGNED_STAGED_FOR_PINNED_HOST` bedeutet nur lokales Staging; echte öffentliche Auslieferung erst nach dem tatsächlichen Render-Deploy und HTTPS-Binäraudit belegen.

## 5. Zwingende Abnahmereihenfolge und Kriterien

|Schritt|PASS-Kriterium|FAIL / aktueller Status|
|---|---|---|
|A: Quellen und QA|Exakter gewählter SHA; nachvollziehbarer Delta; aktuelle Node/Backend/Android-JUnit/Lint/unsignedAPK+AAB-Proof.8417 besitzt diesen PASS, Deploy oben;11Pflichtsuiten45Pflichttests und179Node/Backendtests.|
|A-Grenze|Andere Quelländerungen benötigen neue QA. Diese Dokumentation wird auf ausdrücklichen Mini-Auftrag nicht neu gebaut; kein Android-PASS für einen ungebauten neuen SHA behaupten.|
|B: Owner-SHA-Freigabe|Ausdrückliche Freigabe für genau den später gebauten SHA und Original-Publisher.|V36-Freigabe hier NOT_RUN; Arbeits-/Commitauftrag und altes Manifest approved=true ersetzen sie nicht. SHA-Abweichung → FAIL.|
|C: Original-P12 und signierter Bau|Publisher unverändert gepinnt, dortige Secret-Inputs gültig; Originalzertifikat/PrivateKeyEntry; erneute scriptinterne QA und Release-Lint/Assembly am selben SHA.|NOT_RUN. Fehlende/falsche Schlüssel, anderer Host/Service, Code≠36 oder falsches Zertifikat → STOP/FAIL, kein Ersatzsignierer.|
|D: tatsächliche signierte APK|Binärpackage exakt Owner-Paket, Code36/Name0.3.35, gültige v2-Signatur und Originalfingerabdruck, gemessener SHA/Länge; Manifest exakt dieselben Binärdaten und Quell-SHA.|NOT_RUN. Unsigned-QA-Hash und Manifest-Metadaten allein sind kein Zertifikatsnachweis.|
|E: wirkliche HTTPS-Auslieferung|Nach Publisher-Deploy `latest.json` und komplette APK neu laden; keine Redirects, feste Domain/Digest-Dateiname, tatsächlich ausgelieferte Bytes/Hash/Länge exakt passend. Heruntergeladene APK separat mit apksigner/aapt2 prüfen.|V36 NOT_RUN. `node scripts/continuum-owner-render-readback.mjs` prüft gestreamte Bytes, endet bei Erfolg ausdrücklich `APK_HASH_VERIFIED_CERT_NOT_VERIFIED`; erst zusätzliche echte Zertifikats-/Paketprüfung schließt die Lücke. `--manifest-only` ist kein APK-PASS. Node-Transportfehler dieses Hosts oben sichtbar.|
|F: Fold7 In-Place|Vorher tatsächliches installiertes Paket/Code/Zertifikat erfassen. Über erreichbaren Updateknopf laden, Android-Freigabe bestätigen, nachher OS-Paketinfo Code36/Originalzertifikat und `INSTALLED_AND_VERIFIED`, Funktions- und Datenvergleich.|NOT_RUN, kein Gerät angeschlossen. Berichtete installierte0.3.34/code35 ist kein neuer Geräte-Readback. Falls Gerät bereits Code≥36 hat: Update36 blockiert; keine Schutzumgehung.|

Für den Datenvergleich vor/nach F vorhandene Backups/Hashes erfassen: bestätigte Owner-Fakten und Widerrufe, Wissensarchiv, SWB1-/andere vorhandene Checkpoints, persönliches Lernprotokoll/Versionshistorie sowie Modellimporte. Kein Uninstall, kein App-Datenlöschen, keine neue Paketidentität. Die implementierte In-Place-Kette schreibt APK-Staging in den Cache; sie beweist allein keine fehlerfreie Datenmigration. Export/Restore-Kompatibilität und echter Wiederanlauf sind eigenständige Geräteprüfungen. Die historische256-Datensatz-/254-Importvorschau ist kein Disaster-Recovery-PASS. Bei Installabbruch/zu wenig Flash/Netzverlust muss die alte App weiterbenutzbar bleiben. Android-Downgrade nicht als gewöhnlichen In-Place-Rollback versprechen; alte signierte Artefakte und versionkompatible Datenbackups behalten.

Weitere harte Grenze: Node-HTTPS-Readback begrenzt APKs auf90000000Bytes; Android-/Publisher-Policy erlaubt bis2000000000Bytes. Die beobachtete etwa54.5MB-App liegt unter beiden Grenzen; Größensteigerungen vorher prüfen. Gemma-Modell-Dateien nicht ungeprüft in diese Update-APK einbetten.

## Abschlussstatus

Nur diese Markdown-Datei wird auf dem bestehenden PR331-Branch committed/gepusht und im PR verlinkt. Kein neuer Render-Build für Dokumentation. Keine Publisher-, Secret-, Signatur-, Installations-, Owner-Wissens- oder Produktivmodelländerung. Nächster Ingenieur beginnt mit A/B und dem aktuellen Publisher-DAG; C–F bleiben bis zu ihrer realen Durchführung offen. Forschungs-SHADOW und CDX1-Generierung bleiben getrennt vom originalsignierten Owner-Update.
