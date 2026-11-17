<!-- coderabbit-ai-generated:v1 -->

# Mission OS B — Fensterereignisse und Klickprüfung

## Checkpoint: Gitar-Befunde, noch keine Produktintegration

Die beiden in PR354 gemeldeten Fälle wurden mit Robolectric reproduziert: ein fremder Fensterwechsel invalidierte eine stabile Lesung; fremde Inhaltsereignisse störten den Klick und konnten die Nachprüfung vorzeitig wecken. Der aktive Lese-/Klickvorgang bindet nun unter Lock Paket und Fenster-ID; fremde Inhalte werden beim Klick erst nach Bindung des Ziel-Fensters gefiltert. Ein Ereignis vor dieser Bindung bleibt unsicher. Alle Zustände werden in finally gelöscht; parallele Vorgänge bleiben gesperrt.

Ein fremdes `TYPE_WINDOWS_CHANGED` wird nur ignoriert, wenn Änderungsart, absendendes Fenster/Paket, ursprüngliches aktives fokussiertes Ziel und Epoch/Revision bekannt und unverändert sind und sämtliche übrigen relevanten Fenster nachweislich nicht überlappen. Tastatur, Overlay, unbekannte Kennung, Fokus-/Aktivwechsel, verschwundenes Ziel, API<28 und sonstige Unsicherheit invalidieren. Android kann Fenster oder Ereignisse verzögert bzw. nicht liefern; kein atomarer Gerätebeweis.

Nach tatsächlichem Dispatch wartet die Prüfung auf ein relevantes Zielereignis. Ein unbekanntes Ereignis bleibt für die gesamte Aktion ein Fehler und wird durch ein späteres Zielereignis nicht geheilt. Ein frisch beobachtetes Fenster derselben Ziel-App mit neuer ID kann nach passendem Zielereignis als Nachzustand geprüft werden. Kein automatischer zweiter Klick.

## Tests

- RED2: drei reproduzierte Fehler bei fremdem Fenster/Inhalt und verfrühter Nachprüfung. Weitere unabhängige Negativtests fanden ein ignoriertes Fokusereignis, einen frühen ungebundenen Inhalt und ein fälschlich heilbares unbekanntes Nachereignis; neue Ziel-Fenster-ID wurde vorher fälschlich abgewiesen. Gegenläufe bestehen.
- SDK-Gradle: **1692PASS/0FAIL/3SKIP** von1695, darunter20/20 native Robolectric-Fälle; Lint0Issues, Kotlin-Compile PASS. Node critical135PASS. Rohlogs, ursprüngliche Exit-Codes, SHA256 und XML im Evidence-Verzeichnis.
- `DEVICE_VERIFIED: NOT_RUN`. Kein Fold7, Akkuwert, APK, Signierung oder Merge.

## Produktintegration noch offen

Der Eigentümerauftrag verbietet eine pauschale Erlaubnis von `click_visible_node` als Folge fremder Bildschirmdaten. Die vorbereitete Integration muss deshalb explizit an einen unabhängigen Besitzerauftrag und eine angezeigte Einzelbestätigung gebunden werden. Vor allem muss die Bestätigung den tatsächlichen App-Fokuswechsel handhaben: nach ausdrücklicher Zustimmung Settings sicher wiederherstellen, frisch beobachten, Identität und bestätigtes Ziel vergleichen und bei Änderung erneut um Freigabe bitten. Ein bloßes `ownerConfirmed`-Boolean ist keine Autorität. Registry → ToolGate → Executor → native Aktion erst nach diesen Bedingungen verbinden und mit echten Robolectric-Tests prüfen. Screen-/Fensterzustand bleibt unsicher bei nicht gelieferten OS-Ereignissen; keine impliziten Wiederholungen. Der bestehende Integrationsentwurf ist noch nicht Bestandteil dieses Commits.
