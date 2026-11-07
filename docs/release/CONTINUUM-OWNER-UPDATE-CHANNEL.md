# Continuum Lokal – dauerhafter Original-Updatekanal

## Verbindlicher Schnittstellenvertrag

Der bereits installierte Android-Client liest ausschließlich `https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/latest.json`. Sein Parser `ContinuumOwnerUpdateCatalog.kt` verlangt exakt zwölf Felder: `schema`, `channel`, `packageName`, `versionCode`, `versionName`, `apkUrl`, `apkSha256`, `byteLength`, `sourceCommit`, `signerSha256`, `approved`, `notes`. Zusätzliche Schlüssel wie `status` führen zu einem sicheren Abbruch.

Der freigegebene Katalog muss `schema: 1`, `channel: "OWNER_RELEASE"`, `approved: true`, die Originalpaketkennung `at.heinz.deviceai.continuumlocal`, den Originalzertifikatsfingerabdruck `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`, einen unveränderlichen SHA-256-APK-Dateinamen, exakten Quellcommit, korrekte Dateigröße sowie eine zur VersionCode passende VersionName enthalten. Das ist die Besitzerfreigabe für den Download; die eigentliche Android-Installation muss weiterhin vom Besitzer bestätigt werden.

## Gesicherter Ablauf für spätere Versionen

Die signierte APK wird ausschließlich im bestehenden Render-Original-Publisher gebaut; dessen bestehender HTTPS-Host und Publish-Pfad bleiben fest. Jede neue Version braucht eine reviewte Quell-SHA, exakte SHA-Freigabe und Freeze-Aufhebung, vollumfängliche QA, das ursprüngliche private Signaturmaterial und `apksigner`-/`aapt2`-Nachweise. Das Signaturmaterial bleibt geheim und verlässt den Publisher nicht. `main` sowie der installierte App-Datenbestand werden nicht durch Release-Operationen verändert.

`scripts/continuum-owner-catalog-contract.mjs` prüft **beim Release-Build** den erzeugten Live-Katalog gegen die tatsächliche Kotlin-Schlüsselliste des ausgelieferten Android-Clients und die bestehende Kanal-/Freigabepolicy. `scripts/continuum-owner-catalog-contract.test.mjs` deckt gültige sowie bösartig/versehentlich inkompatible Kataloge ab; `scripts/continuum-owner-v38-release.test.mjs` prüft zusätzlich den echten Manifest-Generator. Ein inkompatibles Manifest blockiert künftig den Deploy, statt eine nicht funktionierende Aktualisierung auf den öffentlichen Updatekanal zu legen.

**Wichtig:** Die aktuell geprüfte Ausführung ist auf Version 0.3.37 / Code 38 festgelegt; bei nachfolgenden Versionsnummern ist die Versionierung des Release-Skripts samt QA-Gates kontrolliert anzupassen und zu testen. Der Kanalvertrag und die automatische Kompatibilitätsprüfung sind wiederverwendbar; ein künftiger Versionswechsel gilt nicht ohne neue Build-/Signierbelege als getestet. Niemals die Originalsignatur-, Paket-, Host- und SHA-256-Prüfungen deaktivieren.

## Ursache des 0.3.37-Problems und Korrektur

Die zuerst bereitgestellte signierte 0.3.37 enthielt `channel:"OWNER_RELEASE_CANDIDATE"`, ein zusätzliches `status` und `approved:false`. Der installierte Android-Parser lehnte das zusätzliche Feld bereits vor allen anderen Prüfungen mit `Manifeststruktur unbekannt oder unvollständig` ab. Die korrigierte Besitzerfreigabe erzeugt `channel:"OWNER_RELEASE"`, **kein `status`**, `approved:true` und erhält sämtliche ursprünglichen Paket-, Zertifikats- und Download-Prüfungen. Die Freigabe betrifft nur das vom Besitzer angeordnete Update 0.3.37, nicht unbekannte zukünftige APKs.

## Verifikation

Nach Render-Deploy muss `/continuum-local/latest.json` öffentlich genau zwölf Felder mit Freigabe und unveränderlicher signierter APK-URL liefern. Die angegebene Binärdatei muss als signierte APK erreichbar sein, Prüfsumme und Länge müssen übereinstimmen. Auf dem Fold7 anschließend über **Sichere Updates** prüfen und laden; Android verlangt eine ausdrückliche Installationsentscheidung. Bei jeder Abweichung Update verweigern und vorherige Installation unangetastet lassen.
