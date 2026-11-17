<!-- coderabbit-ai-generated:v1 -->

# Mission OS B — nativer Accessibility-Klick als nicht integrierter Prüfstand

## Fortschritt

Der vorhandene `ContinuumScreenObservationService` kann über den internen `ScreenObservationBus.clickCandidate` einen experimentellen Port zu `AndroidNativeClick` aufrufen. Der Port liest echte Android-AccessibilityWindowInfo-/AccessibilityNodeInfo-Schnittstellen, begrenzt den Baum, prüft Paket/Fenster/Revision/Epoch, eindeutige sichtbare klickbare nicht sensitive Knoten und ruft höchstens einmal `performAction(ACTION_CLICK)` auf. Erfolg verlangt anschließend ein neues Ereignis, einen veränderten Baum und die vereinbarte neu sichtbare Nachbedingung. Berechtigungswechsel/Abbruch/Frist bleiben Fehler. Nach Node-Refresh werden Fenster und aktiver Root nochmals geprüft. `flagRetrieveInteractiveWindows` wird im Service angefordert.

**NOT_INTEGRATED:** Dieser Pfad ist nicht in ToolRegistry, ToolGate, DeviceToolExecutor oder AndroidDeviceAppActions registriert. Das Test-Boolean `ownerConfirmed` ist kein echter Freigabenachweis; kein Modell oder Nutzer kann diesen Kandidaten über den bestehenden Toolpfad auslösen. Activity-Bestätigung zeigt Element-/Nachbedingungstext weiterhin nicht an. Die bestehende read-only-Firewall nach fremden Appdaten bleibt unverändert. Keine Produktbehauptung über eine nutzbare Klickfunktion.

## Reproduzierte Race und Prüfung

Der Test für ein beim `refresh()` erscheinendes zweites Fenster löste vor der Reparatur noch einen Klick aus (RED Exit1). Nach erneuter Fensterprüfung direkt vor dem Aufruf und eigener Gesamtfrist: acht Robolectric-Fälle PASS, darunter Zielwechsel, falsches Paket, sichtbare doppelte Elemente, Passwort, mehrfache Fenster/Overlay, Widerruf vor/nach Klick, Timeout ohne Nachbeleg und eine einmalige Aktion mit neuem Zielzustand. Framework-Objekte sind Robolectric-Shadows, keine Android-Gerätebeobachtung.

Volles SDK-Gradle:1683 Fälle,1680PASS/0FAIL/3SKIP, Compile und Lint0Issues PASS, Exit0. Kritische Node-QA135PASS. Vorheriger Voll-Node-Lauf1938PASS/0FAIL/13SKIP aus anderem Turn nicht erneut ausgeführt. Zwischenschritte: native2 scheiterte wegen Shadow-Override, korrigiert; native-red wurde wegen unbeschränkter Test-Fixture abgebrochen (130), begrenzter native-red2 reproduzierte die Race. Original-Logs, JUnit, Prüfsummen und exakte SDK-Befehlsreferenz: docs/evidence/continuum-native-click-20261102/results.json.

## Grenzen und Fortsetzung

Der Port akzeptiert konservativ genau ein interaktives Fenster; Statusleisten, Systemfenster oder Fold7-Split-Screen können daher echte Aktionen blockieren. Accessibility-Fenster und Events können fehlen oder verspätet sein; zwischen letzter Prüfung und Android-Aktion besteht keine atomare Garantie. Vor Freischaltung diese Bedingungen auf einem echten Gerät untersuchen und negative Frameworktests für weitere Fälle ergänzen. Der Port muss einen unverfälschbaren, auf exact call-id, Paket, Ziel, Nachbedingung und Epoch/Revision gebundenen Einmal-Nachweis aus dem bestehenden ToolGate erhalten; eine vom Modell gelieferte Boolean-Zustimmung ist ungeeignet. Der Dialog muss genau diese Werte lesbar und sicher anzeigen. Den explizit gewünschten begrenzten Wechsel des ScreenObservationPrompt-Vertrags so umsetzen, dass untrusted App-Inhalt niemals selbst Rechte oder andere Aktionen erzeugt. Danach Registry → Gate → Executor → AndroidDeviceAppActions → Bus verbindlich mit aktuellen Preconditions und no-retry testen. Erst anschließend auf autorisiertem Fold7 Einstellungen öffnen → bestätigten Akku-Knoten anklicken → tatsächlichen neuen Bildschirm und Akkustand lesen. DEVICE_VERIFIED, APK, reale Freigabe und Akku-Ende-zu-Ende sind NOT_RUN. Keine Signierung oder Merge.
