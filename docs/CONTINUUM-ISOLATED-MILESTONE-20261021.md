# Isolierte Entwicklungsstufe – Wiederaufnahme

Basis PR #49 / `377bc05b8a8d96494f4cee7be8665fde3317f1e7`. Branch `sol/continuum-isolated-research-20261021`. EXP-00/01/02/03/04/05/06/07/08/11/12.

Ziel dieses Abschnitts: echter OS-eingeschränkter Ausführer für bestehende Testprofile, persistente Wiederaufnahme im bestehenden Teamleiter, tatsächliche GGUF-Inferenz soweit die Linux-Hardware reicht, drei getrennte Forschungsprototypen mit festgelegten Holdouts. Keine Änderungen an Main, vorhandenen Besitzerdateien oder aktiven neuronalen Gewichten. Restquote unbekannt; kleine Funktionsstände werden regelmäßig gesichert.

Isolation: unveränderliche Quellkopie ohne .git, keine Besitzer-/Credentialverzeichnisse; unprivilegierter Prozess, eigene PID-/Netzwerk-/IPC-Namensräume, keine Fähigkeiten/Privilegienerhöhung, schreibgeschützter Root, begrenzte CPU/RAM/PIDs/Laufzeit und nur private Ausgabeverzeichnisse. Provisionierung von Toolchains/Abhängigkeiten bleibt getrennt von netzloser Kandidatenausführung. Git-Worktree bleibt Versionsisolation, kein OS-Sicherheitsbeweis. Fehlende Android-Fähigkeit -> UNAVAILABLE, keine unsichere Termux-/proot-Ersatzbehauptung.

Parallel klar getrennte Arbeitsbereiche: Root Ausführungsadapter/Integration; vorhandene Worker-Schleife; GGUF-/Kontextmodule; drei reporting-only Experimente. Keiner legt eine zweite Aufgabenverwaltung an. Noch keine neue Freigabe; technische Belege werden nach echten Läufen ergänzt.
