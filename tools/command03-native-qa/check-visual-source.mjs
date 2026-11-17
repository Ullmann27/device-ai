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
const nativeHtml = readFileSync(resolve(root,lock.html_root,'index.android.html'),'utf8');
const extraCss = '  <link rel="stylesheet" href="native-layer.css">'+String.fromCharCode(10);
const extraJs = '  <script src="native-bridge.js"></script>'+String.fromCharCode(10);
assert.equal(nativeHtml.replace(extraCss,'').replace(extraJs,''),html,
  'Android HTML must differ only by loading two separate local adapter files');
assert.ok(nativeHtml.indexOf('native-bridge.js') < nativeHtml.indexOf('app.js'),
  'Android bridge must intercept navigation before preview scripts');
const activity = readFileSync(resolve(root,'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'),'utf8');
assert.ok(activity.includes('attachCommand03MainStage(root)'),'HTML not mounted as main Activity surface');
assert.ok(activity.includes('sendButton.performClick()'),'Native owner-gated command dispatch missing');
assert.ok(activity.includes('ALTE ANSICHT ÖFFNEN'),'No owner-visible fallback');
assert.ok(activity.includes('switchCommand03MainDesign(true)'),'Fallback cannot restore original view');
assert.ok(!activity.includes('showKommando03OriginalStage'),'Separate duplicate WebGL dialog still exists');
assert.ok(activity.includes('ambientMotionEnabled = false'),'Hidden native HUD still animating');
assert.ok(activity.includes('.put("workRows", workRows)'),'Real computer result rows not wired');
const bridge = readFileSync(resolve(root,lock.html_root,'native-bridge.js'),'utf8');
assert.ok(bridge.includes('stopImmediatePropagation()'),'Native bridge must prevent fake demo execution');
assert.ok(!bridge.includes('addJavascriptInterface'));
assert.ok(bridge.includes('select-chat'),'Actual transcript selection route missing');
assert.ok(bridge.includes('data-native-chat'),'Native chat rows not rendered');
assert.ok(bridge.includes('window.ContinuumNative'),'Native state bridge missing');
assert.ok(bridge.includes('snapshot.workRows'),'Computer result panel does not use real Android state');
assert.ok(bridge.includes('document:documentOpened'),'Real selected PDF is not bound to HTML');
assert.ok(bridge.includes("ContinuumWorkspaceMotion.transition(set,{kind:'open',duration:950})"),
  'Native documents must reuse the approved reversible 950ms HTML window flight');
assert.ok(bridge.includes('native-document-panel'),'Projected PDF work surface absent');
assert.ok(bridge.includes('textContent=actual?doc.text.slice(0,12000)'),
  'PDF data must be rendered as text, not injected into HTML');
assert.ok(bridge.includes('!currentScope(currentDocument)') &&
  ['data.sessionId===session','data.pageId===page','data.viewId===view','data.viewRevision===viewRevision']
    .every(guard=>bridge.includes(guard)),
  'Document must be scoped to its owning page, view instance and conversation');
assert.ok(bridge.includes('doc.previewDataUri.length <= 400000'),
  'Oversize document image must be rejected by the view');
assert.ok(bridge.includes('clearDocument()'),'Document cannot be dismissed');
assert.ok(activity.includes('projectCommand03Document(doc)'),'Native PDF read not projected');
assert.ok(activity.includes('doc.firstPage?.copy('),'Preview must own the image copy');
assert.ok(activity.includes('!command03ViewCurrent(projected.view)') &&
  activity.includes('command03Document !== projected') && activity.includes('command03Binding() != scope'),
  'Old view, page or replaced PDF preview must be rejected');
assert.ok(activity.includes('documentReadGeneration != readRevision'),
  'Superseded document content must be rejected');
assert.ok(activity.includes('output.size() > 280_000'),
  'Generated PDF image exceeds the safe byte budget');
assert.ok(!bridge.includes('innerHTML=doc.text'),'PDF text must never become HTML markup');

assert.ok(readFileSync(resolve(root,lock.html_root,'native-layer.css'),'utf8').includes('native-work-active'));

const android = readFileSync(resolve(root,'app/src/main/java/at/heinz/deviceai/ui/ContinuumCommand03OriginalStage.kt'),'utf8');
assert.ok(android.includes('blockNetworkLoads = true'));
assert.ok(android.includes('allowFileAccess = false'));
assert.ok(android.includes('uri.host == LOCAL_HOST'));
assert.ok(android.includes('index.android.html'),'The hardened early-bridge page must be the Android entry');
console.log('COMMAND03_ORIGINAL_SOURCE_LOCK_PASS assets='+Object.keys(lock.files).length+' bytes='+bytes+
  ' scope=ORIGINAL_ARTWORK_AND_MAIN_STAGE_SOURCE_CONTRACT_ONLY (NOT compiled/visual/device/release acceptance)');
