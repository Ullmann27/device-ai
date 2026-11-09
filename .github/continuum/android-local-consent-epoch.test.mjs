import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const activity = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'), 'utf8');
const scope = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/cognitive/TurnScopedInternetMode.kt'), 'utf8');

test('offline native model generation does not borrow Web-revocation authority', () => {
  const first = activity.slice(activity.indexOf('val speechShadow = if ('),
    activity.indexOf('requireUsableGeneration(generation)', activity.indexOf('val speechShadow = if (')));
  assert.match(first, /var generation = run \{/);
  assert.doesNotMatch(first, /withCurrentConsentRevision/);
  const retry = activity.slice(activity.indexOf('val rejected = generation.rejectedToolResponses'),
    activity.indexOf('requireUsableGeneration(generation)', activity.indexOf('val rejected = generation.rejectedToolResponses')));
  assert.match(retry, /generation = run \{/);
  assert.doesNotMatch(retry, /withCurrentConsentRevision/);
});

test('model Web dispatch retains exact-revision and per-session checks', () => {
  const webTool = activity.slice(activity.indexOf('if(request.name in setOf("web_search","web_read"))'),
    activity.indexOf('} else {', activity.indexOf('if(request.name in setOf("web_search","web_read"))')));
  assert.match(webTool, /withCurrentConsentRevision\(/);
  assert.match(webTool, /consentRevisionAtTurnStart/);
  assert.match(webTool, /approvedModelWebSession\.getAndSet\(null\)/);
  assert.match(webTool, /session\.checkAuthorized\(\)/);
  assert.match(activity, /modelWebSessions\.forEach \{ session -> session\.checkAuthorized\(\) \}/);
  assert.match(activity, /internetConsent\.revision\(\) != internetRevisionAtTurnStart/);
  assert.match(activity, /modelWebCalls, modelWebSessions\.isNotEmpty\(\)/);
  assert.match(scope, /fun discardAfterConsentChange\([\s\S]*?promptUsedOnlineEvidence: Boolean = false/);
  assert.match(scope, /changed && \(webCallCount > 0 \|\| hadWebSession \|\| promptUsedOnlineEvidence\)/);
  assert.match(activity, /onlineResearchFrameUsed\.set\(true\)/);
  assert.match(activity, /onlineResearchFrameUsed\.get\(\)/);
  assert.match(activity, /localConversationContext\.hasResearch\(localSessionId\)/);
});

test('native model scope never grants a newly refreshed Web session', () => {
  assert.match(scope, /fun <T> withCurrentConsentRevision\(expected: Long, revision: \(\) -> Long, action: \(\) -> T\): T/);
  assert.match(scope, /check\(revision\(\) == expected\)/);
  assert.doesNotMatch(activity, /internetConsent\.setMode\(at\.heinz\.deviceai\.cognitive\.InternetAccessMode\.ALWAYS_ON\)/);
});
