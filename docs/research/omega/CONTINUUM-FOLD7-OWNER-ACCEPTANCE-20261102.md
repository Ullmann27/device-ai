# Originalsigniertes Fold7-Update — vorbereiteter Abnahmeweg

Status: UNSIGNED_HOST_QA_PASS; SIGNING_NOT_RUN; DEVICE_NOT_RUN.

Geprüfter Quellstand48c0fb1f2b4db06619e170bf36f8d070e002eca4.
Paket at.heinz.deviceai.continuumlocal, Version0.3.37/Code38.
APK-SHA256 abf0664e2bb79190e932c4f8e46adf8545fc50c006c9e37bdc34f5d4a9669845.

PASS/FAIL je Gerätetest schriftlich erfassen. Fehlender Zugriff ist NOT_RUN.
Ein abweichendes Paket/Zertifikat, Datenverlust oder erfundener Aktionserfolg ist FAIL.
Code38 nur verwenden, wenn der tatsächlich installierte Code niedriger ist.

1. Exakten geprüften Quellcommit aus der Abschlussquittung wählen. Der derzeitige
   Forschungszweig enthält die PR360-Integration, keinen Merge nach main.
2. Release-Freeze nur durch ausdrückliche Besitzerfreigabe für genau diese SHA
   aufheben. Auf dem bisherigen Publisher müssen CONTINUUM_RELEASE_APPROVED_SHA
   und CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER genau diese SHA enthalten.
   CONTINUUM_RELEASE_VERSION_CODE=38; CONTINUUM_RELEASE_HOST bleibt
   continuum-owner-v23-release-preflight.onrender.com. Die originale P12 verbleibt
   ausschließlich auf diesem Publisher. Keine Schlüssel in Chat/GitHub übertragen.
3. Quellref auf dem Publisher kontrolliert einstellen und erneut für exakt diese
   SHA vollständig prüfen. Die aktuelle Render-Rezeptur erwartet den kanonischen
   Integrationszweig; PR361 nicht mit dem alten bdf0f89-Build verwechseln.
   Dann erst scripts/render-continuum-owner-v38-release.sh ausführen.
4. Binäres Paket at.heinz.deviceai.continuumlocal, versionCode38/version0.3.37,
   Originalzertifikat SHA256 6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23,
   APK-Digest und HTTPS-Readback unabhängig vergleichen. Katalog bleibt approved:false.
5. Auf dem Fold7 tatsächliche installierte Version und Signatur auslesen; Code38
   muss höher sein. Über bestehende App installieren, ohne Deinstallation oder
   Datenlöschung. Vorher vorhandenen Export-/Sicherungsweg nach Besitzerwahl nutzen.
6. Gesprächsverlauf/Bestätigungswissen prüfen, lokalen Chat und STOP testen,
   mehrsprachige Korrektur mit spätem NICHT, Werkzeugfreigabe/Widerruf,
   Wiederanlauf mit UNKNOWN-Quittung und genau eine freigegebene Android-Mission.
   Gemma↔GGUF, Live-Kamera/Avatar und on-device CCC1 bleiben eigene Feldtests.
7. Erst nach tatsächlicher Fold7-Abnahme separat Veröffentlichung freigeben.
   Bei Fehlern keine Neuinstallation, Datenmigration oder Schlüsseländerung.

Diese Datei enthält ein vorbereitetes Protokoll, keine ausgeführten Geräteaktionen.
