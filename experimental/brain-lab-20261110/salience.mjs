import assert from 'node:assert/strict';

export class EventSalienceProbe {
  #value = 0;
  #lastTick = null;
  observe({ tick, predicted, observed, correction = false }) {
    assert(Number.isSafeInteger(tick) && tick >= 0, 'invalid tick');
    assert(this.#lastTick === null || tick >= this.#lastTick, 'nonmonotonic tick');
    assert(Number.isFinite(predicted) && predicted >= 0 && predicted <= 1, 'invalid prediction');
    assert(observed === 0 || observed === 1, 'invalid outcome');
    assert(typeof correction === 'boolean', 'invalid correction');
    const elapsed = this.#lastTick === null ? 0 : tick - this.#lastTick;
    const residual = Math.abs(observed - predicted);
    const probability = observed === 1 ? predicted : 1 - predicted;
    this.#value = Math.min(1, this.#value * Math.exp(-elapsed / 4) + residual);
    const accumulator = this.#value;
    const wake = correction || accumulator >= 0.75;
    if (wake) this.#value = 0;
    this.#lastTick = tick;
    return { residual, surpriseNats: -Math.log(Math.max(1e-9, probability)),
      accumulator, wake, authority: 'ADVISORY_ONLY' };
  }
}

export const EVENT_STREAMS = Object.freeze([
  { name: 'calibrated-quiet', split: 'DEV', events: Array.from({ length: 12 }, (_, i) =>
    ({ tick: i, predicted: 0.95, observed: 1, expectedWake: false })) },
  { name: 'unexpected-failure', split: 'DEV', events: [
    { tick: 0, predicted: 0.95, observed: 0, expectedWake: true },
    { tick: 1, predicted: 0.95, observed: 1, expectedWake: false }] },
  { name: 'owner-correction', split: 'NEGATIVE', events: [
    { tick: 0, predicted: 1, observed: 1, correction: true, expectedWake: true }] },
  { name: 'alternating-low-residual', split: 'HOLDOUT', events: Array.from({ length: 20 }, (_, i) =>
    ({ tick: i, predicted: i % 2 ? 0.2 : 0.8, observed: i % 2 ? 0 : 1, expectedWake: false })) },
  { name: 'delayed-anomaly', split: 'HOLDOUT', events: [
    { tick: 0, predicted: 0.95, observed: 1, expectedWake: false },
    { tick: 40, predicted: 0.95, observed: 0, expectedWake: true },
    { tick: 41, predicted: 0.95, observed: 1, expectedWake: false }] },
]);

export function eventTrial(stream, arm) {
  assert(['always', 'threshold', 'accumulator'].includes(arm));
  const probe = new EventSalienceProbe();
  const rows = stream.events.map(({ expectedWake, ...event }) => {
    const result = probe.observe(event);
    const wake = arm === 'always' || (arm === 'threshold' ?
      event.correction === true || result.residual >= 0.75 : result.wake);
    return { ...event, ...result, wake, expectedWake };
  });
  return { stream: stream.name, split: stream.split, arm, events: rows.length,
    wakes: rows.filter(x => x.wake).length,
    falseWakes: rows.filter(x => x.wake && !x.expectedWake).length,
    misses: rows.filter(x => !x.wake && x.expectedWake).length, rows };
}
