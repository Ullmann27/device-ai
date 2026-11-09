# Continuum Code 52 – Integrations- und Doppelentwicklungsregister

Stand: 09.11.2026, Quellprüfung; **kein Fold7-Abschluss**.

## Unveränderliche Referenz

- Code 51, originalsignierter Release-Quellstand: `6c23d295e2fca2d4e5950a866547733ec5ff22b0`.
- Code 52, getrennte Arbeitslinie: `chatgpt/code52-unified-integration-20261109`, Draft PR #671; Basis aus PR #669.
- Vollständig paginiert inventarisiert: **627 PRs** (`CODE52_ALL_PRS_20261109.jsonl`) und **727 Branches** (`CODE52_ALL_BRANCHES_20261109.jsonl`). Die 29 jüngsten PRs haben das Rolling-Cutoff `2026-11-08T13:52:00Z`; alle älteren bleiben ausdrücklich prüfpflichtig.
- **Metadaten sind kein Beweis für eine angeschlossene Fähigkeit.** Zählungen ändern sich bei neuen GitHub-Arbeiten.

## Quellgebundene Prüfungen (selektiver Anfang, alle weiteren Einträge offen)

| PR | Befund gegen Code 51 | Code-52-Entscheidung |
| --- | --- | --- |
| #663 | Release-HEAD selbst. | Kanonische Basis, keine Neuimplementierung. |
| #664 | Alle **3/3** geänderten Datei-Blobs byte-identisch in Code 51. | Nicht doppelt bauen; echter langer Fold7-TTS-Test offen. |
| #665 | **3/4** Datei-Blobs byte-identisch. Die abweichende UI-Testdatei besitzt bereits die überprüfte DATA-Phase-Assertion. | Testdatei nicht ungeprüft ersetzen; Android-UI-Lauf offen. |
| #666 | Code 51 enthält **0/8** identische Änderungsversionen. In Code 52 **4/8** identisch, weitere drei produktive Dateien durch Code-52-Integration verändert. | Work-raster, Scan-Fade und Satzgesten aus #666 auf Code-52-Linie übernommen (PR #669). Kein Fold7-Filmbeleg. |
| #659 | **12/59** geänderte Blobs identisch zu Code 51. Nachfolge-Erweiterung `AvatarSurfaceRadiance` fehlte vollständig. | Original-Gesichtsleuchten, GLB-Emissionsmetadaten, bestehender Filament-Aufrufpfad, Kotlin-Test und statischer Node-Vertrag in Code 52 übernommen: `24083a6d0794aeb553368ee1eb2a0523b99a3ec1`. Materialfehler deaktivieren nur den optionalen Effekt. Nicht alle #659-Dateidifferenzen sind damit erledigt. |
| #662 | **20/31** Blobs identisch in Code 51, einschließlich `ChiefBotTeam`, Speicher, `ContinuumBotRosterDialog`, Arbeitstitel und bestehende Navigation. | Bereits laufende UI-/Text-Modellpfade nicht neu bauen. Physischer Coding-Sandbox-Lauf, dynamische CCC1-Worker-Zuordnung und unabhängiger Patchbeleg bleiben **nicht nachgewiesen**. |
| #661 | Referenz-Messwerkzeug/Java2D-Vorschau; kein fertiger Filament-Gerätetest. | Metriken für spätere zweitseitige Grafikabnahme verwenden, nicht als visuellen PASS behaupten. |
| #669 | Code-52-P0-Eingang: native Abbruchursache, COOL-CPU-Default, Android-Akku-Broadcast, Sitzungsbeleg. | Bekannten doppelten `newChat`-View-Fold7-Crash und Akku-Regex-Fehlrouting unter `8226b1d...` korrigiert; Adverb `erneut` unter `0aa7d663...` nach isoliertem roten Test repariert. |
| #630 | Ältere Code50-Staging-Linie, Aktualisierung im 24h-Fenster. | Als Vorläufer gegen Code51 auf Konflikte und unverbundene Funktionshaken prüfen, kein pauschaler Merge. |
| #635 | ARGON-research-only GPU-/Datacenter-unabhängige Computerreuse. | Forschung dokumentieren, nicht als neue physische Rechenkapazität aktivieren. |

Übrige **jüngste** PRs noch einzeln zu vergleichen: #668, #658, #653, #651, #660, #655, #656, #657, #654, #650, #649, #646, #648, #647, #644, #614, #574, #636. #671 ist diese aktive Integrationslinie. Alle weiteren historisch offenen, geschlossenen und gemergten PRs bleiben im vollständigen Inventar.

## Nachweise aus dieser Arbeitsausführung

1. **GitHub-Quellreadback:** 10 gezielte Prüfbedingungen, 10 erfüllt. Beweist nur Verknüpfungen und Sicherheitsanker im Quelltext.
2. **Isolierte Kotlin-Reproduktion, JDK 21 / kotlinc 1.9.0:** 138 Prüfungen für `OwnerPreviousBatteryIntent` sowie monotone und begrenzte `AvatarSurfaceRadiance.gain`: **138 PASS / 0 FAIL**. Erster Lauf war wegen `nicht erneut messen` rot, danach repariert. Dieser Lauf ist **kein** `./gradlew`, kein Robolectric, kein Filament-Gerätelauf.
3. **NOT_RUN:** vollständige Node-/Gradle-/UI-/Lint-Suite für den aktuellen Code-52-SHA, Native-Filament-FPS/Thermik, Fold7-10-Turn-Dialog und Originalsignierung.

## Strikte Integrationsregel für jede künftige Entwicklung

`PR + HEAD -> Quellen-Diff gegenüber Release -> vorhandene Implementierung / exakte Duplikate erkennen -> produktive Call-Site -> benutzbare UI + Freigabe -> gekoppelte Regressionen -> Android/Native-Gerätebeweis -> Release-Entscheidung`

Ein Forschungsprototyp darf **nicht** bloß aufgrund seiner Existenz in der App aktiviert werden. Jeder Kandidat bekommt einen belegbaren Abschluss: **INTEGRATED** (mit Datei, Laufpfad, Test, SHA), **ALREADY_PRESENT** (mit Blob-Vergleich), **RESEARCH_ONLY** (mit sicherer Sperre), **SUPERSEDED** (mit Nachfolger-SHA) oder **BLOCKED** (mit konkreter Ursache). Kein unbekannter Branch wird gelöscht oder stillschweigend als fertig markiert.

Beim nächsten Durchlauf zuerst diesen Index und beide vollständigen JSONL-Snapshots lesen, aktuelle PR-/Branch-HEADs nachziehen und pro Kandidat den fehlenden Quell-/UI-/Gerätebeleg abarbeiten. Keine Signatur, keine Zweit-App und kein Main-Merge ohne Besitzerfreigabe.


## Android-Bedienung, WhatsApp und Projektgrenze (09.11.2026)

- **Vorhandener, nicht aktivierter Klickkern:** `AndroidNativeClick.kt`, `VerifiedNodeClick.kt` und `ScreenObservationBus.clickCandidate()` prüfen genaues Fenster, freigegebenen Accessibility-Dienst, eindeutiges Element, neue Postcondition und Abbruch. `clickCandidate()` ist im Quellcode ausdrücklich experimentell; **kein** `click`- oder `type_text`-Tool in `ToolRegistry`. Kein allgemeiner Gerätezugriff oder verifizierter WhatsApp-Versand.
- **Vorhandener Leseweg:** `read_app_screen` über `AndroidDeviceAppActions`/Accessibility, nur aktuell sichtbarer und freigegebener Bildschirm. Kein Zugriff auf beliebig alte WhatsApp-Chats, keine heimliche Mitlesefreigabe.
- **Sichere, sofort adressierbare Benutzerfunktion in Code 52:** `Systemstatus → WhatsApp · Nachricht entwerfen` zeigt ein überprüfbares Eingabefeld und übergibt nach ausdrücklicher Nutzeraktion über Android `ACTION_SEND`/`EXTRA_TEXT` an `com.whatsapp`. **Empfängerwahl und Senden bleiben in WhatsApp**, keine automatisch gesendete Nachricht und kein erfundener Zustellbeleg. Kein Root/Shizuku für diesen Weg. `WhatsAppDraftPolicy` prüft Textlänge/Steuerzeichen; `WhatsAppDraftPolicyTest` sowie `android-whatsapp-draft-wiring.test.mjs` sind mitgeführt.
- **Tatsächlich ausgeführter isolierter Kotlin-Test:** 11 Prüfungen / 0 Fehler, JDK21, Kotlin; getesteter Quelltext Git-Blob `6c3c1bfad6b1fc53931eb36e1cee592eb4b3a201` entspricht dem Commit im Code-52-Branch. **Android-UI, Node-Vollsuite, echtes WhatsApp-Geräteverhalten NOT_RUN.**
- **Projekttrennung:** Lumo ist kein Modul, Produkt, Datenspeicher, Agent oder App-Name von Continuum. Die historisch abgelegte, abgebrochene Testdatei `docs/chief/TEST_RESULTS-lumo-taskloop.md` darf nicht als Continuum-Feature gezählt oder integriert werden. Neuer eigener Regressionstest `.github/continuum/continuum-project-isolation.test.mjs` sperrt künftige Querverweise im produktiven Android-Quellbaum; Lauf der vollständigen Suite steht aus.
- **Offen vor realem Android-Agenten:** explizite einmalige Eingabe- und Versandfreigabe mit konkretem Ziel und Text, eigener Typisier-Pfad mit Beweis für richtigen Fokus/Empfänger/Ergebnis, kein Versand-Replay, Android-Accessibility-Lifecycle/Play-Konformität, Root/Shizuku nur optional nach Freigabe. Keine Ausweitung von ToolGate-Rechten durch gelesene Fremd-App-Texte.


## Fortsetzung 09.11.2026 – Quellreparaturen #672/#673

- **#672 auf #673 selektiv portiert:** Unbenutzten Fremd-App-Skill entfernt; `CommandInterpreter`, `chief/DeviceControl`, Backend-Dokumentation und eigene Projektgrenze bereinigt. Zusätzlich waren in `BootstrapBrain.kt`, `ContinuumThinkingMemory.kt`, `BootstrapBrainTest.kt` und im Masterprompt Restverweise: korrigiert, ohne generische `find_app`, `launch_app`, `read_app_screen`-Werkzeuge zu entfernen. Der auf #671 bisher rote isolierte Projektgrenzen-Test ist in #673 ergänzt; vollständiger Node-Lauf fehlt.
- **T23/T24 Appsuche:** Zusammengesetzte Verbote bleiben bei der reinen Suche, `Suche nicht …`/negierte Primärklauseln unterbinden jeden Toolstart, echte kombinierte Schreib-/Sendebefehle werden **nicht** als Read-Only-Tools ausgeführt. Ergebnis der Appsuche macht keine Öffnungszusage.
- **T7 Akkuvergleich:** Explizites Neumessen mit Vergleich vs. bloßer Abruf der letzten **belegten** Sitzungsobservation getrennt; negative Erstklauseln sperren die Messung. Kein Replay oder fingierter vorheriger Wert.
- **T20 Consent-Revisions-Fix:** Rein lokale, ohne Webtool erzeugte Modellantwort darf nach internem Web-Cleanup weiterlaufen, **sofern** dieser Turn keinen autorisierten Webpfad oder online-gerahmte Rechercheevidenz verwendet hat. Solche Webpfade bleiben mit Consent-Revision und aktiver Session geschützt; nach Widerruf werden Antworten mit Online-Evidenz verworfen.
- **Host-Evidenz dieser Fortsetzung:** 21/21 isolierte Kotlin-RegEx-/Entscheidungs-Mirror-Kontrollen PASS, 9/9 source-exakte GitHub-Static-Checks PASS, 31 priorisierte PR-Anschlusskontrollen ohne fehlenden Eintrag. **Kein** GitHub-Android-Workflow, Gradle-Gesamtlauf oder physischer Fold7-Test auf #673 durchgeführt.
- **Integration offen:** #673 auf Draft-Basis von #671; PR #672 bleibt als Quellhistorie sichtbar. Vor Master-Übernahme vollständige Node/JUnit/Android-UI/Lint-Gates, Rechte-/Widerrufstests und Geräteabnahme. Keine Änderung an `main`, signierter Code51, App-Paket-ID oder Originalschlüssel.
