// node --test tools/heat-report/heat-report.test.mjs
//
// Every report used here is a SYNTHETIC fixture (fixtures/SYNTHETIC-*.json): written by the app's own protocol and
// report code from a scripted phone in a host test (HeatSyntheticScenarios). None of the numbers was measured on a
// device and none is a typical value. The app-side test HeatMeasurementReportTest keeps the fixtures identical to
// what the app writes, so the summary stored in them is the app's own computation.
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {mkdtempSync, readdirSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import test from 'node:test';
import {compare, firstDifference, histogramPercentile, load, median, plain, reportComparability, single, summarize}
  from './heat-report.mjs';

const here = resolve(fileURLToPath(new URL('.', import.meta.url)));
const tool = join(here, 'heat-report.mjs');
const fixture = name => join(here, 'fixtures', name);
const before = () => load(fixture('SYNTHETIC-heat-report-before.json'));
const after = () => load(fixture('SYNTHETIC-heat-report-after.json'));
const runsOf = (report, id) => report.runs.filter(run => run.configuration === id);
const rowOf = (report, id) => summarize(report).rows.find(row => row.configuration === id);
const comparisonOf = (report, id) => summarize(report).comparisons.find(entry => entry.configuration === id);

test('every fixture is named and marked as synthetic', () => {
  const names = readdirSync(join(here, 'fixtures'));
  assert.deepEqual(names.sort(), ['SYNTHETIC-heat-report-after.json', 'SYNTHETIC-heat-report-before.json']);
  for (const name of names) {
    const report = load(fixture(name));
    assert.equal(report.synthetic, true, name);
    assert.match(report.syntheticNote, /SYNTHETISCHE TESTDATEN/);
    assert.equal(report.device.manufacturer, 'SYNTHETIC');
    assert.equal(report.input.note, 'Eingabe: identischer Text an der Sprachübergabe, kein Mikrofon');
  }
});

test('the recomputed summary is the one the app stored: same statistics on host and phone', () => {
  for (const report of [before(), after()]) {
    const mine = summarize(report);
    for (const part of ['rows', 'comparisons', 'costDriver'])
      assert.equal(firstDifference(report.summary[part], mine[part]), null, part);
    assert.equal(mine.baseline, report.summary.baseline);
  }
});

test('median and histogram percentiles follow the stated definitions', () => {
  assert.equal(median([]), null);
  assert.equal(median([5, 1, 3]), 3);
  assert.equal(median([4, 1, 3, 2]), 2.5);
  const histogram = {bucketMs: 0.5, bucketCount: 300, counts: [[16, 2], [18, 1], [33, 1]], overflow: 0, maxMs: 16.9};
  assert.equal(histogramPercentile(histogram, 50), 8.5, 'upper edge of the class that holds the rank');
  assert.equal(histogramPercentile(histogram, 99), 17);
  assert.equal(histogramPercentile({...histogram, overflow: 1, maxMs: 412.3}, 99), 412.3, 'the measured maximum beyond 150 ms');
  assert.equal(histogramPercentile({bucketMs: 0.5, counts: [], overflow: 0}, 50), null);
  // The percentiles the app stored for a turn are the ones this tool derives from the turn's raw histogram.
  const frames = before().runs[0].turns[0].windowFrames;
  assert.equal(histogramPercentile(frames.histogram, 50), frames.p50Ms);
  assert.equal(histogramPercentile(frames.histogram, 90), frames.p90Ms);
  assert.equal(histogramPercentile(frames.histogram, 99), frames.p99Ms);
  assert.deepEqual([plain(6000), plain(26.667), plain(0.5), plain(null)], ['6000', '26,67', '0,5', '–']);
});

test('one report: tables per configuration, NOT_RUN, interruptions and the cost driver by its rule', () => {
  const text = single(before());
  for (const expected of [
    '> **SYNTHETISCHE TESTDATEN – keine Messung.**',
    'Eingabe: identischer Text an der Sprachübergabe, kein Mikrofon',
    '| A · Basis: Bühne ruht beim Rechnen, Gemma 4 E4B, Gespräch nicht behalten | 1 | 0 | 2 von 2 | 0 | 1 | 1 | 0,42 | 1,2 | 12000 |',
    '| H · Auftrag mit allen Werkzeugschemas (wie Code68 bei gesprochenen Erklärfragen) | 1 | 0 | 0 von 1 | 1 (1× vom Hitzeschutz angehalten) | 3 | 3 |',
    '| C · Bühne mit voller Bewegung beim Rechnen (wie Code67) | NOT_RUN | – |',
    '| F · GPU-Beschleuniger statt CPU | NOT_RUN | – |',
    '| A | 1 von 1 | 1500 | 1500 | 6000 | 26,67 | 8,5 / 17 / 17 | 0 % |',
    '| G | 1 von 1 | 1500 | 6000 | 6000 | 26,67 | 8,5 / 17 / 17 | 0 % |',
    '| B · Bühne aus (Seite unsichtbar) | ja | ±0 | −1 |',
    '| G · Bühne ohne Antwortentwurf (wie Code68) | ja | ±0 | ±0 | ±0 | ±0 | ±0 | ±0 | +300 | – |',
    '| F · GPU-Beschleuniger statt CPU | nicht vergleichbar | – | – | – | – | – | – | – | nicht gelaufen: Synthetisch: GPU wird nicht geschaltet. |',
    'nicht gelaufen: Nicht Teil der Kurzmessung: die volle Bühnenbewegung von Code67 wird nur in der vollen Messung gemessen.',
    '### Gegenüber Code68: G und H gegen die Basis A',
    '| Erstfrage: erster sichtbarer Text ms | 1500 | 6000 | +4500 (+300 %) |',
    '| Erstfrage: Ereignis des ersten sichtbaren Texts | ANSWER_DRAFT_HANDED_TO_STAGE_PAGE | ANSWER_HANDED_TO_STAGE_PAGE |',
    '| Erstfrage: erstes Token ms | 1500 | – | – |',
    '| Abbrüche | 0 | 1 | +1 |',
    '| Thermalstufe max (Median je Lauf) | 1 | 3 | +2 |',
    'die Eingabe an das Modell ist hier absichtlich anders (Prompttext der Erstfrage nicht gemessen, dazu alle Werkzeugschemas)',
    'Ergebnis: H · Auftrag mit allen Werkzeugschemas (wie Code68 bei gesprochenen Erklärfragen) – Abbrüche +1 gegenüber der Basis.',
    'Einzelmessung je Konfiguration',
    '- Lauf 5 · H · kurz · Erstfrage: vom Hitzeschutz angehalten nach 3000 ms, Thermalstufe max 3'])
    assert.ok(text.includes(expected), `missing: ${expected}\n\n${text}`);
  assert.ok(!text.includes('weicht von der neu berechneten ab'));
  assert.ok(!/\bnull\b|undefined|NaN/.test(text), text);
  const second = single(after());
  for (const expected of [
    'Ergebnis: H · Auftrag mit allen Werkzeugschemas (wie Code68 bei gesprochenen Erklärfragen) – höchste Thermalstufe (Median je Lauf) +1 gegenüber der Basis.',
    '| Erstfrage: erster sichtbarer Text ms | 1200 | 5000 | +3800 (+316,67 %) |',
    '| Erstfrage: erstes Token ms | 1200 | 3200 | +2000 (+166,67 %) |',
    '| Erstfrage: Gesamtzeit ms | 5000 | 7400 | +2400 (+48 %) |',
    '| Erstfrage: Prompttext an das Modell (Zeichen) | 900 | 1040 | +140 (+15,56 %) |',
    '| Erstfrage: ohne Werkzeugschemas an das Modell (vollständige Antworten) | 1 von 1 | 0 von 1 |',
    '| Thermalstufe max (Median je Lauf) | 1 | 2 | +1 |',
    'die Eingabe an das Modell ist hier absichtlich anders (Prompttext der Erstfrage +15,56 %, dazu alle Werkzeugschemas); die 10-%-Regel gilt für diese Konfiguration nicht'])
    assert.ok(second.includes(expected), `missing: ${expected}\n\n${second}`);
  assert.ok(!/\bnull\b|undefined|NaN/.test(second), second);
});

test('G and H are compared with the baseline on their own terms', () => {
  // H: the prompt is the factor. More than 10 % difference is the thing measured, not a reason against it.
  const report = after();
  assert.deepEqual(comparisonOf(report, 'H'), {configuration: 'H', factor: 'TOOL_SCHEMAS_IN_PROMPT', comparable: true, reasons: [],
    delta: comparisonOf(report, 'H').delta});
  assert.equal(comparisonOf(report, 'H').delta.ERSTFRAGE.promptCharactersMedianPercent, 15.556);
  assert.deepEqual([rowOf(report, 'A').roles.ERSTFRAGE.toolFreeTurns, rowOf(report, 'H').roles.ERSTFRAGE.toolFreeTurns], [1, 0]);
  // The same size difference anywhere else stays a reason against the comparison.
  runsOf(report, 'B')[0].turns[0].promptCharacters = 1040;
  assert.deepEqual(comparisonOf(report, 'B').reasons, ['PROMPT_SIZE_DIFFERS']);
  // A run of H that went to the model as a plain text question did not measure H.
  runsOf(report, 'H')[0].turns[1].toolFreeRoute = true;
  assert.deepEqual(comparisonOf(report, 'H').reasons, ['FACTOR_NOT_EFFECTIVE']);
  assert.ok(single(report).includes('Nicht vergleichbar mit A: der geänderte Faktor hat in den Läufen nicht gewirkt. Die gemessenen Werte stehen trotzdem hier.'));

  // G: no draft on the stage; the first visible text is the finished answer.
  const draft = before();
  assert.deepEqual([comparisonOf(draft, 'G').comparable, comparisonOf(draft, 'G').delta.ERSTFRAGE.firstVisibleTextMsMedianPercent], [true, 300]);
  assert.deepEqual(rowOf(draft, 'G').roles.ERSTFRAGE.firstVisibleTextEvents, ['ANSWER_HANDED_TO_STAGE_PAGE']);
  assert.deepEqual(rowOf(draft, 'A').roles.ERSTFRAGE.firstVisibleTextEvents, ['ANSWER_DRAFT_HANDED_TO_STAGE_PAGE']);
  runsOf(draft, 'G')[0].turns[0].firstVisibleTextEvent = 'ANSWER_DRAFT_HANDED_TO_STAGE_PAGE';
  assert.deepEqual(comparisonOf(draft, 'G').reasons, ['FACTOR_NOT_EFFECTIVE']);
  // A configuration the short size leaves out says so.
  assert.deepEqual(comparisonOf(before(), 'C').reasons, ['NOT_RUN']);
  assert.equal(rowOf(before(), 'C').runs, 0);
});

test('a stored summary that does not follow from the runs is pointed out and not used', () => {
  const report = before();
  report.summary.rows[0].aborts = 7;
  const text = single(report);
  assert.match(text, /gespeicherte Zusammenfassung weicht von der neu berechneten ab \(\$\.rows\[0\]\.aborts\)/);
  assert.ok(text.includes('| 2 von 2 | 0 | 1 | 1 | 0,42 | 1,2 | 12000 |'), 'the recomputed value is printed');
});

test('two reports: before/after cells with differences', () => {
  const text = compare(before(), after());
  for (const expected of [
    '**Vorher** · kurz', '**Nachher** · kurz',
    'Die Berichte sind nach den geprüften Bedingungen vergleichbar.',
    '| A · Basis: Bühne ruht beim Rechnen, Gemma 4 E4B, Gespräch nicht behalten | ja | 1 → 1 (±0) | 0 → 0 (±0) | 1 → 1 (±0) | 1 → 1 (±0) | 0,42 → 0,38 (−0,04) | 1,2 → 0,8 (−0,4) | 12000 → 10000 (−16,67 %) |',
    '| H · Auftrag mit allen Werkzeugschemas (wie Code68 bei gesprochenen Erklärfragen) | ja | 1 → 1 (±0) | 1 → 0 (−1) | 3 → 2 (−1) |',
    '| C · Bühne mit voller Bewegung beim Rechnen (wie Code67) | NOT_RUN | – → – |',
    '| F · GPU-Beschleuniger statt CPU | NOT_RUN | – → – |',
    '| D | ja | 1 → 1 (±0) | 400 → 300 (−25 %) | 400 → 300 (−25 %) | 3000 → 2600 (−13,33 %) |',
    '| G | ja | 1 → 1 (±0) | 1500 → 1200 (−20 %) | 6000 → 5000 (−16,67 %) | 6000 → 5000 (−16,67 %) |',
    '| H | ja | 0 → 1 (+1) | – → 3200 | – → 3200 | – → 7400 |',
    '- Vorher: Ergebnis: H · Auftrag mit allen Werkzeugschemas (wie Code68 bei gesprochenen Erklärfragen) – Abbrüche +1',
    '- Nachher: Ergebnis: H · Auftrag mit allen Werkzeugschemas (wie Code68 bei gesprochenen Erklärfragen) – höchste Thermalstufe (Median je Lauf) +1'])
    assert.ok(text.includes(expected), `missing: ${expected}\n\n${text}`);
  assert.ok(!/\bnull\b|undefined|NaN/.test(text), text);
});

test('differing start conditions, orders or devices make two reports not comparable', () => {
  assert.deepEqual(reportComparability(before(), after()), {comparable: true, hard: [], notes: []});
  const warm = after();
  warm.baselineStart.batteryTempC = 33.4;
  warm.baselineStart.thermalStatus = 1;
  let check = reportComparability(before(), warm);
  assert.equal(check.comparable, false);
  assert.deepEqual(check.hard, ['Thermalstufe beim ersten Start: 0 → 1',
    'Akkutemperatur beim ersten Start: 30 °C → 33,4 °C (Band ±1 °C)']);
  const text = compare(before(), warm);
  assert.ok(text.includes('**Nicht vergleichbar.**'), text);
  assert.ok(text.includes('| A · Basis: Bühne ruht beim Rechnen, Gemma 4 E4B, Gespräch nicht behalten | nicht vergleichbar (Berichte) |'), text);

  const other = after();
  other.input.promptSetVersion = 2;
  other.size = 'VOLL';
  other.device.speechOutput = true;
  other.device.appVersionCode = 70;
  other.configurations[0].model.sha256 = 'f'.repeat(64);
  other.complete = false;
  check = reportComparability(before(), other);
  assert.deepEqual(check.hard, ['Umfang der Messung: KURZ → VOLL', 'Auftragssatz: continuum-heat-v1 v1 → continuum-heat-v1 v2',
    'Sprachausgabe: false → true', 'Modell der Basis (SHA-256): gemma4-e4b-it → ' + 'f'.repeat(64)]);
  assert.deepEqual(check.notes, ['App-Version (Code): 0 → 70', 'Der Nachher-Bericht ist unvollständig.'],
    'a new app version is expected in a before/after comparison; it is named, not a reason against it');

  const real = after();
  delete real.synthetic;
  assert.ok(reportComparability(before(), real).hard.includes('synthetische Testdaten: true → false'),
    'synthetic test data is never compared with a real measurement');
  const unmeasured = after();
  unmeasured.baselineStart.batteryTempC = null;
  assert.ok(reportComparability(before(), unmeasured).hard.includes('Akkutemperatur beim ersten Start: nicht in beiden Berichten gemessen'));
});

test('one configuration is compared only when it ran under its start condition in both reports', () => {
  const flagged = after();
  runsOf(flagged, 'B')[0].startCondition.reached = false;
  flagged.runs = flagged.runs.filter(run => run.configuration !== 'E');
  runsOf(flagged, 'D')[0].turns[1].outcome = 'NO_MODEL_CALL';
  const text = compare(before(), flagged);
  assert.ok(text.includes('| B · Bühne aus (Seite unsichtbar) | nicht vergleichbar (Startbedingung) |'), text);
  assert.ok(text.includes('| E · Gemma 4 E2B statt E4B | NOT_RUN | 1 → – |'), text);
  assert.ok(text.includes('| D · Gespräch im Modell behalten | nicht vergleichbar (Frage ohne Modellaufruf) |'), text);
  assert.ok(text.includes('| A · Basis: Bühne ruht beim Rechnen, Gemma 4 E4B, Gespräch nicht behalten | ja |'), text);
});

test('inside one report the same reasons against a comparison as in the app', () => {
  const report = before();
  runsOf(report, 'A')[0].startCondition.reached = false;
  assert.deepEqual(['B', 'D', 'E', 'G', 'H'].map(id => comparisonOf(report, id).reasons), Array(5).fill(['START_CONDITION']));
  assert.deepEqual(summarize(report).costDriver, {status: 'NOT_COMPARABLE'});
  assert.ok(single(report).includes('Ergebnis: nicht vergleichbar'));

  const ineffective = before();
  for (const turn of runsOf(ineffective, 'D')[0].turns) turn.dialogueReused = false;
  for (const turn of runsOf(ineffective, 'B')[0].turns) turn.stageFrames.delivered = 9;
  assert.deepEqual(comparisonOf(ineffective, 'D').reasons, ['FACTOR_NOT_EFFECTIVE']);
  assert.deepEqual(comparisonOf(ineffective, 'B').reasons, ['FACTOR_NOT_EFFECTIVE']);
  assert.ok(single(ineffective).includes('der geänderte Faktor hat in den Läufen nicht gewirkt'));

  const equal = before();
  const baseline = runsOf(equal, 'A')[0];
  equal.runs = ['A', 'B', 'D', 'G', 'H', 'E'].map(id => {
    const copy = structuredClone(baseline);
    copy.configuration = id;
    for (const turn of copy.turns) {
      if (id === 'B') turn.stageFrames.delivered = 0;
      if (id === 'D') turn.dialogueReused = true;
      if (id === 'G') turn.firstVisibleTextEvent = 'ANSWER_HANDED_TO_STAGE_PAGE';
      if (id === 'H') turn.toolFreeRoute = false;
    }
    return copy;
  });
  assert.equal(summarize(equal).costDriver.status, 'NONE');
  assert.equal(rowOf(equal, 'H').runTotalMsMedian, 12000);
});

test('the command line prints Markdown and refuses what is not a report', () => {
  const one = spawnSync(process.execPath, [tool, fixture('SYNTHETIC-heat-report-before.json')], {encoding: 'utf8'});
  assert.equal(one.status, 0, one.stderr);
  assert.match(one.stdout, /### Wärme und Abbrüche je Konfiguration/);
  const two = spawnSync(process.execPath, [tool, fixture('SYNTHETIC-heat-report-before.json'),
    fixture('SYNTHETIC-heat-report-after.json')], {encoding: 'utf8'});
  assert.equal(two.status, 0, two.stderr);
  assert.match(two.stdout, /### Wärme und Abbrüche · vorher → nachher \(Δ\)/);
  assert.equal(spawnSync(process.execPath, [tool], {encoding: 'utf8'}).status, 2);
  const directory = mkdtempSync(join(tmpdir(), 'heat-report-test-'));
  try {
    const wrong = join(directory, 'other.json'), broken = join(directory, 'broken.json');
    writeFileSync(wrong, JSON.stringify({schema: 'continuum-model-eval-report-v1'}));
    writeFileSync(broken, '{"schema":');
    for (const path of [wrong, broken, join(directory, 'missing.json')]) {
      const result = spawnSync(process.execPath, [tool, path], {encoding: 'utf8'});
      assert.equal(result.status, 2, path);
      assert.equal(result.stdout, '');
      assert.ok(result.stderr.includes(path));
    }
    assert.throws(() => load(wrong), /kein Bericht des Schemas continuum-heat-measurement-v1/);
  } finally { rmSync(directory, {recursive: true, force: true}); }
});
