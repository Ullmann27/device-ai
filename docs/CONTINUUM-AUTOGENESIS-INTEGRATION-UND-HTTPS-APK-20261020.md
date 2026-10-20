# Device AI Continuum – Integrationsaudit und APK-Freigabe, 20.10.2026

**Repository:** Ullmann27/device-ai (privat)  
**Geschützter Entwicklungsbranch:** `astra/continuum-experiment-charter-20261019`  
**Pull Request:** Draft #42; Basis `astra/render-mentor-apk-builder-20261019` (PR #40).  
**Main:** unangetastet.

## 1. Gesicherter Pull-Request-Befund

Offene PRs 37→38→39→40→42 sind historisch aufeinander aufgebaut:
- #37: integrierter Android-Stand aus vorherigen SOL- und Astra-Arbeiten
- #38: Samsung-Recherche-Hotfix
- #39: optionaler externer Mentor
- #40: isolierter Render-Build und Besitzergeräte-Kopplung
- #42: Android/Termux-Builder, Quelle/Gedächtnis/CDX1, aktueller Entwicklungsstand

Es liegt **kein zusätzlicher neuerer PR nach #42** vor, den wir blind
mischen müssten. Andere historische offene PRs sind nicht automatisch
merge-fähig; ihre einschlägigen Verbindungen sind einzeln zu prüfen.

## 2. Bereits verdrahtet, nicht doppelt implementieren

- Qwen3: `LocalQwenPromptPolicy` ergänzt `/no_think` bereits bei
  direkten Qwen3-Nachrichten. LiteRT-LM-Chat-Template, native Cancel-
  Ursache und Gerätegeschwindigkeit sind damit noch nicht nachgewiesen.
- SWB1: 4.113 Parameter, gesicherte Gewichte, retrospektive Holdouts,
  bestätigte Owner-Erinnerungen mit Revocation.
- CDX1: 576 synthetische Trainingspaare, zwölf Prüfstufen, separater
  Checkpoint; keine Freigabe des generativen Decoders für Live-Chat.
- CSX1: strikt regelbasierter Originalspan-Benchmark, keine universelle
  Sprachgenerierung und keine automatisch übernommene Wahrheit.
- Internetforschung: freigegebener, ressourcenbegrenzter
  JobScheduler mit dynamischer MediaWiki-Themenwarteschlange.
- Antwort-/Quellenarchiv: persistente und quellengebundene Speicherung
  mit Export; noch kein vollwertiger SQLite-FTS/Vektorindex.

## 3. In dieser Integrationsrunde konkret geändert

1. **AUTOGENESIS Orthografie (vorsichtig):**
   `ContinuumOrthographyProbe.kt` schlägt bei einem eindeutigen
   Schreibfehler aus dem bereits zugelassenen lokalen Gedächtnis
   genau eine alternative *Abrufanfrage* vor. Das Original wird
   gespeichert und angezeigt; weder Intent-/Owner-Fakten noch
   Tool-Aufträge werden verändert. Keine sentimentale Bewertung
   aus Tippfehlern, kein automatischer Lebenslauf, keine neue
   dauerhaft gültige Direktive.
2. **Abruf verkabelt:**
   `ContinuumMemoryDialogue.reply` versucht die Schreibvariante
   nur dann, wenn zuvor keine passenden bestätigten Belege gefunden
   wurden, und nicht bei expliziten Mutations-/Installationsbefehlen.
   Die normale SWB1- und CRE1-Abfrage bleibt erhalten.
3. **Öffentliches Archiv genauer gezählt:**
   `ContinuumPublicStudyJobService` meldet eine neue Quelle nur
   anhand eines tatsächlich neu gespeicherten Quellenarchiv-Eintrags,
   unabhängig davon, ob im Mentor-Notizbuch ein zusätzlicher
   Eintrag entsteht.
4. **Qwen-Diagnose:**
   `Continuum Modelltest` prüft weiterhin eine isolierte
   Thinking-Inferenz; `Continuum Modelltest Direkt` prüft neu
   den normalen LiteRT-LM-Textpfad. Es wird kein Tool ausgeführt.
   Bei einem passenden geladenen Qwen3-Modell verwendet dieser
   Pfad die vorhandene `/no_think`-Soft-Switch-Policy.
5. **Regressionen erweitert:**
   `ContinuumOrthographyProbeTest.kt`,
   `.github/continuum/autogenesis-safe-wiring.test.mjs` und
   die angepasste `local-model-smoke.test.mjs`.
6. **Preview-Versionierung:**
   Nur Debug-Preview wird auf VersionCode 5, Mentor-Preview
   auf VersionCode 7 angehoben. Die Standard-App bleibt auf
   VersionCode 4. Keine Main-Veränderung.
7. **HTTPS-Build vorbereitet:**
   `scripts/render-mentor-preview.sh` im Experimentbranch ist
   jetzt streng für diesen Branch bestimmt und prüft zusätzlich
   AUTOGENESIS-/CDX1-/Quellen-Regressionen vor einer signierten APK.
   Der bestehende Render-Builddienst besitzt bereits eine
   persistente Mentor-Preview-Signatur und Signatur-Fingerprint-Gate.

## 4. Messstatus – gesichert vs. offen

- **Tatsächlich ausgeführt:** Isolierte Kotlin-Kompilation und
  Laufzeitprobe der Orthografielogik im vorhandenen Tool-Container:
  korrekte konservative Anpassung von drei Tippfehlern, technische
  Modellkennungen blieben unverändert.
- **Quelltextvergleich:** 18 von 18 neuen Node-Regex-Invarianten
  stimmten mit den GitHub-Dateiinhalten überein.
- **NICHT ausgeführt:** gesamte eingecheckte Node-Suite,
  Android JUnit/Gradle/Lint, neue APK, Samsung-Installation,
  LiteRT-LM-Laufzeitvergleich. Ein Quelltextvergleich ist kein Build.
- **GitHub Actions:** Auf PR42 aktuell keine Runs. Die bestehenden
  Hosted-Runner waren zuletzt durch Kontolimits eingeschränkt.
- **Render:** Service `device-ai-mentor-apk-preview` hat
  bisher ältere, abgeschlossene Builds vom Branch
  `astra/render-mentor-apk-builder-20261019`. **Sein öffentlicher
  APK-Link ist noch keine aktuelle PR42-APK.**
- **Lokale Containerumgebung:** Java und Kotlin vorhanden,
  aber keine vollständige Android-SDK/Gradle-Toolchain für einen
  nachgewiesenen APK-Build.

## 5. Direktes HTTPS statt lokaler Termux-Adresse

Bestehender Render-Service:
`https://device-ai-mentor-apk-preview.onrender.com`

Nach einer bestätigten Render-Branch-Umstellung auf
`astra/continuum-experiment-charter-20261019` und einem
erfolgreichen Build mit geprüftem Commit/Signer wird erwartet:

`https://device-ai-mentor-apk-preview.onrender.com/DeviceAI-Continuum-Groq-Mentor-Preview.apk`

**Diesen URL nicht vor erfolgreicher Live-Build-Abnahme als neuen
Download ausgeben!** Der aktuelle URL liefert sonst nur eine ältere
Ausgabe. Der Render-Connector kann Deploys auslösen, aber die
vorhandene Service-Branch-Konfiguration nicht ändern. Der Wechsel
muss in der Render-Service-Konfiguration erfolgen. Kein
automatisches Umpointen historischer PR40-Branches, keine
Git-Schreiboperation in Main.

Auch nach dem Render-Build bleibt die Android-App-Variante
`at.heinz.deviceai.mentorpaired` von `at.heinz.deviceai.preview`
getrennt: kein stillschweigendes Übertragen privater App-Daten,
kein automatischer Installer und kein Versuch, abweichende
Signaturen zu überschreiben.

## 6. Sichere nächste Abnahme

1. Vorhandene SWB1-Gewichte und Wissensarchiv-Backups unverändert
   aufbewahren.
2. Service-Branch auf PR42 korrigieren, nur nach verifiziertem
   bisherigem Render-Signer und Berechtigung; nach Deployment
   Status, SHA und Signaturbericht prüfen.
3. Nur nach grünem TestDebugUnitTest, lintDebug, assembleDebug
   und APK-Signaturprüfung den HTTPS-Download öffnen.
4. In der **Mentor-Pair-Variante**, nicht der bisherigen Preview:
   Lernstand, CSX1-Vergleich, direkte/Thinking-Qwen-Inferenz und
   Internetquellenzähler kontrollieren.
5. Nie App-Daten löschen, nie ungeprüfte SWB1/CDX1-Kandidaten in
   den produktiven Chat übernehmen.
