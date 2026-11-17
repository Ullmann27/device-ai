# App-Grammatik – Review-Fortsetzung 11.11.2026

**Status: reine Kotlin-/JUnit-Prüfung GRÜN; gemeinsame Android-Abnahme offen.**

- Auftrag: [PR #769, Review discussion_r4539999051](https://github.com/Ullmann27/device-ai/pull/769#discussion_r4539999051).
- Basis: `c36258a853bbc6dacd8fbfa0dbae64f0f95a0d14`.
- Folgebranch: `work/omega-app-grammar-followup-20261111`, [Draft #778](https://github.com/Ullmann27/device-ai/pull/778).
- Integration: `/root`; Ziel `integrate/omega-apk54-20261111` / #769.
- Geprüfter lokaler Quellstand: `acf8c6390b0076b359d3f5284c2c8709b10a5a78`.
- Identischer veröffentlichter Codecheckpoint: `a69a7fe1f0f798d68c5a9e5a247c66d132dcd849`, Tree `03bcc745a6442c326d30c1278a21ea68f8a535b5`.
- Nach dem GREEN-Lauf folgen ausschließlich diese Übergabe und Prüfbelege; keine weitere Produktionsänderung.

## Ursache und Änderung

`OwnerAppLookupIntent.verbFinal` erkannte „Wir starten“, „Lass uns jetzt starten“
und „Morgen wollen wir das Projekt starten“ als App-Öffnungsauftrag. Die
Subjekt-/Modalprüfung ließ diese Plural- und Aufforderungsformen durch. Zudem
trugen verbleibende mehrdeutige Verb-am-Ende-Ziele kein `requireInstalledCandidate`.
Eine erfolglose Suche konnte deshalb eine App-Namensrückfrage aktivieren.

Der kleine Produktionspatch ändert ausschließlich zwei vorhandene Klassen:

1. `cognitive/OwnerAppLookupIntent.kt`: fehlende Formen ergänzt, Wortgrenzen
   explizit mit Unicode-Zeichenklassen definiert. Nur der tatsächlich ausgewählte
   `finalVerb`-Zweig setzt `requireInstalledCandidate = true`; direkte, höfliche,
   Wunsch- und `mach … auf`-Formen behalten ihre bisherige Priorität. Vollständige
   mehrteilige Namen bleiben erhalten.
2. `cognitive/OwnerAppClarification.kt`: dieselbe Subjekt-/Modalprüfung verwirft
   einen gewöhnlichen Satz auch während einer bereits offenen Namensrückfrage.
   Sitzungsbindung, Revisionsbindung und einmaliger Verbrauch bleiben erhalten.

Keine Änderung an `AgentActivity`, `OwnerAppActionFlow`, Werkzeugfreigabe oder
UNKNOWN-Behandlung. Der bereits vorhandene Activity-Pfad ist maßgeblich:
`sendCurrentInput` → `inspect` → `runExplicitAppLookup` → `OwnerAppActionFlow`.
Nur eine verifizierte Suche ohne exakten installierten Treffer beendet die
spekulative Suche vor einer Auswahl und führt zum vorhandenen Gesprächspfad.
Dieser Zweig liegt vor `nameOpening`/`appNameClarification`. Ein exakter
installierter Treffer erreicht weiterhin die vorhandene Werkzeugbestätigung.

## RED → GREEN

| Lauf | Fälle | PASS | FAIL | SKIP | Aussage |
|---|---:|---:|---:|---:|---|
| RED | 10 | 3 | 7 | 0 | Unveränderte Produktion; sieben Quellen hashgleich `c36258a8` |
| GREEN + App-/Freigabenachbarn | 45 | 45 | 0 | 0 | Sechs Klassen; 13 Quellhashes nach Lauf abgeglichen |
| Neue Activity-Fälle | 3 | – | – | – | NOT_RUN; gemeinsame Android-Prüfung durch Integrator |

Neue reine Klasse: `at.heinz.deviceai.cognitive.OwnerAppVerbFinalIntentTest`.
Nachbarn: `OwnerAppOpenRoutingTest`, `OwnerAppLookupIntentTest`,
`OwnerAppLookupNaturalRegressionTest`, `OwnerAppClarificationTest`,
`OwnerAppActionFlowTest`.

Geprüft sind insbesondere alle drei gemeldeten Sätze, weitere Plural-/Modalformen,
mehrteilige Appnamen, höfliche und Wunschformen, exakter installierter Treffer,
bestätigte erfolglose/unscharfe Suche, unbestätigte Suche, vorhandene Freigabe,
UNKNOWN-Nichtwiederholung und Verbrauch einer älteren Namensrückfrage. Die
bestehende Routing-Assertion verlangt für die vier nackten Verb-am-Ende-Formen nun
explizit Installationsevidenz; ihre Ziel-/Öffnungsassertionen bleiben erhalten.

Die reale Activity ist durch drei neue Testquellen abgedeckt, hier noch ungetestet:

- `OwnerAppPlanningContextUiTest.pluralStatementsReachConversationWithoutAppLookup`
- `OwnerAppPlanningContextUiTest.unmatchedVerbFinalReturnsToConversationWithoutArmingNameReply`
- `OwnerAppActionWorkSceneUiTest.installedVerbFinalNameUsesExistingConsentBeforeLaunch`

## Quellbelege und Reproduktion

`docs/evidence/omega-app-grammar-20261111/` enthält RED/GREEN-Rohlogs,
Compilerprotokolle, jeweils `inputs.json`, `summary.json` und den tatsächlich
verwendeten schmalen Host-Runner `run_app_grammar.py`. Der Runner enthält die
konkreten lokalen Cache-/Workspace-Pfade dieses Laufs; seine Abhängigkeiten stammen
aus vorhandenen kompilierten Klassen, kein vollständiger Android-Build.

Ausgeführtes GREEN-Kommando im damaligen Workspace:

```sh
flock /tmp/continuum-omega-gradle.lock python3 /workspace/scratch/2d62a799e417/logic-qa/run_app_grammar.py app-verb-green at.heinz.deviceai.cognitive.OwnerAppVerbFinalIntentTest at.heinz.deviceai.cognitive.OwnerAppOpenRoutingTest at.heinz.deviceai.cognitive.OwnerAppLookupIntentTest at.heinz.deviceai.cognitive.OwnerAppLookupNaturalRegressionTest at.heinz.deviceai.cognitive.OwnerAppClarificationTest at.heinz.deviceai.cognitive.OwnerAppActionFlowTest
```

RED nutzte denselben Runner mit `app-verb-red` und nur der neuen Klasse. Beide
Läufe hielten den gemeinsamen Lock; kein eigener Gradle- oder Node-Lauf.

Produktions-SHA256:

- `OwnerAppLookupIntent.kt`: `bdf6ed4af869f3d2a20906de6d6ce10d48bba403274cd062cd5ac9bde96688eb`
- `OwnerAppClarification.kt`: `c5398ae4d0392b96ddc635f3fecc19281c6bbadc767add0eccb7a63efc4b8603`

`/root/extensions` bestätigte den angewandten Produktionsdiff und dessen Bindung
an dieselben GREEN-Quellhashes unabhängig lesend: kein weiterer Befund, keine
eigenen Testläufe. Die Activity-Produktionsdatei blieb unverändert.

## Integrationsgrenzen und nächster Schritt

PORTIERT: enger Patch und Regressionen auf #778. VERIFIZIERT: 45 reine Hostfälle.
GERÄTEBESTÄTIGT: NOT_RUN. Gemeinsame Android-/UI-Kompilierung und Ausführung der
neuen Activity-Fälle bleiben Aufgabe des Integrators auf dem kombinierten SHA.
Kein Main-Merge, keine Signierung, keine Installation, keine APK-Abnahme.

Rückfall: ausschließlich diesen isolierten Patch zusammenhängend zurücknehmen;
keine ToolGate-, Freigabe-, UNKNOWN- oder Testgrenze lockern. Gesamtmatrix und
Integrationsregister werden von `/root` fortgeschrieben.
