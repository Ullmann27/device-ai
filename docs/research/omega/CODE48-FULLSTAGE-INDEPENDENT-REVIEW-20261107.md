# Code48: unabhängige Prüfung der großen Gedächtnisszene

## Ergebnis und Quellen

Die unabhängigen Prüfungen benutzen die tatsächliche `AgentActivity`, deren vorhandene `ContinuumHomeView`, `ContinuumHologramView` und `CockpitAnimationBus`. Die Gedächtnisereignisse enthalten ausschließlich synthetische Metadaten.

| Prüfung | Exakter Produktionsstand | Ergebnis |
| --- | --- | --- |
| Fünf native Activity-Prüfungen | `284716538321cdf2d1f109aa57cf33eec907a64c` | 5 Tests, 0 Fehler |
| Positiver Activity-Pfad mit 24 Originalframes nach äußerem Canvas-`try/finally` | `a16e36bf97504a1423c1bc2fae1d7b038907ddfd` | 1 Test, 0 Fehler |

Testcommit: `17890105fc47dfbd14001ebbe2e9be8c149b007c`. Die sechs geänderten Produktionsdateien wurden jeweils frisch als Overlay gegen die vollständig kompilierten Klassen von `d73fc46b5a14ea5fc3b82a3bd3b96cacebe7d4de` kompiliert. Produktions- und Testkompilierung endeten mit Exit 0. Vorher-/Nachher-Hashes blieben in beiden Läufen unverändert. Die Manifest-Hashes des abschließenden Laufs wurden zusätzlich gegen die Git-Dateiinhalte von `a16e36bf97504a1423c1bc2fae1d7b038907ddfd` verglichen.

## Was tatsächlich geprüft wurde

1. Ein positives `DocumentLearned` über den Activity-Bus erreicht die große Szene. Der Host überschreitet den bisherigen Avatarbereich. Native Android-Pixel ändern sich, dieselbe Originalrenderer-Instanz vergrößert die Kugel und kehrt nach spätestens 4,8 Sekunden zu den ursprünglichen lokalen Renderermaßen zurück. Geometrie bleibt gleich; temporäre Orbgrenzen werden leer.
2. Nulltreffer, Nullimporte, fremde Turn-IDs und `MemoryStored` allein erzeugen keine große Szene. Positive Ereignisse während einer laufenden Episode verlängern sie nicht. Ein späteres neues positives Ereignis darf wieder starten.
3. Der echte `cancelLocalRequest` verwirft eine bereits eingereihte Gedächtnispräsentation. Das Umhängen des echten Hologramms in den Chat beendet die Szene. Alte Sequenzcallbacks spielen nach Rückkehr nicht erneut ab. Ausschalten der Animationen löscht den Zustand, erneutes Einschalten löst keinen Nachlauf aus.
4. Echte Touch-Ereignisse öffnen das System-Arbeitsfenster, schließen es während der großen Szene und öffnen es anschließend erneut. Damit wird die tatsächliche Viewhierarchie einschließlich Arbeitsfenster über dem Home-Compositor geprüft.
5. Bei höherer Pixeldichte bleibt jedes Pixel der tatsächlichen sichtbaren Shell-Button-Rechtecke im Compositor transparent. Die Bedienelemente liegen über der Szenenebene. Der Fullstage-Host selbst ist weder klickbar noch fokussierbar.

Der neue Test ist zusätzlich zur Produktion des anderen Arbeitszweigs entstanden; dieser Review änderte keine Produktionsdatei. Die gesamte finale Kandidatenquelle muss im gemeinsamen Abschlusslauf geprüft werden. Diese fokussierten Ergebnisse ersetzen diesen Lauf nicht.

## Native Bildfolge

Der optionale Export läuft nur mit `continuum.ui.evidenceDir`. Er verwendet den bestehenden Sequenz-/Framepfad, die echte Activity-Viewhierarchie und Android Native Canvas. Er zeichnet 24 unveränderte PNGs in Schritten von 220 Millisekunden über 5.060 Millisekunden. Die Metadaten und SHA-256 jedes Originals stehen im Evidence-Manifest. Es gibt keine Zwischenbildberechnung.

Die lokalen Originalframes zeigen die Dokumentflugphase, die vergrößerte bestehende Kugel und die Rückkehr ins Cockpit. Alle 24 Originale sind im Manifest mit Hashes adressiert und für die separat bereitgestellte Videovorschau verfügbar. Dieses Repository enthält ausschließlich technische Belege. Weder Vorschau-PNGs noch die private Referenzdatei oder daraus extrahierte Bilder werden hier gespeichert.

| Phase | Native Originalaufnahme |
| --- | --- |
| Dokumentflug, Sample +880 ms | `frame-04.png`, Hash im Manifest |
| Große Originalkugel, Sample +2.860 ms | `frame-13.png`, Hash im Manifest |
| Cockpit-Rückkehr, Sample +4.840 ms | `frame-22.png`, Hash im Manifest |

Die Frames zeigen die funktionale Folge Dokumentflug → Originalkugel → Cockpit. Die realen Bedienelemente bleiben sichtbar davor. Das ist ein Native-Canvas-Hostbeleg mit synthetischen Ereignissen; es belegt weder Filament-GPU-Pixel noch Fold7-Geräteverhalten, Gerätebildrate oder pixelgenaue Übereinstimmung mit einem Referenzvideo.

## Reproduzierbare Belege

[Evidence-Verzeichnis](../../evidence/code48-fullstage-independent-review-20261107/) enthält die beiden originalen Receipts, Compiler- und JUnit-Logs, den fokussierten Runner sowie das Quellen-/Frame-Manifest. Der Runner zeichnet SDK- und Classpath-Aufrufe im Receipt auf. Die übrigen vier unabhängigen Fälle wurden nach der abschließenden, ausschließlich den äußeren Canvas-Restore absichernden Änderung nicht erneut ausgeführt; der gemeinsame finale Gate-Lauf übernimmt die kombinierte Quelle.
