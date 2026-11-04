# Status-Literal-Scan 2026-11-04

Werkzeug: `node tools/status-literal-scan/scan.mjs <repo>` (Contract-Test `.github/continuum/status-literal-scan.test.mjs`, RED 6525e625 → GREEN 0537e5f6, 3/3).
Regeln: String-Literale in `app/src/main/java/**/ui/**/*.kt` und `res/values*/strings*.xml`; Treffer = feste Zahl + Einheit (% · °C · GB · MB · tok/s · ms, auch „42 / 256 GB“), feste Zählwerte („4 Running“, „3 Agenten aktiv“), Platzhalter („Kachel 1–8“, Lorem ipsum, Platzhalter), verzerrter Video-Text (Weelther, Systemtics). Erlaubt: „—“, „nicht verfügbar“, „Quelle fehlt“, Interpolationen (`$x %`), Format-Strings (`%.1f °C`), Zahlkonstanten (dp/sp/alpha/Dauer), Kommentare. Allowlist `tools/status-literal-scan/allowlist.json` (leer; jeder Eintrag braucht `reason`).

| Stand | Treffer |
|---|---|
| continuum/m5-integration-repair @0537e5f6 | 0 |
| origin/main | 0 |
| design/jarvis-cockpit-home-20261104 @0395624f (#481) | 0 |

Gegenprobe: in #481 findet ein rohes grep nur `ui/ContinuumDashboardState.kt:158` (KDoc-Kommentar, korrekt ignoriert); kein `strings.xml` vorhanden.
Außerhalb von ui/ (nicht im Scan-Umfang, nur Info, #481): feste „20 %“-Akkuschwelle in `AgentActivity.kt:315, 3999–4000, 4021–4022` und `learning/NeuralPracticeScheduler.kt:48, 69` – das ist eine echte Regel-Schwelle, kein Live-Wert. Auf main keine solchen Treffer.
