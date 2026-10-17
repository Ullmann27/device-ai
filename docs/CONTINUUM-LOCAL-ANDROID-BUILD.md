# Continuum: lokales Android-M4-Gate ohne GitHub Actions

Stand: 17. Oktober 2026. Zweck: Ein reproduzierbarer, fehlerschließender Build-Nachweis aus einem **vollständigen lokalen Git-Checkout**, auch wenn die GitHub-hosted Runner nicht verfügbar sind.

## Derzeitiger GitHub-Actions-Blocker

Die GitHub-Weboberfläche für Workflow-Run [39009473864](https://github.com/Ullmann27/device-ai/actions/runs/39009473864) zeigt: "The job was not started because recent account payments have failed or your spending limit needs to ...". Build und Repair haben keine Steps ausgeführt. Dies ist ein **Account-/Abrechnungs- oder Budgetblocker**, kein Beweis eines Compilerfehlers. Ob eine fehlgeschlagene Zahlung oder ein Limit konkret ausschlaggebend ist, bleibt anhand der abgeschnittenen Nachricht offen. Die Warnung zur Ubuntu-Image-Migration ist nicht ursächlich.

Nur der Kontoinhaber kann unter [GitHub Billing](https://github.com/settings/billing) den genauen Befund prüfen und ggf. beheben. Budget nicht ohne bewusste Kostenentscheidung erhöhen. Neue Workflow-Runs lösen diesen Blocker nicht; keine Änderung an YAML oder Android-Code als vermeintliche Reparatur dafür durchführen.

## Voraussetzungen für einen lokalen Prüflauf

Ein unveränderter, vollständiger Checkout des zu prüfenden Commits, Git, Bash, Node.js 20/22, JDK 17, Gradle **8.11.1**, Android SDK Platform **36** und Android Build-Tools **35.0.0**. Dazu alle von Gradle benötigten Abhängigkeiten. Der erste lokale Build kann Netzwerkzugriff für Maven-Artefakte benötigen; "ohne GitHub Actions" ist **nicht automatisch** "ohne Internet".

Kontrollieren:

```bash
java -version
gradle --version
node --version
git status --short
git rev-parse HEAD
```

Die lokale Umgebung muss `ANDROID_HOME` oder `ANDROID_SDK_ROOT` auf das vollständig installierte SDK setzen. `JAVA_HOME` muss, sofern gesetzt, auf JDK 17 zeigen.

## Ausführung

Aus dem Root des Device-AI-Checkouts:

```bash
EXPECTED_SHA="$(git rev-parse HEAD)"
bash scripts/local-m4-android.sh "$EXPECTED_SHA"
```

Für eine externe Übergabe sollte die erwartete SHA **vorab aus dem freizugebenden GitHub-Commit** übernommen und nicht blind aus einem beliebigen lokalen HEAD gelesen werden. Das Skript verweigert unsaubere Checkouts, falsche Versionen und fehlende SDK-Dateien. Es ruft `.github/continuum/m4-local-gate.mjs --android` auf und führt nach Erfolg zusätzlich dessen unabhängige `--verify`-Prüfung gegen den erzeugten Bericht aus.

Der M4-Lauf nutzt einen separaten Git-Worktree und schreibt Nachweise standardmäßig unter `${XDG_STATE_HOME:-$HOME/.local/state}/device-ai-continuum/proofs`. `gate-report.json` und die APK werden durch die bestehende Gate-Implementierung verknüpft. Das SHA-256-Feld des Gate-Berichts ist ein Integritätscheck, keine digitale Signatur.

## Welche Abnahme dadurch **nicht** ersetzt wird

Das aktuelle M4-Gate führt seine vorgesehenen Node-/Backend-/Kotlin-Profile und einen Debug-APK-Build aus. **Eine erfolgreiche M4-Prüfung allein beweist noch nicht:** Android-Lint `gradle :app:lintDebug`, vollständige Kotlin/Node-Paritätsrunden mit ihren Pflicht-Umgebungsvariablen, einen signierten Release-Build, die korrekte Integration des LiteRT-LM-Modells oder die Ausführung auf einem realen Samsung-Gerät.

Diese Prüfungen sind gesonderte, weiterhin verbindliche Abnahmebedingungen. Kein Merge in main/production, keine Behauptung "APK auf Gerät erfolgreich", solange die echten Protokolle fehlen.

## Prüfnachweis für den Startmechanismus

```bash
node --test .github/continuum/local-m4-android-gate.test.mjs
```

Die Tests verwenden ausdrücklich simulierte Java-, Gradle-, Node- und SDK-Dateien. Sie testen die **Fail-Closed-Vorbedingungen des Startskripts**, nicht das Vorhandensein einer tatsächlich gebauten APK.
