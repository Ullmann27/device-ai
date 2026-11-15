/* Android-only binding. Source PR #850 HTML, CSS, JS and 3D assets stay unchanged. */
(() => {
  'use strict';
  if (window.ContinuumNative) return;
  const routes = new Set(['home','chat','chats','new-chat','files','research',
    'agents','memory','device','settings','voice-input','stop']);
  const actions = Object.freeze({home:'home',settings:'settings',agents:'agents',
    'new-chat':'new-chat','quick-files':'files','quick-memory':'memory',
    'research-demo':'research','open-demo':'files',document:'files',
    listen:'voice-input',cancel:'stop','memory-source':'memory'});
  const $ = selector => document.querySelector(selector);
  let snapshot = null, session = '', lines = [], rendering = false;
  function send(kind,value) {
    if (kind === 'route' && !routes.has(value)) return;
    if (kind === 'command' && (!value || value.length > 2000)) return;
    window.location.assign('continuum-native://action/' + kind +
      '?value=' + encodeURIComponent(value));
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
      row && /^[a-f0-9-]{36}$/.test(row.id) && typeof row.title === 'string').slice(0,100);
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
  function renderMeters() {
    if (!snapshot) return;
    meter('[data-telemetry-left]','APP-CPU',snapshot.cpu,'Anteil dieser App');
    meter('[data-telemetry-right]','GERÄTE-RAM',snapshot.ram,'Belegt im Gerät');
  }
  function renderFeed() {
    if (!snapshot) return;
    const workspace=$('#workspace');
    if (!workspace) return;
    const show=lines.length>0 || snapshot.busy===true ||
      (Array.isArray(snapshot.workRows) && snapshot.workRows.length>0);
    workspace.classList.toggle('native-work-active',show);
    if (!show) return;
    let panel=workspace.querySelector('#native-live-panel');
    if (!panel) {
      panel=document.createElement('section');
      panel.id='native-live-panel';
      panel.className='projected-computer native-live-panel';
      panel.innerHTML='<div class="op-console-head"><div><span class="op-kicker">CONTINUUM · ANDROID</span><h2>Dein echter Arbeitsraum.</h2></div><span class="op-mode-label">LOKALE DATEN</span></div><div class="native-transcript" aria-live="polite"></div><div class="native-lane-state"></div>';
      workspace.appendChild(panel);
    }
    const feed=panel.querySelector('.native-transcript');
    const entries=lines.slice(-24).map(line=>{
      const item=document.createElement('article');
      item.className='native-chat-line '+(line.user?'native-owner':'native-assistant');
      const label=document.createElement('small');label.textContent=line.user?'DU':'CONTINUUM';
      const content=document.createElement('p');content.textContent=String(line.text||'').slice(0,6000);
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
    feed.replaceChildren(...entries);
    panel.querySelector('.native-lane-state').textContent=snapshot.busy?
      'AUFTRAG WIRD BEARBEITET · STATUS VON ANDROID':
      (snapshot.status||'BEREIT · LOKALER GESPRÄCHSVERLAUF');
    const caption=$('#workspace-footnote');
    if(caption)caption.textContent='Echte lokale Gesprächsdaten · Berechtigungen bleiben in Android';
  }
  const workspace=$('#workspace');
  if(workspace)new MutationObserver(()=>{
    if(snapshot && (lines.length || snapshot.busy || snapshot.workRows?.length) && !workspace.querySelector('#native-live-panel'))renderFeed();
  }).observe(workspace,{childList:true});
  function receive(state) {
    if(!state||typeof state!=='object')return;
    snapshot=state;
    if(typeof state.sessionId==='string') {
      if(session && session!==state.sessionId)lines=[];
      session=state.sessionId;
    }
    renderChats();renderMeters();renderFeed();
    const orb=$('#orb-status');
    if(orb)orb.textContent=state.busy?'ARBEITET':state.listening?'HÖRT ZU':'BEREIT';
  }
  function transcript(data) {
    if(!data||data.sessionId!==session||!Array.isArray(data.entries))return;
    lines=data.entries.filter(e=>e&&typeof e.user==='boolean'&&typeof e.text==='string').slice(-60);
    renderFeed();
  }
  function message(line) {
    if(!line||typeof line.user!=='boolean'||typeof line.text!=='string')return;
    lines.push({user:line.user,text:line.text.slice(0,6000)});
    if(lines.length>60)lines.shift();
    renderFeed();
  }
  function section(name) {
    if(!routes.has(name))return;
    document.body.dataset.nativeSection=name;
    if(name==='chat')$('#command-input')?.focus({preventScroll:true});
  }
  window.ContinuumNative=Object.freeze({receive,transcript,message,section});
  document.addEventListener('click',e=>{
    const own=e.target.closest('[data-native-chat]');
    if(own) {
      e.preventDefault();e.stopImmediatePropagation();
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
    }
  },true);
  $('#command-form')?.addEventListener('submit',e=>{
    e.preventDefault();e.stopImmediatePropagation();
    const field=$('#command-input'), text=field?.value?.trim()||'';
    if(text && text.length<=2000){send('command',text);field.value='';}
  },true);
  const badge=$('.preview-badge');
  if(badge)badge.textContent='ANDROID · ORIGINALDESIGN';
  const note=$('#workspace-footnote');
  if(note)note.textContent='Design aus PR #850 · echte Android-Routen · Gerätetest offen';
})();
