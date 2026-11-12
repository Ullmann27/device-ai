# Astra Code55: beobachtete Suchfeld-Rolle

Basis: `c1dcf613a8`. Ziel: bestehende Code55-Integration, PR #799.
Integrator: Astra. Keine neue Ausführungs- oder Freigabearchitektur.

## Fehler und Änderung

Der Such-Klick erwartete das spätere Eingabefeld wortwörtlich als „Suchen“ oder
„Search“. Ein echtes Feld mit `search_src_text` und „Fragen oder suchen“ wurde dadurch
nicht bestätigt. Das betraf Workflow, Klickprotokoll, native Wartebedingung und
abschließende Beobachtung.

`LiveSearchSemantics` bündelt die bestehende begrenzte Erkennung anhand von Suchlabels
oder Suchfeld-IDs. Eine Feldrolle erfordert Sichtbarkeit, Aktivierung, Editierbarkeit
und ein nicht sensibles Ziel. Menü-IDs allein sind keine Eingabefeldrolle. Sendeflächen
bleiben ausgeschlossen.

`ClickRequest.expectedSearchField` ist standardmäßig aus. Wenn es gesetzt ist, muss
das bisherige erwartete Label leer, `expectedEditable` wahr und `expectedHeading`
falsch sein. Vor dem Klick darf kein solches Feld vorhanden sein. Danach muss genau
ein Feld in einer neuen Revision mit verändertem Baum beobachtet werden. Alle vier
Prüfstellen verwenden dieselbe Nachbedingung.

Die ursprüngliche Klickauswahl bleibt exakt und eindeutig. Der vorhandene
OwnerComputerTaskGrant erlaubt den Schalter nur bei seinem einmalig genehmigten
Such-Klick. Kontakte, Downloads und reine Öffnungsaufträge bekommen dadurch keine
zusätzliche Aktion. Query, App, Auftrag, Epoch, Wiederholungsgrenze, Widerruf und TTL
bleiben gebunden. Die Textfreigabe verlangt ebenfalls genau ein reales Suchfeld.

## Tests

50/50 reine JVM-Tests bestanden: `LiveSearchClickTest`, `VerifiedNodeClickTest`,
`OwnerComputerWorkflowTest`, `OwnerComputerTaskGrantTest`. Die vier geänderten
Produktionsquellen wurden mit dem vorhandenen Kotlin-2.4.0-Compiler gegen die echten
bereits gebauten Produktionsabhängigkeiten der Integrationslinie neu übersetzt.
Es wurden keine Ersatzklassen oder abgeschwächten Prüfungen verwendet.

Zwei zusätzliche Robolectric-Tests in `AndroidLiveNodePortTest` prüfen den echten
Android-Port mit einem abweichenden Hint und den Widerruf unmittelbar vor dem Klick.
Diese beiden Tests wurden hier noch nicht ausgeführt; der Integrator prüft sie mit
der kombinierten Android-Suite. Hier lief kein paralleler Gradle-Job.

Eine erste isolierte Übersetzung mit dem älteren Gradle-internen Kotlin-Compiler
war wegen inkompatibler Kotlin-Metadaten nicht ausführbar. Der anschließende Lauf
mit dem tatsächlich zum Projekt passenden Compiler bestand die genannten 50 Fälle.

PORTIERT: lokaler Folgecommit. VERIFIZIERT: genannte JVM-Fälle.
GERÄTEBESTÄTIGT: nein. Eine unveränderte echte App-Oberfläche auf dem Fold7 ist
weiter der nächste Gerätetest; Hostdaten sind keine Gerätebestätigung.

## Integration und Rücknahme

Den gesamten Commit zusammen übernehmen: Schnittstellen, vier Prüfstellen,
Grant/Controller und Tests. Die Erweiterung ist standardmäßig ausgeschaltet;
gewöhnliche exakte Label- und Überschrift-Nachbedingungen behalten ihr Verhalten.
Bei Regression den zusammengehörigen Folgecommit zurücknehmen. Paket, Signatur,
Originalassets, Nachrichtensendeweg und Speicherformat wurden nicht geändert.
