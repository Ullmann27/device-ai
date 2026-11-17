# Fable 5.1 · Code 51 · P1 #639 Interne Navigation per Sprache/Chat – Handoff (08.11.2026)

**Branch:** `fable/code51-p1-internal-navigation-routing-20261108` (Basis `473f7972`, Code50-QA-Stand)
**Draft-PR:** gegen `chatgpt/code50-qa-staging-20261108` (Nummer siehe PR-Liste; kein Main-Merge)
**Status:** `KOTLIN_UNIT_TESTS_CHECKED` – **nicht** `DEVICE_VERIFIED` (Fold7-Abnahme offen, siehe unten)

## Befund (Quellstand 473f7972)

Der Chat-/Sprachweg `AgentActivity.sendCurrentInput()` kannte interne Ansichten nur über eine fest verdrahtete
`when`-Liste von fünf exakten Kleinschreib-Strings (`"gedächtnis"`, `"gedächtniszentrum"`, `"lernzentrum"`,
`"entwicklungszentrum"`, `"systemstatus"`, `"modelle"/"modellverwaltung"`) **innerhalb** des App-Lookup-Pfads
(`OwnerAppLookupIntent.Intake.Lookup`). Folgen auf dem Gerät:

| Eingabe (ASR/Tipp) | Alter Pfad | Sichtbares Verhalten |
|---|---|---|
| `Jarvis, öffne das Gedächtniszentrum.` (getippt, mit Komma) | Lookup → `when` → `showMemoryCenter` | funktioniert |
| `Jarvis öffne das Gedächtnis Zentrum` (ASR trennt Kompositum) | Lookup(`Gedächtnis Zentrum`) → `find_app` | „Ich habe keine passende startbare App gefunden … Welche App meinst du genau?“ |
| `Öffne Logik und Planung` / `Denken und Planen` | `und` ⇒ `Intake.Planning` → `runLocal` (Gemma) | Modell rät; fragt sinngemäß nach Datei/PDF |
| `Zeig mir das Gedächtnis`, `Geh zum Gedächtnis`, `Ich möchte ins Gedächtniszentrum` | kein Treffer → Modell | wie oben |

„Logik und Planung“ (Test 02B) war damit auf dem alten Stand **nie** per Sprache erreichbar.

## Änderung

- **Neu** `cognitive/OwnerInternalNavigationIntent.kt` (pure Kotlin, kein Android, keine Ausführung):
  normalisiert deutsche Freiformulierungen (Anrede, Höflichkeit, Artikel, Präpositionen, „Zentrum/Ansicht/Bereich“,
  Umlaute, Bindestriche, ASR-Trennungen, `&`→`und`) und ordnet sie per Levenshtein mit längenabhängiger Toleranz
  (≤6 Zeichen exakt, 7–9 → 1, ≥10 → 2) den **bestehenden Routenschlüsseln** zu (`memory`, `logic`, `research`,
  `agents`, `tasks`, `web`, `learning`, `device`, `system`, `development`, `model-management`, `brain`, `settings`,
  `privacy`, `notifications`, `mobile-builder`, `home`, `chat`). Ein Test prüft, dass jedes Ziel in
  `JarvisMenu.routes` bzw. `ContinuumWorkAreaNavigationSpec` wirklich existiert – keine erfundenen Ziele.
- **Abgrenzung** (bleibt Datei-/App-Auftrag, Resolver liefert `null`): Datei-/Ordner-/PDF-/Dokumentwort oder Pfad
  (`OwnerAppLookupIntent.looksLikeFileTarget`, jetzt `internal` statt privat), explizites Wort „App“, Negation,
  zusammengesetzte Aufträge („… und schreibe …“), Fragen/Erklärungen ohne Navigationsverb, Appnamen
  (Signal, WhatsApp, Chrome, Termux, Planer, Memo, Systemd …). Bare „Einstellungen“ bleibt absichtlich Android-App-Suche;
  der Cockpit-Screen heißt „Personalisierung“/„Continuum Einstellungen“.
- `AgentActivity.sendCurrentInput()`: Resolver läuft **vor** `OwnerAppLookupIntent.inspect`, nutzt denselben
  `navigateDashboardTarget(target)`-Weg wie Rail und Kacheln (schließt Arbeitsfenster, `touchedCockpitTarget`,
  `menuNavigator.navigate` bzw. die expliziten Center), schreibt die Modellroute sichtbar in die Tool-Aktivität:
  `NAVIGATION · Gedächtnis geöffnet · interne Ansicht · kein Web oder Modellaufruf`. Die alte `when`-Liste ist entfernt
  (eine Tabelle statt zwei).

## Tests

- `app/src/test/java/at/heinz/deviceai/cognitive/OwnerInternalNavigationIntentTest.kt` (RED auf 473f7972 – Klasse fehlte):
  exakter Owner-Befehl aus #639; 12 Freiform-/ASR-/Tippfehler-Varianten → `memory`; 6 Varianten → `logic` inkl.
  ASR-Verwechslung „Blanung“; alle Rail-/Kategorieziele erreichbar; Datei/PDF/Ordner/Pfad bleiben `null`; Apps,
  Negationen, Fragen, Kombi-Aufträge bleiben `null`; Owner-sichtbares Label; Aktivitätszeile ohne Modell/Web.
- Vollauf: `scripts/android-kotlin-unit-check.sh` (JDK 21, kotlinc 2.4, außerhalb Gradle) → siehe PR-Body; erwartete
  2 vorbestehende Umgebungsfehler (`ChiefBotStorageBoundaryTest` als root, `OwnerAppActionFlowTest` JDK≥19 `\b`).

## Offen / Fold7-Geräteabnahme (nicht behauptet)

1. Sprachmodus: „Jarvis, öffne das Gedächtniszentrum“ und „Öffne Logik und Planung“ per Hands-free → Ansicht öffnet,
   Tool-Aktivität zeigt `NAVIGATION · …`, keine App-/Dateifrage.
2. Negativ: „Öffne die Datei Gedächtniszentrum.pdf“ → weiterhin Dateiweg (Android-Picker), nicht der Tab.
3. Tatsächliche Samsung-ASR-Transkripte der Besitzerbefehle einsammeln (Diagnose) und bei Bedarf als Aliase ergänzen –
   bitte in `destinations` eintragen, nicht in eine zweite Tabelle.
4. Nicht Teil dieses PRs: filmische Darstellung der geöffneten Ansicht (#638 / PR #651), Websuche aus dem Chat (eigener Slice).
