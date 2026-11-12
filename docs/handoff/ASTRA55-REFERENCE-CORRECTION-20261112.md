# Cockpit: Korrektur nach Referenzvergleich

Status: INTEGRIERT IM SUPPORTZWEIG / GEZIELTE HOSTPRÜFUNG BESTANDEN.
Die abschließende Fünfring-Renderfolge wird auf dem eingefrorenen Produktstand erstellt.
Übergabe: #801 gegen die vorhandene Cockpit-Integration #799.

Produktquelle: `62c8602b1437c6b9f6637b2e101f603452089705`.
Öffentlicher identischer Baum: Commit
`c3e9ddf4a7691a26b430b4e4a7778b41081b22f3`, Tree
`4a56b1dbcc0e51ed41cdacf8f060b3e1a163b3c7`.
Hauptintegration bis `7d6479f8c7c1c8e3c59f62399fb0a366676afe53` enthalten.

## Sichtbare Korrektur

Der Besitzer bewertete die vorherige Darstellung als zu transparent und noch
nicht referenzgerecht. Der Originalavatar lädt bei Deckkraft 1 seine
ursprünglichen OPAQUE-/MASK-Materialien. Kugel und Ringe liegen hinter der
Figur; die Smartphone- und Fold-Rahmung zeigt auch Schulterpartie und Brust.
Der Originalavatar-GLB ist byteidentisch.

Die Originalkugel des Hauptteams mit fünf orbitalen Ringen ersetzt die
zwischenzeitliche Vier-Ring-Näherung. CockpitWorldTransforms.orb verwendet
direkt CockpitStageFrame.orbTransform; die Ringe verwenden die ursprüngliche
Phase und `rest × Rx(-pitch) × Ry(yaw)`. Es gibt keine zusätzliche Android-Uhr
und keine zweite Filament-Engine. Der neue Originalkugel-GLB bleibt byteidentisch.

Der Arbeitsplatz besitzt einen gerundeten Tisch mit echten Kanten, eine um
12 Grad geneigte Tastatur und drei körperliche Monitorrahmen. Der mittlere
Monitor zeigt die vorhandene GPU-Bildquelle. Die nativen Seitenflächen und
Beschriftungen liegen lesbar vor der Szene. Auf dem Smartphone werden die
Seitenmonitore ausgeblendet. Kompakte Statusflächen und die acht direkten
Navigationsziele bleiben bedienbar.

Die Seitenmonitore sind eigenständige Szenenwurzeln. Ihr Raumtransform wird
genau einmal angewandt; ältere zusätzliche Tastaturreihen werden nicht erneut
eingebaut. Der tatsächliche --workstation-only-Neuaufbau bestätigt dieselben
vorhandenen Assetbytes.

## Drei Zuständigkeiten

1. Avatar/Kugel: deckender Originalavatar, responsive Rahmung, fünf Originalringe
   und tatsächliche Geometrie-/Projektionsprüfungen.
2. Architektur: vorhandener Generator, Tisch, Tastatur, GLB, Materialpins und
   Seitenmonitorwurzeln.
3. Rendernachweis: dieselben produktiven Kotlin-Matrizen, ein Kameraraum,
   gekennzeichneter Beispielinhalt und sichtbare Bewegung.

Root verbindet native Oberfläche, aktuelle Änderungen des Hauptteams und
gezielte Prüfung. Andere aktive Arbeitsbäume bleiben unberührt.

## Bestehende Funktionsanschlüsse

Die Bild-, Sitzungs-, Scroll- und Aktionssicherungen des Hauptteams sowie dessen
Remount-/Split-Screen-Korrektur sind enthalten. Gebundene Work-Events steuern
weiterhin die Monitorfahrt; bloßes Verbinden einer Bildquelle startet keine
Arbeitsbewegung. Gleiche Bewegungsziele starten keine neue Fahrt. Umkehr bleibt
kontinuierlich. Verbergen, Hintergrund und Detach beenden den Animator und
setzen eine saubere Endpose ohne Restreferenz.

Beispielinhalt existiert ausschließlich in den Hostwerkzeugen. Er ersetzt
keine Android-Bildquelle und wird nicht als Livebild bezeichnet.

## Ausgeführte Prüfung

- Scoped Kotlin-Kompilation von 31 Produktdateien und Abhängigkeiten: PASS.
- 37 Geometrie-, Originalring-, Material- und Framingprüfungen: PASS.
- 7 native Feed-/Work-Event-/Lifecycleprüfungen: PASS.
- Ein historischer Canvas-Helpertest: separat PASS, kein Unified-GPU-Beleg.
- Tatsächlicher Workstation-Neuaufbau und Asset-/Materialverifier: PASS.

Quellhashes, Befehle, Protokolle und Grenzen:
`docs/evidence/cockpit-five-ring-qa-20261112/README.md` und
`docs/evidence/cockpit-five-ring-materials-20261112/`.

## Darstellungsnachweis und Grenzen

Die abschließende Folge verwendet dieselben drei GLBs und Kotlin-Matrizen in
einer Blender-Szene mit einer perspektivischen Kamera. Vorgesehen sind Ruhe-,
Übergangs- und Arbeitsansicht für Fold/Phone sowie ein Drei-Sekunden-Clip.
Renderoptionen werden im tatsächlichen Manifest dokumentiert. Native Texte
und gekennzeichneter Beispielinhalt bleiben unverändert.

Frühere Vier-Ring-Bilder und Testbelege sind historische Zwischenstände und
ersetzen den aktuellen Fünfring-Nachweis nicht. Die bisherigen Ansichten
wurden tatsächlich visuell geprüft; die letzte Reihe wird nach Abschluss
ebenfalls geprüft und gesondert abgelegt.

Physischer Fold7-, Android-Filament-/GPU- und echter App-Capture-Beleg bleiben
NOT_RUN. Hostlicht und Blender-Bildwirkung belegen keine identische
Gerätebelichtung oder Geräteleistung. Kein neues APK-/Installations- oder
Main-Merge-Ergebnis wird behauptet.
