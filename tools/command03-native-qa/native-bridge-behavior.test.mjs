import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';
import test from 'node:test';

// npm install --ignore-scripts --prefix /tmp/command03-dom jsdom@26.1.0
// COMMAND03_DOM_MODULE=/tmp/command03-dom/node_modules/jsdom/lib/api.js node --test tools/command03-native-qa/native-bridge-behavior.test.mjs
const require = createRequire(import.meta.url);
const {JSDOM} = require(process.env.COMMAND03_DOM_MODULE || 'jsdom');
const root = resolve(fileURLToPath(new URL('../../', import.meta.url)));
const assetRoot = resolve(root, 'app/src/main/assets/continuum-command03');
const sessionId = '12345678-1234-1234-1234-123456789abc';
const anotherSession = 'abcdefab-1234-1234-1234-123456789abc';
const pageId = '11111111-1111-4111-8111-111111111111';
const viewId = '22222222-2222-4222-8222-222222222222';
const anotherView = '33333333-3333-4333-8333-333333333333';
const sourceSetId = '55555555-5555-4555-8555-555555555555';
const sourceId = '66666666-6666-4666-8666-666666666666';
const documentId = '77777777-7777-4777-8777-777777777777';
const anotherDocument = '88888888-8888-4888-8888-888888888888';
const binding = {sessionId, pageId, viewId, viewRevision: 1};
const controlState = (f, extra = {}) => f.state({...binding, draftRevision: 1,
  ackEditRevision: 0, viewKind: 'CENTRAL', viewLabel: 'JARVIS', ...extra});
// Four-by-three synthetic JPEG, generated locally; no owner or device image is stored.
const frameDataUri = 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAADAAQDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDiKKKK9g8s/9k=';
const computerFrame = (extra = {}) => ({...binding, generation: 1, sequence: 1, active: true,
  status: 'LIVE', reason: 'Gerätebild verfügbar', taskId: 'synthetic-task', captureSession: 'synthetic-capture',
  sourceEpoch: 1, ageMs: 20, staleAfterMs: 1500, frameDataUri, width: 4, height: 3, ...extra});
const speechFrame = (extra = {}) => ({...binding, generation: 1, sequence: 1, active: true,
  utteranceId: 'synthetic-utterance', viseme: 'AA', weight: .7, jawOpen: .45,
  durationMs: 100, source: 'PCM_ENVELOPE', ...extra});
const acknowledgements = f => f.navigations.filter(url => url.pathname === '/frame-presented');
const invalidations = f => f.navigations.filter(url => url.pathname === '/frame-hidden');
function acknowledge(f,result) {
  const request=f.navigations.findLast(url=>url.pathname==='/command' && url.searchParams.get('id')===result.id);
  assert.ok(request,'The native acknowledgement belongs to an actual submitted request');
  const packet=Object.fromEntries(['pageId','viewId','sessionId'].map(key=>[key,request.searchParams.get(key)]));
  for(const key of ['viewRevision','clientEditRevision'])packet[key]=Number(request.searchParams.get(key));
  f.window.ContinuumNative.commandResult({...packet,...result});
}

async function fixture({realAvatar = false, realScene = false} = {}) {
  const dom = new JSDOM(readFileSync(resolve(assetRoot, 'index.android.html'), 'utf8'), {
    url: 'https://appassets.androidplatform.net/assets/continuum-command03/index.android.html',
    pretendToBeVisual: true, runScripts: 'outside-only'
  });
  const window = dom.window, navigations = [], scheduled = new Map(), scenes = [], decodings = [];
  const speechFrames = [], speechResults = [];
  const intensities = [], sceneQualities = [], audioLevels = [], gazes = [], sceneEvents = [];
  let serial = 0, now = 100, speechClears = 0, occluded = false;
  const schedule = (fn, delay = 0, type = 'timer', repeat = false) => {
    scheduled.set(++serial, {fn, at: now + Math.max(0, delay), type, repeat, delay});return serial;
  };
  const cancel = id => scheduled.delete(id);
  const clock = {setTimeout: (fn, delay) => schedule(fn, delay), clearTimeout: cancel,
    setInterval: (fn, delay) => schedule(fn, delay, 'timer', true), clearInterval: cancel,
    requestAnimationFrame: fn => schedule(fn, 16, 'raf'), cancelAnimationFrame: cancel};
  // Only the Android navigation transport and clock/GPU seams are doubles.
  // Production bridge and original DOM/app/telemetry/motion scripts execute unchanged.
  const proxy = new Proxy(window, {
    get(target, key) {
      if (key === 'location') return {assign: url => navigations.push(new URL(url))};
      if (key === 'performance') return {now: () => now};
      if (key in clock) return clock[key];
      return Reflect.get(target, key);
    }
  });
  window.matchMedia = () => ({matches: false, addEventListener() {}, removeEventListener() {}});
  // The scene double only records calls. `realScene` runs the byte-locked scene.js
  // instead (without a canvas context it keeps its state and event logic only).
  if (!realScene) window.ContinuumScene = {setState(value) {scenes.push(value);},
    setIntensity(value) {intensities.push(value);}, setQuality(value) {sceneQualities.push(value);},
    setAudioLevel(value) {audioLevels.push(value);}, resize() {}};
  // Details are copied into this realm; the event objects belong to the page context.
  window.addEventListener('continuum:gaze', event => gazes.push({target: event.target, detail: {...event.detail}}));
  window.addEventListener('continuum:scenechange', event => sceneEvents.push({...event.detail}));
  // The decoder and viewport are explicit browser seams. Tests never claim decoded GPU pixels.
  window.HTMLImageElement.prototype.decode = function() {
    return new Promise((resolve, reject) => decodings.push({image: this, resolve, reject}));
  };
  const originalBounds = window.HTMLImageElement.prototype.getBoundingClientRect;
  window.HTMLImageElement.prototype.getBoundingClientRect = function() {
    return this.classList.contains('native-computer-image') ?
      {x: 20, y: 100, left: 20, top: 100, right: 340, bottom: 340, width: 320, height: 240} :
      originalBounds.call(this);
  };
  window.document.elementFromPoint = () => occluded ? window.document.body :
    window.document.querySelector('.native-computer-image');
  if (realAvatar || realScene) window.HTMLCanvasElement.prototype.getContext = () => null;
  const context = vm.createContext({
    window: proxy, document: window.document, console, URL, URLSearchParams, TextEncoder,
    CustomEvent: window.CustomEvent, MouseEvent: window.MouseEvent, Event: window.Event,
    MutationObserver: window.MutationObserver, localStorage: window.localStorage,
    performance: {now: () => now}, getComputedStyle: window.getComputedStyle.bind(window),
    ...clock,
    HTMLElement: window.HTMLElement, Element: window.Element, navigator: window.navigator,
    Blob: window.Blob, File: window.File, FileReader: window.FileReader
  });
  for (const name of ['native-bridge.js', ...(realScene ? ['scene.js'] : []),
    ...(realAvatar ? ['avatar3d.js'] : []), 'workspace-motion.js', 'app.js',
    'presence-instruments.js', 'telemetry.js', 'operations.js']) {
    vm.runInContext(readFileSync(resolve(assetRoot, name), 'utf8'), context, {filename: name});
  }
  const style = window.document.createElement('style');
  style.textContent = readFileSync(resolve(assetRoot, 'native-layer.css'), 'utf8');
  window.document.head.appendChild(style);
  await new Promise(resolve => setImmediate(resolve));
  const avatar = window.ContinuumAvatar;
  window.ContinuumAvatar = {...avatar,
    setSpeechFrame(frame) {
      speechFrames.push(JSON.parse(JSON.stringify(frame)));
      const result = avatar ? avatar.setSpeechFrame(frame) : true;
      speechResults.push(result);return result;
    },
    clearSpeech() {speechClears++;avatar?.clearSpeech();}
  };
  let nativeScope={...binding}, nativeDraftRevision=0;
  const state = (extra = {}) => {
    const nextScope={...nativeScope};
    if(extra.sessionId && extra.sessionId!==nativeScope.sessionId && extra.viewId===undefined) {
      nextScope.viewRevision++;nextScope.viewId=anotherView;
    }
    const packet={...nextScope, busy:false, listening:false, cpu:10, ram:30, telemetryAtElapsedMs:now,
      viewKind:'CENTRAL', viewLabel:'JARVIS', ackEditRevision:0, draftRevision:++nativeDraftRevision,
      draft:{text:'',selectionStart:0,selectionEnd:0}, voiceMode:{active:false,paused:false},
      status:'Bereit', chats:[], workRows:[], ...extra};
    nativeScope=Object.fromEntries(['pageId','viewId','sessionId','viewRevision'].map(key=>[key,packet[key]]));
    window.ContinuumNative.receive(packet);
  };
  const click = element => element.dispatchEvent(new window.MouseEvent('click', {bubbles: true, cancelable: true}));
  const submit = () => window.document.querySelector('#command-form').dispatchEvent(
    new window.Event('submit', {bubbles: true, cancelable: true}));
  const input = text => {
    const field = window.document.querySelector('#command-input');
    field.value = text;field.dispatchEvent(new window.Event('input', {bubbles: true}));
    return field;
  };
  const flush = () => new Promise(resolve => setImmediate(resolve));
  return {dom, window, document: window.document, navigations, scenes, state, click, submit, input,
    speechFrames, speechResults, get speechClears() {return speechClears;}, decodings,
    intensities, sceneQualities, audioLevels, gazes, sceneEvents, flush,
    advance(ms) {now += ms;},
    // What the page has asked the clock for and not yet received: 'timer', 'timer:repeat' or 'raf' per entry.
    pendingWork() {return [...scheduled.values()].map(entry => entry.type + (entry.repeat ? ':repeat' : ''));},
    async decode(index = decodings.length - 1, succeeds = true) {
      const pending = decodings[index];assert.ok(pending, 'The production adapter requested image decoding');
      if (succeeds) {
        Object.defineProperties(pending.image, {
          naturalWidth: {value: pending.image.width || 4, configurable: true},
          naturalHeight: {value: pending.image.height || 3, configurable: true},
          complete: {value: true, configurable: true}
        });
        pending.resolve();
      } else pending.reject(new Error('Decoder refused the frame'));
      await flush();
    },
    async paint() {
      now += 16;
      for (const [id, entry] of [...scheduled]) if (entry.type === 'raf') {
        scheduled.delete(id);entry.fn(now);
      }
      await flush();
    },
    async timers(ms) {
      now += ms;
      for (const [id, entry] of [...scheduled]) if (entry.type === 'timer' && entry.at <= now) {
        scheduled.delete(id);entry.fn();
        if (entry.repeat) scheduled.set(id, {...entry, at: now + entry.delay});
      }
      await flush();
    },
    occlude(value) {occluded = value;window.dispatchEvent(new window.Event('scroll'));},
    close() {dom.window.close();}};
}

test('a projected real PDF remains visible through periodic state and transcript updates', async () => {
  const f = await fixture();
  try {
    f.state();
    f.window.ContinuumNative.transcript({...binding, entries: [{user: true, text: 'PDF öffnen'}]});
    f.window.ContinuumNative.document({...binding, documentId, name: 'Beispiel.pdf', pageCount: 2,
      textState: 'EXTRACTED', text: '<script>untrusted</script> echte Textschicht'});
    f.state({cpu: 20});
    f.window.ContinuumNative.message({...binding, user: false, text: 'Dokument gelesen'});
    const workspace = f.document.querySelector('#workspace');
    assert.equal(workspace.classList.contains('native-document-active'), true);
    assert.equal(workspace.classList.contains('native-work-active'), false);
    assert.notEqual(f.window.getComputedStyle(f.document.querySelector('#native-document-panel')).display, 'none');
    assert.equal(f.document.querySelector('.native-document-excerpt').textContent,
      '<script>untrusted</script> echte Textschicht');
    assert.equal(f.document.querySelector('#native-document-panel script'), null);
    f.window.ContinuumNative.clearDocument(binding);
    assert.equal(workspace.classList.contains('native-work-active'), true);
  } finally {f.close();}
});

test('native file and source controls never start the original demonstration', async () => {
  const f = await fixture();
  try {
    f.state();
    f.click(f.document.querySelector('[data-action="local-file"]'));
    assert.equal(f.navigations.at(-1)?.searchParams.get('value'), 'files');
    assert.equal(f.document.querySelector('input[type="file"]'), null);
    const sequence = f.document.querySelector('[data-action="sequence"]');
    f.click(sequence);
    assert.equal(sequence.classList.contains('active'), false);
    f.click(f.document.querySelector('[data-scene="working"]'));
    assert.equal(f.window.ContinuumPreview.getState().state, 'idle');
    const source = f.document.createElement('button');
    source.dataset.action = 'quick-source';f.document.body.appendChild(source);f.click(source);
    assert.equal(f.navigations.at(-1)?.searchParams.get('value'), 'research');
    assert.equal(f.window.ContinuumOperations.getState(), null);
  } finally {f.close();}
});

test('real telemetry stops the hidden simulation and keeps unknown readings unknown', async () => {
  const f = await fixture();
  try {
    f.state({cpu: null, ram: null});
    assert.equal(f.window.ContinuumTelemetry.getStatus().active, false);
    assert.match(f.document.querySelector('[data-telemetry-left]').textContent, /APP-CPU/);
    assert.match(f.document.querySelector('[data-telemetry-left]').textContent, /—/);
    assert.doesNotMatch(f.document.querySelector('[data-telemetry-left]').textContent, /Beispiellast/);
  } finally {f.close();}
});

test('a command draft survives native rejection and clears only after its matching acceptance', async () => {
  const f = await fixture();
  try {
    f.state();const field = f.input('Bitte prüfe diese Frage.');f.submit();
    const request = f.navigations.findLast(url => url.pathname === '/command');
    assert.ok(request);
    assert.equal(field.value, 'Bitte prüfe diese Frage.');
    const id = request.searchParams.get('id');
    assert.ok(id);assert.equal(request.searchParams.get('sessionId'), sessionId);
    acknowledge(f,{id, sessionId, accepted: false, reason: 'busy'});
    assert.equal(field.value, 'Bitte prüfe diese Frage.');
    f.submit();const second = f.navigations.findLast(url => url.pathname === '/command');
    acknowledge(f,{id: second.searchParams.get('id'), sessionId,
      accepted: true, reason: ''});
    assert.equal(field.value, '');
  } finally {f.close();}
});

test('a late acceptance does not delete a new draft or a different conversation', async () => {
  const f = await fixture();
  try {
    f.state();f.input('Erster Auftrag');f.submit();
    const request = f.navigations.findLast(url => url.pathname === '/command');
    f.input('Nächster Entwurf');
    acknowledge(f,{id: request.searchParams.get('id'), sessionId,
      accepted: true, reason: ''});
    assert.equal(f.document.querySelector('#command-input').value, 'Nächster Entwurf');
    f.state({sessionId: anotherSession, draft: {text: 'Anderer Chat', selectionStart: 5, selectionEnd: 5}});
    acknowledge(f,{id: request.searchParams.get('id'), sessionId,
      accepted: true, reason: ''});
    assert.equal(f.document.querySelector('#command-input').value, 'Anderer Chat');
  } finally {f.close();}
});

test('long real commands and input selections use the native session and UTF-8 budget', async () => {
  const f = await fixture();
  try {
    f.state({maxCommandChars: 11500, maxCommandBytes: 11500});
    const field = f.input('a'.repeat(3000));field.setSelectionRange(4, 8);
    field.dispatchEvent(new f.window.Event('select', {bubbles: true}));
    const draft = f.navigations.findLast(url => url.pathname === '/draft');
    assert.equal(draft?.searchParams.get('sessionId'), sessionId);
    assert.equal(draft?.searchParams.get('selectionStart'), '4');
    assert.equal(draft?.searchParams.get('selectionEnd'), '8');
    f.submit();assert.equal(f.navigations.findLast(url => url.pathname === '/command')?.searchParams.get('value').length, 3000);
    const count = f.navigations.filter(url => url.pathname === '/command').length;
    const command = f.navigations.findLast(url => url.pathname === '/command');
    acknowledge(f,{id: command.searchParams.get('id'), sessionId,
      accepted: false, reason: 'busy'});
    f.input('ö'.repeat(6000));f.submit();
    assert.equal(f.navigations.filter(url => url.pathname === '/command').length, count);
    assert.equal(field.value, 'ö'.repeat(6000));
  } finally {f.close();}
});

test('actual busy, listening and speaking transitions drive the original scene without demo timers', async () => {
  const f = await fixture();
  try {
    f.state();f.state({busy: true});assert.equal(f.scenes.at(-1), 'working');
    f.click(f.document.querySelector('[data-scene="speaking"]'));
    assert.equal(f.scenes.at(-1), 'working');
    f.state({listening: true});assert.equal(f.scenes.at(-1), 'listening');
    f.state({speaking: true});assert.equal(f.scenes.at(-1), 'speaking');
    f.state();assert.equal(f.scenes.at(-1), 'idle');
    assert.equal(f.document.querySelector('#orb-status').textContent, 'BEREIT');
    assert.equal(f.window.ContinuumOperations.getState(), null);
  } finally {f.close();}
});

test('telemetry history advances only for new actual measurements and preserves unavailable gaps', async () => {
  const f = await fixture();
  try {
    f.state({telemetryAtElapsedMs: 100, cpu: 10});
    const panel=f.document.querySelector('[data-telemetry-left] .ct-panel');
    assert.ok(panel, 'The original chart panel is retained');
    assert.equal(panel.dataset.samples, '1');
    f.state({telemetryAtElapsedMs: 100, busy: true});assert.equal(panel.dataset.samples, '1');
    f.state({telemetryAtElapsedMs: 5100, cpu: null});
    f.state({telemetryAtElapsedMs: 10100, cpu: 40});
    const trace=panel.querySelector('.ct-trace').getAttribute('d');
    assert.equal((trace.match(/M/g)||[]).length, 2, 'Unknown CPU sample splits the trace');
    assert.equal((trace.match(/L/g)||[]).length, 0);
    f.state({telemetryAtElapsedMs: 50100, cpu: 50});
    assert.equal((panel.querySelector('.ct-trace').getAttribute('d').match(/M/g)||[]).length, 3,
      'A long collection gap is never presented as continuous measured work');
  } finally {f.close();}
});

test('empty native history and native-ready captions replace demo content while retaining button icons', async () => {
  const f = await fixture();
  try {
    const button=f.document.querySelector('#hero-action'), icon=button.querySelector('svg');
    const badgeIndicator=f.document.querySelector('.preview-badge i');
    assert.ok(f.document.querySelector('#chat-list [data-chat]'), 'Original fixture has sample chats');
    f.state();
    assert.equal(f.document.querySelector('#chat-list').children.length, 0);
    assert.match(button.textContent, /Recherche öffnen/);assert.equal(button.querySelector('svg'), icon);
    assert.equal(f.document.querySelector('.preview-badge i'), badgeIndicator);
    assert.equal(f.document.querySelector('#workspace-footnote').textContent, 'Bereit für deinen Auftrag');
    assert.doesNotMatch(f.document.querySelector('.input-hint').textContent, /VORSCHAU/);
    assert.doesNotMatch(f.document.querySelector('.sidebar-note').textContent, /Beispielchats/);
  } finally {f.close();}
});

test('late message and document callbacks cannot cross conversation boundaries', async () => {
  const f = await fixture();
  try {
    f.state();f.window.ContinuumNative.message({...binding, user: false, text: 'Private alte Antwort'});
    assert.match(f.document.querySelector('#native-live-panel').textContent, /Private alte Antwort/);
    f.state({sessionId: anotherSession});
    assert.equal(f.document.querySelector('#native-live-panel'), null);
    f.window.ContinuumNative.message({...binding, user: false, text: 'Verspätete alte Antwort'});
    f.window.ContinuumNative.document({...binding, documentId, name: 'Alt.pdf', pageCount: 1, textState: 'EXTRACTED', text: 'Alte Daten'});
    assert.equal(f.document.querySelector('#native-live-panel'), null);
    assert.equal(f.document.querySelector('#native-document-panel'), null);
  } finally {f.close();}
});

test('chunked transcripts preserve long model responses and user scroll nodes on status refresh', async () => {
  const f = await fixture();
  try {
    const response='Das ist der vollständige Text. '.repeat(450);
    f.state();
    f.window.ContinuumNative.transcript({...binding, append: false, entries: [{user: true, text: 'Frage'}]});
    f.window.ContinuumNative.transcript({...binding, append: true, entries: [{user: false, text: response}]});
    const row=f.document.querySelector('.native-assistant p');
    assert.equal(row.textContent,response);assert.ok(row.textContent.length>6000);
    f.state({cpu: 23});assert.equal(f.document.querySelector('.native-assistant p'),row,
      'A CPU refresh must not replace messages and lose selection or scroll state');
    assert.equal(f.document.querySelectorAll('.native-chat-line').length,2);
  } finally {f.close();}
});

test('native restore does not overwrite a local draft and oversize commands keep their synchronized draft', async () => {
  const f = await fixture();
  try {
    f.state({draft:{text:'Gespeicherter Entwurf',selectionStart:4,selectionEnd:8}});
    const field=f.document.querySelector('#command-input');
    assert.equal(field.value,'Gespeicherter Entwurf');assert.equal(field.selectionStart,4);assert.equal(field.selectionEnd,8);
    f.input('Meine neue Eingabe');
    f.state({draft:{text:'Alter Snapshot',selectionStart:0,selectionEnd:0}});
    assert.equal(field.value,'Meine neue Eingabe');
    const large='ö'.repeat(6000);f.input(large);f.submit();
    const draft=f.navigations.findLast(url=>url.pathname==='/draft');
    assert.equal(draft.searchParams.get('value'),large);
    assert.equal(f.navigations.filter(url=>url.pathname==='/command').length,0);
    assert.equal(field.value,large);
  } finally {f.close();}
});

test('selecting a stored conversation closes the existing mobile drawer without replacing its controls', async () => {
  const f = await fixture();
  try {
    f.state({chats:[{id:sessionId,title:'Gespeicherter Chat'}]});
    const toggle=f.document.querySelector('[data-action="toggle-chats"]');
    f.click(toggle);assert.equal(f.document.body.classList.contains('chats-open'),true);
    const entry=f.document.querySelector('[data-native-chat]');f.click(entry);
    assert.equal(f.document.body.classList.contains('chats-open'),false);
    assert.equal(toggle.getAttribute('aria-expanded'),'false');
    assert.equal(f.document.querySelector('[data-action="toggle-chats"]'),toggle);
    assert.equal(f.navigations.at(-1).pathname,'/select-chat');
    assert.equal(f.navigations.at(-1).searchParams.get('value'),sessionId);
  } finally {f.close();}
});

test('the original composer slot supports exact multiline paste, submit and native restore', async () => {
  const f = await fixture();
  try {
    const text='Erste Zeile\n'+ 'a'.repeat(3200)+'\nDritte Zeile\tmit Tab';
    f.state({draft:{text,selectionStart:3,selectionEnd:20}});
    const field=f.document.querySelector('#command-input');
    assert.equal(field.tagName,'TEXTAREA');assert.equal(field.rows,1);
    assert.equal(f.document.querySelectorAll('#command-input').length,1);
    assert.equal(field.parentElement.className,'command-field');
    assert.equal(field.value,text);assert.equal(field.selectionStart,3);assert.equal(field.selectionEnd,20);
    field.setSelectionRange(4,20); // A real owner selection changes the acknowledged native draft.
    field.dispatchEvent(new f.window.Event('blur'));
    assert.equal(f.navigations.findLast(url=>url.pathname==='/draft').searchParams.get('value'),text);
    f.submit();const command=f.navigations.findLast(url=>url.pathname==='/command');
    assert.equal(command.searchParams.get('value'),text);
    assert.equal(field.value,text,'No newline or pasted text is lost before native acceptance');
    const next='Neue Frage\nZweite Zeile';f.input(next);
    acknowledge(f,{id:command.searchParams.get('id'),sessionId,accepted:true});
    assert.equal(field.value,next);
  } finally {f.close();}
});

test('typing before Android is ready is synchronized as soon as the first native session arrives', async () => {
  const f = await fixture();
  try {
    const text='Schon getippt\nvor der Verbindung';f.input(text);
    assert.equal(f.navigations.length,0);
    f.state({draft:{text:'Alter nativer Entwurf',selectionStart:0,selectionEnd:0}});
    const draft=f.navigations.findLast(url=>url.pathname==='/draft');
    assert.ok(draft);assert.equal(draft.searchParams.get('value'),text);
    assert.equal(draft.searchParams.get('sessionId'),sessionId);
    assert.equal(f.document.querySelector('#command-input').value,text);
  } finally {f.close();}
});

test('native draft revisions cannot restore consumed text or reset a newer local selection', async () => {
  const f = await fixture();
  try {
    f.state({draftRevision:1,draft:{text:'Wiederhergestellt',selectionStart:1,selectionEnd:1}});
    const field=f.document.querySelector('#command-input');field.setSelectionRange(5,5);
    f.state({draftRevision:1,draft:{text:'Wiederhergestellt',selectionStart:1,selectionEnd:1}});
    assert.equal(field.selectionStart,5,'Same revision must not rewind a selection');
    f.submit();const command=f.navigations.findLast(url=>url.pathname==='/command');
    f.state({commandPending:true,draftRevision:2,draft:{text:'',selectionStart:0,selectionEnd:0}});
    assert.equal(field.value,'Wiederhergestellt','A pending snapshot is not a send acknowledgement');
    acknowledge(f,{id:command.searchParams.get('id'),sessionId,accepted:true});
    assert.equal(field.value,'');
    f.state({draftRevision:1,draft:{text:'Wiederhergestellt',selectionStart:1,selectionEnd:1}});
    assert.equal(field.value,'');
    f.state({draftRevision:2,draft:{text:'Wiederhergestellt',selectionStart:1,selectionEnd:1}});
    assert.equal(field.value,'');
    f.state({draftRevision:3,ackEditRevision:Number(command.searchParams.get('clientEditRevision')),
      draft:{text:'Neuer nativer Entwurf',selectionStart:2,selectionEnd:4}});
    assert.equal(field.value,'Neuer nativer Entwurf');assert.equal(field.selectionEnd,4);
  } finally {f.close();}
});

test('computer frames use the original window and acknowledge only decoded visible frames after its one flight', async () => {
  const f = await fixture();
  try {
    f.state();f.window.ContinuumNative.computer(computerFrame());
    const panel = f.document.querySelector('#native-computer-panel');
    assert.ok(panel?.classList.contains('projected-computer'));
    assert.ok(panel.querySelector('.op-browser-frame'));
    assert.equal(panel.querySelector('.native-computer-image'), null);
    assert.equal(acknowledgements(f).length, 0);
    const motion = f.window.ContinuumWorkspaceMotion.getStatus();
    assert.equal(motion.kind, 'open');assert.equal(motion.duration, 950);
    await f.decode();
    const image = panel.querySelector('.native-computer-image');
    assert.equal(image.getAttribute('src'), frameDataUri);
    assert.equal(acknowledgements(f).length, 0, 'Decode does not prove a visible paint');
    f.advance(950);await f.paint();await f.paint();
    assert.equal(acknowledgements(f).length, 0, 'The first RAF is still before its paint');
    await f.paint();
    assert.equal(acknowledgements(f).length, 1);
    const ack = acknowledgements(f)[0];
    assert.equal(ack.searchParams.get('sessionId'), sessionId);
    assert.equal(ack.searchParams.get('generation'), '1');assert.equal(ack.searchParams.get('sequence'), '1');
    f.window.ContinuumNative.computer(computerFrame({sequence: 2}));await f.decode();
    assert.equal(f.document.querySelector('#native-computer-panel'), panel);
    assert.equal(f.window.ContinuumWorkspaceMotion.getStatus().id, motion.id,
      'A subsequent frame never starts a second window animation');
    await f.paint();await f.paint();
    assert.equal(acknowledgements(f).length, 2);
    assert.equal(f.document.querySelectorAll('.native-computer-image').length, 1);
  } finally {f.close();}
});

test('late decode and frame replay cannot reopen a stopped or rebound computer source', async () => {
  const f = await fixture();
  try {
    f.state();f.window.ContinuumNative.computer(computerFrame());
    f.window.ContinuumNative.computer({...binding, generation: 1, sequence: 2, active: false, status: 'ENDED'});
    await f.decode(0);f.advance(950);await f.paint();await f.paint();await f.paint();
    assert.equal(f.document.querySelector('#native-computer-panel'), null);
    assert.equal(acknowledgements(f).length, 0);
    f.window.ContinuumNative.computer(computerFrame({sequence: 3}));
    assert.equal(f.decodings.length, 1, 'A terminal generation cannot resume');
    f.window.ContinuumNative.computer(computerFrame({generation: 2, sequence: 1, sourceEpoch: 2}));
    f.window.ContinuumNative.computer(computerFrame({generation: 1, sequence: 99}));
    assert.equal(f.decodings.length, 2);
    await f.decode(1);
    assert.equal(f.document.querySelector('.native-computer-image').dataset.generation, '2');
    assert.ok(invalidations(f).length > 0);
  } finally {f.close();}
});

test('a computer image expires without new frames and unavailable status removes pixels immediately', async () => {
  const f = await fixture();
  try {
    f.state();f.window.ContinuumNative.computer(computerFrame());await f.decode();
    await f.timers(1501);
    assert.equal(f.document.querySelector('.native-computer-image'), null);
    assert.doesNotMatch(f.document.querySelector('#native-computer-panel .op-mode-label').textContent, /^LIVE$/);
    assert.ok(invalidations(f).length > 0);
    f.window.ContinuumNative.computer(computerFrame({sequence: 2}));await f.decode();
    f.window.ContinuumNative.computer({...binding, generation: 1, sequence: 3,
      active: true, status: 'COVERED', reason: 'Quelle verdeckt'});
    assert.equal(f.document.querySelector('.native-computer-image'), null);
    assert.match(f.document.querySelector('#native-computer-panel').textContent, /Quelle verdeckt/);
  } finally {f.close();}
});

test('PDF and chat remain intact while a covered computer invalidates its previous render receipt', async () => {
  const f = await fixture();
  try {
    f.state();f.window.ContinuumNative.message({...binding, user: false, text: 'Erhaltene Antwort'});
    f.window.ContinuumNative.computer(computerFrame());await f.decode();
    f.advance(950);await f.paint();await f.paint();await f.paint();
    assert.equal(acknowledgements(f).length, 1);
    f.window.ContinuumNative.document({...binding, documentId, name: 'Echt.pdf', pageCount: 1,
      textState: 'EXTRACTED', text: 'Erhaltener PDF-Inhalt'});
    assert.ok(invalidations(f).length > 0, 'Opening a PDF revokes the old computer receipt immediately');
    f.window.ContinuumNative.computer(computerFrame({sequence: 2}));await f.decode();
    f.advance(950);await f.paint();await f.paint();await f.paint();
    assert.equal(acknowledgements(f).length, 1, 'A computer behind a PDF cannot be acknowledged');
    const workspace = f.document.querySelector('#workspace');
    assert.equal(workspace.classList.contains('native-document-active'), true);
    assert.equal(workspace.classList.contains('native-computer-active'), false);
    f.state({busy: true, cpu: 44});
    assert.equal(f.document.querySelector('.native-document-excerpt').textContent, 'Erhaltener PDF-Inhalt');
    f.window.ContinuumNative.computer({...binding, generation: 1, sequence: 3, active: false, status: 'ENDED'});
    f.window.ContinuumNative.clearDocument(binding);
    assert.match(f.document.querySelector('#native-live-panel').textContent, /Erhaltene Antwort/);
  } finally {f.close();}
});

test('invalid computer payloads and callbacks from an old conversation never become visible frames', async () => {
  const f = await fixture();
  try {
    f.state();
    for (const bad of [{width: 641}, {height: 0}, {ageMs: 1501}, {staleAfterMs: 1501},
      {frameDataUri: 'https://example.invalid/frame.jpg'}, {frameDataUri: frameDataUri + 'A'.repeat(174787)},
      {sequence: NaN}, {sessionId: anotherSession}, {sourceEpoch: -1}, {captureSession: 42}]) {
      f.window.ContinuumNative.computer(computerFrame(bad));
    }
    assert.equal(f.decodings.length, 0);
    f.window.ContinuumNative.computer(computerFrame());
    f.state({sessionId: anotherSession});await f.decode();
    assert.equal(f.document.querySelector('#native-computer-panel'), null);
    f.window.ContinuumNative.computer(computerFrame({sequence: 2}));
    f.state();f.window.ContinuumNative.computer(computerFrame({sequence: 3}));
    assert.equal(f.decodings.length, 1, 'Returning to the old session does not revive its retired generation');
    assert.equal(acknowledgements(f).length, 0);
  } finally {f.close();}
});

test('occlusion, page hiding and failed decoding never produce a computer render acknowledgement', async () => {
  const f = await fixture();
  try {
    f.state();f.window.ContinuumNative.computer(computerFrame());await f.decode();
    f.occlude(true);f.advance(950);await f.paint();await f.paint();await f.paint();
    assert.equal(acknowledgements(f).length, 0);
    assert.ok(invalidations(f).length > 0);
    f.window.ContinuumNative.computer(computerFrame({sequence: 2}));await f.decode(1, false);
    assert.equal(f.document.querySelector('.native-computer-image'), null);
    assert.equal(acknowledgements(f).length, 0);
    f.window.ContinuumNative.computer(computerFrame({sequence: 3}));
    Object.defineProperty(f.document, 'visibilityState', {value: 'hidden', configurable: true});
    f.document.dispatchEvent(new f.window.Event('visibilitychange'));
    await f.decode(2);f.advance(950);await f.paint();await f.paint();await f.paint();
    assert.equal(f.document.querySelector('.native-computer-image'), null);
    assert.equal(acknowledgements(f).length, 0);
  } finally {f.close();}
});

test('native speech frames reach the original viseme API with their actual source and expire to silence', async () => {
  const f = await fixture();
  try {
    f.state({speaking: true});
    f.window.ContinuumNative.speech(speechFrame());
    assert.deepEqual(f.speechFrames.at(-1), {viseme: 'AA', weight: .7, jawOpen: .45, durationMs: 100});
    assert.equal(f.document.querySelector('.avatar-crop').dataset.speechSource, 'PCM_ENVELOPE');
    f.window.ContinuumNative.speech(speechFrame({sequence: 2, viseme: 'PP', weight: .9, jawOpen: 0,
      source: 'WORD_RANGE_FALLBACK'}));
    assert.equal(f.speechFrames.at(-1).viseme, 'PP');
    assert.equal(f.document.querySelector('.avatar-crop').dataset.speechSource, 'WORD_RANGE_FALLBACK');
    const before = f.speechClears;await f.timers(101);
    assert.ok(f.speechClears > before, 'No new real pose means the mouth is cleared');
    assert.equal(f.document.querySelector('.avatar-crop').dataset.speechActive, 'false');
  } finally {f.close();}
});

test('speech rejects invalid morphs, late utterances, stopped generations and different sessions', async () => {
  const f = await fixture();
  try {
    f.state({speaking: true});
    for (const bad of [{viseme: 'UNKNOWN'}, {weight: NaN}, {weight: 1.1}, {jawOpen: -.1},
      {durationMs: 501}, {source: 'SIMULATED'}, {sessionId: anotherSession}]) {
      f.window.ContinuumNative.speech(speechFrame(bad));
    }
    assert.equal(f.speechFrames.length, 0);
    f.window.ContinuumNative.speech(speechFrame());
    f.window.ContinuumNative.speech(speechFrame());
    assert.equal(f.speechFrames.length, 1);
    f.window.ContinuumNative.speech({...binding, generation: 1, sequence: 2, active: false});
    f.window.ContinuumNative.speech(speechFrame({sequence: 3}));
    assert.equal(f.speechFrames.length, 1);
    f.window.ContinuumNative.speech(speechFrame({generation: 2, utteranceId: 'new-utterance'}));
    f.window.ContinuumNative.speech(speechFrame({generation: 1, sequence: 99}));
    assert.equal(f.speechFrames.length, 2);
    f.state({sessionId: anotherSession});
    f.window.ContinuumNative.speech(speechFrame({generation: 2, sequence: 2, utteranceId: 'new-utterance'}));
    assert.equal(f.speechFrames.length, 2);
    assert.equal(f.document.querySelector('.avatar-crop').dataset.speechActive, 'false');
  } finally {f.close();}
});

test('the byte-locked bundled avatar accepts the native visemes through its public fallback-mode API', async () => {
  const f = await fixture({realAvatar: true});
  try {
    f.state({speaking: true});f.window.ContinuumNative.speech(speechFrame());
    assert.equal(f.speechResults.at(-1), true);
    assert.equal(f.window.ContinuumAvatar.getStatus().speechSource, 'external-viseme-events-only');
    assert.equal(f.window.ContinuumAvatar.getStatus().renderer, 'original-png');
    f.window.ContinuumNative.speech({...binding, generation: 1, sequence: 2, active: false});
    assert.ok(f.speechClears > 0);
  } finally {f.close();}
});

test('covering a decoded image with a newer pending frame withdraws both exact receipts', async () => {
  const f = await fixture();
  try {
    f.state();f.window.ContinuumNative.computer(computerFrame());await f.decode();
    f.advance(950);await f.paint();await f.paint();await f.paint();
    assert.equal(acknowledgements(f).length, 1);
    f.window.ContinuumNative.computer(computerFrame({sequence: 2}));
    assert.equal(f.document.querySelector('.native-computer-image').dataset.sequence, '1',
      'Decoded old pixels remain until their replacement is decoded, without relabelling their receipt');
    f.window.ContinuumNative.document({...binding, documentId, name: 'Vordergrund.pdf', pageCount: 1,
      textState: 'EXTRACTED', text: 'Die aktuelle Arbeitsfläche ist diese PDF.'});
    assert.deepEqual(invalidations(f).map(url => url.searchParams.get('sequence')), ['1','2']);
    await f.decode(1);await f.paint();await f.paint();
    assert.equal(acknowledgements(f).length, 1);
    assert.equal(f.document.querySelector('.native-computer-image'), null);
    assert.equal(f.document.querySelector('#workspace').classList.contains('native-document-active'), true);
  } finally {f.close();}
});

test('an expired pose cannot transfer its generation to another utterance or return after page hiding', async () => {
  const f = await fixture();
  try {
    f.state({speaking: true});f.window.ContinuumNative.speech(speechFrame());
    await f.timers(101);
    f.window.ContinuumNative.speech(speechFrame({sequence: 2, utteranceId: 'different-utterance'}));
    assert.equal(f.speechFrames.length, 1);
    f.window.ContinuumNative.speech(speechFrame({sequence: 2, source: 'TEXT_PACE_FALLBACK'}));
    assert.equal(f.speechFrames.length, 2, 'A fresh pose from the same utterance can resume after a pose gap');
    assert.equal(f.document.querySelector('.avatar-crop').dataset.speechSource, 'TEXT_PACE_FALLBACK');
    Object.defineProperty(f.document, 'visibilityState', {value: 'hidden', configurable: true});
    f.document.dispatchEvent(new f.window.Event('visibilitychange'));
    Object.defineProperty(f.document, 'visibilityState', {value: 'visible', configurable: true});
    f.document.dispatchEvent(new f.window.Event('visibilitychange'));
    f.window.ContinuumNative.speech(speechFrame({sequence: 3}));
    assert.equal(f.speechFrames.length, 2);
    f.window.ContinuumNative.speech(speechFrame({generation: 2, utteranceId: 'resumed-utterance'}));
    assert.equal(f.speechFrames.length, 3);
  } finally {f.close();}
});

test('native control acknowledgements admit real dictation and consumption without replacing newer local edits', async () => {
  const f = await fixture();
  try {
    controlState(f, {draft: {text: '', selectionStart: 0, selectionEnd: 0}});
    const field = f.input('Mein Entwurf');
    controlState(f, {draftRevision: 2, ackEditRevision: 1,
      draft: {text: 'Mein Entwurf mit Diktat', selectionStart: 22, selectionEnd: 22}});
    assert.equal(field.value, 'Mein Entwurf mit Diktat', 'A confirmed native dictation is a real newer edit');
    f.input('Meine jüngere Änderung');
    controlState(f, {draftRevision: 3, ackEditRevision: 1,
      draft: {text: '', selectionStart: 0, selectionEnd: 0}});
    assert.equal(field.value, 'Meine jüngere Änderung', 'An older acknowledgement cannot erase a newer local edit');
    controlState(f, {draftRevision: 4, ackEditRevision: 2,
      draft: {text: '', selectionStart: 0, selectionEnd: 0}});
    assert.equal(field.value, '', 'Actual native consumption clears the confirmed visible draft');
    const edits = f.navigations.filter(url => url.pathname === '/draft');
    assert.deepEqual(edits.map(url => url.searchParams.get('clientEditRevision')), ['1', '2']);
    for (const edit of edits) {
      assert.equal(edit.searchParams.get('pageId'), pageId);
      assert.equal(edit.searchParams.get('viewId'), viewId);
    }
  } finally {f.close();}
});

test('native control selection acknowledgements preserve a newer caret and never echo a restored draft', async () => {
  const f = await fixture();
  try {
    controlState(f, {draft: {text: 'Ein Text', selectionStart: 1, selectionEnd: 3}});
    const field = f.document.querySelector('#command-input');
    field.setSelectionRange(4, 7);field.dispatchEvent(new f.window.Event('select', {bubbles: true}));
    const edit = f.navigations.findLast(url => url.pathname === '/draft');
    assert.equal(edit.searchParams.get('clientEditRevision'), '1');
    controlState(f, {draftRevision: 2, ackEditRevision: 0,
      draft: {text: 'Ein Text', selectionStart: 1, selectionEnd: 3}});
    assert.equal(field.selectionStart, 4);assert.equal(field.selectionEnd, 7);
    controlState(f, {draftRevision: 3, ackEditRevision: 1,
      draft: {text: 'Ein neuer Text', selectionStart: 5, selectionEnd: 5}});
    assert.equal(field.value, 'Ein neuer Text');assert.equal(field.selectionStart, 5);
    field.dispatchEvent(new f.window.Event('select', {bubbles: true}));
    assert.equal(f.navigations.filter(url => url.pathname === '/draft').length, 1,
      'Applying native text/selection must not start an echo loop');
  } finally {f.close();}
});

test('native control snapshot admission observes a caret change before its delayed select event', async () => {
  const f=await fixture();
  try {
    controlState(f,{draft:{text:'Mein Text',selectionStart:1,selectionEnd:2}});
    const field=f.document.querySelector('#command-input');
    field.setSelectionRange(4,8);
    controlState(f,{draftRevision:2,ackEditRevision:0,
      draft:{text:'Mein Text',selectionStart:1,selectionEnd:2}});
    assert.equal(field.selectionStart,4);assert.equal(field.selectionEnd,8);
    const edit=f.navigations.findLast(url=>url.pathname==='/draft');
    assert.equal(edit.searchParams.get('clientEditRevision'),'1');
    assert.equal(edit.searchParams.get('selectionEnd'),'8');
  } finally {f.close();}
});

test('native control snapshots cannot erase an oversized local draft that could not be acknowledged', async () => {
  const f=await fixture();
  try {
    controlState(f,{draft:{text:'',selectionStart:0,selectionEnd:0}});
    const text='a'.repeat(262145), field=f.input(text);
    controlState(f,{draftRevision:2,ackEditRevision:0,
      draft:{text:'',selectionStart:0,selectionEnd:0}});
    assert.ok(field.value===text,'The oversized local draft is preserved exactly');
    assert.equal(f.navigations.filter(url=>url.pathname==='/draft').length,0);
  } finally {f.close();}
});

test('native control view instances replace bot histories and reject old snapshots messages and command acknowledgements', async () => {
  const f = await fixture();
  try {
    controlState(f);
    f.window.ContinuumNative.transcript({...binding, entries: [{user: false, text: 'Zentraler Verlauf'}]});
    f.input('Alter Auftrag');f.submit();
    const command = f.navigations.findLast(url => url.pathname === '/command');
    const bot = {...binding, viewId: anotherView, viewRevision: 2};
    controlState(f, {...bot, viewKind: 'BOT', viewLabel: 'Atlas', draftRevision: 2,
      draft: {text: 'Bot-Entwurf', selectionStart: 11, selectionEnd: 11}});
    f.window.ContinuumNative.transcript({...bot, entries: [{user: false, text: 'Privater Bot-Verlauf'}]});
    assert.doesNotMatch(f.document.querySelector('#workspace').textContent, /Zentraler Verlauf/);
    f.window.ContinuumNative.message({...binding, user: false, text: 'Verspätete zentrale Nachricht'});
    acknowledge(f,{...binding, id: command.searchParams.get('id'), accepted: true});
    controlState(f, {draftRevision: 99, draft: {text: 'Veralteter Snapshot', selectionStart: 0, selectionEnd: 0}});
    assert.equal(f.document.querySelector('#command-input').value, 'Bot-Entwurf');
    assert.match(f.document.querySelector('#workspace').textContent, /Privater Bot-Verlauf/);
    assert.doesNotMatch(f.document.querySelector('#workspace').textContent, /Verspätete zentrale Nachricht/);
    assert.match(f.document.querySelector('#command-input').getAttribute('aria-label'), /Atlas/);
    controlState(f, {viewId: '44444444-4444-4444-8444-444444444444', viewRevision: 3,
      draftRevision: 3, draft: {text: '', selectionStart: 0, selectionEnd: 0}});
    f.window.ContinuumNative.message({...bot, user: false, text: 'Verspätete Bot-Nachricht'});
    assert.doesNotMatch(f.document.querySelector('#workspace').textContent, /Privater Bot-Verlauf|Verspätete Bot-Nachricht/);
  } finally {f.close();}
});

test('native control focus reaches the visible composer only for the current explicitly selected view', async () => {
  const f = await fixture();
  try {
    controlState(f);
    const field = f.document.querySelector('#command-input');
    assert.notEqual(f.document.activeElement, field);
    f.window.ContinuumNative.section({...binding, name: 'chat'});
    assert.notEqual(f.document.activeElement, field, 'Showing a completed answer must not open the editor');
    f.window.ContinuumNative.section({...binding, name: 'chat', focusComposer: false});
    assert.notEqual(f.document.activeElement, field);
    f.window.ContinuumNative.section({...binding, name: 'chat', focusComposer: true});
    assert.equal(f.document.activeElement, field);
    field.blur();
    f.window.ContinuumNative.section({...binding, pageId: anotherSession, name: 'chat'});
    assert.notEqual(f.document.activeElement, field);
    controlState(f, {cpu: 22, draftRevision: 2});
    assert.notEqual(f.document.activeElement, field, 'Periodic status must never steal focus');
  } finally {f.close();}
});

test('native control automatic command transitions retain an unacknowledged draft for explicit recovery only', async () => {
  const f=await fixture();
  try {
    controlState(f);f.input('@Atlas bitte prüfen');f.submit();
    const command=f.navigations.findLast(url=>url.pathname==='/command');
    const text='Noch unbestätigt\nmit zweiter Zeile', field=f.input(text);
    field.setSelectionRange(5,14);
    const bot={...binding,viewId:anotherView,viewRevision:2};
    const transitionCause={commandId:command.searchParams.get('id'),fromPageId:pageId,
      fromViewId:viewId,fromViewRevision:1,fromSessionId:sessionId,ackEditRevision:1};
    const before=f.navigations.length;
    controlState(f,{...bot,viewKind:'BOT',viewLabel:'Atlas',transitionCause,
      draft:{text:'Nativer neuer Entwurf',selectionStart:20,selectionEnd:20}});
    assert.equal(field.value,'Nativer neuer Entwurf');
    const recovery=f.document.querySelector('.native-draft-recovery');
    assert.ok(recovery,'An unacknowledged edit remains visibly recoverable');
    assert.equal(recovery.querySelector('pre').textContent,text);
    assert.equal(recovery.querySelector('[role="status"]').textContent,'Nicht gesendeter Entwurf · JARVIS');
    assert.equal(recovery.querySelector('pre').closest('[aria-live]'),null,
      'Only the short status, not the potentially long draft, is announced automatically');
    assert.equal(f.document.body.classList.contains('native-has-draft-recovery'),true);
    assert.equal(f.navigations.length,before,'No prior-view edit is automatically sent into the new view');
    acknowledge(f,{id:command.searchParams.get('id'),accepted:true});
    assert.ok(recovery.isConnected,'An old command ACK must not discard the recovery');
    f.input('Neu getippter Bot-Text');
    f.click(recovery.querySelector('[data-native-recovery="restore"]'));
    assert.equal(field.value,'Neu getippter Bot-Text\n\n'+text,
      'Explicit recovery preserves the text already typed into the current view');
    assert.equal(f.document.querySelector('.native-draft-recovery'),null);
    assert.equal(f.document.body.classList.contains('native-has-draft-recovery'),false);
    const edit=f.navigations.findLast(url=>url.pathname==='/draft');
    assert.equal(edit.searchParams.get('viewId'),anotherView);
    assert.equal(edit.searchParams.get('value'),field.value);
  } finally {f.close();}
});

test('native control recovery requires the exact command cause and a genuinely unacknowledged edit', async () => {
  for(const variant of ['wrong-command','wrong-view','acknowledged','deliberate']) {
    const f=await fixture();
    try {
      controlState(f);f.input('@Atlas Auftrag');f.submit();
      const command=f.navigations.findLast(url=>url.pathname==='/command');
      f.input('Spätere Eingabe');
      const transitionCause={commandId:command.searchParams.get('id'),fromPageId:pageId,
        fromViewId:viewId,fromViewRevision:1,fromSessionId:sessionId,ackEditRevision:1};
      if(variant==='wrong-command')transitionCause.commandId='unrelated-command';
      if(variant==='wrong-view')transitionCause.fromViewId=anotherView;
      if(variant==='acknowledged')transitionCause.ackEditRevision=2;
      controlState(f,{viewId:anotherView,viewRevision:2,
        transitionCause:variant==='deliberate'?null:transitionCause,
        draft:{text:'Nativer Zielentwurf',selectionStart:18,selectionEnd:18}});
      assert.equal(f.document.querySelector('.native-draft-recovery'),null,variant);
      assert.equal(f.document.querySelector('#command-input').value,'Nativer Zielentwurf',variant);
    } finally {f.close();}
  }
});

test('native control successive automatic transitions keep each unresolved recovery without persisting it', async () => {
  const f=await fixture();
  try {
    controlState(f);
    const transitions=[
      {from:binding,to:{...binding,viewId:anotherView,viewRevision:2},text:'Erster zurückgehaltener Entwurf'},
      {from:{...binding,viewId:anotherView,viewRevision:2},
        to:{...binding,viewId:'44444444-4444-4444-8444-444444444444',viewRevision:3},text:'Zweiter zurückgehaltener Entwurf'}
    ];
    for(const {from,to,text} of transitions) {
      f.input('@Bot Auftrag');f.submit();
      const command=f.navigations.findLast(url=>url.pathname==='/command');
      f.input(text);
      controlState(f,{...to,transitionCause:{commandId:command.searchParams.get('id'),
        fromPageId:from.pageId,fromViewId:from.viewId,fromViewRevision:from.viewRevision,
        fromSessionId:from.sessionId,ackEditRevision:1}});
    }
    const notes=[...f.document.querySelectorAll('.native-draft-recovery')];
    assert.deepEqual(notes.map(note=>note.querySelector('pre').textContent),transitions.map(item=>item.text));
    const before=f.navigations.length;
    f.click(notes[1].querySelector('[data-native-recovery="discard"]'));
    assert.equal(f.navigations.length,before,'Discarding a recovery performs no native action');
    assert.ok(notes[0].isConnected);assert.equal(notes[1].isConnected,false);
    assert.equal(f.window.localStorage.length,0,'Recovery creates no persistent browser store');
  } finally {f.close();}
});

test('native control routes chat selection sources and PDF actions carry the exact current edit atomically', async () => {
  const f=await fixture();
  try {
    controlState(f,{chats:[{id:anotherSession,title:'Anderes Gespräch'}]});
    f.window.ContinuumNative.sources({...binding,sourceSetId,items:[
      {id:sourceId,title:'Beleg',url:'https://example.org/beleg'}]});
    f.window.ContinuumNative.document({...binding,documentId,name:'Aktuell.pdf',pageCount:1,
      textState:'EXTRACTED',text:'Text',canDraft:true,draftStatus:''});
    const field=f.document.querySelector('#command-input');
    const actions=[
      {element:'[data-action="local-file"]',kind:'/route',value:'files'},
      {element:'[data-native-chat]',kind:'/select-chat',value:anotherSession},
      {element:'[data-native-source]',kind:'/open-source',value:sourceId},
      {element:'[data-native-pdf="draft"]',kind:'/document-draft',value:documentId}
    ];
    for(let index=0;index<actions.length;index++) {
      const action=actions[index], text='  Noch nicht übertragen '+index+'\nzweite Zeile\t  ';
      // Deliberately bypass input delivery: the actual click must transport this edit.
      field.value=text;field.setSelectionRange(3,10);
      f.click(f.document.querySelector(action.element));
      const packet=f.navigations.at(-1);
      assert.equal(packet.pathname,action.kind);assert.equal(packet.searchParams.get('value'),action.value);
      assert.equal(packet.searchParams.get('draftText'),text);
      assert.equal(packet.searchParams.get('selectionStart'),'3');assert.equal(packet.searchParams.get('selectionEnd'),'10');
      assert.equal(packet.searchParams.get('clientEditRevision'),String(index+1));
      for(const key of ['pageId','viewId','viewRevision','sessionId'])
        assert.equal(packet.searchParams.get(key),String(binding[key]));
    }
    assert.equal(f.navigations.filter(url=>url.pathname==='/draft').length,0,
      'Every action has its own edit and does not depend on an earlier draft navigation');
  } finally {f.close();}
});

test('native control a scoped stop remains available for an oversized draft without inventing an empty edit', async () => {
  const f=await fixture();
  try {
    controlState(f);
    const text='x'.repeat(262145),field=f.input(text);
    f.click(f.document.querySelector('[data-action="local-file"]'));
    assert.equal(f.navigations.length,0,'An unsendable draft prevents an ordinary view action');
    const stop=f.document.createElement('button');stop.dataset.action='stop-computer';f.document.body.append(stop);
    f.click(stop);
    assert.equal(f.navigations.length,1);
    const action=f.navigations[0];assert.equal(action.pathname,'/route');
    assert.equal(action.searchParams.get('value'),'stop');
    for(const key of ['pageId','viewId','viewRevision','sessionId'])assert.equal(action.searchParams.get(key),String(binding[key]));
    assert.equal(action.searchParams.has('draftText'),false);assert.equal(action.searchParams.has('clientEditRevision'),false);
    assert.ok(field.value===text,'Stopping never truncates or clears the local draft');
  } finally {f.close();}
});

test('native control page and view admission reject malformed scopes and retired instances', async () => {
  const f=await fixture();
  try {
    controlState(f,{draft:{text:'Aktuelle Eingabe',selectionStart:4,selectionEnd:4}});
    for(const invalid of [{pageId:anotherSession,viewId:anotherView,viewRevision:99},
      {viewId:anotherView,viewRevision:1},{viewId:anotherView,viewRevision:'2'},
      {viewId:anotherView,viewRevision:1.5},{viewId:anotherView,viewRevision:-1},
      {viewId:'not-a-uuid',viewRevision:2},{viewId,viewRevision:2}]) {
      controlState(f,{...invalid,draftRevision:99,draft:{text:'Veraltet',selectionStart:0,selectionEnd:0}});
      assert.equal(f.document.querySelector('#command-input').value,'Aktuelle Eingabe');
      f.window.ContinuumNative.message({...binding,...invalid,user:false,text:'Falsche Ansicht'});
    }
    assert.doesNotMatch(f.document.querySelector('#workspace').textContent,/Falsche Ansicht/);
    controlState(f,{viewId:anotherView,viewRevision:2});
    controlState(f,{viewId:'44444444-4444-4444-8444-444444444444',viewRevision:3,
      draft:{text:'Zurück im Hauptchat',selectionStart:0,selectionEnd:0}});
    controlState(f,{draftRevision:500,draft:{text:'Alte Instanz des Hauptchats',selectionStart:0,selectionEnd:0}});
    assert.equal(f.document.querySelector('#command-input').value,'Zurück im Hauptchat');
  } finally {f.close();}
});

test('native control microphone long press uses the existing native action once and cancellation suppresses it', async () => {
  const f = await fixture();
  try {
    controlState(f, {voiceMode: {active: true, paused: false}, longPressTimeoutMs: 500});
    const mic = f.document.querySelector('#listen-button');
    const pointer = type => {
      const event = new f.window.MouseEvent(type, {bubbles: true, cancelable: true, button: 0});
      Object.defineProperty(event, 'pointerId', {value: 1});mic.dispatchEvent(event);
    };
    pointer('pointerdown');await f.timers(501);pointer('pointerup');f.click(mic);
    const actions = f.navigations.filter(url => url.pathname === '/route');
    assert.equal(actions.length, 1);
    assert.equal(actions[0].searchParams.get('value'), 'voice-input-long-press');
    assert.equal(actions[0].searchParams.get('pageId'), pageId);
    pointer('pointerdown');pointer('pointercancel');await f.timers(501);
    assert.equal(f.navigations.filter(url => url.pathname === '/route').length, 1);
    pointer('pointerdown');f.window.dispatchEvent(new f.window.Event('blur'));await f.timers(501);
    assert.equal(f.navigations.filter(url => url.pathname === '/route').length, 1);
    assert.match(mic.getAttribute('aria-label'), /Gesprächsmodus/);
    pointer('pointerdown');controlState(f,{viewId:anotherView,viewRevision:2});
    await f.timers(501);pointer('pointerup');f.click(mic);
    assert.equal(f.navigations.filter(url => url.pathname === '/route').length, 1,
      'A press from a retired view cannot become a long press or a trailing tap in the new view');
    pointer('pointerdown');f.window.dispatchEvent(new f.window.Event('pagehide'));
    await f.timers(501);pointer('pointerup');f.click(mic);
    assert.equal(f.navigations.filter(url => url.pathname === '/route').length, 1);
    pointer('pointerdown');pointer('pointerup');f.click(mic);
    assert.equal(f.navigations.filter(url => url.pathname === '/route').length, 2);
    assert.equal(f.navigations.at(-1).searchParams.get('value'),'voice-input');
    assert.equal(f.navigations.at(-1).searchParams.get('viewId'),anotherView);
  } finally {f.close();}
});

test('native control source cards use native identifiers and cannot survive a view switch', async () => {
  const f = await fixture();
  try {
    controlState(f);
    f.window.ContinuumNative.message({...binding, user: false, text: 'Antwort mit Quelle'});
    f.window.ContinuumNative.sources({...binding, sourceSetId, items: [
      {id: sourceId, title: '<script>Quelle</script>', url: 'https://example.org/beleg'}]});
    const button = f.document.querySelector('[data-native-source]');assert.ok(button);
    assert.equal(button.textContent.includes('<script>Quelle</script>'), true);
    assert.equal(button.querySelector('script'), null);
    f.click(button);
    const action = f.navigations.at(-1);
    assert.equal(action.pathname, '/open-source');assert.equal(action.searchParams.get('value'), sourceId);
    assert.equal(action.searchParams.has('url'), false);
    assert.equal(action.searchParams.get('viewId'), viewId);
    controlState(f, {viewId: anotherView, viewRevision: 2});
    f.window.ContinuumNative.sources({...binding, sourceSetId: anotherSession, items: [
      {id: sourceId, title: 'Alte Quelle', url: 'https://example.org/alt'}]});
    assert.equal(f.document.querySelector('[data-native-source]'), null);
  } finally {f.close();}
});

test('native control source cards reject unsafe displayed URLs and forged identifiers', async () => {
  const f=await fixture();
  try {
    controlState(f);
    for(const url of ['javascript:alert(1)','data:text/html,test','file:///private.pdf',
      'https://user:password@example.org/private']) {
      f.window.ContinuumNative.sources({...binding,sourceSetId,items:[{id:sourceId,title:'Quelle',url}]});
      assert.equal(f.document.querySelector('[data-native-source]'),null,url);
    }
    const forged=f.document.createElement('button');forged.dataset.nativeSource=sourceId;f.document.body.append(forged);
    f.click(forged);assert.equal(f.navigations.length,0);
    f.window.ContinuumNative.sources({...binding,sourceSetId,items:[
      {id:sourceId,title:'<img src=x onerror=alert(1)>',url:'https://example.org/beleg'}]});
    const card=f.document.querySelector('.native-source-card');
    assert.equal(card.querySelector('img'),null);assert.equal(card.querySelector('a'),null);
    assert.match(card.textContent,/<img src=x onerror=alert\(1\)>/);
    const real=card.querySelector('[data-native-source]');f.click(real);
    const action=f.navigations.at(-1);assert.equal(action.searchParams.get('value'),sourceId);
    assert.equal(action.href.includes('example.org'),false,'Only the opaque native identifier crosses the action boundary');
  } finally {f.close();}
});

test('native control PDF draft action identifies only the currently displayed document and honours native availability', async () => {
  const f = await fixture();
  try {
    controlState(f);
    const doc = {...binding, documentId, name: 'Echt.pdf', pageCount: 1,
      textState: 'EXTRACTED', text: 'Echter Dokumenttext', canDraft: true, draftStatus: 'Entwurf möglich'};
    f.window.ContinuumNative.document(doc);
    let draft = f.document.querySelector('[data-native-pdf="draft"]');assert.ok(draft);
    f.click(draft);
    assert.equal(f.navigations.at(-1).pathname, '/document-draft');
    assert.equal(f.navigations.at(-1).searchParams.get('value'), documentId);
    assert.equal(f.navigations.at(-1).searchParams.get('pageId'), pageId);
    controlState(f,{draftRevision:2});
    assert.equal(draft.disabled,false,'A rejected stale edit must not leave an optimistic permanent pending lock');
    f.window.ContinuumNative.clearDocument({...binding,viewId:anotherView,viewRevision:2});
    assert.ok(f.document.querySelector('#native-document-panel'),'An unrelated view cannot clear the current PDF');
    f.window.ContinuumNative.document({...doc, documentId: anotherDocument, canDraft: false,
      draftStatus: 'Lokales Modell nicht geladen'});
    await f.timers(1000);await f.paint();
    draft = f.document.querySelector('[data-native-pdf="draft"]');
    assert.ok(!draft || draft.disabled);
    const count = f.navigations.length;if (draft) f.click(draft);
    assert.equal(f.navigations.length, count);
    assert.match(f.document.querySelector('#native-document-panel').textContent, /Lokales Modell nicht geladen/);
  } finally {f.close();}
});

test('native control changing only the view fences pending image decoding and speech before accepting current media', async () => {
  const f=await fixture();
  try {
    controlState(f,{speaking:true});
    f.window.ContinuumNative.computer(computerFrame());
    f.window.ContinuumNative.speech(speechFrame());
    const clears=f.speechClears;
    const bot={...binding,viewId:anotherView,viewRevision:2};
    controlState(f,{...bot,viewKind:'BOT',viewLabel:'Atlas'});
    assert.equal(f.document.querySelector('.avatar-crop').dataset.speechActive,'false');
    assert.ok(f.speechClears>clears,'A view change clears the actual public avatar speech API');
    await f.decode(0);f.advance(950);await f.paint();await f.paint();
    assert.equal(f.document.querySelector('.native-computer-image'),null);
    assert.equal(acknowledgements(f).length,0,'A decoded old-view frame cannot be acknowledged under the new scope');
    f.window.ContinuumNative.speech(speechFrame({sequence:2}));
    assert.equal(f.speechFrames.length,1,'An old-view mouth pose cannot resume');
    f.window.ContinuumNative.computer(computerFrame({...bot}));
    await f.decode(1);f.advance(950);await f.paint();await f.paint();await f.paint();
    const ack=acknowledgements(f).at(-1);assert.ok(ack);
    assert.equal(ack.searchParams.get('viewId'),anotherView);
    assert.equal(ack.searchParams.get('viewRevision'),'2');
    assert.equal(ack.searchParams.get('pageId'),pageId);
    f.window.ContinuumNative.speech(speechFrame({...bot}));
    assert.equal(f.speechFrames.length,2,'A genuinely current view starts its own pose sequence');
  } finally {f.close();}
});


test('same-view binding contradictions cannot disable native actions or overwrite a draft', async () => {
  const f=await fixture();
  try {
    f.state();
    const field=f.input('Aktueller Auftrag');
    for(const invalid of [
      {...binding,viewRevision:2},
      {...binding,sessionId:anotherSession},
      {...binding,viewRevision:2,sessionId:anotherSession},
      {...binding,viewId:anotherView}
    ]) {
      f.window.ContinuumNative.receive({...invalid,draftRevision:99,
        draft:{text:'Unerlaubter Entwurf',selectionStart:0,selectionEnd:0}});
      assert.equal(field.value,'Aktueller Auftrag');
    }
    f.submit();
    const previous=f.navigations.findLast(url=>url.pathname==='/command');
    assert.ok(previous);
    assert.equal(previous.searchParams.get('viewId'),viewId);
    assert.equal(previous.searchParams.get('viewRevision'),'1');
    assert.equal(previous.searchParams.get('sessionId'),sessionId);
    acknowledge(f,{id:previous.searchParams.get('id'),accepted:false,reason:'stale-edit'});
    assert.match(f.document.querySelector('#toast').textContent,/Entwurf wurde inzwischen geändert/);
    assert.equal(field.value,'Aktueller Auftrag');
    f.state({viewId:anotherView,viewRevision:2,viewKind:'BOT',viewLabel:'Atlas',
      draft:{text:'Bot-Entwurf',selectionStart:11,selectionEnd:11}});
    assert.equal(field.value,'Bot-Entwurf');
    f.submit();
    const next=f.navigations.findLast(url=>url.pathname==='/command');
    assert.equal(next.searchParams.get('viewId'),anotherView);
    assert.equal(next.searchParams.get('viewRevision'),'2');
  } finally {f.close();}
});

test('settings have a named header button and four unique shortcuts', async () => {
  const f=await fixture();
  try {
    f.state();f.window.dispatchEvent(new f.window.Event('load'));
    const header=f.document.querySelector('.header-tools>[data-action="settings"]');
    assert.match(header.textContent,/Einstellungen/);
    assert.equal(header.getAttribute('aria-label'),'Alle Einstellungen öffnen');
    assert.equal(f.document.querySelector('#main-nav').children[1].dataset.section,'settings');
    assert.equal(f.document.querySelectorAll('.native-settings-shortcuts').length,1);
    assert.deepEqual([...f.document.querySelectorAll('.native-setting-shortcut')].map(b=>b.dataset.action),
      ['settings-voice','settings-model','settings-internet','settings-appearance']);
    assert.equal(f.document.querySelectorAll('.native-holo-field').length,1);
  } finally {f.close();}
});

test('settings shortcuts carry the exact draft and scope without starting a command', async () => {
  const f=await fixture();
  try {
    f.state();const input=f.input('Mein ungesendeter\nAuftrag');input.setSelectionRange(5,12);
    for(const button of f.document.querySelectorAll('.native-setting-shortcut')) {
      f.click(button);const route=f.navigations.at(-1);
      assert.equal(route.pathname,'/route');
      assert.equal(route.searchParams.get('value'),button.dataset.action);
      assert.equal(route.searchParams.get('draftText'),input.value);
      assert.equal(route.searchParams.get('selectionStart'),'5');
      assert.equal(route.searchParams.get('selectionEnd'),'12');
      assert.equal(route.searchParams.get('viewId'),viewId);
      assert.equal(route.searchParams.get('pageId'),pageId);
    }
    assert.equal(f.navigations.some(url=>url.pathname==='/command'),false);
  } finally {f.close();}
});

test('unbound settings never navigate and native appearance rejects stale pages', async () => {
  const f=await fixture();
  try {
    f.click(f.document.querySelector('[data-action="settings-voice"]'));
    assert.equal(f.navigations.length,0);
    f.state({appearance:{neon:35,lightMotion:false}});
    assert.equal(f.document.body.style.getPropertyValue('--native-neon'),'0.35');
    assert.equal(f.document.body.dataset.nativeLightMotion,'off');
    f.window.ContinuumNative.receive({...binding,pageId:anotherView,appearance:{neon:100,lightMotion:true}});
    assert.equal(f.document.body.style.getPropertyValue('--native-neon'),'0.35');
    assert.equal(f.document.body.dataset.nativeLightMotion,'off');
    f.state({appearance:{neon:999,lightMotion:true}});
    assert.equal(f.document.body.style.getPropertyValue('--native-neon'),'1');
    f.state({appearance:{neon:-50,lightMotion:true}});
    assert.equal(f.document.body.style.getPropertyValue('--native-neon'),'0');
  } finally {f.close();}
});

test('additional light motion pauses with the hidden page', async () => {
  const f=await fixture();
  try {
    f.state();
    Object.defineProperty(f.document,'visibilityState',{configurable:true,value:'hidden'});
    f.document.dispatchEvent(new f.window.Event('visibilitychange'));
    assert.equal(f.document.body.classList.contains('native-light-paused'),true);
    Object.defineProperty(f.document,'visibilityState',{configurable:true,value:'visible'});
    f.document.dispatchEvent(new f.window.Event('visibilitychange'));
    assert.equal(f.document.body.classList.contains('native-light-paused'),false);
  } finally {f.close();}
});

// The final integration gate requires fresh JSON captured from the actual Kotlin
// Stage evaluateJavascript output. Its image decoder remains the explicit seam above.
if (process.env.COMMAND03_REQUIRE_NATIVE_PACKET === '1' || process.env.COMMAND03_NATIVE_COMPUTER_PACKET) {
  test('the actual Kotlin Stage computer JSON reaches the original window and its exact render acknowledgement', async () => {
    assert.ok(process.env.COMMAND03_NATIVE_COMPUTER_PACKET,
      'Final gate requires COMMAND03_NATIVE_COMPUTER_PACKET from the fresh Stage UI-test sentinel');
    const packet=JSON.parse(readFileSync(process.env.COMMAND03_NATIVE_COMPUTER_PACKET,'utf8'));
    assert.equal(typeof packet.captureSession,'string','The actual Kotlin Long must preserve its string wire type');
    const f = await fixture();
    try {
      f.state({sessionId:packet.sessionId,pageId:packet.pageId,viewId:packet.viewId,
        viewRevision:packet.viewRevision});f.window.ContinuumNative.computer(packet);
      assert.equal(f.decodings.length,1,'The unchanged producer payload must pass the adapter contract');
      await f.decode();assert.equal(f.document.querySelector('.native-computer-image').src,packet.frameDataUri);
      f.advance(950);await f.paint();await f.paint();await f.paint();
      const ack=acknowledgements(f).at(-1);assert.ok(ack);
      assert.equal(ack.searchParams.get('sessionId'),packet.sessionId);
      assert.equal(ack.searchParams.get('pageId'),packet.pageId);
      assert.equal(ack.searchParams.get('viewId'),packet.viewId);
      assert.equal(ack.searchParams.get('viewRevision'),String(packet.viewRevision));
      assert.equal(ack.searchParams.get('generation'),String(packet.generation));
      assert.equal(ack.searchParams.get('sequence'),String(packet.sequence));
    } finally {f.close();}
  });
}

test('cinematic instruments replace demonstration metrics and follow native state', async () => {
 const f=await fixture();
 try {
  controlState(f);
  const text=()=>f.document.querySelector('.presence-system').textContent;
  assert.doesNotMatch(text(),/Argon4|Demonstration|Entwurfswerte|480/);
  assert.match(text(),/Noch keine Quellen/);
  assert.equal(f.window.ContinuumPresenceInstruments.getStatus().disposed,true);
  for(const [values,label] of [[{listening:true},'Ich höre zu'],[{busy:true},'In Bearbeitung'],
    [{speaking:true},'Ich spreche'],[{},'Bereit']]) {
   f.state(values);
   assert.equal(f.document.querySelector('.native-activity>strong').textContent,label);
  }
  assert.equal(f.document.querySelectorAll('.native-projection-base').length,1);
 } finally {f.close();}
});
test('cinematic source panels use scoped links and clear on chat switch', async () => {
 const f=await fixture();
 try {
  controlState(f);
  const item={id:sourceId,title:'<img src=x onerror=alert(1)>',url:'https://example.org/evidence'};
  f.window.ContinuumNative.sources({...binding,sourceSetId,items:[item]});
  const panel=f.document.querySelector('.native-evidence');
  assert.equal(panel.querySelector('strong').textContent,'1 verknüpft');
  assert.equal(panel.querySelector('img'),null);
  f.click(panel.querySelector('[data-native-source]'));
  assert.equal(f.navigations.at(-1).pathname,'/open-source');
  assert.equal(f.navigations.at(-1).searchParams.get('value'),sourceId);
  f.state({sessionId:anotherSession});
  assert.equal(panel.querySelector('strong').textContent,'Noch keine Quellen');
  f.window.ContinuumNative.sources({...binding,sourceSetId,items:[item]});
  assert.equal(panel.querySelector('[data-native-source]'),null);
  assert.doesNotMatch(f.document.querySelector('#instrument-content').textContent,/img src/);
 } finally {f.close();}
});
test('busy composer exposes native Stop without losing a newer draft', async () => {
 const f=await fixture();
 try {
  controlState(f,{busy:true});f.input('Nächster Auftrag');
  const stop=f.document.querySelector('.native-stop-button');
  assert.equal(stop.hidden,false);
  assert.equal(f.document.querySelector('.send-button').hidden,true);
  f.click(stop);
  const route=f.navigations.at(-1);
  assert.equal(route.pathname,'/route');assert.equal(route.searchParams.get('value'),'stop');
  assert.equal(route.searchParams.get('draftText'),'Nächster Auftrag');
  f.state({busy:false});
  assert.equal(stop.hidden,true);
  assert.equal(f.document.querySelector('.send-button').hidden,false);
  assert.equal(f.document.querySelector('#command-input').value,'Nächster Auftrag');
 } finally {f.close();}
});

// Optional presence, quality and activity fields of the Android snapshot. Every case
// below sends only what a real snapshot may carry; nothing is derived inside the page.
const activityPacket = (extra = {}) => ({revision: 1, kind: 'RESEARCH', title: 'Dateisuche',
  detail: 'Durchsuche freigegebene Dokumente.', active: true,
  steps: [{label: 'Gedächtnis durchsucht', state: 'DONE'}, {label: 'Websuche', state: 'ACTIVE'}], ...extra});
const activityCard = f => f.document.querySelector('.native-activity');
const stepRows = f => [...f.document.querySelectorAll('.native-activity-steps li')].map(item => ({
  state: item.dataset.state, label: item.querySelector('.native-step-label').textContent,
  text: item.querySelector('.native-step-state').textContent}));

test('native presence selects the original scene, the orb mode and a truthful status text', async () => {
  const f = await fixture();
  try {
    const seen = () => ({scene: f.scenes.at(-1), state: f.document.body.dataset.state,
      presence: f.document.body.dataset.presence, text: f.document.querySelector('#orb-status').textContent,
      orb: f.sceneEvents.at(-1)?.state});
    // The scene double emits nothing, so every recorded event is the bridge's own orb announcement.
    f.state({presence: 'IDLE'});
    assert.deepEqual(seen(), {scene: 'idle', state: 'idle', presence: 'idle', text: 'BEREIT', orb: undefined});
    f.state({presence: 'LISTENING'});
    assert.deepEqual(seen(), {scene: 'listening', state: 'listening', presence: 'listening', text: 'HÖRT ZU', orb: undefined});
    f.state({presence: 'THINKING', busy: true});
    assert.deepEqual(seen(), {scene: 'working', state: 'working', presence: 'thinking', text: 'DENKT', orb: undefined});
    f.state({presence: 'RESEARCHING', busy: true});
    assert.deepEqual(seen(), {scene: 'working', state: 'working', presence: 'researching', text: 'RECHERCHIERT', orb: 'research'});
    assert.deepEqual(f.sceneEvents.at(-1), {state: 'research'}, 'Only the state name is announced, never a light or audio value');
    f.state({presence: 'LEARNING', busy: true});
    assert.deepEqual(seen(), {scene: 'working', state: 'working', presence: 'learning', text: 'LERNT', orb: 'learning'});
    const announced = f.sceneEvents.length;
    f.state({presence: 'LEARNING', busy: true, cpu: 55});
    assert.equal(f.sceneEvents.length, announced, 'An unchanged presence is not announced again');
    f.state({presence: 'THINKING', busy: true});
    assert.deepEqual(seen(), {scene: 'working', state: 'working', presence: 'thinking', text: 'DENKT', orb: 'working'});
    f.state({presence: 'SPEAKING', busy: true});
    assert.deepEqual(seen(), {scene: 'speaking', state: 'speaking', presence: 'speaking', text: 'SPRICHT', orb: 'working'});
    f.state({presence: 'WAITING_FOR_APPROVAL', busy: true});
    assert.deepEqual(seen(), {scene: 'idle', state: 'idle', presence: 'waiting-for-approval',
      text: 'WARTET AUF FREIGABE', orb: 'working'});
    f.state({presence: 'BLOCKED', busy: true});
    assert.deepEqual(seen(), {scene: 'idle', state: 'idle', presence: 'blocked', text: 'BLOCKIERT', orb: 'working'});
    assert.equal(f.scenes.includes('research') || f.scenes.includes('learning'), false,
      'The scene and avatar only ever receive names from their own state list');
    assert.equal(f.window.ContinuumOperations.getState(), null);
  } finally {f.close();}
});

test('snapshots without presence render as before and an invalid presence is ignored', async () => {
  const f = await fixture();
  try {
    const status = f.document.querySelector('#orb-status');
    for (const [values, scene, text, label] of [[{busy: true}, 'working', 'ARBEITET', 'In Bearbeitung'],
      [{listening: true}, 'listening', 'HÖRT ZU', 'Ich höre zu'], [{speaking: true}, 'speaking', 'SPRICHT', 'Ich spreche'],
      [{}, 'idle', 'BEREIT', 'Bereit']]) {
      f.state(values);
      assert.equal(f.scenes.at(-1), scene);assert.equal(status.textContent, text);
      assert.equal(activityCard(f).querySelector('strong').textContent, label);
      assert.equal('presence' in f.document.body.dataset, false);
    }
    for (const presence of ['DREAMING', 'thinking', 'constructor', '__proto__', '', 42, null, {}, ['BLOCKED']]) {
      f.state({busy: true, presence});
      assert.equal(f.scenes.at(-1), 'working');assert.equal(status.textContent, 'ARBEITET');
      assert.equal('presence' in f.document.body.dataset, false);
      assert.equal(activityCard(f).querySelector('strong').textContent, 'In Bearbeitung');
    }
    assert.equal(f.sceneEvents.length, 0);assert.equal(f.gazes.length, 0);
    f.state({busy: true, presence: 'RESEARCHING'});
    assert.equal(f.document.body.dataset.presence, 'researching');
    f.state({busy: true});
    assert.equal('presence' in f.document.body.dataset, false, 'A later snapshot without presence returns to the booleans');
    assert.equal(status.textContent, 'ARBEITET');assert.equal(f.sceneEvents.at(-1).state, 'working');
  } finally {f.close();}
});

test('waiting for approval and blocked are calm: no work animation, the avatar faces the owner, Stop stays', async () => {
  const f = await fixture();
  try {
    f.state({busy: true, presence: 'THINKING'});
    assert.equal(activityCard(f).dataset.mode, 'working');
    f.state({busy: true, presence: 'WAITING_FOR_APPROVAL', status: ''});
    assert.equal(activityCard(f).dataset.mode, 'idle');
    assert.equal(activityCard(f).querySelector('strong').textContent, 'Rückfrage');
    assert.equal(activityCard(f).querySelector('p').textContent, 'Der Auftrag wartet auf deine Freigabe.');
    assert.equal(f.document.querySelector('.native-stop-button').hidden, false);
    assert.equal(f.gazes.length, 1);
    assert.equal(f.gazes[0].target, f.window);
    assert.deepEqual(f.gazes[0].detail, {x: 0, y: 0, durationMs: 2200});
    f.state({busy: true, presence: 'WAITING_FOR_APPROVAL', cpu: 44});
    assert.equal(f.gazes.length, 1, 'The neutral gaze is sent once per change, not per snapshot');
    // Real content arriving while the owner is asked must not pull the eyes away.
    f.window.ContinuumNative.document({...binding, documentId, name: 'Beispiel.pdf', pageCount: 1});
    f.window.ContinuumNative.sources({...binding, sourceSetId, items: [{id: sourceId, title: 'Quelle', url: 'https://example.org/a'}]});
    assert.equal(f.gazes.length, 1);
    f.state({busy: true, presence: 'BLOCKED', status: 'Speicherzugriff fehlt'});
    assert.equal(activityCard(f).querySelector('strong').textContent, 'Blockiert');
    assert.equal(activityCard(f).querySelector('p').textContent, 'Speicherzugriff fehlt');
    assert.equal(f.gazes.length, 2);assert.deepEqual(f.gazes[1].detail, {x: 0, y: 0, durationMs: 2200});
    const pill = f.document.querySelector('.core-status');
    assert.equal(pill.getAttribute('role'), 'status');assert.equal(pill.getAttribute('aria-live'), 'polite');
    const node = f.document.querySelector('#orb-status').firstChild;
    f.state({busy: true, presence: 'BLOCKED', status: 'Speicherzugriff fehlt', cpu: 9});
    assert.equal(f.document.querySelector('#orb-status').firstChild, node, 'An unchanged status is not announced again');
  } finally {f.close();}
});

test('the byte-locked scene and avatar keep valid states while the orb mode survives light and audio events', async () => {
  const f = await fixture({realScene: true, realAvatar: true});
  try {
    const scene = f.window.ContinuumScene;
    f.state({busy: true, presence: 'RESEARCHING'});
    assert.equal(scene.getState(), 'working');
    assert.equal(f.window.ContinuumAvatar.getStatus().state, 'working');
    assert.equal(f.sceneEvents.at(-2).state, 'working', 'The original scene announces its own state first');
    assert.deepEqual(f.sceneEvents.at(-1), {state: 'research'});
    // The scene repeats its coarser state with every light, detail or audio change.
    f.state({busy: true, presence: 'RESEARCHING', appearance: {neon: 100, lightMotion: true}, quality: 'BALANCED'});
    await f.flush();
    // Code68: while the on-device model computes the scene runs on its lowest setting, whatever the tier.
    assert.equal(scene.getStatus().intensity, 1);assert.equal(scene.getStatus().quality, .6);
    f.state({busy: true, presence: 'RESEARCHING', appearance: {neon: 100, lightMotion: true}, quality: 'BALANCED',
      cloudBrain: true});
    assert.equal(scene.getStatus().quality, .8, 'With a cloud brain the tier\'s own setting applies');
    f.state({busy: true, presence: 'RESEARCHING', appearance: {neon: 100, lightMotion: true}, quality: 'BALANCED'});
    assert.deepEqual(f.sceneEvents.at(-1), {state: 'research'});
    f.advance(40);scene.setAudioLevel(.5); // beyond the scene's own 33 ms event throttle
    assert.equal(f.sceneEvents.at(-1).state, 'working');
    assert.equal(f.sceneEvents.at(-1).audioLevel, .5);
    await f.flush();
    assert.deepEqual(f.sceneEvents.at(-1), {state: 'research'});
    assert.equal(f.window.ContinuumAvatar.getStatus().state, 'working');
    f.state({busy: true, presence: 'LEARNING', appearance: {neon: 100, lightMotion: true}, quality: 'BALANCED'});
    assert.deepEqual(f.sceneEvents.at(-1), {state: 'learning'});
    f.state({busy: true, presence: 'THINKING', appearance: {neon: 100, lightMotion: true}, quality: 'BALANCED'});
    assert.deepEqual(f.sceneEvents.at(-1), {state: 'working'}, 'Leaving research is announced although the scene state is unchanged');
    const settled = f.sceneEvents.length;
    scene.setAudioLevel(null);await f.flush();
    assert.equal(f.sceneEvents.length, settled + 1, 'Without a refined mode the bridge adds no event of its own');
  } finally {f.close();}
});

test('the real speech level reaches the scene and returns to no signal when speech ends', async () => {
  const f = await fixture();
  try {
    f.state({speaking: true});
    assert.deepEqual(f.audioLevels, [], 'No level is invented before Android sends a pose');
    for (const bad of [{viseme: 'UNKNOWN'}, {jawOpen: 1.4}, {jawOpen: NaN}, {sessionId: anotherSession}])
      f.window.ContinuumNative.speech(speechFrame(bad));
    assert.deepEqual(f.audioLevels, []);
    f.window.ContinuumNative.speech(speechFrame());
    assert.deepEqual(f.audioLevels, [.45]);
    f.window.ContinuumNative.speech(speechFrame({sequence: 2, viseme: 'PP', jawOpen: 0}));
    f.window.ContinuumNative.speech(speechFrame({sequence: 3, viseme: 'AA', jawOpen: 1}));
    assert.deepEqual(f.audioLevels, [.45, 0, 1]);
    await f.timers(101);
    assert.deepEqual(f.audioLevels, [.45, 0, 1, null], 'An expired pose means no signal, not a held level');
    f.window.ContinuumNative.speech(speechFrame({sequence: 4, jawOpen: .2}));
    f.window.ContinuumNative.speech({...binding, generation: 1, sequence: 5, active: false});
    assert.deepEqual(f.audioLevels, [.45, 0, 1, null, .2, null]);
    f.state({sessionId: anotherSession});
    assert.deepEqual(f.audioLevels, [.45, 0, 1, null, .2, null], 'Silence is reported once');
  } finally {f.close();}
});

test('real documents, sources and device frames turn the avatar to the element that shows them', async () => {
  const f = await fixture();
  try {
    f.state();
    const item = {id: sourceId, title: 'Quelle', url: 'https://example.org/evidence'};
    f.window.ContinuumNative.sources({...binding, sourceSetId, items: [item]});
    assert.equal(f.gazes.length, 1);
    const card = f.document.querySelector('#native-live-panel .native-source-card');
    assert.ok(card);assert.equal(f.gazes[0].target, card);
    assert.deepEqual(Object.keys(f.gazes[0].detail), ['target'], 'Same event shape as the original operations script');
    assert.equal(f.gazes[0].detail.target, card);
    f.window.ContinuumNative.sources({...binding, sourceSetId, items: []});
    f.window.ContinuumNative.sources({...binding, sessionId: anotherSession, sourceSetId, items: [item]});
    assert.equal(f.gazes.length, 1, 'Withdrawn or foreign sources are no reason to look');
    f.window.ContinuumNative.document({...binding, documentId, name: 'Beispiel.pdf', pageCount: 2});
    assert.equal(f.gazes.length, 2);
    assert.equal(f.gazes[1].target, f.document.querySelector('#native-document-panel'));
    assert.equal(f.gazes[1].detail.target, f.gazes[1].target);
    f.window.ContinuumNative.document({...binding, documentId, name: 'Beispiel.pdf', pageCount: 2, draftStatus: 'Entwurf bereit'});
    f.window.ContinuumNative.document({...binding, viewId: anotherView, documentId: anotherDocument, name: 'Alt.pdf', pageCount: 1});
    assert.equal(f.gazes.length, 2, 'A refresh of the shown document or a stale document does not look again');
    // While the PDF covers the work panel, the visible source display is the QUELLEN instrument.
    f.window.ContinuumNative.sources({...binding, sourceSetId, items: [item]});
    assert.equal(f.gazes.length, 3);
    assert.equal(f.gazes[2].target, f.document.querySelector('[data-presence-right] .native-evidence'));
    f.window.ContinuumNative.clearDocument(binding);
    f.window.ContinuumNative.computer(computerFrame());
    assert.equal(f.gazes.length, 3, 'An undecoded frame shows nothing yet');
    await f.decode();
    assert.equal(f.gazes.length, 4);
    assert.equal(f.gazes[3].target, f.document.querySelector('#native-computer-panel'));
    assert.ok(f.gazes[3].target.querySelector('.native-computer-image'));
    f.window.ContinuumNative.computer(computerFrame({sequence: 2}));await f.decode();
    assert.equal(f.gazes.length, 4, 'A continuous stream does not hold the gaze');
    f.window.ContinuumNative.computer(computerFrame({sequence: 3, frameDataUri: 'data:text/html;base64,AAAA'}));
    assert.equal(f.gazes.length, 4);
  } finally {f.close();}
});

test('the hologram light level reaches the original scene only when it changes', async () => {
  const f = await fixture();
  try {
    const before = f.intensities.length;
    f.state();f.state({cpu: 31});
    assert.equal(f.intensities.length, before, 'Without an Android level the preview default stays untouched');
    f.state({appearance: {neon: 70, lightMotion: true}});
    assert.deepEqual(f.intensities.slice(before), [.78], 'The Android default keeps the present scene light');
    f.state({appearance: {neon: 70, lightMotion: false}});f.state({appearance: {neon: 70, lightMotion: true}, cpu: 8});
    assert.deepEqual(f.intensities.slice(before), [.78]);
    f.state({appearance: {neon: 100, lightMotion: true}});f.state({appearance: {neon: 0, lightMotion: true}});
    f.state({appearance: {neon: 35, lightMotion: true}});f.state({appearance: {neon: 999, lightMotion: true}});
    f.state({appearance: {neon: -5, lightMotion: true}});
    assert.deepEqual(f.intensities.slice(before), [.78, 1, .35, .565, 1, .35]);
    for (const value of f.intensities.slice(before)) assert.ok(value >= .35 && value <= 1.6, 'Inside the scene range');
    f.state({appearance: {neon: 'bright', lightMotion: true}});
    assert.equal(f.intensities.at(-1), .78, 'An unusable level follows the same default as the page light');
    assert.equal(f.document.body.style.getPropertyValue('--native-neon'), '0.7');
    f.window.ContinuumNative.receive({...binding, pageId: anotherView, appearance: {neon: 100, lightMotion: true}});
    assert.equal(f.intensities.at(-1), .78, 'A stale page cannot change the light');
  } finally {f.close();}
});

test('the Android quality tier is exposed for styling and mapped to the scene detail only on change', async () => {
  const f = await fixture();
  try {
    const before = f.sceneQualities.length, body = f.document.body;
    f.state();
    assert.equal('nativeQuality' in body.dataset, false);assert.equal(f.sceneQualities.length, before);
    for (const invalid of ['ULTRA', 'reduced', 'constructor', 2, null, {}]) {
      f.state({quality: invalid});
      assert.equal('nativeQuality' in body.dataset, false);assert.equal(f.sceneQualities.length, before);
    }
    f.state({quality: 'FULL'});assert.equal(body.dataset.nativeQuality, 'full');
    f.state({quality: 'FULL', cpu: 70});
    f.state({quality: 'BALANCED'});assert.equal(body.dataset.nativeQuality, 'balanced');
    f.state({quality: 'REDUCED'});assert.equal(body.dataset.nativeQuality, 'reduced');
    f.state({quality: 'REDUCED', busy: true});
    assert.deepEqual(f.sceneQualities.slice(before), ['high', 'medium', 'low']);
    // Code68: local computing lowers the scene once and the tier's setting returns once afterwards.
    f.state({quality: 'FULL', busy: true});f.state({quality: 'FULL', busy: true, cpu: 71});
    f.state({quality: 'FULL'});
    assert.deepEqual(f.sceneQualities.slice(before), ['high', 'medium', 'low', 'high']);
    f.state({quality: 'REDUCED', busy: true});
    // Nothing is hidden by a tier: content, controls, avatar and orb stay in the document flow.
    for (const selector of ['.avatar-crop', '.orb-zone', '#command-form', '.native-activity', '.native-evidence',
      '.native-holo-field', '.native-stop-button', '.native-settings-shortcuts'])
      assert.notEqual(f.window.getComputedStyle(f.document.querySelector(selector)).display, 'none', selector);
    f.state({quality: 'MELTING'});
    assert.equal('nativeQuality' in body.dataset, false, 'An invalid tier is not kept as a stale attribute');
    assert.deepEqual(f.sceneQualities.slice(before), ['high', 'medium', 'low', 'high', 'low', 'high']);
  } finally {f.close();}
});

test('a real Android activity fills the activity card with its title, detail and text-only steps', async () => {
  const f = await fixture();
  try {
    f.state({busy: true, presence: 'RESEARCHING', status: 'Recherche läuft', activity: activityPacket()});
    const card = activityCard(f);
    assert.equal(card.querySelector('h3').textContent, 'AKTIVITÄT · RECHERCHE');
    assert.equal(card.querySelector('strong').textContent, 'Dateisuche');
    assert.equal(card.querySelector('p').textContent, 'Durchsuche freigegebene Dokumente.');
    assert.equal(card.dataset.activityKind, 'research');assert.equal(card.dataset.activityActive, 'true');
    assert.equal(card.dataset.mode, 'working');
    assert.deepEqual(stepRows(f), [{state: 'done', label: 'Gedächtnis durchsucht', text: 'erledigt'},
      {state: 'active', label: 'Websuche', text: 'läuft'}]);
    const list = card.querySelector('.native-activity-steps');
    assert.equal(list.tagName, 'OL');assert.equal(list.getAttribute('aria-label'), 'Arbeitsschritte');
    assert.match(f.document.querySelector('.native-instrument-summary').textContent, /^Dateisuche · Durchsuche/);
    const markup = '<img src=x onerror=alert(1)><b>Titel</b>';
    f.state({busy: true, activity: activityPacket({revision: 2, kind: 'TOOL', title: markup, detail: '<script>x</script>',
      steps: [{label: '<i>Schritt</i>', state: 'FAILED'}, {label: 'Freigabe', state: 'WAITING'},
        {label: 'Sperre', state: 'BLOCKED'}, {label: 'Prüfung', state: 'UNVERIFIED'}]})});
    assert.equal(card.querySelector('strong').textContent, markup);
    assert.equal(card.querySelector('p').textContent, '<script>x</script>');
    assert.equal(card.querySelector('img,b,i,script'), null, 'Android strings never become elements');
    assert.equal(card.querySelector('h3').textContent, 'AKTIVITÄT · WERKZEUG');
    assert.deepEqual(stepRows(f), [{state: 'failed', label: '<i>Schritt</i>', text: 'fehlgeschlagen'},
      {state: 'waiting', label: 'Freigabe', text: 'wartet'}, {state: 'blocked', label: 'Sperre', text: 'blockiert'},
      {state: 'unverified', label: 'Prüfung', text: 'ungeprüft'}]);
    // The finished turn keeps its last real result, settled.
    f.state({activity: activityPacket({revision: 3, kind: 'MEMORY', title: 'Erinnerung gefunden', detail: '',
      active: false, steps: []}), status: 'Bereit'});
    assert.equal(card.dataset.activityActive, 'false');assert.equal(card.dataset.mode, 'idle');
    assert.equal(card.querySelector('strong').textContent, 'Erinnerung gefunden');
    assert.equal(card.querySelector('p').textContent, 'Bereit', 'An empty detail falls back to the real Android status');
    assert.equal(card.querySelector('.native-activity-steps'), null);
    assert.equal(card.querySelector('h3').textContent, 'AKTIVITÄT · GEDÄCHTNIS');
  } finally {f.close();}
});

test('an activity is rendered once per revision and null restores the default card', async () => {
  const f = await fixture();
  try {
    f.state({busy: true, activity: activityPacket()});
    const card = activityCard(f), first = card.querySelector('.native-activity-steps li');
    f.state({busy: true, cpu: 60, activity: activityPacket()});
    assert.equal(card.querySelector('.native-activity-steps li'), first, 'Periodic snapshots keep the rendered nodes');
    f.state({busy: true, activity: activityPacket({title: 'Anderer Titel', detail: 'Anderer Text',
      steps: [{label: 'Anderer Schritt', state: 'FAILED'}]})});
    assert.equal(card.querySelector('strong').textContent, 'Dateisuche', 'An unchanged revision changes nothing');
    assert.equal(card.querySelector('p').textContent, 'Durchsuche freigegebene Dokumente.');
    assert.equal(card.querySelector('.native-activity-steps li'), first);
    f.state({busy: true, activity: activityPacket({revision: 2, title: 'Anderer Titel',
      steps: [{label: 'Anderer Schritt', state: 'FAILED'}]})});
    assert.equal(card.querySelector('strong').textContent, 'Anderer Titel');
    assert.deepEqual(stepRows(f), [{state: 'failed', label: 'Anderer Schritt', text: 'fehlgeschlagen'}]);
    f.state({busy: true, activity: null});
    assert.equal(card.querySelector('h3').textContent, 'AKTIVITÄT');
    assert.equal(card.querySelector('strong').textContent, 'In Bearbeitung');
    assert.equal(card.querySelector('p').textContent, 'Bereit');
    assert.equal(card.querySelector('.native-activity-steps'), null);
    assert.equal('activityKind' in card.dataset, false);assert.equal('activityActive' in card.dataset, false);
    f.state({activity: null});
    assert.equal(card.querySelector('strong').textContent, 'Bereit');
    f.state({status: '', activity: undefined});
    assert.equal(card.querySelector('p').textContent, 'Dein nächster Auftrag beginnt hier.');
    f.state({busy: true, activity: activityPacket({revision: 2, title: 'Wieder da'})});
    assert.equal(card.querySelector('strong').textContent, 'Wieder da', 'After null the same revision is real again');
    f.state({sessionId: anotherSession, busy: true});
    assert.equal(card.querySelector('strong').textContent, 'In Bearbeitung', 'Another conversation never inherits an activity');
    assert.equal(card.querySelector('.native-activity-steps'), null);
  } finally {f.close();}
});

test('activity bounds: over-long strings, more than six steps and malformed parts never widen the card contract', async () => {
  const f = await fixture();
  try {
    const card = activityCard(f);
    f.state({busy: true, activity: activityPacket({title: 'T'.repeat(200), detail: 'D'.repeat(400),
      steps: Array.from({length: 9}, (_, index) => ({label: 'S' + index + 'x'.repeat(200), state: 'DONE'}))})});
    assert.equal(card.querySelector('strong').textContent, 'T'.repeat(80));
    assert.equal(card.querySelector('p').textContent, 'D'.repeat(160));
    assert.equal(stepRows(f).length, 6);
    assert.deepEqual(stepRows(f).map(row => row.label.slice(0, 2)), ['S0', 'S1', 'S2', 'S3', 'S4', 'S5']);
    assert.ok(stepRows(f).every(row => row.label.length === 80));
    f.state({busy: true, activity: activityPacket({revision: 2, title: 'a'.repeat(79) + '😀'})});
    assert.equal(card.querySelector('strong').textContent, 'a'.repeat(79), 'A cut never leaves half a character');
    let revision = 2;
    for (const steps of ['ACTIVE', 7, null, undefined, {length: 2, 0: {label: 'x', state: 'DONE'}}]) {
      f.state({busy: true, activity: activityPacket({revision: ++revision, title: 'Ohne Liste ' + revision, steps})});
      assert.equal(card.querySelector('strong').textContent, 'Ohne Liste ' + revision);
      assert.equal(card.querySelector('.native-activity-steps'), null, 'Steps that are no array are no steps');
    }
    f.state({busy: true, activity: activityPacket({revision: ++revision, steps: [null, 'Websuche', {label: 'x'},
      {label: 'x', state: 'RUNNING'}, {label: '   ', state: 'DONE'}, {label: 7, state: 'DONE'},
      {label: 'Echt', state: 'DONE'}, {label: 'Zu spät', state: 'DONE'}]})});
    assert.deepEqual(stepRows(f), [], 'Only the first six entries are considered and malformed ones are dropped');
    f.state({busy: true, activity: activityPacket({revision: ++revision, steps: [{label: 'x', state: 'constructor'},
      {label: ' Echt ', state: 'DONE'}]})});
    assert.deepEqual(stepRows(f), [{state: 'done', label: 'Echt', text: 'erledigt'}]);
    for (const broken of [{revision: -1}, {revision: 1.5}, {revision: '9'}, {revision: Number.MAX_SAFE_INTEGER + 1},
      {kind: 'DANCE'}, {kind: 'toString'}, {title: null}, {title: 5}, {detail: undefined}, {active: 'true'}, {active: undefined}]) {
      f.state({busy: true, activity: activityPacket({revision: ++revision, title: 'Kaputt', ...broken})});
      assert.equal(card.querySelector('strong').textContent, 'In Bearbeitung', JSON.stringify(broken));
      assert.equal(card.querySelector('h3').textContent, 'AKTIVITÄT');
      assert.equal(card.querySelector('.native-activity-steps'), null);
    }
    for (const broken of ['Dateisuche', 3, true, []]) {
      f.state({busy: true, activity: broken});
      assert.equal(card.querySelector('strong').textContent, 'In Bearbeitung');
    }
    f.state({busy: true, activity: activityPacket({revision: ++revision, title: '   ', detail: ''})});
    assert.equal(card.querySelector('strong').textContent, 'In Bearbeitung', 'A blank title never becomes an empty heading');
    assert.equal(card.querySelector('h3').textContent, 'AKTIVITÄT · RECHERCHE');
  } finally {f.close();}
});

// ───────── Code64: animated original orb, one room, real work sequence, files, attention ─────────
const phase = f => f.document.body.dataset.workPhase;
const surface = f => f.document.body.dataset.workSurface;
const filesPacket = (extra = {}) => ({revision: 1, kind: 'SEARCH', label: 'Argon4', total: 3, more: false,
  names: ['Argon4.pdf', 'Argon4 Messprotokoll.pdf', 'Argon4-Notizen.txt'], ...extra});
const fileRows = f => [...f.document.querySelectorAll('#native-files-panel .native-files-list li')]
  .map(item => item.querySelector('.native-file-name').textContent);
const forward = gaze => gaze.target === gaze.target.window && gaze.detail.x === 0 && gaze.detail.y === 0;
/** Lets the original workspace flight (rAF backend in this DOM) reach its end. */
async function land(f) {
  for (let i = 0; i < 4; i++) {f.advance(600);await f.paint();}
}

test('the arrangement is untouched: the orb stays behind the avatar and only light layers are added', async () => {
  const f = await fixture();
  try {
    f.state();
    const core = f.document.querySelector('.presence-core');
    const order = [...core.children].map(node => node.className.split(' ')[0]);
    assert.ok(order.indexOf('orb-zone') < order.indexOf('avatar-zone'), 'The orb precedes the avatar as in the original page');
    assert.deepEqual(order.filter(name => !name.startsWith('native-')),
      ['orb-zone', 'avatar-zone', 'core-status', 'avatar-linkbar'], 'No original child moved or left the core');
    assert.equal(f.document.querySelector('.avatar-crop').dataset.avatarFit, undefined, 'The avatar framing is the original one');
    assert.equal(f.document.body.dataset.nativeStage, undefined);
    assert.equal(f.document.querySelector('.native-energy-link'), null, 'No link between two separated figures exists');
    for (const selector of ['.native-room-light', '.native-orb-light']) {
      const layer = f.document.querySelector(selector);
      assert.ok(layer, selector);
      assert.equal(layer.getAttribute('aria-hidden'), 'true');
      assert.equal(layer.textContent, '', 'A light layer carries no text and no value');
      assert.equal(layer.querySelector('canvas,img,svg'), null, 'No second orb is drawn');
    }
    assert.equal(f.document.querySelector('.native-room-light').parentElement, core);
    assert.equal(f.document.querySelector('.native-orb-light').parentElement, core);
    f.state({cpu: 44});
    assert.equal(f.document.querySelectorAll('.native-room-light,.native-orb-light').length, 2, 'Layers are mounted once');
  } finally {f.close();}
});

test('the original orb leaves its resting picture and returns to it only on real preferences', async () => {
  const f = await fixture();
  try {
    const orb = f.document.querySelector('.orb-zone');
    assert.equal(orb.dataset.orbMotion, 'live', 'The page declared "fixed"; the orb script animates once it reads "live"');
    f.state();assert.equal(orb.dataset.orbMotion, 'live');
    f.state({quality: 'BALANCED'});assert.equal(orb.dataset.orbMotion, 'live');
    f.state({quality: 'REDUCED'});assert.equal(orb.dataset.orbMotion, 'fixed', 'A reduced thermal tier rests the orb');
    f.state({quality: 'FULL'});assert.equal(orb.dataset.orbMotion, 'live');
    f.state({appearance: {neon: 70, lightMotion: false}});
    assert.equal(orb.dataset.orbMotion, 'fixed', 'The owner switched light motion off');
    f.state({appearance: {neon: 70, lightMotion: true}});assert.equal(orb.dataset.orbMotion, 'live');
    f.window.matchMedia = () => ({matches: true, addEventListener() {}, removeEventListener() {}});
    f.state({cpu: 12});assert.equal(orb.dataset.orbMotion, 'fixed', 'The system preference for reduced motion rests the orb');
  } finally {f.close();}
});

test('the light exchange around the orb follows the presence Android reported', async () => {
  const f = await fixture();
  try {
    const energy = () => f.document.body.dataset.energy;
    assert.equal(energy(), undefined, 'Nothing is shown as a state before Android sent one');
    for (const [presence, expected] of [['IDLE', 'idle'], ['LISTENING', 'to-avatar'], ['THINKING', 'to-orb'],
      ['RESEARCHING', 'to-orb'], ['LEARNING', 'to-orb'], ['SPEAKING', 'speech'], ['WAITING_FOR_APPROVAL', 'hold'],
      ['BLOCKED', 'hold']]) {
      f.state({presence, busy: true});
      assert.equal(energy(), expected, presence);
    }
    // Snapshots of an older Android carry only the three booleans.
    f.state();assert.equal(energy(), 'idle');
    f.state({listening: true});assert.equal(energy(), 'to-avatar');
    f.state({busy: true});assert.equal(energy(), 'to-orb');
    f.state({speaking: true});assert.equal(energy(), 'speech');
    f.state({presence: 'DANCING', busy: true});assert.equal(energy(), 'to-orb', 'An unknown presence falls back to the booleans');
    const css = readFileSync(resolve(assetRoot, 'native-layer.css'), 'utf8');
    assert.match(css, /\[data-energy="hold"\] \.native-orb-light i:first-child \{[^}]*border-style:dashed/,
      'Approval and blocker hold one still shell');
    assert.doesNotMatch(css.match(/\[data-energy="hold"\][^{]*\{[^}]*\}/g).join(''), /animation:native-shell/,
      'A held state has no flow');
    assert.doesNotMatch(css.match(/\[data-energy="speech"\][^{]*\{[^}]*\}/g).join(''), /animation/,
      'Speaking is driven by the measured level, not by a clock');
  } finally {f.close();}
});

test('the measured speech level drives corona and room haze and leaves with the speech', async () => {
  const f = await fixture();
  try {
    f.state({speaking: true, presence: 'SPEAKING'});
    const layers = ['.native-orb-light', '.native-room-light'].map(selector => f.document.querySelector(selector));
    const level = () => layers.map(layer => layer.style.getPropertyValue('--native-speech'));
    assert.deepEqual(level(), ['', ''], 'No speech frame, no level');
    f.window.ContinuumNative.speech(speechFrame({jawOpen: .456}));
    assert.deepEqual(level(), ['0.46', '0.46']);
    f.window.ContinuumNative.speech(speechFrame({sequence: 2, jawOpen: 0}));
    assert.deepEqual(level(), ['0', '0'], 'Silence inside an utterance is a real zero');
    f.window.ContinuumNative.speech(speechFrame({sequence: 3, jawOpen: 7}));
    assert.deepEqual(level(), ['0', '0'], 'An invalid frame changes nothing');
    f.window.ContinuumNative.speech(speechFrame({sequence: 4, jawOpen: .8}));
    await f.timers(150);
    assert.deepEqual(level(), ['', ''], 'An expired pose leaves no level behind');
    f.window.ContinuumNative.speech(speechFrame({sequence: 5, jawOpen: .5}));
    f.window.ContinuumNative.speech({...binding, generation: 1, sequence: 6, active: false});
    assert.deepEqual(level(), ['', '']);
  } finally {f.close();}
});

test('the work sequence advances only on Android events: activation, opening, projected, settling, idle', async () => {
  const f = await fixture();
  try {
    assert.equal(phase(f), 'idle');
    f.state();assert.equal(phase(f), 'idle');
    f.state({busy: true, presence: 'THINKING'});
    assert.equal(phase(f), 'activation');assert.equal(surface(f), undefined);
    await f.timers(60000);await f.paint();
    assert.equal(phase(f), 'activation', 'Time alone never advances the sequence');
    f.state({busy: true, presence: 'THINKING', activity: activityPacket()});
    assert.equal(phase(f), 'activation', 'Progress without a work window stays at the core');
    // The file tool answered: the window opens with the original flight and counts as projected when it lands.
    f.state({busy: true, presence: 'THINKING', activity: activityPacket(), files: filesPacket()});
    assert.deepEqual([phase(f), surface(f)], ['opening', 'files']);
    assert.ok(f.window.ContinuumWorkspaceMotion.isAnimating());
    await land(f);
    assert.deepEqual([phase(f), surface(f)], ['projected', 'files']);
    // Android opened a document.
    f.window.ContinuumNative.document({...binding, documentId, name: 'Argon4.pdf', pageCount: 3});
    assert.deepEqual([phase(f), surface(f)], ['opening', 'document']);
    await land(f);
    assert.deepEqual([phase(f), surface(f)], ['projected', 'document']);
    f.state({busy: true, presence: 'THINKING', activity: activityPacket({revision: 2}), files: filesPacket()});
    assert.deepEqual([phase(f), surface(f)], ['projected', 'document'], 'Analysis progress keeps the projection');
    // The reply arrives, then Android reports the turn as finished while it still speaks.
    f.window.ContinuumNative.message({...binding, user: false, text: 'Zusammenfassung: …'});
    assert.equal(phase(f), 'projected', 'A reply alone does not end a running turn');
    f.state({busy: false, speaking: true, presence: 'SPEAKING', activity: activityPacket({revision: 3, active: false}),
      files: filesPacket()});
    assert.deepEqual([phase(f), surface(f)], ['settling', 'document']);
    await f.timers(60000);
    assert.equal(phase(f), 'settling', 'The return waits for the end of the spoken reply, not for a timer');
    f.state({presence: 'IDLE', activity: activityPacket({revision: 3, active: false}), files: filesPacket()});
    assert.deepEqual([phase(f), surface(f)], ['idle', undefined]);
    assert.ok(f.document.querySelector('#native-document-panel'), 'The opened document stays with the owner');
    assert.equal(f.document.querySelector('#native-files-panel'), null, 'The file window left with its turn');
  } finally {f.close();}
});

test('the return waits for the stage return animation and a finished turn without a reply returns at once', async () => {
  const f = await fixture();
  try {
    let finish;
    const light = f.document.querySelector('.native-orb-light');
    const running = [];
    light.getAnimations = () => running;
    f.state({busy: true});
    f.window.ContinuumNative.message({...binding, user: false, text: 'Antwort'});
    running.push({animationName: 'native-corona-breathe', finished: new Promise(() => {})},
      {animationName: 'native-work-return', finished: new Promise(resolve => {finish = resolve;})});
    f.state();
    assert.equal(phase(f), 'settling');
    await f.flush();await f.timers(5000);
    assert.equal(phase(f), 'settling', 'Only the return animation ends the return');
    finish();await f.flush();
    assert.equal(phase(f), 'idle');
    // Stopped, failed or blocked: nothing was delivered, so there is nothing to wait for.
    running.length = 0;
    f.state({busy: true});assert.equal(phase(f), 'activation');
    f.state();assert.equal(phase(f), 'idle');
    // The reply may reach the page after the finished flag: it still gets its return.
    let late;
    running.push({animationName: 'native-work-return', finished: new Promise((_, reject) => {late = reject;})});
    f.window.ContinuumNative.message({...binding, user: false, text: 'Späte Antwort'});
    assert.equal(phase(f), 'settling');
    late(new Error('cancelled'));await f.flush();
    assert.equal(phase(f), 'idle', 'A cancelled animation also ends the return');
    f.window.ContinuumNative.message({...binding, user: false, text: 'Noch eine Nachricht'});
    assert.equal(phase(f), 'idle', 'A message outside a turn starts no sequence');
    // A new turn during a pending return wins; the old completion is ignored.
    running.length = 0;
    let stale;
    running.push({animationName: 'native-work-return', finished: new Promise(resolve => {stale = resolve;})});
    f.state({busy: true});f.window.ContinuumNative.message({...binding, user: false, text: 'A'});f.state();
    assert.equal(phase(f), 'settling');
    f.state({busy: true});assert.equal(phase(f), 'activation');
    stale();await f.flush();
    assert.equal(phase(f), 'activation');
  } finally {f.close();}
});

test('sources and device frames are work windows of their turn and a view change returns to the main stage', async () => {
  const f = await fixture();
  try {
    const item = {id: sourceId, title: 'Quelle', url: 'https://example.org/evidence'};
    f.state();
    f.window.ContinuumNative.sources({...binding, sourceSetId, items: [item]});
    assert.equal(phase(f), 'idle', 'Sources outside a turn open no sequence');
    f.state({busy: true, presence: 'RESEARCHING'});
    assert.equal(phase(f), 'activation', 'Sources of an earlier turn are not this turn\'s window');
    f.window.ContinuumNative.sources({...binding, sourceSetId: anotherDocument, items: [{...item, id: documentId}]});
    assert.equal(surface(f), 'sources');
    await land(f);
    assert.deepEqual([phase(f), surface(f)], ['projected', 'sources']);
    f.window.ContinuumNative.computer(computerFrame());
    assert.deepEqual([phase(f), surface(f)], ['opening', 'computer']);
    await land(f);
    assert.deepEqual([phase(f), surface(f)], ['projected', 'computer']);
    f.window.ContinuumNative.computer({...binding, generation: 1, sequence: 2, active: false, status: 'ENDED'});
    await land(f);
    assert.deepEqual([phase(f), surface(f)], ['projected', 'sources'], 'The remaining real window is shown as such');
    f.state({sessionId: anotherSession, busy: false});
    assert.deepEqual([phase(f), surface(f)], ['idle', undefined]);
    f.window.ContinuumNative.message({...binding, user: false, text: 'Alte Antwort'});
    assert.equal(phase(f), 'idle', 'A reply for the left view moves nothing');
  } finally {f.close();}
});

test('the file window shows only names Android sent: bounded, text only, never a path', async () => {
  const f = await fixture();
  try {
    const workspace = f.document.querySelector('#workspace');
    f.state();
    assert.equal(f.document.querySelector('#native-files-panel'), null);
    f.state({busy: true, files: filesPacket()});
    const panel = f.document.querySelector('#native-files-panel');
    assert.ok(panel && workspace.classList.contains('native-files-active'));
    assert.equal(workspace.classList.contains('native-work-active'), false);
    assert.equal(panel.querySelector('.native-files-kicker').textContent, 'DATEISUCHE · ANDROID-WERKZEUG');
    assert.equal(panel.querySelector('.native-files-title').textContent, '„Argon4“');
    assert.equal(panel.querySelector('.native-files-count').textContent, '3 Dateien gefunden');
    assert.deepEqual(fileRows(f), ['Argon4.pdf', 'Argon4 Messprotokoll.pdf', 'Argon4-Notizen.txt']);
    assert.deepEqual([...panel.querySelectorAll('.native-file-kind')].map(node => node.textContent), ['PDF', 'PDF', 'TXT']);
    assert.equal(f.document.querySelector('#workspace-meta').textContent, 'DATEIEN');
    assert.equal(f.gazes.at(-1).target, panel, 'The avatar looks at the list that really appeared');
    const first = panel.querySelector('li');
    f.state({busy: true, cpu: 55, files: filesPacket({names: ['Anders.pdf'], total: 1})});
    assert.equal(panel.querySelector('li'), first, 'An unchanged revision keeps what is shown');
    // More than twelve names, paths, markup, control characters and non-strings.
    const many = Array.from({length: 30}, (_, index) => 'Datei ' + (index + 1) + '.txt');
    f.state({busy: true, files: filesPacket({revision: 2, total: 250, more: true, names: many})});
    assert.equal(fileRows(f).length, 12);assert.equal(fileRows(f)[11], 'Datei 12.txt');
    assert.equal(panel.querySelector('.native-files-count').textContent,
      'Mindestens 250 Dateien gefunden · 12 angezeigt · Suchgrenze erreicht');
    f.state({busy: true, files: filesPacket({revision: 3, total: 9, label: '<b>x</b>', names: [
      '/sdcard/Download/geheim.pdf', '..\\windows\\pfad.txt', '<img src=x onerror=alert(1)>.pdf', 'Bericht\u0007 2026.pdf',
      7, null, '   ', 'x'.repeat(300) + '.pdf', {name: 'objekt.pdf'}]})});
    assert.deepEqual(fileRows(f), ['<img src=x onerror=alert(1)>.pdf', 'Bericht 2026.pdf', 'x'.repeat(96)]);
    assert.equal(panel.querySelector('img,b,script'), null, 'Names and label are text, never markup');
    assert.equal(panel.querySelector('.native-files-title').textContent, '„<b>x</b>“');
    assert.equal(panel.querySelector('.native-files-count').textContent, '9 Dateien gefunden · 3 angezeigt');
    assert.doesNotMatch(panel.textContent, /sdcard|windows/);
    f.state({busy: true, files: filesPacket({revision: 4, kind: 'FOLDER', label: 'Download', total: 0, names: []})});
    assert.equal(panel.querySelector('.native-files-kicker').textContent, 'ORDNER · ANDROID-WERKZEUG');
    assert.equal(panel.querySelector('.native-files-title').textContent, 'Download');
    assert.equal(panel.querySelector('.native-files-count').textContent, 'Keine Einträge im Ordner');
    assert.deepEqual(fileRows(f), []);
    f.state({busy: true, files: filesPacket({revision: 5, total: 0, names: []})});
    assert.equal(panel.querySelector('.native-files-count').textContent, 'Keine Datei gefunden');
    // Anything malformed withdraws the window instead of showing a guess.
    let revision = 5;
    for (const broken of [{revision: -1}, {revision: 1.5}, {revision: '7'}, {kind: 'TREE'}, {kind: 'constructor'},
      {label: null}, {total: -1}, {total: 1.5}, {total: 100001}, {total: 2}, {names: 'Argon4.pdf'}, {names: null}]) {
      f.state({busy: true, files: filesPacket({revision: ++revision, ...broken})});
      assert.equal(f.document.querySelector('#native-files-panel'), null, JSON.stringify(broken));
      assert.equal(workspace.classList.contains('native-files-active'), false);
    }
    for (const broken of ['Argon4.pdf', 3, true, []]) {
      f.state({busy: true, files: broken});
      assert.equal(f.document.querySelector('#native-files-panel'), null);
    }
  } finally {f.close();}
});

test('the file window is closable, marks the document Android opened and never crosses a view', async () => {
  const f = await fixture();
  try {
    const workspace = f.document.querySelector('#workspace');
    f.state({busy: true, files: filesPacket()});
    assert.deepEqual([...f.document.querySelectorAll('.native-files-list li')].map(item => item.dataset.selected),
      ['false', 'false', 'false'], 'No choice is shown before Android opened a file');
    f.window.ContinuumNative.document({...binding, documentId, name: 'Argon4 Messprotokoll.pdf', pageCount: 2});
    assert.deepEqual([...f.document.querySelectorAll('.native-files-list li')].map(item => item.dataset.selected),
      ['false', 'true', 'false']);
    assert.ok(workspace.classList.contains('native-document-active'));
    assert.equal(workspace.classList.contains('native-files-active'), false, 'The document covers the list');
    f.click(f.document.querySelector('#close-button'));
    assert.equal(f.document.querySelector('#native-document-panel'), null);
    assert.ok(workspace.classList.contains('native-files-active'), 'Closing the document shows the list again');
    f.click(f.document.querySelector('#close-button'));
    assert.equal(f.document.querySelector('#native-files-panel'), null, 'The owner closed the list');
    f.state({busy: true, cpu: 20, files: filesPacket()});
    assert.equal(f.document.querySelector('#native-files-panel'), null, 'A closed revision stays closed');
    f.state({busy: true, files: filesPacket({revision: 2})});
    assert.ok(f.document.querySelector('#native-files-panel'), 'A new answer opens a new list');
    // The next turn starts without the previous list; its own answer has a new revision.
    f.state({files: filesPacket({revision: 2})});
    f.state({busy: true, files: filesPacket({revision: 2})});
    assert.equal(f.document.querySelector('#native-files-panel'), null);
    f.state({busy: true, files: filesPacket({revision: 3, names: ['Neu.pdf'], total: 1})});
    assert.deepEqual(fileRows(f), ['Neu.pdf']);
    f.state({busy: true, files: null});
    assert.equal(f.document.querySelector('#native-files-panel'), null, 'null withdraws the list');
    f.state({busy: true, files: filesPacket({revision: 4})});
    f.state({sessionId: anotherSession});
    assert.equal(f.document.querySelector('#native-files-panel'), null);
    assert.equal(workspace.classList.contains('native-files-active'), false);
  } finally {f.close();}
});

test('attention follows real events: the owner speaking or typing, the open work window, each step, the reply', async () => {
  const f = await fixture();
  try {
    f.state({presence: 'IDLE'});
    assert.equal(f.gazes.length, 0);
    f.state({presence: 'LISTENING', listening: true});
    assert.equal(f.gazes.length, 1);assert.ok(forward(f.gazes[0]), 'Listening faces the owner');
    f.state({presence: 'LISTENING', listening: true, cpu: 33});
    assert.equal(f.gazes.length, 1, 'A repeated snapshot is no new reason');
    f.state({presence: 'IDLE'});
    // Typing: once when the owner starts, not per key.
    f.input('S');f.input('Su');f.input('Suche');
    assert.equal(f.gazes.length, 2);assert.ok(forward(f.gazes[1]));
    f.input('');f.input('N');
    assert.equal(f.gazes.length, 3, 'An emptied field and a new start is a new start');
    f.document.querySelector('#command-input').dispatchEvent(new f.window.Event('blur'));
    f.input('Noch');
    assert.equal(f.gazes.length, 4);
    // Work: each real step draws one glance to where it is shown.
    f.state({busy: true, presence: 'THINKING', activity: activityPacket()});
    assert.equal(f.gazes.length, 5);
    assert.equal(f.gazes[4].target, f.document.querySelector('[data-presence-left] .native-activity'));
    f.state({busy: true, presence: 'THINKING', cpu: 61, activity: activityPacket()});
    assert.equal(f.gazes.length, 5, 'The same step is no new glance');
    f.window.ContinuumNative.document({...binding, documentId, name: 'Argon4.pdf', pageCount: 3});
    assert.equal(f.gazes.length, 6);assert.equal(f.gazes[5].target, f.document.querySelector('#native-document-panel'));
    f.state({busy: true, presence: 'THINKING', activity: activityPacket({revision: 2})});
    assert.equal(f.gazes.length, 7);
    assert.equal(f.gazes[6].target, f.document.querySelector('#native-document-panel'), 'The next step is read in the open window');
    // A finished or calm state gives no work glance; the reply turns her to the owner.
    f.state({busy: true, presence: 'WAITING_FOR_APPROVAL', activity: activityPacket({revision: 3})});
    assert.equal(f.gazes.length, 8);assert.ok(forward(f.gazes[7]), 'Only the calm forward gaze');
    f.state({busy: true, presence: 'THINKING', activity: activityPacket({revision: 4, active: false})});
    assert.equal(f.gazes.length, 8);
    f.window.ContinuumNative.message({...binding, user: true, text: 'Frage'});
    assert.equal(f.gazes.length, 8, 'The owner\'s own line is no reply');
    f.window.ContinuumNative.message({...binding, user: false, text: 'Antwort'});
    assert.equal(f.gazes.length, 9);assert.ok(forward(f.gazes[8]));
    Object.defineProperty(f.document, 'visibilityState', {value: 'hidden', configurable: true});
    f.window.ContinuumNative.message({...binding, user: false, text: 'Unsichtbar'});
    f.input('');f.input('x');
    assert.equal(f.gazes.length, 9, 'A hidden page moves nothing');
  } finally {f.close();}
});

test('a snapshot of an older Android without presence, quality, activity and files renders as before', async () => {
  const f = await fixture();
  try {
    const workspace = f.document.querySelector('#workspace');
    f.state();
    f.window.ContinuumNative.message({...binding, user: true, text: 'Hallo'});
    f.state({busy: true});
    assert.deepEqual([f.document.body.dataset.presence, f.document.body.dataset.nativeQuality], [undefined, undefined]);
    assert.equal(f.document.body.dataset.state, 'working');
    assert.equal(f.document.querySelector('#orb-status').textContent, 'ARBEITET');
    assert.equal(f.document.querySelector('.native-activity strong').textContent, 'In Bearbeitung');
    assert.equal(f.document.querySelector('.native-activity-steps'), null);
    assert.equal(f.document.querySelector('#native-files-panel'), null);
    assert.ok(workspace.classList.contains('native-work-active'));
    assert.equal(workspace.classList.contains('native-files-active'), false);
    assert.equal(f.document.querySelector('#workspace-meta').textContent, 'AUFTRAG AKTIV');
    assert.deepEqual(f.scenes.slice(-2), ['idle', 'working']);
    assert.equal(f.gazes.length, 0, 'No presence, no activity: no gaze was invented');
    assert.equal(phase(f), 'activation');
    f.window.ContinuumNative.message({...binding, user: false, text: 'Antwort'});
    f.state();
    assert.equal(phase(f), 'idle');
    assert.equal(f.document.querySelector('#orb-status').textContent, 'BEREIT');
    assert.equal(f.document.querySelector('#workspace-meta').textContent, 'LOKAL');
  } finally {f.close();}
});

test('the frame limit follows Android\'s tier and the stage rests while the on-device model computes', async () => {
  const f = await fixture();
  try {
    const body = f.document.body, orb = f.document.querySelector('.orb-zone');
    const limit = () => body.dataset.frameLimit;
    f.state();
    assert.equal(limit(), '30', 'Without a tier the stage draws at most 30 frames a second');
    assert.equal('nativeCompute' in body.dataset, false);
    f.state({busy: true});
    assert.deepEqual([limit(), body.dataset.nativeCompute, orb.dataset.orbMotion], ['5', 'local', 'fixed'],
      'An older Android without presence: busy counts as computing on the device');
    f.state({busy: true, speaking: true});
    assert.deepEqual([limit(), body.dataset.nativeCompute, orb.dataset.orbMotion], ['30', undefined, 'live'],
      'A spoken answer keeps the lip movement smooth and the orb alive');
    const calm = {FULL: 30, BALANCED: 20, REDUCED: 10}, local = {FULL: 5, BALANCED: 4, REDUCED: 3},
      cloud = {FULL: 15, BALANCED: 10, REDUCED: 6};
    for (const quality of Object.keys(calm)) {
      for (const presence of ['IDLE', 'LISTENING', 'SPEAKING', 'WAITING_FOR_APPROVAL', 'BLOCKED']) {
        f.state({quality, presence});
        assert.equal(limit(), String(calm[quality]), quality + ' ' + presence);
        assert.equal('nativeCompute' in body.dataset, false, quality + ' ' + presence);
      }
      for (const presence of ['THINKING', 'RESEARCHING', 'LEARNING']) {
        f.state({quality, presence, busy: true});
        assert.deepEqual([limit(), body.dataset.nativeCompute, orb.dataset.orbMotion],
          [String(local[quality]), 'local', 'fixed'], quality + ' ' + presence + ' on the device');
        assert.equal(f.sceneQualities.at(-1), 'low', 'The scene runs on its lowest setting while the device computes');
        f.state({quality, presence, busy: true, cloudBrain: true});
        assert.deepEqual([limit(), body.dataset.nativeCompute], [String(cloud[quality]), undefined],
          quality + ' ' + presence + ' in the cloud');
        assert.equal(orb.dataset.orbMotion, quality === 'REDUCED' ? 'fixed' : 'live');
      }
    }
    f.state({quality: 'FULL', presence: 'IDLE'});
    assert.equal(f.sceneQualities.at(-1), 'high', 'After the work the scene returns to the tier\'s setting');
    f.state({quality: 'UNBEKANNT', presence: 'THINKING', busy: true});
    assert.equal(limit(), '5', 'An unknown tier is treated as full quality, never as unlimited');
  } finally {f.close();}
});

test('the frame counter of the heat measurement counts what the governor hands on and is off unless Android starts it', async () => {
  const f = await fixture();
  try {
    // The page's own scripts reach the clock double directly; the governed function is the one the binding installed.
    const native = f.window.ContinuumNative, raf = f.window.requestAnimationFrame;
    f.state({quality: 'FULL', presence: 'IDLE'});
    assert.equal(f.document.body.dataset.frameLimit, '30');
    assert.equal(native.frameProbe('read'), null, 'Not started: nothing is counted');
    assert.equal(native.frameProbe('stop'), null);
    for (const unknown of ['', 'reset', 'constructor', 'toString', undefined])
      assert.equal(native.frameProbe(unknown), null, 'Only start, read and stop exist');
    let drawn = 0;
    const loop = () => {drawn++;raf(loop);};
    raf(loop);
    for (let frame = 0; frame < 10; frame++) await f.paint();
    const earlier = drawn;
    assert.ok(earlier > 0, 'The stand-in scene draws through the governor');
    assert.equal(native.frameProbe('start'), true);
    // 125 display frames of 16 ms = 2000 ms at a limit of 30 frames a second: every second display frame is handed on.
    for (let frame = 0; frame < 125; frame++) await f.paint();
    const calm = native.frameProbe('read');
    assert.equal(calm.schema, 'continuum-stage-frame-probe-v1');
    assert.equal(calm.delivered, drawn - earlier, 'Exactly the frames the scene really received, none from before the start');
    assert.ok(calm.delivered >= 62 && calm.delivered <= 63, String(calm.delivered));
    assert.equal(calm.ticks, 125, 'Every display frame the page saw');
    assert.deepEqual([calm.limit, calm.observedMs, calm.visibility], [30, 2000, 'visible']);
    assert.deepEqual(JSON.parse(JSON.stringify(calm.histogram)), {bucketMs: .5, bucketCount: 300,
      counts: [[64, calm.delivered - 1]], overflow: 0, maxMs: 32, sumMs: 32 * (calm.delivered - 1)},
      'Every interval between two delivered frames was 32 ms');
    assert.equal(calm.perSecond.reduce((sum, value) => sum + value, 0), calm.delivered);
    assert.equal(calm.limitChanges.length, 0);
    // The on-device model computes: 5 frames a second. An interval above 150 ms is kept with its exact maximum.
    f.state({quality: 'FULL', presence: 'THINKING', busy: true});
    assert.equal(f.document.body.dataset.frameLimit, '5');
    for (let frame = 0; frame < 125; frame++) await f.paint();
    const resting = native.frameProbe('stop');
    assert.equal(resting.delivered, drawn - earlier);
    const slow = resting.delivered - calm.delivered;
    assert.ok(slow >= 9 && slow <= 10, String(slow));
    assert.deepEqual([resting.ticks, resting.limit, resting.observedMs], [250, 5, 4000]);
    assert.equal(JSON.stringify(resting.limitChanges), '[[2000,5]]', 'A change of the limit during the run is kept with its time');
    assert.deepEqual([resting.histogram.overflow, resting.histogram.maxMs], [slow, 208]);
    assert.equal(JSON.stringify(resting.histogram.counts), JSON.stringify(calm.histogram.counts));
    assert.equal(resting.perSecond.reduce((sum, value) => sum + value, 0), resting.delivered);
    assert.equal(native.frameProbe('read'), null, 'Stopped: the counter is gone');
    const afterStop = drawn;
    for (let frame = 0; frame < 26; frame++) await f.paint();
    assert.ok(drawn > afterStop, 'Counting never changes what is drawn');
    assert.equal(native.frameProbe('start'), true);
    assert.equal(native.frameProbe('read').delivered, 0, 'A new start begins at zero');
    assert.equal(native.frameProbe('stop').delivered, 0);
    assert.equal(f.navigations.length, 0, 'The counter answers the caller; it sends nothing to Android by itself');
  } finally {f.close();}
});

test('the running answer is shown as one draft row in the stage and replaced by the checked answer', async () => {
  const f = await fixture();
  try {
    const native = f.window.ContinuumNative;
    f.state();
    f.window.ContinuumNative.message({...binding, user: true, text: 'Erkläre mir das'});
    const feed = () => f.document.querySelector('#native-live-panel .native-transcript');
    const draft = () => f.document.querySelector('.native-draft');
    // Not busy: a late draft of a finished turn is never shown.
    native.draft({...binding, text: 'Verspäteter Entwurf'});
    assert.equal(draft(), null);
    f.state({busy: true, presence: 'THINKING'});
    const owner = feed().querySelector('.native-owner');
    // Another view's draft is ignored.
    native.draft({...binding, sessionId: 'other-session', text: 'Fremder Entwurf'});
    assert.equal(draft(), null);
    native.draft({...binding, text: 'Das ist'});
    assert.equal(draft().querySelector('p').textContent, 'Das ist');
    assert.equal(draft().querySelector('small').textContent, 'CONTINUUM · SCHREIBT …');
    assert.equal(feed().lastElementChild, draft(), 'The draft is the last row of the feed');
    const row = draft();
    native.draft({...binding, text: 'Das ist der zweite Satz.'});
    assert.equal(draft(), row, 'An update changes the text of the same row');
    assert.equal(feed().querySelector('.native-owner'), owner, 'An update does not rebuild the feed');
    assert.equal(row.querySelector('p').textContent, 'Das ist der zweite Satz.');
    // Markup stays text; only the tail of a very long draft is kept.
    native.draft({...binding, text: '<img src=x onerror=alert(1)>' + 'a'.repeat(7000)});
    assert.equal(row.querySelector('img'), null);
    assert.equal(row.querySelector('p').textContent.length, 6000);
    // A state update during the turn keeps the draft at the end.
    f.state({busy: true, presence: 'THINKING', cpu: 31});
    assert.equal(feed().lastElementChild, draft());
    // The checked answer replaces it.
    native.message({...binding, user: false, text: 'Das ist die geprüfte Antwort.'});
    assert.equal(draft(), null);
    assert.equal(feed().lastElementChild.querySelector('p').textContent, 'Das ist die geprüfte Antwort.');
    // An explicit empty draft and the end of the turn both remove it.
    native.draft({...binding, text: 'Noch ein Entwurf'});
    assert.notEqual(draft(), null);
    native.draft({...binding, text: ''});
    assert.equal(draft(), null);
    native.draft({...binding, text: 'Abgebrochener Entwurf'});
    assert.notEqual(draft(), null);
    f.state({busy: false});
    assert.equal(draft(), null, 'A draft never outlives its turn');
    assert.equal(f.navigations.length, 0, 'Showing a draft sends nothing to Android');
  } finally {f.close();}
});

// ───────── Code70 · the windows say what really happens ─────────
// Fixtures carry only what Android's snapshot and frame packets may carry; nothing is derived in the tests.
const workRecord = (extra = {}) => ({consentPending: false, taskId: 'computer-task', captureSession: '7',
  stage: 'DISPATCHED', app: 'Dateien', goal: 'Download-Ordner öffnen', actions: [], ...extra});
const dispatched = (sequence, extra = {}) => ({sequence, kind: 'CLICK', state: 'DISPATCHED', x: .5, y: .25,
  frameSequence: 12, observationRevision: 4, ...extra});
const liveFrame = (extra = {}) => computerFrame({taskId: 'computer-task', captureSession: '7',
  targetPackage: 'com.example.files', viewport: [.25, 0, .75, 1], sourceSequence: 12, observationRevision: 4, ...extra});
const monitor = f => f.document.querySelector('#native-computer-panel');
const pointer = f => f.document.querySelector('#native-computer-panel .native-computer-marker');
const record = f => f.document.querySelector('#native-live-panel .native-monitor-record');
const logRows = host => [...host.querySelectorAll('.native-computer-log li')].map(item =>
  [item.dataset.kind, item.dataset.state, item.querySelector('.native-action-kind').textContent,
    item.querySelector('.native-action-state').textContent]);
const working = {busy: true, presence: 'THINKING', cloudBrain: true};

test('the live window names its real target and says LIVE only while a fresh frame of the source is shown', async () => {
  const f = await fixture();
  try {
    const native = f.window.ContinuumNative;
    const badge = () => [monitor(f).querySelector('.op-mode-label').textContent, monitor(f).dataset.monitorState];
    const title = () => monitor(f).querySelector('.op-window-id').textContent;
    f.state({...working, computerWork: workRecord()});
    native.computer({...binding, generation: 1, sequence: 1, active: true, status: 'WAITING',
      reason: 'Warte auf erstes echtes Bild', taskId: 'computer-task', captureSession: '7',
      targetPackage: 'com.example.files'});
    assert.deepEqual(badge(), ['WARTET AUF ERSTES BILD', 'waiting']);
    assert.equal(title(), 'Dateien · com.example.files', 'App of the approved task and the package Android captures');
    native.computer(liveFrame({sequence: 2}));
    assert.deepEqual(badge(), ['WARTET AUF ERSTES BILD', 'waiting'], 'An undecoded frame is not a live picture');
    await f.decode();
    assert.deepEqual(badge(), ['LIVE', 'live']);
    assert.equal(monitor(f).querySelector('.native-computer-screen').hidden, false);
    assert.equal(monitor(f).querySelector('.native-computer-image').parentElement,
      monitor(f).querySelector('.native-computer-screen'), 'The image sits in the box that also carries the markers');
    // The stream stops delivering: the picture leaves and the window says so instead of pretending.
    await f.timers(1501);
    assert.deepEqual(badge(), ['KEIN FRISCHES BILD', 'stale']);
    assert.equal(monitor(f).querySelector('.native-computer-image'), null);
    assert.equal(monitor(f).querySelector('.native-computer-screen').hidden, true);
    native.computer({...binding, generation: 1, sequence: 3, active: true, status: 'PAUSED', reason: 'Pausiert – kein frisches Bild'});
    assert.deepEqual(badge(), ['PAUSIERT', 'paused']);
    assert.equal(title(), 'Dateien · com.example.files', 'A status packet without names keeps those of its generation');
    native.computer({...binding, generation: 1, sequence: 4, active: true, status: 'COVERED', reason: 'Verdeckt'});
    assert.deepEqual(badge(), ['VERDECKT', 'covered']);
    native.computer(liveFrame({sequence: 5, sourceSequence: 13}));await f.decode();
    assert.deepEqual(badge(), ['LIVE', 'live'], 'A new real frame is live again');
    // A record of another task never lends its app name; a malformed package is not shown at all.
    f.state({...working, computerWork: workRecord({taskId: 'another-task'})});
    assert.equal(title(), 'com.example.files');
    native.computer({...binding, generation: 2, sequence: 1, active: true, status: 'WAITING', reason: 'Warte',
      taskId: 'another-task', captureSession: '7', targetPackage: '<b>kein.paket</b>'});
    assert.equal(title(), 'Dateien', 'Only the app of the matching record remains');
    assert.equal(monitor(f).querySelector('b'), null);
    f.state({...working});
    assert.equal(title(), 'CONTINUUM · ANDROID', 'Without a real name the neutral title stays');
  } finally {f.close();}
});

test('a dispatched input is marked on the shown frame at the coordinate Android measured, once, and then listed', async () => {
  const f = await fixture();
  try {
    const native = f.window.ContinuumNative;
    f.state({...working, computerWork: workRecord()});
    native.computer(liveFrame());await f.decode();
    assert.equal(pointer(f), null, 'No input, no marker');
    assert.equal(monitor(f).querySelector('.native-computer-work').hidden, false, 'Goal and stage of the task are text');
    assert.equal(monitor(f).querySelector('.native-computer-stage').textContent, 'Download-Ordner öffnen · Eingabe ausgelöst');
    assert.deepEqual(logRows(monitor(f)), []);
    assert.equal(monitor(f).querySelector('.native-computer-log').hidden, true, 'No empty list is shown');
    f.advance(1300);
    const gazes = f.gazes.length;
    f.state({...working, computerWork: workRecord({actions: [dispatched(3)]})});
    const mark = pointer(f);
    assert.ok(mark, 'The input Android just dispatched is shown on the image');
    // The frame shows the window part x .25–.75; the target x .5, y .25 of the window lies at 50 % / 25 % of it.
    assert.deepEqual([parseFloat(mark.style.left), parseFloat(mark.style.top), mark.dataset.at, mark.dataset.kind,
      mark.dataset.state], [50, 25, '50.00% 25.00%', 'click', 'dispatched']);
    assert.equal(mark.parentElement, monitor(f).querySelector('.native-computer-image').parentElement);
    assert.equal(mark.getAttribute('aria-hidden'), 'true');
    assert.equal(mark.textContent, '');
    assert.equal(f.gazes.length, gazes + 1);
    assert.equal(f.gazes.at(-1).target, monitor(f), 'The avatar looks at the monitor when an input lands');
    assert.deepEqual(logRows(monitor(f)), [['click', 'dispatched', 'Tippen', 'ausgelöst']]);
    // The effect is confirmed: the same marker changes its state; that is no second input and no second glance.
    f.state({...working, computerWork: workRecord({stage: 'VERIFIED', actions: [dispatched(3, {state: 'VERIFIED'})]})});
    assert.equal(pointer(f), mark);
    assert.equal(mark.dataset.state, 'verified');
    assert.equal(f.gazes.length, gazes + 1);
    assert.deepEqual(logRows(monitor(f)), [['click', 'verified', 'Tippen', 'Wirkung bestätigt']]);
    // The marker leaves after its short time while the stream goes on; the list keeps what was done.
    native.computer(liveFrame({sequence: 2, sourceSequence: 13}));await f.decode();
    assert.equal(pointer(f), mark, 'The next frame of the same window keeps the marker in place');
    f.advance(1200);
    native.computer(liveFrame({sequence: 3, sourceSequence: 14}));await f.decode();
    await f.timers(1300);
    assert.equal(pointer(f), null);
    assert.ok(monitor(f).querySelector('.native-computer-image'), 'The picture itself is still live');
    assert.deepEqual(logRows(monitor(f)), [['click', 'verified', 'Tippen', 'Wirkung bestätigt']]);
    // A repeated snapshot is no new input.
    f.state({...working, computerWork: workRecord({stage: 'VERIFIED', actions: [dispatched(3, {state: 'VERIFIED'})]})});
    assert.equal(pointer(f), null);
    assert.equal(f.navigations.filter(url => !['/frame-presented', '/frame-hidden'].includes(url.pathname)).length, 0,
      'Showing an input sends nothing to Android');
  } finally {f.close();}
});

test('no marker is placed without measured geometry, on an older frame, outside the shown part or for another task', async () => {
  const cases = [
    ['a frame without a measured viewport', liveFrame({viewport: undefined}), dispatched(3)],
    ['a malformed viewport', liveFrame({viewport: [.75, 0, .25, 1]}), dispatched(3)],
    ['a target outside the shown part of the window', liveFrame(), dispatched(3, {x: .1})],
    ['a frame older than the one the input was dispatched on', liveFrame({sourceSequence: 11}), dispatched(3)],
    ['a frame without its sequence', liveFrame({sourceSequence: undefined}), dispatched(3)],
    ['an input without a measured target', liveFrame(), dispatched(3, {kind: 'SET_TEXT', x: undefined, y: undefined})],
    ['a coordinate outside 0..1', liveFrame(), dispatched(3, {y: 1.2})],
    ['a frame of another capture session', liveFrame({captureSession: '8'}), dispatched(3)]];
  for (const [name, frame, action] of cases) {
    const f = await fixture();
    try {
      f.state({...working, computerWork: workRecord()});
      f.window.ContinuumNative.computer(frame);await f.decode();
      f.state({...working, computerWork: workRecord({actions: [action]})});
      assert.ok(monitor(f).querySelector('.native-computer-image'), name + ': the frame itself is shown');
      assert.equal(pointer(f), null, name);
    } finally {f.close();}
  }
  const f = await fixture();
  try {
    const native = f.window.ContinuumNative;
    // The input was dispatched on frame 20; the page still shows frame 12. The marker waits for the right picture.
    f.state({...working, computerWork: workRecord()});
    native.computer(liveFrame());await f.decode();
    f.state({...working, computerWork: workRecord({actions: [dispatched(3, {frameSequence: 20})]})});
    assert.equal(pointer(f), null);
    native.computer(liveFrame({sequence: 2, sourceSequence: 20}));await f.decode();
    assert.ok(pointer(f), 'It appears with the frame it belongs to');
    // An input that is already settled when the page first sees it is history: listed, not replayed.
    const g = await fixture();
    try {
      g.state({...working, computerWork: workRecord({stage: 'VERIFIED', actions: [dispatched(3, {state: 'VERIFIED'})]})});
      g.window.ContinuumNative.computer(liveFrame());await g.decode();
      assert.equal(pointer(g), null);
      assert.deepEqual(logRows(monitor(g)), [['click', 'verified', 'Tippen', 'Wirkung bestätigt']]);
    } finally {g.close();}
  } finally {f.close();}
});

test('the list of inputs is bounded text: kinds, states, unconfirmed effects; malformed entries never appear', async () => {
  const f = await fixture();
  try {
    f.state({...working, computerWork: workRecord({stage: 'PAUSED', goal: '<img src=x onerror=alert(1)>', actions: [
      dispatched(1, {kind: 'SCROLL', state: 'VERIFIED', x: undefined, y: undefined}),
      dispatched(2, {kind: 'SET_TEXT', state: 'UNCONFIRMED', x: undefined, y: undefined}),
      dispatched(2, {kind: 'CLICK'}),                       // not newer than its predecessor
      dispatched(4, {kind: 'DELETE_EVERYTHING'}),           // unknown kind
      dispatched(5, {state: 'DONE'}),                       // unknown state
      {sequence: 'six', kind: 'CLICK', state: 'VERIFIED'},  // malformed
      null,
      dispatched(7, {kind: 'OPEN_APP', state: 'VERIFIED'})]})});
    f.window.ContinuumNative.computer(liveFrame());await f.decode();
    assert.deepEqual(logRows(monitor(f)), [['scroll', 'verified', 'Scrollen', 'Wirkung bestätigt'],
      ['set_text', 'unconfirmed', 'Text eingeben', 'Wirkung nicht bestätigt'],
      ['open_app', 'verified', 'App öffnen', 'Wirkung bestätigt']]);
    const stage = monitor(f).querySelector('.native-computer-stage');
    assert.equal(stage.textContent, '<img src=x onerror=alert(1)> · Arbeit pausiert');
    assert.equal(stage.dataset.stage, 'paused');
    assert.equal(monitor(f).querySelector('.native-computer-work img'), null, 'Markup stays text');
    assert.match(monitor(f).querySelector('.native-computer-log-title').textContent, /FREIGEGEBENEN AUFTRAG/);
    // More than six: the newest six. An unknown stage is not named.
    f.state({...working, computerWork: workRecord({stage: 'DREAMING', goal: '',
      actions: Array.from({length: 9}, (_, index) => dispatched(index + 1, {state: 'VERIFIED'}))})});
    assert.equal(logRows(monitor(f)).length, 6);
    assert.equal(monitor(f).querySelector('.native-computer-stage').hidden, true);
    // A malformed record is no record.
    for (const broken of [{taskId: 'computer-task'}, {taskId: 'a b', captureSession: '7'},
      {taskId: 'computer-task', captureSession: 'seven'}, 'text', 7, []]) {
      f.state({...working, computerWork: broken});
      assert.equal(monitor(f).querySelector('.native-computer-work').hidden, true, JSON.stringify(broken));
    }
  } finally {f.close();}
});

test('an ended or stopped live window stays as a record without a picture until the next turn', async () => {
  const f = await fixture();
  try {
    const native = f.window.ContinuumNative;
    const done = workRecord({stage: 'COMPLETED', actions: [dispatched(3, {state: 'VERIFIED'})]});
    f.state({busy: true, presence: 'THINKING', computerWork: workRecord({actions: [dispatched(3)]})});
    native.message({...binding, user: true, text: 'Öffne den Download-Ordner'});
    assert.equal(record(f).hidden, true, 'No record without a real end');
    assert.equal(record(f).childElementCount, 0);
    native.computer(liveFrame());await f.decode();
    native.computer({...binding, generation: 1, sequence: 2, active: false, status: 'ENDED', reason: 'Live-Bild angehalten'});
    assert.equal(monitor(f), null, 'The monitor itself is gone with its source');
    assert.equal(f.document.querySelector('.native-computer-image'), null);
    const shown = record(f);
    assert.equal(shown.hidden, false);
    assert.equal(shown.dataset.state, 'ended');
    assert.equal(shown.querySelector('.native-record-badge').textContent, 'BEENDET');
    assert.equal(shown.querySelector('strong').textContent, 'Dateien · com.example.files');
    assert.equal(shown.querySelector('p').textContent, 'Live-Bild angehalten. Es wird kein Bild mehr übertragen.');
    assert.equal(shown.querySelector('img,canvas,video'), null, 'A record never keeps a picture');
    assert.deepEqual(logRows(shown), [['click', 'dispatched', 'Tippen', 'ausgelöst']]);
    assert.ok(f.document.querySelector('#workspace').classList.contains('native-work-active'), 'It is shown in the work window');
    // The task's own end and the reply keep it; its inputs follow Android's record.
    f.state({busy: false, presence: 'IDLE', computerWork: done});
    native.message({...binding, user: false, text: 'Der Download-Ordner ist geöffnet.'});
    assert.equal(record(f).hidden, false);
    assert.equal(record(f).querySelector('.native-computer-stage').textContent, 'Download-Ordner öffnen · Auftrag abgeschlossen');
    assert.deepEqual(logRows(record(f)), [['click', 'verified', 'Tippen', 'Wirkung bestätigt']]);
    f.state({busy: false, presence: 'IDLE', cpu: 41, computerWork: done});
    assert.equal(record(f).querySelector('.native-record-badge').textContent, 'BEENDET', 'Periodic snapshots keep it');
    // The next turn starts: the record of the previous one leaves.
    f.state({busy: true, presence: 'THINKING', computerWork: done});
    assert.equal(record(f).hidden, true);
    assert.equal(record(f).childElementCount, 0);
  } finally {f.close();}
  const g = await fixture();
  try {
    const native = g.window.ContinuumNative;
    g.state({busy: true, presence: 'THINKING', computerWork: workRecord()});
    native.computer(liveFrame());await g.decode();
    // The owner's Stop, as Android confirms it to the page.
    native.section({...binding, name: 'stop'});
    assert.equal(monitor(g), null);
    assert.equal(record(g).dataset.state, 'stopped');
    assert.equal(record(g).querySelector('.native-record-badge').textContent, 'GESTOPPT');
    assert.match(record(g).querySelector('p').textContent, /^Du hast das Live-Bild gestoppt\./);
    // Closing the window closes the record; nothing is sent for it.
    const sent = g.navigations.length;
    g.click(g.document.querySelector('#close-button'));
    assert.equal(record(g)?.hidden ?? true, true);
    assert.equal(g.navigations.length, sent);
    // A new source starts a new window and never inherits an old record; a view change keeps none either.
    native.computer(liveFrame({generation: 2}));await g.decode();
    native.computer({...binding, generation: 2, sequence: 2, active: false, status: 'ENDED', reason: 'Aufnahme beendet'});
    assert.equal(record(g).dataset.state, 'ended');
    native.computer(liveFrame({generation: 3}));
    assert.equal(record(g)?.hidden ?? true, true, 'A new generation is a new window');
    native.computer({...binding, generation: 3, sequence: 2, active: false, status: 'ENDED', reason: 'Aufnahme beendet'});
    assert.equal(record(g).hidden, false);
    g.state({sessionId: anotherSession});
    assert.equal(g.document.querySelector('.native-monitor-record:not([hidden])'), null);
    // A source that never was on screen leaves no record.
    g.window.ContinuumNative.computer({...binding, sessionId: anotherSession, viewId: anotherView, viewRevision: 2,
      generation: 1, sequence: 1, active: false, status: 'ENDED', reason: 'Nie gezeigt'});
    assert.equal(g.document.querySelector('.native-monitor-record:not([hidden])'), null);
  } finally {g.close();}
});

test('Android\'s open capture consent is shown as waiting for the owner, who is faced once', async () => {
  const f = await fixture();
  try {
    f.state({busy: true, presence: 'THINKING'});
    assert.equal(f.gazes.length, 0);
    f.state({busy: true, presence: 'THINKING', computerWork: {consentPending: true}});
    const shown = record(f);
    assert.equal(shown.hidden, false);
    assert.equal(shown.dataset.state, 'consent');
    assert.equal(shown.querySelector('.native-record-badge').textContent, 'WARTET AUF DEINE FREIGABE');
    assert.match(shown.querySelector('p').textContent, /Ohne deine Zustimmung wird kein Bild übertragen/);
    assert.equal(shown.querySelector('strong'), null, 'No target is named before Android reports one');
    assert.equal(shown.querySelector('img,canvas,video'), null);
    assert.equal(f.gazes.length, 1);assert.ok(forward(f.gazes[0]), 'A consent concerns the owner');
    f.state({busy: true, presence: 'THINKING', cpu: 44, computerWork: {consentPending: true}});
    assert.equal(f.gazes.length, 1, 'A repeated snapshot is no new reason');
    // Consent given and the source started: the monitor replaces the note.
    f.state({busy: true, presence: 'THINKING', computerWork: workRecord({consentPending: false, stage: 'ACCEPTED'})});
    assert.equal(record(f).hidden, true);
    f.window.ContinuumNative.computer(liveFrame());await f.decode();
    assert.equal(monitor(f).dataset.monitorState, 'live');
    // Anything but an explicit true is no consent.
    for (const value of ['true', 1, null]) {
      const g = await fixture();
      try {
        g.state({busy: true, presence: 'THINKING', computerWork: {consentPending: value}});
        assert.equal(g.document.querySelector('.native-monitor-record:not([hidden])'), null, String(value));
      } finally {g.close();}
    }
  } finally {f.close();}
});

test('the work window states its real state: running with step count, answer being written, approval, failure, delivery', async () => {
  const f = await fixture();
  try {
    const native = f.window.ContinuumNative;
    const badge = () => {
      const node = f.document.querySelector('#native-live-panel .op-console-head .op-mode-label');
      return [node.textContent, node.dataset.windowState];
    };
    f.state();
    native.message({...binding, user: true, text: 'Suche die Unterlagen'});
    assert.deepEqual(badge(), ['LOKALE DATEN', 'local']);
    f.state({busy: true, presence: 'THINKING'});
    assert.deepEqual(badge(), ['LÄUFT', 'running'], 'Without steps no count is made up');
    const steps = [{label: 'Gedächtnis durchsucht', state: 'DONE'}, {label: 'Websuche', state: 'ACTIVE'},
      {label: 'Ergebnis prüfen', state: 'WAITING'}];
    f.state({busy: true, presence: 'RESEARCHING', activity: activityPacket({steps})});
    assert.deepEqual(badge(), ['LÄUFT · 1/3', 'running']);
    native.draft({...binding, text: 'Die Unterlagen'});
    assert.deepEqual(badge(), ['ANTWORT ENTSTEHT', 'writing']);
    native.draft({...binding, text: ''});
    assert.deepEqual(badge(), ['LÄUFT · 1/3', 'running']);
    f.state({busy: true, presence: 'WAITING_FOR_APPROVAL', activity: activityPacket({steps})});
    assert.deepEqual(badge(), ['WARTET AUF DEINE FREIGABE', 'approval']);
    f.state({busy: true, presence: 'BLOCKED', activity: activityPacket({steps})});
    assert.deepEqual(badge(), ['BLOCKIERT', 'blocked']);
    const finished = steps.map(step => ({...step, state: 'DONE'}));
    native.message({...binding, user: false, text: 'Die Unterlagen liegen im Ordner Heizung.'});
    f.state({busy: false, presence: 'IDLE', activity: activityPacket({revision: 2, active: false, steps: finished})});
    assert.deepEqual(badge(), ['ANTWORT GELIEFERT', 'answered']);
    const reply = f.document.querySelector('#native-live-panel .native-assistant');
    assert.equal(reply.dataset.result, 'true', 'The delivered answer is this turn\'s result while it is the last line');
    f.state({busy: false, presence: 'IDLE', activity: activityPacket({revision: 3, active: false,
      steps: [finished[0], {label: 'Websuche', state: 'FAILED'}, finished[2]]})});
    assert.deepEqual(badge(), ['SCHRITT FEHLGESCHLAGEN', 'failed'], 'A failed step is not hidden behind the answer');
    // The owner's next line: the answer is no longer the last one, the result mark leaves.
    native.message({...binding, user: true, text: 'Danke'});
    f.state({busy: false, presence: 'IDLE'});
    assert.deepEqual(badge(), ['LOKALE DATEN', 'local']);
    assert.equal(f.document.querySelector('#native-live-panel [data-result]'), null);
    // A restored transcript is history, not a delivery.
    native.transcript({...binding, entries: [{user: true, text: 'Frage'}, {user: false, text: 'Alte Antwort'}]});
    assert.deepEqual(badge(), ['LOKALE DATEN', 'local']);
    assert.equal(f.document.querySelector('#native-live-panel [data-result],#native-live-panel [data-arrival]'), null);
  } finally {f.close();}
});

test('sources form a wall of small screens: one tile per delivered source with its host, title and address', async () => {
  const f = await fixture();
  try {
    const native = f.window.ContinuumNative;
    f.state();
    native.sources({...binding, sourceSetId, items: [
      {id: sourceId, title: 'Förderung Wärmepumpe', url: 'https://www.land-oberoesterreich.gv.at/foerderungen?x=1'},
      {id: '66666666-6666-4666-8666-666666666667', title: '<b>Bundesförderung</b>', url: 'http://umweltfoerderung.at/privat'}]});
    const card = f.document.querySelector('#native-live-panel .native-source-card');
    const tiles = [...card.querySelectorAll('.native-source-link')];
    assert.equal(tiles.length, 2, 'One tile per source, none added');
    assert.deepEqual(tiles.map(tile => tile.querySelector('.native-source-host').textContent),
      ['www.land-oberoesterreich.gv.at', 'umweltfoerderung.at'], 'The host is taken from the real address');
    assert.deepEqual(tiles.map(tile => tile.querySelector('strong').textContent), ['Förderung Wärmepumpe', '<b>Bundesförderung</b>']);
    assert.equal(tiles[1].querySelector('b'), null, 'Markup stays text');
    assert.equal(card.querySelector('img,iframe,canvas,p'), null, 'No page is drawn and no excerpt exists');
    assert.equal(card.dataset.arrival, 'sources', 'Marked once on arrival');
    // The next rebuild of the feed shows the same wall without replaying its arrival.
    native.message({...binding, user: true, text: 'Weiter'});
    assert.equal(f.document.querySelector('#native-live-panel .native-source-card').dataset.arrival, undefined);
    assert.equal(f.document.querySelectorAll('#native-live-panel .native-source-link').length, 2);
  } finally {f.close();}
});

test('window effects are opt-in per tier and absent while the device computes; marks for one pass are set once', async () => {
  const f = await fixture();
  try {
    const native = f.window.ContinuumNative, body = f.document.body;
    const effects = () => body.dataset.nativeEffects;
    f.state();
    assert.equal(effects(), 'full', 'Without a tier the full preview, as for the frame limit');
    for (const [quality, tier] of [['FULL', 'full'], ['BALANCED', 'balanced'], ['REDUCED', 'light'], ['UNBEKANNT', 'full']]) {
      for (const presence of ['IDLE', 'LISTENING', 'SPEAKING', 'WAITING_FOR_APPROVAL', 'BLOCKED']) {
        f.state({quality, presence});
        assert.equal(effects(), tier, quality + ' ' + presence);
      }
      for (const presence of ['THINKING', 'RESEARCHING', 'LEARNING']) {
        f.state({quality, presence, busy: true});
        assert.deepEqual([effects(), body.dataset.nativeCompute], ['rest', 'local'], quality + ' ' + presence + ' on the device');
        f.state({quality, presence, busy: true, cloudBrain: true});
        assert.equal(effects(), tier, quality + ' ' + presence + ' in the cloud');
      }
    }
    f.state({busy: true});
    assert.equal(effects(), 'rest', 'An older Android without presence: busy counts as computing on the device');
    // A local turn that is still running under another presence: the Code68 stage keeps its tested behaviour
    // (alive for the lip movement), the added window effects stay off because the model may still compute.
    for (const [presence, extra] of [['SPEAKING', {speaking: true}], ['LISTENING', {listening: true}],
      ['WAITING_FOR_APPROVAL', {}], ['BLOCKED', {}]]) {
      f.state({quality: 'FULL', presence, busy: true, ...extra});
      assert.deepEqual([effects(), body.dataset.nativeCompute, body.dataset.frameLimit], ['rest', undefined, '30'], presence);
      f.state({quality: 'FULL', presence, busy: true, cloudBrain: true, ...extra});
      assert.equal(effects(), 'full', presence + ' with a cloud brain');
    }
    // Which window the workspace really shows.
    f.state();
    assert.equal(body.dataset.nativeWindow, undefined);
    native.message({...binding, user: true, text: 'Frage'});
    assert.equal(body.dataset.nativeWindow, 'work');
    native.document({...binding, documentId, name: 'Argon4.pdf', pageCount: 3});
    assert.equal(body.dataset.nativeWindow, 'result');
    native.clearDocument(binding);
    f.state({files: filesPacket()});
    assert.equal(body.dataset.nativeWindow, 'files');
    f.state();
    native.computer(liveFrame());
    assert.equal(body.dataset.nativeWindow, 'live');
    native.computer({...binding, generation: 1, sequence: 2, active: false, status: 'ENDED', reason: 'Ende'});
    assert.equal(body.dataset.nativeWindow, 'work');
    // A step whose state Android changed is marked for exactly that rendering.
    const changed = () => stepRows(f).map((row, index) =>
      f.document.querySelectorAll('.native-activity-steps li')[index].dataset.changed === 'true');
    f.state({busy: true, presence: 'THINKING', activity: activityPacket()});
    assert.deepEqual(changed(), [false, false], 'A first list has no change');
    f.state({busy: true, presence: 'THINKING', activity: activityPacket({revision: 2,
      steps: [{label: 'Gedächtnis durchsucht', state: 'DONE'}, {label: 'Websuche', state: 'DONE'}, {label: 'Prüfung', state: 'ACTIVE'}]})});
    assert.deepEqual(changed(), [false, true, false], 'Only the step that moved; a new step is not a change');
    f.state({busy: true, presence: 'THINKING', activity: activityPacket({revision: 3,
      steps: [{label: 'Gedächtnis durchsucht', state: 'DONE'}, {label: 'Websuche', state: 'DONE'}, {label: 'Prüfung', state: 'ACTIVE'}]})});
    assert.deepEqual(changed(), [false, false, false]);
    // The delivered answer arrives once.
    native.message({...binding, user: false, text: 'Antwort'});
    assert.equal(f.document.querySelector('#native-live-panel .native-assistant').dataset.arrival, 'reply');
    native.message({...binding, user: true, text: 'Noch eine Frage'});
    assert.equal(f.document.querySelector('#native-live-panel [data-arrival]'), null);
    // The depth layer and the window frame carry nothing and are mounted once.
    for (const selector of ['.native-depth-veil', '.workspace-shell>.native-window-frame']) {
      assert.equal(f.document.querySelectorAll(selector).length, 1, selector);
      const layer = f.document.querySelector(selector);
      assert.equal(layer.getAttribute('aria-hidden'), 'true');
      assert.equal(layer.textContent, '');
      assert.equal(layer.querySelector('canvas,img,svg'), null);
    }
    assert.equal(f.document.querySelector('.native-depth-veil').parentElement, f.document.querySelector('.presence-core'));
    assert.equal(f.document.querySelectorAll('canvas').length, f.document.querySelectorAll('#scene-canvas').length,
      'The binding adds no canvas of its own');
  } finally {f.close();}
});

test('glances for quickly repeating events are rate-limited, rarer while the device computes, and start nothing continuous', async () => {
  const f = await fixture();
  try {
    const native = f.window.ContinuumNative;
    const land = (sequence, values = working) => f.state({...values, computerWork: workRecord({
      actions: Array.from({length: sequence}, (_, index) => dispatched(index + 1, {state: index + 1 === sequence ? 'DISPATCHED' : 'VERIFIED'}))})});
    const stream = async sequence => {native.computer(liveFrame({sequence, sourceSequence: 11 + sequence}));await f.decode();};
    f.state({...working, computerWork: workRecord()});
    await stream(1);
    assert.equal(f.gazes.length, 1, 'The appearing picture');
    land(1);
    assert.equal(f.gazes.length, 1, 'An input right after another glance does not jerk the head again');
    f.advance(1200);await stream(2);land(2);
    assert.equal(f.gazes.length, 2);assert.equal(f.gazes[1].target, monitor(f));
    f.advance(400);land(3);f.advance(400);land(4);
    assert.equal(f.gazes.length, 2, 'Inputs in quick succession share one glance');
    f.advance(400);await stream(3);land(5);
    assert.equal(f.gazes.length, 3);
    // A glance is an event for the avatar's own frame loop. The page asks the clock for nothing because of it:
    // what is pending are the one-shot timers of the frame lease and the marker, never a repeating one.
    const before = f.pendingWork();
    f.advance(1200);await stream(4);
    const pending = f.pendingWork().filter(kind => kind !== 'raf');
    land(6);
    assert.equal(f.gazes.length, 4);
    assert.deepEqual(f.pendingWork().filter(kind => kind !== 'raf'), pending, 'The glance and its marker reuse the one marker timer');
    assert.equal(f.pendingWork().includes('timer:repeat'), false);
    assert.equal(before.includes('timer:repeat'), false);
    // While the on-device model computes the same events move the head far less often: at most once in four
    // seconds, counted from the last glance, instead of once in 1.2 seconds.
    const local = {busy: true, presence: 'THINKING'};
    f.advance(1300);await stream(5);land(7, local);
    assert.equal(f.document.body.dataset.nativeCompute, 'local');
    f.advance(1300);await stream(6);land(8, local);
    f.advance(1300);await stream(7);land(9, local);
    assert.equal(f.gazes.length, 4, 'Three inputs within four seconds of the last glance: the head rests');
    assert.ok(pointer(f), 'The marker itself is shown either way');
    f.advance(1300);await stream(8);land(10, local);
    assert.equal(f.gazes.length, 5);assert.equal(f.gazes[4].target, monitor(f));
    assert.equal(f.pendingWork().includes('timer:repeat'), false);
  } finally {f.close();}
  // The answer being written draws one look per turn, and only where it really is on screen.
  const g = await fixture();
  try {
    const native = g.window.ContinuumNative;
    g.state();
    native.message({...binding, user: true, text: 'Erkläre mir das'});
    g.state({busy: true, presence: 'THINKING'});
    native.draft({...binding, text: 'Das'});
    assert.equal(g.gazes.length, 1);
    assert.equal(g.gazes[0].target, g.document.querySelector('.native-draft'));
    for (const text of ['Das ist', 'Das ist der', 'Das ist der Anfang']) {g.advance(5000);native.draft({...binding, text});}
    assert.equal(g.gazes.length, 1, 'The growing text is not followed word by word');
    native.message({...binding, user: false, text: 'Das ist die Antwort.'});
    assert.equal(g.gazes.length, 2);assert.ok(forward(g.gazes[1]), 'The delivered answer turns her to the owner');
    // Behind an open document the draft row is not on screen: nothing to look at.
    native.document({...binding, documentId, name: 'Argon4.pdf', pageCount: 3});
    const looks = g.gazes.length;
    g.state({busy: true, presence: 'THINKING'});
    g.advance(5000);
    native.draft({...binding, text: 'Im Dokument'});
    assert.equal(g.gazes.length, looks);
  } finally {g.close();}
});

test('heat contract of the window styles: no backdrop-filter, no filter, motion only in the full and balanced tiers', () => {
  const css = readFileSync(resolve(assetRoot, 'native-layer.css'), 'utf8');
  const bridge = readFileSync(resolve(assetRoot, 'native-bridge.js'), 'utf8');
  assert.doesNotMatch(css.replace(/\/\*[\s\S]*?\*\//g, ''), /backdrop-filter/,
    'The Android layer never blurs what lies behind a panel');
  assert.doesNotMatch(bridge, /backdrop-filter|backdropFilter|createElement\('canvas'\)|getContext\(/,
    'The binding creates no canvas and sets no blur');
  const start = css.indexOf('Code70 · cinematic windows');
  assert.ok(start > 0, 'The section exists');
  const section = css.slice(start).replace(/\/\*[\s\S]*?\*\//g, '');
  assert.doesNotMatch(section, /(^|[;{\s])filter\s*:/, 'No filter in the window section');
  assert.doesNotMatch(section, /transition\s*:(?!\s*none)/, 'The window section adds no transition');
  // Keyframes: transform and opacity only.
  const frames = [...section.matchAll(/@keyframes\s+([\w-]+)\s*\{((?:[^{}]*\{[^{}]*\})*)\s*\}/g)];
  assert.deepEqual(frames.map(match => match[1]).sort(),
    ['native-live-lamp', 'native-marker-ping', 'native-row-arrive', 'native-step-settle', 'native-tile-arrive']);
  for (const [, name, body] of frames) {
    const properties = [...body.matchAll(/([\w-]+)\s*:/g)].map(match => match[1]);
    assert.ok(properties.length > 0 && properties.every(property => property === 'transform' || property === 'opacity'),
      name + ' animates ' + properties.join(', '));
  }
  // Every rule that starts an animation is limited to the full or balanced tier by each of its selectors.
  const rules = [...section.replace(/@keyframes[^{]*\{(?:[^{}]*\{[^{}]*\})*\s*\}/g, '')
    .matchAll(/([^{}]+)\{([^{}]*)\}/g)].map(match => [match[1].trim(), match[2]]);
  let moving = 0;
  for (const [selectors, body] of rules) {
    const animation = /animation\s*:\s*([^;]+)/.exec(body)?.[1];
    if (!animation || /^none\b/.test(animation.trim())) continue;
    moving++;
    for (const selector of selectors.split(',')) {
      assert.match(selector, /\[data-native-effects="(full|balanced)"\]/, 'Unconditional animation: ' + selector.trim());
      assert.doesNotMatch(selector, /\[data-native-effects="(rest|light)"\]/);
      if (/infinite/.test(animation))
        assert.match(selector, /\[data-native-effects="full"\].*\[data-monitor-state="live"\]/,
          'A loop runs in the full tier only, on a monitor that really is live: ' + selector.trim());
    }
    if (!/infinite/.test(animation)) assert.match(animation, /\s1(\s|$)/, 'Finite: ' + animation);
  }
  assert.ok(moving >= 5, 'The rules were found');
  assert.equal(rules.filter(([, body]) => /infinite/.test(body)).length, 1, 'Exactly one loop exists in the section');
});
