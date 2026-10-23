# Frischer Gesamt-Diff-Review und Nachprüfung

Read-only-Review des neuen Implementierungsstands gegen `b842d471…`, danach gezielte Nachprüfung
von `c787c0986a2dd944e41cfa08bf4ceba226148a07`. Der Reviewer änderte keine Dateien und baute keine APKs.

| Reproduzierter Befund | Korrektur | Nachweis |
|---|---|---|
| Termux `process.platform=android` übersprang Linux-Prozesskontrolle | `/proc` auf beiden Plattformen; vererbte zufällige Lauf-ID und PID-Startidentität | Android-Plattformsimulation mit abgetrenntem Prozess |
| Schnell beendeter Launcher ließ Daemon verwaisen | Lauf-ID bleibt nach Reparenting sichtbar | Echter harmloser Node-Orphan zunächst rot, danach beendet |
| ENOSPC beim Log löste unbestätigte Lease | Retain-Flag unmittelbar nach Toolrückkehr vor I/O | ENOSPC-Injektion, null Freigaben |
| Gültiges Host-Profil konnte als Termux beschriftet und Signer-Feld erfunden werden | Profil/Plattform/Architektur abgleichen; gehashte echte Signer-/Identity-Belege mit APK-Bindung | Mutierte Receipts liefern INCONCLUSIVE |
| Ausstehender Build blockierte Recovery | Eigener einmaliger projektgebundener Status-Slot | Neuer Robolectric-Test zuerst rot, dann grün; MAIN bleibt erhalten |
| Unlesbares proc/stat galt als verschwundener Prozess | Nur ENOENT/ESRCH als verschwunden; sonst keine positive Beendigungsbestätigung | EACCES zuerst rot, danach grün; direkte ChildProcess-Beendigung/Timeout weiterhin begrenzt |

Letzte Nachprüfung: kein weiterer konkreter Befund. Prozesssuite 10/10, vollständige Node-Suite 1074/1074.
Repro-Prozesse wurden beendet. Die Lauf-ID ist keine Sandbox gegen absichtlich umgehende Tools.
Android-SELinux kann Prozesssicht verhindern; dann wird sicher gesperrt, nicht Erfolg behauptet.
