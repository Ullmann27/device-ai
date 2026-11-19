# Prüfbelege für Command03-Computer-Navigation

Geprüfte Quelle: `7e783f3ee8bc3e75f800b8249f5cef283187752d`.
Gesamter geprüfter `app`-Baum: `6f34fa6a25cf884bb7fe3d7cb708d18e4c0a7148`.

- `receipts/`: vollständige Runner-Belege, Testauswahl, Quellhashes und Ressourcensamples.
- `logs/`: unveränderte Gradle-Ausgaben; rote Läufe sind ausdrücklich Fehlervorführungen.
- `junit/`: 18 frische XML-Dateien der drei grünen Gruppen, **128 Tests**.
- `web/`: **28 bestandene** DOM-/Interop-Fälle, tatsächliches synthetisches Kotlin-Paket und Originalasset-Abgleich.
- `apk/`: unabhängiger Paketprüfer, seine Ergebnisse und Ausgaben.
- `manifest.json`: SHA-256 und Dateigrößen aller ursprünglichen Belegdateien.

Die ursprünglichen absoluten Laufpfade in den Belegen bleiben unverändert.
Die oben angegebenen relativen Ordner enthalten ihre gesicherten Dateien.
Der Prüfer verwendet die bestehende Toolchain und die unveränderten Repo-Gates;
konkrete Befehle stehen jeweils im Feld `command` der Laufbelege.

`navigation-red-ui-02` belegt den Navigationsfehler vor der Produktkorrektur:
17 Fälle, davon genau ein erwarteter Fehler. `navigation-red-ui-01` enthält
zusätzlich den im [Handoff](../COMMAND03-COMPUTER-NAVIGATION-HANDOFF.md)
erklärten Layoutfehler der neuen Leerlauf-Testvorbedingung. Diese Fälle werden
nicht zum grünen Ergebnis addiert.

Die **156 bestandenen Fälle** sind lokale Fokusprüfungen. Die Prüf-APK ist
unsigniert; Installation, reale Android-/GPU-Darstellung und vollständige
Besitzer-Release-QA sind dadurch nicht bestätigt.
