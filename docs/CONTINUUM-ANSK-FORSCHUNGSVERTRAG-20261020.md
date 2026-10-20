# Continuum ANSK – Forschungsvertrag und Abnahme

Stand: 20.10.2026 · isolierter Branch `astra/continuum-experiment-charter-20261019` · Draft PR #42

## Leitidee

ANSK (Autokatalytische Neuro-Symbolische Kristallisation) bezeichnet im Projekt das **Forschungsziel**, verifizierte, wiederverwendbare Reparaturrezepte mit sicherem Abruf durch eine kleine lokale Ranking-Komponente zu kombinieren. Es ist **kein wissenschaftlich bewiesenes allgemeines KI-Verfahren**, kein Superintelligenznachweis und kein Ersatz für Compiler, Sicherheitsprüfung oder menschliche Freigabe.

Ein „Kristall“ ist zunächst **nur ein versionsgebundener, deklarativer Reparaturvertrag** (Diagnoseklasse, erlaubte Dateitypen, exakte Anwendungsbedingungen, Patch/Hunk-Muster, Testprofil, Quellen-/Evidenzbezug, SHA-256 der Rezeptbytes, Abbruchgrenzen). Er wird nicht als frei generierter ausführbarer Code ohne Review in `AgentToolLayer.kt` oder `repair-strategies.mjs` registriert.

CRE1 (32-D) ist ein probabilistischer Kandidaten-Ranker, keine eindeutige Adresse und keine Beweisfunktion. Der unveränderliche Rezept-Hash identifiziert eine konkrete Version; der lokale Index darf nur deren Suchreihenfolge beeinflussen. Vorhandene deterministische Risiko-Tiers und `repair-critic.mjs` haben Vorrang.

## Verbindliche Nachweiskette

1. **Grün 0:** Unveränderten Basis-Commit in einem ausschließlich verwalteten Wegwerf-Worktree überprüfen. Echte Profile der festgelegten Kette, mindestens ein ausgeführter Test, null Fehler, null Skips. Quellzustand/HEAD und Testprofil-Versionen festhalten.
2. **Rot Mut:** Nur in demselben temporären Worktree eine bytegenau reversible, erlaubte Mutation anwenden; dieselbe Pflichtkette starten. Ein konkreter Compiler-/Testfehler auf dem mutierten Artefakt muss tatsächlich reproduzierbar sein. Unentdeckte Mutationen werden verworfen, nicht als Trainingsbeispiel ausgegeben.
3. **Grün Fix:** Vorhandene `proposeCandidates`, `critique`, `applyEdits`, Kotlin-/JS-Syntax-Gates und `runChain` verwenden. Kein Rückgriff auf Testmanipulation oder ungeprüfte Modellvorschläge. Primärer Fix darf die pro Profil ausgeführte Testzahl nicht senken oder Skips erhöhen.
4. **Unabhängigkeit:** Einen frisch erzeugten zweiten Worktree auf denselben Basis-Commit zurücksetzen, den **identischen** geprüften Patch unabhängig anwenden und die vollständige Pflichtkette erneut ausführen. Das bestehende `evidenceSufficient` und `validateVerifiedReport` bleiben maßgeblich. Ein anderes SHA-256-Artefakt oder nur ein behauptetes `independentPass:true` ist **kein Nachweis**.
5. **Lernen:** Erst nach einem tatsächlich vom vertrauenswürdigen Worker abgeschlossenen Zweitnachweis dürfen positive Erfahrungen im regulären `repair-ledger` erscheinen. Ein negativer Kandidat muss wirklich kompiliert/ausgeführt und widerlegt worden sein; Critic-Rejection, Timeout und Parserfehler sind **keine** negativen Wahrheitslabels. Ein gescheiterter Groq-Vorschlag beweist nicht die allgemeine Fehlerhaftigkeit einer gesamten Strategie. Training nur auf isolierter Snapshot-Kopie, strikt gruppengetrennte Train/Dev/Holdout-Provenienz, einmalige versiegelte Holdout-Abnahme, Anchors ohne Regression, jederzeit Rollback. Ohne echte verbesserte Holdout-Metriken kein Promotion.

## Implementierter Teil (noch keine reale Trainingsübung)

- `.github/continuum/repair-strategies.mjs`: `planInverseKotlinImportMutation` erstellt eine reversible Forschungs-Mutationsplanung **ohne Dateischreibzugriff**; `proofStatus` kennzeichnet unbewiesene Kandidaten.
- `.github/continuum/inverse-mutation.test.mjs`: fünf statische Planner-Tests für Hash/Roundtrip, Kommentare und Strings, Pfadschutz, Ambiguität, CRLF.
- `.github/continuum/repair-cycle-safety.mjs`: `validateTriplePhaseSample` prüft ausschließlich die Struktur von `runChain`-förmigen Grün–Rot–Grün-Beobachtungen mit Kandidaten/Gegenbeispielen, Testzahl-/Skip-Gates und Zweitbeobachtung.
- `.github/continuum/repair-cycle-safety.test.mjs`: 13 neue Regressionstests, alte Tests erhalten.

**Unverhandelbar:** Selbst bei `{valid:true}` gibt der strukturelle Validator nur `authority:'SHADOW_ONLY'` zurück. Eine frei erzeugte JSON-Eingabe beweist weder Testausführung noch Signatur noch Worktree-Isolation. Der strukturelle Filter schreibt keinerlei `repair-ledger`-Erfolg, verändert keine CRE1-Gewichte und erzeugt keinen Android-Worker.

## Noch offen

- Neue Node-Tests auf Samsung/Termux ausführen und Ergebnis protokollieren.
- Einen echten, owner-freigegebenen Einmalversuch mit verwalteten Worktrees und dem festen `android`-Checkprofil implementieren; Fail-Closed-Cleanup muss auch nach SIGTERM/Restart überprüfbar sein.
- Reale reproduzierbare Compiler-/JUnit-Diagnosen für die Mutation einholen.
- Unabhängigen zweiten Worktree durch die vorhandene Beweispipeline validieren.
- Erst dann Trainingstupel mit tatsächlicher Provenienz an einen **Shadow-Only**-CRE1-Versuch weitergeben. Messwerte für Top-1, MRR, Margin, Zeit, RAM und Ankerstabilität liefern.
- Später Kristall-/Rezeptbibliothek ausschließlich als Review-gesteuerte Daten mit klaren Versionen und Widerruf untersuchen.

## Sofortiger Test auf dem Samsung

```bash
cd "$HOME/device-ai"
git status -sb
node --test .github/continuum/repair-cycle-safety.test.mjs .github/continuum/inverse-mutation.test.mjs
```

Die Code-Dateien müssen vor diesem Test aus dem isolierten Branch aktualisiert sein. Ein grüner Node-Test bestätigt nur die Struktur-/Planner-Logik, **noch keinen erfolgreichen Grün–Rot–Grün-Compiler-/Reparatur-/Lernversuch**. Kein Push/Merge in `main`, kein unbeaufsichtigtes APK-Update.
