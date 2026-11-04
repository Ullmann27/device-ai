package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.*
import kotlin.math.abs
import kotlin.math.hypot

/** Executes the real mapper/reducer; the runner supplies only the GLB target-name declaration. */
private fun pose(state: AvatarState = AvatarState.IDLE, viseme: Viseme = Viseme.SIL,
                 jaw: Float = 0f, amount: Float = 1f, observed: Boolean = false) =
    AvatarPose(state, AvatarExpression.NEUTRAL, 0f, 0f, jaw, viseme, amount, jaw, observed)
private fun mapper() = AvatarMorphMapper(AvatarGlbContract.TARGETS)
private fun AvatarMorphMapper.weight(name: String) = weights[AvatarGlbContract.TARGETS.indexOf(name)]
private fun near(expected: Float, actual: Float, epsilon: Float = .00001f) {
    check(expected.isFinite() && actual.isFinite() && abs(expected - actual) <= epsilon) {
        "expected=$expected actual=$actual tolerance=$epsilon"
    }
}
private fun angles(m: AvatarMorphMapper) = listOf(m.headYaw, m.headPitch, m.eyeYaw, m.eyePitch)
private fun closed(m: AvatarMorphMapper) {
    for (v in Viseme.entries) near(0f, m.weight(v.name), 0f)
    near(0f, m.weight("JawOpen"), 0f); near(0f, m.jawPitch, 0f)
}

fun main() {
    var passed = 0
    val failures = mutableListOf<String>()
    fun test(name: String, body: () -> Unit) {
        try { body(); passed++; println("PASS $name") }
        catch (failure: Throwable) {
            failures += name
            println("FAIL $name: ${failure::class.simpleName}: ${failure.message}")
        }
    }
    test("idle gaze holds a fixation instead of continuously sweeping") {
        val m = mapper(); m.update(pose(), .5f, true)
        val yaw = m.eyeYaw; val pitch = m.eyePitch
        m.update(pose(), 1f, true); near(yaw, m.eyeYaw); near(pitch, m.eyePitch)
    }
    test("idle gaze moves to a new bounded fixation") {
        val m = mapper(); m.update(pose(), 2.8f, true)
        val yaw = m.eyeYaw; val pitch = m.eyePitch
        m.update(pose(), 3.4f, true)
        check(hypot((m.eyeYaw - yaw).toDouble(), (m.eyePitch - pitch).toDouble()) > .1)
    }
    test("double blink has two closures separated by reopened lids") {
        val m = mapper(); m.update(pose(), 13.425f, true)
        check(m.weight("BlinkLeft") > .99f) { "missing first closure: ${m.weight("BlinkLeft")}" }
        m.update(pose(), 13.575f, true); near(0f, m.weight("BlinkLeft"), 0f)
        m.update(pose(), 13.725f, true); check(m.weight("BlinkLeft") > .99f)
    }
    val blinkStarts = mutableListOf<Float>()
    test("one 460-second cycle contains 100 single and 20 extra blink closures") {
        val m = mapper(); var wasClosed = false
        for (i in 0..46_000) {
            val t = i / 100f; m.update(pose(), t, true)
            val isClosed = m.weight("BlinkLeft") > .5f
            if (isClosed && !wasClosed) blinkStarts += t
            wasClosed = isClosed
        }
        check(blinkStarts.size == 120) { "closures=${blinkStarts.size}, expected=120" }
    }
    test("every sampled 60-second window stays within 12 to 20 closures") {
        for (start in 0..400) {
            val count = blinkStarts.count { it >= start && it < start + 60 }
            check(count in 12..20) { "window=$start closures=$count" }
        }
    }
    test("combined cosmetic gaze and head drift remains below six degrees") {
        val m = mapper()
        for (i in 0..23_000) {
            m.update(pose(), i / 50f, true)
            check(hypot((m.headYaw + m.eyeYaw).toDouble(), (m.headPitch + m.eyePitch).toDouble()) <= 6.0)
            check(m.weights.all { it.isFinite() && it in 0f..1f })
        }
    }
    test("same timestamp is independent of prior cosmetic frame sequence") {
        for (t in listOf(.04f, 3.09f, 13.425f, 84.22f, 459.99f)) {
            val a = mapper(); val b = mapper()
            for (i in 0..30) a.update(pose(), i * .013f, true)
            for (old in listOf(12f, 459.5f, -50f, 4f, Float.NaN)) b.update(pose(), old, true)
            a.update(pose(), t, true); b.update(pose(), t, true)
            angles(a).zip(angles(b)).forEach { (x,y) -> near(x,y,0f) }
            a.weights.zip(b.weights).forEach { (x,y) -> near(x,y,0f) }
        }
    }
    test("cosmetic motion is continuous across the existing clock wrap") {
        val m = mapper(); m.update(pose(), 459.999f, true); val before = angles(m)
        m.update(pose(), 0f, true)
        before.zip(angles(m)).forEach { (a,b) -> near(a,b,.003f) }
        near(0f, m.weight("BlinkLeft"), 0f)
    }
    test("whole-cycle clock offsets preserve cosmetic pose") {
        for (t in listOf(.5f, 3.5f, 13.5f, 84.5f)) {
            val a = mapper(); val b = mapper(); a.update(pose(),t,true); b.update(pose(),t+460f,true)
            angles(a).zip(angles(b)).forEach { (x,y) -> near(x,y,.001f) }
            near(a.weight("BlinkLeft"),b.weight("BlinkLeft"),.001f)
        }
    }
    test("observed gaze overrides every idle offset") {
        val m = mapper(); val p = pose(observed=true).copy(gazeX=.25f,gazeY=-.4f)
        for (t in listOf(1f,13.425f,200f)) {
            m.update(p,t,true); near(2f,m.headYaw,0f); near(2f,m.headPitch,0f)
            near(4.25f,m.eyeYaw,0f); near(4f,m.eyePitch,0f)
        }
    }
    test("all non-idle runtime states retain their actual gaze") {
        for (state in AvatarState.entries.filter { it != AvatarState.IDLE }) {
            val m=mapper(); m.update(pose(state=state).copy(gazeX=.2f,gazeY=.3f),13.425f,true)
            near(1.6f,m.headYaw); near(-1.5f,m.headPitch); near(3.4f,m.eyeYaw); near(-3f,m.eyePitch)
        }
    }
    test("reduced motion clears cosmetic motion and blinking immediately") {
        val m=mapper(); m.update(pose(),13.425f,true); m.update(pose(),13.425f,false)
        angles(m).forEach { near(0f,it,0f) }; m.weights.forEach { near(0f,it,0f) }
    }
    test("invalid time clears cosmetics without producing nonfinite values") {
        val m=mapper()
        for (t in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)) {
            m.update(pose(),13.425f,true); m.update(pose(),t,true)
            angles(m).forEach { near(0f,it,0f) }; near(0f,m.weight("BlinkLeft"),0f)
        }
        for (t in listOf(Float.MAX_VALUE,-Float.MAX_VALUE,-.5f)) {
            m.update(pose(),t,true); check(angles(m).all { it.isFinite() })
            check(m.weights.all { it.isFinite() && it in 0f..1f })
        }
    }
    test("paused mapper resumes at current time without catch-up") {
        val a=mapper(); val b=mapper()
        a.update(pose(),13.425f,true); a.update(pose(),14f,false); a.update(pose(),200f,true)
        b.update(pose(),200f,true)
        angles(a).zip(angles(b)).forEach { (x,y) -> near(x,y,0f) }
    }
    test("blink targets remain name-bound under reversed asset ordering") {
        val names=AvatarGlbContract.TARGETS.reversed(); val a=mapper(); val b=AvatarMorphMapper(names)
        a.update(pose(),13.425f,true); b.update(pose(),13.425f,true)
        for (name in names) near(a.weight(name),b.weights[names.indexOf(name)],0f)
    }
    test("both eyelids use the same bounded blink") {
        val m=mapper()
        for (i in 0..2_300) { m.update(pose(),i/100f,true); near(m.weight("BlinkLeft"),m.weight("BlinkRight"),0f) }
    }
    test("existing 150ms primary blink timing stays compatible") {
        val m=mapper(); m.update(pose(),4.525f,true); near(1f,m.weight("BlinkLeft"),0f)
        m.update(pose(),4.44f,true); near(0f,m.weight("BlinkLeft"),0f)
        m.update(pose(),4.61f,true); near(0f,m.weight("BlinkLeft"),0f)
    }
    test("existing 70ms speech crossfade is unchanged") {
        val m=mapper(); m.update(pose(AvatarState.SPEAKING,Viseme.AA,.4f),1f,true)
        m.update(pose(AvatarState.SPEAKING,Viseme.O,.4f),1.035f,true)
        near(.5f,m.weight("AA"),.001f); near(.5f,m.weight("O"),.001f)
        m.update(pose(AvatarState.SPEAKING,Viseme.O,.4f),1.08f,true)
        near(0f,m.weight("AA"),0f); near(1f,m.weight("O"),0f)
    }
    test("bilabial seal closes outgoing vowel and measured jaw immediately") {
        val m=mapper(); m.update(pose(AvatarState.SPEAKING,Viseme.AA,.6f),1f,true)
        for (i in 1..5) {
            m.update(pose(AvatarState.SPEAKING,Viseme.PP,.6f),1f+i*.016f,true)
            near(0f,m.weight("AA"),0f); near(1f,m.weight("PP"),0f)
            near(0f,m.weight("JawOpen"),0f); near(0f,m.jawPitch,0f)
        }
    }
    test("silence immediately closes an active mouth transition") {
        val m=mapper(); m.update(pose(AvatarState.SPEAKING,Viseme.AA,.6f),1f,true)
        m.update(pose(AvatarState.SPEAKING,Viseme.O,.6f),1.016f,true)
        m.update(pose(AvatarState.SPEAKING),1.032f,true); closed(m)
    }
    test("reduced motion clears speech and does not revive old vowels") {
        val m=mapper(); m.update(pose(AvatarState.SPEAKING,Viseme.AA,.6f),1f,true)
        m.update(pose(AvatarState.SPEAKING,Viseme.O,.6f),1.016f,true)
        m.update(pose(AvatarState.SPEAKING,Viseme.O,.6f),1.032f,false); closed(m)
        m.update(pose(AvatarState.SPEAKING,Viseme.PP,.6f),1.048f,true)
        near(0f,m.weight("AA"),0f); near(0f,m.weight("O"),0f); near(1f,m.weight("PP"),0f)
    }
    test("repeated speech time preserves the blend and rollback starts a new epoch") {
        val m=mapper(); m.update(pose(AvatarState.SPEAKING,Viseme.AA,.6f),1f,true)
        m.update(pose(AvatarState.SPEAKING,Viseme.O,.6f),1.016f,true)
        val before=m.weights.copyOf(); val jawBefore=m.jawPitch
        m.update(pose(AvatarState.SPEAKING,Viseme.O,.6f),1.016f,true)
        check(before.contentEquals(m.weights)); near(jawBefore,m.jawPitch,0f)
        m.update(pose(AvatarState.SPEAKING,Viseme.O,.6f),.5f,true)
        near(0f,m.weight("AA"),0f); near(1f,m.weight("O"),0f)
    }
    test("existing reducer still rejects interrupted utterance callbacks") {
        val r=AvatarPoseReducer(); val m=mapper()
        r.apply(AvatarEvent.SpeechStarted("old","synthetic",0))
        r.apply(AvatarEvent.AudioLevel("old",1f,1))
        r.apply(AvatarEvent.VisemeCue("old",Viseme.AA,1f,200,1))
        r.apply(AvatarEvent.SpeechEnded("old",true,2))
        r.apply(AvatarEvent.AudioLevel("old",1f,3))
        r.apply(AvatarEvent.VisemeCue("old",Viseme.O,1f,200,3))
        m.update(r.pose(3),13.425f,true); closed(m)
    }
    test("existing observed-gaze consent clear still reaches the mapper") {
        val r=AvatarPoseReducer(); val m=mapper()
        r.apply(AvatarEvent.GazeObserved(GazeTarget(.5f,.2f),90,10)); m.update(r.pose(10),1f,true)
        near(4f,m.headYaw); near(8.5f,m.eyeYaw)
        r.apply(AvatarEvent.GazeCleared(11)); val p=r.pose(11); check(!p.gazeObserved)
        m.update(p,1f,false); near(0f,m.headYaw,0f); near(0f,m.eyeYaw,0f)
    }
    test("cosmetic idle motion never invents speech weights") {
        val m=mapper()
        for (i in 0..2_300) { m.update(pose(),i/100f,true); closed(m) }
    }
    test("malformed target names still fail closed") {
        for (names in listOf(AvatarGlbContract.TARGETS.dropLast(1),AvatarGlbContract.TARGETS+"AA")) {
            var rejected=false
            try { AvatarMorphMapper(names) } catch (_: IllegalArgumentException) { rejected=true }
            check(rejected)
        }
    }
    println("RESULT passed=$passed failed=${failures.size} total=${passed+failures.size}")
    check(failures.isEmpty()) { "Failed checks: ${failures.joinToString()}" }
}
