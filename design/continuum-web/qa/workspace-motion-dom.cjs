/* Motion lifecycle/continuity checks in jsdom. WAAPI is a controllable test model;
 * RAF executes the real fallback. This is not a rendered browser or GPU test. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { JSDOM, VirtualConsole } = require('../interop/require-dev.cjs')('jsdom');
const base = path.resolve(__dirname, '../continuum-preview');
const html = fs.readFileSync(path.join(base, 'index.html'), 'utf8');
const source = fs.readFileSync(path.join(base, 'workspace-motion.js'), 'utf8');
const app = fs.readFileSync(path.join(base, 'app.js'), 'utf8');
const flush = async () => { for(let i=0;i<5;i++)await Promise.resolve(); };
const near = (a,b,message,tolerance=1e-7) => assert.ok(Math.abs(a-b)<=tolerance, `${message}: ${a} versus ${b}`);
function samePose(a,b) { for(const key of Object.keys(a))near(a[key],b[key],key); }

function fixture(options={}) {
  const errors=[],vc=new VirtualConsole();vc.on('jsdomError',e=>errors.push(e.message));
  const dom=new JSDOM(html,{url:'https://continuum.local/',runScripts:'outside-only',pretendToBeVisual:true,virtualConsole:vc});
  const w=dom.window,d=w.document;
  let clock=0,serial=0,reads=0,rafRequests=0,styleCalls=0;
  const timers=new Map(),animationCalls=[],mediaListeners=new Set();
  Object.defineProperty(w.performance,'now',{value:()=>clock});
  const schedule=(fn,delay=0,interval=0)=>{const id=++serial;timers.set(id,{at:clock+Math.max(0,delay),fn,interval});return id;};
  w.setTimeout=(fn,delay=0,...args)=>schedule(()=>fn(...args),Number(delay)||0);
  w.setInterval=(fn,delay=0,...args)=>schedule(()=>fn(...args),Math.max(1,Number(delay)||1),Math.max(1,Number(delay)||1));
  w.clearTimeout=w.clearInterval=id=>timers.delete(id);
  w.requestAnimationFrame=fn=>{rafRequests++;return schedule(()=>fn(clock),16);};w.cancelAnimationFrame=w.clearTimeout;
  const preference={matches:!!options.reduced,addEventListener:(type,fn)=>mediaListeners.add(fn),removeEventListener:(type,fn)=>mediaListeners.delete(fn)};
  w.matchMedia=()=>preference;
  const setReduced=value=>{preference.matches=value;for(const fn of mediaListeners)fn({matches:value});};
  const dimensions={left:100,top:200,width:640,height:420};
  const originalBounds=w.Element.prototype.getBoundingClientRect;
  w.Element.prototype.getBoundingClientRect=function(){
    if(!this.classList.contains('workspace-motion-stage'))return originalBounds.call(this);
    reads++;
    const t=w.ContinuumWorkspaceMotion?.getStatus().layout||{x:0,y:0,sx:1,sy:1};
    const height=dimensions.height+(d.querySelector('.app-shell')?.classList.contains('expanded')?180:0);
    const left=dimensions.left+t.x,top=dimensions.top+t.y,width=dimensions.width*t.sx,h=height*t.sy;
    return {left,top,width,height:h,right:left+width,bottom:top+h,x:left,y:top,toJSON(){return this;}};
  };
  const nativeStyle=w.getComputedStyle.bind(w);w.getComputedStyle=(...args)=>{styleCalls++;return nativeStyle(...args);};
  if(options.waapi){
    Object.defineProperty(d,'timeline',{value:{get currentTime(){return clock;}}});
    w.Element.prototype.animate=function(frames,settings){
      const record={target:this,frames,settings,cancelled:false,start:clock,timer:null,completed:false};
      animationCalls.push(record);
      if(options.throwOnOpacity&&Object.hasOwn(frames[0],'opacity'))throw new Error('Test: opacity backend unavailable');
      let resolve,reject;const finished=new Promise((a,b)=>{resolve=a;reject=b;});
      function scheduleEnd(){timers.delete(record.timer);record.timer=schedule(()=>{record.completed=true;resolve(animation);},Math.max(0,record.start+settings.duration-clock));}
      const animation={finished,get currentTime(){return record.cancelled?null:Math.max(0,Math.min(settings.duration,clock-record.start));},get startTime(){return record.start;},set startTime(value){record.start=value;scheduleEnd();},cancel(){if(record.cancelled)return;record.cancelled=true;timers.delete(record.timer);reject(new Error('cancelled'));}};
      record.animation=animation;scheduleEnd();return animation;
    };
  }
  w.ContinuumScene={setState(){},resize(){},setIntensity(){},setQuality(){}};
  w.HTMLElement.prototype.scrollIntoView=function(){};
  const shell=d.querySelector('.workspace-shell'),closeButton=d.querySelector('#close-button'),parent=shell.parentElement;
  w.eval(source);if(options.app)w.eval(app);
  async function advance(ms){
    const end=clock+ms;let count=0;
    while(true){const next=[...timers.entries()].filter(([,t])=>t.at<=end).sort((a,b)=>a[1].at-b[1].at||a[0]-b[0])[0];if(!next)break;
      assert.ok(++count<10000,'Timer runaway');const[id,t]=next;clock=t.at;if(t.interval)timers.set(id,{...t,at:t.at+t.interval});else timers.delete(id);t.fn();await flush();}
    clock=end;await flush();
  }
  const api=()=>w.ContinuumWorkspaceMotion;
  const click=selector=>{const n=d.querySelector(selector);assert.ok(n,selector);n.dispatchEvent(new w.MouseEvent('click',{bubbles:true,cancelable:true}));};
  return {w,d,api,advance,dimensions,animationCalls,setReduced,errors,click,shell,closeButton,parent,
    metrics:()=>({reads,rafRequests,styleCalls,clock,pending:timers.size}),close(){api()?.dispose();w.close();}};
}
const tests=[];const test=(name,fn,options={})=>tests.push({name,fn,options});

test('One persistent shell and stable controls survive nested mutations',async f=>{
  let presses=0;f.closeButton.addEventListener('click',()=>presses++);
  const value=f.api().transition(()=>{f.api().transition(()=>{f.shell.dataset.test='inner';},{kind:'resize'});return 47;},{kind:'switch'});
  assert.equal(value,47);assert.equal(f.d.querySelectorAll('.workspace-motion-stage').length,1);assert.equal(f.d.querySelectorAll('.workspace-motion-front').length,1);
  for(let i=0;i<20;i++)f.api().transition(()=>{f.shell.dataset.test=String(i);},{kind:'resize'});
  assert.equal(f.d.querySelector('.workspace-shell'),f.shell);assert.equal(f.d.querySelector('#close-button'),f.closeButton);
  f.click('#close-button');assert.equal(presses,1);await f.advance(400);assert.equal(f.api().isAnimating(),false);
});

test('Open is a 950ms continuous rear/side reveal with no frame-by-frame layout reads',async f=>{
  let resolved=false;const completion=f.api().open().then(r=>{resolved=true;return r;});
  const initialReads=f.metrics().reads;await f.advance(380);
  assert.ok(f.api().getStatus().pose.ry < -100,'Back plate must enter view');near(f.api().getStatus().pose.alpha,1,'Solid material during turn');
  await f.advance(569);assert.equal(resolved,false);assert.equal(f.api().getStatus().duration,950);
  await f.advance(12);assert.equal((await completion).finished,true);assert.equal(f.api().getStatus().phase,'resting');
  near(f.api().getStatus().pose.ry,0,'Final yaw');assert.equal(f.metrics().reads,initialReads);assert.equal(f.metrics().styleCalls,0);
});

test('Open→close→open preserves exact current pose and cancels stale completions',async f=>{
  const opening=f.api().open();await f.advance(240);const beforeClose=f.api().getStatus().pose;
  const closing=f.api().close();samePose(f.api().getStatus().pose,beforeClose);assert.equal((await opening).cancelled,true);
  await f.advance(144);const beforeReopen=f.api().getStatus().pose;
  const reopened=f.api().open();samePose(f.api().getStatus().pose,beforeReopen);
  assert.equal((await closing).finished,false);await f.advance(960);assert.equal((await reopened).finished,true);
  assert.equal(f.api().getStatus().phase,'resting');assert.equal(f.d.querySelector('.workspace-motion-stage').inert,false);
});

test('Close lasts 650ms and its completed ID becomes stale after a new intent',async f=>{
  let complete=false;const closing=f.api().close().then(r=>{complete=true;return r;});
  await f.advance(640);assert.equal(complete,false);assert.ok(f.api().getStatus().pose.ry < -105);assert.equal(f.api().getStatus().duration,650);
  await f.advance(16);const result=await closing;assert.equal(result.finished,true);assert.equal(f.api().getStatus().phase,'closed');assert.equal(f.api().isCurrent(result.id),true);
  f.api().transition(()=>{}, {kind:'switch'});assert.equal(f.api().isCurrent(result.id),false);assert.equal(f.d.querySelector('.workspace-motion-stage').inert,false);
});

test('Duplicate open and nested resize calls retain one active opening deadline',async f=>{
  const first=f.api().open();await f.advance(192);const id=f.api().getStatus().id;
  assert.equal(f.api().open(),first);
  f.api().transition(()=>{f.api().transition(()=>{}, {kind:'open'});},{kind:'resize'});
  assert.equal(f.api().getStatus().id,id);near(f.api().getStatus().progress,192/950,'Original progress');
  await f.advance(768);assert.equal((await first).finished,true);
});

test('FLIP preserves the presented rectangle and can reverse while expanding',async f=>{
  const before={...f.dimensions};
  f.api().transition(()=>{Object.assign(f.dimensions,{left:70,top:170,width:900,height:650});},{kind:'resize'});
  let t=f.api().getStatus().layout;near(f.dimensions.left+t.x,before.left,'Initial x');near(f.dimensions.top+t.y,before.top,'Initial y');near(f.dimensions.width*t.sx,before.width,'Initial width');near(f.dimensions.height*t.sy,before.height,'Initial height');
  await f.advance(128);t=f.api().getStatus().layout;
  const visible={left:f.dimensions.left+t.x,top:f.dimensions.top+t.y,width:f.dimensions.width*t.sx,height:f.dimensions.height*t.sy};
  f.api().transition(()=>Object.assign(f.dimensions,before),{kind:'resize'});
  t=f.api().getStatus().layout;near(f.dimensions.left+t.x,visible.left,'Reversal x');near(f.dimensions.top+t.y,visible.top,'Reversal y');near(f.dimensions.width*t.sx,visible.width,'Reversal width');near(f.dimensions.height*t.sy,visible.height,'Reversal height');
  const reads=f.metrics().reads;await f.advance(352);assert.equal(f.metrics().reads,reads);assert.equal(f.api().isAnimating(),false);near(f.api().getStatus().layout.sy,1,'Final scale');
});

test('Reduced motion keeps rotation flat and fades without changing app timing',async f=>{
  const opening=f.api().open();await f.advance(400);let p=f.api().getStatus().pose;near(p.ry,0,'No yaw');near(p.z,0,'No depth flight');assert.equal(f.api().getStatus().duration,950);
  await f.advance(560);await opening;const closing=f.api().close();await f.advance(320);p=f.api().getStatus().pose;near(p.ry,0,'No closing yaw');assert.ok(p.alpha<1&&p.alpha>0);assert.equal(f.api().getStatus().duration,650);
  await f.advance(336);assert.equal((await closing).finished,true);
},{reduced:true});

test('Changing reduced-motion preference cancels flight and settles from current pose',async f=>{
  const opening=f.api().open();await f.advance(240);const before=f.api().getStatus().pose;f.setReduced(true);samePose(f.api().getStatus().pose,before);assert.equal((await opening).cancelled,true);
  await f.advance(192);assert.equal(f.api().getStatus().phase,'resting');near(f.api().getStatus().pose.ry,0,'Settled yaw');
});

test('WAAPI tracks contain only transform/opacity and share a clock',async f=>{
  const opening=f.api().open();assert.equal(f.animationCalls.length,2);
  for(const call of f.animationCalls){assert.equal(call.settings.duration,950);assert.equal(call.settings.easing,'linear');for(const frame of call.frames)for(const key of Object.keys(frame))assert.ok(['offset','transform','opacity'].includes(key),`Unexpected animated property ${key}`);}
  assert.equal(f.animationCalls[0].animation.startTime,f.animationCalls[1].animation.startTime);
  await f.advance(250);const before=f.api().getStatus().pose;const closing=f.api().close();samePose(f.api().getStatus().pose,before);assert.equal((await opening).finished,false);
  assert.ok(f.animationCalls.slice(0,2).every(a=>a.cancelled));assert.equal(f.metrics().rafRequests,0);
  await f.advance(650);assert.equal((await closing).finished,true);assert.equal(f.api().isAnimating(),false);
},{waapi:true});

test('A failing WAAPI backend releases effects and uses the actual RAF fallback',async f=>{
  const opening=f.api().open();assert.equal(f.api().getStatus().backend,'raf');assert.equal(f.animationCalls[0].cancelled,true);
  await f.advance(384);assert.ok(f.api().getStatus().pose.ry < -100);await f.advance(576);assert.equal((await opening).finished,true);
},{waapi:true,throwOnOpacity:true});

test('Real app: navigate→forming retains the entry; 950ms state and abort still work',async f=>{
  await f.advance(400);f.w.ContinuumPreview.openDocument(true);assert.equal(f.w.ContinuumPreview.getState().state,'forming');
  await f.advance(380);assert.ok(f.api().getStatus().pose.ry < -100,'Same-turn navigate must not suppress entry');
  await f.advance(569);assert.equal(f.w.ContinuumPreview.getState().state,'forming');await f.advance(1);assert.equal(f.w.ContinuumPreview.getState().state,'working');
  f.click('#close-button');await f.advance(192);f.w.ContinuumPreview.navigate('files');await f.advance(1000);
  assert.equal(f.w.ContinuumPreview.getState().section,'files');assert.equal(f.shell.classList.contains('returning'),false);assert.equal(f.shell.dataset.motionPhase,'resting');
},{app:true});

test('Real expand action uses FLIP without replacing the workspace shell',async f=>{
  await f.advance(400);f.click('#expand-button');assert.ok(f.d.querySelector('.app-shell').classList.contains('expanded'));
  assert.equal(f.api().getStatus().layoutAnimating,true);assert.ok(f.api().getStatus().layout.sy<1);assert.equal(f.d.querySelector('.workspace-shell'),f.shell);
  await f.advance(128);f.click('#expand-button');assert.equal(f.d.querySelector('.app-shell').classList.contains('expanded'),false);await f.advance(352);assert.equal(f.api().isAnimating(),false);
},{app:true});

test('Disposal cancels outstanding work and restores the same DOM nodes',async f=>{
  const pending=f.api().open();await f.advance(80);f.api().dispose();assert.equal((await pending).cancelled,true);
  assert.equal(f.d.querySelector('.workspace-shell'),f.shell);assert.equal(f.shell.parentElement,f.parent);assert.equal(f.d.querySelector('#close-button'),f.closeButton);
  assert.equal(f.d.querySelector('.workspace-motion-stage'),null);assert.equal(f.d.querySelector('.workspace-motion-front'),null);assert.equal(f.shell.hasAttribute('data-motion-managed'),false);
  f.w.eval(source);assert.equal(f.d.querySelectorAll('.workspace-motion-stage').length,1);assert.equal(f.api().isAnimating(),false);
});

(async()=>{
  const report={suite:'workspace-motion',runtime:'jsdom + controlled clock',waapi:'test model, not browser execution',visualOrGPUVerification:false,tests:[]};
  for(const item of tests){let f;try{f=fixture(item.options);await item.fn(f);await flush();assert.deepEqual(f.errors,[]);report.tests.push({name:item.name,passed:true});console.log('PASS '+item.name);}catch(error){report.tests.push({name:item.name,passed:false,error:error.stack});console.error('FAIL '+item.name+'\n'+error.stack);}finally{f?.close();await flush();}}
  report.passed=report.tests.filter(t=>t.passed).length;report.failed=report.tests.length-report.passed;
  fs.writeFileSync(path.join(__dirname,'workspace-motion-results.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify({passed:report.passed,failed:report.failed,total:report.tests.length,visualOrGPUVerification:false}));process.exitCode=report.failed?1:0;
})().catch(error=>{console.error(error);process.exitCode=1;});
