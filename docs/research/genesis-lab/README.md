# Genesis G1: unabhaengiger Messvertrag

**Oeffentliches DEV-Paket, kein blinder Benchmark, kein Modellresultat.**
Genau ein Meilenstein, vorregistriert und gepusht in
[`71ee11739f218e729876930cebc0d957f0eeb71b`](https://github.com/Ullmann27/device-ai/commit/71ee11739f218e729876930cebc0d957f0eeb71b).
Draft [#660](https://github.com/Ullmann27/device-ai/pull/660), nicht mergen.

- [Audit](AUDIT-20261108.md): Architektur, H1-H4, belegte Hindernisse, ARGON-4.
- [Protokoll](PROTOCOL-G1.md): sechs Dimensionen, Kontrollen, Leckageschutz,
  vorab definierte Erfolgskriterien und bewusst nicht gestarteter Modelltest.
- [24 Kontrastaufgaben](challenges.json): zwoelf Paare, sechs Konstrukte,
  oeffentliche Loesungen/Begruendungen und Messgrenzen.
- [Vertragstest](check_pack.py): nutzt ausschliesslich den unveraenderten
  Scorer aus PR573; implementiert keine zweite Bewertungspipeline.
- [Hashgebundener Vertragsreport](contract-result.json): acht bestandene
  Pakettests, explizit Modell/Geraet NOT_RUN.
- [Handoff](../omega/GENESIS-INDEPENDENT-HANDOFF-20261108.md): Status und Grenzen.

Paket-Freeze und verifizierter Remote-Checkpoint:
[`611ea5b75d6b671983f0c277fb6395175651872c`](https://github.com/Ullmann27/device-ai/commit/611ea5b75d6b671983f0c277fb6395175651872c).
Der Report wurde danach mit unveraenderten Aufgaben und unveraendertem
Checker erzeugt. Zusaetzlich bestaetigt: ein falscher Scorerhash und ein
bereits vorhandener Report werden mit Exitcode 2 abgelehnt; der vorhandene
Report bleibt byte-identisch. **G1-Artefakt-Gate PASS, Intelligenzgewinn
NOT_MEASURED.** Dies schliesst den einzigen Meilenstein, keinen Modellversuch.

## Reproduzieren ohne App-Aenderung

Python 3.10+ und Standardbibliothek. In einem eigenen Checkout den bestehenden
Scorer an dessen unveraenderlichem Commit bereitstellen:

```sh
git fetch origin codex/continuum-fold7-quality-lab-20261106
git worktree add --detach ../genesis-quality-snapshot \
  5a78ee19cddb1f5bd0d5b652114d3132ddc4a73f
python3 -B docs/research/genesis-lab/check_pack.py \
  --scorer ../genesis-quality-snapshot/tools/fold7_quality_lab/intelligence.py
```

`git worktree add` nur mit einem **noch nicht vorhandenen eigenen Pfad**.
Keinen fremden Worktree umschalten. Alternativ eine bereits autorisiert
vorhandene byte-identische Datei uebergeben; der Checker prueft SHA-256
`51ea7af14bb65d12ba7c4c385152bb8856c23e3c0af48fca1e8e90f740840722`
vor Import. Kein automatischer Download, kein Modellaufruf. Offline am Telefon
genuegen das Paket und diese eine Scorer-Datei; Git/ADB ist zur Auswertung
nicht erforderlich. Telefon/Termux hier **NOT_RUN**.

Optional `--output /eigener/neuer/pfad/contract.json` nutzen; Elternverzeichnis
muss bestehen, vorhandene Reports werden nie ueberschrieben.
Ein PASS bestaetigt Paketform, kontrastive Labels, Negativkontrollen,
Prompt-Export und einige formal aufzaehlbare Sollwerte, **keine Intelligenz**.
Begruendungen der Sprachfaelle sind manuell in dieser Sitzung geprueft,
nicht durch einen separaten blinden menschlichen Gutachter bestaetigt.

Prompt-only Export mit dem bestehenden Werkzeug:

```sh
python3 -B ../genesis-quality-snapshot/tools/fold7_quality_lab/intelligence.py \
  export-prompts docs/research/genesis-lab/challenges.json \
  --output /eigener/neuer/pfad/public-dev-prompts.json
```

Der Export entfernt Loesungen/Metadaten. Das Paket bleibt trotzdem
oeffentlich/DEV. Der Scorer wertet die Transportfelder `domain`, nicht die
wissenschaftlichen `dimension`-Felder aus: fuer Dimensionsberichte die
`per_case`-IDs mit dem Katalog verbinden, nicht die alten Domain-Zaehler
als sechs neue Kompetenzmessungen ausgeben. Kein Prozentwert fuer eine
Dimension ohne echten Antwortlauf.

### Warum die Aufgaben neu, aber nicht blind sind

Die 24 Texte wurden nach dem Audit hier neu geschrieben, nicht aus
H1-H4 oder dem PR573-Generator entnommen. Ihre Kriterien sind bewusst
quelleninformiert: etwa kollidierende erste zehn Zeichen, bitemporale
Gueltigkeit, Intervention vs. Konditionierung, Budgetgrenzen und Widerruf.
Dieses Wissen verhindert eine Behauptung vollstaendiger Unabhaengigkeit.

Die Aufgaben zeigen **Diagnoseformen**. Insbesondere belegen mitgelieferte
Historien kein Langzeitgedaechtnis, kurze Graphen keine Langzeitautonomie,
Regeln im Prompt kein kontinuierliches Lernen. Genau diese Trennung ist
Bestandteil des Gates. Ein spaeterer echter Test braucht den separaten
Testverwalter und die Zustands-/Provenienzregeln des Protokolls.

## H4-Artefaktaudit wiederholen

In einem eigenen unveraenderten Checkout von
`c90f020b8db929ae9a53e2cd95c39419497281b4`:

```sh
python3 -B -W error::ResourceWarning tools/continuum-cognitive-pilots/h4-audit-test.py
python3 -B tools/continuum-cognitive-pilots/h4b-score-test.py
python3 -B tools/continuum-cognitive-pilots/h4-audit.py \
  docs/evidence/h4-20261108/test/worlds.json \
  docs/evidence/h4-20261108/test/0-certificate.tsv \
  /eigener/neuer/pfad/h4-certificate-replay.json
python3 -B tools/continuum-cognitive-pilots/h4b-score.py \
  docs/evidence/h4-20261108/h4b/test/stories.json \
  docs/evidence/h4-20261108/h4b/test/0-candidate.tsv
node --test tools/continuum-virtual-fabric/*.test.mjs \
  tools/continuum-cognitive-pilots/verified-reuse-pilot.test.mjs
```

H4-A-Arme: `sequential`, `batch`, `certificate`, `uniform`, `myopic`;
H4-B1-Arme: `original`, `candidate`, `ablation-prefix3`,
`ablation-truncate-only`. Fuer Replays entsprechend andere archivierte
TSVs waehlen; nicht als neue Inferenz ausgeben. Der alte H4-A-CLI kann
Ausgabedateien ueberschreiben: deshalb immer einen neuen eigenen Ausgabepfad
verwenden. Zum Nachrechnen des einseitigen gepaarten Tests bei W Gewinnen
und L Verlusten: `sum(comb(W+L,k) for k in range(W,W+L+1))/2**(W+L)`.
H4-A gegen Sequential: W=18, L=9; H4-B1: W=83, L=0.

## Schutz

Nur dieser Forschungsordner und der eigene Handoff werden hinzugefuegt.
Kein App-/Build-/Workflow-Patch, keine neue Datenbank, kein Training,
keine dauerhafte Aufgabe und keine Berechtigungs-/Featureaktivierung.
Code51, Main und APK-Freigaben werden nicht beschrieben oder ausgefuehrt.
