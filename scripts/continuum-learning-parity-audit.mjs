/** Read-only differential audit of *reported* Continuum learning states.
 * No Android data access, training, imports, network calls or state mutation.
 * Missing readings remain null and cannot be presented as successful transfer.
 */
import {readFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';

const OLD = 'at.heinz.deviceai.mentorpaired';
const NEW = 'at.heinz.deviceai.continuumlocal';
const HASH = /^[a-f0-9]{64}$/;
function optionalInt(value, label) {
  if (value === null || value === undefined) return null;
  if (!Number.isSafeInteger(value) || value < 0) throw Error('INVALID_' + label);
  return value;
}
function optionalBool(value, label) {
  if (value === null || value === undefined) return null;
  if (typeof value !== 'boolean') throw Error('INVALID_' + label);
  return value;
}
function optionalHash(value, label) {
  if (value === null || value === undefined) return null;
  if (typeof value !== 'string' || !HASH.test(value)) throw Error('INVALID_' + label);
  return value;
}
function holdout(value, label) {
  if (value == null) return null;
  if (typeof value !== 'object' || Array.isArray(value)) throw Error('INVALID_' + label);
  const correct = optionalInt(value.correct, label + '_CORRECT');
  const total = optionalInt(value.total, label + '_TOTAL');
  if (correct === null || total === null || !total || correct > total) throw Error('INVALID_' + label);
  return {correct, total};
}
function normalize(value, id) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || value.appId !== id)
    throw Error('APP_ID_MISMATCH');
  const study = value.publicStudy ?? {};
  if (!study || typeof study !== 'object' || Array.isArray(study)) throw Error('INVALID_STUDY');
  const benchmarkId = value.benchmarkId ?? null;
  if (benchmarkId !== null && (typeof benchmarkId !== 'string' || !/^[A-Za-z0-9_.-]{4,80}$/.test(benchmarkId)))
    throw Error('INVALID_BENCHMARK_ID');
  return Object.freeze({
    appId: id,
    swb1Sha256: optionalHash(value.swb1Sha256, 'SWB1_SHA'),
    swb1Updates: optionalInt(value.swb1Updates, 'SWB1_UPDATES'),
    archiveSources: optionalInt(value.archiveSources, 'ARCHIVE_SOURCES'),
    learningEventsToday: optionalInt(value.learningEventsToday, 'EVENTS_TODAY'),
    benchmarkId,
    intentHoldout: holdout(value.intentHoldout, 'INTENT_HOLDOUT'),
    semanticHoldout: holdout(value.semanticHoldout, 'SEMANTIC_HOLDOUT'),
    publicStudy: Object.freeze({enabled:optionalBool(study.enabled,'STUDY_ENABLED'),
      registered:optionalBool(study.registered,'STUDY_REGISTERED'),
      accepted:optionalInt(study.accepted,'STUDY_ACCEPTED')})
  });
}
export function compareContinuumLearningStates(previous, current) {
  const old = normalize(previous, OLD);
  const next = normalize(current, NEW);
  const sameWeights = old.swb1Sha256 && next.swb1Sha256
    ? old.swb1Sha256 === next.swb1Sha256 : null;
  const comparable = old.benchmarkId !== null && old.benchmarkId === next.benchmarkId &&
    old.intentHoldout !== null && next.intentHoldout !== null &&
    old.semanticHoldout !== null && next.semanticHoldout !== null &&
    old.intentHoldout.total === next.intentHoldout.total &&
    old.semanticHoldout.total === next.semanticHoldout.total;
  const previousScore = comparable ? old.intentHoldout.correct + old.semanticHoldout.correct : null;
  const currentScore = comparable ? next.intentHoldout.correct + next.semanticHoldout.correct : null;
  return Object.freeze({
    schema:'continuum-learning-parity-readonly-v1',
    comparedAppIds:[OLD, NEW],
    swb1WeightHashEqual:sameWeights,
    swb1UpdateDifference:old.swb1Updates !== null && next.swb1Updates !== null
      ? next.swb1Updates - old.swb1Updates : null,
    archiveSourceDifference:old.archiveSources !== null && next.archiveSources !== null
      ? next.archiveSources - old.archiveSources : null,
    todayEventDifference:old.learningEventsToday !== null && next.learningEventsToday !== null
      ? next.learningEventsToday - old.learningEventsToday : null,
    researchEnabledOld:old.publicStudy.enabled,
    researchEnabledNew:next.publicStudy.enabled,
    researchScheduledNew:next.publicStudy.registered,
    holdoutComparable:comparable,
    holdoutCorrectDifference:comparable ? currentScore - previousScore : null,
    verifiedModelWeightMigration:sameWeights === true,
    verifiedFullMigration:false,
    verifiedGemmaInference:false,
    interpretation:Object.freeze([
      'Updatezahlen des SWB1-Encoders messen keine Gemma-4-Parameter oder allgemeine Sprachintelligenz.',
      'Archivierte Quellen sind nicht gleichbedeutend mit migrierten SWB1-Gewichten oder Experience-Protokollen.',
      sameWeights === null ? 'SWB1-Modellhash nicht in beiden Apps gemessen; Gewichtsgleichheit unbestimmt.'
        : sameWeights ? 'SWB1-Modellhash identisch; weitere Speicherbereiche weiterhin unbestimmt.'
          : 'SWB1-Modellhash unterscheidet sich; keinen fremden Checkpoint ohne Holdout-Freigabe erzwingen.',
      comparable ? 'Gleiche ausdrücklich bestätigte Benchmark-Identität und Fallzahl; Korrektzahlen verglichen.'
        : 'Unabhängige Qualität nicht vergleichbar: Benchmark-Identität oder vollständige Messwerte fehlen.',
      next.publicStudy.enabled === false ? 'Öffentliche Forschung in der neuen App deaktiviert; nur nach Besitzerfreigabe aktivieren.'
        : next.publicStudy.registered === false ? 'Kein registrierter Forschungsjob in der neuen App nachgewiesen.'
          : next.publicStudy.registered === null ? 'JobScheduler-Status nicht gemessen.'
            : 'Forschungsjob registriert; Ausführung und Lerngewinn dadurch nicht bewiesen.'
    ])
  });
}
if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  try {
    if (process.argv.length !== 5 || process.argv[2] !== '--compare') throw Error('USAGE');
    const load = name => {
      const bytes = readFileSync(name);
      if (bytes.length > 8192) throw Error('INPUT_TOO_LARGE');
      return JSON.parse(bytes.toString('utf8'));
    };
    process.stdout.write(JSON.stringify(compareContinuumLearningStates(load(process.argv[3]),load(process.argv[4])),null,2)+'\n');
  } catch (e) {
    process.stderr.write('CONTINUUM_PARITY_BLOCKED=' + String(e.message).slice(0,100) + '\n');
    process.exitCode = 2;
  }
}
