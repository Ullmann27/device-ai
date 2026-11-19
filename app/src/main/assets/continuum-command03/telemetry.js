/* Continuum COMMAND instruments: explicit demo or actual browser measurements.
 * No device CPU/RAM access. No network, permission prompt or synthetic workload.
 * rAF collection only sums intervals; DOM and SVG updates run at most twice/s.
 */
(function () {
  'use strict';
  const SAMPLE_MS = 500, HISTORY = 36, WINDOW_MS = SAMPLE_MS * HISTORY;
  const MODES = new Set(['simulation', 'browser']);
  const TYPES = new Set(['start', 'progress', 'complete', 'cancel', 'error']);
  const KINDS = new Set(['document', 'diagram', 'agent', 'input', 'system', 'research', 'file']);
  const clamp = (value, low, high) => Math.max(low, Math.min(high, value));
  const number = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 1, minimumFractionDigits: 1 });
  const integer = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 0 });
  const MiB = 1024 * 1024;
  const views = [], operations = new Map(), bursts = [], tasks = [], removers = [];
  const cpuHistory = [], ramHistory = [];
  let mode = 'simulation', active = false, paused = false, observer = null, longTasksAvailable = false;
  let sampleTimer = null, raf = null, previousFrame = null, frameSum = 0, frameCount = 0, maxFrame = 0;
  let samples = 0, renders = 0, started = 0, lastSampleAt = null, lastOperation = null;
  let lastBrowser = { frameMs: null, maxFrameMs: null, heap: null, longTasks: null };
  let lastReading = null;

  const text = (node, value) => { const next = String(value); if (node.textContent !== next) node.textContent = next; };
  const attr = (node, name, value) => { const next = String(value); if (node.getAttribute(name) !== next) node.setAttribute(name, next); };
  function listen(target, name, fn, options) { target.addEventListener(name, fn, options); removers.push(() => target.removeEventListener(name, fn, options)); }
  function push(history, value) { history.push(value); if (history.length > HISTORY) history.shift(); }

  function frame(now) {
    if (!active || paused) return;
    if (previousFrame !== null) {
      const interval = now - previousFrame;
      if (Number.isFinite(interval) && interval > 0) { frameSum += interval; frameCount++; maxFrame = Math.max(maxFrame, interval); }
    }
    previousFrame = now;
    raf = window.requestAnimationFrame(frame);
  }

  function readHeap() {
    try {
      const memory = window.performance.memory;
      if (!memory) return null;
      const used = Number(memory.usedJSHeapSize), allocated = Number(memory.totalJSHeapSize), limit = Number(memory.jsHeapSizeLimit);
      if (![used, allocated, limit].every(Number.isFinite) || used < 0 || allocated < used || limit <= 0 || used > limit) return null;
      return { usedBytes: used, allocatedBytes: allocated, limitBytes: limit };
    } catch { return null; }
  }

  function collectBrowser(now) {
    const cutoff = now - WINDOW_MS;
    while (tasks.length && tasks[0].end < cutoff) tasks.shift();
    const reading = { frameMs: frameCount ? frameSum / frameCount : null,
      maxFrameMs: frameCount ? maxFrame : null, heap: readHeap(),
      longTasks: longTasksAvailable ? tasks.length : null };
    frameSum = 0; frameCount = 0; maxFrame = 0;
    return reading;
  }

  function demoAt(seconds, now) {
    // Fixed analytic signals: identical time and operation events give identical
    // demonstration values. They never represent the user's device or Android.
    let response = 0;
    for (const burst of bursts) {
      const age = (now - burst.at) / 1000;
      if (age >= 0 && age < 18) response += burst.strength * (1 - Math.exp(-age / .32)) * Math.exp(-age / 5.2);
    }
    const sustained = Math.min(operations.size, 3) * 3.2;
    const cpu = clamp(31 + 11 * Math.sin(seconds * .61 + .2) + 7 * Math.sin(seconds * 1.13 + 1.4) + 4 * Math.sin(seconds * .193 + 2.3) + response * 43 + sustained, 3, 97);
    const ram = clamp(2.7 + .23 * Math.sin(seconds * .217 + .6) + .13 * Math.sin(seconds * .493 + 1.7) + response * .78 + sustained * .025, .7, 7.4);
    return { source: 'simulation', cpuPercent: cpu, ramGiB: ram, frameMs: null, heapMiB: null, longTasks: null };
  }

  function makeReading(now) {
    if (mode === 'simulation') return demoAt((now - started) / 1000, now);
    return { source: 'browser', cpuPercent: null, ramGiB: null, frameMs: lastBrowser.frameMs,
      heapMiB: lastBrowser.heap ? lastBrowser.heap.usedBytes / MiB : null, longTasks: lastBrowser.longTasks };
  }

  function seedHistory(now) {
    cpuHistory.length = 0; ramHistory.length = 0;
    if (mode === 'simulation') {
      for (let index = HISTORY - 1; index >= 0; index--) {
        const at = now - index * SAMPLE_MS, reading = demoAt((at - started) / 1000, at);
        cpuHistory.push(reading.cpuPercent); ramHistory.push(reading.ramGiB);
      }
    } else {
      for (let index = 0; index < HISTORY; index++) { cpuHistory.push(null); ramHistory.push(null); }
    }
  }

  function pathFor(history, scale) {
    let line = '', area = '', segment = [], latest = null;
    function closeSegment() {
      if (!segment.length) return;
      line += (line ? ' ' : '') + 'M' + segment.map(point => point.join(',')).join(' L');
      area += (area ? ' ' : '') + `M${segment[0][0]},42 L` + segment.map(point => point.join(',')).join(' L') + ` L${segment[segment.length - 1][0]},42 Z`;
      segment = [];
    }
    history.forEach((value, index) => {
      if (!Number.isFinite(value)) { closeSegment(); return; }
      const x = 3 + index / (HISTORY - 1) * 274, y = 42 - clamp(value / scale, 0, 1) * 39;
      const point = [Number(x.toFixed(2)), Number(y.toFixed(2))]; segment.push(point); latest = point;
    });
    closeSegment(); return { line, area, latest };
  }

  function makeView(host, channel) {
    const id = `ct-${channel}`, panel = document.createElement('section');
    panel.className = 'ct-panel'; panel.dataset.channel = channel; panel.setAttribute('aria-labelledby', `${id}-heading`);
    panel.innerHTML = `<header class="ct-heading"><h3 id="${id}-heading"></h3><span class="ct-tag"></span></header>
      <div class="ct-reading"><div class="ct-number"><strong>—</strong><span class="ct-unit"></span></div><div class="ct-caption"><span></span><small></small></div></div>
      <div class="ct-plot"><svg viewBox="0 0 280 44" preserveAspectRatio="none" aria-hidden="true"><path class="ct-grid" d="M3 3H277M3 16H277M3 29H277M3 42H277M3 0V44M71.5 0V44M140 0V44M208.5 0V44M277 0V44"/><path class="ct-area"/><path class="ct-trace"/><circle class="ct-endpoint" r="2.1"/><path class="ct-ticks" d="M3 0V5M16.7 0V3M30.4 0V3M44.1 0V3M57.8 0V3M71.5 0V5M85.2 0V3M98.9 0V3M112.6 0V3M126.3 0V3M140 0V5M153.7 0V3M167.4 0V3M181.1 0V3M194.8 0V3M208.5 0V5M222.2 0V3M235.9 0V3M249.6 0V3M263.3 0V3M277 0V5"/></svg><span class="ct-empty"></span></div>
      <div class="ct-meta"><span>−18 s</span><span class="ct-detail"></span></div>
      <button type="button" class="ct-mode-toggle"><span></span><b aria-hidden="true">↗</b></button>
      <p id="${id}-description" class="ct-sr"></p>`;
    panel.setAttribute('aria-describedby', `${id}-description`); host.appendChild(panel);
    const q = selector => panel.querySelector(selector);
    const view = { host, panel, channel, title: q('h3'), tag: q('.ct-tag'), value: q('strong'), unit: q('.ct-unit'),
      caption: q('.ct-caption span'), scope: q('.ct-caption small'), plot: q('.ct-plot'), area: q('.ct-area'), trace: q('.ct-trace'),
      endpoint: q('.ct-endpoint'), empty: q('.ct-empty'), detail: q('.ct-detail'), toggle: q('button'), toggleText: q('button span'), description: q('.ct-sr') };
    listen(view.toggle, 'click', () => setMode(mode === 'simulation' ? 'browser' : 'simulation'));
    views.push(view);
  }

  function render() {
    if (!lastReading) return;
    const demo = mode === 'simulation';
    for (const view of views) {
      const cpu = view.channel === 'cpu', history = cpu ? cpuHistory : ramHistory;
      const value = demo ? cpu ? lastReading.cpuPercent : lastReading.ramGiB : cpu ? lastReading.frameMs : lastReading.heapMiB;
      const available = Number.isFinite(value);
      const largest = Math.max(0, ...history.filter(Number.isFinite));
      const scale = demo ? cpu ? 100 : 8 : cpu ? Math.max(50, Math.ceil(largest / 25) * 25) : Math.max(64, 2 ** Math.ceil(Math.log2(Math.max(1, largest * 1.12))));
      const plot = pathFor(history, scale);
      view.panel.dataset.mode = mode; view.panel.dataset.available = String(available); view.panel.dataset.paused = String(paused);
      text(view.title, demo ? `SIMULATION / ${cpu ? 'CPU' : 'RAM'}` : cpu ? 'FRAMEZEIT / BROWSER' : 'JS-HEAP / BROWSER');
      text(view.tag, paused ? 'PAUSE' : demo ? 'DEMO' : 'LIVE');
      text(view.value, available ? number.format(value) : '—'); text(view.unit, demo ? cpu ? '%' : 'GiB' : cpu ? 'ms' : 'MiB');
      text(view.caption, demo ? cpu ? 'Beispiellast' : 'Beispielprofil' : cpu ? 'rAF-Abstand' : 'JS-Heap');
      text(view.scope, demo ? cpu ? 'Keine Messung' : '8 GiB' : available ? cpu ? 'Mittel / 0,5 s' : 'Schätzung' : 'Nicht verfügbar');
      text(view.empty, paused ? 'Messung pausiert' : available ? '' : cpu ? 'Messung startet …' : 'Nicht verfügbar');
      text(view.detail, demo ? cpu ? '0–100 % · Demo' : 'Profil: 8 GiB · Demo' : cpu ? !longTasksAvailable ? 'Aufgaben: keine API' : lastBrowser.longTasks === null ? 'Aufgaben: Messung startet' : `Aufgaben ≥50 ms: ${integer.format(lastBrowser.longTasks)}` : available ? 'Heap-Schätzung' : 'Browser liefert keinen Heap');
      text(view.toggleText, demo ? 'Browsermessung' : 'Lastdemo');
      attr(view.toggle, 'aria-label', demo ? 'Beide Instrumente auf echte Browsermessung umschalten' : 'Beide Instrumente auf ausdrücklich gekennzeichnete Lastsimulation umschalten');
      const description = demo ? 'Deterministische CPU- und RAM-Lastdemo. Keine Messung dieses Geräts. Aktionen steuern nur die Demonstration.' : cpu ? 'Mittlerer Abstand der requestAnimationFrame-Aufrufe in Millisekunden. Kein CPU-Prozentwert. Lange Aufgaben ab 50 Millisekunden im sichtbaren 18-Sekunden-Zeitraum, falls die API verfügbar ist.' : 'Vom Browser gelieferte, nicht standardisierte JavaScript-Heap-Schätzung. Kein gesamter Geräte-RAM; andere Prozesse und Worker sind nicht vollständig erfasst.';
      text(view.description, description); view.panel.title = lastOperation ? `${description} Letzte Aktion: ${lastOperation.label}` : description;
      attr(view.trace, 'd', plot.line); attr(view.area, 'd', plot.area);
      attr(view.endpoint, 'visibility', plot.latest ? 'visible' : 'hidden');
      if (plot.latest) { attr(view.endpoint, 'cx', plot.latest[0]); attr(view.endpoint, 'cy', plot.latest[1]); }
      attr(view.plot, 'aria-label', `${demo ? 'Simulierter' : 'Gemessener'} Verlauf über 18 Sekunden, Skala 0 bis ${integer.format(scale)} ${demo ? cpu ? 'Prozent' : 'GiB' : cpu ? 'Millisekunden' : 'MiB'}`);
    }
    renders++;
  }

  function scheduleSample() { sampleTimer = window.setTimeout(sample, SAMPLE_MS); }
  function sample() {
    if (!active || paused) return;
    const now = window.performance.now();
    if (lastSampleAt !== null && now - lastSampleAt < SAMPLE_MS - 1) { scheduleSample(); return; }
    lastSampleAt = now; lastBrowser = collectBrowser(now); lastReading = makeReading(now); samples++;
    push(cpuHistory, mode === 'simulation' ? lastReading.cpuPercent : lastReading.frameMs);
    push(ramHistory, mode === 'simulation' ? lastReading.ramGiB : lastReading.heapMiB);
    while (bursts.length && now - bursts[0].at > WINDOW_MS) bursts.shift();
    render(); scheduleSample();
  }

  function setMode(next) {
    if (!MODES.has(next)) return false;
    if (mode === next) return true;
    mode = next;
    const now = window.performance.now(); seedHistory(now); lastReading = makeReading(now);
    if (mode === 'browser') { push(cpuHistory, lastReading.frameMs); push(ramHistory, lastReading.heapMiB); }
    render();
    try { window.localStorage.setItem('continuum-telemetry-mode', mode); } catch { /* A local preview may disallow storage. */ }
    window.dispatchEvent(new CustomEvent('continuum:telemetrymode', { detail: { mode } }));
    return true;
  }

  function operation(event) {
    const data = event.detail;
    if (!data || !TYPES.has(data.type)) return;
    const now = window.performance.now(), kind = KINDS.has(data.kind) ? data.kind : 'system';
    const id = typeof data.id === 'string' && data.id ? data.id.slice(0, 100) : `operation-${kind}`;
    const label = typeof data.label === 'string' && data.label.trim() ? data.label.trim().slice(0, 100) : 'Aktion';
    const progress = Number.isFinite(data.progress) ? clamp(data.progress, 0, 1) : null;
    lastOperation = { id, type: data.type, kind, label, progress, simulated: data.simulated === true };
    if (data.type === 'start') {
      operations.set(id, lastOperation);
      if (operations.size > 8) operations.delete(operations.keys().next().value);
      const strength = { document: .55, file: .6, diagram: .4, agent: .85, research: .7, input: .25, system: .35 }[kind];
      bursts.push({ at: now, strength }); if (bursts.length > 32) bursts.shift();
    } else if (data.type === 'progress' && operations.has(id)) operations.set(id, lastOperation);
    else if (data.type !== 'progress') operations.delete(id);
    // No DOM write and no synthetic CPU measurement here. The next scheduled
    // sample uses the event only if the selected source is the explicit demo.
  }

  function visibility() {
    paused = document.hidden;
    window.clearTimeout(sampleTimer); sampleTimer = null;
    window.cancelAnimationFrame(raf); raf = null;
    previousFrame = null; frameSum = 0; frameCount = 0; maxFrame = 0;
    if (!paused) {
      lastSampleAt = window.performance.now();
      // A hidden gap must not compress old samples into a false 18-second plot.
      // Live history starts afresh; the labelled analytic demo can be reseeded.
      seedHistory(lastSampleAt);
      lastBrowser = { frameMs: null, maxFrameMs: null, heap: null, longTasks: null };
      lastReading = makeReading(lastSampleAt);
      raf = window.requestAnimationFrame(frame); scheduleSample();
    }
    render();
  }

  function destroy() {
    active = false; window.clearTimeout(sampleTimer); window.cancelAnimationFrame(raf);
    sampleTimer = raf = null; observer?.disconnect(); observer = null;
    removers.splice(0).forEach(remove => remove()); views.splice(0).forEach(view => view.panel.remove());
  }

  function boot() {
    if (active) return;
    const left = document.querySelector('[data-telemetry-left]'), right = document.querySelector('[data-telemetry-right]');
    if (!left && !right) return;
    try { const saved = window.localStorage.getItem('continuum-telemetry-mode'); if (MODES.has(saved)) mode = saved; } catch { /* Default remains explicitly labelled simulation. */ }
    if (left) makeView(left, 'cpu'); if (right) makeView(right, 'ram');
    active = true; paused = document.hidden; started = window.performance.now(); seedHistory(started);
    const Observer = window.PerformanceObserver;
    if (Observer && Observer.supportedEntryTypes?.includes('longtask')) {
      try {
        observer = new Observer(list => {
          if (!active || paused) return;
          for (const entry of list.getEntries()) if (Number.isFinite(entry.duration) && entry.duration >= 50 && Number.isFinite(entry.startTime)) {
            tasks.push({ end: entry.startTime + entry.duration }); if (tasks.length > 128) tasks.shift();
          }
        });
        observer.observe({ type: 'longtask', buffered: false }); longTasksAvailable = true;
      } catch { observer?.disconnect(); observer = null; longTasksAvailable = false; }
    }
    listen(window, 'continuum:operation', operation); listen(document, 'visibilitychange', visibility);
    lastReading = makeReading(started); render();
    if (!paused) { raf = window.requestAnimationFrame(frame); scheduleSample(); }
  }

  window.ContinuumTelemetry = Object.freeze({ setMode, mount: boot, destroy,
    getStatus: () => ({ active, paused, mode, mounted: views.map(view => view.channel), sampleIntervalMs: SAMPLE_MS,
      samples, renders, longTasksAvailable, heapAvailable: lastBrowser.heap !== null,
      lastSampleAt, reading: lastReading ? { ...lastReading } : null,
      browser: { ...lastBrowser, heap: lastBrowser.heap ? { ...lastBrowser.heap } : null },
      operation: lastOperation ? { ...lastOperation } : null, activeOperations: operations.size,
      history: { cpu: [...cpuHistory], ram: [...ramHistory] }, deviceCpuAccess: false, deviceRamAccess: false }) });
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true }); else boot();
})();
