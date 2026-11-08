# FABLE 5.1 · Code51 · P1 #640 · Fakt-/Meta-Anweisungs-Trennung · Handoff 2026-11-08

**Branch:** `fable/code51-p1-memory-meta-separation-20261108` (Basis `473f7972`, Code50-Source)
**Status:** Implementiert, Unit-Tests siehe unten. **Fold7-Geräteabnahme offen.**
**Issue:** #640 (gespeicherter Frage-/Anweisungstext wird mit ausgegeben)

## Befund
`Merke dir: Mein Testwort lautet Nebel 731.“ Danach fragen: „Wie lautet mein Testwort?“` wurde bis Code50
**wörtlich** als Fakt gespeichert (`BootstrapBrain.decide` → `substringAfter(':')`). Der Recall war korrekt,
aber `OwnerMemoryPerspective.display` gab den Rohtext inkl. Dialoganweisung und defekter Anführungszeichen aus.
Ursache: ungefilterte Speicherung, kein Retrieval-Fehler.

## Änderungen
| Datei | Änderung |
|---|---|
| `cognitive/OwnerFactMetaSeparator.kt` (NEU) | Pure Trennung Fakt ↔ nachgestellte Dialoganweisung (`Danach fragen:`, `Frag mich später:`, `Zum Test fragen:`, `Prüfe danach:` …); nur nach Satzende/Anführungszeichen, nie am Satzanfang; `writeNote()` für die Rückmeldung |
| `BootstrapBrain.kt` (REMEMBER) | Speichert nur `split.fact`; Anweisung wird als `message` zurückgegeben |
| `AgentActivity.rememberOwnerFact(fact, note)` | Hängt die Notiz („… habe ich nicht als Fakt gespeichert; Dialoganweisung“) an die Erfolgsmeldung |
| `learning/OwnerMemoryPerspective.display` | **Präsentations-only**: alte Einträge mit angehängter Anweisung zeigen nur den Fakt + wörtliches Zitat; Ledger/Index/Prompt bleiben unverändert (keine stille Umformulierung alter Fakten) |
| `test/cognitive/OwnerFactMetaSeparatorTest.kt` (NEU) | 4 Tests: Trennung, Variante, sauberer Fakt unverändert + Negativfälle („Meine Tochter fragt …“, „Ich prüfe jeden Montag: …“), Legacy-Display |

Kein zweiter Memory-Speicher; `ContinuumLearningCore.confirmOwnerFact`, `BootstrapMemoryStore`, SWB1-Gate unverändert.

## Tests
Kommando: `scripts/android-kotlin-unit-check.sh /tmp/unit640` (JDK 21, kotlinc, `CONTINUUM_ANDROID_DEPS`).
Ergebnis: siehe PR-Beschreibung (wird nach Lauf ergänzt). Bekannte Umgebungs-Fails: `ChiefBotStorageBoundaryTest` (root),
`OwnerAppActionFlowTest` (JDK≥19 `\b`).

## Offen / Nächster Schritt
- Fold7: Reproduktion lt. Issue (sauberer Fakt vs. Fakt mit `Danach fragen:`), Anzeige in „Gemerkte Fakten“.
- Noch nicht umgesetzt aus #643 P1-Kognition: Holdout-Messung verrauschter Transkripte, Modellroute pro Turn sichtbar
  (Gemma vs. Lernkern vs. Rückfall) – Übergabe an **Astra** (kognitive Anbindung) und **SOL** (Gesamtintegration).
- Integrationsreihenfolge mit #654/#656: alle drei Branches berühren `AgentActivity.sendCurrentInput`/`rememberOwnerFact`
  an unterschiedlichen Stellen; konfliktarm, aber gemeinsam zu rebasen.
