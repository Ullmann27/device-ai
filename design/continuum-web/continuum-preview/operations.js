/* A projected, inspectable work surface. Research is a labelled playback; file analysis is local. */
(() => {
  'use strict';
  const app = window.ContinuumPreview;
  if (!app) return;
  window.ContinuumOperations?.dispose?.();
  const q = selector => document.querySelector(selector);
  const esc = value => String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const sources = Object.freeze([
    {id:'motion',name:'Animationen kontrollieren',publisher:'MDN Web Docs',domain:'developer.mozilla.org',url:'https://developer.mozilla.org/en-US/docs/Web/API/Web_Animations_API/Using_the_Web_Animations_API',tag:'Bewegungssteuerung',summary:'Die Web Animations API stellt steuerbare Animationen mit einer gemeinsamen Zeitleiste bereit. Wiedergabe, Pause und Geschwindigkeit lassen sich direkt beeinflussen.',finding:'Ein Fenster kann während seiner Bewegung steuerbar bleiben.',proof:'Die API beschreibt Animationen als steuerbare Objekte. Zeit, Wiedergabe und Abschluss sind voneinander unterscheidbar.'},
    {id:'render',name:'Flüssige Oberflächen aufbauen',publisher:'web.dev',domain:'web.dev',url:'https://web.dev/articles/animations-guide',tag:'Darstellung',summary:'Bewegungen über transform und opacity vermeiden unnötige Änderungen der Seitengeometrie. Aufwendige Zeichen- und Layoutarbeit kann die Darstellung verlangsamen.',finding:'Position und Transparenz sind geeignete Hauptspuren für einen Fensterflug.',proof:'Der Leitfaden empfiehlt, Auswirkungen auf Layout und Zeichnen zu prüfen und Bewegung nach Möglichkeit über transform und opacity umzusetzen.'},
    {id:'measure',name:'Laufzeit beobachten',publisher:'MDN Web Docs',domain:'developer.mozilla.org',url:'https://developer.mozilla.org/en-US/docs/Web/API/PerformanceObserver',tag:'Messung',summary:'PerformanceObserver kann unterstützte Leistungseinträge beobachten. Welche Arten verfügbar sind, hängt vom Browser ab und muss vor der Verwendung geprüft werden.',finding:'Tatsächliche Messungen müssen von einer Lastsimulation getrennt bleiben.',proof:'Der Browser meldet unterstützte Eintragstypen; eine Webseite erhält dadurch keinen allgemeinen Zugriff auf die CPU- oder RAM-Auslastung des Geräts.'}
  ]);
  const stages = [
    {at:0,name:'Auftrag',detail:'Die Arbeitsfläche wird in den Raum projiziert.'},
    {at:1200,name:'Suchlauf',detail:'Der Ablauf zeigt eine Suchanfrage im projizierten Browser.'},
    {at:3400,name:'Quellen',detail:'Drei gespeicherte Fachquellen werden sichtbar und auswählbar.'},
    {at:6200,name:'Abgleich',detail:'Eine Aussage wird mit ihrer Quelle verbunden.'},
    {at:9100,name:'Ergebnis',detail:'Die nachvollziehbare Ergebnisübersicht steht bereit.'}
  ];
  const duration=11000;
  let current=null, raf=0, clock=0, lastUI=0, generation=0, fileURL=null, picker;
  const announce=detail=>window.dispatchEvent(new CustomEvent('continuum:operation',{detail}));
  const setText=(selector,value)=>{const e=q(selector);if(e&&e.textContent!==String(value))e.textContent=value;};
  const progress=()=>current?Math.min(1,current.elapsed/duration):0;
  function signal(type){if(current)announce({id:current.id,type,kind:current.kind,label:current.kind==='research'?'Rechercheablauf':current.fileName||'Datei öffnen',progress:progress(),simulated:current.kind==='research'});}
  function detach(){generation++;cancelAnimationFrame(raf);raf=0;clock=0;if(current&&current.status==='running')signal('cancel');current=null;document.body.removeAttribute('data-operation');if(fileURL){URL.revokeObjectURL(fileURL);fileURL=null;}}
  function graph(){return `<svg class="op-evidence-graph" viewBox="0 0 480 106" role="img" aria-label="Auftrag, drei Quellen und Ergebnis sind verbunden"><path class="op-grid-line" d="M18 25H462M18 53H462M18 81H462"/><path class="op-graph-line" d="M28 53 158 20 340 53 448 53M28 53H158L340 53M28 53 158 86 340 53"/><path class="op-graph-pulse" d="M28 53 158 20 340 53 448 53"/><circle cx="28" cy="53" r="8"/><circle cx="158" cy="20" r="7"/><circle cx="158" cy="53" r="7"/><circle cx="158" cy="86" r="7"/><circle cx="340" cy="53" r="12"/><circle cx="448" cy="53" r="8"/><text x="20" y="101">Auftrag</text><text x="197" y="101">Quellen</text><text x="389" y="101">Ergebnis</text></svg>`;}
  function researchHTML(query){return `<section class="projected-computer" data-operation-panel data-op-stage="0" aria-label="Projizierter Computer mit steuerbarer Recherche-Demonstration">
    <div class="projection-lens" aria-hidden="true"></div><div class="projection-ruler" aria-hidden="true"><span>01</span><i></i><span>02</span><i></i><span>03</span></div>
    <div class="op-console-head"><div><span class="op-kicker">PROJIZIERTER COMPUTER</span><h2>Recherche wird sichtbar.</h2></div><span class="op-mode-label">ABLAUFSIMULATION</span></div>
    <p class="op-intro">Steuere den Ablauf selbst. Die Quellen sind gespeichert und lassen sich im Original öffnen.</p>
    <div class="op-controls"><button class="primary-button" data-op-action="toggle">Ablauf starten</button><button class="secondary-button" data-op-action="step">Nächster Schritt</button><button class="secondary-button" data-op-action="restart">Neu starten</button><label class="op-speed">Tempo <select data-op-speed aria-label="Geschwindigkeit des Beispielablaufs"><option value="0.65">Ruhig</option><option value="1" selected>Normal</option><option value="1.5">Zügig</option></select></label></div>
    <div class="op-browser-frame">
      <div class="op-browser-chrome"><div class="op-tabs" role="tablist" aria-label="Arbeitsansicht"><button role="tab" aria-selected="true" data-op-view="sources">Recherche</button><button role="tab" aria-selected="false" data-op-view="evidence">Zusammenhang</button><button role="tab" aria-selected="false" data-op-view="result">Ergebnis</button></div><span class="op-window-id">CONTINUUM / 01</span></div>
      <div class="op-addressbar"><span aria-hidden="true">⌕</span><span data-op-address>Arbeitsfläche bereit</span><span class="op-address-mode">VORSCHAU</span></div>
      <form class="op-query-form"><label for="op-query">DEIN BEISPIELAUFTRAG</label><div><input id="op-query" value="${esc(query)}" maxlength="260" autocomplete="off"><button type="submit" class="icon-button" aria-label="Beispiel mit diesem Auftrag starten"><svg><use href="#i-arrow"/></svg></button></div></form>
      <div class="op-browser-body">
        <div class="op-waiting"><div class="op-search-orbit" aria-hidden="true"><i></i><b></b></div><h3 data-op-phase-title>Bereit für deinen Auftrag.</h3><p>Beispielthema: flüssige, interaktive Oberflächen.</p><span class="op-typed-query" data-op-typed aria-hidden="true"></span></div>
        <div class="op-sources-view" data-op-pane="sources" hidden><div class="op-source-list">${sources.map((source,index)=>`<button class="op-source-card" data-op-source="${index}" aria-pressed="${index===0}"><span class="op-source-id">0${index+1}</span><span><small>${esc(source.publisher)} · ${esc(source.tag)}</small><strong>${esc(source.name)}</strong><span>${esc(source.domain)}</span></span><b aria-hidden="true"><svg><use href="#i-arrow"/></svg></b></button>`).join('')}</div><article class="op-reading-pane" aria-label="Ausgewählte Quellenbeschreibung"><span class="op-kicker" data-op-publisher></span><h3 data-op-source-title></h3><p data-op-summary></p><div class="op-evidence"><span>ABGELEITETER ZUSAMMENHANG</span><p data-op-finding></p></div><a class="secondary-button" data-op-source-link target="_blank" rel="noopener">Originalquelle öffnen ↗</a></article></div>
        <div class="op-evidence-view" data-op-pane="evidence" hidden><span class="op-kicker">AUSSAGE → QUELLE → BEDEUTUNG</span><h3>Ein Zusammenhang bleibt überprüfbar.</h3>${graph()}<div class="op-source-pills">${sources.map((s,i)=>`<button class="secondary-button" data-op-source="${i}" aria-pressed="${i===0}">0${i+1} ${s.tag}</button>`).join('')}</div><div class="op-proof"><b data-op-proof-title></b><p data-op-proof></p><a data-op-proof-link target="_blank" rel="noopener">Ursprung öffnen ↗</a></div></div>
        <div class="op-result-view" data-op-pane="result" hidden><div class="op-result-heading"><span class="op-result-seal" aria-hidden="true">✓</span><div><span class="op-kicker">ERGEBNIS / GESPEICHERTE RECHERCHE</span><h3>Bewegung. Kontrolle. Nachweis.</h3></div></div><ol class="op-findings">${sources.map((s,i)=>`<li><span>0${i+1}</span><div><b>${esc(s.finding)}</b><p>${esc(s.summary)}</p><button class="text-action" data-op-source="${i}">Quelle ${i+1} ansehen ↗</button></div></li>`).join('')}</ol><div class="action-row"><button class="primary-button" data-op-action="export">Ergebnis herunterladen</button><button class="secondary-button" data-op-action="file">Eigene Datei öffnen</button></div></div>
        <span class="op-demo-pointer" aria-hidden="true"><svg viewBox="0 0 24 28"><path d="m3 2 18 14-10 1-5 8Z"/></svg><small>CONTINUUM</small></span>
      </div>
      <div class="op-browser-status"><span class="status-dot"></span><span data-op-status>Wähle „Ablauf starten“ oder einen Schritt.</span><span data-op-clock>00:00</span></div>
    </div>
    <div class="op-steps" aria-label="Recherchephasen">${stages.map((s,i)=>`<button data-op-step="${i}" aria-current="${i===0?'step':'false'}"><span>0${i+1}</span><b>${s.name}</b><i></i></button>`).join('')}</div><div class="op-progress" role="progressbar" aria-label="Fortschritt des Beispielablaufs" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0"><i></i></div><p class="op-disclosure">Gespeicherte Fachquellen zum Beispielthema · geprüft am 15.11.2026. Ein geänderter Auftrag steuert die Darstellung; eine neue Websuche findet in dieser Vorschau nicht statt.</p>
    <div class="op-file-entry"><span>Mit eigenen Inhalten ausprobieren</span><button class="secondary-button" data-op-action="file">Text, Markdown, CSV oder JSON öffnen</button></div>
  </section>`;}
  function open({kind='research',autoplay=false,query='Wie entstehen flüssige holografische Arbeitsflächen?'}={}){
    detach();
    app.mountWorkspace({html:researchHTML(query),label:'CONTINUUM · PROJIZIERTER COMPUTER',meta:'ABLAUFSIMULATION',foot:'Gespeicherte Fachquellen · steuerbarer Beispielablauf · eigene Textdateien werden lokal gelesen',targetSection:kind==='research'?'research':'computer',scene:autoplay?'working':'idle',presence:{eyebrow:'RECHERCHE · ABLAUFSIMULATION',title:['Dein Auftrag.','Im Blick.'],caption:'Projizierter Rechercheablauf'}});
    current={id:'operation-'+(++generation),kind:'research',query,elapsed:0,status:autoplay?'running':'ready',stage:-1,view:'sources',selectedSource:0,speed:1,manualView:false};
    document.body.dataset.operation=autoplay?'running':'ready';
    selectSource(0,false);render(true);
    if(autoplay){signal('start');raf=requestAnimationFrame(tick);}
  }
  function selectSource(index,goToSource=true){
    if(!current||current.kind!=='research'||!Number.isInteger(index)||!sources[index])return;
    current.selectedSource=index;const source=sources[index];
    if(current.elapsed>=stages[2].at||current.manualView)setText('[data-op-address]',source.domain+' / gespeicherte Quelle');
    setText('[data-op-publisher]',source.publisher+' / '+source.tag);setText('[data-op-source-title]',source.name);setText('[data-op-summary]',source.summary);setText('[data-op-finding]',source.finding);setText('[data-op-proof-title]',source.finding);setText('[data-op-proof]',source.proof);
    for(const e of document.querySelectorAll('[data-op-source-link],[data-op-proof-link]'))e.href=source.url;
    for(const e of document.querySelectorAll('[data-op-source]')){const selected=+e.dataset.opSource===index;e.setAttribute('aria-pressed',String(selected));e.classList.toggle('selected',selected);}
    if(goToSource&&current.view==='result')setView('sources');
    const el=q(`.op-source-card[data-op-source="${index}"]`);if(el)el.dispatchEvent(new CustomEvent('continuum:gaze',{bubbles:true,detail:{target:el}}));
  }
  function setView(view,manual=true){
    if(!current||current.kind!=='research'||!['sources','evidence','result'].includes(view))return;
    current.view=view;current.manualView=manual;
    const visible=current.elapsed>=stages[2].at||manual;
    q('.op-waiting').hidden=visible;
    for(const el of document.querySelectorAll('[data-op-pane]'))el.hidden=!(visible&&el.dataset.opPane===view);
    for(const el of document.querySelectorAll('[data-op-view]')){const selected=el.dataset.opView===view;el.setAttribute('aria-selected',String(selected));el.setAttribute('tabindex',selected?'0':'-1');}
  }
  function render(force=false){
    if(!current||current.kind!=='research')return;
    const s=current,stage=stages.reduce((r,v,i)=>s.elapsed>=v.at?i:r,0);
    if(stage!==s.stage||force){
      s.stage=stage;q('[data-operation-panel]').dataset.opStage=String(stage);
      document.body.dataset.operation=s.status;
      for(const el of document.querySelectorAll('[data-op-step]')){const n=+el.dataset.opStep;el.classList.toggle('complete',n<stage);el.setAttribute('aria-current',n===stage?'step':'false');}
      setText('[data-op-status]',stages[stage].detail);
      setText('[data-op-phase-title]',stage===0?'Der Auftrag kommt an.':'Die Suchanfrage wird sichtbar.');
      setText('[data-op-address]',stage<2?'CONTINUUM / Recherchevorschau':sources[s.selectedSource].domain+' / gespeicherte Quelle');
      if(!s.manualView)setView(stage>=4?'result':stage===3?'evidence':'sources',false);else setView(s.view,true);
      signal(s.status==='complete'?'complete':'progress');
    }
    const r=progress();const bar=q('.op-progress');bar.setAttribute('aria-valuenow',String(Math.round(r*100)));bar.querySelector('i').style.transform=`scaleX(${r})`;
    setText('[data-op-clock]',`00:${Math.floor(s.elapsed/1000).toString().padStart(2,'0')}`);
    setText('[data-op-typed]',stage===1?s.query.slice(0,Math.floor((s.elapsed-stages[1].at)/25)):stage===0?'':s.query);
    setText('[data-op-action="toggle"]',s.status==='running'?'Pause':s.status==='paused'?'Fortsetzen':s.status==='complete'?'Erneut abspielen':'Ablauf starten');
    const panel=q('[data-operation-panel]');panel.dataset.operationStatus=s.status;
  }
  function tick(now){
    raf=0;if(!current||current.status!=='running')return;
    if(clock)current.elapsed=Math.min(duration,current.elapsed+Math.min(80,now-clock)*current.speed);
    clock=now;
    if(now-lastUI>=80||current.elapsed===duration){lastUI=now;render();}
    if(current.elapsed===duration){current.status='complete';clock=0;render(true);app.setScene('result',{render:false,cancel:false});return;}
    raf=requestAnimationFrame(tick);
  }
  function toggle(){
    if(!current||current.kind!=='research')return;
    if(current.status==='running'){current.status='paused';cancelAnimationFrame(raf);raf=0;clock=0;signal('pause');}
    else{if(current.elapsed>=duration){current.elapsed=0;current.stage=-1;current.manualView=false;}current.status='running';clock=0;window.ContinuumScene?.setState('working');signal('start');raf=requestAnimationFrame(tick);}
    render(true);
  }
  function seek(index){if(!current||current.kind!=='research'||!stages[index])return;cancelAnimationFrame(raf);raf=0;clock=0;current.elapsed=stages[index].at;current.status='paused';current.manualView=false;render(true);}
  function restart(){if(!current)return;const query=q('#op-query')?.value.trim()||current.query;open({autoplay:true,query});}
  function download(name,text){const url=URL.createObjectURL(new Blob([text],{type:'text/markdown;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1500);}
  function exportResult(){if(!current)return;if(current.kind==='file'){download('Continuum_Dateiauswertung.md',current.report);return;}download('Continuum_Recherchebeispiel.md',`# Continuum – Recherchebeispiel\n\nAuftrag in der Darstellung: ${current.query}\n\nGespeicherte Recherche zum Beispielthema flüssige holografische Arbeitsflächen. Keine neu ausgeführte Websuche. Quellen geprüft am 15.11.2026.\n\n`+sources.map((s,i)=>`## ${i+1}. ${s.finding}\n\n${s.summary}\n\nQuelle: ${s.url}`).join('\n\n'));app.toast('Die Quellenübersicht wurde als Markdown-Datei ausgegeben.');}
  function openFilePicker(){if(!picker){picker=document.createElement('input');picker.type='file';picker.accept='.txt,.md,.markdown,.csv,.json,text/plain,text/markdown,text/csv,application/json';picker.hidden=true;picker.setAttribute('aria-label','Eigene Textdatei auswählen');picker.addEventListener('change',()=>{const file=picker.files?.[0];if(file)loadFile(file);picker.value='';});document.body.append(picker);}picker.click();}
  const readFile=file=>typeof file.text==='function'?file.text():new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(String(reader.result));reader.onerror=()=>reject(reader.error||new Error('Datei konnte nicht gelesen werden.'));reader.readAsText(file);});
  async function loadFile(file){
    if(!file||!/[.](txt|md|markdown|csv|json)$/i.test(file.name)){app.toast('Bitte eine Text-, Markdown-, CSV- oder JSON-Datei auswählen.');return false;}
    if(file.size>2*1024*1024){app.toast('Für diese lokale Ansicht bitte eine Datei bis 2 MB auswählen.');return false;}
    detach();const token=++generation;
    const loading=`<div class="op-file-loading"><span class="section-kicker">LOKALE DATEI</span><h2>${esc(file.name)}</h2><p>Der ausgewählte Inhalt wird gelesen.</p><button class="secondary-button" data-action="cancel">Abbrechen</button></div>`;
    app.mountWorkspace({html:loading,label:'LOKALE DATEI',meta:'TATSÄCHLICHER DATEIINHALT',foot:'Lokale Verarbeitung im Browser · kein Upload',scene:'working'});
    const acceptedGeneration=generation;
    current={id:'file-'+token,kind:'file',fileName:file.name,status:'running',elapsed:0};signal('start');
    try{
      const text=await readFile(file);
      if(generation!==acceptedGeneration||current?.id!=='file-'+token)return false;
      if(text.includes('\u0000'))throw new Error('Diese Datei enthält Binärdaten. Bitte eine Textdatei wählen.');
      const words=(text.match(/[\p{L}\p{N}]+(?:['’\-][\p{L}\p{N}]+)*/gu)||[]).length;
      const lines=text?text.split(/\r?\n/).length:0;
      const paragraphs=text.trim()?text.split(/\r?\n\s*\r?\n/).map(t=>t.trim()).filter(Boolean):[];
      const entries=paragraphs.slice(0,10).map((t,i)=>({i,words:(t.match(/[\p{L}\p{N}]+/gu)||[]).length,text:t}));
      const max=Math.max(1,...entries.map(e=>e.words));
      const visual=entries.map((e,i)=>`<button class="op-file-bar" data-op-paragraph="${i}" aria-label="Abschnitt ${i+1}: ${e.words} Wörter"><span>${String(i+1).padStart(2,'0')}</span><i style="--bar-width:${Math.max(2,e.words/max*100)}%"></i><b>${e.words}</b></button>`).join('');
      const report=`# ${file.name}\n\nLokal im Browser ausgewertet. Keine KI-Zusammenfassung.\n\n- Wörter: ${words}\n- Zeichen: ${Array.from(text).length}\n- Zeilen: ${lines}\n- Absätze: ${paragraphs.length}\n\n## Inhalt\n\n${text}`;
      const html=`<section class="projected-computer op-local-document" data-operation-panel><div class="op-console-head"><div><span class="op-kicker">DEINE DATEI / LOKAL GELESEN</span><h2>${esc(file.name)}</h2></div><span class="op-mode-label real">LOKALE AUSWERTUNG</span></div><div class="op-local-metrics"><div><b>${words.toLocaleString('de-DE')}</b><span>Wörter</span></div><div><b>${lines.toLocaleString('de-DE')}</b><span>Zeilen</span></div><div><b>${paragraphs.length.toLocaleString('de-DE')}</b><span>Absätze</span></div></div><div class="op-local-grid"><article class="op-file-text" aria-label="Tatsächlicher Dateiinhalt">${paragraphs.length?paragraphs.map((p,i)=>`<p id="op-paragraph-${i}" tabindex="-1"><span>ABSCHNITT ${i+1}</span>${esc(p)}</p>`).join(''):'<p>Diese Datei ist leer.</p>'}</article><aside class="op-file-analysis"><span class="op-kicker">WÖRTER JE ABSCHNITT</span><h3>Struktur wird sichtbar.</h3><p>Wähle einen Balken. Die zugehörige Textstelle wird markiert.</p><div class="op-file-bars">${visual}</div><small>${paragraphs.length>10?'Die ersten zehn Abschnitte sind im Diagramm dargestellt.':'Die Balken zeigen die tatsächlichen Wortzahlen.'}</small></aside></div><div class="action-row"><button class="primary-button" data-op-action="export">Auswertung herunterladen</button><button class="secondary-button" data-op-action="file">Andere Datei öffnen</button><button class="secondary-button" data-op-action="research">Rechercheablauf ansehen</button></div><p class="op-disclosure">Der Text bleibt in dieser Browseransicht. Wort- und Abschnittszahlen werden lokal berechnet; dies ist keine KI-Zusammenfassung.</p></section>`;
      current.status='complete';
      app.mountWorkspace({html,label:'DATEI · INHALT & STRUKTUR',meta:'LOKALE AUSWERTUNG',foot:'Tatsächlich gelesene Textdatei · lokale Berechnung · kein Upload',scene:'result',kind:'switch'});
      current={id:'file-'+token,kind:'file',fileName:file.name,status:'complete',elapsed:duration,report,metrics:{words,lines,paragraphs:paragraphs.length}};document.body.dataset.operation='complete';signal('complete');return true;
    }catch(error){if(generation!==acceptedGeneration)return false;current=null;app.mountWorkspace({html:`<div class="state-stage"><h2>Datei konnte nicht geöffnet werden.</h2><p>${esc(error.message)}</p><button class="primary-button" data-op-action="file">Andere Datei wählen</button></div>`,label:'DATEI ÖFFNEN',meta:'HINWEIS',foot:'Die übrige Vorschau bleibt bedienbar.',scene:'idle'});announce({id:'file-'+token,type:'error',kind:'file',label:error.message,simulated:false});return false;}
  }
  function click(event){
    const source=event.target.closest('[data-op-source]');if(source){selectSource(+source.dataset.opSource);return;}
    const step=event.target.closest('[data-op-step]');if(step){seek(+step.dataset.opStep);return;}
    const view=event.target.closest('[data-op-view]');if(view){setView(view.dataset.opView);return;}
    const para=event.target.closest('[data-op-paragraph]');if(para){document.querySelectorAll('.op-file-text p').forEach(p=>p.classList.remove('selected'));const target=q('#op-paragraph-'+para.dataset.opParagraph);target?.classList.add('selected');target?.scrollIntoView({behavior:window.matchMedia?.('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'nearest'});target?.focus({preventScroll:true});return;}
    const action=event.target.closest('[data-op-action]')?.dataset.opAction;
    if(action==='toggle')toggle();else if(action==='step')seek(Math.min(4,(current?.stage||0)+1));else if(action==='restart')restart();else if(action==='export')exportResult();else if(action==='file')openFilePicker();else if(action==='research')open({autoplay:true});
  }
  function submit(event){if(!event.target.matches('.op-query-form'))return;event.preventDefault();restart();}
  function change(event){if(event.target.matches('[data-op-speed]')&&current)current.speed=Number(event.target.value)||1;}
  function keydown(event){const tab=event.target.closest('[data-op-view]');if(!tab||!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();const views=['sources','evidence','result'];let i=views.indexOf(tab.dataset.opView);i=event.key==='Home'?0:event.key==='End'?2:(i+(event.key==='ArrowRight'?1:2))%3;setView(views[i]);q(`[data-op-view="${views[i]}"]`).focus();}
  function visibility(){clock=0;if(document.hidden&&current?.status==='running'){current.status='paused';cancelAnimationFrame(raf);raf=0;render(true);}}
  document.addEventListener('click',click);document.addEventListener('submit',submit);document.addEventListener('change',change);document.addEventListener('keydown',keydown);document.addEventListener('visibilitychange',visibility);
  window.ContinuumOperations={open,openFilePicker,loadFile,detach,seek,toggle,setView,selectSource,getState:()=>current?{id:current.id,kind:current.kind,status:current.status,stage:current.stage,elapsed:current.elapsed,view:current.view,selectedSource:current.selectedSource,metrics:current.metrics?{...current.metrics}:undefined}:null,getSources:()=>sources.map(s=>({...s})),dispose(){detach();document.removeEventListener('click',click);document.removeEventListener('submit',submit);document.removeEventListener('change',change);document.removeEventListener('keydown',keydown);document.removeEventListener('visibilitychange',visibility);picker?.remove();}};
})();
