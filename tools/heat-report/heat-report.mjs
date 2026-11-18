#!/usr/bin/env node
// Host tool for the reports of Continuum's owner-started „Wärme- und Zeitmessung“
// (schema continuum-heat-measurement-v1, written by the app into the owner's Download folder).
//
//   node tools/heat-report/heat-report.mjs <bericht.json>                  one report as Markdown tables
//   node tools/heat-report/heat-report.mjs <vorher.json> <nachher.json>    before/after tables with differences
//
// It recomputes the summary from the raw runs with the same definitions as the app
// (app/src/main/java/at/heinz/deviceai/evaluation/HeatMeasurementReport.kt) and says so when the summary stored
// in the file differs. It never fills a gap: a value that was not measured is printed as „–“, a configuration that
// did not run as NOT_RUN, and anything that must not be compared as „nicht vergleichbar“ with the reason.
// No dependencies, no network.
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

export const SCHEMA = 'continuum-heat-measurement-v1';
const NOT_RUN = 'NOT_RUN', COMPLETE = 'COMPLETE', MEASURED = 'MEASURED';
const ABORTS = new Set(['STOPPED_BY_THERMAL_GUARD', 'PAUSED_BY_THERMAL_GUARD', 'TIMEOUT', 'FAILED', 'STALLED',
  'OUTPUT_LIMIT', 'CANCELLED_BY_APP']);
const ROLES = ['ERSTFRAGE', 'FOLGEFRAGE'];
const ROLE_NAMES = {ERSTFRAGE: 'Erstfrage', FOLGEFRAGE: 'Folgefrage'};
// Order and minimum change of the cost driver rule; identical to HeatReport.METRICS in the app.
const METRICS = [
  ['aborts', 1, 'Abbrüche'],
  ['thermalMaxMedian', 1, 'höchste Thermalstufe (Median je Lauf)'],
  ['headroomMaxMedian', 0.05, 'höchster Thermal-Headroom (Median je Lauf)'],
  ['batteryRiseCMedian', 0.5, 'Anstieg der Akkutemperatur in °C (Median je Lauf)'],
  ['runTotalMsMedianPercent', 10, 'Gesamtzeit je Lauf in % (Median)']];
const OUTCOMES = {COMPLETE: 'vollständig', STOPPED_BY_THERMAL_GUARD: 'vom Hitzeschutz angehalten',
  PAUSED_BY_THERMAL_GUARD: 'wegen Hitze nicht gestartet', TIMEOUT: 'Zeitlimit der Messung',
  STALLED: 'Modell lieferte keinen Text mehr', OUTPUT_LIMIT: 'Ausgabelimit erreicht',
  CANCELLED_BY_OWNER: 'vom Besitzer gestoppt', CANCELLED_BY_APP: 'von der App abgebrochen',
  NO_MODEL_CALL: 'ohne Modellaufruf beantwortet', NOT_ACCEPTED: 'Auftrag nicht angenommen', FAILED: 'fehlgeschlagen'};
const REASONS = {NOT_RUN: 'nicht gelaufen', BASELINE_MISSING: 'die Basis ist nicht gelaufen',
  START_CONDITION: 'Startbedingung in mindestens einem Lauf nicht erreicht',
  RUNS_DIFFER: 'andere Anzahl Läufe als die Basis', TURN_WITHOUT_MODEL: 'eine Frage wurde ohne Modellaufruf beantwortet',
  STOPPED_BY_OWNER: 'ein Lauf wurde vom Besitzer gestoppt',
  CONDITIONS_CHANGED: 'Laden oder Energiesparen war anders als in der Basis',
  PROMPT_SIZE_DIFFERS: 'die Eingabe an das Modell war mehr als 10 % anders groß',
  FACTOR_NOT_EFFECTIVE: 'der geänderte Faktor hat in den Läufen nicht gewirkt'};

// ── statistics (same definitions as the app) ───────────────────────────────────────────────────────────────

const round3 = value => value === null ? null : Math.round(value * 1000) / 1000;
const number = (object, key) => {
  const value = object?.[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
};
const text = (object, key) => typeof object?.[key] === 'string' && object[key] !== '' ? object[key] : null;
const list = value => Array.isArray(value) ? value.filter(item => item && typeof item === 'object') : [];
const max = values => values.length ? Math.max(...values) : null;

/** Median with the mean of the two middle values for an even count; null for an empty list. */
export function median(values) {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b), middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
}

/** Nearest-rank percentile of a 0.5 ms class histogram: the upper edge of the class, the maximum in the overflow. */
export function histogramPercentile(histogram, percent) {
  const counts = Array.isArray(histogram?.counts) ? histogram.counts : [];
  const total = counts.reduce((sum, entry) => sum + entry[1], 0) + (histogram?.overflow || 0);
  if (!total) return null;
  const rank = Math.min(total, Math.max(1, Math.ceil(percent / 100 * total)));
  let seen = 0;
  for (const [index, count] of [...counts].sort((a, b) => a[0] - b[0])) {
    seen += count;
    if (seen >= rank) return (index + 1) * histogram.bucketMs;
  }
  return histogram.maxMs;
}

const turnsOf = run => list(run.turns);
const turnValues = (run, block, key) => turnsOf(run).map(turn => number(turn[block], key)).filter(value => value !== null);
const state = (condition, key) => condition[key] === null || condition[key] === undefined ? 'unknown' : String(condition[key] === true);
const difference = (own, base) => own === null || base === null ? null : own - base;
const percent = (own, base) => own === null || base === null || base === 0 ? null : (own - base) / base * 100;

function roleRow(turns) {
  const complete = turns.filter(turn => turn.outcome === COMPLETE);
  const med = key => round3(median(complete.map(turn => number(turn, key)).filter(value => value !== null)));
  const frames = (block, key) => round3(median(complete.filter(turn => turn[block]?.status === MEASURED)
    .map(turn => number(turn[block], key)).filter(value => value !== null)));
  return {turns: turns.length, complete: complete.length,
    firstTokenMsMedian: med('firstTokenMs'), firstVisibleTextMsMedian: med('firstVisibleTextMs'),
    totalMsMedian: med('totalMs'), charsPerSecondMedian: med('charsPerSecond'),
    promptCharactersMedian: med('promptCharacters'),
    firstVisibleTextEvents: [...new Set(complete.map(turn => text(turn, 'firstVisibleTextEvent')).filter(Boolean))].sort(),
    firstVisibleTextHidden: complete.filter(turn => turn.firstVisibleTextShown === false).length,
    dialogueReusedTurns: complete.filter(turn => turn.dialogueReused === true).length,
    toolFreeTurns: complete.filter(turn => turn.toolFreeRoute === true).length,
    windowFramesMeasured: complete.filter(turn => turn.windowFrames?.status === MEASURED).length,
    windowP50MsMedian: frames('windowFrames', 'p50Ms'), windowP90MsMedian: frames('windowFrames', 'p90Ms'),
    windowP99MsMedian: frames('windowFrames', 'p99Ms'),
    windowOver2VsyncShareMedian: frames('windowFrames', 'over2VsyncShare'),
    windowFramesPerSecondMedian: frames('windowFrames', 'framesPerSecond'),
    stageFramesMeasured: complete.filter(turn => turn.stageFrames?.status === MEASURED).length,
    stageFramesPerSecondMedian: frames('stageFrames', 'deliveredPerSecond'),
    stageIntervalP50MsMedian: frames('stageFrames', 'intervalP50Ms'),
    stageIntervalP90MsMedian: frames('stageFrames', 'intervalP90Ms'),
    stageIntervalP99MsMedian: frames('stageFrames', 'intervalP99Ms')};
}

function row(id, runs) {
  const turns = runs.flatMap(turnsOf).filter(turn => turn.outcome !== NOT_RUN);
  const abortKinds = {};
  for (const outcome of turns.map(turn => turn.outcome).filter(outcome => ABORTS.has(outcome)).sort())
    abortKinds[outcome] = (abortKinds[outcome] || 0) + 1;
  const conditions = runs.map(run => run.startCondition).filter(condition => condition && typeof condition === 'object');
  const perRun = pick => runs.map(pick).filter(value => value !== null);
  const runThermal = perRun(run => max(turnValues(run, 'thermal', 'max')));
  const runHeadroom = perRun(run => max(turnValues(run, 'headroom', 'max')));
  const runRise = perRun(run => {
    const start = number(run.startCondition, 'batteryTempC'), highest = max(turnValues(run, 'batteryTempC', 'max'));
    return start !== null && highest !== null ? highest - start : null;
  });
  const runTotal = perRun(run => {
    const all = turnsOf(run);
    return all.length && all.every(turn => turn.outcome === COMPLETE && number(turn, 'totalMs') !== null)
      ? all.reduce((sum, turn) => sum + turn.totalMs, 0) : null;
  });
  return {configuration: id, runs: runs.length,
    startConditionMissed: conditions.filter(condition => condition.reached !== true).length + (runs.length - conditions.length),
    turns: turns.length, complete: turns.filter(turn => turn.outcome === COMPLETE).length,
    aborts: turns.filter(turn => ABORTS.has(turn.outcome)).length, abortKinds,
    cancelledByOwner: turns.filter(turn => turn.outcome === 'CANCELLED_BY_OWNER').length,
    withoutModelCall: turns.filter(turn => turn.outcome === 'NO_MODEL_CALL' || turn.outcome === 'NOT_ACCEPTED').length,
    thermalMax: max(runThermal), thermalMaxMedian: round3(median(runThermal)),
    headroomMaxMedian: round3(median(runHeadroom)), batteryRiseCMedian: round3(median(runRise)),
    runTotalMsMedian: round3(median(runTotal)),
    chargingStates: [...new Set(conditions.map(condition => state(condition, 'charging')))].sort(),
    powerSaveStates: [...new Set(conditions.map(condition => state(condition, 'powerSave')))].sort(),
    roles: Object.fromEntries(ROLES.map(role => [role, roleRow(turns.filter(turn => turn.role === role))]))};
}

function factorEffective(factor, runs) {
  const turns = runs.flatMap(turnsOf).filter(turn => turn.outcome === COMPLETE);
  if (factor === 'DIALOGUE_REUSE') return turns.some(turn => turn.role === 'FOLGEFRAGE' && turn.dialogueReused === true);
  if (factor === 'STAGE_OFF') return !turns.some(turn => turn.stageFrames?.status === MEASURED && (turn.stageFrames.delivered || 0) > 0);
  return true;
}

function comparison(configuration, own, baseline, runs) {
  const reasons = [];
  if (configuration.status !== 'RUN' || own.runs === 0) reasons.push('NOT_RUN');
  if (!baseline || baseline.runs === 0) reasons.push('BASELINE_MISSING');
  if (!reasons.length) {
    if (own.startConditionMissed > 0 || baseline.startConditionMissed > 0) reasons.push('START_CONDITION');
    if (own.runs !== baseline.runs) reasons.push('RUNS_DIFFER');
    if (own.withoutModelCall > 0 || baseline.withoutModelCall > 0) reasons.push('TURN_WITHOUT_MODEL');
    if (own.cancelledByOwner > 0 || baseline.cancelledByOwner > 0) reasons.push('STOPPED_BY_OWNER');
    if (JSON.stringify(own.chargingStates) !== JSON.stringify(baseline.chargingStates) ||
      JSON.stringify(own.powerSaveStates) !== JSON.stringify(baseline.powerSaveStates)) reasons.push('CONDITIONS_CHANGED');
    const size = own.roles[ROLES[0]].promptCharactersMedian, base = baseline.roles[ROLES[0]].promptCharactersMedian;
    if (size !== null && base !== null && base > 0 && Math.abs(size - base) / base > 0.10) reasons.push('PROMPT_SIZE_DIFFERS');
    if (!factorEffective(configuration.factor ?? null, runs.filter(run => run.configuration === configuration.id)))
      reasons.push('FACTOR_NOT_EFFECTIVE');
  }
  const delta = {};
  if (baseline && !reasons.includes('NOT_RUN') && !reasons.includes('BASELINE_MISSING')) {
    delta.aborts = own.aborts - baseline.aborts;
    for (const key of ['thermalMaxMedian', 'headroomMaxMedian', 'batteryRiseCMedian'])
      delta[key] = round3(difference(own[key], baseline[key]));
    delta.runTotalMsMedianPercent = round3(percent(own.runTotalMsMedian, baseline.runTotalMsMedian));
    for (const role of ROLES) {
      delta[role] = {};
      for (const key of ['firstTokenMsMedian', 'firstVisibleTextMsMedian', 'totalMsMedian'])
        delta[role][key + 'Percent'] = round3(percent(own.roles[role][key], baseline.roles[role][key]));
    }
  }
  return {configuration: configuration.id, factor: configuration.factor ?? null, comparable: reasons.length === 0, reasons, delta};
}

function costDriver(comparisons, rows, baselineId) {
  const candidates = comparisons.filter(entry => entry.comparable);
  if (!candidates.length) return {status: 'NOT_COMPARABLE'};
  const singleRuns = candidates.some(entry => rows.get(entry.configuration)?.runs === 1) || rows.get(baselineId)?.runs === 1;
  for (const [metric, threshold, metricLabel] of METRICS) {
    const measured = candidates.map(entry => [entry, number(entry.delta, metric)]).filter(pair => pair[1] !== null);
    if (!measured.length) continue;
    // The first of equally large changes, in the order of the configurations.
    const best = measured.reduce((winner, pair) => Math.abs(pair[1]) > Math.abs(winner[1]) ? pair : winner);
    if (Math.abs(best[1]) < threshold) continue;
    return {status: 'FOUND', configuration: best[0].configuration, factor: best[0].factor, metric, metricLabel,
      delta: best[1], singleRuns,
      tied: measured.filter(pair => Math.abs(pair[1]) === Math.abs(best[1])).map(pair => pair[0].configuration)};
  }
  return {status: 'NONE', singleRuns};
}

/** The summary of a report, recomputed from its raw runs. */
export function summarize(report) {
  const configurations = list(report.configurations), runs = list(report.runs);
  const rows = new Map(configurations.map(configuration =>
    [configuration.id, row(configuration.id, runs.filter(run => run.configuration === configuration.id))]));
  const baselineId = configurations.find(configuration => (configuration.factor ?? null) === null)?.id ?? null;
  const comparisons = configurations.filter(configuration => configuration.id !== baselineId)
    .map(configuration => comparison(configuration, rows.get(configuration.id), rows.get(baselineId), runs));
  return {baseline: baselineId, rows: [...rows.values()], comparisons, costDriver: costDriver(comparisons, rows, baselineId)};
}

/** Null when [left] and [right] are the same JSON (numbers by value, null like missing); else the first differing path. */
export function firstDifference(left, right, path = '$') {
  const missing = value => value === null || value === undefined;
  if (missing(left) || missing(right)) return missing(left) && missing(right) ? null : path;
  if (Array.isArray(left) && Array.isArray(right)) {
    if (left.length !== right.length) return path + '.length';
    for (let index = 0; index < left.length; index++) {
      const found = firstDifference(left[index], right[index], `${path}[${index}]`);
      if (found) return found;
    }
    return null;
  }
  if (typeof left === 'object' && typeof right === 'object') {
    for (const key of [...new Set([...Object.keys(left), ...Object.keys(right)])].sort()) {
      const found = firstDifference(left[key], right[key], `${path}.${key}`);
      if (found) return found;
    }
    return null;
  }
  if (typeof left === 'number' && typeof right === 'number') return Math.abs(left - right) <= 1e-9 ? null : path;
  return left === right ? null : path;
}

/** Reads and checks one report file; throws with a plain reason when it is not a report of this schema. */
export function load(path) {
  let report;
  try { report = JSON.parse(readFileSync(path, 'utf8')); }
  catch (error) { throw new Error(`${path}: nicht lesbar (${String(error.message).split('\n')[0]})`); }
  if (report?.schema !== SCHEMA) throw new Error(`${path}: kein Bericht des Schemas ${SCHEMA}`);
  if (!Array.isArray(report.runs) || !Array.isArray(report.configurations)) throw new Error(`${path}: Bericht ohne Läufe oder Konfigurationen`);
  return report;
}

// ── output ─────────────────────────────────────────────────────────────────────────────────────────────────

/** Whole numbers without decimals, others with up to two, decimal comma; „–“ for a value that was not measured. */
export function plain(value) {
  if (value === null || value === undefined) return '–';
  if (typeof value !== 'number') return String(value);
  const scaled = Math.round(value * 100) / 100;
  return Number.isInteger(scaled) ? String(scaled) : String(scaled).replace('.', ',');
}
const signed = value => value === null || value === undefined ? '–'
  : (value > 0 ? '+' : value < 0 ? '−' : '±') + plain(Math.abs(value));
const share = value => value === null || value === undefined ? '–' : plain(value * 100) + ' %';
const table = (head, rows) => ['| ' + head.join(' | ') + ' |', '|' + head.map(() => '---').join('|') + '|',
  ...rows.map(cells => '| ' + cells.join(' | ') + ' |')].join('\n');
const label = configuration => `${configuration.id} · ${configuration.label ?? ''}`.trim();
const abortText = entry => entry.aborts + (Object.keys(entry.abortKinds).length
  ? ' (' + Object.keys(entry.abortKinds).sort().map(kind => `${entry.abortKinds[kind]}× ${OUTCOMES[kind] ?? kind}`).join(', ') + ')' : '');
const reasonText = (reason, configuration) => reason === 'NOT_RUN' && text(configuration, 'reason')
  ? `${REASONS.NOT_RUN}: ${configuration.reason}` : REASONS[reason] ?? reason;

function headLines(report, title) {
  const device = report.device ?? {}, start = report.baselineStart ?? {}, input = report.input ?? {};
  const model = list(report.configurations).find(configuration => (configuration.factor ?? null) === null)?.model ?? device.loadedModel ?? {};
  const lines = [];
  if (report.synthetic === true) lines.push(`> **SYNTHETISCHE TESTDATEN – keine Messung.** ${report.syntheticNote ?? ''}`.trim(), '');
  lines.push(`**${title}** · ${report.size === 'VOLL' ? 'voll' : 'kurz'} · gestartet ${new Date(report.startedAtEpochMs ?? 0).toISOString()} (UTC)`,
    `- Gerät: ${plain(device.manufacturer)} ${plain(device.model)} · Android ${plain(device.androidRelease)} (SDK ${plain(device.androidSdk)}) · App ${plain(device.appVersion)} (Code ${plain(device.appVersionCode)})`,
    `- Modell der Basis: ${plain(model.id)} · Datei ${plain(model.fileName)} · ${plain(model.bytes)} Bytes · Backend ${plain(model.backend)} · CPU-Threads ${plain(model.threads)}`,
    `- ${input.note ?? 'Eingabe: unbekannt'} · Auftragssatz ${plain(input.promptSet)} (Version ${plain(input.promptSetVersion)})`,
    `- Sprachausgabe: ${device.speechOutput === true ? 'an' : device.speechOutput === false ? 'aus' : '–'} · Helligkeit: ${plain(device.brightnessMode)} (${plain(device.brightness)}) · Bildwiederholrate ${plain(device.refreshRateHz)} Hz · Anzeige ${plain(device.displayWidthPx)}×${plain(device.displayHeightPx)} px`,
    `- Startbedingung (erster Start): Thermalstufe ${plain(start.thermalStatus)} · Akku ${plain(start.batteryTempC)} °C · Headroom ${plain(start.headroom)} · lädt: ${start.charging === true ? 'ja' : start.charging === false ? 'nein' : '–'} · Energiesparen: ${start.powerSave === true ? 'ja' : start.powerSave === false ? 'nein' : '–'}`,
    `- Status: ${report.complete === true ? 'vollständig' : 'unvollständig'}${report.stop ? ` · vorzeitig beendet: ${report.stop.reason}${report.stop.message ? ' – ' + report.stop.message : ''}` : ''} · Wiederherstellung: ${report.restoreComplete === true ? 'vollständig' : report.restoreComplete === false ? 'NICHT vollständig' : 'nicht aufgezeichnet'}`);
  return lines;
}

function driverText(report, summary) {
  const driver = summary.costDriver, configurations = new Map(list(report.configurations).map(entry => [entry.id, entry]));
  if (driver.status === 'FOUND') {
    const others = driver.tied.filter(id => id !== driver.configuration);
    return `Ergebnis: ${label(configurations.get(driver.configuration) ?? {id: driver.configuration})} – ${driver.metricLabel} ${signed(driver.delta)} gegenüber der Basis.` +
      (others.length ? ` Gleich große Änderung auch bei: ${others.join(', ')}.` : '') +
      (driver.singleRuns ? ' Einzelmessung je Konfiguration: Die Streuung ist unbekannt.' : '');
  }
  return driver.status === 'NONE'
    ? 'Ergebnis: Keine vergleichbare Konfiguration erreicht eine Mindeständerung gegenüber der Basis; kein Kostentreiber benennbar.'
    : 'Ergebnis: nicht vergleichbar – keine Konfiguration ist mit der Basis vergleichbar; es wird kein Kostentreiber genannt.';
}

const RULE = 'Regel: Einzelfaktor-Konfigurationen, die mit der Basis vergleichbar sind, werden über die Mediane je Lauf verglichen; ' +
  'geprüft werden der Reihe nach Abbrüche (≥ 1), höchste Thermalstufe (≥ 1), Thermal-Headroom (≥ 0,05), Anstieg der ' +
  'Akkutemperatur (≥ 0,5 °C), Gesamtzeit je Lauf (≥ 10 %). Die erste Kennzahl mit einer Mindeständerung entscheidet.';

/** Markdown for one report. */
export function single(report) {
  const summary = summarize(report), configurations = list(report.configurations);
  const rows = new Map(summary.rows.map(entry => [entry.configuration, entry]));
  const out = [...headLines(report, 'Continuum · Wärme- und Zeitmessung'), ''];
  const stored = report.summary ? firstDifference({rows: report.summary.rows, comparisons: report.summary.comparisons, costDriver: report.summary.costDriver},
    {rows: summary.rows, comparisons: summary.comparisons, costDriver: summary.costDriver}) : null;
  if (stored) out.push(`> Hinweis: Die im Bericht gespeicherte Zusammenfassung weicht von der neu berechneten ab (${stored}). Hier steht die neu berechnete.`, '');
  out.push('### Wärme und Abbrüche je Konfiguration', '', table(
    ['Konfiguration', 'Läufe', 'Start nicht erreicht', 'Antworten vollständig', 'Abbrüche', 'Thermalstufe max',
      'Thermalstufe (Median je Lauf)', 'Headroom max (Median)', 'Akku-Anstieg °C (Median)', 'Gesamtzeit je Lauf ms (Median)'],
    configurations.map(configuration => {
      const entry = rows.get(configuration.id);
      return entry.runs === 0 ? [label(configuration), NOT_RUN, '–', '–', '–', '–', '–', '–', '–', '–']
        : [label(configuration), entry.runs, entry.startConditionMissed, `${entry.complete} von ${entry.turns}`, abortText(entry),
          plain(entry.thermalMax), plain(entry.thermalMaxMedian), plain(entry.headroomMaxMedian), plain(entry.batteryRiseCMedian),
          plain(entry.runTotalMsMedian)];
    })), '');
  for (const role of ROLES) out.push(`### Zeiten und Bildzeit · ${ROLE_NAMES[role]}`, '', table(
    ['Konfiguration', 'Antworten vollständig', 'Erstes Token ms', 'Erster sichtbarer Text ms', 'Gesamtzeit ms', 'Zeichen/s',
      'Fensterbild ms p50 / p90 / p99', 'Bilder > 2 Vsync', 'Fensterbilder/s', 'Bühnenbilder/s', 'Bühnenabstand ms p50 / p90 / p99'],
    configurations.map(configuration => {
      const part = rows.get(configuration.id).roles[role];
      return part.turns === 0 ? [configuration.id, NOT_RUN, '–', '–', '–', '–', '–', '–', '–', '–', '–']
        : [configuration.id, `${part.complete} von ${part.turns}`, plain(part.firstTokenMsMedian), plain(part.firstVisibleTextMsMedian),
          plain(part.totalMsMedian), plain(part.charsPerSecondMedian),
          `${plain(part.windowP50MsMedian)} / ${plain(part.windowP90MsMedian)} / ${plain(part.windowP99MsMedian)}`,
          share(part.windowOver2VsyncShareMedian), plain(part.windowFramesPerSecondMedian), plain(part.stageFramesPerSecondMedian),
          `${plain(part.stageIntervalP50MsMedian)} / ${plain(part.stageIntervalP90MsMedian)} / ${plain(part.stageIntervalP99MsMedian)}`];
    })), '');
  out.push(`### Vergleich mit der Basis ${summary.baseline ?? '–'} (jeweils ein Faktor)`, '', table(
    ['Konfiguration', 'Vergleichbar', 'Abbrüche Δ', 'Thermalstufe Δ', 'Headroom Δ', 'Akku-Anstieg Δ °C', 'Gesamtzeit je Lauf Δ %',
      'Erstfrage: erster sichtbarer Text Δ %', 'Hinweis'],
    summary.comparisons.map(entry => {
      const configuration = configurations.find(item => item.id === entry.configuration);
      return [label(configuration), entry.comparable ? 'ja' : 'nicht vergleichbar', signed(entry.delta.aborts),
        signed(entry.delta.thermalMaxMedian), signed(entry.delta.headroomMaxMedian), signed(entry.delta.batteryRiseCMedian),
        signed(entry.delta.runTotalMsMedianPercent), signed(entry.delta[ROLES[0]]?.firstVisibleTextMsMedianPercent),
        entry.reasons.map(reason => reasonText(reason, configuration)).join('; ') || '–'];
    })), '', 'Δ = Konfiguration minus Basis.', '', '### Größter gemessener Kostentreiber', '', RULE, '', driverText(report, summary), '');
  const interrupted = list(report.runs).flatMap(run => turnsOf(run).filter(turn => turn.outcome !== COMPLETE && turn.outcome !== NOT_RUN)
    .map(turn => `- Lauf ${run.index} · ${run.configuration} · ${run.prompt} · ${ROLE_NAMES[turn.role] ?? turn.role}: ${OUTCOMES[turn.outcome] ?? turn.outcome} nach ${plain(turn.totalMs)} ms, Thermalstufe max ${plain(turn.thermal?.max)}`));
  if (interrupted.length) out.push('### Nicht vollständig beantwortete Fragen', '', ...interrupted, '');
  return out.join('\n');
}

/** Reasons why two reports must not be compared at all, and differences the reader has to know. */
export function reportComparability(before, after) {
  const hard = [], notes = [];
  const pair = (name, left, right, target) => { if (JSON.stringify(left ?? null) !== JSON.stringify(right ?? null)) target.push(`${name}: ${plain(left)} → ${plain(right)}`); };
  pair('Umfang der Messung', before.size, after.size, hard);
  pair('Auftragssatz', `${before.input?.promptSet} v${before.input?.promptSetVersion}`, `${after.input?.promptSet} v${after.input?.promptSetVersion}`, hard);
  pair('Eingabeart', before.input?.kind, after.input?.kind, hard);
  pair('Gerät', `${before.device?.manufacturer} ${before.device?.model}`, `${after.device?.manufacturer} ${after.device?.model}`, hard);
  pair('synthetische Testdaten', before.synthetic === true, after.synthetic === true, hard);
  const startBefore = before.baselineStart ?? {}, startAfter = after.baselineStart ?? {};
  pair('Thermalstufe beim ersten Start', startBefore.thermalStatus, startAfter.thermalStatus, hard);
  const band = number(before.limits, 'batteryBandC') ?? 1, coolBefore = number(startBefore, 'batteryTempC'), coolAfter = number(startAfter, 'batteryTempC');
  if (coolBefore === null || coolAfter === null) hard.push('Akkutemperatur beim ersten Start: nicht in beiden Berichten gemessen');
  else if (Math.abs(coolBefore - coolAfter) > band) hard.push(`Akkutemperatur beim ersten Start: ${plain(coolBefore)} °C → ${plain(coolAfter)} °C (Band ±${plain(band)} °C)`);
  pair('Laden beim ersten Start', startBefore.charging, startAfter.charging, hard);
  pair('Energiesparen beim ersten Start', startBefore.powerSave, startAfter.powerSave, hard);
  pair('Sprachausgabe', before.device?.speechOutput, after.device?.speechOutput, hard);
  pair('Bühne (Command03) sichtbar', before.device?.htmlStage, after.device?.htmlStage, hard);
  pair('Bildwiederholrate (Hz)', before.device?.refreshRateHz, after.device?.refreshRateHz, notes);
  pair('Anzeige (px)', `${before.device?.displayWidthPx}×${before.device?.displayHeightPx}`, `${after.device?.displayWidthPx}×${after.device?.displayHeightPx}`, hard);
  pair('Helligkeit', `${before.device?.brightnessMode} ${before.device?.brightness}`, `${after.device?.brightnessMode} ${after.device?.brightness}`, notes);
  pair('Leistungsprofil der Bühne', before.device?.stageProfile, after.device?.stageProfile, notes);
  pair('Android', before.device?.androidRelease, after.device?.androidRelease, notes);
  pair('App-Version (Code)', before.device?.appVersionCode, after.device?.appVersionCode, notes);
  const baseModel = report => list(report.configurations).find(entry => (entry.factor ?? null) === null)?.model ?? {};
  pair('Modell der Basis (SHA-256)', baseModel(before).sha256 ?? baseModel(before).id, baseModel(after).sha256 ?? baseModel(after).id, hard);
  pair('Backend der Basis', baseModel(before).backend, baseModel(after).backend, hard);
  pair('CPU-Threads der Basis', baseModel(before).threads, baseModel(after).threads, notes);
  if (before.complete !== true) notes.push('Der Vorher-Bericht ist unvollständig.');
  if (after.complete !== true) notes.push('Der Nachher-Bericht ist unvollständig.');
  return {comparable: hard.length === 0, hard, notes};
}

/** Markdown with the before/after tables of two reports. */
export function compare(before, after) {
  const left = summarize(before), right = summarize(after), check = reportComparability(before, after);
  const rowsLeft = new Map(left.rows.map(entry => [entry.configuration, entry])), rowsRight = new Map(right.rows.map(entry => [entry.configuration, entry]));
  const configurations = [...new Map([...list(before.configurations), ...list(after.configurations)].map(entry => [entry.id, entry])).values()];
  const out = [...headLines(before, 'Vorher'), '', ...headLines(after, 'Nachher'), '', '### Vergleichbarkeit der beiden Berichte', ''];
  out.push(check.comparable ? 'Die Berichte sind nach den geprüften Bedingungen vergleichbar.'
    : '**Nicht vergleichbar.** Die Differenzen unten sind keine Aussage über eine Änderung der App:', ...check.hard.map(line => `- ${line}`));
  if (check.notes.length) out.push('', 'Zu beachten:', ...check.notes.map(line => `- ${line}`));
  // One configuration before and after: both must have run under their start condition with the same number of runs.
  const pairState = id => {
    const a = rowsLeft.get(id), b = rowsRight.get(id);
    if (!a || !b || a.runs === 0 || b.runs === 0) return NOT_RUN;
    if (!check.comparable) return 'nicht vergleichbar (Berichte)';
    if (a.startConditionMissed > 0 || b.startConditionMissed > 0) return 'nicht vergleichbar (Startbedingung)';
    if (a.runs !== b.runs) return 'nicht vergleichbar (Anzahl Läufe)';
    if (a.withoutModelCall > 0 || b.withoutModelCall > 0) return 'nicht vergleichbar (Frage ohne Modellaufruf)';
    return 'ja';
  };
  const cell = (a, b, format = plain) => `${format(a)} → ${format(b)}` + (typeof a === 'number' && typeof b === 'number' ? ` (${signed(round3(b - a))})` : '');
  const percentCell = (a, b) => `${plain(a)} → ${plain(b)}` + (typeof a === 'number' && typeof b === 'number' && a !== 0 ? ` (${signed(round3((b - a) / a * 100))} %)` : '');
  const value = (map, id, pick) => { const entry = map.get(id); return entry && entry.runs > 0 ? pick(entry) : null; };
  out.push('', '### Wärme und Abbrüche · vorher → nachher (Δ)', '', table(
    ['Konfiguration', 'Vergleichbar', 'Läufe', 'Abbrüche', 'Thermalstufe max', 'Thermalstufe (Median je Lauf)', 'Headroom max (Median)',
      'Akku-Anstieg °C (Median)', 'Gesamtzeit je Lauf ms (Median)'],
    configurations.map(configuration => {
      const id = configuration.id, both = pick => [value(rowsLeft, id, pick), value(rowsRight, id, pick)];
      return [label(configuration), pairState(id), cell(...both(entry => entry.runs)), cell(...both(entry => entry.aborts)),
        cell(...both(entry => entry.thermalMax)), cell(...both(entry => entry.thermalMaxMedian)), cell(...both(entry => entry.headroomMaxMedian)),
        cell(...both(entry => entry.batteryRiseCMedian)), percentCell(...both(entry => entry.runTotalMsMedian))];
    })));
  for (const role of ROLES) {
    out.push('', `### Zeiten und Bildzeit · ${ROLE_NAMES[role]} · vorher → nachher (Δ)`, '', table(
      ['Konfiguration', 'Vergleichbar', 'Antworten vollständig', 'Erstes Token ms', 'Erster sichtbarer Text ms', 'Gesamtzeit ms', 'Zeichen/s',
        'Fensterbild p50 ms', 'Fensterbild p90 ms', 'Fensterbild p99 ms', 'Bilder > 2 Vsync', 'Bühnenbilder/s', 'Bühnenabstand p90 ms'],
      configurations.map(configuration => {
        const id = configuration.id;
        const both = pick => [value(rowsLeft, id, entry => entry.roles[role].turns ? pick(entry.roles[role]) : null),
          value(rowsRight, id, entry => entry.roles[role].turns ? pick(entry.roles[role]) : null)];
        return [id, pairState(id), cell(...both(part => part.complete)), percentCell(...both(part => part.firstTokenMsMedian)),
          percentCell(...both(part => part.firstVisibleTextMsMedian)), percentCell(...both(part => part.totalMsMedian)),
          percentCell(...both(part => part.charsPerSecondMedian)), cell(...both(part => part.windowP50MsMedian)),
          cell(...both(part => part.windowP90MsMedian)), cell(...both(part => part.windowP99MsMedian)),
          cell(...both(part => part.windowOver2VsyncShareMedian), share).replace(/ \(.*\)$/, ''),
          cell(...both(part => part.stageFramesPerSecondMedian)), cell(...both(part => part.stageIntervalP90MsMedian))];
      })));
  }
  out.push('', '„–“: nicht gemessen oder nicht gelaufen. Δ = nachher minus vorher; bei Zeiten zusätzlich in Prozent des Vorher-Werts.',
    '', '### Größter gemessener Kostentreiber', '', RULE, '', `- Vorher: ${driverText(before, left)}`, `- Nachher: ${driverText(after, right)}`, '');
  return out.join('\n');
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const files = process.argv.slice(2);
  if (files.length < 1 || files.length > 2 || files.some(file => file.startsWith('-'))) {
    console.error('Aufruf: node tools/heat-report/heat-report.mjs <bericht.json> [<nachher.json>]');
    process.exit(2);
  }
  try {
    const reports = files.map(load);
    console.log(reports.length === 1 ? single(reports[0]) : compare(reports[0], reports[1]));
  } catch (error) {
    console.error(error.message);
    process.exit(2);
  }
}
