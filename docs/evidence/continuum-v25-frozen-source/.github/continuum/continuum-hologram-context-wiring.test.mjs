// Defensive source-contract tests for the shared Android on-device UI/runtime.
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const root='app/src/main/java/at/heinz/deviceai/';
const orb=readFileSync(root+'ContinuumHologramView.kt','utf8');
const activity=readFileSync(root+'AgentActivity.kt','utf8');
const cognition=readFileSync(root+'learning/ContinuumCognitiveRuntime.kt','utf8');

const segment=(src,start,end)=>{
 const a=src.indexOf(start);
 assert.notEqual(a,-1,'Missing '+start);
 const b=src.indexOf(end,a+start.length);
 assert.ok(b>a,'Missing ending '+end);
 return src.slice(a,b);
};

test('single hologram frame callback advances phase independently of Canvas onDraw',()=>{
 const callback=segment(orb,'private val drawFrame = Runnable {','/** Owns the single visible-view frame callback');
 const draw=segment(orb,'override fun onDraw(canvas: Canvas) {','private fun advanceFrame()');
 assert.ok(callback.includes('advanceFrame()'));
 assert.ok(callback.includes('postInvalidateOnAnimation()'));
 assert.ok(callback.includes('scheduleFrame()'));
 assert.ok(!draw.includes('advanceFrame()'), 'onDraw must not drive the motion clock');
 assert.ok(!draw.includes('scheduleFrame()'), 'onDraw must not queue duplicate callbacks');
 assert.ok(orb.includes('if (canAnimate() && !framePending)'));
});
test('Android window visibility and focus can restore orbital clock without cached view visibility',()=>{
 assert.ok(orb.includes('override fun onVisibilityChanged(changedView: View, visibility: Int)'));
 assert.ok(orb.includes('override fun onWindowFocusChanged(hasWindowFocus: Boolean)'));
 assert.ok(orb.includes('isAttachedToWindow && isShown && windowVisibility == VISIBLE'));
 assert.ok(!orb.includes('aggregatedVisible'), 'a cached visibility flag can freeze an attached View');
 assert.ok(orb.includes('visibleFrameCount++'), 'frames must be countable for device diagnosis');
 assert.ok(orb.includes('motionDiagnostics()'), 'owner must see blocked system gate');
});
test('foreground lifecycle explicitly pauses, cancels, and resumes hologram',()=>{
 const paused=segment(activity,'override fun onPause() {','override fun onSaveInstanceState(');
 const resumed=segment(activity,'override fun onResume() {','override fun onPause()');
 const policy=segment(orb,'private fun updateMotionPolicy() {','private fun settleState()');
 assert.ok(paused.includes('hologramView?.setForegroundActive(false)'));
 assert.ok(resumed.includes('hologramView?.setForegroundActive(true)'));
 assert.ok(activity.includes('it.setForegroundActive(foregroundLearningActive)'),
   'Fold/home rebuild must inherit current foreground status');
 assert.ok(orb.includes('foregroundActive && motionAllowed && isAttachedToWindow'));
 assert.ok(policy.includes('removeCallbacks(drawFrame)'));
 assert.ok(policy.includes('framePending = false'));
 assert.ok(policy.includes('lastFrameMs = 0L'));
 assert.ok(policy.includes('scheduleFrame()'));
 assert.ok(orb.includes('ValueAnimator.areAnimatorsEnabled()'));
 assert.ok(orb.includes('!powerManager.isPowerSaveMode'));
});
test('local model prompt never reads a profile-wide old dialogue after session change',()=>{
 const reply=segment(cognition,'fun reply(input:String):ContinuumCognitiveTurn {','internal fun localPrompt(question:String,resolved:String):String');
 assert.ok(reply.includes('localPrompt(clean,currentContext)'));
 assert.ok(reply.includes('val scopedPrevious=lastScopedGroundedQuestion'));
 assert.ok(reply.includes('val currentContext=if(followUp && scopedPrevious!=null)'));
 assert.ok(!reply.includes('localPrompt(input,turn.resolvedQuery)'));
 assert.ok(cognition.includes('@Synchronized fun reply(input:String):ContinuumCognitiveTurn'));
 assert.ok(cognition.includes('priorScopedGroundedQuestion:String?=null'));
 assert.ok(cognition.includes('val nextScopedGroundedQuestion:String?=null'));
 assert.ok(cognition.includes('lastScopedGroundedQuestion=when {'));
 assert.ok(!reply.includes('core.dialogueHistory()'));
 const prompt=segment(cognition,'internal fun localPrompt(question:String,resolved:String):String','companion object {');
 assert.ok(prompt.includes('if (candidate==clean)'));
 assert.ok(prompt.includes('Bitte frage nach'));
 assert.ok(prompt.includes('nicht unabhängig bestätigt'));
 assert.ok(!prompt.includes('prior.lastOrNull'));
});

test('each AgentActivity chat turn captures only its session-scoped local-model topic',()=>{
 assert.ok(activity.includes('private data class LocalPromptSession(val id:String,val groundedTopic:String?)'));
 assert.ok(activity.includes('private var scopedLocalPromptSession:LocalPromptSession?=null'));
 assert.ok(activity.includes('val requestSessionId=sessionId'));
 assert.ok(activity.includes('scopedLocalPromptSession?.takeIf { it.id==requestSessionId }?.groundedTopic'));
 assert.ok(activity.includes('conversationId=requestSessionId,priorScopedGroundedQuestion=priorScopedTopic'));
 assert.ok(activity.includes('cognitive.localModelPrompt,cognitive.nextScopedGroundedQuestion'));
 assert.ok(activity.includes('scopedLocalPromptSession=LocalPromptSession(requestSessionId,nextTopic)'));
 assert.ok(activity.includes('scopedLocalPromptSession=null'), 'new chat must clear old session topic');
});
