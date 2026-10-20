# ANSK-Rezepttransfer — EXP-02 / EXP-03 / EXP-07

Stand: 20.10.2026. Ausgangscommit: `97c6b6da3218a27b8de255512e6b450b8c28992c`.

Der Besitzer bestätigt den Samsung-Lauf `TRIPLE_PHASE_VERIFIED_SHADOW_RECIPE` mit je 632 Tests. Sein Rezept liegt im lokalen Termux-State, nicht im Codex-Checkout. Dieser ursprüngliche Versuch wird nicht erneut ausgeführt. Der neue Transfer ist **SOURCE_INTEGRATED / MOCK_TESTED**, noch kein zweiter Gerätebeweis.

## Ausführbarer Transfer

`node .github/continuum/ansk-transfer-lab.mjs --once --recipe <64-stelliger Rezept-SHA256> --file <zweite Kotlin-Quelldatei>`

Optional `--order control-first` für einen getrennten, gegenbalancierten Folgelauf. Default: Rezept zuerst. Die Quelldatei muss eine andere vorhandene Produktionsdatei mit demselben tatsächlich benutzten JDK-Import sein; Kopien mit identischem Quellhash werden verworfen. Keine freie Shell, keine Downloads, kein Erzeugen eines Ersatzrezepts. Nur sauberer gemeinsamer Forschungsbranch, maximal 45 Minuten und bestehende feste Android-Compile-/Unit-Testprofile. Die globale lokale Build-Lease sperrt konkurrierende kooperierende Worker am Checkout. Ein Git-Worktree ist keine Betriebssystem-Sandbox.

Ablauf: grüne Baseline der zweiten Datei → kontrollierter roter Importfehler → Rezeptarm und bestehender deterministischer Kontrollarm **ohne Rezept/Erfahrungen**. Jeder Arm wird im eigenen Arbeitsstand repariert, kompiliert und getestet sowie nochmals in einem frischen unabhängigen Worktree geprüft. Alle Gradle-Läufe sind seriell und erzwingen neue Ausführung; Testanzahl darf nicht sinken, übersprungene Fälle müssen unverändert bleiben. Das Originalrezept wird nur gelesen.

Ergebnisse landen getrennt von produktivem Gedächtnis/Ranking unter `~/.local/state/device-ai-continuum/ansk-lab/shadow-transfers/<sha256>.json`, exklusiv erstellt und zurückgelesen. Der Bericht enthält Quell-HEAD, Herkunft, Mutation-/Patch-/Quellhashes, Reihenfolge, Plattform, Profile, Testzahlen, Planungs-/Prüfdauer, Erfolg und beobachtete Fehlerquote **eines** Versuchs je Arm. Rohlogs oder Dokumentinhalte werden nicht gespeichert. SHA-256 prüft Integrität, nicht Urheberschaft. `TRANSFER_VERIFIED_SHADOW_ONLY` setzt zwei unabhängig erfolgreiche Arme voraus. Fehlerhafte Kontrollen und Abdeckungsverlust bleiben sichtbar; kein Training, kein automatischer produktiver Rangwechsel, kein kontrastiver Lernbeleg wird behauptet.

## Prüfung und Grenzen

`node --test .github/continuum/ansk-*.test.mjs .github/continuum/repair-experience.test.mjs .github/continuum/repair-loop.test.mjs`

82/82 Tests bestanden. Die neuen Integrationstests verwenden echte Git-Worktrees und einen **simulierten** Gradle-Prozess, um Reihenfolge, feste Befehle, Bereinigung, Dateiintegrität und verweigerte Abdeckungsregression zu prüfen. Das ist kein Kotlin-/Android-Transfernachweis. Rezeptänderung, breite Dateirechte, Symlinks, übergroße Rezepte, unsauberer Checkout und ungültige CLI-Optionen werden negativ geprüft.

Wissenschaftliche Grenze: Beide Strategien können denselben Importpatch erzeugen. Ein positiver Transfer belegt dann Wiederverwendung, keinen Leistungsvorsprung. Ein einzelnes Laufzeitpaar unterliegt Cache-/Thermaleffekten; keine Signifikanz- oder Generalisierungsbehauptung. Die beobachtete Fehlerquote ist keine geschätzte allgemeine Fehlerrate. Weitere unbekannte Dateien und gegenbalancierte Wiederholungen bleiben ein späteres ausdrücklich gestartetes Experiment.

Kompatibilität: Rezeptformat v1 bleibt unverändert; Transferberichte haben eigenes v1-Schema. Vorhandene CEGIS-Kandidaten, Kritiker, Checks, Worktrees und Lease werden wiederverwendet. Rücksetzen: Transferbefehl nicht starten beziehungsweise Implementierungscommit zurücknehmen; bestehende Rezepte bleiben unverändert. Nächster Schritt: vorhandenen Samsung-Rezepthash und eine zweite reale Quelldatei in einem ruhenden lokalen Checkout verwenden. Keine Installation erforderlich.
