# Device AI Continuum – M6 Modelladapter: Umgebungs- und Trace-Sicherheit

**Datum:** 25. Oktober 2026  
**Repository:** `Ullmann27/device-ai`  
**Integrationsbranch:** `integration/continuum-m6-all-verified-20261025`  
**Quellstand bei Prüfung:** `b168948248cb30e404f13e1fc4fada6fc970dbc2`  
**Status:** Review / Draft, kein Android-Besitzerrelease

## Anlass und belegter Befund

Der bisherige lokale Coding-Provider reichte mit `{...env}` sämtliche Umgebungsvariablen an die Node-CLI weiter. Dies schloss bei entsprechender Elternkonfiguration auch fremde Tokens, Proxy-Einstellungen und Node-Startoptionen ein. Zudem speicherte der bisherige Request-Trace den gesamten `userPrompt` (mit Quellkontext) und die rohe Modellantwort in einer Datei des Zustandsverzeichnisses. Eine spätere Verwechslung von lokaler, vorübergehender Verarbeitung und dauerhaftem Auditmaterial war möglich.

## Tatsächlich implementiert

- `.github/continuum/coding-provider.mjs` erstellt die Kindprozess-Umgebung ausschließlich mit `CONTINUUM_LOCAL_BRAIN_API_KEY`, `CONTINUUM_LOCAL_BRAIN_MODEL` und den explizit neutralisierten Node-Schaltern `NODE_OPTIONS`, `NODE_V8_COVERAGE`, `NODE_USE_ENV_PROXY`. Sonstige Elternvariablen werden nicht übernommen.
- Der Modellname bleibt ausdrücklich **frei wählbar** als nicht ausführbarer JSON-Datenwert; die bereits eingesetzten Qwen-/llama.cpp-Bezeichnungen werden nicht auf eine feste Liste eingeschränkt. Nichtleere einzeilige Werte bis 128 Zeichen sind zulässig; ASCII-Steuerzeichen einschließlich CR und LF sind ausgeschlossen.
- Das neue persistente Request-Trace enthält `promptSha256`, `contextSha256`, `responseSha256`, `responseBytes`, Modellkennung, Quellenbindung, Laufzeit und Exit-Status, jedoch weder vollständigen Prompt/Quelltext noch die rohe Modellantwort. `rawContentStored:false` dokumentiert das bewusst.
- Zusätzliche Node-Regressionstests prüfen ausgeschlossene Tokens/Proxy- und Node-Optionen, ausbleibende Rohtextpersistenz und ungültige Modellnamen einschließlich echter Zeilenumbrüche.

## Unabhängig ausgeführter fokussierter Funktionstest

Unter Node v22.16.0 wurde eine isolierte Testumgebung mit den aus dem Repository kopierten beiden relevanten Codeabschnitten ausgeführt. Die Quellidentität des Umgebungsabschnitts wurde durch Länge **547 Zeichen** und FNV32 **`c253fd25`** gegenüber dem zurückgelesenen GitHub-Quelltext abgeglichen. Der Traceabschnitt wurde ebenfalls in dieser lokalen Testumgebung ausgeführt. **6/6 fokussierte Tests bestanden:** Quellvergleich, übliche Modellnamen, Null-/Standardmodell, Variablen-Allowlist, ungültige Modelle und Redigierung des Trace-Inhalts. Der Testlauf deckt weder Imports des vollständigen Providers noch den Qwen-/llama.cpp-Server ab.

## Bewusste Grenzen und nächste Prüfschritte

- Diese Änderung beseitigt **nicht rückwirkend** ältere im Git-Verlauf oder außerhalb des Repositories gespeicherte Rohtraces; solche Altbestände sind separat und rechtmäßig auf Zugriffsrechte und Aufbewahrung zu prüfen.
- Der Modellprompt wird weiterhin vorübergehend zur lokalen Inferenz benötigt; die Änderung minimiert ausschließlich die dauerhafte Speicherung und unnötige Prozessumgebung.
- Die neu im Repository hinterlegten Testfälle wurden wegen weiter fehlender GitHub-Actions-Runner **noch nicht als vollständiger Repository-Test ausgeführt**. Auch Gradle/Kotlin, Backend, Docker-Isolation und ein physischer Fold7-Test sind weiterhin offen.
- Für Android/Termux sind der tatsächliche Start der CLI mit minimierter Umgebung und die Kompatibilität der lokal installierten Node-Binary noch zu bestätigen.
- Keine Änderungen an `main`, Besitzerzertifikat, APK, Versionskennung, Modellgewichten oder produktiven Erinnerungen; PR #132 bleibt Draft.
