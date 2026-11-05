# Continuum Code44 – Build-Handoff (05.11.2026)

Status: Lokale Reparaturprüfungen grün; vollständiger neuer Publisherlauf noch erforderlich. Noch keine Code44-APK signiert oder veröffentlicht.

## Quellen und vorhandener Kanal

- App-Kandidat: `e9b3922d2c9afadf1e5d3594f294cebea5fab3ef`, PR #565 (Draft).
- Vorheriger Live-Stand: `ff8f6070bb3a6f4dfbb6215ce3e3e269974c1d53`, Version 0.3.42 / Code43.
- Ziel: 0.3.43 / Code44, `at.heinz.deviceai.continuumlocal`.
- Original-Publisher: `srv-dbf1tr2vcj2c73ehpfog`, unveränderter Host `https://continuum-owner-v23-release-preflight.onrender.com`.
- Lauf: `dep-dbmeldon74is73cbjik0`, gestartet 2026-11-05T20:44:39Z, exakte Quelle e9b3922d.
- Publisher-Branch per Fast-forward aktualisiert, kein Force-Push; `main` und PR #565 unverändert.
- Bestehender Einstieg `scripts/render-continuum-m8-owner-release.sh` delegiert bei Code>=39 an den kontinuierlichen QA-/Release-Pfad. Version wird ausschließlich über `CONTINUUM_RELEASE_VERSION_CODE=44` gesetzt; die Originalpaket-Spur nutzt `continuumStandaloneVersionCode`, nicht `continuumOwnerLocalVersionCode`.
- Original-P12 verbleibt ausschließlich im Publisher. Keine Schlüsselabfrage oder Schlüsselübertragung.

## Echte Prüfungen

- Lokale Release-/Katalog-/Negativprüfungen auf e9b3922d: Node 24.22.1, 75 PASS / 0 FAIL / 0 SKIP.
- Render-Toolchain: JDK 17.0.20.1, Gradle 8.11.1, Node 24.22.1.
- Render-Full-Node: 11 Fehler in sieben Verdrahtungs-Vertragsdateien; keine Freigabe übergangen.
- Render-Backend: `QA_GREEN backend-lockfile-check-and-tests` am 05.11.2026 um 20:48:10Z.
- Der erste Renderlauf endete am 05.11.2026 um 21:07:37 UTC mit `build_failed`. Android: 3.381 Fälle, 3.350 PASS / 11 FAIL / 20 SKIP. Sechs Fehler betreffen die alte reflektierte `JarvisWorkWindow.open`-Signatur, fünf den Dialog-Helfer in `ContinuumReferenceCockpitUiTest`. Signierung und Veröffentlichung wurden durch die unveränderten QA-Gates gesperrt.

## Eng begrenzte Reparatur

Die Fehler in android-avatar-wiring, app-navigation-wiring, continuous-conversation-wiring, fold7-portrait-reference, opus55-preflight-contracts, owner-cockpit-voice-original-orb und continuum-ui-routing einzeln gegen die tatsächlichen Anschlüsse prüfen. Alte Textmuster nur ersetzen, wenn das neue Verhalten und seine Runtime-Tests die ursprüngliche Invariante nachweisen. Keine Tests auslassen und keine Release-Gates abschwächen.

Nach Reparatur: lokale betroffene Tests, dann vollständiger originaler Publisherlauf auf exakt neuer SHA; erst nach grüner QA Signierung und Downloadprüfung. Bestehender Code43 bleibt bis zur erfolgreichen Veröffentlichung erhalten.

## Noch offen

- Vollständiger grüner QA-/Signierlauf des finalen SHA.
- Unabhängiger HTTPS-Download: zwölf Manifestfelder, Quelle/Code/Paket, Dateilänge, SHA-256, v2-Signatur und Originalzertifikat mittels apksigner, Paket/Version mittels aapt2.
- Reale Fold7-Installation, GPU, Audio, Temperatur und Updateknopf: NOT_RUN.

## Reparatur-Checkpoint

Die elf Fehler wurden gegen unveränderten App-Code e9b3922d reproduziert. Sie betreffen alte Quelltextverträge nach der bereits vorhandenen WorkWindow-, AvatarFraming-/Neck- und Sprach-Chunk-Integration. Ausschließlich sieben Node-Testdateien wurden angepasst; Produktionscode und Publisher-Gates bleiben unverändert.

| Teilbereich | RED | GREEN | Negative Anschlusskontrollen |
| --- | --- | --- | --- |
| Navigation / Center-Buttons | 11 PASS / 4 FAIL | 15 PASS / 0 FAIL / 0 SKIP | 10/10 Defekte erkannt |
| Framing / Hals / Freisprechen | 7 PASS / 5 FAIL | 12 PASS / 0 FAIL / 0 SKIP | 6/6 Defekte erkannt |
| Sprachabschluss / nächste Abschnitte | 6 PASS / 2 FAIL | 8 PASS / 0 FAIL / 0 SKIP | ID-, STOP-, Reihenfolge- und MORE_CHUNKS-Grenzen ausdrücklich geprüft |

Node-Version: 24.22.1. Alle 35 bestehenden Testfälle bleiben erhalten. Der erste Renderlauf e9b3922d bleibt wegen seiner nachgewiesenen Fehler für eine Veröffentlichung gesperrt.

## Vollständiger Node-Nachlauf

- Ergebnis: 2.424 erfasste Fälle, 2.387 PASS / 0 FAIL / 37 SKIP; keine Platzhalter, vollständige Evidence, unveränderte Quellen während des Laufs.
- Lokaler Prüfcommit: `44c11ac454c54049b40ca0fe0dd7c4d122e98416`; über GitHub gespeicherter Reparaturcommit: `d6fa15ceb49aecf9dd91b482543eb8b632165867`.
- Beide Commits haben denselben Git-Quellbaum `bda2bee1f3a70d8907442bd5b734fb845f3ef702`; unterschiedliche Commit-Metadaten werden ausdrücklich nicht als identische Commit-SHA bezeichnet.
- JUnit-SHA256: `f00fcee6deefd23ee1fb8a6bfdb5c905317ecf96cbc1a60eedae8c2116717af6`.
- Die 37 Skips betreffen unter anderem nicht verfügbare Docker-/Kernel-Isolations- und optionale SDK-/JVM-Harness-Voraussetzungen. Sie sind weder PASS noch Geräteabnahme.
- Lokale Android-Toolchain installiert und verifiziert: JDK 17.0.20.1+1, Gradle 8.11.1, SDK36 Revision2 und Build Tools35. `:app:compileDebugKotlin` besteht (17 ausgeführte Tasks). Signiermaterial wurde nicht lokal abgerufen.

Der endgültige Remote-Kandidat muss vor Signierung erneut die komplette originale Publisher-QA bestehen. Ein gezielter lokaler UI-Nachlauf ersetzt diesen Gate-Lauf nicht.

## Belegter Layoutfehler nach Testanpassung

Die beiden vollständigen UI-Klassen wurden lokal mit echter Android-/Robolectric-Toolchain ausgeführt: 19 Fälle, 16 PASS / 3 FAIL / 0 SKIP. Sämtliche sieben `JarvisWorkWindowUiTest`-Fälle bestehen nach Korrektur der reflektierten Signatur. Die zwölf Cockpit-Fälle bestehen zu neun; die drei verbleibenden Fehler betreffen jeweils `CODING-AUFTRAG ENTWERFEN` bei Schriftfaktor 2,0.

| Profil | Fenster | Kopfzeile | Sichtbarer Scrollbereich | Benötigte Knopfhöhe |
| --- | --- | --- | --- | --- |
| Fold 840×320 | 279×270 px | 157 px | 101 px | 128 px |
| Fold 840×649 | 279×270 px | 157 px | 101 px | 128 px |
| Telefon 412×915 | 396×161 px | 81 px | 68 px | 95 px |

Die unveränderte Prüfung vollständiger Touchziel-Sichtbarkeit erkennt damit einen echten Höhenfehler. Die bisherige geometrische Mindestgröße 200×160 dp berücksichtigt weder die umgebrochene Kopfzeile noch die tatsächliche Aktionshöhe. Der Fix misst den erforderlichen Platz an der Kandidatenbreite; nur passende geschützte Rechtecke dürfen ein Arbeitsfenster öffnen. Andernfalls bleibt der bestehende native Dialog-Fallback erhalten. Keine Schriftverkleinerung, gelockerten Touchprüfungen oder Release-Gate-Änderungen.

JUnit-SHA256 des lokalen RED-Nachweises: Cockpit `844f8131a84f1b2ccfcbcbae5ab901aa71568404edbcc01bfecafb2a2a82c959`, WorkWindow `5b4e5ff4f50759f75e8766dad4bdb9034c617b0589e216b6658ee640eb9756d9`. Prüfquelle: d6fa15ce mit den noch uncommitteten zwei UI-Testanpassungen; keine Behauptung einer Prüfung des späteren Produktionsfixes.

## Produktionsfix und fokussierter GREEN-Nachlauf

- `JarvisWorkWindowGeometry` liefert geschützte Kandidaten nach bisherigem Flächenscore; die vorhandene dreiparametrige JVM-Signatur bleibt erhalten.
- `JarvisWorkWindow` misst echten Header und unparented Body an der inward gerundeten Pixelbreite. Erforderlich sind Header, Padding und das größte sichtbare interaktive Element, mindestens 48 dp Scrollhöhe.
- `JarvisWorkWindowHost` öffnet den ersten passenden Kandidaten. Sein Messnachweis erlaubt dem Test, beim nativen Fallback jeden geometrischen Kandidaten und dessen unzureichende Höhe ausdrücklich zu prüfen.
- Originaler Konstruktor, achtparametriges `open`, Close-/Frame-/Callback-Verhalten, Schutzflächen, Schriftgröße, Paket und Release-Gates bleiben erhalten.
- Zwei neue reale Coding-Nutzerwegtests bei 200 % Schriftgröße; alle vorherigen 19 Fälle der ursprünglichen beiden Klassen bleiben erhalten, dort nun 21 Fälle.
- Gezielter Android-Nachlauf: **19 PASS / 0 FAIL / 0 ERROR / 0 SKIP**, `BUILD SUCCESSFUL in 2m 56s`. Enthalten: zwei neue Coding-Regressionen, sieben WorkWindow-, drei Lifecycle-, zwei Density-, drei System-/Werkzeugszenen- und zwei Scroll-Fälle. Quellinhalt während des Laufs unverändert.
- Zusätzlich fünf Node-Navigationsverträge PASS / 0 FAIL / 0 SKIP; unabhängige read-only Prüfung des Produktionsdiffs ohne verbleibenden Befund.
- Dieser fokussierte Lauf ist keine Behauptung eines vollständigen Android-Nachlaufs. Der Publisher führt vor Signierung die gesamte Suite auf der endgültigen Remote-SHA aus.
