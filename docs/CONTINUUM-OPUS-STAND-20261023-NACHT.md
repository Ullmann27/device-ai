# Continuum – Stand nach der Nacht zum 24.10.2026 (Opus übernimmt den GPT-6-Auftrag)

Heinz' Anweisung: Opus erledigt den Auftrag, der für GPT-6 vorgesehen war, selbst. Diese Datei ist der aktuelle
Einstieg; die ältere Übergabe `docs/CONTINUUM-OPUS-TO-GPT6-HANDOFF-20261023.md` bleibt als Hintergrund gültig.
Nichts wurde nach `main` oder auf den Signierzweig geschrieben, nichts veröffentlicht, installiert oder signiert.

## Zweige

| Zweig | Spitze | Zweck |
|---|---|---|
| `opus/continuum-next-20261023` | siehe `git ls-remote` (ab `c7b73dbf`) | Integrationslinie auf Sols PR #108 (`e4fd67c3`) plus Reparaturen dieser Nacht. Für den **Termux-Checkout** (CCC1-Werkzeuge) |
| `opus/continuum-0.3.18-candidate-20261023` | `421a96b7` | **APK-Kandidat 0.3.18 / code 19**, Details `docs/CONTINUUM-OPUS-0.3.18-KANDIDAT-20261023.md` auf diesem Zweig |
| `opus/ccc1-e2e-20261023` (PR #99) | `8f88cc44` | Herkunft/Review, nicht erneut mergen |

## Was diese Nacht erledigt wurde

1. **Zwei rote Tests am neuesten Stand repariert** (`a68119fb`): `runOwnedCommand` (Sols M4-Prozessverfolgung)
   verweigerte jede bestätigte Aufräummeldung, sobald irgendein gleich-UID-Prozess unlesbar war; im Container ist das
   PID 1, auf dem Telefon können es die Termux-App oder andere ältere Prozesse sein. Jetzt zählen nur Prozesse, die
   nicht vor dem gestarteten Kind begonnen haben; ein unlesbarer jüngerer Prozess blockiert weiterhin.
2. **Issue #76, Doppelfehler** (`8cef3ce2`): Kam der Stopp von Android genau während der Wiederholung eines
   fehlgeschlagenen Schreibens, wurde er als Plattenfehler (`COMMIT_FALSE`) gemeldet; jetzt `STOPPED`/`INTERRUPTED`.
   Issue #76 bleibt offen bis zur Gerätebestätigung.
3. **CCC1: Abbruch beendet auch den laufenden Prüfschritt** (`4de36706`, `02540e8d`, Beleg `c2d0126a`):
   Schrittwächter mit eigener Prozessgruppe; Checkpoint auch nach jedem Schritt. Echter Lauf über die Termux-Aufrufe:
   **6,5 s statt 38,7 s** (Schrittgrenze) bzw. 70,6 s (ohne Abbruch). Zehn Mutationen, alle erkannt.
   `docs/evidence/ccc1-step-watchdog-20261023/README.md`.
4. **CCC1: große Kandidaten-Änderungen zerstörten das Reparatur-Ledger** (`c7b73dbf`): Ein Kandidat mit mehr als
   20 000 Zeichen (z. B. Rückgängig-Machen über weite Teile einer großen Kotlin-Datei) ließ das Speichern scheitern;
   ein gescheiterter Auftrag bekam einen falschen Grund, ein bereits geprüfter Patch wurde als Fehler gemeldet.
   Jetzt nur mit Datei, Prüfsummen und Längen gespeichert und nie aus dem Ledger wiederholt.
5. **Release 0.3.18 vorbereitet** (Zweig oben): Der letzte Stand, dessen 22 freigegebene Dateien bitgleich sind
   (`b842d471`, PR #107), **wäre auf Render gescheitert**: 5 veraltete statische Node-Verdrahtungstests (alter
   Builder-Dialog), ein veralteter Kotlin-Test (M4 brachte 3 neue Operationen) und ein **echter Sicherheitsfehler**:
   „Installier das und zeig mir den Akku“ wurde als Gerätewerkzeug eingestuft (Regex verlangte „installiere/-en/-st“).
   Korrigiert (`18b6a0d6`, `21fc3f7d`, `be469760`; die beiden letzten bitgleich zu Sols Fassung). Danach bestehen alle
   v19-Prüfungen vor Gradle und alle Kotlin-Unit-Tests. **Eine Besitzerentscheidung bleibt:** `BootstrapBrain.kt`
   ist freigabepflichtig; den Fingerabdruck im Build-Skript (5ddd43cb → e0981a9a) habe ich bewusst nicht selbst geändert.

## Prüfungen (Linux-Container, Node 22, JDK 21; kein Android-SDK, `dl.google.com` gesperrt)

| Stand | Node | Backend | Kotlin ohne Gradle | Sonstiges |
|---|---|---|---|---|
| `opus/continuum-next-20261023` @ `02540e8d` | 1084/1084 (mit `kotlinc`) | 60/60 | 867/867 + 4 Prüfprogramme (am #76-Stand) | Ledger-Fix: 287/287 in 37 betroffenen Testdateien |
| Kandidat @ `be469760`, v19-Gates wörtlich | 1035 bestanden, 6 übersprungen (brauchen `kotlinc`, wie auf Render), 0 Fehler | 60/60 | 863/863 + 4 Prüfprogramme | Astra 34/34, AUTOGENESIS/CDX1 30/30, Groq 26/26; Fingerabdrücke 21/22 (s. o.) |

Nicht ausgeführt: Gradle `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, Signatur, Gerät.

## Was nur Heinz tun kann

1. **Kandidat freigeben und bauen** – genaue Schritte im Kandidatendokument: zuerst den einen Fingerabdruck für
   `BootstrapBrain.kt` freigeben, dann Signierzweig vorspulen (kein Force-Push), Render mit
   `CONTINUUM_RELEASE_SHA=<Kandidaten-SHA>`, Skript `render-mentor-owner-update-v19.sh`.
2. **Termux-Checkout** für CCC1: `opus/continuum-next-20261023` (CCC1-Protokoll v1 unverändert, die CCC1-Teile des
   Builder-Skripts sind gegenüber dem Kandidaten unverändert) oder für einen einheitlichen Gerätetest der
   Kandidatenzweig. M4-Projektquittungen von Sols neuerem Termux-Stand mit der Kandidaten-APK sind nicht am Gerät geprüft.
3. Gerät: In-place-Update, `ccc1-check --full`, ein CCC1-Auftrag mit Abbruch, Forschungszyklus ohne
   `FAILED_SAVE_CYCLE`; danach Issue #76 schließen.
4. Optional: `dl.google.com` in den erlaubten Domains der Umgebung, dann kann ich Gradle selbst ausführen.

## Als Nächstes (laufend)

- **S1/S2** (Sols Übergabe §6): heutiger Dialogkern gegen eine begrenzte Kontextroutine auf versiegelten,
  ungesehenen Mehrturn-Dialogen; vorregistriert, TEST einmal. Konkrete Lücke im Kern: Fürwörter („er“, „sie“, „es“)
  sind Stoppwörter, die Anschlusserkennung kennt nur „darüber/dazu/damit/weiter/noch mehr/und warum/und wie“.
- **S3** besteht im Kern schon (`EXPERIENCE_REPLAY`: unabhängig geprüfter Patch für denselben Diagnose-Fingerabdruck,
  nur wenn der Abschnitt noch genau einmal passt; danach Kritiker, echte Tests, unabhängiger Lauf).
