/* Android-only binding. Source PR #850 HTML, CSS, JS and 3D assets stay unchanged. */
(() => {
  'use strict';
  if (window.ContinuumNative) return;
  // Frame governor (heat). The original scene, orb and avatar redraw on every display frame – up to 120 times a
  // second on the Fold7 – without a limit of their own, next to the local model. This binding loads before them
  // and hands their animation-frame callbacks on at most `limit` times per second, with the real frame time, so
  // every time-based motion keeps its speed. No original file changes. Android's tier and the running work set
  // the limit (see renderPresence).
  const frameGovernor = (() => {
    const request = typeof window.requestAnimationFrame==='function' ? window.requestAnimationFrame.bind(window) : null;
    if(request===null) return {set() {}, limit:() => null, probe:() => null};
    let limit = 30, waiting = new Map(), serial = 0, armed = false, last = -Infinity;
    // Frame counter of the owner's „Wärme- und Zeitmessung“. Off (null) unless Android starts it; it only counts
    // what this governor really hands on and never changes what is drawn. Intervals between delivered frames are
    // kept in 0.5 ms classes up to 150 ms (same layout as the Android report), the rest with its exact maximum.
    const PROBE_BUCKET_MS = .5, PROBE_BUCKETS = 300, PROBE_SECONDS = 900;
    let probe = null;
    const probeNow = () => typeof performance==='object' && typeof performance.now==='function' ? performance.now() : Date.now();
    const probeCommands = {
      start() {
        probe = {startedAt:probeNow(), ticks:0, delivered:0, first:null, previous:null, counts:new Map(), overflow:0,
          maxMs:0, sumMs:0, perSecond:[], limitChanges:[]};
        return true;
      },
      read() {
        if(probe===null) return null;
        return {schema:'continuum-stage-frame-probe-v1', delivered:probe.delivered, ticks:probe.ticks, limit,
          observedMs:Math.round(probeNow()-probe.startedAt),
          limitChanges:probe.limitChanges.slice(), perSecond:probe.perSecond.slice(),
          visibility:typeof document.visibilityState==='string' ? document.visibilityState : 'unknown',
          histogram:{bucketMs:PROBE_BUCKET_MS, bucketCount:PROBE_BUCKETS,
            counts:[...probe.counts.entries()].sort((a,b) => a[0]-b[0]), overflow:probe.overflow,
            maxMs:Math.round(probe.maxMs*1000)/1000, sumMs:Math.round(probe.sumMs*1000)/1000}};
      },
      stop() {const result = probeCommands.read();probe = null;return result;}
    };
    const probeDelivered = time => {
      if(probe.previous!==null) {
        const gap = time-probe.previous, index = Math.floor(gap/PROBE_BUCKET_MS);
        if(gap>=0) {
          if(index>=PROBE_BUCKETS) probe.overflow++;
          else probe.counts.set(index, (probe.counts.get(index)||0)+1);
          if(gap>probe.maxMs) probe.maxMs = gap;
          probe.sumMs += gap;
        }
      } else probe.first = time;
      probe.previous = time;probe.delivered++;
      const second = Math.floor((time-probe.first)/1000);
      if(second>=0 && second<PROBE_SECONDS) {
        while(probe.perSecond.length<=second) probe.perSecond.push(0);
        probe.perSecond[second]++;
      }
    };
    const pump = time => {
      armed = false;
      // Without a real frame time (no browser clock) nothing is held back.
      const timed = typeof time==='number' && Number.isFinite(time);
      if(probe!==null && timed) probe.ticks++;
      if(!timed || limit===null || time-last >= 1000/limit-2) {
        if(timed) last = time;
        const due = waiting;
        waiting = new Map();
        if(probe!==null && timed && due.size) probeDelivered(time);
        for(const callback of due.values()) {
          try {callback(time);}
          catch(error) {if(typeof window.reportError==='function')window.reportError(error);else setTimeout(() => {throw error;});}
        }
      }
      if(waiting.size && !armed) {armed = true;request(pump);}
    };
    window.requestAnimationFrame = callback => {
      const id = ++serial;
      waiting.set(id, callback);
      if(!armed) {armed = true;request(pump);}
      return id;
    };
    window.cancelAnimationFrame = id => {waiting.delete(id);};
    return {set(value) {
      if(probe!==null && value!==limit && probe.limitChanges.length<64)
        probe.limitChanges.push([Math.round(probeNow()-probe.startedAt), value]);
      limit = value;
    }, limit:() => limit,
      probe:command => Object.prototype.hasOwnProperty.call(probeCommands, command) ? probeCommands[command]() : null};
  })();
  const routes = new Set(['home','chat','chats','new-chat','files','research',
    'agents','memory','device','settings','settings-voice','settings-model',
    'settings-internet','settings-appearance','voice-input','voice-input-long-press','stop','storage-clean']);
  const actions = Object.freeze({home:'home',settings:'settings',agents:'agents',
    'settings-voice':'settings-voice','settings-model':'settings-model',
    'settings-internet':'settings-internet','settings-appearance':'settings-appearance',
    'new-chat':'new-chat','quick-files':'files','quick-memory':'memory',
    'research-demo':'research','open-demo':'files',document:'files',
    'local-file':'files','quick-source':'research','run-agents':'agents',
    'run-computer':'device','stop-agents':'stop','stop-computer':'stop',
    listen:'voice-input',cancel:'stop','memory-source':'memory'});
  const layoutActions = new Set(['expand','toggle-chats','toggle-instruments','close-panels']);
  const chatId = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/;
  const DEFAULT_COMMAND_LIMIT = 11500, MAX_MESSAGE = 68096, MAX_DRAFT = 262144, MAX_ANSWER_DRAFT = 6000, HISTORY = 36;
  const $ = selector => document.querySelector(selector);
  // Keep the approved composer slot and attributes. A real textarea is required
  // because HTML input.value silently removes line breaks during native restore.
  const originalInput=$('#command-input');
  if(originalInput?.tagName==='INPUT') {
    const field=document.createElement('textarea');
    for(const attribute of originalInput.attributes)
      if(attribute.name!=='type')field.setAttribute(attribute.name,attribute.value);
    field.rows=1;field.value=originalInput.value;field.setAttribute('aria-multiline','true');
    originalInput.replaceWith(field);
  }
  let snapshot = null, session = '', lines = [], rendering = false, currentDocument = null;
  let pendingCommand = null, requestNumber = 0, dirtyDraft = false, lastDraft = '';
  let telemetryAt = null, lastScene = null, feedRevision = 0, latestDraftRevision = -1;
  let page = '', view = '', viewRevision = -1, clientEditRevision = 0, sentEditRevision = -1;
  let documentRevision = 0, micPress = null, suppressMicClick = false;
  const sourceSets = new Map();
  const computerStates = new Set(['NO_SOURCE','WAITING','LIVE','PAUSED','COVERED','ENDED']);
  const visemes = new Set(['SIL','PP','FF','TH','DD','KK','CH','SS','NN','RR','AA','E','I','O','U']);
  const speechSources = new Set(['PCM_ENVELOPE','WORD_RANGE_FALLBACK','TEXT_PACE_FALLBACK']);
  const computerGate = {generation:0,sequence:-1,ended:false,binding:null};
  const speechGate = {generation:0,sequence:-1,ended:false,utteranceId:null};
  let currentComputer = null, computerVisible = false, displayedFrame = null, pendingFrame = null;
  let visibilityCheck = 0, speechExpiry = 0, currentSpeech = null;
  const readings = [], nativeMeters = new Map();
  const feedVersions = new WeakMap();
  // Optional Android presence. Every entry maps to a state the original scene and
  // avatar accept; `orb` is the additional mode name of the original orb only.
  const presences = Object.freeze({
    IDLE:{scene:'idle',status:'BEREIT',label:'Bereit',detail:'Dein nächster Auftrag beginnt hier.'},
    LISTENING:{scene:'listening',status:'HÖRT ZU',label:'Ich höre zu',detail:'Deine Sprache wird aufgenommen.'},
    THINKING:{scene:'working',status:'DENKT',label:'In Bearbeitung',detail:'Der aktuelle Auftrag wird verarbeitet.'},
    RESEARCHING:{scene:'working',orb:'research',status:'RECHERCHIERT',label:'Recherche läuft',detail:'Die Recherche läuft.'},
    LEARNING:{scene:'working',orb:'learning',status:'LERNT',label:'Lernen läuft',detail:'Der Lernvorgang läuft.'},
    WAITING_FOR_APPROVAL:{scene:'idle',calm:true,status:'WARTET AUF FREIGABE',label:'Rückfrage',
      detail:'Der Auftrag wartet auf deine Freigabe.'},
    SPEAKING:{scene:'speaking',status:'SPRICHT',label:'Ich spreche',detail:'Die Sprachausgabe ist aktiv.'},
    BLOCKED:{scene:'idle',calm:true,status:'BLOCKIERT',label:'Blockiert',detail:'Der Auftrag ist blockiert.'}});
  const qualities = Object.freeze({FULL:'high',BALANCED:'medium',REDUCED:'low'});
  // Frames per second the stage may draw: [otherwise, while the local model computes]. Speaking keeps the first
  // value of its tier for the lip movement.
  const frameLimits = Object.freeze({FULL:[30,15],BALANCED:[20,10],REDUCED:[10,6]});
  const computing = Object.freeze({THINKING:true,RESEARCHING:true,LEARNING:true});
  // Owner finding (Fold7, Code67): with the effects on, the device overheated as soon as an order started. While
  // the model computes ON THE DEVICE the stage must leave the processor to it: the orb rests on its still frame,
  // the scene runs on its lowest setting, and only these few frames a second are drawn; a slow pulse of the corona
  // (opacity only) shows the thinking. With a cloud brain nothing computes locally and the full motion stays.
  const localComputeFrames = Object.freeze({FULL:5,BALANCED:4,REDUCED:3});
  /** True while Android reports work in progress that is computed by the on-device model. */
  function computesLocally(state) {
    if(state.cloudBrain===true)return false;
    return own(presences,state.presence) ? own(computing,state.presence)
      : state.busy===true && state.speaking!==true && state.listening!==true;
  }
  const activityKinds = Object.freeze({THINK:'DENKEN',RESEARCH:'RECHERCHE',LEARN:'LERNEN',DEVELOP:'ENTWICKLUNG',
    EXECUTE:'AUSFÜHRUNG',VERIFY:'PRÜFUNG',TOOL:'WERKZEUG',WORKER:'HELFER',MEMORY:'GEDÄCHTNIS',
    DOCUMENT:'DOKUMENT',CALCULATION:'BERECHNUNG',NOTIFICATION:'BENACHRICHTIGUNG'});
  const stepStates = Object.freeze({ACTIVE:'läuft',WAITING:'wartet',DONE:'erledigt',BLOCKED:'blockiert',
    FAILED:'fehlgeschlagen',UNVERIFIED:'ungeprüft'});
  const own = (map,key) => typeof key==='string' && Object.prototype.hasOwnProperty.call(map,key);
  let currentActivity = null, orbMode = 'idle', orbRefined = false, orbStale = false, orbRepair = false;
  let orbEmitting = false, lastPresence = null, lastIntensity = null, lastQuality = null, audioSent = false;
  // Light exchange between the orb's shell and the avatar in front of it: inward to the avatar
  // while she listens, outward to the orb while it works. The presence Android reported, never a timer.
  const energies = Object.freeze({IDLE:'idle',LISTENING:'to-avatar',THINKING:'to-orb',RESEARCHING:'to-orb',
    LEARNING:'to-orb',WAITING_FOR_APPROVAL:'hold',SPEAKING:'speech',BLOCKED:'hold'});
  const sceneEnergies = Object.freeze({idle:'idle',listening:'to-avatar',working:'to-orb',speaking:'speech'});
  const fileKinds = Object.freeze({SEARCH:'DATEISUCHE',FOLDER:'ORDNER',APPS:'APP-SUCHE'});
  const MAX_FILES = 12, MAX_FILE_NAME = 96;
  // One real turn on the stage: activation → opening → projected → settling → idle.
  let workPhase = 'idle', workTurn = false, workReplied = false, workAwaitingReply = false;
  let workSurface = null, workSources = false, workToken = 0, workReturned = true;
  let currentFiles = null, filesDismissed = null, typing = false, lastActivityGaze = null;
  // Code70 · the three windows say what really happens. Every label names a fact Android reported – a step, a
  // frame, a dispatched input, a consent that is still open – and nothing here runs on a clock of its own.
  const workStages = Object.freeze({ACCEPTED:'Auftrag angenommen',FOCUS:'Ziel gewählt',DISPATCHED:'Eingabe ausgelöst',
    OBSERVED:'Wirkung wird geprüft',VERIFIED:'Schritt geprüft',ASK_OWNER:'Rückfrage an dich',PAUSED:'Arbeit pausiert',
    STOPPED:'Auftrag gestoppt',ERROR:'Auftrag angehalten',COMPLETED:'Auftrag abgeschlossen'});
  const actionKinds = Object.freeze({CLICK:'Tippen',SET_TEXT:'Text eingeben',SCROLL:'Scrollen',OPEN_APP:'App öffnen'});
  const actionStates = Object.freeze({DISPATCHED:'ausgelöst',VERIFIED:'Wirkung bestätigt',
    UNCONFIRMED:'Wirkung nicht bestätigt'});
  // Strength of the added window effects: none of them while the device computes ("rest"), then as much as
  // Android's thermal tier allows. Unknown tier = the full preview, as for the frame limit.
  const effectTiers = Object.freeze({FULL:'full',BALANCED:'balanced',REDUCED:'light'});
  const MAX_ACTIONS = 6, MARKER_MS = 2400, GLANCE_MS = 1200, GLANCE_LOCAL_MS = 4000;
  const packageName = /^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+$/;
  let currentWork = null, workSignature = 'null', actionKey = '', actionSequence = -1;
  let marker = null, markerTimer = 0, computerRecord = null, lastGlanceAt = -Infinity;
  let replyLine = null, arrivedLine = null, arrivedSources = null, draftGlanced = false, lastConsent = false;
  const stepStatesShown = new Map();
  // Code71 · tactical command centre: words for what Android reports, and what is shown of it right now.
  const orderStates = Object.freeze({ACTIVE:'LÄUFT',WAITING:'WARTET AUF DICH',DONE:'ERLEDIGT',FAILED:'FEHLGESCHLAGEN',
    STOPPED:'GESTOPPT',SKIPPED:'ÜBERSPRUNGEN',BLOCKED:'BLOCKIERT',UNVERIFIED:'UNGEPRÜFT'});
  const orderOutcomes = Object.freeze({COMPLETED:'ERLEDIGT',FAILED:'FEHLGESCHLAGEN',STOPPED:'GESTOPPT',ENDED:'BEENDET'});
  const internetModes = Object.freeze({OFF:'AUS',ASK:'NACHFRAGE',ALWAYS_ON:'EIN'});
  // PowerManager's thermal status 0..6 by name; never a temperature.
  const thermalNames = Object.freeze(['NORMAL','LEICHT','ERHÖHT','HOCH','KRITISCH','NOTFALL','ABSCHALTUNG']);
  const effectNames = Object.freeze({full:'VOLL',balanced:'AUSGEWOGEN',light:'REDUZIERT',rest:'RUHE'});
  const checkStates = Object.freeze({OK:'BEREIT',WARN:'WARNUNG',FAIL:'FEHLER',OFF:'AUS'});
  const cardKinds = Object.freeze({calendar:'TERMINE',weather:'WETTER',messages:'NACHRICHTEN',tasks:'AUFGABEN',
    news:'NEUIGKEITEN',system:'SYSTEM'});
  const windowNames = Object.freeze({live:'LIVE-MONITOR',result:'ERGEBNIS',files:'DATEIEN',work:'ARBEITSFENSTER'});
  const MAX_ORDERS = 8, OPEN_ORDERS = 3, MAX_CHECKS = 12, MAX_CARDS = 6, MAX_STAGGER = 7;
  let currentTactical = null, currentOrders = null, currentChecks = [], currentCards = [];
  let armed = false, armingMode = null, armingToken = 0, ownerActed = false, lastTier;
  let shownWindow = null, shownName = '', windowQueued = false, windowSerial = 0;
  const flights = new WeakMap(), gliding = new Set(), leavers = new Map();
  function presenceOf(state) {return own(presences,state?.presence)?presences[state.presence]:null;}
  function sceneOf(state) {
    return presenceOf(state)?.scene ?? (state?.speaking===true?'speaking':state?.listening===true?'listening':
      state?.busy===true?'working':'idle');
  }
  function bounded(value,max) {
    const text=value.slice(0,max);
    return /[\uD800-\uDBFF]$/.test(text)?text.slice(0,-1):text;
  }
  /** Android's optional activity. Anything malformed renders as no activity at all. */
  function activityOf(value) {
    if(!value || typeof value!=='object' || !Number.isSafeInteger(value.revision) || value.revision<0 ||
      !own(activityKinds,value.kind) || typeof value.title!=='string' || typeof value.detail!=='string' ||
      typeof value.active!=='boolean')return null;
    const steps=[];
    for(const step of Array.isArray(value.steps)?value.steps.slice(0,6):[]) {
      if(!step || typeof step.label!=='string' || !step.label.trim() || !own(stepStates,step.state))continue;
      steps.push({label:bounded(step.label.trim(),80),state:step.state});
    }
    return {revision:value.revision,kind:value.kind,title:bounded(value.title.trim(),80),
      detail:bounded(value.detail.trim(),160),active:value.active,steps};
  }
  /** Android's optional file tool answer: names only. A path, markup or a contradiction is never shown. */
  function filesOf(value) {
    if(!value || typeof value!=='object' || !Number.isSafeInteger(value.revision) || value.revision<0 ||
      !own(fileKinds,value.kind) || typeof value.label!=='string' || !Number.isSafeInteger(value.total) ||
      value.total<0 || value.total>100000 || !Array.isArray(value.names))return null;
    const names=[];
    for(const entry of value.names.slice(0,MAX_FILES)) {
      if(typeof entry!=='string')continue;
      const name=entry.replace(/[\u0000-\u001f\u007f-\u009f]/g,'').trim();
      if(!name || /[\/\\]/.test(name))continue;
      names.push(bounded(name,MAX_FILE_NAME));
    }
    if(value.total<names.length)return null;
    return {revision:value.revision,kind:value.kind,total:value.total,more:value.more===true,names,
      label:bounded(value.label.replace(/[\u0000-\u001f\u007f-\u009f]/g,'').trim(),80)};
  }
  function filesOpen() {return !!currentFiles && currentFiles.revision!==filesDismissed;}
  /**
   * Android's optional record of the approved computer task: which inputs were really dispatched, where, and
   * whether their effect was confirmed. Anything malformed is dropped; a coordinate is never assumed.
   */
  function computerWorkOf(value) {
    if(!value || typeof value!=='object')return null;
    const consentPending=value.consentPending===true;
    if(typeof value.taskId!=='string' || !/^[A-Za-z0-9._:-]{1,128}$/.test(value.taskId) ||
      typeof value.captureSession!=='string' || !/^[0-9]{1,19}$/.test(value.captureSession))
      return consentPending?{consentPending,taskId:null,captureSession:null,stage:null,app:'',goal:'',actions:[]}:null;
    const actions=[];
    for(const item of Array.isArray(value.actions)?value.actions.slice(0,64):[]) {
      if(!item || !Number.isSafeInteger(item.sequence) || item.sequence<=0 || !own(actionKinds,item.kind) ||
        !own(actionStates,item.state) || actions.some(row=>row.sequence>=item.sequence))continue;
      const placed=Number.isFinite(item.x) && Number.isFinite(item.y) && item.x>=0 && item.x<=1 && item.y>=0 && item.y<=1;
      actions.push({sequence:item.sequence,kind:item.kind,state:item.state,x:placed?item.x:null,y:placed?item.y:null,
        frameSequence:Number.isSafeInteger(item.frameSequence) && item.frameSequence>=0?item.frameSequence:null});
    }
    const label=text=>typeof text==='string'?bounded(text.replace(/[\u0000-\u001f\u007f-\u009f]/g,'').trim(),80):'';
    return {consentPending,taskId:value.taskId,captureSession:value.captureSession,
      stage:own(workStages,value.stage)?value.stage:null,app:label(value.app),goal:label(value.goal),
      actions:actions.slice(-MAX_ACTIONS)};
  }
  /** The part of the target window a frame shows, in window coordinates, or null when Android measured none. */
  function viewportOf(value) {
    return Array.isArray(value) && value.length===4 && value.every(part=>Number.isFinite(part) && part>=0 && part<=1) &&
      value[0]<value[2] && value[1]<value[3]?value.slice():null;
  }
  /** Takes a new record; returns the input that was dispatched since the last one, if any. */
  function acceptWork(work) {
    const signature=JSON.stringify(work);
    if(signature===workSignature)return null;
    workSignature=signature;currentWork=work;
    const key=work?.taskId?work.taskId+'|'+work.captureSession:'';
    if(key!==actionKey) {actionKey=key;actionSequence=-1;clearMarker();}
    const newest=work?.actions.at(-1);
    if(!newest || newest.sequence<=actionSequence)return null;
    actionSequence=newest.sequence;
    // Only an input that is reported while still open has just happened; an older, settled one is history.
    return newest.state==='DISPATCHED'?newest:null;
  }
  function clearMarker() {clearTimeout(markerTimer);markerTimer=0;marker=null;}
  /** The record that belongs to the task whose image the monitor shows (or showed). */
  function workOf(source) {
    return currentWork?.taskId && source?.taskId===currentWork.taskId &&
      source.captureSession===currentWork.captureSession?currentWork:null;
  }
  // The original orb also knows RESEARCH and LEARNING, which the scene's own state
  // list cannot name. Announce them with the scene's own event after the scene has
  // settled; the avatar ignores names outside its list and keeps its valid state.
  function emitOrb() {
    orbStale=false;orbEmitting=true;
    try {window.dispatchEvent(new CustomEvent('continuum:scenechange',{detail:{state:orbMode}}));}
    finally {orbEmitting=false;}
  }
  function syncOrb(name,scene) {
    const refined=name!==scene, was=orbRefined;
    if(name===orbMode && refined===was && !orbStale)return;
    orbMode=name;orbRefined=refined;
    if(refined || was)emitOrb();
  }
  window.addEventListener('continuum:scenechange',event=>{
    // Light, detail and audio changes make the scene repeat its own coarser state.
    if(orbEmitting || !orbRefined || event.detail?.state===orbMode)return;
    orbStale=true;
    if(orbRepair)return;
    orbRepair=true;
    Promise.resolve().then(()=>{orbRepair=false;if(orbStale && orbRefined)emitOrb();});
  });
  function sceneAudio(level) {
    if(level===null && !audioSent)return;
    audioSent=level!==null;
    window.ContinuumScene?.setAudioLevel?.(level);
  }
  /** Turns the avatar to the element that really shows new content, as operations.js does. */
  function lookAt(target) {
    if(!target?.isConnected || document.visibilityState!=='visible' || presenceOf(snapshot)?.calm)return;
    lastGlanceAt=performance.now();
    target.dispatchEvent(new CustomEvent('continuum:gaze',{bubbles:true,detail:{target}}));
  }
  /**
   * A glance for events that can follow each other quickly (a dispatched input, the answer being written): at most
   * one per interval, and rarer while the device computes. It is one eased turn of the head inside the avatar's
   * frame loop, which runs anyway at the governed rate – no timer, no extra frame and nothing continuous.
   */
  function glance(target) {
    const wait=document.body.dataset.nativeCompute==='local'?GLANCE_LOCAL_MS:GLANCE_MS;
    if(performance.now()-lastGlanceAt<wait)return false;
    lookAt(target);return true;
  }
  /** Faces the owner: straight ahead, ending any gaze at a work window. */
  function faceOwner() {
    if(document.visibilityState!=='visible')return;
    window.dispatchEvent(new CustomEvent('continuum:gaze',{detail:{x:0,y:0,durationMs:2200}}));
  }
  function ownerTyping() {
    if(!$('#command-input')?.value) {typing=false;return;}
    if(typing)return;
    typing=true;faceOwner();
  }
  function reducedMotion() {
    return typeof window.matchMedia==='function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
  function renderEnergy(state) {
    const energy=own(presences,state.presence)?energies[state.presence]:sceneEnergies[sceneOf(state)];
    if(document.body.dataset.energy!==energy)document.body.dataset.energy=energy;
    // The original orb runs its own per-mode motion; a reduced tier, the owner's light-motion
    // switch and the system preference return it to its resting frame.
    const orb=$('.orb-zone');
    if(!orb)return;
    const motion=state.quality==='REDUCED' || state.appearance?.lightMotion===false || reducedMotion() ||
      computesLocally(state)?'fixed':'live';
    if(orb.dataset.orbMotion!==motion)orb.dataset.orbMotion=motion;
  }
  /** The measured speech level also drives the orb's corona and the room haze; null removes it. */
  function speechLight(level) {
    for(const layer of [$('.native-orb-light'),$('.native-room-light'),$('.native-bust-back')]) {
      if(!layer)continue;
      if(level===null)layer.style.removeProperty('--native-speech');
      else layer.style.setProperty('--native-speech',String(Math.round(level*100)/100));
    }
  }
  function setWorkPhase(phase) {
    workPhase=phase;
    const body=document.body, surface=phase==='idle' || phase==='activation'?null:workSurface;
    if(body.dataset.workPhase!==phase)body.dataset.workPhase=phase;
    if(!surface)delete body.dataset.workSurface;
    else if(body.dataset.workSurface!==surface)body.dataset.workSurface=surface;
  }
  /** The work window that really is open: device image, document, file list or this turn's sources. */
  function openSurface() {
    return computerOpen()?'computer':currentDocument?'document':filesOpen()?'files':workSources?'sources':null;
  }
  function surfaceElement() {
    const surface=openSurface();
    return surface==='computer'?$('#native-computer-panel'):surface==='document'?$('#native-document-panel'):
      surface==='files'?$('#native-files-panel'):null;
  }
  /** The return ends with the stage's own return animation and never while the reply is still spoken. */
  function finishReturn() {
    if(workPhase!=='settling' || !workReturned)return;
    if(sceneOf(snapshot)==='speaking' || currentSpeech)return;
    workSurface=null;setWorkPhase('idle');
  }
  function settleWork() {
    const token=++workToken;
    workTurn=false;workAwaitingReply=false;workReturned=false;
    // The file window belongs to the finished turn; an opened document stays with the owner.
    if(filesOpen()) {filesDismissed=currentFiles.revision;renderFiles();renderFeed();}
    setWorkPhase('settling');
    const returning=($('.native-orb-light')?.getAnimations?.() ?? [])
      .filter(item=>item.animationName==='native-work-return');
    const done=()=>{if(token===workToken) {workReturned=true;finishReturn();}};
    if(returning.length)Promise.allSettled(returning.map(item=>item.finished)).then(done);
    else done();
  }
  /**
   * Visible sequence of one real turn. It moves only when Android reported something:
   * a snapshot (busy, activity), a work window (files, document, sources, device image)
   * or the reply. No step is assumed and no timer stands in for one.
   */
  function advanceWork(freshFiles=false) {
    if(!snapshot)return;
    const running=snapshot.busy===true || currentActivity?.active===true;
    if(running && !workTurn) {
      workToken++;workTurn=true;workReplied=false;workAwaitingReply=false;workSources=false;workSurface=null;
      // A list from an earlier turn leaves with it; this turn's own answer has a new revision.
      if(filesOpen() && !freshFiles) {filesDismissed=currentFiles.revision;renderFiles();renderFeed();}
      // So does the record of a live window that ended before this turn.
      if(computerRecord) {computerRecord=null;renderFeed();}
      setWorkPhase('activation');
      return;
    }
    if(workTurn && running) {
      const surface=openSurface();
      if(!surface) {
        workSurface=null;
        if(workPhase!=='activation') {workToken++;setWorkPhase('activation');}
        return;
      }
      if(surface===workSurface)return;
      const token=++workToken;
      workSurface=surface;setWorkPhase('opening');
      const project=()=>{if(token===workToken && workPhase==='opening')setWorkPhase('projected');};
      // The window's flight is the original controller's; the content counts as projected when it lands.
      const motion=window.ContinuumWorkspaceMotion;
      if(motion?.isAnimating?.())motion.whenSettled().then(project);
      else project();
      return;
    }
    if(workTurn) {
      // Android reports the turn as finished; the stage returns once its reply is on screen.
      if(workReplied)settleWork();
      else {workToken++;workTurn=false;workAwaitingReply=true;workSurface=null;setWorkPhase('idle');}
      return;
    }
    finishReturn();
  }
  function replyDelivered() {
    if(workTurn)workReplied=true;
    else if(workAwaitingReply)settleWork();
    faceOwner();
  }
  /**
   * The gate for every added effect, set before anything of a snapshot is rendered, so that a mark for motion is
   * decided by the state it belongs to (see motionTier) and never by the one before it.
   */
  function renderMotionGate(state) {
    const moved=motionTier();
    const quality=own(qualities,state.quality)?state.quality:null;
    const effects=computesLocally(state) || (state.busy===true && state.cloudBrain!==true)?'rest':
      quality===null?'full':effectTiers[quality];
    if(document.body.dataset.nativeEffects!==effects)document.body.dataset.nativeEffects=effects;
    const motion=state.appearance?.lightMotion===false?'off':'on';
    if(document.body.dataset.nativeLightMotion!==motion)document.body.dataset.nativeLightMotion=motion;
    // Motion ends here (the device starts to compute, the tier drops, the owner switched it off): what was still
    // arriving or leaving is settled now, so that no mark is left to start a pass when motion returns.
    if(moved && !motionTier())settleMarks();
  }
  function renderPresence(state) {
    const key=own(presences,state.presence)?state.presence:null;
    const name=key?key.toLowerCase().replace(/_/g,'-'):null;
    if(name===null)delete document.body.dataset.presence;
    else if(document.body.dataset.presence!==name)document.body.dataset.presence=name;
    if(key!==lastPresence) {
      lastPresence=key;
      // A question, a blocker or the owner speaking concerns the owner: end any work gaze and face forward.
      if(key && (presences[key].calm || key==='LISTENING'))faceOwner();
    }
    const quality=own(qualities,state.quality)?state.quality:null;
    if(quality===null)delete document.body.dataset.nativeQuality;
    else if(document.body.dataset.nativeQuality!==quality.toLowerCase())
      document.body.dataset.nativeQuality=quality.toLowerCase();
    const local=computesLocally(state);
    if(local) {if(document.body.dataset.nativeCompute!=='local')document.body.dataset.nativeCompute='local';}
    else if('nativeCompute' in document.body.dataset)delete document.body.dataset.nativeCompute;
    // The added window effects are opt-in per tier and absent while the device computes (see native-layer.css).
    // They rest more readily than the Code68 stage does: also while a local turn is still running under another
    // presence – its answer being spoken, a question to the owner – because the model may still be computing.
    renderMotionGate(state);
    // Without a tier the original preview setting ("hoch") stays in force; local computing always gets "low".
    const sceneQuality=local?'low':quality===null?null:qualities[quality];
    if(sceneQuality!==lastQuality) {
      lastQuality=sceneQuality;
      window.ContinuumScene?.setQuality?.(sceneQuality===null?'high':sceneQuality);
    }
    const busy=key===null ? state.busy===true && state.speaking!==true && state.listening!==true : own(computing,key);
    const tier=quality===null?'FULL':quality;
    const frames=local?localComputeFrames[tier]:frameLimits[tier][busy?1:0];
    if(frameGovernor.limit()!==null && frameGovernor.limit()!==frames)frameGovernor.set(frames);
    if(frameGovernor.limit()!==null && document.body.dataset.frameLimit!==String(frames))
      document.body.dataset.frameLimit=String(frames);
  }
  function validScope(data) {
    return data && typeof data.sessionId==='string' && typeof data.pageId==='string' &&
      typeof data.viewId==='string' && chatId.test(data.sessionId) && chatId.test(data.pageId) && chatId.test(data.viewId) &&
      Number.isSafeInteger(data.viewRevision) && data.viewRevision>=0;
  }
  function scope() {return {sessionId:session,pageId:page,viewId:view,viewRevision};}
  function currentScope(data) {
    return validScope(data) && data.sessionId===session && data.pageId===page &&
      data.viewId===view && data.viewRevision===viewRevision;
  }
  function draftKey(text,start,end) {return JSON.stringify([text,start,end]);}
  function editorDraft() {
    const field=$('#command-input');
    if(!field)return null;
    const value=field.value;
    const start=field.selectionStart ?? value.length, end=field.selectionEnd ?? start;
    const key=draftKey(value,start,end);
    if(key!==lastDraft) {
      if(clientEditRevision>=Number.MAX_SAFE_INTEGER)return null;
      clientEditRevision++;lastDraft=key;dirtyDraft=true;
    }
    if(value.length>MAX_DRAFT || new TextEncoder().encode(value).length>MAX_DRAFT) {
      notice('Der Entwurf ist zu groß für die Übergabe. Bitte kürze ihn vor dem Ansichtswechsel.');
      return null;
    }
    return {draftText:value,selectionStart:start,selectionEnd:end,clientEditRevision};
  }
  function send(kind,value,extra = {}) {
    if (kind === 'route' && !routes.has(value)) return false;
    if(!validScope(scope())) {
      notice('Die Android-Verbindung startet. Dein Entwurf bleibt erhalten.');return false;
    }
    const command=['route','select-chat','command','open-source','document-draft'].includes(kind);
    const fields={...scope(),...extra};
    if(command) {
      if(document.visibilityState!=='visible')return false;
      const draft=editorDraft();
      // Stop remains available even when a draft cannot be transported. Android
      // still validates the exact scope and never treats omission as an empty edit.
      if(!draft && !(kind==='route' && value==='stop'))return false;
      if(draft) {Object.assign(fields,draft);sentEditRevision=clientEditRevision;}
    }
    const query = new URLSearchParams({value,...fields});
    window.location.assign('continuum-native://action/' + kind +
      '?' + query.toString());
    return true;
  }
  function notice(text) {
    const target = $('#toast');
    if (target) {target.textContent=text;target.classList.add('show');}
  }
  function retainTransitionDraft(cause) {
    const request=pendingCommand, field=$('#command-input');
    if(!request || !field || !cause || cause.commandId!==request.id ||
      cause.fromPageId!==request.pageId || cause.fromViewId!==request.viewId ||
      cause.fromViewRevision!==request.viewRevision || cause.fromSessionId!==request.sessionId ||
      !Number.isSafeInteger(cause.ackEditRevision) || cause.ackEditRevision<request.clientEditRevision ||
      clientEditRevision<=cause.ackEditRevision || !field.value || field.value===request.draft)return;
    const text=field.value, start=field.selectionStart, end=field.selectionEnd;
    // This is a visible, ephemeral DOM recovery, never an edit for the destination
    // view. Keeping each unresolved note also preserves successive transition races.
    const note=document.createElement('aside');note.className='native-draft-recovery';
    const title=document.createElement('p');
    title.setAttribute('role','status');title.setAttribute('aria-live','polite');title.setAttribute('aria-atomic','true');
    title.textContent='Nicht gesendeter Entwurf · '+String(snapshot?.viewLabel||'Vorheriger Dialog').slice(0,100);
    const details=document.createElement('details'), summary=document.createElement('summary');
    summary.textContent='Entwurf ansehen';
    const content=document.createElement('pre');content.textContent=text;details.append(summary,content);
    const buttons=document.createElement('div');buttons.className='action-row';
    const dismiss=()=>{
      note.remove();document.body.classList.toggle('native-has-draft-recovery',!!$('.native-draft-recovery'));
    };
    for(const [action,label] of [['restore','Zur Eingabe hinzufügen'],['discard','Verwerfen']]) {
      const button=document.createElement('button');button.type='button';button.className='secondary-button';
      button.dataset.nativeRecovery=action;button.textContent=label;
      button.addEventListener('click',()=>{
        if(action==='discard'){dismiss();return;}
        if(!validScope(scope()) || document.visibilityState!=='visible')return;
        const current=$('#command-input');if(!current)return;
        const prefix=current.value && current.value!==text?current.value+'\n\n':'';
        current.value=prefix+text;
        current.setSelectionRange(prefix.length+start,prefix.length+end);
        dirtyDraft=true;fitComposer();syncDraft();dismiss();
        current.focus({preventScroll:true});
      });
      buttons.appendChild(button);
    }
    note.append(title,details,buttons);$('#command-form')?.before(note);
    document.body.classList.add('native-has-draft-recovery');
  }
  function labelWithIcons(node,label) {
    if(!node)return;
    const texts=[...node.childNodes].filter(child=>child.nodeType===3 && child.textContent.trim());
    if(texts.length) {
      if(texts[0].textContent.trim()!==label)texts[0].textContent=' '+label+' ';
      texts.slice(1).forEach(child=>{child.textContent='';});
    } else node.appendChild(document.createTextNode(' '+label));
  }
  function mountSettingsControls() {
    document.body.classList.add('native-hologram');
    const settings=$('.header-tools>[data-action="settings"]');
    if(settings) {
      settings.classList.add('native-settings-entry');
      settings.setAttribute('aria-label','Alle Einstellungen öffnen');
      settings.setAttribute('title','Alle Einstellungen öffnen');
      labelWithIcons(settings,'Einstellungen');
    }
    const nav=$('#main-nav'), entry=nav?.querySelector('[data-section="settings"]');
    if(entry && nav.children[1]!==entry)nav.insertBefore(entry,nav.children[1]||null);
    if(!$('.native-settings-shortcuts')) {
      const row=document.createElement('nav');
      row.className='native-settings-shortcuts';row.setAttribute('aria-label','Schnelleinstellungen');
      const icons={voice:'<rect x="9" y="3" width="6" height="12" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8"/>',
        model:'<rect x="6" y="6" width="12" height="12" rx="2"/><path d="M9 1v5m6-5v5M9 18v5m6-5v5M1 9h5m-5 6h5m12-6h5m-5 6h5"/>',
        internet:'<circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="4" ry="9"/><path d="M3 12h18"/>',
        appearance:'<path d="m12 3 9 5v8l-9 5-9-5V8zm0 0v18M3 8l9 5 9-5"/>'};
      for(const [kind,label] of [['voice','Stimme'],['model','KI-Modell'],['internet','Internet'],['appearance','Hologramm']]) {
        const button=document.createElement('button');button.type='button';
        button.className='native-setting-shortcut';button.dataset.action='settings-'+kind;
        button.setAttribute('aria-label',label+' einstellen');
        button.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true">'+icons[kind]+'</svg><span>'+label+'</span>';
        row.appendChild(button);
      }
      $('.mobile-side-controls')?.after(row);
    }
    if(!$('.native-holo-field')) {
      const field=document.createElement('div');field.className='native-holo-field';
      field.setAttribute('aria-hidden','true');$('.presence-core')?.prepend(field);
    }
    if(!$('.native-projection-base')) {
      const base=document.createElement('div');base.className='native-projection-base';
      base.setAttribute('aria-hidden','true');
      base.innerHTML='<i></i><i></i><i></i><span></span>';
      $('.presence-core')?.prepend(base);
    }
    const pill=$('.core-status');
    if(pill && !pill.hasAttribute('role')) {
      pill.setAttribute('role','status');pill.setAttribute('aria-live','polite');pill.setAttribute('aria-atomic','true');
    }
    mountRoom();
    if(!$('.native-stop-button')) {
      const stop=document.createElement('button');stop.type='button';stop.className='native-stop-button';
      stop.dataset.action='cancel';stop.setAttribute('aria-label','Auftrag stoppen');
      stop.title='Auftrag stoppen';stop.textContent='■';stop.hidden=true;
      $('.send-button')?.before(stop);
    }
    renderNativeInstruments();
  }
  /**
   * One room, unchanged arrangement: the original orb stays behind the original avatar and no
   * element moves. Two light-only layers are added (no layout box of their own in the flow):
   * the room haze behind the centre and the status tiles, and the orb's corona between orb and avatar.
   */
  function mountRoom() {
    const core=$('.presence-core'), orb=$('.orb-zone');
    if(!core)return;
    if(!document.body.dataset.workPhase)document.body.dataset.workPhase='idle';
    // The page declares the orb as a resting picture ("fixed"); its own script animates every
    // mode once the anchor says "live". Until Android names a preference, follow the system one.
    if(orb && !snapshot && !reducedMotion() && orb.dataset.orbMotion!=='live')orb.dataset.orbMotion='live';
    if(!$('.native-room-light')) {
      const room=document.createElement('div');room.className='native-room-light';
      room.setAttribute('aria-hidden','true');core.prepend(room);
    }
    if(!$('.native-orb-light')) {
      const light=document.createElement('div');light.className='native-orb-light';
      light.setAttribute('aria-hidden','true');light.innerHTML='<i></i><i></i><b></b>';
      core.prepend(light);
    }
    // Code70 · depth. Two still layers without a box in the flow: the avatar's separation from the orb behind it
    // (between the two, under the corona) and the glass frame on the work window. Neither moves, ever.
    if(!$('.native-depth-veil')) {
      const veil=document.createElement('div');veil.className='native-depth-veil';
      veil.setAttribute('aria-hidden','true');core.prepend(veil);
    }
    const shell=$('.workspace-shell');
    if(shell && !shell.querySelector(':scope>.native-window-frame')) {
      const frame=document.createElement('div');frame.className='native-window-frame';
      frame.setAttribute('aria-hidden','true');shell.appendChild(frame);
    }
    mountBust();
  }
  // ───────── Code73 · the bust in the room ─────────
  // The original avatar draws its model with an orthographic camera of fixed height (BUST.halfHeight around
  // BUST.centerY) that widens when its frame is narrower than the camera (see avatar3d.js, Qd: "contain"); the
  // verified PNG it falls back to is that same picture. So every height of the model has one place in the frame,
  // and the dissolve is laid where the model really ends (its flat lower plane at y = 0), not at a guessed line.
  const BUST = Object.freeze({centerY:.215, halfHeight:.225, aspect:.6/.45, neck:.15, chest:.1, cut:0, shoulder:.258,
    head:.345, headHalf:.085});
  /** Two still light layers around the original avatar: behind it (platform, halo) and in front (threads, motes). */
  function mountBust() {
    const zone=$('.avatar-zone'), crop=zone?.querySelector('.avatar-crop');
    if(!crop || zone.querySelector(':scope>.native-bust'))return;
    const layer=(name,parts)=>{
      const node=document.createElement('div');node.className='native-bust '+name;node.setAttribute('aria-hidden','true');
      for(const part of parts) {const item=document.createElement('i');item.className=part;node.appendChild(item);}
      return node;
    };
    crop.before(layer('native-bust-back',['native-bust-halo','native-bust-halo native-bust-inner','native-bust-ring',
      'native-bust-ring native-bust-inner']));
    crop.after(layer('native-bust-front',['native-bust-lines','native-bust-motes','native-bust-ring',
      'native-bust-ring native-bust-inner']));
    placeBust();
  }
  /** Measures where neck, chest, shoulders and the model's lower plane stand in the avatar's frame. */
  function placeBust() {
    const zone=$('.avatar-zone'), crop=zone?.querySelector('.avatar-crop');
    if(!crop)return;
    const width=crop.clientWidth, height=crop.clientHeight;
    if(!(width>0 && height>0))return;
    const aspect=width/height, half=BUST.halfHeight*Math.max(1,BUST.aspect/aspect);
    const y=value=>((BUST.centerY+half-value)/(2*half)*100).toFixed(2)+'%';
    const x=value=>(value/(2*half*aspect)*100).toFixed(2)+'%';
    const values={'--bust-neck':y(BUST.neck),'--bust-chest':y(BUST.chest),'--bust-cut':y(BUST.cut),
      '--bust-shoulder':x(BUST.shoulder),'--bust-head':y(BUST.head),'--bust-headw':x(BUST.headHalf)};
    for(const [name,value] of Object.entries(values))
      if(zone.style.getPropertyValue(name)!==value)zone.style.setProperty(name,value);
    // The light layers cover exactly the avatar's frame inside its zone.
    for(const layer of zone.querySelectorAll(':scope>.native-bust')) {
      const box={left:crop.offsetLeft+'px',top:crop.offsetTop+'px',width:width+'px',height:height+'px',right:'auto',bottom:'auto'};
      for(const [name,value] of Object.entries(box))if(layer.style[name]!==value)layer.style[name]=value;
    }
  }
  window.addEventListener('resize',()=>placeBust());
  window.addEventListener('continuum:avatarchange',()=>placeBust());
  function renderFiles() {
    const workspace=$('#workspace');
    if(!workspace)return;
    const shown=filesOpen() && !currentDocument && !computerOpen();
    workspace.classList.toggle('native-files-active',shown);
    let panel=workspace.querySelector('#native-files-panel');
    if(!filesOpen()) {panel?.remove();return;}
    const files=currentFiles;
    if(!panel) {
      panel=document.createElement('section');panel.id='native-files-panel';
      panel.className='projected-computer native-files-panel';
      const head=document.createElement('div');head.className='native-files-heading';
      const kicker=document.createElement('small');kicker.className='native-files-kicker';
      const title=document.createElement('h2');title.className='native-files-title';
      const count=document.createElement('p');count.className='native-files-count';count.setAttribute('role','status');
      head.append(kicker,title,count);
      const list=document.createElement('ol');list.className='native-files-list';
      const note=document.createElement('p');note.className='native-files-note';
      panel.append(head,list,note);workspace.appendChild(panel);
    }
    if(feedVersions.get(panel)!==files.revision) {
      // The same window shows the hits of the installed-app search: the labels Android matched, nothing started.
      const apps=files.kind==='APPS';
      panel.dataset.kind=files.kind.toLowerCase();
      panel.setAttribute('aria-label',apps?'App-Fenster mit der Antwort der Android-App-Suche':
        'Dateifenster mit der Antwort des Android-Dateiwerkzeugs');
      panel.querySelector('.native-files-note').textContent=apps?
        'Nur App-Namen aus der Antwort des Android-Werkzeugs. Keine App wurde gestartet.':
        'Nur Dateinamen aus der Antwort des Android-Werkzeugs. Kein Inhalt wurde geöffnet.';
      panel.querySelector('.native-files-kicker').textContent=fileKinds[files.kind]+' · ANDROID-WERKZEUG';
      panel.querySelector('.native-files-title').textContent=files.label?
        (files.kind==='FOLDER'?files.label:'„'+files.label+'“'):(apps?'App-Suche':files.kind==='SEARCH'?'Dateisuche':'Ordner');
      const unit=apps?(files.total===1?'App gefunden':'Apps gefunden'):
        files.kind==='SEARCH'?(files.total===1?'Datei gefunden':'Dateien gefunden'):
        (files.total===1?'Eintrag im Ordner':'Einträge im Ordner');
      panel.querySelector('.native-files-count').textContent=
        (files.total===0?(apps?'Keine App gefunden':files.kind==='SEARCH'?'Keine Datei gefunden':'Keine Einträge im Ordner'):
          (files.more?'Mindestens ':'')+files.total+' '+unit)+
        (files.total>files.names.length && files.names.length?' · '+files.names.length+' angezeigt':'')+
        (files.more?' · Suchgrenze erreicht':'');
      panel.querySelector('.native-files-list').replaceChildren(...files.names.map((name,index)=>{
        const item=document.createElement('li');
        const glyph=document.createElement('span');glyph.className='native-file-glyph';glyph.setAttribute('aria-hidden','true');
        const label=document.createElement('span');label.className='native-file-name';label.textContent=name;
        item.append(glyph,label);
        // Each hit of a new answer flies in, one after the other.
        enter(item,index+1);
        const dot=name.lastIndexOf('.'), kind=!apps && dot>0?name.slice(dot+1):'';
        if(/^[A-Za-z0-9]{1,5}$/.test(kind)) {
          const chip=document.createElement('span');chip.className='native-file-kind';chip.textContent=kind.toUpperCase();
          item.appendChild(chip);
        }
        return item;
      }));
      feedVersions.set(panel,files.revision);
    }
    // Android opened exactly this listed file: mark it instead of guessing a choice.
    for(const item of panel.querySelectorAll('.native-files-list li')) {
      const chosen=String(currentDocument?.name===item.querySelector('.native-file-name').textContent);
      if(item.dataset.selected!==chosen)item.dataset.selected=chosen;
    }
    if(shown)workspace.classList.remove('native-work-active');
    renderWindowKind();
  }
  function dismissFiles() {
    if(!filesOpen())return;
    filesDismissed=currentFiles.revision;renderFiles();renderFeed();advanceWork();
  }
  function renderNativeInstruments() {
    window.ContinuumPresenceInstruments?.dispose();
    const presence=presenceOf(snapshot), mode=sceneOf(snapshot), activity=snapshot?currentActivity:null;
    const labels={idle:'Bereit',listening:'Ich höre zu',working:'In Bearbeitung',speaking:'Ich spreche'};
    const details={idle:'Dein nächster Auftrag beginnt hier.',listening:'Deine Sprache wird aufgenommen.',
      working:'Der aktuelle Auftrag wird verarbeitet.',speaking:'Die Sprachausgabe ist aktiv.'};
    const label=activity?.title || presence?.label || labels[mode];
    const copy=String(activity?.detail || snapshot?.status || presence?.detail || details[mode]).slice(0,180);
    const panel=(selector,kind,title)=>{
      const host=$(selector);if(!host)return null;
      let section=host.querySelector('.native-instrument');
      if(!section) {
        section=document.createElement('section');section.className='native-instrument native-'+kind;
        const head=document.createElement('h3');head.textContent=title;
        const art=document.createElement('div');art.className='native-instrument-art';art.setAttribute('aria-hidden','true');
        art.innerHTML='<svg viewBox="0 0 200 90"><path d="M10 45h35l20-25h70l20 25h35M10 45h35l20 25h70l20-25"/><circle cx="100" cy="45" r="28"/><circle cx="100" cy="45" r="18"/><path d="M85 45h30M100 30v30"/></svg>';
        const value=document.createElement('strong'), copy=document.createElement('p'), links=document.createElement('div');
        links.className='native-instrument-links';section.append(head,art,value,copy,links);
        host.replaceChildren(section);
      }
      return section;
    };
    const setText=(node,text)=>{if(node && node.textContent!==text)node.textContent=text;};
    const left=panel('[data-presence-left]','activity','AKTIVITÄT');
    if(left) {
      left.dataset.mode=mode;
      setText(left.querySelector('h3'),activity?'AKTIVITÄT · '+activityKinds[activity.kind]:'AKTIVITÄT');
      setText(left.querySelector('strong'),snapshot?label:'Verbindung startet');
      setText(left.querySelector('p'),snapshot?copy:'Warte auf den Android-Status.');
      let steps=left.querySelector('.native-activity-steps');
      if(!activity) {
        delete left.dataset.activityKind;delete left.dataset.activityActive;steps?.remove();stepStatesShown.clear();
      } else {
        left.dataset.activityKind=activity.kind.toLowerCase();
        left.dataset.activityActive=String(activity.active);
        if(!activity.steps.length) {steps?.remove();stepStatesShown.clear();}
        else {
          if(!steps) {
            steps=document.createElement('ol');steps.className='native-activity-steps';
            steps.setAttribute('aria-label','Arbeitsschritte');left.querySelector('p').after(steps);
          }
          // One real revision is rendered once; periodic snapshots keep its nodes.
          if(feedVersions.get(steps)!==activity.revision) {
            steps.replaceChildren(...activity.steps.map(step=>{
              const item=document.createElement('li');item.dataset.state=step.state.toLowerCase();
              // The step Android just moved to another state is marked for this one rendering.
              const before=stepStatesShown.get(step.label);
              if(before!==undefined && before!==step.state)item.dataset.changed='true';
              const name=document.createElement('span');name.className='native-step-label';
              name.textContent=step.label;
              const state=document.createElement('span');state.className='native-step-state';
              state.textContent=stepStates[step.state];
              item.append(name,state);return item;
            }));
            feedVersions.set(steps,activity.revision);
            stepStatesShown.clear();
            for(const step of activity.steps)stepStatesShown.set(step.label,step.state);
          }
        }
      }
    }
    const items=[...new Map([...sourceSets.values()].flatMap(set=>set.items).map(item=>[item.id,item])).values()];
    const right=panel('[data-presence-right]','evidence','QUELLEN');
    if(right) {
      setText(right.querySelector('strong'),items.length?items.length+' verknüpft':'Noch keine Quellen');
      setText(right.querySelector('p'),items.length?'Quellen der aktuellen Unterhaltung.':'Quellen erscheinen nach einer Recherche.');
      const links=right.querySelector('.native-instrument-links');
      const key=JSON.stringify(items.slice(-3));
      if(feedVersions.get(links)!==key) {
        links.replaceChildren(...items.slice(-3).map(item=>{
          const button=document.createElement('button');button.type='button';
          button.className='native-source-link';button.dataset.nativeSource=item.id;
          button.textContent=item.title;button.title=item.url;return button;
        }));
        feedVersions.set(links,key);
      }
    }
    const rail=$('#instrument-content');
    if(rail) {
      const signature=JSON.stringify([mode,snapshot?.status,items,presence?.label,activity?.revision]);
      if(feedVersions.get(rail)!==signature || !rail.querySelector('.native-instrument-summary')) {
        const summary=document.createElement('p');summary.className='native-instrument-summary';
        summary.textContent=snapshot?label+' · '+copy:'Warte auf Android.';
        const note=document.createElement('p');note.textContent=items.length?'Quellen dieser Unterhaltung':'Noch keine Quellen in dieser Unterhaltung.';
        rail.replaceChildren(summary,note,...(items.length?[sourceCard({items:items.slice(-8)})]:[]));
        feedVersions.set(rail,signature);
      }
    }
    setText($('.presence-edition'),'CONTINUUM / LIVE');
    setText($('#state-eyebrow'),'DEIN PERSÖNLICHER KI-RAUM');
    setText($('.instrument-count'),String(items.length));
    setText($('#scene-number'),'');
    setText($('.command-field label'),'DEIN AUFTRAG');
    const running=snapshot?.busy===true || snapshot?.commandPending===true;
    const stop=$('.native-stop-button'), submit=$('.send-button');
    if(stop)stop.hidden=!running;
    if(submit)submit.hidden=running;
  }
  function renderAppearance(value) {
    const neon=Number.isFinite(value?.neon)?Math.max(0,Math.min(100,value.neon)):70;
    const level=String(neon/100);
    if(document.body.style.getPropertyValue('--native-neon')!==level)
      document.body.style.setProperty('--native-neon',level);
    // The original scene keeps its preview default (.78) until Android names a level.
    // 0 → the scene's floor, 70 (Android default) → that same .78, 100 → the full 1.
    if(Number.isFinite(value?.neon) || lastIntensity!==null) {
      const intensity=Math.round((neon<=70?.35+neon/70*.43:.78+(neon-70)/30*.22)*1000)/1000;
      if(intensity!==lastIntensity) {lastIntensity=intensity;window.ContinuumScene?.setIntensity?.(intensity);}
    }
    const motion=value?.lightMotion===false?'off':'on';
    if(document.body.dataset.nativeLightMotion!==motion)document.body.dataset.nativeLightMotion=motion;
  }
  function fitComposer() {
    const field=$('#command-input');
    if(!field)return;
    const style=window.getComputedStyle(field);
    const min=parseFloat(style.minHeight)||29, max=parseFloat(style.maxHeight)||116;
    field.style.height='0px';
    field.style.height=Math.max(min,Math.min(max,field.scrollHeight))+'px';
    const height=$('#command-form')?.getBoundingClientRect().height;
    if(height>0)document.body.style.setProperty('--native-composer-height',height+'px');
  }
  function commandLimits() {
    return {
      chars: Number.isInteger(snapshot?.maxCommandChars) && snapshot.maxCommandChars>0 ?
        Math.min(DEFAULT_COMMAND_LIMIT,snapshot.maxCommandChars) : DEFAULT_COMMAND_LIMIT,
      bytes: Number.isInteger(snapshot?.maxCommandBytes) && snapshot.maxCommandBytes>0 ?
        Math.min(DEFAULT_COMMAND_LIMIT,snapshot.maxCommandBytes) : DEFAULT_COMMAND_LIMIT
    };
  }
  function inputError(value) {
    const limit = commandLimits();
    if (value.length>limit.chars || new TextEncoder().encode(value).length>limit.bytes)
      return 'Der Auftrag ist zu lang. Dein Entwurf bleibt erhalten.';
    if (/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f-\u009f]/.test(value))
      return 'Der Text enthält ein nicht unterstütztes Steuerzeichen. Dein Entwurf bleibt erhalten.';
    return '';
  }
  function syncDraft() {
    if(!validScope(scope()))return;
    const draft=editorDraft();
    if(!draft || sentEditRevision===draft.clientEditRevision)return;
    sentEditRevision=draft.clientEditRevision;
    send('draft',draft.draftText,{clientEditRevision:draft.clientEditRevision,
      selectionStart:draft.selectionStart,selectionEnd:draft.selectionEnd});
  }
  function commandResult(result) {
    if (!currentScope(result) || !pendingCommand || result.id!==pendingCommand.id ||
      result.clientEditRevision!==pendingCommand.clientEditRevision) return;
    const request=pendingCommand;pendingCommand=null;
    if (result.accepted===true) {
      const field=$('#command-input');
      if (field && field.value===request.draft && clientEditRevision===request.clientEditRevision) {
        field.value='';dirtyDraft=false;fitComposer();typing=false;
        lastDraft=draftKey('',0,0);sentEditRevision=clientEditRevision;
      }
      clearDocument();
      $('#toast')?.classList.remove('show');
      return;
    }
    const reasons={busy:'Ein Auftrag läuft noch. Dein Entwurf bleibt erhalten.',
      'stale-session':'Das Gespräch wurde gewechselt. Dein Entwurf wurde nicht gesendet.',
      'stale-context':'Die Ansicht wurde gewechselt. Dein Entwurf wurde nicht gesendet.',
      'stale-edit':'Der Entwurf wurde inzwischen geändert. Bitte prüfe ihn vor dem Senden.',
      'too-long':'Der Auftrag ist zu lang. Dein Entwurf bleibt erhalten.',
      'invalid-input':'Dieser Text konnte nicht übernommen werden. Dein Entwurf bleibt erhalten.',
      unavailable:'Die Eingabe ist noch nicht bereit. Dein Entwurf bleibt erhalten.'};
    notice(reasons[result.reason] || 'Der Auftrag wurde nicht übernommen. Dein Entwurf bleibt erhalten.');
  }
  function chatRow(row) {
    const button = document.createElement('button');
    button.className = 'chat-entry' + (row.id === session ? ' active' : '');
    button.dataset.nativeChat = row.id;
    button.setAttribute('aria-current', row.id === session ? 'page' : 'false');
    const glyph = document.createElement('span');
    glyph.className = 'native-history-glyph';
    glyph.textContent = '◈';
    const label = document.createElement('span');
    label.textContent = row.title || 'Gespräch';
    const sub = document.createElement('small');
    sub.textContent = 'Gespeicherter Android-Chat';
    label.appendChild(sub);
    button.append(glyph,label);
    return button;
  }
  function renderChats() {
    const target = $('#chat-list');
    if (!target || !snapshot || rendering) return;
    const rows = (Array.isArray(snapshot.chats) ? snapshot.chats : []).filter(row =>
      row && chatId.test(row.id) && typeof row.title === 'string').slice(0,100);
    const current = [...target.querySelectorAll(':scope > button[data-native-chat]')];
    if (current.length === rows.length && target.children.length === rows.length &&
      current.every((node,i) => node.dataset.nativeChat === rows[i].id &&
        node.classList.contains('active') === (rows[i].id === session) &&
        node.querySelector('span:not(.native-history-glyph)')?.firstChild?.textContent === rows[i].title)) return;
    rendering = true;
    target.replaceChildren(...rows.map(chatRow));
    rendering = false;
  }
  const chats = $('#chat-list');
  if (chats) new MutationObserver(renderChats).observe(chats,{childList:true});

  function meter(selector,label,percent,note) {
    const host = $(selector);
    if (!host || !snapshot) return;
    let el = host.querySelector('.native-meter');
    if (!el) {el = document.createElement('div');el.className='native-meter';host.appendChild(el);}
    const actual = Number.isInteger(percent) && percent >= 0 && percent <= 100;
    el.replaceChildren();
    const caption=document.createElement('small');caption.textContent=label+' · LIVE';
    const figure=document.createElement('strong');figure.textContent=actual?percent+' %':'—';
    const track=document.createElement('div');track.className='native-meter-track';
    const value=document.createElement('span');value.style.width=(actual?percent:0)+'%';
    track.appendChild(value);
    const bottom=document.createElement('small');
    bottom.textContent=actual?note:'Messung nicht verfügbar';
    el.append(caption,figure,track,bottom);
  }
  function stopDemoTelemetry() {
    const telemetry=window.ContinuumTelemetry;
    if (!telemetry?.getStatus().active) return;
    for (const [channel,selector] of [['cpu','[data-telemetry-left]'],['ram','[data-telemetry-right]']]) {
      const host=$(selector), panel=host?.querySelector('.ct-panel');
      if (panel) nativeMeters.set(channel,{host,panel});
    }
    // The public lifecycle API also cancels its rAF sampler and long-task observer.
    telemetry.destroy();
    for (const [channel,{host,panel}] of nativeMeters) {
      host.querySelector('.native-meter')?.remove();
      panel.classList.add('native-telemetry-panel');
      panel.querySelector('.ct-mode-toggle')?.remove();
      panel.querySelector('h3').textContent=channel==='cpu'?'APP-CPU':'GERÄTE-RAM';
      panel.querySelector('strong').textContent='—';
      panel.querySelector('.ct-tag').textContent='ANDROID';
      panel.querySelector('.ct-caption span').textContent='Messung startet …';
      panel.querySelector('.ct-caption small').textContent='Noch kein Messwert';
      panel.querySelector('.ct-trace')?.setAttribute('d','');
      panel.querySelector('.ct-area')?.setAttribute('d','');
      panel.querySelector('.ct-endpoint')?.setAttribute('visibility','hidden');
      host.appendChild(panel);
    }
  }
  function validPercent(value) {
    return Number.isFinite(value) && value>=0 && value<=100 ? value : null;
  }
  function telemetryPath(channel) {
    const first=readings[0]?.at, last=readings.at(-1)?.at;
    let path='', previous=null;
    for (const item of readings) {
      const value=item[channel];
      if (value===null) {previous=null;continue;}
      const x=last===first?277:3+(item.at-first)/(last-first)*274;
      const y=42-value*.39;
      path+=(previous!==null && item.at-previous<=7500?' L':' M')+x.toFixed(2)+','+y.toFixed(2);
      previous=item.at;
    }
    return path.trim();
  }
  function renderMeters() {
    if (!snapshot) return;
    stopDemoTelemetry();
    const at=snapshot.telemetryAtElapsedMs;
    if (Number.isFinite(at) && at>=0 && at!==telemetryAt) {
      if (telemetryAt!==null && at<telemetryAt) readings.length=0;
      telemetryAt=at;
      readings.push({at,cpu:validPercent(snapshot.cpu),ram:validPercent(snapshot.ram)});
      if (readings.length>HISTORY) readings.shift();
    }
    for (const [channel,label,note] of [['cpu','APP-CPU','Anteil dieser App'],['ram','GERÄTE-RAM','Belegt im Gerät']]) {
      const view=nativeMeters.get(channel), percent=validPercent(snapshot[channel]);
      if (!view) {
        meter(channel==='cpu'?'[data-telemetry-left]':'[data-telemetry-right]',label,percent,note);
        continue;
      }
      const {panel}=view;
      const text=(selector,value)=>{const node=panel.querySelector(selector);if(node)node.textContent=value;};
      panel.dataset.mode='android';panel.dataset.available=String(percent!==null);
      panel.dataset.samples=String(readings.length);panel.dataset.paused='false';
      text('h3',label);text('.ct-tag','ANDROID');text('strong',percent===null?'—':String(percent));
      text('.ct-unit','%');text('.ct-caption span',note);
      text('.ct-caption small',percent===null?'Nicht verfügbar':'Gemessen');
      text('.ct-empty',readings.length?'':'Messverlauf startet …');
      text('.ct-meta>span:first-child','Messverlauf');text('.ct-detail','0–100 % · Gerätedaten');
      text('.ct-sr',label+': '+(percent===null?'Messung nicht verfügbar':percent+' Prozent')+'. '+note+'.');
      panel.title=label+' · '+note+' · gemessene Werte';
      panel.querySelector('.ct-trace')?.setAttribute('d',telemetryPath(channel));
      panel.querySelector('.ct-area')?.setAttribute('d','');
      panel.querySelector('.ct-endpoint')?.setAttribute('visibility','hidden');
      panel.querySelector('.ct-plot')?.setAttribute('aria-label',label+' · echte Messwerte; Lücken bleiben leer');
    }
  }
  function newerLivePacket(data, gate) {
    return currentScope(data) && typeof data.active==='boolean' &&
      Number.isSafeInteger(data.generation) && data.generation>0 &&
      Number.isSafeInteger(data.sequence) && data.sequence>=0 &&
      (data.generation>gate.generation || data.generation===gate.generation &&
        !gate.ended && data.sequence>gate.sequence);
  }
  function acceptLivePacket(data, gate) {
    gate.generation=data.generation;gate.sequence=data.sequence;gate.ended=!data.active;
  }
  function computerOpen() {return computerVisible && currentComputer?.active===true;}
  function invalidateFrame(frame) {
    if(!frame || frame.hiddenNotified)return;
    frame.hiddenNotified=true;
    const data=frame.data;
    if(currentScope(data))send('frame-hidden','',{generation:String(data.generation),sequence:String(data.sequence)});
  }
  function disposeFrame(frame) {
    if(!frame)return;
    clearTimeout(frame.expiry);
    for(const id of frame.paints)cancelAnimationFrame(id);
    frame.paints=[];frame.image.onload=null;frame.image.onerror=null;
    frame.image.removeAttribute('src');frame.image.remove();
  }
  function clearComputerFrames() {
    for(const frame of new Set([displayedFrame,pendingFrame])) {
      invalidateFrame(frame);disposeFrame(frame);
    }
    displayedFrame=null;pendingFrame=null;
  }
  function renderComputer() {
    const workspace=$('#workspace');
    if(!workspace)return;
    workspace.classList.toggle('native-computer-active',!!computerOpen());
    if(!currentComputer) {workspace.querySelector('#native-computer-panel')?.remove();return;}
    let panel=workspace.querySelector('#native-computer-panel');
    if(!panel) {
      panel=document.createElement('section');panel.id='native-computer-panel';
      panel.className='projected-computer native-computer-panel';
      panel.setAttribute('aria-label','Projizierter Computer mit aktuellem Android-Gerätebild');
      // The existing projected window and its original CSS own all framing and motion.
      panel.innerHTML='<div class="projection-lens" aria-hidden="true"></div><div class="op-console-head"><div><span class="op-kicker">PROJIZIERTER COMPUTER</span><h2>Dein Gerätebild.</h2></div><span class="op-mode-label real" role="status"></span></div><div class="op-controls"><button type="button" class="secondary-button" data-action="stop-computer">Computer stoppen</button></div><div class="op-browser-frame"><div class="op-browser-chrome"><span class="op-window-id">CONTINUUM · ANDROID</span></div><div class="op-browser-body native-computer-viewport"><div class="op-waiting"><h3>Warte auf ein aktuelles Bild.</h3><p class="native-computer-waiting"></p></div><div class="native-computer-screen" hidden></div></div><div class="op-browser-status"><span class="status-dot"></span><span class="native-computer-status" role="status"></span></div></div>';
      panel.appendChild(actionLog());
      workspace.appendChild(panel);
    }
    const fresh=!!displayedFrame && performance.now()<displayedFrame.expiresAt;
    // LIVE is said only while a decoded, unexpired frame of this source is on screen; everything else is named.
    const [state,label]=fresh?['live','LIVE']:currentComputer.status==='COVERED'?['covered','VERDECKT']:
      currentComputer.status==='PAUSED'?['paused','PAUSIERT']:
      currentComputer.shown?['stale','KEIN FRISCHES BILD']:['waiting','WARTET AUF ERSTES BILD'];
    const badge=panel.querySelector('.op-mode-label');
    if(badge.textContent!==label)badge.textContent=label;
    if(panel.dataset.monitorState!==state)panel.dataset.monitorState=state;
    const work=workOf(currentComputer);
    // The title bar names the real target: the app of the approved task and the package Android captures.
    const target=[work?.app,currentComputer.targetPackage].filter(Boolean).join(' · ') || 'CONTINUUM · ANDROID';
    const title=panel.querySelector('.op-window-id');
    if(title.textContent!==target)title.textContent=target;
    const reason=currentComputer.reason || 'Noch kein aktuelles Gerätebild verfügbar.';
    panel.querySelector('.native-computer-status').textContent=reason;
    panel.querySelector('.native-computer-waiting').textContent=reason;
    panel.querySelector('.op-waiting').hidden=fresh;
    const screen=panel.querySelector('.native-computer-screen');
    screen.hidden=!fresh;
    if(computerOpen()) {
      workspace.classList.remove('native-document-active','native-work-active');
      if(fresh && displayedFrame.image.parentElement!==screen)screen.prepend(displayedFrame.image);
    }
    renderMarker(screen,fresh?displayedFrame:null,work);
    renderActionLog(panel.querySelector('.native-computer-work'),work);
    nativeLabels();
  }
  /**
   * Where a dispatched input lies on the shown image. Android measured the target in the coordinates of the
   * target window; the frame shows the part `view` of that window. No frame geometry, another task, an older
   * frame than the one the input was dispatched on, or a target outside the shown part: no marker.
   */
  function markerPosition(action,frame,work) {
    if(!action || action.x===null || !frame?.view || !work)return null;
    if(action.frameSequence!==null && (frame.sourceSequence===null || frame.sourceSequence<action.frameSequence))return null;
    const [left,top,right,bottom]=frame.view;
    if(action.x<left || action.x>right || action.y<top || action.y>bottom)return null;
    return [(action.x-left)/(right-left),(action.y-top)/(bottom-top)];
  }
  function renderMarker(screen,frame,work) {
    const shown=screen.querySelector('.native-computer-marker');
    const action=marker && work?work.actions.find(row=>row.sequence===marker.sequence):null;
    const position=markerPosition(action,frame && workOf(frame.data)===work?frame:null,work);
    if(!position) {shown?.remove();return;}
    let dot=shown;
    // One element per input: its single ring plays once when it appears and never again.
    if(dot?.dataset.sequence!==String(action.sequence)) {
      dot?.remove();
      dot=document.createElement('i');dot.className='native-computer-marker';dot.setAttribute('aria-hidden','true');
      dot.dataset.sequence=String(action.sequence);dot.dataset.kind=action.kind.toLowerCase();
      screen.appendChild(dot);
    }
    if(dot.dataset.state!==action.state.toLowerCase())dot.dataset.state=action.state.toLowerCase();
    const at=(position[0]*100).toFixed(2)+'% '+(position[1]*100).toFixed(2)+'%';
    if(dot.dataset.at!==at) {
      dot.dataset.at=at;
      [dot.style.left,dot.style.top]=at.split(' ');
    }
  }
  /** The list of what the approved task really did. Text only; it is empty until Android reports an input. */
  function actionLog() {
    const host=document.createElement('div');host.className='native-computer-work';host.hidden=true;
    const stage=document.createElement('p');stage.className='native-computer-stage';stage.setAttribute('role','status');
    const title=document.createElement('small');title.className='native-computer-log-title';
    title.textContent='EINGABEN IN DEINEM FREIGEGEBENEN AUFTRAG';
    const list=document.createElement('ol');list.className='native-computer-log';
    list.setAttribute('aria-label','Ausgeführte Eingaben');
    host.append(stage,title,list);return host;
  }
  function renderActionLog(host,work) {
    if(!host)return;
    const actions=work?.actions ?? [];
    const line=work?[work.goal,work.stage?workStages[work.stage]:''].filter(Boolean).join(' · '):'';
    host.hidden=!line && !actions.length;
    if(host.hidden)return;
    const stage=host.querySelector('.native-computer-stage');
    if(stage.textContent!==line)stage.textContent=line;
    stage.hidden=!line;
    if(work.stage)stage.dataset.stage=work.stage.toLowerCase().replace(/_/g,'-');else delete stage.dataset.stage;
    host.querySelector('.native-computer-log-title').hidden=!actions.length;
    const list=host.querySelector('.native-computer-log'), key=JSON.stringify(actions);
    list.hidden=!actions.length;
    if(feedVersions.get(list)===key)return;
    list.replaceChildren(...actions.map(action=>{
      const item=document.createElement('li');
      item.dataset.kind=action.kind.toLowerCase();item.dataset.state=action.state.toLowerCase();
      const kind=document.createElement('span');kind.className='native-action-kind';kind.textContent=actionKinds[action.kind];
      const state=document.createElement('span');state.className='native-action-state';
      state.textContent=actionStates[action.state];
      item.append(kind,state);return item;
    }));
    feedVersions.set(list,key);
  }
  /**
   * Ends the shown source. With a cause ('stopped' by the owner here, 'ended' by Android or the page) the end is
   * kept as a record – target, cause and what was really done, never a picture – until the next turn starts.
   */
  function retireComputer(cause=null,reason='') {
    if(cause && currentComputer) computerRecord={state:cause,taskId:currentComputer.taskId,
      captureSession:currentComputer.captureSession,targetPackage:currentComputer.targetPackage,
      app:workOf(currentComputer)?.app || '',reason};
    computerGate.ended=true;clearComputerFrames();currentComputer=null;computerVisible=false;clearMarker();
    renderComputer();renderDocument();renderFiles();renderFeed();advanceWork();
  }
  function coverComputer() {
    computerVisible=false;clearComputerFrames();renderComputer();renderFiles();
  }
  function currentDecodedFrame(frame) {
    return frame===displayedFrame && currentComputer?.active===true &&
      frame.data.sessionId===session && frame.data.generation===computerGate.generation &&
      frame.data.sequence===computerGate.sequence && !computerGate.ended && performance.now()<frame.expiresAt;
  }
  function visibleFrame(frame) {
    const image=frame.image;
    if(!computerOpen() || document.visibilityState!=='visible' || !image.isConnected || !image.complete ||
       image.naturalWidth!==frame.data.width || image.naturalHeight!==frame.data.height)return false;
    for(let node=image;node instanceof Element;node=node.parentElement) {
      const style=window.getComputedStyle(node);
      if(node.hidden || node.inert || style.display==='none' || style.visibility==='hidden' ||
        style.visibility==='collapse' || style.opacity!=='' && Number(style.opacity)<=.01)return false;
    }
    const rect=image.getBoundingClientRect();
    const left=Math.max(0,rect.left),top=Math.max(0,rect.top);
    const right=Math.min(window.innerWidth,rect.right),bottom=Math.min(window.innerHeight,rect.bottom);
    if(right<=left || bottom<=top)return false;
    if(typeof document.elementFromPoint==='function') {
      const topmost=document.elementFromPoint((left+right)/2,(top+bottom)/2);
      if(topmost!==image && !image.contains(topmost))return false;
    }
    return true;
  }
  function queueFrameAcknowledgement(frame) {
    if(frame.acknowledged || frame.paintQueued || !currentDecodedFrame(frame))return;
    frame.paintQueued=true;
    const afterMotion=()=>{
      if(!currentDecodedFrame(frame))return;
      frame.paints.push(requestAnimationFrame(()=>{
        if(!currentDecodedFrame(frame))return;
        frame.paints.push(requestAnimationFrame(()=>{
          frame.paintQueued=false;
          if(!currentDecodedFrame(frame))return;
          if(!visibleFrame(frame)) {invalidateFrame(frame);return;}
          frame.acknowledged=true;frame.hiddenNotified=false;
          const data=frame.data;
          // This reports decoded, visible pixels only. Android still requires its
          // own visual-state callback and subsequent onDraw before authorizing work.
          if(currentScope(data))send('frame-presented','',{generation:String(data.generation),sequence:String(data.sequence)});
        }));
      }));
    };
    const motion=window.ContinuumWorkspaceMotion;
    if(motion?.isAnimating())motion.whenSettled().then(afterMotion);
    else afterMotion();
  }
  function checkComputerVisibility() {
    visibilityCheck=0;
    if(!displayedFrame)return;
    if(!visibleFrame(displayedFrame)) {
      if(displayedFrame.acknowledged || !window.ContinuumWorkspaceMotion?.isAnimating())invalidateFrame(displayedFrame);
    } else queueFrameAcknowledgement(displayedFrame);
  }
  function scheduleVisibilityCheck() {
    if(!visibilityCheck)visibilityCheck=requestAnimationFrame(checkComputerVisibility);
  }
  function validComputerFrame(data) {
    return data.status==='LIVE' && typeof data.taskId==='string' && data.taskId.length>0 && data.taskId.length<=200 &&
      typeof data.captureSession==='string' && data.captureSession.length>0 && data.captureSession.length<=200 &&
      Number.isSafeInteger(data.sourceEpoch) && data.sourceEpoch>=0 &&
      Number.isInteger(data.width) && data.width>0 && data.width<=640 &&
      Number.isInteger(data.height) && data.height>0 && data.height<=640 &&
      Number.isFinite(data.ageMs) && data.ageMs>=0 && Number.isFinite(data.staleAfterMs) &&
      data.staleAfterMs>0 && data.staleAfterMs<=1500 && data.ageMs<data.staleAfterMs &&
      typeof data.frameDataUri==='string' && data.frameDataUri.length<=174787 &&
      /^data:image\/jpeg;base64,\/9j\/[A-Za-z0-9+/]*={0,2}$/.test(data.frameDataUri);
  }
  function computer(data) {
    if(!newerLivePacket(data,computerGate) || !computerStates.has(data.status))return;
    const hasFrame=data.frameDataUri!==undefined && data.frameDataUri!==null;
    if(data.active && hasFrame && !validComputerFrame(data))return;
    if(data.active && document.visibilityState!=='visible')return;
    const nextGeneration=data.generation!==computerGate.generation;
    const binding=hasFrame?JSON.stringify([data.taskId,data.captureSession,data.sourceEpoch]):null;
    if(data.active && hasFrame && !nextGeneration && computerGate.binding!==null && computerGate.binding!==binding)return;
    if(nextGeneration)computerGate.binding=null;
    if(data.active && hasFrame)computerGate.binding=binding;
    acceptLivePacket(data,computerGate);
    const reason=typeof data.reason==='string'?data.reason.slice(0,240):'';
    if(!data.active) {retireComputer('ended',reason);return;}
    // A new source is a new window; the record of an earlier one does not stay beside it.
    const replaced=nextGeneration && computerRecord!==null;
    if(nextGeneration) {clearComputerFrames();computerVisible=true;computerRecord=null;}
    // Task, capture session and target stay those of this generation; a status packet may repeat or omit them.
    const earlier=currentComputer?.generation===data.generation?currentComputer:null;
    const named=value=>typeof value==='string' && value.length>0 && value.length<=200?value:null;
    currentComputer={sessionId:data.sessionId,generation:data.generation,sequence:data.sequence,
      active:true,status:data.status,reason,taskId:named(data.taskId) ?? earlier?.taskId ?? null,
      captureSession:named(data.captureSession) ?? earlier?.captureSession ?? null,
      targetPackage:(named(data.targetPackage) && packageName.test(data.targetPackage)?data.targetPackage:null) ??
        earlier?.targetPackage ?? null,
      shown:earlier?.shown===true};
    if(replaced)renderFeed();
    const update=()=>renderComputer();
    if(nextGeneration && typeof window.ContinuumWorkspaceMotion?.transition==='function')
      window.ContinuumWorkspaceMotion.transition(update,{kind:'open',duration:950});
    else update();
    advanceWork();
    if(data.status!=='LIVE') {clearComputerFrames();renderComputer();return;}
    if(!hasFrame)return; // Status-only packets never renew the previous image's lease.
    disposeFrame(pendingFrame);
    const image=document.createElement('img');image.className='native-computer-image';
    image.alt='Aktuelles Bild der freigegebenen Android-Quelle';image.width=data.width;image.height=data.height;
    image.dataset.generation=String(data.generation);image.dataset.sequence=String(data.sequence);
    const frame={data,image,expiresAt:performance.now()+data.staleAfterMs-data.ageMs,
      paints:[],expiry:0,paintQueued:false,acknowledged:false,hiddenNotified:false,
      // Geometry Android measured for exactly this frame; without it no input is ever placed on the image.
      view:viewportOf(data.viewport),
      sourceSequence:Number.isSafeInteger(data.sourceSequence) && data.sourceSequence>=0?data.sourceSequence:null};
    pendingFrame=frame;
    const failed=reason=>{
      if(pendingFrame!==frame && displayedFrame!==frame)return;
      if(pendingFrame===frame) {
        invalidateFrame(displayedFrame);disposeFrame(displayedFrame);displayedFrame=null;
      }
      invalidateFrame(frame);disposeFrame(frame);
      if(pendingFrame===frame)pendingFrame=null;
      if(displayedFrame===frame)displayedFrame=null;
      if(currentComputer?.generation===data.generation && computerGate.sequence===data.sequence) {
        currentComputer.reason=reason;
      }
      renderComputer();
    };
    frame.expiry=setTimeout(()=>failed('Bild nicht mehr aktuell. Warte auf ein neues Gerätebild.'),
      data.staleAfterMs-data.ageMs);
    const decoded=()=>{
      if(pendingFrame!==frame || data.generation!==computerGate.generation || data.sequence!==computerGate.sequence ||
        data.sessionId!==session || computerGate.ended || performance.now()>=frame.expiresAt)return;
      if(image.naturalWidth!==data.width || image.naturalHeight!==data.height) {
        failed('Das Gerätebild konnte nicht dargestellt werden.');return;
      }
      if(!computerOpen() || document.visibilityState!=='visible') {
        failed('Gerätebild ist nicht sichtbar.');return;
      }
      const appeared=!displayedFrame;
      invalidateFrame(displayedFrame);disposeFrame(displayedFrame);
      displayedFrame=frame;pendingFrame=null;
      if(currentComputer)currentComputer.shown=true;
      image.onload=null;image.onerror=null;renderComputer();queueFrameAcknowledgement(frame);
      // Once per appearing image; a continuous stream does not hold the avatar's eyes.
      if(appeared)lookAt($('#native-computer-panel'));
    };
    image.onerror=()=>failed('Das Gerätebild konnte nicht dargestellt werden.');
    if(typeof image.decode!=='function')image.onload=decoded;
    image.src=data.frameDataUri;
    if(typeof image.decode==='function')image.decode().then(decoded,()=>failed('Das Gerätebild konnte nicht dargestellt werden.'));
  }
  function clearNativeSpeech(retire=false) {
    clearTimeout(speechExpiry);speechExpiry=0;currentSpeech=null;
    if(retire)speechGate.ended=true;
    window.ContinuumAvatar?.clearSpeech();
    sceneAudio(null);speechLight(null);finishReturn();
    const avatar=$('.avatar-crop');
    if(avatar) {avatar.dataset.speechActive='false';delete avatar.dataset.speechSource;}
  }
  function speech(data) {
    if(!newerLivePacket(data,speechGate))return;
    if(data.active && (!visemes.has(data.viseme) || !Number.isFinite(data.weight) || data.weight<0 || data.weight>1 ||
      !Number.isFinite(data.jawOpen) || data.jawOpen<0 || data.jawOpen>1 || !Number.isFinite(data.durationMs) ||
      data.durationMs<20 || data.durationMs>500 || data.source!=null && !speechSources.has(data.source) ||
      typeof data.utteranceId!=='string' || !data.utteranceId.length || data.utteranceId.length>200))return;
    if(data.active && data.generation===speechGate.generation && speechGate.utteranceId!==null &&
      data.utteranceId!==speechGate.utteranceId)return;
    if(data.active && document.visibilityState!=='visible')return;
    const nextGeneration=data.generation!==speechGate.generation;
    if(nextGeneration)speechGate.utteranceId=data.active?data.utteranceId:null;
    acceptLivePacket(data,speechGate);
    if(!data.active || document.visibilityState!=='visible') {clearNativeSpeech(true);return;}
    if(nextGeneration)clearNativeSpeech();
    currentSpeech=data;clearTimeout(speechExpiry);
    window.ContinuumAvatar?.setSpeechFrame({viseme:data.viseme,weight:data.weight,jawOpen:data.jawOpen,durationMs:data.durationMs});
    // The same measured mouth opening drives the orb; null afterwards means no signal.
    sceneAudio(Math.max(0,Math.min(1,data.jawOpen)));speechLight(Math.max(0,Math.min(1,data.jawOpen)));
    const avatar=$('.avatar-crop');
    if(avatar) {avatar.dataset.speechActive='true';avatar.dataset.speechSource=data.source||'UNKNOWN';}
    speechExpiry=setTimeout(()=>{if(currentSpeech===data)clearNativeSpeech();},data.durationMs);
  }
  function renderFeed() {
    if (!snapshot) return;
    // A draft never outlives its turn, whatever was or was not delivered.
    if (snapshot.busy!==true) draftText='';
    const workspace=$('#workspace');
    if (!workspace) return;
    const show=lines.length>0 || sourceSets.size>0 || snapshot.busy===true || recordState()!==null ||
      (Array.isArray(snapshot.workRows) && snapshot.workRows.length>0);
    workspace.classList.toggle('native-work-active',show && !currentDocument && !computerOpen() &&
      !filesOpen());
    if (!show) {
      workspace.querySelector('#native-live-panel')?.remove();renderNativeInstruments();renderWindowKind();return;
    }
    let panel=workspace.querySelector('#native-live-panel');
    if (!panel) {
      panel=document.createElement('section');
      panel.id='native-live-panel';
      panel.className='projected-computer native-live-panel';
      panel.innerHTML='<div class="op-console-head"><div><span class="op-kicker">CONTINUUM · ANDROID</span><h2>Dein echter Arbeitsraum.</h2></div><span class="op-mode-label">LOKALE DATEN</span></div><div class="native-transcript" aria-live="polite"></div><aside class="native-monitor-record" aria-label="Live-Fenster" hidden></aside><div class="native-lane-state"></div>';
      workspace.appendChild(panel);
    }
    panel.querySelector('h2').textContent=snapshot.viewKind==='BOT'?String(snapshot.viewLabel||'Helfer').slice(0,96):'Dein echter Arbeitsraum.';
    renderWorkBadge(panel);
    const feed=panel.querySelector('.native-transcript');
    const signature=feedRevision+'|'+JSON.stringify(snapshot.workRows);
    if (feedVersions.get(feed)!==signature) {
      const entries=lines.slice(-60).map(line=>{
      const item=document.createElement('article');
      item.className='native-chat-line '+(line.user?'native-owner':'native-assistant');
      // The answer Android just delivered arrives once; while it is the last line it is this turn's result.
      if(line===arrivedLine)item.dataset.arrival='reply';
      if(line===replyLine && line===lines.at(-1))item.dataset.result='true';
      const label=document.createElement('small');label.textContent=line.user?'DU':
        snapshot.viewKind==='BOT'?String(snapshot.viewLabel||'Helfer').slice(0,96):'CONTINUUM';
      const content=document.createElement('p');content.textContent=boundedMessage(line.text);
      item.append(label,content);
      for(const [id,set] of sourceSets)if(set.anchor===line)item.appendChild(sourceCard(set,id===arrivedSources));
      return item;
    });
    for(const [id,set] of sourceSets)if(set.anchor===null)entries.push(sourceCard(set,id===arrivedSources));
    arrivedLine=null;arrivedSources=null;
    if (Array.isArray(snapshot.workRows)) snapshot.workRows.slice(0,20).forEach(row=>{
      if(!row || typeof row.label !== 'string' || typeof row.value !== 'string')return;
      const item=document.createElement('article');
      item.className='native-chat-line native-computer-result';
      const label=document.createElement('small');
      label.textContent='ANDROID-COMPUTER · '+row.label.slice(0,100);
      const content=document.createElement('p');
      content.textContent=row.value.slice(0,500);
      item.append(label,content);entries.push(item);
    });
      const pinned=feed.scrollHeight-feed.scrollTop-feed.clientHeight<40;
      feed.replaceChildren(...entries);feedVersions.set(feed,signature);
      if(pinned)feed.scrollTop=feed.scrollHeight;
    }
    renderDraft(feed);renderRecord(panel);
    panel.querySelector('.native-lane-state').textContent=snapshot.busy?
      'AUFTRAG WIRD BEARBEITET · STATUS VON ANDROID':
      (snapshot.status||'BEREIT · LOKALER GESPRÄCHSVERLAUF');
    const caption=$('#workspace-footnote');
    if(caption && !currentDocument && !computerOpen() && !filesOpen())
      caption.textContent='Dein Gespräch und deine Arbeitsschritte';
    renderNativeInstruments();renderWindowKind();
  }

  /**
   * The work window's state, said in its own corner. In this order: a question to the owner, a blocker, the answer
   * being written, running work with its real step count, a failed step, the delivered answer; otherwise the
   * plain origin of the content. Every case is a fact from the snapshot, the draft or the reply – never a guess.
   */
  function renderWorkBadge(panel=document.querySelector('#native-live-panel')) {
    const badge=panel?.querySelector('.op-console-head .op-mode-label');
    if(!badge || !snapshot)return;
    const presence=own(presences,snapshot.presence)?snapshot.presence:null, steps=currentActivity?.steps ?? [];
    const running=snapshot.busy===true || currentActivity?.active===true;
    const [state,label]=presence==='WAITING_FOR_APPROVAL'?['approval','WARTET AUF DEINE FREIGABE']:
      presence==='BLOCKED'?['blocked','BLOCKIERT']:
      running && draftText?['writing','ANTWORT ENTSTEHT']:
      running?['running',steps.length?'LÄUFT · '+steps.filter(step=>step.state==='DONE').length+'/'+steps.length:'LÄUFT']:
      steps.some(step=>step.state==='FAILED')?['failed','SCHRITT FEHLGESCHLAGEN']:
      replyLine && replyLine===lines.at(-1)?['answered','ANTWORT GELIEFERT']:['local','LOKALE DATEN'];
    if(badge.textContent!==label)badge.textContent=label;
    if(badge.dataset.windowState!==state)badge.dataset.windowState=state;
  }
  /** What the live window has to say while no image is shown: an open Android consent, or how it ended. */
  function recordState() {
    if(currentWork?.consentPending===true && !computerOpen())return 'consent';
    return computerRecord?computerRecord.state:null;
  }
  function renderRecord(panel) {
    const host=panel.querySelector('.native-monitor-record');
    if(!host)return;
    const state=recordState();
    if(!state) {
      if(!host.hidden) {host.hidden=true;host.replaceChildren();delete host.dataset.state;feedVersions.delete(host);}
      return;
    }
    const record=state==='consent'?null:computerRecord, work=record?workOf(record):null;
    const target=record?[work?.app || record.app,record.targetPackage].filter(Boolean).join(' · '):currentWork.app;
    const signature=JSON.stringify([state,target,record?.reason,work?.goal,work?.stage,work?.actions]);
    host.hidden=false;
    if(feedVersions.get(host)===signature)return;
    feedVersions.set(host,signature);host.dataset.state=state;
    const head=document.createElement('div');head.className='native-record-head';
    const kicker=document.createElement('small');kicker.textContent='LIVE-FENSTER';
    const badge=document.createElement('span');badge.className='native-record-badge';badge.setAttribute('role','status');
    badge.textContent=state==='consent'?'WARTET AUF DEINE FREIGABE':state==='stopped'?'GESTOPPT':'BEENDET';
    head.append(kicker,badge);
    const parts=[head];
    if(target) {const name=document.createElement('strong');name.textContent=target;parts.push(name);}
    const text=document.createElement('p');
    text.textContent=state==='consent'?
      'Android fragt nach der Bildschirmfreigabe. Ohne deine Zustimmung wird kein Bild übertragen.':
      state==='stopped'?'Du hast das Live-Bild gestoppt. Es wird kein Bild mehr übertragen.':
      (record.reason?record.reason.replace(/[.\s]+$/,'')+'. ':'')+'Es wird kein Bild mehr übertragen.';
    parts.push(text);
    if(work) {const log=actionLog();renderActionLog(log,work);parts.push(log);}
    host.replaceChildren(...parts);
  }
  function sourceCard(set,arrived=false) {
    const card=document.createElement('section');card.className='native-source-card';
    if(arrived)card.dataset.arrival='sources';
    const heading=document.createElement('small');heading.textContent='QUELLEN';card.appendChild(heading);
    for(const item of set.items) {
      const button=document.createElement('button');button.type='button';button.className='native-source-link';
      button.dataset.nativeSource=item.id;
      button.setAttribute('aria-label','Quelle öffnen: '+item.title);
      // One small screen per source the app really received: its host as the address line, its title below.
      // There is no excerpt in what Android holds for a source, so none is shown.
      const host=document.createElement('small');host.className='native-source-host';
      try {host.textContent=new URL(item.url).hostname;}catch {host.textContent='';}
      const title=document.createElement('strong');title.textContent=item.title;
      const url=document.createElement('span');url.textContent=item.url;
      button.append(host,title,url);card.appendChild(button);
    }
    return card;
  }
  function sources(data) {
    if(!currentScope(data) || !chatId.test(data.sourceSetId) || !Array.isArray(data.items))return;
    const items=[];
    for(const item of data.items.slice(0,8)) {
      if(!item || !chatId.test(item.id) || typeof item.title!=='string' ||
        typeof item.url!=='string' || item.url.length>4096 || items.some(row=>row.id===item.id))continue;
      let parsed;try {parsed=new URL(item.url);}catch {continue;}
      if(!['https:','http:'].includes(parsed.protocol) || parsed.username || parsed.password)continue;
      items.push({id:item.id,title:item.title.slice(0,300)||parsed.hostname,url:item.url});
    }
    const previous=sourceSets.get(data.sourceSetId);
    if(items.length)sourceSets.set(data.sourceSetId,{items,
      anchor:previous?.anchor ?? [...lines].reverse().find(row=>!row.user) ?? null});
    else sourceSets.delete(data.sourceSetId);
    while(sourceSets.size>60)sourceSets.delete(sourceSets.keys().next().value);
    if(items.length && workTurn)workSources=true;
    if(items.length)arrivedSources=data.sourceSetId;
    feedRevision++;renderFeed();advanceWork();
    if(snapshot && sceneChoice(snapshot)?.kind==='web')renderScene(snapshot);
    if(items.length) {
      const workspace=$('#workspace');
      const card=workspace?.classList.contains('native-work-active')?
        workspace.querySelector('#native-live-panel [data-native-source="'+items[0].id+'"]')?.closest('.native-source-card'):null;
      lookAt(card || $('[data-presence-right] .native-evidence'));
    }
  }
  function pruneSources() {
    for(const [id,set] of sourceSets)if(set.anchor!==null && !lines.includes(set.anchor))sourceSets.delete(id);
  }

  /**
   * Only the document already opened via Android's read-only picker is shown.
   * All metadata/text are inserted using textContent, never trusted as HTML.
   * There is no data transfer, filesystem access or preview-side PDF parser.
   */
  function renderDocument() {
    const workspace=$('#workspace');
    if (!workspace || !currentScope(currentDocument)) return;
    if(computerOpen()) {workspace.classList.remove('native-document-active');return;}
    let panel=workspace.querySelector('#native-document-panel');
    if (!panel) {
      panel=document.createElement('section');
      panel.id='native-document-panel';
      panel.className='projected-computer native-document-panel';
      const top=document.createElement('div');
      top.className='native-document-heading';
      const label=document.createElement('small');label.textContent='ECHTES DOKUMENT · ANDROID-AUSWAHL';
      const title=document.createElement('h2');
      title.className='native-document-title';
      top.append(label,title);
      const buttons=document.createElement('div');
      buttons.className='action-row';
      for(const [id,text] of [['files','ANDERE PDF ÖFFNEN'],['draft','ENTWURF ERSTELLEN'],['close','DOKUMENT SCHLIESSEN']]) {
        const b=document.createElement('button');
        b.type='button';b.className='secondary-button';
        b.dataset.nativePdf=id;b.textContent=text;buttons.appendChild(b);
      }
      const detail=document.createElement('div');detail.className='native-document-facts';
      const image=document.createElement('img');
      image.className='native-document-image';image.alt='Tatsächlich gerenderte erste PDF-Seite';
      image.loading='eager';
      const text=document.createElement('div');text.className='native-document-text';
      detail.append(image,text);
      const draftStatus=document.createElement('p');draftStatus.className='native-document-draft-status';
      draftStatus.setAttribute('aria-live','polite');
      panel.append(top,buttons,draftStatus,detail);
      workspace.appendChild(panel);
    }
    const doc=currentDocument;
    panel.querySelector('[data-native-pdf="draft"]').disabled=doc.canDraft!==true ||
      snapshot?.busy===true || snapshot?.commandPending===true;
    panel.querySelector('.native-document-draft-status').textContent=String(doc.draftStatus||'').slice(0,500);
    panel.querySelector('.native-document-title').textContent=String(doc.name||'Dokument.pdf');
    const img=panel.querySelector('.native-document-image');
    if(typeof doc.previewDataUri==='string' &&
        /^data:image\/jpeg;base64,[A-Za-z0-9+/=]+$/.test(doc.previewDataUri) &&
        doc.previewDataUri.length <= 400000) {
      if(img.src!==doc.previewDataUri)img.src=doc.previewDataUri;
      img.hidden=false;
    } else {
      img.removeAttribute('src');img.hidden=true;
    }
    const text=panel.querySelector('.native-document-text');
    text.replaceChildren();
    const meta=document.createElement('small');
    meta.textContent='SEITEN: '+(Number.isInteger(doc.pageCount)?doc.pageCount:'?')+
      ' · DATEI VOM ANDROID-DOKUMENTPICKER';
    const notice=document.createElement('p');
    const actual=doc.textState==='EXTRACTED' && typeof doc.text==='string' && doc.text.length>0;
    notice.textContent=actual ?
      'Der folgende Text stammt aus der auf dem Gerät geöffneten PDF. Es ist keine KI-Zusammenfassung.' :
      doc.textState==='NEEDS_ANDROID_15' ?
        'Textauszug wird erst ab Android 15 unterstützt. Das Seitenbild ist echt.' :
        'Kein auslesbarer Text vorhanden. Eine OCR wurde nicht durchgeführt.';
    const body=document.createElement('pre');
    body.className='native-document-excerpt';
    body.textContent=actual?doc.text.slice(0,12000):'';
    text.append(meta,notice,body);
    workspace.classList.add('native-document-active');
    workspace.classList.remove('native-work-active');
    const foot=$('#workspace-footnote');
    if(foot)foot.textContent='PDF · '+String(doc.name||'Dokument.pdf');
    renderWindowKind();
  }
  function clearDocument() {
    documentRevision++;currentDocument=null;
    const workspace=$('#workspace');
    workspace?.querySelector('#native-document-panel')?.remove();
    workspace?.classList.remove('native-document-active');
    renderFiles();renderFeed();renderComputer();advanceWork();
  }
  function documentOpened(document) {
    if(!snapshot || !currentScope(document) || !chatId.test(document.documentId)) return;
    if(typeof document.name!=='string' || document.name.length>120 ||
       !Number.isInteger(document.pageCount) || document.pageCount < 0) return;
    const revision=++documentRevision;
    if(currentDocument?.documentId===document.documentId) {
      currentDocument=document;renderDocument();return;
    }
    const set = () => {
      if(revision!==documentRevision || !currentScope(document))return;
      coverComputer();
      currentDocument=document;
      renderFiles();renderDocument();
      lookAt($('#native-document-panel'));
    };
    // Reuse the ORIGINAL 950ms interruptible flight/FLIP controller from PR #850.
    // No second CSS animation or stale timer can overwrite a newer native document.
    if (typeof window.ContinuumWorkspaceMotion?.transition === 'function') {
      window.ContinuumWorkspaceMotion.transition(set,{kind:'open',duration:950});
    } else set();
    advanceWork();
  }

  /** Which window the workspace really shows: the live window, a result (document), files or the work feed. */
  function renderWindowKind() {
    const shown=computerOpen()?'live':currentDocument?'result':filesOpen()?'files':
      $('#workspace')?.classList.contains('native-work-active')?'work':null;
    if(shown===null)delete document.body.dataset.nativeWindow;
    else if(document.body.dataset.nativeWindow!==shown)document.body.dataset.nativeWindow=shown;
    // One render can pass through several windows; the hand-over is decided from where it ends (see handover).
    if(shown!==shownWindow && !windowQueued) {windowQueued=true;Promise.resolve().then(handover);}
  }
  function nativeLabels() {
    if(!snapshot)return;
    for(const [action,label] of [['open-demo','PDF öffnen'],['research-demo','Recherche öffnen'],
      ['local-file','Datei öffnen']]) {
      document.querySelectorAll('[data-action="'+action+'"]').forEach(button=>{
        if([...button.childNodes].some(node=>node.nodeType===3 && node.textContent.trim()))labelWithIcons(button,label);
      });
    }
    const set=(selector,text)=>{const node=$(selector);if(node && node.textContent!==text)node.textContent=text;};
    labelWithIcons($('.preview-badge'),'ANDROID · LOKAL');set('.sidebar-note small','Gespeicherte Gespräche auf deinem Gerät.');
    set('.project-button small','Deine Gespräche');set('.input-hint','LOKAL');
    set('#workspace-meta',computerOpen()?'COMPUTER':currentDocument?'PDF':filesOpen()?(currentFiles.kind==='APPS'?'APPS':'DATEIEN'):
      snapshot.busy?'AUFTRAG AKTIV':'LOKAL');
    renderWindowKind();
    set('#workspace-footnote',computerOpen()?(currentComputer.reason||'Gerätebild · Android'):
      currentDocument?'PDF · '+currentDocument.name:
      filesOpen()?(currentFiles.kind==='APPS'?'App-Namen aus dem Android-Werkzeug':'Dateinamen aus dem Android-Werkzeug'):
      lines.length?'Dein Gespräch und deine Arbeitsschritte':'Bereit für deinen Auftrag');
    const recipient=snapshot.viewKind==='BOT'?String(snapshot.viewLabel||'Helfer').slice(0,96):null;
    $('#command-input')?.setAttribute('aria-label',recipient?'Nachricht an '+recipient:'Dein Auftrag');
    $('#command-input')?.setAttribute('placeholder',recipient?'Nachricht an '+recipient+' …':'Schreibe deinen Auftrag …');
    const mic=$('#listen-button'), voiceMode=snapshot.voiceMode;
    const micLabel=voiceMode?.active===true?
      (voiceMode.paused===true?'Gesprächsmodus pausiert. Antippen: weiter sprechen. Lange drücken: beenden.':
        'Gesprächsmodus aktiv. Antippen: jetzt sprechen. Lange drücken: beenden.'):
      'Spracheingabe starten. Lange drücken: still diktieren.';
    mic?.setAttribute('aria-label',micLabel);mic?.setAttribute('title',micLabel);
    mic?.setAttribute('aria-pressed',voiceMode?.active===true?'true':'false');
    $('.send-button')?.setAttribute('aria-label','Auftrag senden');
  }
  // ───────── Code71 · tactical command centre ─────────
  // Status strip, order rail, start-up check and info cards above the stage. Every element stands for a fact of
  // the snapshot (`tactical`, `orders`, `systemCheck`, `briefingCards`, tier and presence) and is absent without
  // it; no figure, target or alarm is made up. Motion is CSS: this code sets one mark per real event (an entrance,
  // a changed state, a departure) and only where motion may run – the "full" and "balanced" effect tiers, the
  // owner's light motion on, no system preference against it. While the device computes nothing here moves.
  /** Plain bounded text of a snapshot string; anything else is the empty string. */
  function plain(value,max) {
    return typeof value==='string'?bounded(value.replace(/[\u0000-\u001f\u007f-\u009f]/g,'').trim(),max):'';
  }
  /** Android's optional order record: the open mission's steps or the running turn's steps. Malformed = none. */
  function ordersOf(value) {
    if(!value || typeof value!=='object' || !Number.isSafeInteger(value.revision) || value.revision<0 ||
      (value.source!=='MISSION' && value.source!=='TURN') || typeof value.key!=='string' ||
      !/^[mt][0-9]{1,12}$/.test(value.key) || typeof value.active!=='boolean' || !Number.isSafeInteger(value.total) ||
      value.total<0 || value.total>999 || !Array.isArray(value.items))return null;
    const items=[];
    for(const item of value.items.slice(0,MAX_ORDERS)) {
      if(!item || typeof item.key!=='string' || !item.key || item.key.length>120 || !Number.isSafeInteger(item.number) ||
        item.number<1 || item.number>999 || !own(orderStates,item.state) || items.some(row=>row.key===item.key))continue;
      const label=plain(item.label,80);
      if(label)items.push({key:item.key,number:item.number,label,state:item.state});
    }
    return {revision:value.revision,source:value.source,key:value.key,title:plain(value.title,80),
      total:Math.max(value.total,items.length),active:value.active,
      outcome:own(orderOutcomes,value.outcome)?value.outcome:null,items};
  }
  /** Android's readouts for the status strip. A reading the platform did not report stays null: no figure. */
  function tacticalOf(value) {
    if(!value || typeof value!=='object' || (value.brain!=='CLAUDE' && value.brain!=='LOCAL'))return null;
    return {brain:value.brain,internet:own(internetModes,value.internet)?value.internet:null,
      thermal:Number.isInteger(value.thermal) && value.thermal>=0 && value.thermal<=6?value.thermal:null,
      battery:Number.isInteger(value.battery) && value.battery>=0 && value.battery<=100?value.battery:null,
      charging:typeof value.charging==='boolean'?value.charging:null};
  }
  /** `systemCheck`: [{key,label,state:"OK"|"WARN"|"FAIL"|"OFF",detail}]. Unknown states are dropped, not guessed. */
  function checksOf(value) {
    const checks=[];
    for(const item of Array.isArray(value)?value.slice(0,MAX_CHECKS):[]) {
      if(!item || typeof item.key!=='string' || !/^[A-Za-z0-9._:-]{1,64}$/.test(item.key) ||
        !own(checkStates,item.state) || checks.some(row=>row.key===item.key))continue;
      const label=plain(item.label,40);
      if(label)checks.push({key:item.key,label,state:item.state,detail:plain(item.detail,120)});
    }
    return checks;
  }
  /** `briefingCards`: [{id,kind,title,lines:[≤4],state:"info"|"attention"}]. */
  function cardsOf(value) {
    const cards=[];
    for(const item of Array.isArray(value)?value.slice(0,MAX_CARDS):[]) {
      if(!item || typeof item.id!=='string' || !/^[A-Za-z0-9._:-]{1,64}$/.test(item.id) || !own(cardKinds,item.kind) ||
        (item.state!=='info' && item.state!=='attention') || cards.some(row=>row.id===item.id))continue;
      const title=plain(item.title,60);
      if(title)cards.push({id:item.id,kind:item.kind,title,state:item.state,
        lines:(Array.isArray(item.lines)?item.lines:[]).map(line=>plain(line,90)).filter(Boolean).slice(0,4)});
    }
    return cards;
  }
  /** The tier in which added motion may run right now, or null. */
  function motionTier() {
    const effects=document.body.dataset.nativeEffects;
    return (effects==='full' || effects==='balanced') && document.body.dataset.nativeLightMotion!=='off' &&
      !reducedMotion()?effects:null;
  }
  /** Marks a new element for its one entrance; `order` staggers elements that arrive together. */
  function enter(node,order=0) {
    if(!motionTier())return;
    node.dataset.nativeEnter='a';
    if(order>0)node.style.setProperty('--tac-i',String(Math.min(order,MAX_STAGGER)));
  }
  /**
   * Ends something that is shown. Where motion may run the element gets its leaving mark and is finished when the
   * animations of that mark have ended; where none started (resting stage, reduced motion, no animation engine)
   * it is finished at once. The final state never depends on an animation having run.
   */
  function leave(node,finish) {
    if(!node || node.dataset.nativeLeaving==='true')return;
    node.dataset.nativeLeaving='true';delete node.dataset.nativeEnter;
    const done=()=>{
      if(leavers.get(node)!==done)return;
      leavers.delete(node);finish(node);
    };
    leavers.set(node,done);
    const leaving=motionTier()?(node.getAnimations?.() ?? [])
      .filter(item=>typeof item.animationName==='string' && /^native-(tac|scene)-out/.test(item.animationName)):[];
    if(leaving.length)Promise.allSettled(leaving.map(item=>item.finished)).then(done);
    else done();
  }
  /** Settles everything that is arriving, changing or leaving at once: the state without animation. */
  function settleMarks() {
    for(const done of [...leavers.values()])done();
    for(const flight of [...gliding])flight.cancel();
    document.querySelectorAll('[data-native-enter]').forEach(node=>{delete node.dataset.nativeEnter;});
    document.querySelectorAll('.native-tac[data-changed],.native-tac [data-changed],.native-scene [data-changed]')
      .forEach(node=>node.removeAttribute('data-changed'));
    document.querySelectorAll('.workspace-shell>.native-window-ghost').forEach(node=>node.remove());
    if(armingMode==='run')settleArming();
  }
  /**
   * Applies a change of the regions above the stage. What that change pushes down or pulls up glides to its new
   * place (FLIP): two measurements per real event, then transform only – no layout per frame. A newer change
   * starts from where the element visibly is.
   */
  function reflow(change) {
    const tier=motionTier(), stage=$('.center-stage');
    const movers=tier && stage && typeof stage.animate==='function'?
      [...stage.children].filter(node=>!node.classList.contains('native-tac')):[];
    const before=movers.map(node=>node.getBoundingClientRect().top);
    for(const node of movers) {flights.get(node)?.cancel();flights.delete(node);}
    change();
    movers.forEach((node,index)=>{
      // A change nested in this one has measured for itself; this one replaces its flight.
      flights.get(node)?.cancel();flights.delete(node);
      const delta=before[index]-node.getBoundingClientRect().top;
      if(Math.abs(delta)<2 || Math.abs(delta)>720)return;
      const flight=node.animate([{transform:'translateY('+delta+'px)'},{transform:'translateY(0)'}],
        {duration:tier==='full'?360:200,easing:'cubic-bezier(.16,.84,.24,1)',composite:'add'});
      flights.set(node,flight);gliding.add(flight);
      const landed=()=>gliding.delete(flight);
      flight.finished.then(landed,landed);
    });
  }
  function tacHud() {
    let hud=$('#native-tac-hud');
    const stage=$('.center-stage');
    if(!hud && stage) {
      hud=document.createElement('div');hud.id='native-tac-hud';hud.className='native-tac native-tac-hud';
      stage.prepend(hud);
    }
    return hud;
  }
  const setText=(node,text)=>{if(node && node.textContent!==text)node.textContent=text;};
  const part=(tag,name,text)=>{const node=document.createElement(tag);node.className=name;if(text!==undefined)node.textContent=text;return node;};
  /** What the strip, the rail and the cards have to warn about right now: 'fail', 'warn' or nothing. */
  function attentionOf(state) {
    const presence=own(presences,state.presence)?state.presence:null, items=currentOrders?.items ?? [];
    if(currentChecks.some(check=>check.state==='FAIL') || (currentTactical?.thermal ?? 0)>=4 || presence==='BLOCKED' ||
      currentOrders?.outcome==='FAILED' || (currentOrders?.active && items.some(item=>item.state==='FAILED')))return 'fail';
    if(currentChecks.some(check=>check.state==='WARN') || currentCards.some(card=>card.state==='attention') ||
      (currentTactical?.thermal ?? 0)>=2 || presence==='WAITING_FOR_APPROVAL' || currentWork?.consentPending===true ||
      (currentOrders?.active && items.some(item=>item.state==='WAITING')))return 'warn';
    return null;
  }
  /** The status strip: one cell per reading Android really sent. */
  function renderStrip(state) {
    const hud=tacHud();
    let strip=$('#native-tac-strip');
    if(!currentTactical || !hud) {strip?.remove();return;}
    const effects=document.body.dataset.nativeEffects, tier=own(qualities,state.quality)?state.quality:null;
    const open=currentOrders?.active?currentOrders.items.filter(item=>item.state==='ACTIVE' || item.state==='WAITING'):[];
    const waiting=open.some(item=>item.state==='WAITING');
    const ready=currentChecks.filter(check=>check.state==='OK').length;
    const cells=[['brain','HIRN',currentTactical.brain==='CLAUDE'?'CLAUDE':'LOKAL',currentTactical.brain==='CLAUDE'?'cloud':'local'],
      currentTactical.internet?['internet','NETZ',internetModes[currentTactical.internet],
        currentTactical.internet==='OFF'?'off':'normal']:null,
      currentTactical.thermal!==null?['thermal','WÄRME',thermalNames[currentTactical.thermal],
        currentTactical.thermal>=4?'fail':currentTactical.thermal>=2?'warn':'normal']:null,
      // The strength of the stage's own effects, as it is applied right now (see renderPresence).
      own(effectNames,effects)?['effects','EFFEKTE',effectNames[effects],effects==='rest' || tier==='REDUCED'?'off':'normal']:null,
      currentTactical.battery!==null?['battery','AKKU',currentTactical.battery+' %'+(currentTactical.charging===true?' · LÄDT':''),
        currentTactical.battery<=15 && currentTactical.charging!==true?'warn':'normal']:null,
      currentChecks.length?['systems','SYSTEME',ready+'/'+currentChecks.length,
        currentChecks.some(check=>check.state==='FAIL')?'fail':currentChecks.some(check=>check.state==='WARN')?'warn':
        ready===currentChecks.length?'ok':'off',armingMode!==null]:null,
      ['orders','AUFTRÄGE',open.length?open.length+(waiting?' · WARTET':' AKTIV'):'KEINE',waiting?'warn':open.length?'active':'off']]
      .filter(Boolean);
    if(!strip) {
      strip=document.createElement('section');strip.id='native-tac-strip';strip.className='native-tac-strip';
      strip.setAttribute('aria-label','Lage: Gehirn, Netz, Wärme, Akku und Aufträge');
      hud.prepend(strip);enter(strip);
    }
    const signature=JSON.stringify(cells);
    if(feedVersions.get(strip)===signature)return;
    const earlier=new Map([...strip.children].map(node=>[node.dataset.cell,node]));
    strip.replaceChildren(...cells.map(([key,label,value,tone,listed])=>{
      // The check cell opens the list of checks; every other cell is a readout.
      let cell=earlier.get(key);
      if(!cell) {
        cell=document.createElement(key==='systems'?'button':'div');cell.className='native-tac-cell';cell.dataset.cell=key;
        if(key==='systems') {cell.type='button';cell.dataset.nativeTac='systems';}
        cell.append(part('span','native-tac-key',label),part('span','native-tac-value'));
      }
      const shown=cell.querySelector('.native-tac-value');
      // A reading that moved (not its first appearance) is marked for one pass.
      if(feedVersions.has(strip) && earlier.has(key) && shown.textContent!==value && motionTier())cell.dataset.changed='true';
      setText(shown,value);
      if(cell.dataset.tone!==tone)cell.dataset.tone=tone;
      if(key==='systems') {
        cell.setAttribute('aria-expanded',String(listed));
        cell.setAttribute('aria-label','Systemcheck: '+value+' bereit. Liste '+(listed?'schließen':'öffnen'));
      }
      return cell;
    }));
    feedVersions.set(strip,signature);
  }
  /** The order rail: the steps Android really reported, as cards; finished steps dock into a compact stack. */
  function renderOrders() {
    const hud=tacHud(), orders=currentOrders;
    let rail=$('#native-order-rail');
    if(!orders || !hud) {
      if(rail)leave(rail,node=>reflow(()=>node.remove()));
      return;
    }
    if(rail && (rail.dataset.orderKey!==orders.key || rail.dataset.nativeLeaving==='true')) {
      // A newer order wins: the rail that is shown (or still leaving) and the cards leaving in it are gone at once.
      for(const node of [rail,...rail.querySelectorAll('[data-native-leaving]')])leavers.delete(node);
      rail.remove();rail=null;
    }
    if(rail && feedVersions.get(rail)===orders.revision)return;
    reflow(()=>{
      const fresh=!rail;
      if(fresh) {
        rail=document.createElement('section');rail.id='native-order-rail';rail.className='native-tac native-order-rail';
        rail.dataset.orderKey=orders.key;rail.setAttribute('aria-label','Arbeitsaufträge');
        const head=document.createElement('button');head.type='button';head.className='native-order-head';
        head.dataset.nativeTac='orders';
        const stamp=part('span','native-order-stamp');stamp.setAttribute('role','status');
        head.append(part('span','native-order-call'),part('span','native-order-title'),part('span','native-order-count'),stamp);
        const list=part('ol','native-order-list');list.setAttribute('aria-label','Laufende Schritte');
        const done=part('ol','native-order-done');done.setAttribute('aria-label','Erledigte Schritte');
        const banner=part('span','native-order-banner');banner.setAttribute('aria-hidden','true');
        rail.append(head,list,done,banner);
        // Its own region of the stage column, directly below the strip: it stays in view while the page scrolls.
        hud.after(rail);
        enter(rail);
        // The line of the moment passes in the full tier only.
        if(motionTier()==='full')enter(banner);
      }
      const number=String(Number(orders.key.slice(1))).padStart(2,'0');
      const docked=orders.items.filter(item=>item.state==='DONE'), live=orders.items.filter(item=>item.state!=='DONE');
      const waiting=orders.active && orders.items.some(item=>item.state==='WAITING');
      const stampText=orders.outcome?orderOutcomes[orders.outcome]:waiting?orderStates.WAITING:
        orders.active?orderStates.ACTIVE:'SCHRITTE GEMELDET';
      const stampState=orders.outcome?orders.outcome.toLowerCase():waiting?'waiting':orders.active?'active':'idle';
      rail.dataset.source=orders.source.toLowerCase();
      if(rail.dataset.orderState!==stampState)rail.dataset.orderState=stampState;
      setText(rail.querySelector('.native-order-call'),(orders.source==='MISSION'?'MISSION M-':'AUFTRAG A-')+number);
      setText(rail.querySelector('.native-order-title'),orders.title?'„'+orders.title+'“':
        orders.source==='MISSION'?'Auftragsfolge':'Laufender Auftrag');
      // Folded, the rail shows its three newest open steps; the count says when there are more.
      setText(rail.querySelector('.native-order-count'),docked.length+'/'+orders.total+' ERLEDIGT'+
        (live.length>OPEN_ORDERS?' · '+live.length+' OFFEN':''));
      const stamp=rail.querySelector('.native-order-stamp');
      if(stamp.textContent!==stampText) {
        // The outcome is stamped once when Android reports it.
        if(!fresh && motionTier())stamp.dataset.changed='true';
        stamp.textContent=stampText;
      }
      // The moment the order or the plan arrives: named once, in a line that is visible only while it passes.
      if(fresh)rail.querySelector('.native-order-banner').textContent=orders.source==='MISSION'?
        'PLAN STEHT · '+orders.total+(orders.total===1?' SCHRITT':' SCHRITTE'):'AUFTRAG ANGENOMMEN';
      const expanded=rail.dataset.expanded==='true';
      const head=rail.querySelector('.native-order-head');
      head.setAttribute('aria-expanded',String(expanded));
      head.setAttribute('aria-label',rail.querySelector('.native-order-call').textContent+': '+stampText+
        '. Alle Schritte '+(expanded?'einklappen':'ausklappen'));
      const list=rail.querySelector('.native-order-list'), stack=rail.querySelector('.native-order-done');
      const shown=new Map([...list.children].filter(node=>node.dataset.nativeLeaving!=='true').map(node=>[node.dataset.key,node]));
      let arrived=0;
      for(const item of live) {
        let card=shown.get(item.key);
        if(card)shown.delete(item.key);
        else {
          card=part('li','native-order-card');card.dataset.key=item.key;
          card.append(part('span','native-order-no'),part('span','native-order-label'),part('span','native-order-state'));
          list.appendChild(card);enter(card,fresh?arrived:0);arrived++;
        }
        const state=item.state.toLowerCase();
        if(card.dataset.state!==state) {
          if(card.dataset.state && motionTier())card.dataset.changed='true';
          card.dataset.state=state;
        }
        setText(card.querySelector('.native-order-no'),String(item.number).padStart(2,'0'));
        setText(card.querySelector('.native-order-label'),item.label);
        setText(card.querySelector('.native-order-state'),orderStates[item.state]);
      }
      // A card that is no longer open: its step is done (it is stamped and docks) or Android no longer lists it.
      for(const [key,card] of shown) {
        if(docked.some(item=>item.key===key)) {
          card.dataset.state='done';setText(card.querySelector('.native-order-state'),orderStates.DONE);
        }
        // When it has left, a list without cards takes no room.
        leave(card,node=>reflow(()=>{node.remove();list.hidden=!list.children.length;}));
      }
      const chips=new Map([...stack.children].map(node=>[node.dataset.key,node]));
      for(const item of docked) {
        let chip=chips.get(item.key);
        if(chip)chips.delete(item.key);
        else {
          chip=part('li','native-order-chip');chip.dataset.key=item.key;chip.dataset.state='done';
          chip.append(part('span','native-order-no'),part('span','native-order-label'));
          stack.appendChild(chip);
          // It appears as its card leaves; a step that was already done when the rail was built just stands there.
          if(shown.has(item.key))enter(chip);
        }
        setText(chip.querySelector('.native-order-no'),String(item.number).padStart(2,'0'));
        setText(chip.querySelector('.native-order-label'),item.label);
      }
      for(const chip of chips.values())chip.remove();
      stack.hidden=!docked.length;list.hidden=!list.children.length;
      feedVersions.set(rail,orders.revision);
    });
  }
  function checkVerdict() {
    const not=state=>currentChecks.filter(check=>check.state===state);
    if(currentChecks.every(check=>check.state==='OK'))return ['ready','ALLE SYSTEME BEREIT'];
    // Which checks are not ready, by name; what each one says stands in its row.
    return ['problems','NICHT ALLES BEREIT · '+[['FAIL','Fehler'],['WARN','Warnung'],['OFF','Aus']]
      .filter(([state])=>not(state).length).map(([state,word])=>word+': '+not(state).map(check=>check.label).join(', ')).join(' · ')];
  }
  /**
   * The start-up check. `armingMode` is 'run' (the stage just appeared: every check ticks in), 'problems' (only what
   * is not ready stays listed), 'list' (the owner opened the list) or null (closed; the strip keeps the count).
   */
  function renderArming() {
    const hud=tacHud();
    let panel=$('#native-arming');
    if(!currentChecks.length || !armingMode || !hud) {
      if(panel)leave(panel,node=>reflow(()=>node.remove()));
      if('nativeArming' in document.body.dataset)delete document.body.dataset.nativeArming;
      return;
    }
    if(panel?.dataset.nativeLeaving==='true') {leavers.delete(panel);panel.remove();panel=null;}
    const [verdict,sentence]=checkVerdict();
    const rows=armingMode==='problems'?currentChecks.filter(check=>check.state!=='OK'):currentChecks;
    const signature=JSON.stringify([armingMode,rows,sentence]);
    if(panel && feedVersions.get(panel)===signature)return;
    reflow(()=>{
      const fresh=!panel;
      if(fresh) {
        panel=document.createElement('section');panel.id='native-arming';panel.className='native-arming';
        panel.setAttribute('aria-label','Systemcheck');
        const head=part('div','native-arming-head');
        const count=part('span','native-arming-count');count.setAttribute('role','status');
        const close=part('button','native-arming-close','SCHLIESSEN');close.type='button';close.dataset.nativeTac='arming-close';
        close.setAttribute('aria-label','Systemcheck schließen');
        head.append(part('span','native-arming-call'),count,close);
        const list=part('ol','native-arming-list');
        const sentenceNode=part('p','native-arming-verdict');sentenceNode.setAttribute('role','status');
        panel.append(head,list,sentenceNode);
        hud.appendChild(panel);
      }
      panel.dataset.mode=armingMode;panel.dataset.verdict=verdict;
      panel.style.setProperty('--tac-n',String(rows.length));
      setText(panel.querySelector('.native-arming-call'),armingMode==='run'?'SYSTEMSTART · PRÜFUNG':'SYSTEMCHECK');
      setText(panel.querySelector('.native-arming-count'),
        currentChecks.filter(check=>check.state==='OK').length+'/'+currentChecks.length+' BEREIT');
      const running=armingMode==='run' && fresh && !!motionTier();
      panel.querySelector('.native-arming-list').replaceChildren(...rows.map((check,index)=>{
        const row=part('li','native-arming-row');row.dataset.state=check.state.toLowerCase();
        const lamp=part('span','native-check-lamp');lamp.setAttribute('aria-hidden','true');
        row.append(lamp,part('span','native-check-label',check.label),part('span','native-check-detail',check.detail),
          part('span','native-check-state',checkStates[check.state]));
        if(running) {row.dataset.nativeEnter='a';row.style.setProperty('--tac-i',String(index));}
        return row;
      }));
      setText(panel.querySelector('.native-arming-verdict'),sentence);
      if(running) {
        // `pass` lasts as long as the sequence (its line along the lower edge); the entrance marks are shorter.
        panel.dataset.nativeEnter='a';panel.dataset.pass='true';
        if(motionTier()==='full')panel.querySelector('.native-arming-verdict').dataset.nativeEnter='a';
      } else delete panel.dataset.pass;
      feedVersions.set(panel,signature);
    });
    if(armingMode==='run')document.body.dataset.nativeArming='run';
    else if('nativeArming' in document.body.dataset)delete document.body.dataset.nativeArming;
    if(armingMode!=='run' || panel.dataset.pass!=='true' || feedVersions.get(panel)!==signature)return;
    // The sequence is over when the panel's own pass has ended: then only what is not ready stays.
    const token=++armingToken;
    const pass=(panel.getAnimations?.({subtree:true}) ?? []).filter(item=>item.animationName==='native-tac-arming-pass');
    if(pass.length)Promise.allSettled(pass.map(item=>item.finished)).then(()=>{if(token===armingToken)settleArming();});
  }
  /** Ends the start-up sequence: by its own end or by any input of the owner. */
  function settleArming() {
    if(armingMode!=='run')return;
    armingToken++;
    armingMode=currentChecks.some(check=>check.state!=='OK')?'problems':null;
    document.querySelectorAll('#native-tac-hud [data-native-enter]').forEach(node=>{delete node.dataset.nativeEnter;});
    renderArming();
    if(snapshot)renderStrip(snapshot);
    faceOwner();
  }
  /** Info cards: one per item of `briefingCards`; a card that is gone from the list leaves. */
  function renderBriefing() {
    const stage=$('.center-stage'), hud=tacHud();
    let deck=$('#native-briefing');
    if(!hud || !stage)return;
    if(!currentCards.length) {
      if(deck)leave(deck,node=>reflow(()=>node.remove()));
      return;
    }
    if(deck?.dataset.nativeLeaving==='true') {leavers.delete(deck);deck.remove();deck=null;}
    const signature=JSON.stringify(currentCards);
    if(deck && feedVersions.get(deck)===signature)return;
    reflow(()=>{
      if(!deck) {
        deck=document.createElement('section');deck.id='native-briefing';deck.className='native-tac native-briefing';
        deck.setAttribute('aria-label','Lagekarten');
        ($('#native-order-rail') ?? hud).after(deck);
      }
      const shown=new Map([...deck.children].filter(node=>node.dataset.nativeLeaving!=='true').map(node=>[node.dataset.card,node]));
      let arrived=0;
      for(const card of currentCards) {
        let node=shown.get(card.id);
        if(node)shown.delete(card.id);
        else {
          node=part('article','native-brief-card');node.dataset.card=card.id;
          node.append(part('small','native-brief-kind'),part('strong','native-brief-title'),part('ul','native-brief-lines'));
          deck.appendChild(node);enter(node,arrived++);
        }
        node.dataset.kind=card.kind;
        if(node.dataset.state!==card.state) {
          if(node.dataset.state && card.state==='attention' && motionTier())node.dataset.changed='true';
          node.dataset.state=card.state;
        }
        setText(node.querySelector('.native-brief-kind'),cardKinds[card.kind]+(card.state==='attention'?' · ACHTUNG':''));
        setText(node.querySelector('.native-brief-title'),card.title);
        const lines=node.querySelector('.native-brief-lines'), key=JSON.stringify(card.lines);
        if(feedVersions.get(lines)!==key) {
          lines.replaceChildren(...card.lines.map(line=>part('li','native-brief-line',line)));
          lines.hidden=!card.lines.length;feedVersions.set(lines,key);
        }
      }
      for(const node of shown.values())leave(node,gone=>reflow(()=>gone.remove()));
      feedVersions.set(deck,signature);
    });
  }
  /** Everything of this section, from one snapshot. */
  function renderTactical(state) {
    const before={orders:currentOrders,cards:currentCards,checks:currentChecks};
    currentTactical=tacticalOf(state.tactical);
    const orders=ordersOf(state.orders);
    // An unchanged revision keeps what is shown; null withdraws the rail.
    if(!orders || orders.key!==currentOrders?.key || orders.revision!==currentOrders.revision)currentOrders=orders;
    currentChecks=checksOf(state.systemCheck);currentCards=cardsOf(state.briefingCards);
    // The stage appears with real checks: they tick in once. Checks that arrive later only fill the strip.
    if(currentChecks.length && !armed) {armed=true;armingMode=!ownerActed && state.busy!==true?'run':null;}
    if(!currentChecks.length)armingMode=null;
    const attention=attentionOf(state);
    if(attention===null) {if('nativeAttention' in document.body.dataset)delete document.body.dataset.nativeAttention;}
    else if(document.body.dataset.nativeAttention!==attention)document.body.dataset.nativeAttention=attention;
    renderArming();renderStrip(state);renderOrders();renderBriefing();
    // Android changed the thermal tier: the strip's effect cell is marked for one pass.
    const tier=own(qualities,state.quality)?state.quality:null;
    if(lastTier!==undefined && tier!==lastTier && motionTier())$('#native-tac-strip [data-cell="effects"]')?.setAttribute('data-changed','true');
    lastTier=tier;
    // New work, a new card or the start-up list: one glance to where it is shown.
    if(armingMode==='run' && before.checks.length===0)lookAt($('#native-arming'));
    else if(currentOrders && (currentOrders.key!==before.orders?.key ||
      currentOrders.items.length>(before.orders?.items.length ?? 0)))glance($('#native-order-rail'));
    else if(currentCards.some(card=>!before.cards.some(row=>row.id===card.id)))glance($('#native-briefing'));
  }
  /** Another chat: its orders are not this chat's. Checks and info cards belong to the device and stay. */
  function clearTactical() {
    const rail=$('#native-order-rail');
    if(rail) {for(const node of [rail,...rail.querySelectorAll('[data-native-leaving]')])leavers.delete(node);rail.remove();}
    currentOrders=null;
    if(armingMode==='run')settleArming();
  }
  /** Window choreography: the hand-over between two work windows, decided once per task from the final state. */
  function handover() {
    windowQueued=false;
    const to=document.body.dataset.nativeWindow ?? null, from=shownWindow, name=shownName;
    if(to===from)return;
    shownWindow=to;shownName=to==='files' && currentFiles?.kind==='APPS'?'APPS':to?windowNames[to]:'';
    const shell=$('.workspace-shell');
    // A newer hand-over wins: what was still leaving or arriving is settled.
    shell?.querySelectorAll(':scope>.native-window-ghost').forEach(node=>{leavers.delete(node);node.remove();});
    for(const kind of Object.keys(windowNames)) {const panel=windowPanel(kind);if(panel)delete panel.dataset.nativeEnter;}
    if(!motionTier() || !shell)return;
    if(from) {
      // The window that leaves: its plate with its name slides out while the next one comes in.
      const ghost=part('div','native-window-ghost');ghost.setAttribute('aria-hidden','true');ghost.dataset.window=from;
      ghost.appendChild(part('span','native-window-ghost-name',name));
      shell.appendChild(ghost);leave(ghost,node=>node.remove());
    }
    // The same entrance under two names: a window that returns at once starts its entrance again.
    const panel=windowPanel(to);
    if(panel)panel.dataset.nativeEnter=++windowSerial%2?'a':'b';
  }
  function windowPanel(kind) {
    return kind==='live'?$('#native-computer-panel'):kind==='result'?$('#native-document-panel'):
      kind==='files'?$('#native-files-panel'):kind==='work'?$('#native-live-panel'):null;
  }

  // ───────── Code73 · work scenes ─────────
  // One holographic work window directly below the avatar, for exactly one real lane at a time. Android's
  // VisualWorkstationDirector names it (`scene`: {kind, since}); without that key (an older Android) a storage run
  // or a mail record that is present is shown. Each kind is only a configuration of the same renderer: a header with
  // the lane and its real phase, an instrument (the measured storage, the mail search), cards that arrive with the
  // lane's real list, a progress bar of real completed steps, figures, a notice, a result line and at most one
  // action. Everything that reads like data is snapshot data, set as text; a missing field leaves its element out.
  // Motion: the marks of the tactical section (enter/leave, one pass, only where motionTier() allows it). The window
  // arises from the platform's light when the lane starts, cards fly in when Android lists them, the bar travels
  // from the old to the new real value; the avatar looks at the window when it opens and faces the owner when the
  // lane ends or asks. Nothing runs on a clock of its own.
  const sceneKinds = Object.freeze({deepclean:'SPEICHER & BEREINIGUNG',mail:'E-MAIL',send:'SENDEN',web:'RECHERCHE'});
  // Kinds the director may name for the existing work windows: they own the stage, no scene is drawn for them.
  const stageWindows = new Set(['computer','files']);
  const cleanPhases = Object.freeze({SCAN:['SCANNT','active'],CLASSIFY:['ORDNET EIN','active'],CLEAN:['BEREINIGT','active'],
    CHOICE:['WARTET AUF DICH','waiting'],DONE:['ABGESCHLOSSEN','done'],STOPPED:['GESTOPPT','stopped'],FAILED:['FEHLGESCHLAGEN','failed']});
  const cleanModes = Object.freeze({ANALYSE:'Analyse',DEEP_ANALYSE:'Tiefenanalyse',SAFE_CLEAN:'Sichere Bereinigung',
    DEEP_CLEAN:'Tiefenbereinigung',OPTIMISE:'Optimierung',TARGETED:'Gezielte Auswahl',REPORT:'Bericht',RESTORE:'Wiederherstellen'});
  const cleanLevels = Object.freeze([['GREEN','SICHER ENTFERNBAR','green'],['YELLOW','ZUR AUSWAHL','yellow'],
    ['RED','GESCHÜTZT','red'],['EXCLUDED','AUSGENOMMEN','slate']]);
  const mailPhases = Object.freeze({CONNECT:['VERBINDET','active'],SEARCH:['SUCHT','active'],READ:['LIEST','active'],
    SUMMARIZE:['FASST ZUSAMMEN','active'],DRAFT:['ENTWURF · NICHT GESENDET','waiting'],SEND:['SENDET','active'],
    DONE:['ERLEDIGT','done'],FAILED:['FEHLGESCHLAGEN','failed']});
  const sendPhases = Object.freeze({RUNNING:['BEREITET VOR','active'],WAITING:['WARTET AUF DICH','waiting'],
    SENT:['GESENDET · BESTÄTIGT','done'],DRAFT:['ENTWURF · NICHT GESENDET','done'],HANDED_OVER:['AN MAIL-APP ÜBERGEBEN','done'],
    FAILED:['NICHT GESENDET','failed'],STOPPED:['GESTOPPT','stopped']});
  const MAX_SCENE_CARDS = 8;
  let currentScene = null, sceneDismissed = null, sceneGlanced = '';
  const count = value => Number.isSafeInteger(value) && value>=0?value:null;
  /** Bytes as Android's own storage screens say them: decimal units, German comma. */
  function bytesText(value) {
    if(!Number.isSafeInteger(value) || value<0)return '';
    const units=[[1e12,'TB'],[1e9,'GB'],[1e6,'MB'],[1e3,'kB']];
    for(const [size,unit] of units)if(value>=size) {
      const amount=value/size;
      return (amount>=100?String(Math.round(amount)):amount.toFixed(1).replace('.',','))+' '+unit;
    }
    return value+(value===1?' Byte':' Bytes');
  }
  const filesText = value => value===1?'1 Datei':value.toLocaleString('de-DE')+' Dateien';
  function volumeOf(value) {
    const free=count(value?.freeBytes), total=count(value?.totalBytes);
    return free!==null && total!==null && total>0 && free<=total?{free,total}:null;
  }
  /** `deepClean` (schema continuum.deepclean.v1): phase, progress and measured figures; never a file name. */
  function deepCleanOf(value) {
    if(!value || typeof value!=='object' || value.schema!=='continuum.deepclean.v1' || !own(cleanPhases,value.phase))return null;
    const pair=item=>({count:count(item?.count) ?? 0,bytes:count(item?.bytes) ?? 0});
    const categories=[];
    for(const item of Array.isArray(value.categories)?value.categories.slice(0,24):[]) {
      if(!item || typeof item.key!=='string' || !/^[A-Z0-9_]{1,40}$/.test(item.key) ||
        categories.some(row=>row.key===item.key))continue;
      const label=plain(item.label,60), bytes=count(item.bytes), files=count(item.count);
      if(label && bytes!==null && files!==null)categories.push({key:item.key,label,files,bytes});
    }
    const levels={};
    for(const [key] of cleanLevels)levels[key]=pair(value.levels?.[key]);
    return {phase:value.phase,mode:own(cleanModes,value.mode)?value.mode:null,
      done:count(value.progress?.done) ?? 0,total:count(value.progress?.total) ?? 0,categories,levels,
      before:volumeOf(value.before),after:volumeOf(value.after),
      removed:{files:count(value.removed?.files) ?? 0,bytes:count(value.removed?.bytes) ?? 0},
      choice:pair(value.choice),shared:['GRANTED','MISSING','UNSUPPORTED'].includes(value.sharedAccess)?value.sharedAccess:null};
  }
  /**
   * `mailWork` (version 1, gmail/MailWorkBoard): phase, the search, counts, up to six cards (sender, subject, date)
   * and the result line. It never holds an address, a body or an attachment. Unknown fields are ignored; a missing
   * one leaves its element out.
   */
  function mailWorkOf(value) {
    if(!value || typeof value!=='object' || !own(mailPhases,value.phase))return null;
    const cards=[];
    for(const item of Array.isArray(value.cards)?value.cards.slice(0,6):[]) {
      const sender=plain(item?.sender,80), subject=plain(item?.subject,120);
      if(!sender && !subject)continue;
      const state=typeof item.state==='string'?item.state.toUpperCase():'';
      cards.push({index:Number.isSafeInteger(item.index) && item.index>0?item.index:cards.length+1,sender,subject,
        date:mailDate(item.dateMillis),state:['UNREAD','READ','SELECTED'].includes(state)?state:'READ'});
    }
    const progress=value.progress && count(value.progress.total)!==null && count(value.progress.done)!==null &&
      value.progress.done<=value.progress.total?{done:value.progress.done,total:value.progress.total}:null;
    return {phase:value.phase,query:plain(value.query,120),found:count(value.found),read:count(value.read),
      cards,progress,result:plain(value.result,200)};
  }
  const weekdays=Object.freeze(['So.','Mo.','Di.','Mi.','Do.','Fr.','Sa.']);
  /** A mail's date as the owner reads it: today its time („14:05“), otherwise „Di., 17.11.“. */
  function mailDate(millis) {
    if(!Number.isSafeInteger(millis) || millis<=0)return '';
    const date=new Date(millis), now=new Date(), two=value=>String(value).padStart(2,'0');
    if(date.getFullYear()===now.getFullYear() && date.getMonth()===now.getMonth() && date.getDate()===now.getDate())
      return two(date.getHours())+':'+two(date.getMinutes());
    return weekdays[date.getDay()]+', '+two(date.getDate())+'.'+two(date.getMonth()+1)+'.';
  }
  /** `sendWork` (Android's record of the send lane's order): phase, channel and recipient as the lane named them. */
  function sendWorkOf(value) {
    if(!value || typeof value!=='object' || !own(sendPhases,value.phase))return null;
    return {phase:value.phase,channel:plain(value.channel,24),to:plain(value.to,60),detail:plain(value.detail,160)};
  }
  /** Which scene the snapshot asks for: the director's choice, or what an older Android sends without one. */
  function sceneChoice(state) {
    if(own(state,'scene')) {
      const scene=state.scene;
      if(!scene || typeof scene!=='object' || typeof scene.kind!=='string')return null;
      if(!own(sceneKinds,scene.kind) && !stageWindows.has(scene.kind))return null;
      return {kind:scene.kind,since:Number.isSafeInteger(scene.since)?scene.since:0};
    }
    if(state.deepClean)return {kind:'deepclean',since:0};
    if(state.mailWork)return {kind:'mail',since:0};
    return null;
  }
  /** The steps of the running order Android reported, as the cards of a lane that has no list of its own. */
  function orderCards() {
    return (currentOrders?.items ?? []).slice(0,MAX_SCENE_CARDS).map(item=>({key:'o'+item.key,title:item.label,
      meta:orderStates[item.state],tone:item.state==='DONE'?'green':item.state==='WAITING'?'yellow':
        item.state==='FAILED' || item.state==='BLOCKED'?'red':'cyan'}));
  }
  function deepCleanScene(clean) {
    const [phaseText,tone]=cleanPhases[clean.phase];
    const scene={kind:'deepclean',title:sceneKinds.deepclean,sub:clean.mode?cleanModes[clean.mode]:'',phase:clean.phase,
      phaseText,tone,cards:[],levels:[],progress:null,figures:[],notice:'',result:'',action:null,storage:null,live:tone==='active'};
    // 2 · the device storage as Android measured it before the run (and after it, once measured).
    const volume=clean.after ?? clean.before;
    if(volume)scene.storage={total:volume.total,free:volume.free,before:clean.before,after:clean.after};
    // 3 · 4 · the analysed categories, each with its measured size, scaled against the largest one.
    const largest=Math.max(1,...clean.categories.map(item=>item.bytes));
    scene.cards=clean.categories.slice(0,MAX_SCENE_CARDS).map(item=>({key:'c'+item.key,title:item.label,
      meta:filesText(item.files),value:bytesText(item.bytes),fill:item.bytes/largest,tone:'cyan'}));
    if(clean.categories.length>MAX_SCENE_CARDS)
      scene.more=(clean.categories.length-MAX_SCENE_CARDS)+' weitere Kategorien';
    // 5 · the safety levels; GREEN is what may be removed without asking, highlighted while it is cleaned.
    scene.levels=cleanLevels.filter(([key])=>clean.levels[key].count>0)
      .map(([key,label,shade])=>({key,label,shade,text:filesText(clean.levels[key].count)+' · '+bytesText(clean.levels[key].bytes),
        active:key==='GREEN' && clean.phase==='CLEAN'}));
    // 6 · 8 · real completed steps: files seen while scanning, GREEN files removed while cleaning.
    if(clean.phase==='SCAN')scene.progress={done:clean.done,total:0,label:filesText(clean.done)+' geprüft'};
    else if(clean.phase==='CLEAN' && clean.total>0)
      scene.progress={done:Math.min(clean.done,clean.total),total:clean.total,
        label:Math.min(clean.done,clean.total).toLocaleString('de-DE')+' von '+filesText(clean.total)+' entfernt'};
    // 7 · what the owner has to decide.
    if(clean.phase==='CHOICE' && clean.choice.count>0) {
      scene.notice=filesText(clean.choice.count)+' ('+bytesText(clean.choice.bytes)+') warten auf deine Auswahl. '+
        'Nichts davon wird ohne dich entfernt.';
      scene.action={label:'Auswahl öffnen',route:'storage-clean'};
    }
    // 9 · 10 · measured before/after and what was really removed – each labelled as what it is.
    if(clean.before && clean.after) {
      scene.figures.push(['FREI VORHER',bytesText(clean.before.free),'gemessen'],
        ['FREI NACHHER',bytesText(clean.after.free),'gemessen']);
      const change=clean.after.free-clean.before.free;
      scene.figures.push(['ÄNDERUNG',(change>=0?'+ ':'− ')+bytesText(Math.abs(change)),'freier Speicher, gemessen']);
    } else if(clean.before)scene.figures.push(['FREI VORHER',bytesText(clean.before.free),'gemessen']);
    if(clean.removed.files>0 || ['DONE','STOPPED'].includes(clean.phase))
      scene.figures.push(['ENTFERNT',filesText(clean.removed.files)+' · '+bytesText(clean.removed.bytes),'Summe der Dateigrößen']);
    if(clean.shared==='MISSING')
      scene.notice=(scene.notice?scene.notice+' ':'')+'Gemeinsamer Speicher nicht freigegeben – geprüft wurden nur Continuums eigene Dateien.';
    if(clean.phase==='DONE')scene.result=clean.removed.files>0?'Bereinigung abgeschlossen.':
      clean.mode && !['SAFE_CLEAN','DEEP_CLEAN','TARGETED'].includes(clean.mode)?'Analyse abgeschlossen – nichts wurde gelöscht.':
        'Abgeschlossen – nichts wurde gelöscht.';
    else if(clean.phase==='STOPPED')scene.result='Gestoppt. Was bis dahin entfernt wurde, steht oben.';
    else if(clean.phase==='FAILED')scene.result='Fehlgeschlagen. Es wird nichts weiter entfernt.';
    if(clean.phase!=='CHOICE' && ['DONE','STOPPED','FAILED'].includes(clean.phase) && clean.choice.count>0)
      scene.action={label:'Speicher & Bereinigung öffnen',route:'storage-clean'};
    return scene;
  }
  function mailScene(mail) {
    const [phaseText,tone]=mailPhases[mail.phase];
    const scene={kind:'mail',title:sceneKinds.mail,sub:'',phase:mail.phase,phaseText,tone,levels:[],figures:[],
      notice:'',result:'',action:null,storage:null,query:mail.query,live:tone==='active',
      cards:mail.cards.map(card=>({key:'m'+card.index+'\u0000'+card.sender+'\u0000'+card.subject,title:card.subject || card.sender,
        meta:card.subject?card.sender:'',value:card.date,tone:card.state==='UNREAD'?'cyan':card.state==='SELECTED'?'violet':'slate',
        mark:String(card.index).padStart(2,'0')+(card.state==='UNREAD'?' · UNGELESEN':card.state==='SELECTED'?' · AUSGEWÄHLT':'')}))};
    if(mail.found!==null)scene.figures.push(['GEFUNDEN',String(mail.found),'']);
    if(mail.read!==null)scene.figures.push(['GELESEN',String(mail.read),'']);
    if(mail.progress)scene.progress={...mail.progress,label:mail.progress.done+' von '+mail.progress.total};
    if(mail.result)scene.result=mail.result;
    else if(mail.phase==='SUMMARIZE')scene.notice='Die Zusammenfassung entsteht aus den gelesenen Nachrichten.';
    if(mail.phase==='DRAFT')scene.notice='Der Entwurf ist nicht gesendet. Gesendet wird nur nach deinem Ja.';
    return scene;
  }
  function sendScene(send) {
    const [phaseText,tone]=sendPhases[send.phase];
    const scene={kind:'send',title:sceneKinds.send,sub:send.channel,phase:send.phase,phaseText,tone,levels:[],figures:[],
      notice:'',result:send.detail,action:null,storage:null,live:tone==='active',cards:orderCards()};
    if(send.to)scene.figures.push(['AN',send.to,send.channel]);
    if(send.phase==='WAITING')scene.notice='Gesendet wird erst nach deiner Bestätigung.';
    return scene;
  }
  function webScene() {
    const activity=currentActivity?.kind==='RESEARCH'?currentActivity:null;
    const newest=[...sourceSets.values()].at(-1);
    if(!activity && !newest)return null;
    const steps=activity?.steps ?? [];
    const active=activity?.active===true;
    return {kind:'web',title:sceneKinds.web,sub:activity?.title ?? '',phase:active?'ACTIVE':'DONE',
      phaseText:active?'RECHERCHIERT':'QUELLEN GELIEFERT',tone:active?'active':'done',levels:[],figures:[],notice:'',result:'',
      action:null,storage:null,live:active,
      cards:(newest?.items ?? []).slice(0,MAX_SCENE_CARDS).map(item=>{
        let host='';try {host=new URL(item.url).hostname;}catch {}
        return {key:'s'+item.id,title:plain(item.title,90) || host,meta:host,tone:'cyan'};
      }),
      progress:steps.length?{done:steps.filter(step=>step.state==='DONE').length,total:steps.length,
        label:steps.filter(step=>step.state==='DONE').length+' von '+steps.length+' Schritten'}:null};
  }
  function sceneModel(choice,state) {
    if(!choice || stageWindows.has(choice.kind))return null;
    if(choice.kind==='deepclean') {const clean=deepCleanOf(state.deepClean);return clean && deepCleanScene(clean);}
    if(choice.kind==='mail') {const mail=mailWorkOf(state.mailWork);return mail && mailScene(mail);}
    if(choice.kind==='send') {const send=sendWorkOf(state.sendWork);return send && sendScene(send);}
    return webScene();
  }
  // F · readouts in the window's foot: only readings the snapshot carries, each absent without its reading.
  function systemBars(state) {
    const bars=[];
    if(validPercent(state.cpu)!==null)bars.push(['cpu','APP-CPU',state.cpu,state.cpu+' %']);
    if(validPercent(state.ram)!==null)bars.push(['ram','GERÄTE-RAM',state.ram,state.ram+' %']);
    const clean=deepCleanOf(state.deepClean), volume=clean?.after ?? clean?.before;
    if(volume)bars.push(['storage','SPEICHER BELEGT',Math.round((volume.total-volume.free)/volume.total*100),
      bytesText(volume.total-volume.free)+' von '+bytesText(volume.total)]);
    if(currentTactical?.battery!=null)bars.push(['battery','AKKU',currentTactical.battery,
      currentTactical.battery+' %'+(currentTactical.charging===true?' · LÄDT':'')]);
    return bars;
  }
  // The brain's four steps, lit by what Android reported: an order taken (understand), the model at work (think),
  // an input or a tool step under way (act), a check of an effect (verify). Nothing is lit by time.
  function brainSteps(state) {
    const kind=currentActivity?.active?currentActivity.kind:null, stage=currentWork?.stage;
    const thinking=computesLocally(state) || state.presence==='THINKING' || kind==='THINK' || kind==='RESEARCH' || kind==='LEARN';
    const acting=['EXECUTE','TOOL','WORKER','DEVELOP','DOCUMENT','CALCULATION'].includes(kind) ||
      stage==='DISPATCHED' || stage==='FOCUS' || currentScene?.live===true;
    const verifying=kind==='VERIFY' || stage==='OBSERVED' || stage==='VERIFIED';
    const finished=currentOrders?.outcome==='COMPLETED' || stage==='COMPLETED';
    return [['understand','VERSTEHEN',!!currentOrders || !!currentActivity || !!currentWork?.taskId],
      ['think','DENKEN',thinking],['act','HANDELN',acting],['verify','PRÜFEN',verifying || finished]];
  }
  function sceneHost() {
    let scene=$('#native-scene');
    if(!scene) {
      const core=$('.presence-orbit .presence-system>.presence-core');
      if(!core)return null;
      scene=document.createElement('section');scene.id='native-scene';scene.className='native-scene';
      const glow=part('div','native-scene-glow');glow.setAttribute('aria-hidden','true');
      const head=part('header','native-scene-head');
      const phase=part('span','native-scene-phase');phase.setAttribute('role','status');
      const close=part('button','native-scene-close','×');close.type='button';close.dataset.nativeScene='close';
      close.setAttribute('aria-label','Arbeitsszene schließen');
      head.append(part('span','native-scene-kind'),part('span','native-scene-sub'),phase,close);
      const storage=part('div','native-scene-storage');
      storage.append(part('span','native-scene-storage-label'),part('span','native-scene-gauge'),part('span','native-scene-storage-note'));
      storage.querySelector('.native-scene-gauge').append(part('i','native-scene-used'),part('i','native-scene-before'));
      const query=part('p','native-scene-query');
      const levels=part('ul','native-scene-levels');levels.setAttribute('aria-label','Sicherheitsstufen');
      const cards=part('ol','native-scene-cards');
      const more=part('p','native-scene-more');
      const progress=part('div','native-scene-progress');
      progress.append(part('span','native-scene-progress-label'),part('span','native-scene-bar'));
      progress.querySelector('.native-scene-bar').appendChild(part('i','native-scene-fill'));
      const figures=part('dl','native-scene-figures');
      const notice=part('p','native-scene-notice');
      const result=part('p','native-scene-result');result.setAttribute('role','status');
      const actions=part('div','native-scene-actions');
      const foot=part('div','native-scene-foot');
      foot.append(part('ul','native-scene-system'),part('ol','native-scene-brain'));
      foot.querySelector('.native-scene-system').setAttribute('aria-label','Systemstatus');
      foot.querySelector('.native-scene-brain').setAttribute('aria-label','KI-Gehirn');
      // Most urgent first: what is happening, what the owner must decide, what was measured; then the lists.
      scene.append(glow,head,query,progress,notice,actions,storage,figures,result,levels,cards,more,foot);
      // Directly below the avatar, in the presence block's own grid: on a phone between the avatar and the tiles.
      core.after(scene);
      enter(scene);
    }
    return scene;
  }
  /** Sets a bar's real fraction; where motion may run, it travels there from where it visibly was. */
  function setBar(node,fraction) {
    const value=Math.max(0,Math.min(1,fraction)), text='scaleX('+value.toFixed(4)+')';
    if(node.style.transform===text)return;
    const from=node.style.transform;
    node.style.transform=text;
    if(from && motionTier() && typeof node.animate==='function') {
      const glide=node.animate([{transform:from},{transform:text}],{duration:motionTier()==='full'?420:240,easing:'cubic-bezier(.16,.84,.24,1)'});
      gliding.add(glide);const landed=()=>gliding.delete(glide);glide.finished.then(landed,landed);
    }
  }
  function renderScene(state) {
    const choice=sceneChoice(state);
    const model=choice && sceneDismissed!==choice.kind+'|'+choice.since?sceneModel(choice,state):null;
    const before=currentScene;
    currentScene=model;
    let scene=$('#native-scene');
    if(!model) {
      // The tiles take their row back when the window has left.
      const gone=()=>{if(!currentScene && 'nativeScene' in document.body.dataset)delete document.body.dataset.nativeScene;};
      if(scene && scene.dataset.nativeLeaving!=='true')leave(scene,node=>{node.remove();gone();});
      else if(!scene)gone();
      if(choice===null)sceneDismissed=null;
      return;
    }
    if(scene?.dataset.nativeLeaving==='true') {leavers.delete(scene);scene.remove();scene=null;}
    const fresh=!scene || scene.dataset.kind!==model.kind;
    if(scene && fresh) {scene.remove();scene=null;}
    scene=sceneHost();
    if(!scene)return;
    if(document.body.dataset.nativeScene!==model.kind)document.body.dataset.nativeScene=model.kind;
    scene.dataset.kind=model.kind;scene.dataset.tone=model.tone;
    scene.setAttribute('aria-label','Arbeitsszene: '+model.title);
    setText(scene.querySelector('.native-scene-kind'),model.title);
    setText(scene.querySelector('.native-scene-sub'),model.sub);
    const phase=scene.querySelector('.native-scene-phase');
    if(scene.dataset.phase!==model.phase) {
      if(scene.dataset.phase && motionTier())phase.dataset.changed='true';
      scene.dataset.phase=model.phase;
    }
    setText(phase,model.phaseText);
    // The measured storage: used part of the volume; after a clean the earlier used part stays visible behind it.
    const storage=scene.querySelector('.native-scene-storage');
    storage.hidden=!model.storage;
    if(model.storage) {
      const {total,free,before:earlier,after}=model.storage;
      setText(storage.querySelector('.native-scene-storage-label'),'GERÄTESPEICHER · '+bytesText(total));
      setText(storage.querySelector('.native-scene-storage-note'),bytesText(total-free)+' belegt · '+bytesText(free)+' frei'+
        (after?' (nachher, gemessen)':' (gemessen)'));
      setBar(storage.querySelector('.native-scene-used'),(total-free)/total);
      const previous=storage.querySelector('.native-scene-before');
      previous.hidden=!(earlier && after);
      if(earlier && after)setBar(previous,(earlier.total-earlier.free)/earlier.total);
      if(!storage.dataset.measured) {storage.dataset.measured='true';enter(storage);}
    }
    const query=scene.querySelector('.native-scene-query');
    query.hidden=!model.query;
    if(model.query) {
      const text='Suche: „'+model.query+'“';
      if(query.textContent!==text && query.textContent && motionTier())query.dataset.changed='true';
      setText(query,text);
    }
    scene.dataset.searching=String(model.kind==='mail' && model.phase==='SEARCH');
    // Safety levels as chips; GREEN lights while it is being cleaned.
    const levels=scene.querySelector('.native-scene-levels'), levelKey=JSON.stringify(model.levels);
    if(feedVersions.get(levels)!==levelKey) {
      const shown=new Map([...levels.children].map(node=>[node.dataset.level,node]));
      levels.replaceChildren(...model.levels.map((level,index)=>{
        let chip=shown.get(level.key);
        if(!chip) {chip=part('li','native-scene-level');chip.dataset.level=level.key;
          chip.append(part('span','native-scene-level-name'),part('span','native-scene-level-value'));
          if(feedVersions.has(levels))enter(chip,index);}
        else if(chip.querySelector('.native-scene-level-value').textContent!==level.text && motionTier())chip.dataset.changed='true';
        chip.dataset.shade=level.shade;chip.dataset.active=String(level.active);
        setText(chip.querySelector('.native-scene-level-name'),level.label);
        setText(chip.querySelector('.native-scene-level-value'),level.text);
        return chip;
      }));
      levels.hidden=!model.levels.length;feedVersions.set(levels,levelKey);
    }
    // Cards: one per item of the lane's real list, keyed; a new one flies in, a gone one leaves.
    const cards=scene.querySelector('.native-scene-cards');
    const shown=new Map([...cards.children].filter(node=>node.dataset.nativeLeaving!=='true').map(node=>[node.dataset.key,node]));
    let arrived=0, newest=null;
    const order=[];
    for(const item of model.cards) {
      let card=shown.get(item.key);
      if(card)shown.delete(item.key);
      else {
        card=part('li','native-scene-card');card.dataset.key=item.key;
        card.append(part('span','native-scene-card-mark'),part('strong','native-scene-card-title'),part('span','native-scene-card-meta'),
          part('span','native-scene-card-value'),part('span','native-scene-card-fill'));
        card.querySelector('.native-scene-card-fill').appendChild(part('i'));
        cards.appendChild(card);
        enter(card,++arrived);
        newest=card;
      }
      card.dataset.tone=item.tone;
      setText(card.querySelector('.native-scene-card-mark'),item.mark ?? '');
      setText(card.querySelector('.native-scene-card-title'),item.title);
      setText(card.querySelector('.native-scene-card-meta'),item.meta ?? '');
      setText(card.querySelector('.native-scene-card-value'),item.value ?? '');
      const fill=card.querySelector('.native-scene-card-fill');
      fill.hidden=typeof item.fill!=='number';
      if(typeof item.fill==='number')setBar(fill.firstElementChild,item.fill);
      order.push(card);
    }
    for(const card of shown.values())leave(card,node=>node.remove());
    // Android's order of the list (by size for the storage categories) is the order on screen.
    order.forEach((card,index)=>{if(cards.children[index]!==card)cards.insertBefore(card,cards.children[index] ?? null);});
    cards.hidden=!model.cards.length;
    setText(scene.querySelector('.native-scene-more'),model.more ?? '');
    scene.querySelector('.native-scene-more').hidden=!model.more;
    const progress=scene.querySelector('.native-scene-progress');
    progress.hidden=!model.progress;
    if(model.progress) {
      setText(progress.querySelector('.native-scene-progress-label'),model.progress.label);
      const bar=progress.querySelector('.native-scene-bar');
      // Scanning has no total: a counter, no bar.
      bar.hidden=!(model.progress.total>0);
      if(model.progress.total>0)setBar(bar.firstElementChild,model.progress.done/model.progress.total);
    }
    const figures=scene.querySelector('.native-scene-figures'), figureKey=JSON.stringify(model.figures);
    if(feedVersions.get(figures)!==figureKey) {
      figures.replaceChildren(...model.figures.map(([label,value,note])=>{
        const row=part('div','native-scene-figure');
        row.append(part('dt','native-scene-figure-label',label),part('dd','native-scene-figure-value',value));
        if(note)row.appendChild(part('dd','native-scene-figure-note',note));
        if(feedVersions.has(figures) && !JSON.parse(feedVersions.get(figures)).some(entry=>entry[0]===label))enter(row);
        return row;
      }));
      figures.hidden=!model.figures.length;feedVersions.set(figures,figureKey);
    }
    setText(scene.querySelector('.native-scene-notice'),model.notice);
    scene.querySelector('.native-scene-notice').hidden=!model.notice;
    const result=scene.querySelector('.native-scene-result');
    if(result.textContent!==model.result && model.result && motionTier())result.dataset.nativeEnter='a';
    setText(result,model.result);result.hidden=!model.result;
    const actions=scene.querySelector('.native-scene-actions'), actionKey=JSON.stringify(model.action);
    if(feedVersions.get(actions)!==actionKey) {
      actions.replaceChildren(...(model.action?[(()=>{
        const button=part('button','native-scene-action',model.action.label);button.type='button';
        button.dataset.nativeScene='route';button.dataset.route=model.action.route;return button;})()]:[]));
      actions.hidden=!model.action;feedVersions.set(actions,actionKey);
    }
    // F · the foot: system readouts and the brain's steps.
    const system=scene.querySelector('.native-scene-system'), bars=systemBars(state), barKey=JSON.stringify(bars);
    if(feedVersions.get(system)!==barKey) {
      const earlier=new Map([...system.children].map(node=>[node.dataset.bar,node]));
      system.replaceChildren(...bars.map(([key,label,percent,text])=>{
        let row=earlier.get(key);
        if(!row) {row=part('li','native-scene-sysbar');row.dataset.bar=key;
          row.append(part('span','native-scene-sysbar-name',label),part('span','native-scene-sysbar-track'),part('span','native-scene-sysbar-value'));
          row.querySelector('.native-scene-sysbar-track').appendChild(part('i'));}
        setText(row.querySelector('.native-scene-sysbar-value'),text);
        setBar(row.querySelector('.native-scene-sysbar-track>i'),percent/100);
        return row;
      }));
      system.hidden=!bars.length;feedVersions.set(system,barKey);
    }
    const brain=scene.querySelector('.native-scene-brain'), steps=brainSteps(state);
    if(brain.children.length!==steps.length)brain.replaceChildren(...steps.map(([key,label])=>{
      const step=part('li','native-scene-brain-step',label);step.dataset.step=key;return step;}));
    steps.forEach(([key,,lit],index)=>{
      const step=brain.children[index];
      if(step.dataset.lit!==String(lit)) {
        if(step.dataset.lit===undefined || !lit || !motionTier())step.removeAttribute('data-changed');
        else step.dataset.changed='true';
        step.dataset.lit=String(lit);
      }
    });
    // Attention: the window when it opens and when the lane lists something new; the owner when it is over or
    // when the lane waits for an answer.
    const gazeKey=model.kind+'|'+model.phase;
    if(fresh || !before)lookAt(scene);
    else if(newest)glance(scene);
    if(gazeKey!==sceneGlanced) {
      if(sceneGlanced && (model.tone==='done' || model.tone==='waiting' || model.tone==='failed' || model.tone==='stopped'))faceOwner();
      sceneGlanced=gazeKey;
    }
  }
  // A mark is for one pass: when its animation has ended (or was cut off) it is removed, so that a later change
  // of tier cannot start it again.
  for(const type of ['animationend','animationcancel'])document.addEventListener(type,event=>{
    const node=event.target, name=event.animationName ?? '';
    if(!node?.dataset || event.pseudoElement)return;
    if(name.startsWith('native-tac-in') && node.dataset.nativeEnter)delete node.dataset.nativeEnter;
    // The pass of a changed state runs on the word inside the element that carries the mark.
    if(name.startsWith('native-tac-mark') || name.startsWith('native-scene-mark'))node.closest('[data-changed]')?.removeAttribute('data-changed');
    if(name.startsWith('native-scene-in') && node.dataset.nativeEnter)delete node.dataset.nativeEnter;
  },true);
  // Any input of the owner ends the start-up sequence; its own buttons do what they say.
  for(const type of ['pointerdown','keydown'])document.addEventListener(type,event=>{
    ownerActed=true;
    if(armingMode==='run' && !event.target?.closest?.('[data-native-tac]'))settleArming();
  },true);
  const workspace=$('#workspace');
  if(workspace)new MutationObserver(()=>{
    if(snapshot && (lines.length || snapshot.busy || snapshot.workRows?.length) && !workspace.querySelector('#native-live-panel'))renderFeed();
    if(currentDocument && !workspace.querySelector('#native-document-panel'))renderDocument();
    if(currentComputer && !workspace.querySelector('#native-computer-panel'))renderComputer();
    if(filesOpen() && !workspace.querySelector('#native-files-panel'))renderFiles();
    nativeLabels();
  }).observe(workspace,{childList:true});
  function receive(state) {
    if(!validScope(state))return;
    // The first native snapshot binds this document. A real WebView reload has
    // a fresh JS context; an old page can never adopt a different page token.
    if(page && state.pageId!==page)return;
    // Match Android Stage.bind: any new revision requires a new view identity.
    // An equal revision can only replay the same view and session.
    const initial=!page;
    const same=!initial && state.viewId===view && state.viewRevision===viewRevision &&
      state.sessionId===session;
    if(!initial && !same &&
      (state.viewRevision<=viewRevision || state.viewId===view))return;
    const changed=!same;
    // Selection events may be queued after a snapshot. Observe the actual editor
    // first, including an oversized edit that cannot be sent, before trusting its ACK.
    if(!initial)editorDraft();
    if(changed) {
      if(!initial)retainTransitionDraft(state.transitionCause);
      cancelMicPress();retireComputer();clearNativeSpeech(true);
      Object.assign(computerGate,{generation:0,sequence:-1,ended:false,binding:null});
      Object.assign(speechGate,{generation:0,sequence:-1,ended:false,utteranceId:null});
      lines=[];draftText='';sourceSets.clear();feedRevision++;clearDocument();pendingCommand=null;
      lastDraft='';latestDraftRevision=-1;clientEditRevision=0;sentEditRevision=-1;lastScene=null;
      currentActivity=null;lastPresence=null;lastActivityGaze=null;typing=false;
      currentFiles=null;filesDismissed=null;renderFiles();
      currentWork=null;workSignature='null';actionKey='';actionSequence=-1;clearMarker();computerRecord=null;
      replyLine=null;arrivedLine=null;arrivedSources=null;draftGlanced=false;lastConsent=false;stepStatesShown.clear();
      workToken++;workTurn=false;workReplied=false;workAwaitingReply=false;workSources=false;
      workSurface=null;workReturned=true;setWorkPhase('idle');
      clearTactical();sceneDismissed=null;sceneGlanced='';
      if(!initial) {
        dirtyDraft=false;
        const field=$('#command-input');if(field) {field.value='';fitComposer();}
      }
      closePanels();
    }
    page=state.pageId;view=state.viewId;viewRevision=state.viewRevision;session=state.sessionId;snapshot=state;
    renderMotionGate(state);
    const activity=activityOf(state.activity);
    // An unchanged revision keeps what is already shown, whatever else it carries.
    if(!activity || activity.revision!==currentActivity?.revision)currentActivity=activity;
    // Same rule for the file tool's answer: one revision is one list, null withdraws it.
    const files=filesOf(state.files), freshFiles=!!files && files.revision!==currentFiles?.revision;
    if(!files)currentFiles=null;
    else if(freshFiles)currentFiles=files;
    // The approved computer task: one record per change, and at most one input that was dispatched just now.
    const workBefore=workSignature, landed=acceptWork(computerWorkOf(state.computerWork));
    const revision=Number.isSafeInteger(state.draftRevision) && state.draftRevision>=0?state.draftRevision:null;
    const newerDraft=revision!==null && revision>latestDraftRevision;
    // Observing a pending revision prevents its consumed text from returning
    // after the command ACK. Native consumption publishes a later revision.
    if(revision!==null)latestDraftRevision=Math.max(latestDraftRevision,revision);
    const acknowledged=Number.isSafeInteger(state.ackEditRevision) && state.ackEditRevision===clientEditRevision;
    const draft=state.draft, field=$('#command-input');
    if(draft && typeof draft.text==='string' && acknowledged && !(initial && dirtyDraft) &&
      !pendingCommand && state.commandPending!==true && newerDraft && field &&
      draft.text.length<=MAX_DRAFT && new TextEncoder().encode(draft.text).length<=MAX_DRAFT &&
      Number.isInteger(draft.selectionStart) && Number.isInteger(draft.selectionEnd) &&
      draft.selectionStart>=0 && draft.selectionEnd>=draft.selectionStart && draft.selectionEnd<=draft.text.length) {
      field.value=draft.text;field.setSelectionRange(draft.selectionStart,draft.selectionEnd);fitComposer();
      dirtyDraft=false;lastDraft=draftKey(field.value,field.selectionStart,field.selectionEnd);
      sentEditRevision=clientEditRevision;
    } else if(initial && !dirtyDraft && field) {
      lastDraft=draftKey(field.value,field.selectionStart,field.selectionEnd);sentEditRevision=clientEditRevision;
    }
    window.ContinuumOperations?.detach();
    const showFiles=()=>{renderFiles();renderFeed();};
    // A new list opens with the original window flight, like a document does.
    if(freshFiles && !currentDocument && !computerOpen() &&
      typeof window.ContinuumWorkspaceMotion?.transition==='function')
      window.ContinuumWorkspaceMotion.transition(showFiles,{kind:'open',duration:950});
    else renderFiles();
    renderChats();renderMeters();renderFeed();renderDocument();nativeLabels();renderAppearance(state.appearance);
    renderPresence(state);renderEnergy(state);advanceWork(freshFiles);
    renderTactical(state);
    renderScene(state);
    if(landed) {
      // Shown where it happened for a short while; the list below the monitor keeps it.
      marker={sequence:landed.sequence};
      clearTimeout(markerTimer);
      markerTimer=setTimeout(()=>{marker=null;markerTimer=0;renderComputer();},MARKER_MS);
    }
    if(workSignature!==workBefore)renderComputer();
    if(landed && computerOpen())glance($('#native-computer-panel'));
    // Android's consent dialog concerns the owner: face forward once when it opens.
    const consent=currentWork?.consentPending===true;
    if(consent!==lastConsent) {lastConsent=consent;if(consent)faceOwner();}
    if(freshFiles && filesOpen())lookAt($('#native-files-panel'));
    // Each real step of a running turn draws one glance to where it is shown.
    if(currentActivity?.active===true && currentActivity.revision!==lastActivityGaze) {
      lastActivityGaze=currentActivity.revision;
      if(sceneOf(state)==='working' && !freshFiles)
        lookAt(surfaceElement() || $('[data-presence-left] .native-activity'));
    }
    const presence=presenceOf(state), scene=sceneOf(state);
    if(scene!==lastScene) {
      lastScene=scene;document.body.dataset.state=scene;
      window.ContinuumScene?.setState(scene);
      document.querySelectorAll('[data-scene]').forEach(button=>{
        button.classList.toggle('active',button.dataset.scene===scene);
        button.setAttribute('aria-current',button.dataset.scene===scene?'step':'false');
        button.setAttribute('aria-disabled','true');
      });
    }
    syncOrb(presence?.orb || scene,scene);
    const orb=$('#orb-status');
    const orbText=presence?presence.status:state.speaking?'SPRICHT':state.listening?'HÖRT ZU':state.busy?'ARBEITET':'BEREIT';
    if(orb && orb.textContent!==orbText)orb.textContent=orbText;
    const caption=$('#scene-caption');
    if(caption)caption.textContent=state.status||'Bereit';
    // Typing can precede the first Android snapshot. Bind that existing edit now
    // so an immediate return to the classic view retains it without another key.
    if(dirtyDraft)syncDraft();
  }
  // The answer while the on-device model is still writing it. One row at the end of the feed, labelled as a
  // draft, text-only updates (no rebuild of the feed); the checked answer (message) or the end of the turn removes it.
  let draftText='';
  function renderDraft(feed=document.querySelector('#native-live-panel .native-transcript')) {
    if(!feed)return;
    let row=feed.querySelector('.native-draft');
    if(!draftText) {row?.remove();return;}
    const pinned=feed.scrollHeight-feed.scrollTop-feed.clientHeight<40;
    if(!row) {
      row=document.createElement('article');
      row.className='native-chat-line native-assistant native-draft';
      const label=document.createElement('small');label.textContent='CONTINUUM · SCHREIBT …';
      row.append(label,document.createElement('p'));
    }
    if(feed.lastElementChild!==row)feed.appendChild(row);
    const content=row.querySelector('p');
    if(content.textContent!==draftText)content.textContent=draftText;
    if(pinned)feed.scrollTop=feed.scrollHeight;
  }
  function draft(data) {
    if(!currentScope(data)||typeof data.text!=='string')return;
    const text=snapshot?.busy===true?boundedMessage(data.text).slice(-MAX_ANSWER_DRAFT):'';
    if(text===draftText)return;
    draftText=text;
    if(text && !document.querySelector('#native-live-panel'))renderFeed();else {renderDraft();renderWorkBadge();}
    if(!text) {draftGlanced=false;return;}
    // The answer starts to appear: one look at it for this turn, where it really is on screen.
    if(draftGlanced)return;
    draftGlanced=true;
    const row=document.querySelector('#workspace.native-work-active #native-live-panel .native-draft');
    if(row)glance(row);
  }
  function boundedMessage(value) {
    const text=typeof value==='string'?value:'';
    const bounded=text.slice(0,MAX_MESSAGE);
    return /[\uD800-\uDBFF]$/.test(bounded)?bounded.slice(0,-1):bounded;
  }
  function transcript(data) {
    if(!currentScope(data)||!Array.isArray(data.entries))return;
    const rows=data.entries.filter(e=>e&&typeof e.user==='boolean'&&typeof e.text==='string')
      .map(e=>({user:e.user,text:boundedMessage(e.text)}));
    lines=(data.append===true?lines.concat(rows):rows).slice(-60);pruneSources();feedRevision++;
    if(data.append!==true)draftText='';
    renderFeed();
  }
  function message(line) {
    if(!currentScope(line)||typeof line.user!=='boolean'||typeof line.text!=='string')return;
    const row={user:line.user,text:boundedMessage(line.text)};
    lines.push(row);feedRevision++;
    if(lines.length>60)lines.shift();pruneSources();
    if(!line.user) {draftText='';draftGlanced=false;replyLine=arrivedLine=row;}
    renderFeed();
    if(!line.user)replyDelivered();
  }
  function closePanels() {
    document.body.classList.remove('chats-open','instruments-open');
    document.querySelectorAll('[data-action="toggle-chats"],[data-action="toggle-instruments"]')
      .forEach(button=>button.setAttribute('aria-expanded','false'));
  }
  function section(name,focusComposer=false) {
    if(!routes.has(name))return;
    if(name==='stop') {retireComputer('stopped');clearNativeSpeech(true);}
    else if(name==='device' && currentComputer) {
      computerVisible=true;renderComputer();scheduleVisibilityCheck();
    } else if(computerOpen() && name!=='voice-input' && name!=='voice-input-long-press') {
      coverComputer();renderDocument();renderFeed();
    }
    closePanels();
    document.body.dataset.nativeSection=name;
    const original=name==='home'||name==='chat'?'conversation':name==='device'?'computer':name;
    if(document.querySelector('.nav-button[data-section="'+original+'"]'))
      document.querySelectorAll('.nav-button[data-section]').forEach(button=>{
        const selected=button.dataset.section===original;
        button.classList.toggle('active',selected);button.setAttribute('aria-current',selected?'page':'false');
      });
    if(name==='chat' && focusComposer && document.visibilityState==='visible')
      $('#command-input')?.focus({preventScroll:true});
  }
  function closeProjection() {
    if(computerOpen()) {retireComputer('stopped');send('route','stop');}
    else if(currentDocument)clearDocument();
    else if(filesOpen())dismissFiles();
    // The record of an ended live window is closed like a window; nothing else is open then.
    else if(computerRecord) {computerRecord=null;renderFeed();}
    else clearDocument();
  }
  function cancelMicPress(suppress=true) {
    if(!micPress)return;
    clearTimeout(micPress.timer);micPress=null;
    if(suppress)suppressMicClick=true;
  }
  const micButton=$('#listen-button');
  micButton?.addEventListener('pointerdown',e=>{
    if(e.button!==0 || e.isPrimary===false)return;
    cancelMicPress(false);suppressMicClick=false;
    const press={pointerId:e.pointerId,x:e.clientX,y:e.clientY,context:scope(),fired:false,timer:0};
    micPress=press;
    const duration=Number.isInteger(snapshot?.longPressTimeoutMs)?
      Math.max(200,Math.min(2000,snapshot.longPressTimeoutMs)):500;
    press.timer=setTimeout(()=>{
      if(micPress!==press || !currentScope(press.context) || document.visibilityState!=='visible')return;
      press.fired=true;suppressMicClick=true;
      send('route','voice-input-long-press');
    },duration);
  });
  micButton?.addEventListener('pointermove',e=>{
    if(micPress?.pointerId===e.pointerId &&
      Math.hypot(e.clientX-micPress.x,e.clientY-micPress.y)>10)cancelMicPress();
  });
  micButton?.addEventListener('pointerup',e=>{
    if(micPress?.pointerId!==e.pointerId)return;
    const fired=micPress.fired;cancelMicPress(fired);
    if(fired)e.preventDefault();
  });
  for(const event of ['pointercancel','pointerleave'])micButton?.addEventListener(event,()=>cancelMicPress());
  micButton?.addEventListener('contextmenu',e=>e.preventDefault());
  micButton?.addEventListener('keydown',e=>{
    if(e.key!=='ContextMenu' && !(e.key==='F10' && e.shiftKey))return;
    e.preventDefault();e.stopImmediatePropagation();cancelMicPress(false);
    send('route','voice-input-long-press');
  });
  window.addEventListener('blur',()=>cancelMicPress());
  window.ContinuumNative=Object.freeze({receive,transcript,message,draft,
    section:data=>{if(currentScope(data))section(data.name,data.focusComposer===true);},document:documentOpened,
    clearDocument:data=>{if(currentScope(data))clearDocument();},commandResult,computer,speech,sources,
    // Measurement only: 'start' | 'read' | 'stop' of the frame counter above; anything else answers null.
    frameProbe:command=>frameGovernor.probe(command)});
  document.addEventListener('click',e=>{
    if(e.target.closest('#listen-button') && suppressMicClick) {
      suppressMicClick=false;e.preventDefault();e.stopImmediatePropagation();return;
    }
    const sceneControl=e.target.closest('[data-native-scene]');
    if(sceneControl) {
      e.preventDefault();e.stopImmediatePropagation();
      if(sceneControl.dataset.nativeScene==='close') {
        // Closed by the owner: this scene stays away until Android names another one.
        const choice=snapshot && sceneChoice(snapshot);
        if(choice) {sceneDismissed=choice.kind+'|'+choice.since;renderScene(snapshot);faceOwner();}
      } else if(sceneControl.dataset.nativeScene==='route' && sceneControl.dataset.route==='storage-clean')
        send('route','storage-clean');
      return;
    }
    const tac=e.target.closest('[data-native-tac]');
    if(tac) {
      e.preventDefault();e.stopImmediatePropagation();
      if(tac.dataset.nativeTac==='orders') {
        // Folds every step out to its full label and back.
        const rail=tac.closest('#native-order-rail');
        reflow(()=>{rail.dataset.expanded=String(rail.dataset.expanded!=='true');});
        tac.setAttribute('aria-expanded',rail.dataset.expanded);
      } else if(snapshot && currentChecks.length) {
        if(armingMode==='run')settleArming();
        else {armingMode=tac.dataset.nativeTac==='systems' && !armingMode?'list':null;renderArming();renderStrip(snapshot);}
      }
      return;
    }
    const source=e.target.closest('[data-native-source]');
    if(source) {
      e.preventDefault();e.stopImmediatePropagation();
      const id=source.dataset.nativeSource;
      if([...sourceSets.values()].some(set=>set.items.some(item=>item.id===id)))send('open-source',id);
      return;
    }
    const pdf=e.target.closest('[data-native-pdf]');
    if(pdf){
      e.preventDefault();e.stopImmediatePropagation();
      if(pdf.dataset.nativePdf==='files')send('route','files');
      else if(pdf.dataset.nativePdf==='close')clearDocument();
      else if(pdf.dataset.nativePdf==='draft' && currentScope(currentDocument) &&
        currentDocument.canDraft===true &&
        snapshot?.busy!==true && snapshot?.commandPending!==true) {
        send('document-draft',currentDocument.documentId);
      }
      return;
    }
    const own=e.target.closest('[data-native-chat]');
    if(own) {
      e.preventDefault();e.stopImmediatePropagation();
      closePanels();
      send('select-chat',own.dataset.nativeChat);return;
    }
    const fake=e.target.closest('[data-chat]');
    if(fake) {
      e.preventDefault();e.stopImmediatePropagation();
      send('route','chats');return;
    }
    const nav=e.target.closest('button[data-section]');
    if(nav) {
      e.preventDefault();e.stopImmediatePropagation();
      const value=nav.dataset.section==='conversation'?'chat':
        nav.dataset.section==='computer'?'device':nav.dataset.section;
      if(routes.has(value) && send('route',value))section(value,true);return;
    }
    const action=e.target.closest('[data-action]');
    const value=action&&actions[action.dataset.action];
    if(value) {
      e.preventDefault();e.stopImmediatePropagation();
      if(send('route',value))section(value,true);
      return;
    }
    if(action && action.dataset.action==='close') {
      e.preventDefault();e.stopImmediatePropagation();closeProjection();return;
    }
    if((action && !layoutActions.has(action.dataset.action)) ||
      e.target.closest('[data-scene],[data-phase],[data-source],[data-op-action],[data-op-view],[data-op-source],[data-op-step],[data-op-paragraph]')) {
      // Demo handlers are never allowed to fabricate a task, source or result in
      // the Android session. Pure layout controls keep their original behavior.
      e.preventDefault();e.stopImmediatePropagation();
    }
  },true);
  document.addEventListener('keydown',e=>{
    if(e.key==='Escape' && !document.body.classList.contains('chats-open') &&
      !document.body.classList.contains('instruments-open')) {
      e.preventDefault();e.stopImmediatePropagation();closeProjection();
    }
  },true);
  $('#command-form')?.addEventListener('submit',e=>{
    e.preventDefault();e.stopImmediatePropagation();
    const field=$('#command-input'), text=field?.value?.trim()||'';
    if(!text)return;
    const error=inputError(text);
    if(error){notice(error);return;}
    if(!validScope(scope())){notice('Die Android-Verbindung startet. Dein Entwurf bleibt erhalten.');return;}
    if(pendingCommand || snapshot?.commandPending===true){notice('Die Eingabe wird übernommen. Dein Entwurf bleibt erhalten.');return;}
    const draft=editorDraft();if(!draft)return;
    pendingCommand={id:'command-'+(++requestNumber),...scope(),draft:field.value,
      clientEditRevision:draft.clientEditRevision};
    if(!send('command',text,{id:pendingCommand.id}))pendingCommand=null;
  },true);
  const input=$('#command-input');
  input?.addEventListener('input',()=>{dirtyDraft=true;fitComposer();syncDraft();ownerTyping();});
  input?.addEventListener('blur',()=>{typing=false;});
  for(const event of ['select','keyup','click','blur','change'])input?.addEventListener(event,syncDraft);
  window.addEventListener('load',stopDemoTelemetry,{once:true});
  window.addEventListener('load',mountSettingsControls,{once:true});
  window.addEventListener('resize',fitComposer);
  window.addEventListener('resize',scheduleVisibilityCheck);
  document.addEventListener('scroll',scheduleVisibilityCheck,true);
  window.addEventListener('scroll',scheduleVisibilityCheck);
  document.addEventListener('visibilitychange',()=>{
    document.body.classList.toggle('native-light-paused',document.visibilityState!=='visible');
    if(document.visibilityState!=='visible') {cancelMicPress();retireComputer('ended');clearNativeSpeech(true);}
    else scheduleVisibilityCheck();
  });
  window.addEventListener('pagehide',()=>{
    document.body.classList.add('native-light-paused');
    cancelMicPress();retireComputer('ended');clearNativeSpeech(true);
  });
  window.addEventListener('pageshow',()=>{
    document.body.classList.toggle('native-light-paused',document.visibilityState!=='visible');
  });
  new MutationObserver(scheduleVisibilityCheck).observe(document.body,{attributes:true,attributeFilter:['class','style']});
  const badge=$('.preview-badge');
  mountSettingsControls();
  labelWithIcons(badge,'ANDROID · LOKAL');
  const note=$('#workspace-footnote');
  if(note)note.textContent='Verbindung wird hergestellt …';
})();

