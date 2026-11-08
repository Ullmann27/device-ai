# Genesis Independent Intelligence: Handoff

Status: RESEARCH_SHADOW. Eigener Branch
`research/continuum-genesis-independent-audit-20261108`,
Basis Code50-Staging `473f79727e4b52509cc7d1af71e59b80ab9b6be5`.
Kein Produktivcode, Gewichtsupdate oder zweiter Gedaechtnisspeicher.

## Einstieg

1. [Schreibgeschuetztes Audit](../genesis-lab/AUDIT-20261108.md):
   genaue SHAs, Architektur, H1-H4, negative Resultate und ARGON-4-Grenzen.
2. [G1-Vorregistrierung](../genesis-lab/PROTOCOL-G1.md):
   einziges neues Arbeitspaket; vor der Aufgabenkonstruktion sichern.

## Checkpoint 1: Vorregistrierung

Audit abgeschlossen. H4-Artefaktreplay bestaetigt H4-A FAIL und den
begrenzten H4-B1-PASS; keine neue Kotlin-/Modell-/Geraeteausfuehrung.
Bestehende H3/Broker-Tests 55 PASS, H4-A-Auditor 6 PASS,
H4-B1-Scorer 14 Kontrollen PASS, PR573-Scorer 11 PASS.
Siehe Audit fuer genaue Interpretation und NOT_RUN.

Commit vor der Aufgabenkonstruktion:
[`71ee11739f218e729876930cebc0d957f0eeb71b`](https://github.com/Ullmann27/device-ai/commit/71ee11739f218e729876930cebc0d957f0eeb71b).
Damals Aufgaben/Paketpruefung NOT_RUN; dieser historische Zustand wird nicht
nachtraeglich als schon bestanden ausgegeben.

## Checkpoint 2: oeffentliches G1-Paket

[Anleitung und Grenzen](../genesis-lab/README.md).
24 neue DEV-Aufgaben, zwoelf Kontrastpaare, sechs Konstrukte.
Keine H1-H4- oder PR573-Aufgabe kopiert; quelleninformiert statt blind.
Unveraenderter PR573-Scorer, vor Import durch SHA-256 gebunden.

Tatsaechlicher Befehl, Python 3.12.3:

```sh
python3 -B docs/research/genesis-lab/check_pack.py \
  --scorer ../device-ai-quality/tools/fold7_quality_lab/intelligence.py
```

**8/8 Vertragstests PASS**, 0 Fehler/Skips. Kontrolliert werden Struktur,
Paare, Exportgrenze, synthetische Antworten, fehlende/falsche/vertauschte
Antworten, Feld-/Typmutationen, fremde/doppelte IDs, Integritaet und formal
aufzaehlbare Loesungen. Kein Modell-, Geraete- oder Lernlauf.
24/24 Sollantworten sind ausdruecklich nur `synthetic_selftest`.

Suite-ID:
`10301f1b7f840a82bab891b1e6249a1761cc3a7fe697630fa2fcdbd81dbc4124`.
Nach dem Commit dieses Pakets wird einmal mit identischen Quellen ein
hashgebundener Vertragsreport gesichert. Danach stoppen, kein G2.

## Schutz und Rueckfall

SOL integriert, Astra/Fable bearbeiten ihre bestehenden Oberflaechen- und
Produktpfade; H1-H4 bleiben bei den vorhandenen PRs. Dieses Paket ist nur
Evaluation und Dokumentation. Code51-Kandidat, Main, APK/Signierung,
Workflows, vorhandene Experimente und Besitzer-Memory bleiben unveraendert.
Rollback: diesen Forschungsbranch nicht integrieren; keine Migration,
kein Laufzeitflag und kein neuer Dienst rueckgaengig zu machen.
