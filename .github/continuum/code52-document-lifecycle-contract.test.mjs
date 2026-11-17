import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const root = 'app/src/main/java/at/heinz/deviceai/ui/';
const orchestration = fs.readFileSync(root + 'Airbus2100CockpitOrchestrator.kt', 'utf8');
const projection = fs.readFileSync(root + 'DocumentBlueprintProjection.kt', 'utf8');

test('verbal completion cannot erase an in-flight document draft', () => {
  assert.match(orchestration, /private var documentTurnId: String\? = null/);
  assert.match(orchestration, /documentTurnId = event\.turnId/);
  assert.match(orchestration, /if \(turnId != documentTurnId\) \{\s*document\.reset\(\)/);
  assert.match(orchestration, /is DocumentPipelineStepEvent -> \{\s*if \(event\.turnId != documentTurnId \|\| !document\.hasDocument\) return/);
  assert.match(orchestration, /is DocumentDraftReadyEvent -> \{\s*if \(event\.turnId != documentTurnId \|\| !document\.hasDocument\) return/);
});

test('memory and cancelled turns cannot create false document world', () => {
  // #701 isolation is stricter than a hasDocument early-return: DocumentLearned
  // must never be applied to the document workscene, even when a PDF is already open.
  assert.match(orchestration, /is CockpitAnimationEvent\.DocumentLearned -> Unit/);
  assert.doesNotMatch(orchestration, /is CockpitAnimationEvent\.DocumentLearned -> \{/);
  assert.doesNotMatch(orchestration, /document\.onLearned\(/);
  assert.match(projection, /fun onLearned\([\s\S]*?event: CockpitAnimationEvent\.DocumentLearned\) = Unit/);
  assert.doesNotMatch(projection, /entriesAdded = event\.entriesAdded/);
  assert.doesNotMatch(projection, /name = event\.docId/);
  assert.match(orchestration, /if \(!event\.delivered\) \{\s*document\.reset\(\)\s*documentTurnId = null/);
});
