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
    if(request===null) return {set() {}, limit:() => null};
    let limit = 30, waiting = new Map(), serial = 0, armed = false, last = -Infinity;
    const pump = time => {
      armed = false;
      // Without a real frame time (no browser clock) nothing is held back.
      const timed = typeof time==='number' && Number.isFinite(time);
      if(!timed || limit===null || time-last >= 1000/limit-2) {
        if(timed) last = time;
        const due = waiting;
        waiting = new Map();
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
    return {set(value) {limit = value;}, limit:() => limit};
  })();
  const routes = new Set(['home','chat','chats','new-chat','files','research',
    'agents','memory','device','settings','settings-voice','settings-model',
    'settings-internet','settings-appearance','voice-input','voice-input-long-press','stop']);
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
  const DEFAULT_COMMAND_LIMIT = 11500, MAX_MESSAGE = 68096, MAX_DRAFT = 262144, HISTORY = 36;
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
  const fileKinds = Object.freeze({SEARCH:'DATEISUCHE',FOLDER:'ORDNER'});
  const MAX_FILES = 12, MAX_FILE_NAME = 96;
  // One real turn on the stage: activation → opening → projected → settling → idle.
  let workPhase = 'idle', workTurn = false, workReplied = false, workAwaitingReply = false;
  let workSurface = null, workSources = false, workToken = 0, workReturned = true;
  let currentFiles = null, filesDismissed = null, typing = false, lastActivityGaze = null;
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
    target.dispatchEvent(new CustomEvent('continuum:gaze',{bubbles:true,detail:{target}}));
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
    for(const layer of [$('.native-orb-light'),$('.native-room-light')]) {
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
  }
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
      panel.setAttribute('aria-label','Dateifenster mit der Antwort des Android-Dateiwerkzeugs');
      const head=document.createElement('div');head.className='native-files-heading';
      const kicker=document.createElement('small');kicker.className='native-files-kicker';
      const title=document.createElement('h2');title.className='native-files-title';
      const count=document.createElement('p');count.className='native-files-count';count.setAttribute('role','status');
      head.append(kicker,title,count);
      const list=document.createElement('ol');list.className='native-files-list';
      const note=document.createElement('p');note.className='native-files-note';
      note.textContent='Nur Dateinamen aus der Antwort des Android-Werkzeugs. Kein Inhalt wurde geöffnet.';
      panel.append(head,list,note);workspace.appendChild(panel);
    }
    if(feedVersions.get(panel)!==files.revision) {
      panel.dataset.kind=files.kind.toLowerCase();
      panel.querySelector('.native-files-kicker').textContent=fileKinds[files.kind]+' · ANDROID-WERKZEUG';
      panel.querySelector('.native-files-title').textContent=files.label?
        (files.kind==='SEARCH'?'„'+files.label+'“':files.label):(files.kind==='SEARCH'?'Dateisuche':'Ordner');
      const unit=files.kind==='SEARCH'?(files.total===1?'Datei gefunden':'Dateien gefunden'):
        (files.total===1?'Eintrag im Ordner':'Einträge im Ordner');
      panel.querySelector('.native-files-count').textContent=
        (files.total===0?(files.kind==='SEARCH'?'Keine Datei gefunden':'Keine Einträge im Ordner'):
          (files.more?'Mindestens ':'')+files.total+' '+unit)+
        (files.total>files.names.length && files.names.length?' · '+files.names.length+' angezeigt':'')+
        (files.more?' · Suchgrenze erreicht':'');
      panel.querySelector('.native-files-list').replaceChildren(...files.names.map(name=>{
        const item=document.createElement('li');
        const glyph=document.createElement('span');glyph.className='native-file-glyph';glyph.setAttribute('aria-hidden','true');
        const label=document.createElement('span');label.className='native-file-name';label.textContent=name;
        item.append(glyph,label);
        const dot=name.lastIndexOf('.'), kind=dot>0?name.slice(dot+1):'';
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
        delete left.dataset.activityKind;delete left.dataset.activityActive;steps?.remove();
      } else {
        left.dataset.activityKind=activity.kind.toLowerCase();
        left.dataset.activityActive=String(activity.active);
        if(!activity.steps.length)steps?.remove();
        else {
          if(!steps) {
            steps=document.createElement('ol');steps.className='native-activity-steps';
            steps.setAttribute('aria-label','Arbeitsschritte');left.querySelector('p').after(steps);
          }
          // One real revision is rendered once; periodic snapshots keep its nodes.
          if(feedVersions.get(steps)!==activity.revision) {
            steps.replaceChildren(...activity.steps.map(step=>{
              const item=document.createElement('li');item.dataset.state=step.state.toLowerCase();
              const name=document.createElement('span');name.className='native-step-label';
              name.textContent=step.label;
              const state=document.createElement('span');state.className='native-step-state';
              state.textContent=stepStates[step.state];
              item.append(name,state);return item;
            }));
            feedVersions.set(steps,activity.revision);
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
      panel.innerHTML='<div class="projection-lens" aria-hidden="true"></div><div class="op-console-head"><div><span class="op-kicker">PROJIZIERTER COMPUTER</span><h2>Dein Gerätebild.</h2></div><span class="op-mode-label real"></span></div><div class="op-controls"><button type="button" class="secondary-button" data-action="stop-computer">Computer stoppen</button></div><div class="op-browser-frame"><div class="op-browser-chrome"><span class="op-window-id">CONTINUUM · ANDROID</span></div><div class="op-browser-body native-computer-viewport"><div class="op-waiting"><h3>Warte auf ein aktuelles Bild.</h3><p class="native-computer-waiting"></p></div></div><div class="op-browser-status"><span class="status-dot"></span><span class="native-computer-status" role="status"></span></div></div>';
      workspace.appendChild(panel);
    }
    const fresh=displayedFrame && performance.now()<displayedFrame.expiresAt;
    panel.querySelector('.op-mode-label').textContent=fresh?'GERÄTEBILD':'WARTET';
    const reason=currentComputer.reason || 'Noch kein aktuelles Gerätebild verfügbar.';
    panel.querySelector('.native-computer-status').textContent=reason;
    panel.querySelector('.native-computer-waiting').textContent=reason;
    panel.querySelector('.op-waiting').hidden=!!fresh;
    if(computerOpen()) {
      workspace.classList.remove('native-document-active','native-work-active');
      if(fresh && displayedFrame.image.parentElement!==panel.querySelector('.native-computer-viewport'))
        panel.querySelector('.native-computer-viewport').appendChild(displayedFrame.image);
    }
    nativeLabels();
  }
  function retireComputer() {
    computerGate.ended=true;clearComputerFrames();currentComputer=null;computerVisible=false;
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
    if(!data.active) {retireComputer();return;}
    if(nextGeneration) {clearComputerFrames();computerVisible=true;}
    currentComputer={sessionId:data.sessionId,generation:data.generation,sequence:data.sequence,
      active:true,status:data.status,reason:typeof data.reason==='string'?data.reason.slice(0,240):''};
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
      paints:[],expiry:0,paintQueued:false,acknowledged:false,hiddenNotified:false};
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
    const workspace=$('#workspace');
    if (!workspace) return;
    const show=lines.length>0 || sourceSets.size>0 || snapshot.busy===true ||
      (Array.isArray(snapshot.workRows) && snapshot.workRows.length>0);
    workspace.classList.toggle('native-work-active',show && !currentDocument && !computerOpen() &&
      !filesOpen());
    if (!show) {workspace.querySelector('#native-live-panel')?.remove();renderNativeInstruments();return;}
    let panel=workspace.querySelector('#native-live-panel');
    if (!panel) {
      panel=document.createElement('section');
      panel.id='native-live-panel';
      panel.className='projected-computer native-live-panel';
      panel.innerHTML='<div class="op-console-head"><div><span class="op-kicker">CONTINUUM · ANDROID</span><h2>Dein echter Arbeitsraum.</h2></div><span class="op-mode-label">LOKALE DATEN</span></div><div class="native-transcript" aria-live="polite"></div><div class="native-lane-state"></div>';
      workspace.appendChild(panel);
    }
    panel.querySelector('h2').textContent=snapshot.viewKind==='BOT'?String(snapshot.viewLabel||'Helfer').slice(0,96):'Dein echter Arbeitsraum.';
    const feed=panel.querySelector('.native-transcript');
    const signature=feedRevision+'|'+JSON.stringify(snapshot.workRows);
    if (feedVersions.get(feed)!==signature) {
      const entries=lines.slice(-60).map(line=>{
      const item=document.createElement('article');
      item.className='native-chat-line '+(line.user?'native-owner':'native-assistant');
      const label=document.createElement('small');label.textContent=line.user?'DU':
        snapshot.viewKind==='BOT'?String(snapshot.viewLabel||'Helfer').slice(0,96):'CONTINUUM';
      const content=document.createElement('p');content.textContent=boundedMessage(line.text);
      item.append(label,content);
      for(const set of sourceSets.values())if(set.anchor===line)item.appendChild(sourceCard(set));
      return item;
    });
    for(const set of sourceSets.values())if(set.anchor===null)entries.push(sourceCard(set));
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
    panel.querySelector('.native-lane-state').textContent=snapshot.busy?
      'AUFTRAG WIRD BEARBEITET · STATUS VON ANDROID':
      (snapshot.status||'BEREIT · LOKALER GESPRÄCHSVERLAUF');
    const caption=$('#workspace-footnote');
    if(caption && !currentDocument && !computerOpen() && !filesOpen())
      caption.textContent='Dein Gespräch und deine Arbeitsschritte';
    renderNativeInstruments();
  }

  function sourceCard(set) {
    const card=document.createElement('section');card.className='native-source-card';
    const heading=document.createElement('small');heading.textContent='QUELLEN';card.appendChild(heading);
    for(const item of set.items) {
      const button=document.createElement('button');button.type='button';button.className='native-source-link';
      button.dataset.nativeSource=item.id;
      button.setAttribute('aria-label','Quelle öffnen: '+item.title);
      const title=document.createElement('strong');title.textContent=item.title;
      const url=document.createElement('span');url.textContent=item.url;
      button.append(title,url);card.appendChild(button);
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
    feedRevision++;renderFeed();advanceWork();
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
    set('#workspace-meta',computerOpen()?'COMPUTER':currentDocument?'PDF':filesOpen()?'DATEIEN':
      snapshot.busy?'AUFTRAG AKTIV':'LOKAL');
    set('#workspace-footnote',computerOpen()?(currentComputer.reason||'Gerätebild · Android'):
      currentDocument?'PDF · '+currentDocument.name:
      filesOpen()?'Dateinamen aus dem Android-Werkzeug':
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
      lines=[];sourceSets.clear();feedRevision++;clearDocument();pendingCommand=null;
      lastDraft='';latestDraftRevision=-1;clientEditRevision=0;sentEditRevision=-1;lastScene=null;
      currentActivity=null;lastPresence=null;lastActivityGaze=null;typing=false;
      currentFiles=null;filesDismissed=null;renderFiles();
      workToken++;workTurn=false;workReplied=false;workAwaitingReply=false;workSources=false;
      workSurface=null;workReturned=true;setWorkPhase('idle');
      if(!initial) {
        dirtyDraft=false;
        const field=$('#command-input');if(field) {field.value='';fitComposer();}
      }
      closePanels();
    }
    page=state.pageId;view=state.viewId;viewRevision=state.viewRevision;session=state.sessionId;snapshot=state;
    const activity=activityOf(state.activity);
    // An unchanged revision keeps what is already shown, whatever else it carries.
    if(!activity || activity.revision!==currentActivity?.revision)currentActivity=activity;
    // Same rule for the file tool's answer: one revision is one list, null withdraws it.
    const files=filesOf(state.files), freshFiles=!!files && files.revision!==currentFiles?.revision;
    if(!files)currentFiles=null;
    else if(freshFiles)currentFiles=files;
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
    renderFeed();
  }
  function message(line) {
    if(!currentScope(line)||typeof line.user!=='boolean'||typeof line.text!=='string')return;
    lines.push({user:line.user,text:boundedMessage(line.text)});feedRevision++;
    if(lines.length>60)lines.shift();pruneSources();
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
    if(name==='stop') {retireComputer();clearNativeSpeech(true);}
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
    if(computerOpen()) {retireComputer();send('route','stop');}
    else if(currentDocument || !filesOpen())clearDocument();
    else dismissFiles();
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
  window.ContinuumNative=Object.freeze({receive,transcript,message,
    section:data=>{if(currentScope(data))section(data.name,data.focusComposer===true);},document:documentOpened,
    clearDocument:data=>{if(currentScope(data))clearDocument();},commandResult,computer,speech,sources});
  document.addEventListener('click',e=>{
    if(e.target.closest('#listen-button') && suppressMicClick) {
      suppressMicClick=false;e.preventDefault();e.stopImmediatePropagation();return;
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
    if(document.visibilityState!=='visible') {cancelMicPress();retireComputer();clearNativeSpeech(true);}
    else scheduleVisibilityCheck();
  });
  window.addEventListener('pagehide',()=>{
    document.body.classList.add('native-light-paused');
    cancelMicPress();retireComputer();clearNativeSpeech(true);
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

