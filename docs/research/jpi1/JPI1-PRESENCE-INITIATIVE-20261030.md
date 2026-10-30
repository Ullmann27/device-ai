# Continuum JPI1 — Presence & Initiative (SHADOW-Forschungsmodul)

## Zweck

Ein bewusst **nicht produktiv angeschlossener** Präsenz- und Initiativkern für denselben bestehenden Continuum-Dialog und dieselben bestehenden ToolGate-/Besitzerrechte. Die drei Module in `app/src/main/java/at/heinz/deviceai/jarvis/` implementieren ausschließlich eine datenorientierte Entscheidungsschicht:

- `ContinuumJarvisPresenceEngine.kt` nimmt vorgeprüfte Situationsdaten entgegen, erzeugt einen begrenzten Hinweis und liefert nach einem ausdrücklich authorisierten, **extern verifizierten** Abschaltversuch einen Bericht. **Keine reale Home-Assistant-, SmartThings-, Android-Auto-, Bluetooth- oder Herdschnittstelle ist angeschlossen.**
- `JarvisSessionHandoff.kt` behält die vorhandene Sitzungs-ID beim überprüften Wechsel zwischen Zuhause, Auto, Fold7 und PC. Es überträgt **keine Gespräche**, führt **keine Identifizierung** aus und besitzt **kein eigenes Gedächtnis**.
- `JarvisInitiativePolicy.kt` legt die unverifizierte Gefahren-/Unterbrechungsheuristik fest. Nur bei explizit bestätigtem leerem Haus und aktivem, verifiziertem Gerät kommt `SPEAK_WHEN_ENDPOINT_AUTHORIZED` in Betracht. Die Ausgabe erfolgt hier nicht; ein künftiger Audioadapter muss die Endpunktfreigabe separat prüfen.

## Sicherheitsvertrag — Herdbeispiel

1. Das vorhandene System liefert einen **signierten/autorisiert abgeleiteten** Haushalts-Quorumzustand; allein ein verlassenes Heim-WLAN oder verbundene Fahrzeuglautsprecher sind kein Beweis für `HOUSE_CONFIRMED_EMPTY`. Bei unklarer Anwesenheit wird nur vorsichtig gewarnt; während der Fahrt bleibt die Warnung zunächst stumm.
2. Gerätezustand und Vertrauensstatus müssen aus frischer Telemetrie stammen. Für unverifizierte oder ältere als 30 Sekunden alte Daten wird keine Initiative erzeugt.
3. `TELEMETRY_READ_ONLY` kann **keine Fernabschaltung** anfordern. Die Produktkonfiguration darf Fernabschaltung ausschließlich für eigens dafür zugelassene und technisch geeignete Geräte freigeben.
4. Ein Sprachtranskript ist keine Eigentümeridentifizierung. Die Forschungsklasse verlangt einen externen **OwnerShutoffApprovalGate** mit beiden Freigaben (Authentifizierung UND bestehendes ToolGate), aktueller Bestätigung des leeren Hauses und frischer Sensor-Vorprüfung.
5. Ein erfolgreich gesendeter Schaltbefehl ist **kein bestätigtes AUS**. Erst ein zeitlich neuerer, auf dieselbe Befehls-ID bezogener, vom Adapter separat verifizierter Messwert `active=false` wird als `TELEMETRY_CONFIRMED_OFF` gemeldet. Der Status bedeutet **nur bestätigte Telemetrie**, nicht eine unabhängig zertifizierte physische Sicherheit.
6. Bei fehlender Freigabe, veraltetem Zustand, widersprüchlicher Bestätigung oder abgelaufener Anfrage wird die Aktion verweigert. Doppelte Anfragen werden unterdrückt; nach fünf Minuten ist bei frischen Daten eine erneute Warnung möglich.
7. SHA-256 ist lediglich eine lokale **Prüfsumme der Zusammenfassung**, keine kryptografisch authentisierte Hardwarequittung. Für belastbare Quittungen wären signierte, replay-geschützte Hardware-/Gateway-Belege erforderlich.

## Prüfdateien und Status

`app/src/test/java/at/heinz/deviceai/jarvis/` enthält **26 geschriebene JUnit-Testmethoden** für Präsenz, Textfreigabe, Eigentümer- und Werkzeugfreigabe, Familienrückkehr, falsch zugeordnete Sensorquittungen, stale data, Sperrzeiten, Endpunktwechsel und Fahr-Unterbrechungen.

**Einschränkung:** Das Anlegen der Tests auf GitHub ist **keine Ausführung**. Für diesen Forschungszweig gibt es bislang **keinen vollständig nachgewiesenen Kotlin-/Android- oder JUnit-Durchlauf**. Alle Tests müssen auf exakt dem JPI1-HEAD laufen und ihre Rohbelege veröffentlicht werden, bevor irgendeine Aktivierung erwogen werden kann.

Dieser Branch verändert **nicht** `AgentActivity.kt`, `ContinuumBrain.kt`, `LocalConversationContext.kt`, Berechtigungssysteme, Gerätepakete, Besitzerinformationen, Signierschlüssel, native Gemma-Aufrufe oder `main`.

## Nächste Arbeitspakete

1. Auf einem sauberen Worktree mit lokal verfügbarer Android-Kotlin-Toolchain alle 26 Tests und die vorhandene Android-Regression ausführen. Auftretende Fehler isoliert reparieren, committen, pushen und erneut messen.
2. Vor einer **reinen SHADOW-Beobachtung** den vorhandenen Continuum-Dialogzustand, die Besitzerrechte und die Telemetriequellen an den echten Aufrufketten nachweisen. Kein neuer Gedächnisspeicher und kein zusätzlicher Worker.
3. Einen defensiven, inhaltsfreien Geräte-Trace entwickeln: `eventType`, `reasonCode`, `sourceAge`, `endpointType`, `decision`, `receiptStatus`, aber keine Identitäts-/Standortdaten oder privaten Sprachtranskripte.
4. Haushalts-Quorum gegen falsche Anwesenheit, Netzwerkausfall, Symlinks im Diagnosepfad, Replay alter Gerätequittungen sowie Verbindungsabbrüche testen.
5. Auf dem Fold7 echte Endpunkt-/Audio- und Hintergrundwechsel mit Android-Grenzen messen. In einem Fahrzeug keine ungeprüften Audio-Unterbrechungen erzeugen.
6. Vor jeder realen Herdsteuerung konkrete Herstellerfähigkeit, sichere Zertifizierung, Besitzerfreigabe, unabhängige Gateway-Authentifizierung und Fehlermodi prüfen. **Bis dahin keine echte Fernabschaltung, auch nicht zu Demonstrationszwecken.**

### Status

Forschungs-Prototyp · isolierter Draft-Branch · vorhandenes Gehirn unverändert · keine APK · keine Geräte-/Hardware-Freigabe.
