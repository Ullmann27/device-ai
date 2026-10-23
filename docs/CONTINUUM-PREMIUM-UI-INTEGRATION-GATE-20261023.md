# Continuum – Premium-Design erhalten und für Besitzer-APK vollständig übernehmen
Stand: 23. Oktober 2026. **KEIN DESIGN-FREEZE. KEINE HERABSTUFUNG. KEINE RELEASE-FREIGABE.**

## Besitzerentscheidung

Die nächste Besitzer-APK muss **alle zu diesem Zeitpunkt tatsächlich fertiggestellten, geprüften Astra-Designverbesserungen** übernehmen: Hologramm, Neon/Glas, Orbitalringe, Fold7-Ansichten, Cockpit, Schrift, Animation und effiziente Grafikimplementierung. Die bereits zusammengeführte Hologramm-/Bento-/Fold7-UI darf nicht durch ältere Zweigstände überschrieben werden.

Die neue APK wird erst auf **einem** gemeinsamen überprüften Commit aufgebaut, auf dem sowohl CCC1-Reparatur/Termux als auch der neueste geprüfte Premium-UI-Stand vollständig vorhanden sind. Keine vorzeitige APK aus Opus-Code ohne Astra-UI.

## Was GitHub aktuell wirklich beweist

- PR #97 `integration/continuum-current-review-20261023` enthält UI aus PR #91 und Fold7-Composer-Code aus PR #96.
- Opus' Integration `opus/ccc1-e2e-20261023` enthält diese gemeinsame PR-97-Basis plus die jüngsten `node-repair`- und Android↔Termux-Befehle. Kein erneuter harter Merge alter Opus-Dateien.
- Der während Astras Cloud-Durchlauf gemeldete Hologramm-Glow-/Bildvergleich, die ca. 53–54 % geringeren JVM-Renderer-Allokationen, die QA-Erweiterungen aus PR #90 und die abschließenden Gradle-/Lint-Testergebnisse sind **noch nicht als neue Astra-Commit-ID oder überprüfbare Assets im GitHub-Bestand gefunden worden**. Aussagen über tatsächliche Integration wären zum jetzigen Zeitpunkt unberechtigt.
- Der vorliegende Branch `integration/continuum-premium-ui-preservation-20261023` umfasst von der zu Beginn bestätigten Opus-Basis die gezielte Korrektur des veralteten `fold-responsive-reflow.test.mjs`-Quelltests. Der Test wurde auf die reale `ComposerReflowDraft`-Implementierung mit Text, beiderseits begrenzter Selektion, Fokus und IME umgestellt; keine optischen Dateien wurden verändert.
- Der versehentlich zuvor erstellte Branch mit Namen `integration/continuum-ui-freeze-ccc1-20261023` ist **keine** geplante Basis und bedeutet ausdrücklich keine Designfreigabe oder gestoppte Entwicklung. Für Integration den neueren Review-Zweig verwenden.

## Aufnahme von Astras Ergebnis, ohne Verluste

1. Nach Abschluss der Astra-Cloud-Session deren **tatsächlichen committed und gepushten Branchnamen samt vollständiger HEAD-SHA** feststellen. Cloud-Ausgaben oder Screenshots allein sind kein GitHub-Commit.
2. `git diff --name-status` gegen den gemeinsamen Basisstand durchführen. Neue UI-Implementierungen, Font-/Layoutänderungen, Prüfsuiten, Messdaten, Render-Screenshots und Referenzen **als Einheit** betrachten; kein Cherry-Pick nur des Bildmaterials.
3. In isoliertem Integrations-Worktree die neuen Astra-Änderungen **konfliktbewusst** auf den neuesten CCC1-Zweig übertragen. UI-Dateien nicht über eine ältere Kopie von `AgentActivity.kt` oder `ContinuumHologramRenderer.kt` ersetzen. Bei Konflikten die neue UI-/Fold7-Funktion erhalten und Opus-Termux/Status-Schnittstellen ebenfalls erhalten.
4. Bestehende Source-Contract-Tests aktualisieren, **niemals durch Entfernen relevanter Sicherheitsassertions grün färben**. Insbesondere die Fold-Reflow-Testkorrektur in diesem Branch verwenden, statt die neue `ComposerReflowDraft` rückgängig zu machen.
5. Identische Render-Settings und Vergleichsdaten für aktuelles Telefon-, geöffnetes Fold7- und Chat-/Entwicklungszentrum-Screen prüfen; visuelle Regressionen (abgeschnittener Text, Kollisionen, schlechterer Kontrast, fehlender Status, unbenutzbarer IME) verhindern.
6. Astra-Ergebnis separat auf Geschwindigkeit/Allokationen prüfen. JVM-/Java2D-Hostmesswerte beweisen **weder** Android-FPS noch Akkulaufzeit; keine ungedeckte 60-FPS-Garantie.
7. Erst auf dem gemeinsamen unveränderlich protokollierten Quell-SHA: volle Node-Tests (einschließlich CCC1), JVM/JUnit, Android Gradle, Lint (mit echtem Prüfumfang), Emulator- und soweit verfügbar Fold7-Geratetests ausführen; alle sechs historisch offenen Node-Fehler erneut beurteilen. Unverfügbare Namespaces nicht als Erfolg werten.
8. Besitzer-Paket `at.heinz.deviceai.mentorpaired`, `versionCode>17` und exakt gleiches Zertifikat wie die installierte `0.3.16` sicherstellen. Erst danach signierte APK bauen und In-place-Update auf dem Fold7 prüfen.

**Freigabeverbot:** Kein neuer Owner-APK-Build solange Astra-UI noch nicht gesichert/integriert und die gemeinsamen Prüfungen nicht nachgewiesen sind.

## Priorität

Jetzt keine weiteren optionalen Designexperimente erzwingen. Das Ziel ist **Astras tatsächlich verbesserte vorhandene Designarbeit abschließen, sichern, zusammenführen und ohne Verschlechterung in der neuen APK sichtbar machen.** Das ist kein Freeze der Oberfläche: spätere Verbesserungen bleiben möglich.
