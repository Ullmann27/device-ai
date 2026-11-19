/* Semantic instruments beside the original Continuum presence.
 * All timing and source relationships come from ContinuumPreview's public API.
 * No sensor values, model progress or independent timing dataset are invented.
 */
(() => {
  'use strict';
  const previous = window.ContinuumPresenceInstruments;
  if (previous && typeof previous.dispose === 'function') previous.dispose();
  const escape = value => String(value).replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]));
  let left = null, right = null, signature = '', disposed = false, updates = 0, lastData = null;
  const text = (node, value) => { if (node && node.textContent !== String(value)) node.textContent = String(value); };
  const attr = (node, key, value) => { if (node && node.getAttribute(key) !== String(value)) node.setAttribute(key, String(value)); };
  const ms = value => Number(value).toLocaleString('de-AT', { maximumFractionDigits: 2 });

  function readData(detail) {
    const raw = detail && Array.isArray(detail.phases) ? detail : window.ContinuumPreview?.getVisualizationData?.();
    if (!raw || !Array.isArray(raw.phases) || !raw.phases.length) return null;
    const phases = raw.phases.map((p, index) => ({
      index, name: String(p.name ?? ''), short: String(p.short || p.name || ''),
      start: Number(p.start), end: Number(p.end), source: String(p.source ?? ''), detail: String(p.detail ?? '')
    }));
    if (phases.some(p => !p.name || !p.source || !Number.isFinite(p.start) || !Number.isFinite(p.end) || p.start > p.end)) return null;
    const selected = Number(raw.selectedPhase);
    const groups = [];
    for (const phase of phases) {
      let group = groups.find(g => g.id === phase.source);
      if (!group) { group = { id: phase.source, phases: [], index: groups.length }; groups.push(group); }
      group.phases.push(phase);
    }
    return { phases, groups, selectedPhase: Number.isInteger(selected) && phases[selected] ? selected : 0,
      state: String(raw.state || 'idle'), section: String(raw.section || 'conversation'),
      minimum: Math.min(...phases.map(p => p.start)), maximum: Math.max(...phases.map(p => p.end)) };
  }

  function phaseChart(data) {
    const start = data.minimum, duration = Math.max(1, data.maximum - start), x = value => 10 + (value - start) / duration * 220;
    const rowHeight = 96 / data.phases.length;
    return `<svg class="pi-time-chart" viewBox="0 0 240 112" preserveAspectRatio="none" role="img" aria-label="Phasenintervalle aus den Entwurfswerten; zugehörige Zahlen stehen in den Auswahlknöpfen">
      <defs><linearGradient id="pi-time-bar" x1="0" x2="1"><stop stop-color="#205777"/><stop offset="1" stop-color="#58bdce"/></linearGradient><linearGradient id="pi-time-selected" x1="0" x2="1"><stop stop-color="#3f879f"/><stop offset=".8" stop-color="#a0eced"/><stop offset="1" stop-color="#e0fbef"/></linearGradient></defs>
      <path class="pi-grid-line" d="M10 2V105 M65 2V105 M120 2V105 M175 2V105 M230 2V105"/>
      ${data.phases.map((p, i) => { const y = 8 + i * rowHeight, a = x(p.start), b = x(p.end), width = Math.max(1, b - a); return `<g class="pi-time-row" data-pi-row="${i}" data-selected="false"><path class="pi-track" d="M10 ${y+4}H230"/><rect class="pi-bar-depth" x="${a}" y="${y+2.8}" width="${width}" height="7" rx="1.8"/><rect class="pi-bar" x="${a}" y="${y}" width="${width}" height="7" rx="1.8"/><path class="pi-bar-highlight" d="M${a+1.6} ${y+1}H${Math.max(a+1.6,b-1.6)}"/><path class="pi-time-impulse" d="M${a} ${y+3.5}H${b}"/></g>`; }).join('')}
      <line class="pi-selection-line" data-pi-selection-line x1="10" x2="10" y1="2" y2="105"/>
    </svg>`;
  }

  function sourceChart(data) {
    const groups = data.groups;
    const centers = groups.map((g, i) => ({ id:g.id, index:i, x:groups.length === 1 ? 100 : 26 + i * 148 / (groups.length - 1), y:57 }));
    return `<svg class="pi-source-chart" viewBox="0 0 200 115" role="img" aria-label="Das Demonstrationsdokument enthält Quellenabschnitte. Die ausgewählte Quelle belegt die ausgewählte Phase.">
      <defs><radialGradient id="pi-source-halo"><stop stop-color="#6476b1" stop-opacity=".12"/><stop offset="1" stop-color="#6476b1" stop-opacity="0"/></radialGradient><linearGradient id="pi-source-edge" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#8b91c9"/><stop offset="1" stop-color="#65c8d5"/></linearGradient></defs>
      <ellipse cx="100" cy="58" rx="92" ry="48" fill="url(#pi-source-halo)" stroke="none"/>
      <path class="pi-network-depth" d="M16 72 100 112 186 72 M16 72 100 29 186 72"/>
      ${centers.map(n => `<g class="pi-source-branch" data-pi-source="${escape(n.id)}" data-selected="false"><path class="pi-contains" d="M100 24 C100 35 ${n.x} 34 ${n.x} 44"/><path class="pi-source-impulse" d="M100 24 C100 35 ${n.x} 34 ${n.x} 44"/><circle class="pi-node-shadow" cx="${n.x+1.8}" cy="${n.y+3}" r="12"/><circle class="pi-source-node" cx="${n.x}" cy="${n.y}" r="12"/><circle class="pi-source-corona" cx="${n.x}" cy="${n.y}" r="15.5"/><text class="pi-node-number" x="${n.x}" y="${n.y+5}" text-anchor="middle">${String(n.index+1).padStart(2,'0')}</text><path class="pi-proves" d="M${n.x} 71 C${n.x} 89 100 83 100 96"/></g>`).join('')}
      <rect class="pi-document-shadow" x="83" y="5" width="37" height="21" rx="5"/>
      <rect class="pi-document-node" x="81" y="2" width="38" height="22" rx="5"/>
      <path class="pi-document-icon" d="M94 7H105M94 11H105M94 15H102"/>
      <path class="pi-target-node" d="m100 95 9 6-9 7-9-7Z"/>
      <path class="pi-target-glint" d="m94 101 6 4 6-4"/>
    </svg>`;
  }

  function build(data) {
    left.innerHTML = `<section class="pi-shell pi-time-shell" aria-labelledby="pi-time-heading"><div class="pi-rear-skin" aria-hidden="true"></div><div class="pi-front-skin" aria-hidden="true"></div><div class="pi-heading"><span class="pi-instrument-icon" aria-hidden="true"><svg viewBox="0 0 20 20"><path d="M3 15V5M3 15H17M6 12l3-4 3 1 5-6"/></svg></span><h3 id="pi-time-heading">Phasenverlauf</h3><span class="pi-title-unit">ms</span></div><div class="pi-time-reading"><div><span class="pi-data-label">Entwurfswerte</span><span class="pi-current-name" data-pi-current-name></span></div><strong class="pi-end-value"><span data-pi-current-end></span><small>ms</small></strong></div><div class="pi-time-viewport">${phaseChart(data)}</div><div class="pi-axis-labels" aria-hidden="true"><span>${ms(data.minimum)} ms</span><span>${ms(data.maximum)} ms</span></div><div class="pi-phase-controls" role="group" aria-label="Phase des Szenenaufbaus auswählen">${data.phases.map((p,i) => `<button class="pi-phase-button" type="button" data-phase="${i}" aria-pressed="false" aria-label="${escape(p.name)}: ${ms(p.start)} bis ${ms(p.end)} Millisekunden" title="${escape(p.name)} · ${ms(p.start)}–${ms(p.end)} ms"><span class="pi-phase-number">${String(i+1).padStart(2,'0')}</span><span class="pi-phase-end">${ms(p.end)}<small> ms</small></span></button>`).join('')}</div></section>`;
    right.innerHTML = `<section class="pi-shell pi-context-shell" aria-labelledby="pi-context-heading"><div class="pi-rear-skin" aria-hidden="true"></div><div class="pi-front-skin" aria-hidden="true"></div><div class="pi-heading"><span class="pi-instrument-icon pi-icon-violet" aria-hidden="true"><svg viewBox="0 0 20 20"><path d="m10 3-6 6 6 7 6-7Z M4 9h12M10 3v13"/></svg></span><h3 id="pi-context-heading">Quellennetz</h3><span class="pi-title-unit">↗</span></div><div class="pi-document-label"><span>Argon4</span><small>Demonstration</small></div><div class="pi-context-viewport">${sourceChart(data)}</div><div class="pi-source-controls" role="group" aria-label="Verknüpfte Quellenpassage öffnen">${data.groups.map((g,i) => `<button class="pi-source-button" type="button" data-source="${escape(g.id)}" aria-pressed="false" aria-label="Quelle ${String(i+1).padStart(2,'0')} öffnen: ${escape(g.phases.map(p=>p.name).join(' und '))}" title="${escape(g.phases.map(p=>p.name).join(' / '))}"><span>${String(i+1).padStart(2,'0')}</span><small>Quelle</small></button>`).join('')}</div><div class="pi-relationship-label" aria-live="polite"><span class="pi-relation-word">belegt</span><strong data-pi-source-proof></strong></div></section>`;
  }

  function paintSelection(data) {
    const phase = data.phases[data.selectedPhase];
    // data-scene is reserved for app.js scene-navigation controls.
    left.dataset.instrumentState = data.state;
    right.dataset.instrumentState = data.state;
    for (const button of left.querySelectorAll('.pi-phase-button')) {
      const active = Number(button.dataset.phase) === data.selectedPhase;
      attr(button, 'aria-pressed', active);
      button.classList.toggle('is-selected', active);
    }
    for (const row of left.querySelectorAll('[data-pi-row]')) attr(row, 'data-selected', Number(row.dataset.piRow) === data.selectedPhase);
    const cursor = 10 + (phase.end - data.minimum) / Math.max(1, data.maximum - data.minimum) * 220;
    attr(left.querySelector('[data-pi-selection-line]'), 'x1', cursor);
    attr(left.querySelector('[data-pi-selection-line]'), 'x2', cursor);
    text(left.querySelector('[data-pi-current-name]'), phase.short);
    text(left.querySelector('[data-pi-current-end]'), ms(phase.end));
    attr(left.querySelector('[data-pi-current-name]'), 'title', `${phase.name}: ${phase.detail}`);
    for (const button of right.querySelectorAll('.pi-source-button')) {
      const active = button.dataset.source === phase.source;
      attr(button, 'aria-pressed', active);
      button.classList.toggle('is-selected', active);
    }
    for (const branch of right.querySelectorAll('[data-pi-source]')) attr(branch, 'data-selected', branch.dataset.piSource === phase.source);
    text(right.querySelector('[data-pi-source-proof]'), phase.name);
    attr(right.querySelector('[data-pi-source-proof]'), 'title', phase.detail);
    attr(right.querySelector('.pi-source-chart'), 'aria-label', `Argon4 enthält ${data.groups.length} Quellenabschnitte. Die ausgewählte Quelle belegt die Phase ${phase.name}.`);
  }

  function refresh(detail) {
    if (disposed) return false;
    left = document.querySelector('[data-presence-left]');
    right = document.querySelector('[data-presence-right]');
    if (!left || !right) return false;
    const data = readData(detail);
    if (!data) { left.dataset.connected = 'false'; right.dataset.connected = 'false'; return false; }
    const nextSignature = JSON.stringify(data.phases.map(p => [p.name,p.short,p.start,p.end,p.source,p.detail]));
    const rebuild = nextSignature !== signature || !left.querySelector('.pi-shell') || !right.querySelector('.pi-shell');
    let focus = null;
    if (rebuild) {
      const active = document.activeElement;
      if (left.contains(active) && active?.dataset.phase !== undefined) focus = {host:left,selector:`[data-phase="${Number(active.dataset.phase)}"]`};
      if (right.contains(active) && active?.dataset.source) focus = {host:right,source:active.dataset.source};
      build(data); signature = nextSignature;
    }
    paintSelection(data);
    left.dataset.connected = 'true'; right.dataset.connected = 'true';
    if (focus) {
      const target = focus.source ? [...focus.host.querySelectorAll('[data-source]')].find(n=>n.dataset.source===focus.source) : focus.host.querySelector(focus.selector);
      target?.focus({preventScroll:true});
    }
    lastData = data; updates++;
    return true;
  }

  const onChange = event => refresh(event.detail);
  const onReady = () => refresh();
  window.addEventListener('continuum:visualizationchange', onChange);
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', onReady, {once:true});
  refresh();
  window.ContinuumPresenceInstruments = Object.freeze({
    refresh,
    getStatus: () => ({connected:!!lastData,disposed,updates,phases:lastData?.phases.length||0,sources:lastData?.groups.length||0,selectedPhase:lastData?.selectedPhase??null}),
    dispose() { if(disposed)return;disposed=true;window.removeEventListener('continuum:visualizationchange',onChange);document.removeEventListener('DOMContentLoaded',onReady); }
  });
})();
