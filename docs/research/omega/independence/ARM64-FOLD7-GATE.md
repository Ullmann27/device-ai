# ARM64/Fold7: getrennte Freigabe

Aktuelle ADB-Abfrage am2026-11-03: keine angeschlossenen Geräte. Android-Version,
ABI, installierte App, Thermik, Audio und Displayzustände wurden nicht gemessen.
Linux-x64-Qwen-Läufe und ein Android-Quellbuild ersetzen diese Nachweise nicht.

Der nächste Geräteschritt beginnt mit lesenden Bestandsabfragen auf dem tatsächlich
freigegebenen Gerät: Modell, Android-Version, ABI, installierte Paketversion und
bestehende Termux-/Worker-Anbindung. Keine erneute Installation oder ein anderer
Paketname als Ausweichlösung. Originalpaket:
`at.heinz.deviceai.continuumlocal`; unveränderter Zertifikat-SHA256:
`6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.

Vor tatsächlicher Dienstaktivierung auf ARM64 sind erforderlich:

1. Ein separat geprüftes ARM64-Runtime-Artefakt mit Herkunft, Lizenz und festen
   Prüfsummen. Der zugelassene x64-Hash ist kein ARM64-Nachweis.
2. Nachgewiesene Isolation des generierten Codes über den vorhandenen Executor.
   PRoot, ein Ordner oder ein Git-Worktree allein genügt nicht. Kein Shell-Fallback.
3. Tatsächlich verfügbare und passende Node-/JVM-/Testtoolchain, Ausführungsrechte,
   Besitzergrant und feste Befehle; keine vorausgesetzten Root-/AVF-Rechte.
4. Dieselben Start-/Stopp-, Crash-, Widerrufs-, Quellbindungs-, RED/GREEN- und
   unabhängigen Prüfungen auf dem Telefon, mit getrennten Artefakten.
5. RAM, Latenz, Thermal-API-Werte und Energie nur entsprechend tatsächlich
   gemessener Größen; App-Lifecycle/Doze, Offlinebetrieb und beide Displays prüfen.
6. Originalsignierung, höhere verifizierte Version und Updateinstallation erst im
   eigenen Releasegate mit erforderlicher Besitzerfreigabe.

Bis dahin: ARM64_RUNTIME=`NOT_RUN`, ANDROID_ISOLATION=`BLOCKED`,
FOLD7_VERIFIED=`NOT_RUN`, RELEASED=`NO`. Die Host-Funktion bleibt davon getrennt
implementier- und prüfbar.
