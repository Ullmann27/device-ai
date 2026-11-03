# TEST_RESULTS – continuum/grok1-integration-20261103

Kopf vor diesem Commit: `aa993c34` (Baum = `2f81e60f`): volle JUnit **1818 / 0 FAIL / 5 skip**; Node 58 Wiring-Dateien + neue Verträge 0 fail; lintDebug sauber; assembleDebug grün (auf `aa2b8f76`). Gesamtstand inkl. #372/#373 siehe unten (Branch integration/continuum-grok1-369-372-20261103).


Stand 03.11.2026, Europe/Vienna. Rechner: gemeinsamer Box-Host, JDK 17.0.21, Android SDK 36, Gradle-Wrapper 8.11.1, Node 24.22.1.
Belegstufe: `HOST_VERIFIED`. **Nicht ausgeführt:** Fold7-Gerät, native Gemma/LiteRT-LM-Inferenz, Render-QA, Signierung, GitHub Actions.

## Inhalt (nur getestete, fixe SHAs)
| Quelle | SHA |
|---|---|
| #369 m1 (Basis, enthält #365–#368, #363/#364-Inhalt über Code-39-Release `a9709854`) | `7316b1f5` |
| GROK1 Fold7-Kontext-Retention RED `b3a52209` → GREEN `e2fd08f7` | |
| GROK1 Negation de/en RED `665dfdd7` → GREEN `aa2b8f76` | |
| #371 Gedächtnis (Grok 2) | `ea7d08f1` (Merge `9981abed`) |
| #370 m2 Kontext/Zeitachse/Prompt/CCC1 | `ea785432` (Merge `980dfd8b`) |
| #371-H2 RED `4a9557ad` → GREEN `dd329e5f`; H1 RED `f756724f` → GREEN `2f81e60f` | |
| eigener Avatar-Port zurückgenommen (Koordination: Avatar nur #373) | `4b6d99f6`, `aa993c34` |
| #372 m5 Journal/Barge-in/Diktat | `a655bac8` (Merge `8aea2c44`) |
| #373 M3 + AvatarContract/AvatarDirector (HAUPT) | `26162442` |

## Ergebnisse
| Prüfung | SHA | Ergebnis |
|---|---|---|
| JUnit voll, Basis m1 | `7316b1f5` | 1752 Tests, 0 FAIL, 5 skip |
| JUnit voll, #371 allein | `ea7d08f1` | 1787, 0 FAIL, 5 skip (Angabe von Grok 2 bestätigt) |
| JUnit voll, GROK1+#371+#370 | `2f81e60f` | 1818, 0 FAIL, 5 skip |
| JUnit voll, +#372 | `8aea2c44` | 1835, 0 FAIL, 5 skip |
| **JUnit voll, +#373 (dieser Stand)** | `7185af6a` | **1856, 0 FAIL, 5 skip** |
| `:app:lintDebug` | `7185af6a` | No issues found |
| `:app:assembleDebug` | `7185af6a` | BUILD SUCCESSFUL |
| Node, alle 300+ Testdateien außerhalb docs/ | `8aea2c44` | 2065 Tests, 2050 pass, **2 fail**, 13 skip – beide Fehler (`scripts/continuum-isolated-node.test.mjs`: Linux-Namespaces) identisch auf Basis `7316b1f5` → Umgebung, keine Regression |
| Node, 58 Android-Wiring-Dateien + neue Verträge | `7185af6a` | 372 Tests, 369 pass, 0 fail, 3 skip |
| Unsignierter Release-Build (Render-Transportmodus, versionCode 40) | `aa2b8f76` | `at.heinz.deviceai.continuumlocal`, versionCode 40, versionName 0.3.39, unsigniert |

