/* Android-only binding. Source PR #850 HTML, CSS, JS and 3D assets stay unchanged. */
(() => {
  'use strict';
  if (window.ContinuumNative) return;
  const routes = new Set(['home','chat','chats','new-chat','files','research',
    'agents','memory','device','settings','voice-input','stop']);
  const actions = Object.freeze({home:'home',settings:'settings',agents:'agents',
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
  const computerStates = new Set(['NO_SOURCE','WAITING','LIVE','PAUSED','COVERED','ENDED']);
  const visemes = new Set(['SIL','PP','FF','TH','DD','KK','CH','SS','NN','RR','AA','E','I','O','U']);
  const speechSources = new Set(['PCM_ENVELOPE','WORD_RANGE_FALLBACK','TEXT_PACE_FALLBACK']);
  const computerGate = {generation:0,sequence:-1,ended:false,binding:null};
  const speechGate = {generation:0,sequence:-1,ended:false,utteranceId:null};
  let currentComputer = null, computerVisible = false, displayedFrame = null, pendingFrame = null;
  let visibilityCheck = 0, speechExpiry = 0, currentSpeech = null;
  const readings = [], nativeMeters = new Map();
  const feedVersions = new WeakMap();
  function send(kind,value,extra = {}) {
    if (kind === 'route' && !routes.has(value)) return false;
    const query = new URLSearchParams({value,...extra});
    window.location.assign('continuum-native://action/' + kind +
      '?' + query.toString());
    return true;
  }
  function notice(text) {
    const target = $('#toast');
    if (target) {target.textContent=text;target.classList.add('show');}
  }
  function labelWithIcons(node,label) {
    if(!node)return;
    const texts=[...node.childNodes].filter(child=>child.nodeType===3 && child.textContent.trim());
    if(texts.length) {
      if(texts[0].textContent.trim()!==label)texts[0].textContent=' '+label+' ';
      texts.slice(1).forEach(child=>{child.textContent='';});
    } else node.appendChild(document.createTextNode(' '+label));
  }
  function fitComposer() {
    const field=$('#command-input');
    if(!field)return;
    const style=window.getComputedStyle(field);
    const min=parseFloat(style.minHeight)||29, max=parseFloat(style.maxHeight)||116;
    field.style.height='0px';
    field.style.height=Math.max(min,Math.min(max,field.scrollHeight))+'px';
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
    const field = $('#command-input');
    if (!field || !chatId.test(session)) return;
    const value=field.value;
    // Oversized edits stay in the page. Never silently truncate them to make a
    // different command appear accepted by the existing native context budget.
    if (value.length>MAX_DRAFT || new TextEncoder().encode(value).length>MAX_DRAFT) {
      notice('Der Entwurf ist zu groß für die Übergabe. Bitte kürze ihn vor dem Ansichtswechsel.');
      return;
    }
    const start=field.selectionStart ?? value.length, end=field.selectionEnd ?? start;
    const key=JSON.stringify([session,value,start,end]);
    if (key===lastDraft) return;
    lastDraft=key;
    send('draft',value,{sessionId:session,selectionStart:String(start),selectionEnd:String(end)});
  }
  function commandResult(result) {
    if (!result || !pendingCommand || result.id!==pendingCommand.id ||
      result.sessionId!==pendingCommand.sessionId || result.sessionId!==session) return;
    const request=pendingCommand;pendingCommand=null;
    if (result.accepted===true) {
      const field=$('#command-input');
      if (field && field.value===request.draft) {
        field.value='';dirtyDraft=false;fitComposer();syncDraft();
      }
      clearDocument();
      $('#toast')?.classList.remove('show');
      return;
    }
    const reasons={busy:'Ein Auftrag läuft noch. Dein Entwurf bleibt erhalten.',
      'stale-session':'Das Gespräch wurde gewechselt. Dein Entwurf wurde nicht gesendet.',
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
    return data && data.sessionId===session && chatId.test(session) && typeof data.active==='boolean' &&
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
    send('frame-hidden','',{sessionId:data.sessionId,generation:String(data.generation),sequence:String(data.sequence)});
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
    renderComputer();renderDocument();renderFeed();
  }
  function coverComputer() {
    computerVisible=false;clearComputerFrames();renderComputer();
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
          send('frame-presented','',{sessionId:data.sessionId,generation:String(data.generation),sequence:String(data.sequence)});
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
      invalidateFrame(displayedFrame);disposeFrame(displayedFrame);
      displayedFrame=frame;pendingFrame=null;
      image.onload=null;image.onerror=null;renderComputer();queueFrameAcknowledgement(frame);
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
    const avatar=$('.avatar-crop');
    if(avatar) {avatar.dataset.speechActive='true';avatar.dataset.speechSource=data.source||'UNKNOWN';}
    speechExpiry=setTimeout(()=>{if(currentSpeech===data)clearNativeSpeech();},data.durationMs);
  }
  function renderFeed() {
    if (!snapshot) return;
    const workspace=$('#workspace');
    if (!workspace) return;
    const show=lines.length>0 || snapshot.busy===true ||
      (Array.isArray(snapshot.workRows) && snapshot.workRows.length>0);
    workspace.classList.toggle('native-work-active',show && !currentDocument && !computerOpen());
    if (!show) {workspace.querySelector('#native-live-panel')?.remove();return;}
    let panel=workspace.querySelector('#native-live-panel');
    if (!panel) {
      panel=document.createElement('section');
      panel.id='native-live-panel';
      panel.className='projected-computer native-live-panel';
      panel.innerHTML='<div class="op-console-head"><div><span class="op-kicker">CONTINUUM · ANDROID</span><h2>Dein echter Arbeitsraum.</h2></div><span class="op-mode-label">LOKALE DATEN</span></div><div class="native-transcript" aria-live="polite"></div><div class="native-lane-state"></div>';
      workspace.appendChild(panel);
    }
    const feed=panel.querySelector('.native-transcript');
    const signature=feedRevision+'|'+JSON.stringify(snapshot.workRows);
    if (feedVersions.get(feed)!==signature) {
      const entries=lines.slice(-60).map(line=>{
      const item=document.createElement('article');
      item.className='native-chat-line '+(line.user?'native-owner':'native-assistant');
      const label=document.createElement('small');label.textContent=line.user?'DU':'CONTINUUM';
      const content=document.createElement('p');content.textContent=boundedMessage(line.text);
      item.append(label,content);return item;
    });
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
    if(caption && !currentDocument && !computerOpen())caption.textContent='Dein Gespräch und deine Arbeitsschritte';
  }

  /**
   * Only the document already opened via Android's read-only picker is shown.
   * All metadata/text are inserted using textContent, never trusted as HTML.
   * There is no data transfer, filesystem access or preview-side PDF parser.
   */
  function renderDocument() {
    const workspace=$('#workspace');
    if (!workspace || !currentDocument || currentDocument.sessionId !== session) return;
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
      for(const [id,text] of [['files','ANDERE PDF ÖFFNEN'],['close','DOKUMENT SCHLIESSEN']]) {
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
      panel.append(top,buttons,detail);
      workspace.appendChild(panel);
    }
    const doc=currentDocument;
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
    currentDocument=null;
    const workspace=$('#workspace');
    workspace?.querySelector('#native-document-panel')?.remove();
    workspace?.classList.remove('native-document-active');
    renderFeed();renderComputer();
  }
  function documentOpened(document) {
    if(!snapshot || !document || document.sessionId!==session) return;
    if(typeof document.name!=='string' || document.name.length>120 ||
       !Number.isInteger(document.pageCount) || document.pageCount < 0) return;
    const set = () => {
      coverComputer();
      currentDocument=document;
      renderDocument();
    };
    // Reuse the ORIGINAL 950ms interruptible flight/FLIP controller from PR #850.
    // No second CSS animation or stale timer can overwrite a newer native document.
    if (typeof window.ContinuumWorkspaceMotion?.transition === 'function') {
      window.ContinuumWorkspaceMotion.transition(set,{kind:'open',duration:950});
    } else set();
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
    set('#workspace-meta',computerOpen()?'COMPUTER':currentDocument?'PDF':snapshot.busy?'AUFTRAG AKTIV':'LOKAL');
    set('#workspace-footnote',computerOpen()?(currentComputer.reason||'Gerätebild · Android'):
      currentDocument?'PDF · '+currentDocument.name:
      lines.length?'Dein Gespräch und deine Arbeitsschritte':'Bereit für deinen Auftrag');
    $('#command-input')?.setAttribute('aria-label','Dein Auftrag');
    $('#command-input')?.setAttribute('placeholder','Schreibe deinen Auftrag …');
    $('#listen-button')?.setAttribute('aria-label',snapshot.listening?'Zuhören beenden':'Spracheingabe starten');
    $('.send-button')?.setAttribute('aria-label','Auftrag senden');
  }
  const workspace=$('#workspace');
  if(workspace)new MutationObserver(()=>{
    if(snapshot && (lines.length || snapshot.busy || snapshot.workRows?.length) && !workspace.querySelector('#native-live-panel'))renderFeed();
    if(currentDocument && !workspace.querySelector('#native-document-panel'))renderDocument();
    if(currentComputer && !workspace.querySelector('#native-computer-panel'))renderComputer();
    nativeLabels();
  }).observe(workspace,{childList:true});
  function receive(state) {
    if(!state||typeof state!=='object')return;
    snapshot=state;
    if(typeof state.sessionId==='string' && chatId.test(state.sessionId)) {
      const changed=session!==state.sessionId;
      const initial=!session;
      if(changed) {
        retireComputer();clearNativeSpeech(true);
        lines=[];feedRevision++;currentDocument=null;pendingCommand=null;lastDraft='';
        latestDraftRevision=-1;
        $('#workspace')?.classList.remove('native-document-active');
        $('#native-document-panel')?.remove();
        if(!initial)dirtyDraft=false;
      }
      session=state.sessionId;
      const revision=Number.isSafeInteger(state.draftRevision) && state.draftRevision>=0?
        state.draftRevision:null;
      const newerDraft=revision===null?latestDraftRevision<0:revision>latestDraftRevision;
      // Even ignored snapshots advance the observed revision, so the submitted
      // draft cannot reappear after its later acceptance acknowledgement.
      if(revision!==null)latestDraftRevision=Math.max(latestDraftRevision,revision);
      if(state.draft && typeof state.draft.text==='string' && !dirtyDraft &&
        !pendingCommand && state.commandPending!==true && newerDraft) {
        const field=$('#command-input');
        if(field && state.draft.text.length<=MAX_DRAFT) {
          field.value=state.draft.text;
          const start=Number.isInteger(state.draft.selectionStart)?state.draft.selectionStart:field.value.length;
          const end=Number.isInteger(state.draft.selectionEnd)?state.draft.selectionEnd:start;
          field.setSelectionRange(Math.max(0,start),Math.max(0,end));
          fitComposer();
        }
      }
    }
    window.ContinuumOperations?.detach();
    renderChats();renderMeters();renderFeed();nativeLabels();
    const scene=state.speaking===true?'speaking':state.listening===true?'listening':state.busy===true?'working':'idle';
    if(scene!==lastScene) {
      lastScene=scene;document.body.dataset.state=scene;
      window.ContinuumScene?.setState(scene);
      document.querySelectorAll('[data-scene]').forEach(button=>{
        button.classList.toggle('active',button.dataset.scene===scene);
        button.setAttribute('aria-current',button.dataset.scene===scene?'step':'false');
        button.setAttribute('aria-disabled','true');
      });
    }
    const orb=$('#orb-status');
    if(orb)orb.textContent=state.speaking?'SPRICHT':state.listening?'HÖRT ZU':state.busy?'ARBEITET':'BEREIT';
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
    if(!data||data.sessionId!==session||!Array.isArray(data.entries))return;
    const rows=data.entries.filter(e=>e&&typeof e.user==='boolean'&&typeof e.text==='string')
      .map(e=>({user:e.user,text:boundedMessage(e.text)}));
    lines=(data.append===true?lines.concat(rows):rows).slice(-60);feedRevision++;
    renderFeed();
  }
  function message(line) {
    if(!line||line.sessionId!==session||typeof line.user!=='boolean'||typeof line.text!=='string')return;
    lines.push({user:line.user,text:boundedMessage(line.text)});feedRevision++;
    if(lines.length>60)lines.shift();
    renderFeed();
  }
  function closePanels() {
    document.body.classList.remove('chats-open','instruments-open');
    document.querySelectorAll('[data-action="toggle-chats"],[data-action="toggle-instruments"]')
      .forEach(button=>button.setAttribute('aria-expanded','false'));
  }
  function section(name) {
    if(!routes.has(name))return;
    if(name==='stop') {retireComputer();clearNativeSpeech(true);}
    else if(name==='device' && currentComputer) {
      computerVisible=true;renderComputer();scheduleVisibilityCheck();
    } else if(computerOpen()) {
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
    if(name==='chat')$('#command-input')?.focus({preventScroll:true});
  }
  function closeProjection() {
    if(computerOpen()) {retireComputer();send('route','stop');}
    else clearDocument();
  }
  window.ContinuumNative=Object.freeze({receive,transcript,message,section,document:documentOpened,
    clearDocument,commandResult,computer,speech});
  document.addEventListener('click',e=>{
    const pdf=e.target.closest('[data-native-pdf]');
    if(pdf){
      e.preventDefault();e.stopImmediatePropagation();
      if(pdf.dataset.nativePdf==='files')send('route','files');
      else if(pdf.dataset.nativePdf==='close')clearDocument();
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
      if(routes.has(value))send('route',value);
      section(value);return;
    }
    const action=e.target.closest('[data-action]');
    const value=action&&actions[action.dataset.action];
    if(value) {
      e.preventDefault();e.stopImmediatePropagation();
      send('route',value);section(value);
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
    if(!chatId.test(session)){notice('Die Android-Verbindung startet. Dein Entwurf bleibt erhalten.');return;}
    if(pendingCommand || snapshot?.commandPending===true){notice('Die Eingabe wird übernommen. Dein Entwurf bleibt erhalten.');return;}
    pendingCommand={id:'command-'+(++requestNumber),sessionId:session,draft:field.value};
    send('command',text,{id:pendingCommand.id,sessionId:session});
  },true);
  const input=$('#command-input');
  input?.addEventListener('input',()=>{dirtyDraft=true;fitComposer();syncDraft();});
  for(const event of ['select','keyup','click','blur'])input?.addEventListener(event,syncDraft);
  window.addEventListener('load',stopDemoTelemetry,{once:true});
  window.addEventListener('resize',fitComposer);
  window.addEventListener('resize',scheduleVisibilityCheck);
  document.addEventListener('scroll',scheduleVisibilityCheck,true);
  window.addEventListener('scroll',scheduleVisibilityCheck);
  document.addEventListener('visibilitychange',()=>{
    if(document.visibilityState!=='visible') {retireComputer();clearNativeSpeech(true);}
    else scheduleVisibilityCheck();
  });
  window.addEventListener('pagehide',()=>{retireComputer();clearNativeSpeech(true);});
  new MutationObserver(scheduleVisibilityCheck).observe(document.body,{attributes:true,attributeFilter:['class','style']});
  const badge=$('.preview-badge');
  labelWithIcons(badge,'ANDROID · LOKAL');
  const note=$('#workspace-footnote');
  if(note)note.textContent='Verbindung wird hergestellt …';
})();
