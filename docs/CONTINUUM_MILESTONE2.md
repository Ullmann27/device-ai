# Continuum Ω – Meilenstein 2: Kontext über Werkzeugantworten, flüchtige Aussagen-Zeitachse, Promptgröße, CCC1-Quittung

Stand: **03.11.2026, Europe/Vienna**. Bearbeitung: Grok Bot im Auftrag von Heinz Ullmann.
Belegstufen nach Argon-4-Dossier: `DEVICE_OBSERVED`, `RELEASE_VERIFIED`, `HOST_VERIFIED`, `SOURCE_ONLY`, `RESEARCH_HYPOTHESIS`.

## 1. Quell-SHAs
| Rolle | SHA |
|---|---|
| Installierte Geräte-Releasesource 0.3.38 / Code 39 | `a9709854f48d2fd6589f9ce5ea97f6ec3be0d233` |
| Basis = M1-Kopf (PR #369) | `7316b1f5d32cf9ad52d9f463a987aae23bf26ba2` |
| Dieser Branch | `continuum/m2-grok-context-state-20261103` (Kette #365 → #366 → #367 → #368 → #369 → dieser PR) |

## 2. RED → GREEN (je eigener Commit, alle `HOST_VERIFIED`)

| # | Fehler / Ziel | RED | GREEN |
|---|---|---|---|
| 1 | Komma-Kontrast „Zeig mir die Downloads, nicht die Bilder“ startete kein Werkzeug (M1-Grenze) | `0b1e9191`: 2 Tests, 1 FAIL | `4819cccc`: 2/2 |
| 2 | Nicht-Modellantwort (Gerätewerkzeug, Geräteuhr, Startkern, Sicherheitsprüfung) löschte den ganzen Kurzzeitkontext | `03761217`: 4 Tests, 3 FAIL | `aa2b3e58`: 4/4 |
| 3 | Ring-Spezialfall → domänenneutrale flüchtige Zeitachse der Nutzeraussagen | `ab71a43f`: 6 Tests, 5 FAIL | `5e3b68e3`: 6/6 |
| 4 | Werkzeugschemata doppelt im Prompt; Zeitanker im statischen Präfix | `2206e8fb`: 2 Tests, 2 FAIL | `b8f0d17b`: 2/2 |
| 5 | CCC1 RED→GREEN-Patch-Quittung als prüfbares Datenobjekt | `010b93e6`: 6 Tests, 5 FAIL | `8af4e47c`: 6/6 |

### 2.1 Komma-Kontrast
Ein Komma trennt nur dann eine Klausel, wenn danach `nicht/niemals/kein…` (optional mit `aber/jedoch/doch`) folgt. Ein Nebensatz-Komma („Ich will nicht, dass du meine Downloads öffnest“) ist bewusst keine Grenze, damit die Negation des Hauptsatzes bestehen bleibt.

### 2.2 Kontext überlebt Werkzeugantworten
`LocalContextRetentionPolicy` + `LocalConversationContext.recordNonModel`. Szenario aus dem Test: Ringaufgabe → „Wie viel Speicher habe ich?“ → „Welche Kiste hatte ursprünglich mehr?“. Code39 hat den Kontext nach der Speicherantwort gelöscht. Jetzt wird die Werkzeugfrage als Nutzerturn mit festem Etikett verbucht, ohne Messwerte und ohne Modelltext. Weiterhin gilt: maximal 4 Turns, nur gleiche Sitzung. **Erinnerungsänderungen (Merken/Ändern/Vergessen) löschen den Kurzzeitkontext weiterhin**, weil alter Dialog einer neuen Besitzerangabe widersprechen kann. In `AgentActivity` an 5 Stellen über `addNonModelReply(kind)` verdrahtet (Werkzeugergebnis, Sicherheitsablehnung, Geräteuhr, Startkern-Antwort, Startkern-Fallback für Begrüßung/Fähigkeiten); diese Stellen sind `SOURCE_ONLY`, die Regel selbst ist `HOST_VERIFIED`.

### 2.3 `OwnerStatementTimeline` (Dossier 6.2, flüchtig)
- Liest nur die eigenen Nachrichten des Besitzers dieser Sitzung (max. 4 + aktuelle). Kein Modelltext, kein Web, kein Langzeitgedächtnis, keine Speicherung, keine Werkzeugbefugnis.
- Erkennt `<Artikel> [Adjektiv] Nomen … Zahl [Einheit]`, die Ellipse aus dem Dossier-T1 („in der blauen 7“), „statt 4“, „anstatt/anstelle von 4“, „von 4 auf 9“ und „ab morgen“. Fragen („…?“) behaupten nichts.
- Verwirft die **ganze** Projektion bei: Negation, Unsicherheit („vielleicht/etwa/oder“), Bedingung („wenn/falls/hätte“), ausdrücklicher Vergangenheit („gestern/damals“), gemischter Zukunft in einem Satz, Einheitenkonflikt, Doppelwert in einer Nachricht, > 8 Entitäten und Größengrenzen.
- Kompositum-Einheiten („Kupferringe“ ~ „Ringe“) gelten als gleich. Diese Regel war nötig, weil der Dossier-T1 „Kupferringe“ sagt und der T2 „Ringe“.
- `AgentActivity` nutzt für erkannte Anschlussfragen jetzt diese Klasse statt `TaskScopedRingStateProjector`. Die Ring-Klasse bleibt samt Tests unverändert im Repo (nicht gelöscht), wird aber nicht mehr aufgerufen.
- Offen benannte Testkorrektur im GREEN-Commit: Eine bedingte **Frage** („Wenn … hätte, was dann?“) behauptet nichts und wird ignoriert. Der Test prüft jetzt die bedingte **Aussage**, die korrekt verworfen wird.

### 2.4 LiteRT-LM-Prompt: Größe und stabiles Präfix
- Befund (Quelle `LiteRtLmLocalModelRuntime`): Alle Werkzeuge werden der Chat-Conversation bereits nativ als `OpenApiTool` übergeben. Der Textabschnitt `REGISTERED TOOLS` mit Beschreibungen war eine Dublette.
- `AiContextPolicy.nativeToolCatalog = true` in `AgentActivity`: Im Abschnitt stehen nur noch Namen und Risikoklasse. Die Autorisierung bleibt unverändert im ToolGate.
- **Messung (`HOST_VERIFIED`, echter `ToolRegistry`, Fixture ohne Erinnerungen, T2 als Anfrage): `compiledContext` 2.910 B → 1.267 B (−1.643 B, −56 %).** Alle Werkzeugnamen und die Governance bleiben enthalten.
- Der minütlich wechselnde Zeitanker steht jetzt in `requiredTail` (letzte Zeile). Damit ist das statische Anweisungspräfix zwischen Turns byteidentisch, die Voraussetzung für späteres Präfix-/KV-Caching. Geprüft per Quellvertrag-Test. Ein tatsächlicher Cache-Gewinn ist **nicht** gemessen (`RESEARCH_HYPOTHESIS`): Die Runtime nutzt pro Turn weiterhin `freshPrompt=true`.
- Korrektur zur M1-Doku: Der kompilierte Kontext ist ohne Erinnerungen ca. 2,9 kB groß, nicht „ca. 10 kB“ (10,5 kB ist nur die Obergrenze).
- Nicht gemessen: Prefill-Zeit und Token auf dem Fold7.

### 2.5 `Ccc1PatchReceipt`
Datenobjekt mit strenger Prüfung und kanonischem JSON (`continuum-ccc1-patch-receipt`, Version 1, Parser über die bestehende `StrictJson`).
- `GREEN_VERIFIED_PENDING_REVIEW` nur, wenn alle Bedingungen erfüllt sind:
  - volle SHAs, paarweise verschieden;
  - SHA-256 des Patches;
  - der RED-Lauf auf der RED-SHA scheitert wirklich an deklarierten Zieltests;
  - der GREEN-Lauf auf der GREEN-SHA hat 0 Fehler und besteht alle Zieltests.
- Timeout, Abbruch oder fehlender Exit-Code ergeben `UNKNOWN`. Eine behauptete Merge-, Installations- oder Gerätebestätigung ergibt `INVALID`.
- Die Testfixture ist die reale M1-Quittung (`988ff152…` RED, 3/7 FAIL → `cf4620e0…` GREEN).
- **Grenze:** noch **nicht** in den CCC1-/Termux-Laufpfad (`ContinuumMobileCodingJob`, `ContinuumCcc1Bridge`) verdrahtet (`SOURCE_ONLY` für die Integration). Die bestehende Quittung dort kennt baseline/candidate/independent, aber keinen RED-Nachweis.

## 3. Gesamtprüfung (`HOST_VERIFIED`, JDK 17.0.21, SDK 36, Gradle 8.11.1)
| Prüfung | SHA | Ergebnis |
|---|---|---|
| Android-JVM-JUnit vollständig | `8af4e47c` | **1.772 Tests, 0 FAIL, 5 skip** (M1: 1.752 + 20 neue) |
| `:app:lintDebug` | `8af4e47c` | „No issues found.“ |
| Node, 58 Android-bezogene Testdateien | `8af4e47c` | 362 Tests, 359 pass, 0 fail, 3 skip |

Nicht ausgeführt: GitHub Actions (laut `AGENTS.md` nicht auslösen), Render-QA, APK/Signatur, Fold7, native Gemma-Inferenz.

## 4. Grenzen
- Kein Gerätebeleg dafür, dass Gemma mit Zeitachse + Aufgabenrahmen T2/T3 richtig beantwortet.
- Die Zeitachse ist lexikalisch-deutsch: Nomen ohne Artikel („Rote Kiste hat 9“) und Mengen in Worten („vier“) werden nicht erkannt. In diesen Fällen gibt es keine Projektion, es wird nichts geraten.
- Die verbuchten Nicht-Modellturns belegen Plätze im 4-Turn-Fenster. Zwei Werkzeugfragen zwischen T1 und T3 verdrängen T1 noch nicht, vier schon.
- `TaskScopedRingStateProjector` ist jetzt toter Code. Entfernen erst nach Besitzerentscheidung.

## 5. Release-Vertrag
Unverändert: kein `main`-Merge, keine Signatur-, Schlüssel- oder Publisher-Änderung, Paket `at.heinz.deviceai.continuumlocal`. Eine In-place-APK braucht `continuumOwnerLocalVersionCode` ≥ 40 über den bestehenden original signierten Render-Pfad. Keine APK gebaut.

## 6. Fold7-Abnahme (Internet aus)
1. T1 → „Wie viel Speicher habe ich?“ → T3 („ursprünglich“) → erwartet blau 7. Die Speicherantwort darf den Kontext nicht löschen.
2. Dossier-T1 „In der roten Kiste liegen 4 Kupferringe, in der blauen 7.“ → T2 → erwartet rot 9.
3. „Zeig mir die Downloads, nicht die Bilder“ → Downloads-Liste; „Ich will nicht, dass du meine Downloads öffnest“ → kein Werkzeug.
4. Prefill-/Antwortzeit T2 gegenüber Code39 notieren (kleinerer Prompt).

## 7. Übergang zu Meilenstein 3 („Projekt Selbstrekonstruktion“, Besitzerauftrag 03.11.2026 13:48)
M2 liefert die prüfbaren Bausteine, auf die M3 aufsetzt:
- `Ccc1PatchReceipt`: Belegformat für den autonomen Programmierer. Als Nächstes in den CCC1-Laufpfad verdrahten, damit jede Selbstkorrektur via Tests eine RED→GREEN-Quittung erzeugt.
- `OwnerStatementTimeline` + `LocalFollowUpTaskFrame` + `LocalContextRetentionPolicy`: Gesprächskontext und Zustandsverfolgung für Planner/Executor-Schleifen.
- Kleinerer, stabiler Prompt: Budget für ReAct-artige Werkzeugschritte mit Verifikation.

M3 verwendet nur öffentlich dokumentierte Verfahren (Planner/Executor, ReAct mit Verifikation, Retrieval-Gedächtnis, Selbstkorrektur via Tests, Spezialisten-Routing), jeweils mit Alternativenvergleich und Messung. Die 3D-Kopf-UI startet erst, wenn die Referenzbilder des Besitzers vorliegen.
