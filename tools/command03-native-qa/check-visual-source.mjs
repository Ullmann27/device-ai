import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = resolve(fileURLToPath(new URL('../../', import.meta.url)));
const lock = JSON.parse(readFileSync(resolve(root,'docs/research/omega/COMMAND03-HTML-VISUAL-PARITY-LOCK.json'), 'utf8'));
assert.equal(lock.source_commit, '0876aa0accdf8154e06e53da42aa5c9492892066');
assert.equal(lock.schema, 1);
assert.equal(lock.mode, 'HTML_ASSETS_EXACT_MAIN_STAGE_WIP_NOT_DEVICE_VERIFIED');
let bytes = 0;
for (const [relative, expected] of Object.entries(lock.files)) {
  assert.match(relative, /^[A-Za-z0-9_./-]+$/);
  assert.ok(!relative.split('/').includes('..'));
  const payload = readFileSync(resolve(root,lock.html_root,relative));
  const actual = createHash('sha1').update('blob '+payload.length+'\0').update(payload).digest('hex');
  assert.equal(actual,expected.git_blob_sha,'Original-Web-Asset geändert: '+relative);
  assert.equal(payload.length,expected.bytes);
  bytes += payload.length;
}
const html = readFileSync(resolve(root,lock.html_root,'index.html'),'utf8');
for (const section of ['presence-system','presence-core','presence-wing-left','presence-wing-right',
  'orb-zone','avatar-crop','workspace-shell','floating-dock','chat-sidebar']) {
  assert.ok(html.includes(section),'Original-Element fehlt: '+section);
}
const activity = readFileSync(resolve(root,'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'),'utf8');
assert.ok(activity.includes('attachCommand03MainStage(root)'),'HTML not mounted as main Activity surface');
assert.ok(activity.includes('sendButton.performClick()'),'Native owner-gated command dispatch missing');
const bridge = readFileSync(resolve(root,lock.html_root,'native-bridge.js'),'utf8');
assert.ok(bridge.includes('stopImmediatePropagation()'),'Native bridge must prevent fake demo execution');
assert.ok(!bridge.includes('addJavascriptInterface'));
assert.ok(bridge.includes('select-chat'),'Actual transcript selection route missing');
assert.ok(bridge.includes('data-native-chat'),'Native chat rows not rendered');
assert.ok(bridge.includes('window.ContinuumNative'),'Native state bridge missing');
assert.ok(readFileSync(resolve(root,lock.html_root,'native-layer.css'),'utf8').includes('native-work-active'));

const android = readFileSync(resolve(root,'app/src/main/java/at/heinz/deviceai/ui/ContinuumCommand03OriginalStage.kt'),'utf8');
assert.ok(android.includes('blockNetworkLoads = true'));
assert.ok(android.includes('allowFileAccess = false'));
assert.ok(android.includes('uri.host == LOCAL_HOST'));
console.log('COMMAND03_ORIGINAL_SOURCE_LOCK_PASS assets='+Object.keys(lock.files).length+' bytes='+bytes+
  ' scope=ORIGINAL_ARTWORK_AND_MAIN_STAGE_SOURCE_CONTRACT_ONLY (NOT compiled/visual/device/release acceptance)');
