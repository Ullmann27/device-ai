package at.heinz.deviceai

import at.heinz.deviceai.avatar.*
import at.heinz.deviceai.avatar3d.*
import kotlin.math.abs
import kotlin.random.Random

/** Actual copied source is compiled alongside this probe. No Android/rendering mocks are run. */
fun main(args: Array<String>) {
    val label = args.single()
    val names = AvatarGlbContract.TARGETS
    val facts = mutableListOf<String>()
    var assertions = 0
    fun ensure(ok: Boolean, label: String) { check(ok) { label }; assertions++ }
    fun fact(key: String, value: Number) { facts += "\"$key\":$value" }
    fun pose(viseme: Viseme, weight: Float = 1f, jaw: Float = .8f) =
        AvatarPose(AvatarState.SPEAKING, AvatarExpression.NEUTRAL, 0f, 0f, jaw, viseme, weight, .8f)
    fun AvatarMorphMapper.at(name: String) = weights[names.indexOf(name)]

    val mapper = AvatarMorphMapper(names)
    mapper.update(pose(Viseme.AA), 1f, true)
    fact("aaJawWeight", mapper.at("JawOpen")); fact("aaJawPitchDegrees", mapper.jawPitch)
    mapper.update(pose(Viseme.PP), 2f, true)
    fact("settledPpJawWeightAtInput08", mapper.at("JawOpen"))
    fact("settledPpJawPitchDegreesAtInput08", mapper.jawPitch)
    ensure(mapper.at("PP") == 1f, "PP actual target")
    ensure(mapper.at("JawOpen") == if (label == "p503") 0f else .8f, "PP snapshot behavior")

    val repeated = AvatarMorphMapper(names)
    repeated.update(pose(Viseme.AA), 1f, true)
    repeated.update(pose(Viseme.O), 1.01f, true)
    val before = repeated.weights.copyOf()
    repeated.update(pose(Viseme.O), 1.01f, true)
    val repeatedDelta = before.indices.maxOf { abs(before[it]-repeated.weights[it]) }
    fact("sameTimestampMaximumWeightChange", repeatedDelta)
    ensure(if (label == "p503") repeatedDelta == 0f else repeatedDelta > .8f, "Repeated-time snapshot behavior")

    val random = Random(491503)
    val rapid = AvatarMorphMapper(names)
    var t = 0f; var maxSum = 0f; var maxJawPitch = 0f
    repeat(10000) {
        t += listOf(.001f,.008f,.016f,.033f,.070f,.2f)[random.nextInt(6)]
        rapid.update(pose(Viseme.entries[random.nextInt(15)], random.nextFloat(), random.nextFloat()), t, true)
        val sum = (1..14).sumOf { rapid.weights[it].toDouble() }.toFloat()
        maxSum = maxOf(maxSum,sum); maxJawPitch=maxOf(maxJawPitch,rapid.jawPitch)
        ensure(rapid.weights.all { it.isFinite() && it in 0f..1f }, "Finite bounded rapid weights")
        ensure(sum <= 1.00001f && rapid.jawPitch in 0f..12.0001f, "Aggregate opening bounds")
    }
    fact("rapidSamples",10000); fact("maximumVisemeWeightSum",maxSum);fact("maximumJawPitchDegrees",maxJawPitch)
    for (bad in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-100f)) {
        rapid.update(pose(Viseme.O,bad,bad).copy(gazeX=bad,gazeY=bad),bad,true)
        ensure(rapid.weights.all { it.isFinite() && it in 0f..1f } &&
            listOf(rapid.headYaw,rapid.headPitch,rapid.eyeYaw,rapid.eyePitch,rapid.jawPitch).all { it.isFinite() }, "Nonfinite inputs")
    }
    rapid.update(pose(Viseme.AA),2f,true)
    rapid.update(pose(Viseme.SIL,0f,0f),2.016f,true)
    ensure((0..14).all { rapid.weights[it]==0f } && rapid.at("JawOpen")==0f && rapid.jawPitch==0f,"Silence immediate")
    rapid.update(pose(Viseme.AA),3f,true); rapid.update(pose(Viseme.AA),3.016f,false)
    ensure((0..14).all { rapid.weights[it]==0f } && rapid.jawPitch==0f,"Motion disabled immediate")

    val reducer=AvatarPoseReducer()
    reducer.apply(AvatarEvent.SpeechStarted("first","synthetic",0))
    reducer.apply(AvatarEvent.VisemeCue("first",Viseme.AA,1f,1000,1))
    reducer.apply(AvatarEvent.AudioLevel("first",1f,2))
    ensure(reducer.pose(2).jawOpen>0f && reducer.pose(2).viseme==Viseme.AA,"Started mouth")
    reducer.apply(AvatarEvent.SpeechEnded("first",true,3))
    reducer.apply(AvatarEvent.AudioLevel("first",1f,4))
    reducer.apply(AvatarEvent.VisemeCue("first",Viseme.AA,1f,1000,4))
    ensure(reducer.pose(4).jawOpen==0f && reducer.pose(4).viseme==Viseme.SIL,"Cancellation rejects stale callbacks")
    reducer.apply(AvatarEvent.SpeechStarted("second","synthetic",5))
    reducer.apply(AvatarEvent.AudioLevel("second",1f,6))
    reducer.apply(AvatarEvent.SpeechEnded("first",true,7))
    ensure(reducer.pose(7).jawOpen>0f,"Old end cannot clear new utterance")
    ensure(reducer.pose(257).jawOpen==0f,"Audio freshness expiry")

    // Two 100 ms chunks are synthesized in advance; only a future playback anchor makes them visible.
    val signal=TtsHologramSignal()
    fun pcm16(sample:Int,frames:Int):ByteArray=ByteArray(frames*2) { i -> if(i%2==0) sample.toByte() else (sample shr 8).toByte() }
    signal.activate("u");signal.begin("u",16000,2,1)
    signal.append("u",pcm16(8192,1600));signal.append("u",pcm16(24576,1600))
    ensure(signal.level(4999)==0f,"Synthesis is not playback")
    signal.anchor("u",0,5000)
    ensure(abs(signal.level(5000)-.25f)<1e-6f && abs(signal.level(5100)-.75f)<1e-6f,"Delayed playback anchor")
    fact("syntheticPlaybackAnchorMillis",5000)
    fact("levelAtAnchor",signal.level(5000));fact("level100msAfterAnchor",signal.level(5100))
    signal.anchor("old",0,5100)
    ensure(abs(signal.level(5100)-.75f)<1e-6f,"Stale anchor rejected")
    signal.stop();signal.append("u",pcm16(32767,1600));signal.anchor("u",0,5200)
    ensure(signal.level(5200)==0f,"Stop clears PCM state and old callbacks")
    facts += "\"cancellationAndMouthNeutral\":true"
    facts += "\"physicalPlaybackMeasured\":false"
    fact("assertions",assertions)
    println("{"+facts.joinToString(",")+"}")
}
