import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = readFileSync(
  new URL('../../app/src/main/java/at/heinz/deviceai/ContinuumDashboardCollector.kt', import.meta.url),
  'utf8'
);

test('home dashboard source total is grounded in real content-addressed archive', () => {
  assert.match(source, /ContinuumKnowledgeArchive\(files\)\.stats\(\)\.values\.sum\(\)/);
  const area = source.slice(source.indexOf('val sources = guard('), source.indexOf('val publicStudy ='));
  assert.doesNotMatch(area, /WebLearningJournal\(context\)\.notes\(\)\.size/);
  assert.match(area, /cachedArchiveCount = fresh/);
});

test('archive scans are capped instead of repeated every five seconds', () => {
  assert.match(source, /lastArchiveCountAtMs/);
  assert.match(source, /30_000L/);
});
