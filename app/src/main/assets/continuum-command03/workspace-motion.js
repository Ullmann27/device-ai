/* One reversible display controller. Layout is read only at transaction boundaries.
 * Transform/opacity tracks use WAAPI when available, otherwise a single RAF loop.
 * Neither backend changes content, logical app scenes, or operation timers.
 */
(() => {
  'use strict';
  window.ContinuumWorkspaceMotion?.dispose?.();
  const KEYS = ['x', 'y', 'z', 'rx', 'ry', 'rz', 'sx', 'sy', 'alpha'];
  const LAYOUT_KEYS = ['x', 'y', 'sx', 'sy'];
  const REST = Object.freeze({ x: 0, y: 0, z: 0, rx: 0, ry: 0, rz: 0, sx: 1, sy: 1, alpha: 1 });
  const FLAT = Object.freeze({ x: 0, y: 0, sx: 1, sy: 1 });
  const PRIORITY = { resize: 0, switch: 1, settle: 2, open: 3 };
  const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
  const now = () => performance.now();
  const clean = n => Math.abs(n) < 0.000001 ? 0 : Number(n.toFixed(6));
  const zeros = keys => Object.fromEntries(keys.map(key => [key, 0]));
  const poseTransform = p => `perspective(1400px) translate3d(${clean(p.x)}px,${clean(p.y)}px,${clean(p.z)}px) rotateX(${clean(p.rx)}deg) rotateY(${clean(p.ry)}deg) rotateZ(${clean(p.rz)}deg) scale(${clean(p.sx)},${clean(p.sy)})`;
  const layoutTransform = p => `matrix(${clean(p.sx)},0,0,${clean(p.sy)},${clean(p.x)},${clean(p.y)})`;
  const media = typeof matchMedia === 'function' ? matchMedia('(prefers-reduced-motion: reduce)') : null;
  let reduced = !!media?.matches, disposed = false, shell = null, stage = null, front = null, original = null;
  let waapi = false, pose = { ...REST }, layout = { ...FLAT }, motion = null, layoutMotion = null;
  let serial = 0, latestMotionId = 0, transactionSerial = 0, frame = 0, transaction = null, phase = 'resting', lastResult = null;

  function trackSample(track, elapsed) {
    const t = clamp(elapsed, 0, track.duration), frames = track.frames;
    if (t >= track.duration) return { value: { ...frames[frames.length - 1].value }, velocity: zeros(track.keys) };
    const i = Math.min(frames.length - 2, Math.floor(t / track.duration * (frames.length - 1)));
    const a = frames[i], b = frames[i + 1], span = b.time - a.time;
    const portion = span > 0 ? clamp((t - a.time) / span, 0, 1) : 0;
    const value = {}, velocity = {};
    for (const key of track.keys) {
      value[key] = a.value[key] + (b.value[key] - a.value[key]) * portion;
      velocity[key] = (b.value[key] - a.value[key]) / Math.max(.001, span / 1000);
    }
    return { value, velocity };
  }
  function elapsed(track) {
    const time = track?.animation?.currentTime;
    return track ? clamp(typeof time === 'number' ? time : now() - track.started, 0, track.duration) : 0;
  }
  function poseSample() { return motion ? trackSample(motion, elapsed(motion)) : { value: { ...pose }, velocity: zeros(KEYS) }; }
  function layoutSample() { return layoutMotion ? trackSample(layoutMotion, elapsed(layoutMotion)) : { value: { ...layout }, velocity: zeros(LAYOUT_KEYS) }; }

  // Cubic Hermite segments retain the current direction and velocity when interrupted.
  // The keyframes all use the same transform list, avoiding matrix decomposition flips.
  function makeTrack(start, velocity, target, duration, keys, waypoint = null) {
    const points = [{ at: 0, value: start, velocity }, ...(waypoint ? [{ at: .4, value: waypoint, velocity: zeros(keys) }] : []),
      { at: 1, value: target, velocity: zeros(keys) }];
    const frames = [], count = Math.max(12, Math.ceil(duration / 16));
    for (let i = 0; i <= count; i++) {
      const at = i / count;
      let segment = 0;
      while (segment < points.length - 2 && at > points[segment + 1].at) segment++;
      const a = points[segment], b = points[segment + 1], u = (at - a.at) / (b.at - a.at);
      const seconds = (b.at - a.at) * duration / 1000;
      const h00 = 2*u*u*u - 3*u*u + 1, h10 = u*u*u - 2*u*u + u;
      const h01 = -2*u*u*u + 3*u*u, h11 = u*u*u - u*u;
      const value = {};
      for (const key of keys) {
        const maximum = key.startsWith('s') ? 1.6 : key === 'alpha' ? 4 : key.startsWith('r') ? 420 : 720;
        const v0 = clamp(a.velocity[key] || 0, -maximum, maximum);
        value[key] = h00*a.value[key] + h10*seconds*v0 + h01*b.value[key] + h11*seconds*(b.velocity[key] || 0);
        if (key === 'alpha') value[key] = clamp(value[key], 0, 1);
        if (key === 'sx' || key === 'sy') value[key] = Math.max(.08, value[key]);
      }
      frames.push({ offset: at, time: at * duration, value });
    }
    return { duration, keys, frames, started: now(), animation: null, animations: [], settled: false, backend: 'raf' };
  }
  function applyPose(value) {
    pose = { ...value };
    if (!shell || !stage) return;
    shell.style.transform = poseTransform(pose);
    stage.style.opacity = String(clean(pose.alpha));
  }
  function applyLayout(value) {
    layout = { ...value };
    if (stage) stage.style.transform = layoutTransform(layout);
  }
  function emit() {
    if (!shell) return;
    const active = !!motion || !!layoutMotion;
    shell.dataset.motionPhase = phase;
    stage.dataset.motionActive = String(active);
    window.dispatchEvent(new CustomEvent('continuum:workspacemotion', { detail: {
      active, phase, id: latestMotionId, kind: motion?.kind ?? lastResult?.kind ?? 'none',
      backend: motion?.backend ?? layoutMotion?.backend ?? (waapi ? 'waapi' : 'raf'), reducedMotion: reduced
    } }));
  }
  function cancelAnimations(track) {
    for (const animation of track?.animations || []) {
      try { animation.cancel(); } catch (_) { /* An already removed effect has no work left. */ }
    }
  }
  function resultFor(track, finished, reason) {
    return { finished, cancelled: !finished, id: track?.id || serial, kind: track?.kind || 'settle', reason: reason || (finished ? 'completed' : 'interrupted') };
  }
  function stopMotion(reason = 'interrupted') {
    const sample = poseSample(), old = motion;
    if (old) {
      old.settled = true; motion = null;
      applyPose(sample.value); cancelAnimations(old);
      lastResult = resultFor(old, false, reason); old.resolve(lastResult);
    }
    return sample;
  }
  function stopLayout(reason = 'interrupted') {
    const sample = layoutSample(), old = layoutMotion;
    if (old) {
      old.settled = true; layoutMotion = null;
      applyLayout(sample.value); cancelAnimations(old);
      old.resolve(resultFor(old, false, reason));
    }
    return sample;
  }
  function finishMotion(track) {
    if (disposed || track.settled || motion !== track) return;
    track.settled = true; motion = null;
    applyPose(track.frames[track.frames.length - 1].value); cancelAnimations(track);
    phase = track.kind === 'close' ? 'closed' : 'resting';
    stage.inert = phase === 'closed';
    lastResult = resultFor(track, true); track.resolve(lastResult); emit();
  }
  function finishLayout(track) {
    if (disposed || track.settled || layoutMotion !== track) return;
    track.settled = true; layoutMotion = null;
    applyLayout(FLAT); cancelAnimations(track);
    track.resolve(resultFor(track, true)); emit();
  }
  function requestTick() {
    if (!frame && !disposed && ((motion && motion.backend === 'raf') || (layoutMotion && layoutMotion.backend === 'raf'))) frame = requestAnimationFrame(tick);
  }
  function tick() {
    frame = 0;
    if (disposed) return;
    const active = motion, activeLayout = layoutMotion;
    if (active?.backend === 'raf') {
      const time = elapsed(active); applyPose(trackSample(active, time).value);
      if (time >= active.duration) finishMotion(active);
    }
    if (activeLayout?.backend === 'raf') {
      const time = elapsed(activeLayout); applyLayout(trackSample(activeLayout, time).value);
      if (time >= activeLayout.duration) finishLayout(activeLayout);
    }
    requestTick();
  }
  function startEffects(track, isLayout) {
    if (waapi) {
      try {
        const options = { duration: track.duration, easing: 'linear', fill: 'both' };
        if (isLayout) track.animations.push(stage.animate(track.frames.map(f => ({ offset: f.offset, transform: layoutTransform(f.value) })), options));
        else {
          track.animations.push(shell.animate(track.frames.map(f => ({ offset: f.offset, transform: poseTransform(f.value) })), options));
          track.animations.push(stage.animate(track.frames.map(f => ({ offset: f.offset, opacity: f.value.alpha })), options));
        }
        track.animation = track.animations[0];
        const timelineTime = document.timeline?.currentTime;
        if (typeof timelineTime === 'number') for (const animation of track.animations) animation.startTime = timelineTime;
        track.backend = 'waapi';
        // Every finished promise is observed, including the companion opacity track.
        for (const animation of track.animations.slice(1)) animation.finished.catch(() => {});
        track.animation.finished.then(() => isLayout ? finishLayout(track) : finishMotion(track), () => {
          if (!track.settled && !disposed) {
            if (isLayout && layoutMotion === track) stopLayout('effect-cancelled');
            if (!isLayout && motion === track) { stopMotion('effect-cancelled'); phase = 'held'; }
            emit();
          }
        });
        return;
      } catch (_) {
        for (const animation of track.animations) animation.finished?.catch(() => {});
        cancelAnimations(track); track.animations = []; track.animation = null; waapi = false;
      }
    }
    track.backend = 'raf'; requestTick();
  }
  const isRest = p => Math.abs(p.x)+Math.abs(p.y)+Math.abs(p.z)+Math.abs(p.rx)+Math.abs(p.ry)+Math.abs(p.rz)+Math.abs(1-p.sx)+Math.abs(1-p.sy)+Math.abs(1-p.alpha) < .001;
  function startMotion(kind, options = {}) {
    if (!mount() || disposed) return Promise.resolve(resultFor(null, false, 'unavailable'));
    if (motion?.kind === kind && !options.force) return motion.promise;
    const interrupted = !!motion, sample = stopMotion(kind === 'close' ? 'closed-again' : 'superseded');
    stage.inert = false;
    const duration = clamp(Number(options.duration) || (kind === 'open' ? 950 : kind === 'close' ? 650 : reduced ? 180 : 360), 1, 5000);
    if (kind !== 'close' && kind !== 'open' && isRest(sample.value) && !interrupted && kind !== 'switch') {
      latestMotionId = ++serial; phase = 'resting'; applyPose(REST); emit(); return Promise.resolve(resultFor(null, true));
    }
    let target = { ...REST }, waypoint = null;
    if (kind === 'close') target = reduced ? { ...REST, alpha: 0 } : { ...REST, x: -28, y: -20, z: -145, rx: 8, ry: -108, sx: .88, sy: .88, alpha: 0 };
    else if (kind === 'open' && !reduced && isRest(sample.value)) waypoint = { ...REST, x: -18, y: -9, z: -125, rx: 7, ry: -103, sx: .91, sy: .91 };
    else if (kind === 'switch' && !reduced && !interrupted && isRest(sample.value)) waypoint = { ...REST, y: -2, z: -14, rx: .8, ry: -3 };
    const track = makeTrack(sample.value, sample.velocity, target, duration, KEYS, waypoint);
    if (kind === 'close' && !reduced) {
      // The rear remains solid while it turns into view; dissolution follows the turn.
      const initialAlpha = sample.value.alpha;
      for (const f of track.frames) {
        const fade = clamp((f.offset - .7) / .3, 0, 1);
        f.value.alpha = initialAlpha * (1 - fade*fade*(3-2*fade));
      }
    }
    track.id = ++serial; latestMotionId = track.id; track.kind = kind;
    track.promise = new Promise(resolve => { track.resolve = resolve; });
    motion = track; phase = kind === 'close' ? 'closing' : kind === 'open' ? 'opening' : 'settling';
    applyPose(sample.value); startEffects(track, false); emit();
    return track.promise;
  }
  function startLayout(from, velocity = zeros(LAYOUT_KEYS), duration = reduced ? 120 : 340) {
    stopLayout('layout-retargeted');
    if (Math.abs(from.x)+Math.abs(from.y)+Math.abs(1-from.sx)+Math.abs(1-from.sy) < .001) { applyLayout(FLAT); return null; }
    const track = makeTrack(from, velocity, FLAT, duration, LAYOUT_KEYS);
    track.id = ++serial; track.kind = 'resize';
    track.promise = new Promise(resolve => { track.resolve = resolve; });
    layoutMotion = track; applyLayout(from); startEffects(track, true); emit();
    return track.promise;
  }
  function readBounds() {
    const rect = stage.getBoundingClientRect(), current = layoutSample().value;
    return { visual: { left: rect.left, top: rect.top, width: rect.width, height: rect.height },
      base: { left: rect.left-current.x, top: rect.top-current.y, width: rect.width/current.sx, height: rect.height/current.sy } };
  }
  function mergeOptions(first, next) {
    const oldKind = first.kind || 'resize', kind = next.kind || 'resize';
    return (PRIORITY[kind] ?? 0) > (PRIORITY[oldKind] ?? 0) ? { ...first, ...next } : first;
  }
  function begin(options = {}) {
    if (!mount() || disposed) return null;
    if (!transaction) transaction = { stack: [], before: readBounds(), options: { kind: 'resize', ...options } };
    else transaction.options = mergeOptions(transaction.options, options);
    const token = { id: ++transactionSerial, closed: false };
    transaction.stack.push(token); return token;
  }
  function commit(token, options = {}) {
    if (!token || token.closed || disposed || !transaction) return false;
    if (transaction.stack[transaction.stack.length-1] !== token) throw new Error('Workspace motion transactions must close in reverse order.');
    token.closed = true; transaction.stack.pop(); transaction.options = mergeOptions(transaction.options, options);
    if (transaction.stack.length) return true;
    const batch = transaction; transaction = null;
    const after = readBounds(), a = batch.before, b = after.base;
    const changed = ['left','top','width','height'].some(key => Math.abs(a.base[key] - b[key]) > .35);
    if (changed && b.width > 0 && b.height > 0 && a.visual.width > 0 && a.visual.height > 0) {
      const velocity = layoutSample().velocity;
      startLayout({ x:a.visual.left-b.left, y:a.visual.top-b.top, sx:a.visual.width/b.width, sy:a.visual.height/b.height }, velocity);
    }
    if (batch.options.kind !== 'resize') startMotion(batch.options.kind, batch.options);
    return true;
  }
  function transition(update, options = {}) {
    if (typeof update !== 'function') throw new TypeError('Workspace transition requires a synchronous DOM update function.');
    const token = begin(options);
    try { return update(); } finally { commit(token); }
  }
  function cancel(options = {}) {
    const sample = stopMotion('cancelled'), currentLayout = stopLayout('cancelled');
    latestMotionId = ++serial; phase = 'held'; emit();
    if (options.settle === false || disposed || !mount()) return Promise.resolve(resultFor(null, false, 'held'));
    stage.inert = false;
    startLayout(currentLayout.value, currentLayout.velocity, reduced ? 120 : 260);
    // Preserve the sampled pose/direction after stopping the previous operation.
    const track = makeTrack(sample.value, sample.velocity, REST, reduced ? 180 : 360, KEYS);
    track.id = ++serial; latestMotionId = track.id; track.kind = 'settle'; track.promise = new Promise(resolve => { track.resolve = resolve; });
    motion = track; phase = 'settling'; startEffects(track, false); emit(); return track.promise;
  }
  function mount() {
    if (disposed) return false;
    if (shell?.isConnected && stage?.isConnected) return true;
    const target = document.querySelector('.workspace-shell');
    if (!target || !target.parentNode) return false;
    shell = target;
    original = { transform:shell.style.transform, managed:shell.getAttribute('data-motion-managed'), phase:shell.getAttribute('data-motion-phase') };
    stage = document.createElement('div'); stage.className = 'workspace-motion-stage'; stage.dataset.motionActive = 'false';
    shell.parentNode.insertBefore(stage, shell); stage.appendChild(shell);
    front = document.createElement('div'); front.className = 'workspace-motion-front'; front.setAttribute('aria-hidden', 'true'); shell.prepend(front);
    shell.dataset.motionManaged = 'true'; shell.dataset.motionPhase = 'resting';
    waapi = typeof shell.animate === 'function' && typeof stage.animate === 'function';
    applyPose(REST); applyLayout(FLAT); return true;
  }
  function status() {
    return { mounted:!!shell?.isConnected, active:!!motion||!!layoutMotion, phase, id:latestMotionId,
      kind:motion?.kind??lastResult?.kind??'none', duration:motion?.duration||0, progress:motion?elapsed(motion)/motion.duration:1,
      layoutAnimating:!!layoutMotion, backend:motion?.backend??layoutMotion?.backend??(waapi?'waapi':'raf'), reducedMotion:reduced,
      pose:poseSample().value, layout:layoutSample().value, lastResult:lastResult?{...lastResult}:null };
  }
  const onReady = () => mount();
  const onPreference = event => { reduced = !!event.matches; if (motion || layoutMotion) cancel({settle:true}); else emit(); };
  function dispose() {
    if (disposed) return;
    stopMotion('disposed'); stopLayout('disposed'); disposed = true;
    cancelAnimationFrame(frame); frame=0; transaction=null;
    document.removeEventListener('DOMContentLoaded',onReady);
    media?.removeEventListener?.('change',onPreference);
    if (media && !media.removeEventListener) media.removeListener?.(onPreference);
    front?.remove();
    if (shell && original) {
      shell.style.transform=original.transform;
      for (const [attribute,value] of [['data-motion-managed',original.managed],['data-motion-phase',original.phase]]) {
        if(value===null)shell.removeAttribute(attribute);else shell.setAttribute(attribute,value);
      }
      if(stage?.parentNode){stage.parentNode.insertBefore(shell,stage);stage.remove();}
    }
  }
  window.ContinuumWorkspaceMotion = Object.freeze({ begin, commit, transition,
    open:options=>startMotion('open',options), close:options=>startMotion('close',options), cancel,
    whenSettled:()=>Promise.all([motion?.promise,layoutMotion?.promise].filter(Boolean)).then(results=>({finished:results.every(r=>r.finished),results})),
    isAnimating:()=>!!motion||!!layoutMotion, isCurrent:id=>!disposed && id===latestMotionId, getStatus:status, dispose });
  if(media?.addEventListener)media.addEventListener('change',onPreference);else media?.addListener?.(onPreference);
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',onReady,{once:true});
  mount();
})();
