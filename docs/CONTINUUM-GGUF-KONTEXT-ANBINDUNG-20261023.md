# Wiederherstellung des isolierten GGUF- und Kontextpfads – 23.10.2026

## Grundlage und Ziel
Dies ist **keine APK-/Releasefreigabe**. Eigener, überprüfbarer Integrations-Draft auf der signierten Quellbasis `9fb4361c896315e9e974dd5d8feab5ce1ce65803` (Code17, 0.3.16). Sols Audit auf PR #91 / `c60addce` hat festgestellt, dass die ältere GGUF-/lokale Kontextkette von Commit `3f09c5427b59d8a944b7ceaacc3cb5d0d77837a9` auf dieser Basis und in Opus' damaligem PR #92 (`524c1afc`) fehlte.

Hier werden **nur fünf zuvor bestehende Dateien byteidentisch** als neue, separat nutzbare Skripte eingeführt. Die Blob-SHAs stammen aus dem genannten historischen Commit. Keine bestehende Datei wird überschrieben. Insbesondere:
- `scripts/continuum-local-brain.mjs` wird **nicht** aus dem alten Commit zurückgesetzt. Die signierte Basis enthält bereits die aktuellere OpenAI-/llama.cpp-konforme verschachtelte `response_format.json_schema`-Hülle und zusätzliche HTTP-/Sicherheitsprüfungen.
- Keine Veränderungen an Opus' PR #92, Astras PR #91, `main`, Android-Manifest, `app/build.gradle.kts`, Signatur, Modellgewichten, Forschungsdaten, Task-/Repair-Gates oder bestehender App-UI.
- `continuum-gguf-smoke.mjs` benötigt einen ausdrücklich manuell gestarteten, authentifizierten lokalen llama.cpp-Server und führt **nicht** selbst Server, Modell-Downloads oder Patches aus. Es findet keine automatische Promotion, keine neue Besitzereinwilligung und keine reale Samsung-Inferenz statt.

## Dateibelege
- `scripts/continuum-gguf.mjs` ← Blob `bc2ff6ba56fb2799610078dabc3dec4d38ee86dc` aus `3f09c542`
- `scripts/continuum-gguf-smoke.mjs` ← Blob `db6d69612db765e4452522af4d5b926cead10b76` aus `3f09c542`
- `scripts/continuum-gguf.test.mjs` ← Blob `64edfef2d254ad5430b9e48fc14395f2c3389d05` aus `3f09c542`
- `scripts/continuum-local-context.mjs` ← Blob `b177b4c06b19a724a4f425f347a68dd2c38b1a07` aus `3f09c542`
- `scripts/continuum-local-context.test.mjs` ← Blob `146203410877fed9525753fc65a51d506930af5b` aus `3f09c542`

## Bereits belegte Ergebnisse und ausstehende Tests
Der historische PR #53 dokumentiert GGUF-/Linux-x86-Prüfungen. Diese sind **keine Bestätigung für diesen neuen Kombinationsstand**. Das hier verwendete aktuelle `continuum-local-brain.mjs` ist bewusst neuer als die historische Vorlage. Deshalb sind sämtliche folgenden Prüfungen für diesen Branch **noch auszuführen und zu protokollieren**:

```sh
node --check scripts/continuum-gguf.mjs
node --check scripts/continuum-gguf-smoke.mjs
node --check scripts/continuum-local-context.mjs
node --test scripts/continuum-gguf.test.mjs scripts/continuum-local-context.test.mjs scripts/continuum-local-brain.test.mjs
```

Danach kontrollierte Runtime-/Kontext-Integration mit Opus' **eingefrorenem** aktuellstem Stand, eingeschränkte lokale OS-Sandbox einschließlich Importauflösung/Netzwerkschutz, UI- und Kotlin-Tests am kombinierten Baum, Android-Lint und echte Geräteabnahme. Bei nicht bestandenem Test: keinen Merge, keine APK. Die oben aufgeführten Skripte sind für sich alleine **kein** neuer Android-Chat- oder vollständiger Coding-Agent-Pfad.

## Sicherheitsregeln
Kein automatischer Merge. Kein Force-Push. Niemals `main` oder den Signierzweig überschreiben. Kein Modell als vertrauenswürdig markieren: eine passende GGUF-SHA ist nur Byte-Identität, kein Authentizitäts- oder Sicherheitsbeweis. Kein direkter unbegrenzter Zugriff auf Besitzerdateien oder Netzwerk. Vor produktiver Ausführung sind die Beschränkungen und der eindeutige Modell-/Server-Abgleich nachzuweisen.

Rollback: ausschließlich diesen eigenständigen Branch bzw. die fünf hinzugefügten Skripte verwerfen; keine Datenmigration oder Checkpointänderung.
