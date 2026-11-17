/** Parses the final Node TAP summary without treating skipped tests as coverage. */
import {readFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';

const KEYS = Object.freeze(['tests','pass','fail','cancelled','skipped','todo']);
export function parseNodeTapSummary(text) {
  if (typeof text !== 'string' || !text.trim()) throw Error('NODE_TAP_SUMMARY_MISSING');
  const seen = new Map();
  for (const line of text.split(/\r?\n/)) {
    const match = /^# (tests|pass|fail|cancelled|skipped|todo) (\d+)$/.exec(line);
    if (match) {
      const count = Number(match[2]);
      if (!Number.isSafeInteger(count)) throw Error('NODE_TAP_COUNT_INVALID');
      seen.set(match[1],count);
    }
  }
  if (KEYS.some(key => !seen.has(key))) throw Error('NODE_TAP_SUMMARY_INCOMPLETE');
  const [tests,pass,fail,cancelled,skipped,todo] = KEYS.map(key => seen.get(key));
  if (tests < 1 || pass < 1 || tests !== pass + fail + cancelled + skipped + todo)
    throw Error('NODE_TAP_TOTAL_MISMATCH');
  if (fail > 0 || cancelled > 0) throw Error('NODE_TAP_EXECUTION_FAILED');
  return Object.freeze({tests,pass,fail,cancelled,skipped,todo,
    status:skipped || todo ? 'PASS_WITH_UNEXECUTED_CASES' : 'ALL_EXECUTED_PASS'});
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (process.argv.length !== 3) throw Error('ONE_TAP_FILE_REQUIRED');
  const result = parseNodeTapSummary(readFileSync(process.argv[2],'utf8'));
  for (const key of [...KEYS,'status']) console.log(`${key}=${result[key]}`);
}
