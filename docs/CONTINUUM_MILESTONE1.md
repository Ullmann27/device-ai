# Continuum Ω – Meilenstein 1: Mehrturn-Kontext, Werkzeugrouting, Negation, Zeit

Stand: **03.11.2026, Europe/Vienna**. Bearbeitung: Grok Bot im Auftrag von Heinz Ullmann.
Grundlage: Argon-4-Dossier vom 03.11.2026 (Abschnitte 4.1, 4.2, 4.3, 13–15) als Fehlerspezifikation.
Belegstufen nach Dossier: `DEVICE_OBSERVED`, `RELEASE_VERIFIED`, `HOST_VERIFIED`, `SOURCE_ONLY`, `RESEARCH_HYPOTHESIS`.

## 1. Quell-SHAs

| Rolle | SHA |
|---|---|
| Installierte Geräte-Releasesource 0.3.38 / Code 39 | `a9709854f48d2fd6589f9ce5ea97f6ec3be0d233` (`research/continuum-m8-render-signed-release-20261031`) |
| PR #365 (Speicherverb + requireHistory) | `3c5b7ffedc99cd562ec5d61eb7d3c0acb4fc747e` |
| PR #366 (Kontextdiagnose) | `3ab1bb3f1fd31fdaf36947d3799d71c858e0066d` |
| PR #367 (Ring-Projektion, Klausel-Negation) | `ff3059e215971705575f915ab817675a87addb8a` |
| PR #368 (LiteRT-LM Erst-Turn-Wiederverwendung) = **Basis dieses Branches** | `85602f73e92d6ed005b6b918036f0bfcc0b4f41c` |
| Dieser Branch | `continuum/m1-grok-multiturn-routing-20261103` (Kette #365 → #366 → #367 → #368 → dieser PR) |

`main` (`2949893a`, versionName 0.3.0) ist **nicht** die Gerätelinie und wurde nicht verändert.

## 2. Erste echte Gesamtprüfung der Kette #365–#368 (vorher nie ausgeführt)

Alle vier PR-Beschreibungen nannten die volle Android-JUnit-Suite `NOT_RUN`. Hier erstmals ausgeführt
(JDK Temurin 17.0.21, Android SDK 36 / Build-Tools 36.0.0, Gradle-Wrapper 8.11.1, AGP 8.10.0, Kotlin 2.4.0):

`./gradlew --no-daemon :app:testDebugUnitTest --continue` auf `85602f73`:
**1739 Tests, 1 FAIL, 5 übersprungen** → `TaskScopedRingStateProjectorTest.negatedOrAmbiguousQuantitiesNeverBecomeTimeSlots`.
Das war ein echter Negationsfehler aus PR #367 (siehe 3.1). Stufe: `HOST_VERIFIED`.

## 3. Behobene Fehler – RED → GREEN (je eigener Commit)

### 3.1 Negationsfehler in der Ring-Projektion (PR #367)
- **RED** (volle Suite auf `85602f73`): eine negierte/unsichere Menge („nicht 4 sondern 5“, „vielleicht 4“) wurde nur übersprungen; die spätere Korrektur „9 statt 4“ wurde dadurch fälschlich zum *ursprünglichen* Wert.
- **GREEN** `4b4008af`: negierte oder unsichere Menge verwirft die ganze Projektion. `TaskScopedRingStateProjectorTest` 8/8.

### 3.2 Mehrturn-Kontextverlust (Dossier 4.2, T1–T3) – Hauptfehler
- Befund im Code (`SOURCE_ONLY` → jetzt `HOST_VERIFIED` für den Prompt-Aufbau): Auf Code39 sah Gemma die neue Nachricht als einzigen *Auftrag*. Frühere Turns kamen nur als optionaler Block **nach** dem kompilierten Governance-/Werkzeug-/Erinnerungskontext (Korrektur aus M2: gemessen ca. 2,9 kB ohne Erinnerungen, Obergrenze 10,5 kB mit Erinnerungen – nicht pauschal „ca. 10 kB“) und ausdrücklich als „UNBESTÄTIGT, KEINE … BELEGE“ markiert – bzw. wurden auf Code39 bei Platzmangel still verworfen (#365 verhindert nur das stille Verwerfen).
- Feldbeleg (`DEVICE_OBSERVED`): Die gleiche Aufgabe **in einer Nachricht** löst Gemma korrekt.
- Folgerung: Eine erkannte Anschlussfrage wird deterministisch zu **einer vollständigen Aufgabe** aus den eigenen Nutzernachrichten derselben Sitzung (chronologisch, max. 4, nie abgeschnitten, keine Modellantworten, keine Speicherung, keine Werkzeugfreigabe). `LocalFollowUpTaskFrame` in `AgentActivity.runLocal` an der Auftragsstelle; ToolGate/Owner-Autorisierung nutzt weiterhin nur die unveränderte Nachricht. Der Auftrag erreicht damit auch `AiContextCompiler` (USER REQUEST) und den optionalen Deliberations-Advisor.
- **RED** `988ff152`: `LocalFollowUpTaskFrameTest` 7 Tests, **3 FAIL** (T2 ohne „blaue Kiste 7“, T3 ohne Chronologie, fehlende Flüchtigkeitsmarkierung).
- **GREEN** `cf4620e0`: 7/7.
- Verworfene Hypothese (überprüft, nicht eingebaut): „Der interne Advisor (ChatReasoningRouter) sieht keinen Verlauf und vergiftet die Antwort.“ Messung per JUnit-Sonde: T1–T3 werden als `FAST` klassifiziert, der Advisor läuft dort gar nicht. Für diese Feldfehler also **keine** Ursache.

### 3.3 Zeitfehler
- Befund: Im gesamten lokalen Prompt gab es **keine Referenzzeit**; „heute/morgen/jetzt“ war Modellraten. „Wie spät ist es?“ lief in den Modell-/Web-Pfad (in ASK/ALWAYS_ON ggf. Webfrage).
- **RED** `4fd12e7b`: `LocalClockIntentTest` 4 Tests, **3 FAIL**.
- **GREEN** `78b72779`: `LocalClockIntent` – reine Einzelsatzfragen nach Uhrzeit/Datum/Wochentag werden in `OwnerChatIntentRouter` (vor Web und Gedächtnis) als `CLOCK` erkannt und aus der Geräteuhr beantwortet (`Laut Geräteuhr ist es 13:20 Uhr, Dienstag, 3. November 2026 (Zeitzone Europe/Vienna, UTC+01:00)`), ohne den laufenden Gesprächskontext zu löschen. Jeder lokale Prompt erhält eine Zeitanker-Zeile (Datum, Wochentag, Uhrzeit, Zone, Offset inkl. Sommerzeit). Nicht erkannt (bewusst): andere Orte („in Tokio“), Negation, Rechenaufgaben, Mehrfachaufträge. 4/4, `OwnerChatIntentRouterTest` 5/5.

### 3.4 Falsches Werkzeugrouting durch Negation
- Befund per Routing-Sonde auf `85602f73`: Speicher/Akku waren klauselweise negationsfest (#365/#367), die übrigen read-only Werkzeuge nicht: „Zeig mir nicht meine Downloads“ → `list_files`; „Prüfe nicht mein Gerät“ → `get_device_info`; „Kein Netzwerk prüfen, sag mir nur einen Witz“ → `get_network_summary`; „Schließe alle Apps“ → `list_installed_apps`; „Ich habe keine Downloads gemacht, wie geht es dir?“ → `list_files`. Umgekehrt wurde „Welche installierten Apps habe ich?“ nicht erkannt.
- **RED** `790b00d0`: `BootstrapBrainNegatedToolRoutingTest` 2 Tests, **2 FAIL**.
- **GREEN** `1e3657aa`: Downloads/Apps/Gerät/Netzwerk/Lumo nutzen dieselbe Klausel-Negation wie Speicher/Akku; App-Liste nicht bei „schließen/beenden/stoppen/aktualisieren“. 2/2, `BootstrapBrainTest` 14/14.
- Bekannte Grenze: Komma trennt keine Klausel; „Zeig die Downloads, nicht die Bilder“ startet daher **kein** Werkzeug (fail-closed, Modellantwort statt Tool).

### 3.5 Quellvertrag
`225e86b1`: `.github/continuum/android-integration-wiring.test.mjs` prüfte wörtlich `val agentTask = resolvedQuestion`; auf den neuen Vertrag angepasst (Sinn unverändert: keine Kürzung des Auftrags).

## 4. Ausgeführte Tests (`HOST_VERIFIED`, dieser Rechner)

| Prüfung | SHA | Ergebnis |
|---|---|---|
| Android-JVM-JUnit vollständig | `85602f73` (Basis) | 1739 Tests, **1 FAIL**, 5 skip |
| Android-JVM-JUnit vollständig | `1e3657aa` + Sonde | 1753 Tests, **0 FAIL**, 5 skip (inkl. 1 lokaler, nicht committeter Sondentest) |
| Node, 58 Android-bezogene Testdateien (`.github/continuum`, `scripts`, `backend`) | `85602f73` | 362 Tests, 359 pass, 0 fail, 3 skip |
| dieselben Node-Tests | `225e86b1` | 362 Tests, 359 pass, 0 fail, 3 skip |
| Android-JVM-JUnit vollständig + `:app:lintDebug` | `225e86b1` (Code-Endstand; danach nur dieses Dokument) | **1752 Tests, 0 FAIL, 5 skip**; Lint: „No issues found.“ |

Nicht ausgeführt / nicht möglich hier: GitHub Actions (laut `AGENTS.md` P0 nicht auslösen; der Workflow läuft ohnehin nur für PRs auf `main`), Render-QA-Host, APK-Build/Signatur, **jede Fold7-Geräteprüfung**, native LiteRT-LM/Gemma-Inferenz (keine Modellgewichte, kein Gerät). Historische Node-Dateien unter `docs/evidence/**` (eingefrorene Altstände) prüfen den *aktuellen* Quelltext und schlagen auch auf der Basis fehl; nicht als Regression gewertet.

## 5. Grenzen – ehrlich

- Ob Gemma 4 E4B mit dem neuen Aufgabenrahmen T2 = „rot 9“ und T3 = „blau 7“ antwortet, ist **nicht** geräteseitig belegt (`RESEARCH_HYPOTHESIS` gestützt durch `DEVICE_OBSERVED`: Ein-Turn-Fassung funktioniert).
- Die Anschlussfrage-Erkennung (`LocalConversationFollowUpPolicy`) ist lexikalisch; „Welche ist jetzt größer?“ ohne „statt/ursprünglich“ wird nicht als Anschluss erkannt (der Verlauf geht dann weiterhin nur als optionaler Block mit).
- Nicht-Modellantworten (z. B. Gerätewerkzeug, Startkern-Antwort) löschen weiterhin den flüchtigen Gesprächskontext (`addAssistantMessage` ohne `preserveLocalContext`). Bewusst nicht in M1 geändert, weil es eine Sicherheitsentscheidung ist.
- Zeitanker ändert sich minütlich → späteres Prompt-Prefix-Caching muss ihn ans Ende verschieben.
- `TaskScopedRingStateProjector` (PR #367) bleibt domänenspezifisch; der neue `LocalFollowUpTaskFrame` ist der domänenneutrale Weg.

## 6. Release-Vertrag
Keine Änderung an Signatur, Schlüsseln, Publisher, Paket `at.heinz.deviceai.continuumlocal` oder `main`.
versionCode wird nicht im Quelltext gesetzt, sondern über die bestehende Gradle-Eigenschaft
`continuumOwnerLocalVersionCode` des original signierten Render-Pfads; eine In-place-APK aus dieser Kette braucht **≥ 40**
und nachgewiesene Zertifikatsidentität. Keine APK gebaut, nichts veröffentlicht.

## 7. Geräte-Abnahme (für den Besitzer, Internet am Fold7 aus)
1. T1, T2, T3 aus Dossier 15.1 im selben Chat → erwartet blau 7 / rot 9 / blau 7; `KONTEXT`-Zeile in der Werkzeugaktivität beachten.
2. „Speichere diese Angaben nicht dauerhaft.“ und „Zeig mir nicht meine Downloads“ → kein Gerätewerkzeug.
3. „Wie spät ist es?“ → `GERÄTEUHR`-Antwort; danach erneut T3 → Kontext muss erhalten sein.
4. „Welcher Tag ist morgen?“ → Modell soll Mittwoch, 4. November 2026 nennen (Zeitanker).

## 8. Empfohlener Meilenstein 2
1. **Geräteabnahme M1** über den bestehenden original signierten Render-Pfad (versionCode 40), danach Ergebnis als `DEVICE_OBSERVED` hier nachtragen.
2. **Kontext überlebt Nicht-Modellantworten**: Werkzeug-/Startkern-Turns als Nutzer-Turn mit Quittung in `LocalConversationContext` statt Löschen (RED: Ringaufgabe → „Wie viel Speicher?“ → „ursprünglich?“).
3. **Domänenneutraler flüchtiger Zustandsgraph** (Dossier 6.2, bitemporal) aus Nutzerturns statt Ring-Spezialfall, mit den fünf Falsifikationstests aus Argons offener Frage.
4. **LiteRT-LM Geschwindigkeit**: Prompt-Präfix stabil halten (Zeitanker/Variablen ans Ende), Governance-/Werkzeugblock kürzen, Prefill-Zeit pro Turn messen (Gerät), danach native Conversation-Wiederverwendung über Turns mit Epoch-/Cancel-Schutz (Fortsetzung #368).
5. **CCC1 RED→GREEN-Quittung** (Dossier 9.1, Punkt 6) als Datenobjekt mit Diff, SHAs, Testartefakten und UNKNOWN-Zustand – dieser Meilenstein liefert das manuelle Vorbild.
