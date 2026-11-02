<!-- coderabbit-ai-generated:v1 -->

# Mission OS B — vorbereiteter Prüfvertrag (2026-11-02)

## Status: IMPLEMENTED / TESTED (synthetisch), NOT_INTEGRATED

`VerifiedNodeClick` ist ein interner, nicht aufgerufener Kandidat. Er prüft vor einem einzelnen Portaufruf eine ausdrückliche Bestätigung, Zielpaket, Epoch/Revision, vollständige und als sicher gemeldete Fenster, eindeutiges sichtbares nicht sensitivas Ziel und eine vorher noch nicht vorhandene Nachbedingung. Er verlangt danach eine neue Revision, geänderten Zustand und genau eine nicht sensitive erwartete Beobachtung. Abbruch- und Fristfehler werden durchgereicht; kein zweiter Aufruf erfolgt. Sechs synthetische Unit-Tests decken falsche App, alte Revision, Mehrdeutigkeit, ein als Overlay markiertes Fenster, sensible Knoten, Berechtigungswechsel, Timeout und Abbruch ab.

**Dies ist keine tatsächliche Android-Handlung.** `ClickObservationPort` ist nur ein Test-Port. Seine Informationen, das Boolean `ownerConfirmed`, der Fensterstatus und der beobachtete Nachzustand sind nicht eigenständig vertrauenswürdig. Es gibt keinen AccessibilityNodeInfo-Adapter und kein `performAction`; der Kandidat ist nicht in Registry, Executor, ToolGate oder UI registriert. Ein grüner Porttest beweist deshalb weder die echte Overlay-Sperre noch eine Besitzerfreigabe oder ein Gerät. Bestehende Firewall nach fremden Bildschirmdaten bleibt unverändert.

## Nachgewiesene Integrationslücken

1. `AgentActivity.safeArgumentSummary` zeigt Strings außer Paketnamen nur als Zeichenanzahl. Eine Freigabe für einen Klick würde das konkrete Element und den erwarteten Zielzustand verbergen. Es braucht eine explizite, begrenzte, gegen Steuer-/Richtungssonderzeichen geschützte Darstellung beider exakten Werte mit einmaliger call-id- und revisionsgebundener Freigabe. Der bestehende ToolGate muss selbst diese Zustimmung kontrollieren; das Test-Boolean allein ist kein Freigabenachweis.
2. `continuum_screen_observation.xml` fordert kein `flagRetrieveInteractiveWindows` an; der Dienst prüft weder vollständige Fensterliste noch überdeckende Fenster. Ein Android-Port muss Verfügbarkeit und Identität des aktiven Ziel-Fensters sowie alle relevanten überlappenden Fenster vor und nach dem einzelnen Klick fail closed prüfen. Die Android-API liefert Ereignisse asynchron; verzögerte/fehlende Ereignisse und der Zeitpunkt zwischen Prüfung und Aktion benötigen konkrete Negativtests und müssen offen dokumentiert bleiben.
3. Die Bestätigung ist ein Continuum-Activity-Dialog. Sein Vordergrundwechsel kann die zuvor beobachtete Zielrevision invalidieren. Der integrierte Ablauf benötigt eine explizite sichere Rückkehr zur Ziel-App und erneute Zustandsprüfung, ohne impliziten erneuten Klick oder von Modelltext bestimmte Touchkoordinaten.
4. `ScreenObservationPrompt.allowsTool` begrenzt Folgeaktionen nach fremdem Bildschirmtext auf lesende Tools. Der Besitzer hat eine separat bestätigte Klickhandlung gewünscht; ein zukünftiger begrenzter Übergang ist eine explizite Änderung dieses Vertrags und muss weiterhin sicherstellen, dass fremder Text keine Freigabe oder andere Werkzeuge erzeugt. Der Vertrag wurde hier nicht geändert.

## Ergebnisse

- RED: Exit1 wegen noch nicht existierender Kotlin-Typen. Dies ist **kein** reproduzierter echter Klick-Fehler.
- Zwischenlauf: Exit1 wegen falschem Testparameter `enabled`; korrigiert.
- GREEN: volle SDK-Gradle-Suite1675 Fälle:1672PASS/0FAIL/3SKIP; sechs Kandidatentests bestanden, Kotlin-Compile und Lint0Issues. Synthetischer Port; keine neuen Robolectric-Tests des Klickpfads.
- Node critical135PASS, Exit0. Voriger vollständiger Node-Lauf1938PASS/0FAIL/13SKIP wurde nicht wiederholt.
- Original-Logs, SDK-JUnit, QA-Bericht, Quell- und Umgebungs-SHA: `docs/evidence/continuum-mission-b-candidate-20261102/results.json`. APK/Fold7/Gemma und die Einstellung→Akku-Handlung NOT_RUN.

## Fortsetzungsauftrag

Zuerst Remote-HEAD und diese Belege prüfen. Bestehenden `ToolRegistry → ToolGate → DeviceToolExecutor → AndroidDeviceAppActions → ContinuumScreenObservationService` nutzen. Einzelne text-/resource-ID-basierte Aktion mit exakter angezeigter Freigabe an frische Beobachtung und Besitzerzustimmung binden. Android-Fenster, App, Overlay, sensible/mehrdeutige Knoten und Revisions-/Epoch-Wechsel vor tatsächlichem `ACTION_CLICK` kontrollieren; genau einmal ausführen. Nach Ausführung eine neue eigene Beobachtung der vereinbarten Nachbedingung verlangen, nicht den Boolean von `performAction`. Keine ungeprüfte Portimplementierung aktivieren. Neue echte Robolectric-Tests für widerrufene Freigaben, fremde Fenster/Overlays, doppelte/Passwortknoten, Timeout/Abbruch und tatsächlichen erfolgreichen Zustandswechsel ergänzen, dann SDK-Gradle/Lint/Node-Gates. Danach auf vom Besitzer autorisiertem Fold7 Einstellungen öffnen → Akku-Knoten nach Freigabe anklicken → echten Wert im neu beobachteten Bildschirm lesen; erst mit tatsächlichem Gerätebeleg DEVICE_VERIFIED melden. Keine produktive Signierung, Eigentümerdaten oder Modellgewichte verändern.
