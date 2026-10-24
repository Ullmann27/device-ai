# Continuum Premium-UI – Übergabe (Designphase abgeschlossen)

Stand: 22.10.2026 · Zweig `opus/continuum-premium-ui-20261022` · Draft-PR #74 · Basis `619ebeb` (Release 0.3.12, versionCode 15)

Diese Phase ist laut Auftrag abgeschlossen: Das Design ist funktional integriert, technisch geprüft
(Umfang siehe unten) und als editierbarer GitHub-Stand gesichert. Weitere kosmetische Arbeit ist eine spätere,
eigene Phase. Kein Merge nach `main`, kein APK-Release, keine Installation ohne Heinz.

## Ziel und Ergebnis

Vorgabe: `docs/design_targets/continuum-2026-10-22/reference-phone.png` und `reference-fold.png`.
Der Startbildschirm wird aus diesen Bildern vermessen (Phone 412 dp, Fold 840 dp breit, gleichmäßig mit der
Fensterbreite skaliert) und mit echten Systemwerten gefüllt.

JVM-Vorschauen des echten Zeichencodes (Java2D statt Android-Skia, daher leichte Raster-/Hinting-Unterschiede):

| Datei | Inhalt |
|---|---|
| `docs/design_targets/continuum-2026-10-22/preview-phone.png` | Phone mit den Beispielwerten der Referenz (nur Vorschau, `app/src/test/.../ReferencePreviewData.kt`) |
| `docs/design_targets/continuum-2026-10-22/preview-fold.png` | Fold aufgeklappt, ebenfalls mit Beispielwerten |
| `docs/design_targets/continuum-2026-10-22/preview-phone-empty.png` | Frische Installation: nichts gemessen, überall „—“ |
| `docs/design_targets/continuum-2026-10-22/preview-fold-empty.png` | dasselbe für den Fold |

Bekannte sichtbare Abweichung zur Referenz: Die Kugel ist im Inneren weniger dicht und weniger hell als im
gerenderten Referenzbild. Layout, Farben, Schriften, Karten und Navigation stimmen nach Messung überein.

## Code-Landkarte

| Datei | Aufgabe |
|---|---|
| `ui/ContinuumPainter.kt` | plattformfreie 2D-Schnittstelle (Android-Canvas in der App, Java2D in der Vorschau) |
| `ui/AndroidContinuumPainter.kt` | Canvas-Implementierung mit Shader-/Pfad-Cache, Schriften aus `assets/fonts` |
| `ui/ContinuumFonts.kt`, `assets/fonts/` | Montserrat (OFL 1.1) für Überschriften, Roboto (Apache 2.0) für Text |
| `ui/ContinuumTheme.kt` | aus der Referenz gemessene Farben und Dichte |
| `ui/ContinuumHologramRenderer.kt` | lebender Kern: Kugel, Orbits, Sockel, Zustände READY/LISTENING/THINKING/SPEAKING/RESEARCH/LEARNING |
| `ContinuumHologramView.kt` | Android-View des Kerns; Animation nur sichtbar, mit Animator-Freigabe und ohne Energiesparmodus |
| `ui/ContinuumHomeLayout.kt` | vermessene Slot-Positionen Phone/Fold, Regel `isWide` (ab 700 × 500 dp) |
| `ui/ContinuumHomeElements.kt`, `ui/ContinuumWideElements.kt`, `ui/ContinuumSurfaces.kt`, `ui/ContinuumIcons.kt` | gezeichnete Karten, Labels, Diagramme, Navigation, Icons |
| `ui/ContinuumHomeScreen.kt` | ordnet jedem Slot sein Element zu |
| `ui/ContinuumHomeView.kt` | ein klickbarer, fokussierbarer View je Slot; TalkBack liest die echten Werte vor |
| `ui/ContinuumDashboardState.kt` | alle angezeigten Werte; `null` = nicht gemessen → „—“ |
| `ui/ContinuumDashboardMath.kt` | Reihen, Tages-/Vortagesvergleich, Median, 24-h-Differenz, Messverlauf |
| `ContinuumDashboardCollector.kt` | liest die echten Werte (alle 5 s, nur solange sichtbar, nicht im UI-Thread) |
| `diagnostics/ContinuumDiagnostics.kt` | persistentes Fehler- und Diagnosezentrum |
| `AgentActivity.kt` | Einbau: `buildConversationPane`, `openHomeSlot`, `refreshDashboard`, `showDiagnosticsCenter`, Navigation |

## Herkunft jeder Anzeige

| Anzeige | Quelle | wenn nicht verfügbar |
|---|---|---|
| Modellstatus | „Aktiv“ **nur** bei geladenem lokalem Sprachmodell im Zustand READY; lesbarer SWB1-Status heißt „Nur Startkern“ | „Nicht verfügbar“ |
| „Prüffragen“ % und Segmentbalken | Anteil richtig beantworteter Holdout-Prüffragen des Lernkerns (Intent + Semantik). **Keine allgemeine Intelligenzbewertung**, so auch in TalkBack | „—“, leerer Balken |
| 24-h-Veränderung | gespeicherter Messverlauf (alle 30 min, 30 Tage); Vergleich mit Messung von vor 24 h–3 Tagen; zeigt auch Rückgänge („−n Pkt.“) | „24 h: noch kein Vergleich“ |
| Reaktionszeit | Median der gemessenen Chat-Antwortzeiten | „—“ |
| Kontextfenster | Startkern: „1 Zug (Thema)“. Er speichert 8 Züge, antwortet aber nur mit dem Thema des letzten. Beim geladenen Modell nicht gemeldet | „—“ |
| Lernereignisse heute, Veränderung zu gestern, Stundenkurve | Erfahrungs-Zeitstempel des Lernkerns (`experienceTimeline`) | „—“ / keine Prozentangabe, wenn gestern 0 |
| Speicher belegt, Segmente | `StatFs` des App-Datenträgers; Kategorien nach Verzeichnisnamen | „—“ |
| Quellen („N gespeicherte Quellen“) | Anzahl gespeicherter Web-Notizen | „—“ |
| Entwicklung: offene Aufgaben, laufend, fehlgeschlagen, 14-Tage-Balken | Entwicklungs-Backlog (nicht abgeschlossene, laufende, fehlgeschlagene Aufgaben; DONE-Übergänge je Tag). Abzeichen „Keine Fehlschläge“ statt „Alle Systeme nominal“ | „—“, „Status unbekannt“ |
| Systemstatus (Fold) | Abzeichen „n offen“ aus der Diagnoseliste. Ist die Liste nicht lesbar: „Liste unlesbar“. **Nie „Optimal“** | „Liste unlesbar“ |
| Systemaktivität (Fold), Messung „alle 5 s“ | App-CPU über `Process.getElapsedCpuTime`, RAM über `ActivityManager.MemoryInfo`, Speicher über `StatFs`. Netz nur als Text (`NET_CAPABILITY_VALIDATED`), **ohne erfundenen Pegel** | GPU immer „—“ (nicht messbar) |
| Glocken-Badge | offene Diagnosemeldungen + wartende Lernfrage | kein Badge bei 0 |
| Kern-Labels (Verbunden, Denken, …) | echte Zustände (Internetmodus, beschäftigt, Zuhören, Recherche, Lernen, Autostudium) | „Bereit“ / „Aus“ |

## Bedienung: jeder Tipp hat ein echtes Ziel

| Element | Ziel |
|---|---|
| Logo | Startseite nach oben |
| Suche | Wissensarchiv durchsuchen |
| Glocke, Systemaktivität | Fehler- und Diagnosezentrum |
| Profil | Personalisierung |
| Modellstatus, Modell-Pill | Modellzentrum |
| VERBUNDEN | Internet-Freigaben |
| DENKEN | Chat |
| LERNEN, Lernkarte | Lernzentrum |
| ANALYSIEREN | Recherche |
| ENTWICKELN | Entwicklungszentrum (dort Coding-Agent, Backlog, Teamleiter, Handy-Builder) |
| EVOLVIEREN | Lern-Dashboard |
| Speicherkarte | oben Gedächtniszentrum, unterer Bereich „Quellen“ Mentor-Quellen |
| Entwicklungskarte | Entwicklungszentrum; auf dem Fold unterer Teil Backlog |
| Systemstatus (Fold) | Systemzentrum (Shizuku, Internet, Cloud, Modelle, Schnellzugriffe) |
| Navigation Phone / Rail Fold | Start, Chat, Lernen, Entwickeln, Mehr (Rail zusätzlich Analysieren, Speicher, System) |

Die alten Startseiten-Bausteine (Cockpit-Kacheln, Kopfzeile, Seitenleiste) sind entfernt. Alle Funktionen, die sie
öffneten, sind über die Tabelle oben, das „Mehr“-Blatt und den Chat-Komponisten (Mikrofon, Denken, Web, Datei)
erreichbar. Die Statusabzeichen Gerät/Web/Basis werden nicht mehr angezeigt (die Referenz hat keine); sie bleiben
initialisiert, weil bestehende Aktualisierungspfade sie beschreiben. Ihre Fakten stehen im Systemzentrum.

## Fehler- und Diagnosezentrum

- Datei `files/diagnostics/issues-v1.tsv`, eine Zeile je Problem; Schlüssel = Komponente + Code, daher keine Duplikate,
  sondern Zähler, erstes/letztes Auftreten, App-Version.
- Status offen → behoben (durch eine spätere erfolgreiche Prüfung derselben Komponente) oder quittiert (durch Heinz).
  Tritt ein quittiertes Problem erneut auf, ist es wieder offen.
- Neue Probleme melden sich sofort (Hinweis), die Liste bleibt in der Glocke sichtbar; „Verlauf anzeigen“ zeigt alles.
- Vor dem Speichern werden Zugangsdaten, Tokens, E-Mail-Adressen, Telefonnummern, URL-Parameter, private Pfade und
  lange Hashes/Base64 entfernt. Export als Klartext über das Android-Teilen-Menü, ebenfalls bereinigt.
- Abstürze werden über den Standard-Handler für nicht abgefangene Ausnahmen erfasst und danach normal weitergereicht.

## Prüfstatus (nur tatsächlich Ausgeführtes)

| Stufe | Ergebnis |
|---|---|
| SOURCE_INTEGRATED | ja |
| KOTLIN_COMPILE_CHECKED | ja: `CONTINUUM_ANDROID_DEPS=<jars> scripts/android-kotlin-compile-check.sh <out>` → keine Fehler außerhalb der LiteRT-LM-Dateien (deren 127 Fehler sind unverändert: Google Maven ist hier gesperrt) |
| JVM_TESTED | 13/13 JUnit-Tests (nach dem Audit; zuvor 12/12) (kotlinc 2.0.20 + JUnit 4.13.2, außerhalb von Gradle): `ContinuumDashboardMathTest`, `ContinuumDiagnosticsTest`, `ContinuumHomeScreenTest` |
| Node-Quelltests | `node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs` → 808/813. Die 5 Fehlschläge sind die Linux-Sandbox-Tests in `scripts/continuum-isolated-node.test.mjs`: `/bin/setpriv` findet `libcap-ng.so.0` in diesem Container nicht (Umgebung, nicht UI) |
| Gradle-Unit-Tests | nicht ausgeführt: Android SDK hier nicht verfügbar |
| APK_BUILT | nein |
| DEVICE_VERIFIED | nein |

16 Quelltests, die das alte Cockpit lasen, prüfen jetzt den echten Startbildschirm, darunter: jedes antippbare Element
hat eine Route zu einem echten Bildschirm. Eine Probe-Mutation (Route entfernt) lässt die Tests fehlschlagen.

## Nachprüfung nach dem Besitzer-Audit (22.10.2026, abends)

**Korrigiert: Anzeigen, die mehr behaupteten, als die Daten tragen**
- „Online“ bei bloß lesbarem SWB1-Status.
- „Leistung“ für die Holdout-Quote.
- „Optimal“ bei leerer Fehlerliste. Dasselbe galt, wenn die Liste nicht lesbar war, weil der Fehler zu 0 wurde.
- „Alle Systeme nominal“.
- Netzwerk-Balken und Netzwerk-Ring auf 100 % bei bloßer Verbindung.
- „Echtzeit“ statt der tatsächlichen Abtastung alle 5 s.
- „8 Gesprächszüge“ Kontext.
- „aktive“ Quellen und Projekte.

Ein Node-Test sichert diese Regeln ab (`android-holographic-home.test.mjs`).

**Diagnoseexport gegen Datenlecks**
- Die Bereinigung wurde erweitert um: Authorization-Header, JWT, AWS-Schlüssel, private PEM-Schlüssel, `hf_`/`glpat-`-Token, Home-Pfade, IPv4-Adressen und die Namen des Besitzers. Der Paketname bleibt lesbar.
- Ein neuer adversarialer JUnit-Test prüft **die Rohdatei auf der Festplatte und den Export** gegen 16 Geheimnisformen.
- JVM jetzt **13/13**.

**Keine leeren Tests**
- `scripts/continuum-ui-test-mutations.mjs` bricht je umgestelltem Node-Test genau eine echte Funktion (Route, Handler, Barrierefreiheit, Ehrlichkeitsregel). Der Test muss dann rot werden.
- Erster Lauf: 17/20. Drei Lücken wurden gefunden: Zwei Tests prüften Zeilen, die auch in einer zweiten Klasse stehen; eine Mutation war zu schwach.
- Nach der Korrektur: **20/20**, Beleg in `docs/evidence/ui-20261022/mutation-check.txt`.

**Die LiteRT-LM-Kompilierfehler stammen nicht aus PR #74**
- Der SDK-freie Kompiliercheck läuft auf dem unveränderten Release `619ebeb` und auf diesem Zweig.
- Ergebnis: **identische 127 Fehler nach Datei und Meldung**, nur in `localmodel/LiteRtLmLocalModelRuntime.kt` (109) und `localmodel/LocalModelCatalog.kt` (18).
- Ursache: Die Abhängigkeit `com.google.ai.edge.litertlm:litertlm-android:0.18.0` gibt es nur auf Google Maven, und das ist hier gesperrt.
- Der signierte 0.3.12-Build auf Render hat sie aufgelöst.
- Ein SDK-freier Kompiliercheck **ersetzt keinen vollständigen Android-Build**.

**Die 5 fehlschlagenden Node-Tests stammen nicht aus PR #74**
- Dieselben 5 Sandbox-Tests schlagen in diesem Container auch auf dem unveränderten Release fehl (Node 808/813 dort wie hier).
- Grund: `/bin/setpriv` findet `libcap-ng.so.0` nicht.

## Bekannte Grenzen

- Kein APK-Build und kein Gerätetest in dieser Umgebung. Für ein Update-APK: gleiches Paket
  `at.heinz.deviceai.mentorpaired`, gleiches Zertifikat, versionCode > 15; nur mit Heinz' Freigabe.
- Im Chat-Modus ist der Kern nicht sichtbar; die Startseite und der Chat wechseln sich ab.
- LISTENING ist eine Zustandsanimation: Der System-Spracherkenner liefert keinen Pegel. Die Sprechlinien reagieren nur
  auf echten Ausgabe-Pegel (> 0).
- GPU-Auslastung ist ohne Root nicht messbar („—“). Speicherkategorien beruhen auf Verzeichnisnamen.
- Phone im Querformat nutzt das Phone-Layout mit der Fensterbreite skaliert; die Seite wird größer und scrollt.
- `ContinuumNexusHomeSpec` und `ContinuumWorkAreaNavigationSpec.quickActionColumns` werden vom neuen Startbildschirm
  nicht mehr benutzt (eigene Tests bleiben grün). Entfernen nur nach Heinz' Entscheidung.

## Weiterarbeiten

```bash
# Vorschau (braucht kotlinc 2.0.20 und JUnit-Jars, siehe Kopf von ContinuumPreviewMain.kt)
java -cp <preview.jar> at.heinz.deviceai.ui.ContinuumPreviewMain <repo> <outDir> phone|fold|phone-empty|fold-empty|hero
# Kompilierprüfung ohne Android SDK
CONTINUUM_ANDROID_DEPS=<jars> scripts/android-kotlin-compile-check.sh <outDir>
```
