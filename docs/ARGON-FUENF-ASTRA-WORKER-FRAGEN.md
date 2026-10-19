# Fünf experimentelle Kernfragen an Gemini Argon 4 – Astra/Continuum

**Repository:** `Ullmann27/device-ai`. **Kein GitHub-Zugriff bei Argon.** Es geht ausschließlich um Device AI Continuum, nicht um Lumo oder ein anderes Projekt.

**Beobachteter Stand (19.10.2026):** Der Groq-Textmentor funktioniert auf einem Samsung; Android-Vorschau über Render testbar. Unser Teamleiter verwendet `.github/continuum/autonomous-teamleader.mjs`, `local-teamleader-loop.mjs`, `repair-loop.mjs`, `m4-local-gate.mjs`, `development-backlog.mjs`. Neuer Astra-Vertical-Slice auf einem isolierten Branch: `continuum-workshop.mjs`, `worker-academy.mjs`, `worker-atlas.mjs`, Android `ContinuumWorkerChatRouter.kt`. Die Academy hat sieben deterministisch geprüfte Übungsbereiche; Atlas speichert SHA-verknüpfte, unverifizierte bibliografische Hinweise aus den öffentlichen Wikipedia-/OpenAlex-/Crossref-APIs. Bisher besitzt Android kein geladenes LiteRT-LM-Sprachmodell und keine Host-Jobübergabe. Kein ungeprüftes `main`-Merge, keine unbestätigten Lernwahrheiten.

## 1. Universeller Entwickler ohne universelles Compiler-Abonnement
Wie implementierst du eine **extensible Capability-/Toolchain-Rezept-Architektur**, die Android Gradle, Node, Flutter, Godot und mit geeignetem macOS/Xcode auch Swift/iOS tatsächlich bauen kann? Entwirf ein versioniertes, signierbares Job-/Toolchain-Manifest, feste Befehlsprofile, unabhängige Beweisverifikation und Paket-/Signaturprüfung. Ein fehlendes SDK muss als `UNAVAILABLE` statt `SUCCESS` erscheinen. Zeige die konkreten Schnittstellen, die das bestehende M4-Gate wiederverwenden und den bestehenden Reparaturmechanismus nicht duplizieren.

## 2. „Virtuelles Weltgedächtnis“ ohne massiven Datendump
Wie errichtest du über den vorhandenen `worker-atlas.mjs` und `public-research.js` hinaus ein **Content-Addressable-Provenance-Graph-Memory**, das über Quellenreferenzen, extraktive Evidence-Snippets, aktualisierbare Merkle-Teilgraphen, Herkunft, Nutzungsbedingungen und Dokumentversionen skaliert, ohne ungeprüfte Internetdaten ins bestätigte Gedächtnis zu übernehmen? Benötigt wird ein kleiner, effizienter Hot-Index für Android und ein optionaler persistenter PC-Cache. Gib deterministische Rekonstruierbarkeit, Audit- und Tamper-Tests an.

## 3. Selbstverbesserung ohne Cloud-LLM als zwingende Voraussetzung
Welche belastbare Kombination aus **begrenzter enumerativer Programmsynthese, CEGIS, statischer Analyse, testgetriebener Reparatur, retrieval-gestützter Code-Synthese mit optionalem lokalem GGUF-Coder** ermöglicht reale Kotlin-/Node-Patches auf einem eingeschalteten PC? Zeige, wie Trainingsdatensatz, Modellgewichte, erfolgreiche Reparaturstrategie und verifizierte Testevidenz getrennt bleiben, und wie unbekannter Code eine OS-Sandbox nicht verlassen darf. Welche Variante lässt sich ohne großes generatives Modell jetzt bereits nachweisbar bauen?

## 4. Lernfortschritt ohne Überanpassung an eigene Tests
Die `worker-academy.mjs` bewahrt pro Übung Test-/Quelldatei-SHA-256 und Git-HEAD auf. Wie erweitern wir dies zu einem **unabhängigen, versionierten Kompetenznachweis** mit Holdout-Challenges, Mutations-/Metamorphic-Tests, neuartigen Programmen, strukturierten Fehlerfällen, Anti-Datenleckage und geerbten Regressionen? Bitte formuliere Akzeptanzschwellen, die nicht mit bloß grünem Unit-Test verwechselt werden können. Schlage vollständige adversariale Testfälle vor.

## 5. Android-Baumeister ↔ autonomer Host-Worker
Wie implementierst du einen **dauerhaften, signierten und eng berechtigten bidirektionalen Job-Kanal**, damit Nutzer über die Android-App `Worker: Erstelle ...` eine Entwicklungsaufgabe absenden, Status abfragen und am Ende **einen geprüften, signierten Zwillings-APK-Kandidaten** vorfinden können? Verwende die vorhandene `DeviceDevelopmentBacklog`-/Node-`BacklogStore`-Operation-ID-Semantik. Schreibe ein exakt definiertes ACK/RETRY/LEASE/EXPIRE-Protokoll mit Idempotenz, Geräte-Keypair, Benutzerfreigabe, eingeschränkter Scope, Serverrestart-Recovery und Offline-Fallback. Der Host darf nicht in `main` schreiben und keine APK ohne Einwilligung installieren.

## Antwortvertrag

Beantworte zuerst **(a)** Welche Teile sind bereits konkret ausführbar, **(b)** welche SDKs, Originaldateien oder Lizenzen fehlen, **(c)** wie lautet die kleinste sichere Nachweiskette? Danach gib **für die drei höchsten Hebel** konkrete, auf vorhandene Schnittstellen abgestimmte Code-Patches und vollständig ausführbare Tests. Gib keine fiktiven APK-Erfolge oder APIs an. Trenne ursprüngliche Spekulationen von nachgewiesenen Eigenschaften. Keine zweite Backlogverwaltung; keine unerlaubten Datenquellen. Der Schwerpunkt ist ein ambitioniertes, aber falsifizierbares Experiment – nicht ein weiterer fiktiver Vortrag.
