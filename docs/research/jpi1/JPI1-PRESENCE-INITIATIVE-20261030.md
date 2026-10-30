# JPI1 · Continuum JARVIS Presence & Initiative · Forschungsübergabe

Ausgangsstand: `0f6aa264cfaf6c0490861e5ffc0a109c2d4c9914` (PR #245).
Branch: `research/jpi1-presence-shadow-20261030`.

## Was jetzt implementiert ist

- Reine Kotlin-JPI1-Zustandslogik für die vier bezeichneten Endpunkte HOME_AMBIENT, VEHICLE_HANDSFREE_AUDIO, FOLD7_MOBILE_DIRECT und PC_WORKSTATION.
- Haushalt: OWNER_AT_HOME, OWNER_AWAY_OTHERS_PRESENT, HOUSE_CONFIRMED_EMPTY, OCCUPANCY_UNKNOWN.
- Nur frische Telemetrie bei als gefährlich markierten eingeschalteten Geräten; UNKNOWN meldet Unsicherheit statt leeres Haus zu behaupten; anwesende Familie unterdrückt den spezifischen Abwesenheitsalarm.
- Unterscheidung TELEMETRY_READ_ONLY / SAFE_REMOTE_SHUTOFF_ONLY / FULL_BIDIRECTIONAL_CONTROL.
- Ablauf mit zufälliger, kurzlebiger, einmalig einlösbarer Proposal-ID, eng begrenzter expliziter Sprachbestätigung, zusätzlicher Besitzer-Authentisierung durch einen **künftigen** vertrauenswürdigen Adapter, unmittelbar erneut geprüfter Haus-/Gerätesituation und nach dem Befehl frischer, zugeordnet überprüfter Telemetrie.
- Keine globale synchrone Sperre um die Brückenaufrufe, begrenzte Pending-Menge und Cooldown gegen wiederholte Warnungen.
- Lokale SHA-256-Ergebnisprüfsumme, **keine** digitale Signatur und **kein** unabhängiger physischer Schaltbeweis.

## Der entscheidende Sicherheitsmodus

`researchSimulatedActuationEnabled=false` ist die Standardeinstellung; dann wird die Schnittstelle **nie** aufgerufen, auch nicht bei einer vermeintlichen Zustimmung.

`HomeApplianceToolBridge` ist lediglich ein Forschungsvertrag. Es existiert **kein** produktiver Home-Assistant-/SmartThings-Adapter, kein registrierter Herd-Schaltbefehl in `ToolRegistry`, keine Android-Auto-Audioausgabe, kein geräteübergreifendes Gesprächs-Handoff und kein Hintergrunddienst. Nur Unit-Tests setzen das explizite Simulationsflag und verwenden einen Mock.

Eine Antwort wie `Ja, Jarvis, mach bitte` ist **keine Authentisierung**. Bevor eine spätere reale Aktion möglich ist, müssen die vorhandenen Continuum-`ToolGate`-Rechte und ein abgesicherter Identitäts-/Sitzungskanal die konkrete Aktion genehmigen; die Freigabe darf nicht aus Modellausgaben, Sprachtexten oder selbst gemeldeten Anwesenheitszuständen übernommen werden.

Ein AUS-Sensorwert von Home Assistant/SmartThings wäre zunächst eine **Telemetriemeldung** und noch kein Beweis, dass die Herdplatte physisch kalt oder gefahrlos ist. In einem sicherheitskritischen System sind Gerätefähigkeit, Herstellerfreigaben, Sensorzuverlässigkeit und unabhängige Bestätigung vor jedem produktiven Anschluss zu prüfen. Keine beliebigen Smart Plugs an Herden als Sicherheitsabschaltung.

## Tests und Grenzen

`app/src/test/java/at/heinz/deviceai/jarvis/ContinuumJarvisPresenceEngineTest.kt` enthält 16 JUnit-4-Testfälle: Haus leer, Familie anwesend, Status unbekannt, reiner Lesemodus, SHADOW-Sperre, frischer AUS-Readback, fehlerhafte oder fremde Rückmeldung, fehlende Authentisierung, unklare/negierte Zustimmung, Replay, Ablauf, Rückkehr weiterer Haushaltsmitglieder, veraltete Telemetrie, Cooldown, vier Endpunkte und fehlgeschlagener Befehl.

**Lokale Vorprüfung:** Kotlin/JVM mit `kotlinc 1.9.0` kompiliert; die 16 Testmethoden mit einem temporären kompatiblen JUnit-Annotations-/Assert-Testtreiber ausgeführt: 16 PASS / 0 FAIL. Das ist ausdrücklich **kein** vollständiger Gradle-`testDebugUnitTest`-Lauf, keine Android-Geräteabnahme und keine authentische JUnit-Runtime-Validierung. Der zusätzliche produktive ToolGate-Integrationsweg ist nicht aktiviert.

## Für spätere echte End-to-End-Integration

1. Vertrauenswürdigen, getrennt konfigurierten Anwesenheitsaggregator mit mehreren Bewohnern und Freshness-Provenienz bereitstellen.
2. Gerätemodell und zertifizierte Fähigkeit zur Fernabschaltung beim konkreten Kochfeld aus Herstellerdokumentation und Hardwaretest verifizieren.
3. Besitzerkontrolle über bestehendes `ToolGate` einschließlich autorisierter Geräte- und Aufrufidentität integrieren; keine Sprachbestätigung als alleinige Authentisierung.
4. Sensor-Readback mit vom Kommando unabhängiger Quelle, nachvollziehbarem Zeitpunkt, Timeout, Retry-/Fehlerregel und Auditbelegen testen.
5. Android-Ereignisdienst und nur zulässige, explizit freigegebene Fahrzeug-Audioausgabe entwickeln; Hintergrund- und Android-Auto-Beschränkungen real prüfen.
6. Sicheren Handoff des **bestehenden** Continuum-Gehirns über die vier Endpunkte entwickeln, ohne eine zweite Gedächtnisdatenbank.
7. Fehler-, Offline-, Telefon-/Audio-Wechsel, Race-, Stale- und Mehrpersonenfälle auf dem Fold7 abnehmen.
8. Erst dann eine gesondert bestätigte Freigabe/PR für die produktive Geräteanbindung vorbereiten.

Kein Merge nach `main`, keine Besitzer-Schlüssel und keine echte Hardwareaktion in diesem Forschungsschritt.
