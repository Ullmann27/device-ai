# Device AI Continuum — verbindlicher Cinema-52-Auslieferungsvertrag

Stand: 2026-11-10 · Gültig für die Besitzer-APK `0.3.51` / `versionCode 52`.
Bestätigte Produktentscheidung: **nur die neueste Cinema-Version**, nie ein älteres Standard-Cockpit als versteckte Voreinstellung.

## 1. Alle angeschlossenen Cinema-Effekte ab Start sichtbar

- `JarvisCinematicSettings.depthExperimentEnabled` verwendet **true** als Default, niemals `false`.
- `AgentActivity.onCreate` führt vor `buildUi()` die **einmalige Upgrade-Migration** `activateCinema52ByDefault(this)` aus. Dadurch werden alte persistierte OFF-Werte für Master-Cinema und die 15 angeschlossenen EXP01–EXP15 beim Upgrade auf Code52 auf **true** gesetzt.
- Das normale Cinema-52-Menü darf **keinen** Hauptknopf „Standardmodus“ haben, der die ganze entwickelte Cinema-Oberfläche auf einen älteren Zustand zurückschaltet.
- **Alle Einstiegsstellen** zählen: Auch im Avatar-/Kugel-Einstellungsdialog (`ContinuumHologramView.showAppearanceChooser`) ist „Statische Effekte · Rückfall“ verboten. Ein zweiter versteckter Master-OFF-Schalter ist ein Release-Blocker, selbst wenn das Cinema-Menü „aktiv“ meldet.
- Spätere **explizite** individuelle Einstellungen des Besitzers werden nicht bei jedem Neustart überschrieben. Neue Nutzer erhalten immer alle angeschlossenen Effekte.
- Android-Power-Save, `reducedMotion`, thermische Zustände, Hintergrund/fehlender Fokus und die vorhandene `JarvisRenderQuality`-Regelung behalten ihre Autorität. Der Sicherheitsbudgetschutz ist **nicht** das Deaktivieren des Cinema-Releases.

## 2. Entwickelte Funktionen nicht stillschweigend zurückhalten

Jede kompatible, funktionsfähige und freigegebene Arbeit aus den aktuellen Cinema-PRs ist zu übernehmen, statt einen älteren Quellstand als „Release“ auszugeben. Relevante Linien: #698 (Cinema Hauptintegrator), #699/#702 (Produkt-/QA-Reparaturen), #703 (ToolRegistry/Sicherheit), #708 (Glas/Tiefe), #710 (Auftrags-Wegflug), #711 (kompaktes Telefon-/Fold-Layout), #709 (Legacy-Inventur, nach tatsächlichem Nachweis), #707 (Originalsignier-Vorbereitung).

**Niemals blind mergen**: Status anhand echter SHAs, Produktionsaufrufe und Konflikte prüfen; fehlende Features explizit als BLOCKED mit Ursache und Nachweis dokumentieren. Kein stilles Weglassen, keine ungeprüften Behauptungen „alles integriert“.

## 3. Verbindliche sichtbare Fold7-Abnahme

Die Java2D-Grafik-Testbilder (36 Cinema-Frames) prüfen nur den portablen Renderer. Sie zeigen **keinen** echten Android-Bildschirm, Filament-Avatar, Android-TTS oder einen laufenden Live-Agenten. Sie dürfen nicht als Besitzer-Fold7-Abnahme ausgegeben werden.

Vor der endgültigen Besitzerfreigabe müssen tatsächliche Gerätetests den Start, Originalkopf + Kugel, echte filmische Fensterflug-Animationen nach einem Auftrag, reale Fortschritte, Sprache+Mund, STOP/Rückkehr, Fold-Layout, Zugriff und Wärme prüfen. Wenn nicht ausgeführt, ausdrücklich **NOT_RUN**.

## 4. Release-Disziplin

- Keine zweite App, kein anderer Paketname: `at.heinz.deviceai.continuumlocal`.
- Kein eigenmächtiger Merge nach `main`, keine unautorisierte Originalsignierung oder Veröffentlichung.
- Jeder finale Quell-SHA benötigt vollständige source-exakte Node-/Backend-/Android-/Lint-/ARM64-QA. Grüne Tests eines älteren SHA sind kein Freibrief.
- Bei Quellen- oder Assetverlust die Funktionen wiederherstellen und den Fehler testen, nicht Ersatzgrafiken oder Täuschbilder liefern.
- Keine neuen optionalen Forschungs-Features sollen den Abschluss des produktiven Cinema-Releases durch endlose Zusatzarbeit aufhalten.
