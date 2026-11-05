package at.heinz.deviceai

import kotlin.math.abs
import kotlin.math.sqrt

internal enum class OwnerFacePhase {
    OFF, READY, LOADING, ENROLLING, RECOGNIZING, MATCH, NO_MATCH, NO_FACE,
    MULTIPLE_FACES, LOW_QUALITY, ENROLLED, NOT_ENROLLED, DELETED, UNAVAILABLE, ERROR
}

internal data class OwnerFaceStatus(
    val phase: OwnerFacePhase,
    val hasProfile: Boolean = false,
    val displayName: String? = null,
    val acceptedSamples: Int = 0,
    val requiredSamples: Int = OwnerFacePolicy.ENROLLMENT_SAMPLES,
    val sessionActive: Boolean = false,
    val message: String
)

internal object OwnerFaceModelContract {
    const val SOURCE_COMMIT = "48b10fa505aeb51fbd127fc60a5c660040e823b7"
    const val DETECTOR_ASSET = "faceid/face_detection_yunet_2023mar.onnx"
    const val DETECTOR_SHA256 = "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4"
    const val DETECTOR_BYTES = 232_589L
    const val RECOGNIZER_ASSET = "faceid/face_recognition_sface_2021dec.onnx"
    const val RECOGNIZER_SHA256 = "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79"
    const val RECOGNIZER_BYTES = 38_696_353L
    const val FEATURE_SIZE = 128
}

internal class OwnerFaceProfile(
    val displayName: String,
    val createdAtMillis: Long,
    val modelSha256: String,
    val features: List<FloatArray>
) {
    fun valid(): Boolean = validName(displayName) && createdAtMillis > 0 &&
        modelSha256 == OwnerFaceModelContract.RECOGNIZER_SHA256 &&
        features.size == OwnerFacePolicy.ENROLLMENT_SAMPLES && features.all { feature ->
            feature.size == OwnerFaceModelContract.FEATURE_SIZE && feature.all(Float::isFinite) &&
                abs(feature.sumOf { it.toDouble() * it } - 1.0) <= .002
        }

    fun clear() = features.forEach { it.fill(0f) }

    companion object {
        fun validName(name: String): Boolean = name.isNotBlank() && name == name.trim() &&
            name.length <= 64 && name.none { Character.isISOControl(it) || Character.isSurrogate(it) }
    }
}

internal data class OwnerFaceQuality(
    val frameWidth: Int,
    val frameHeight: Int,
    val faceX: Float,
    val faceY: Float,
    val faceWidth: Float,
    val faceHeight: Float,
    val detectionScore: Float,
    val eyeDistance: Float,
    val eyeSlope: Float,
    val noseOffset: Float,
    val brightness: Double,
    val sharpnessVariance: Double
) {
    fun acceptable(): Boolean {
        val values = listOf(faceX, faceY, faceWidth, faceHeight, detectionScore, eyeDistance, eyeSlope, noseOffset)
        if (values.any { !it.isFinite() } || !brightness.isFinite() || !sharpnessVariance.isFinite()) return false
        return frameWidth in 1..640 && frameHeight in 1..640 && faceX >= 0f && faceY >= 0f &&
            faceWidth >= 64f && faceHeight >= 64f && faceX + faceWidth <= frameWidth &&
            faceY + faceHeight <= frameHeight && faceWidth / faceHeight in .55f..1.35f &&
            detectionScore in .9f..1f && eyeDistance >= 20f && abs(eyeSlope) <= .35f &&
            abs(noseOffset) <= .45f && brightness in 45.0..225.0 && sharpnessVariance >= 60.0
    }
}

internal class OwnerFaceObservation(
    val faceCount: Int,
    val observedAtElapsedMillis: Long,
    val quality: OwnerFaceQuality? = null,
    val feature: FloatArray? = null,
    val modelSha256: String = OwnerFaceModelContract.RECOGNIZER_SHA256
) : AutoCloseable {
    override fun close() { feature?.fill(0f) }
}

internal data class OwnerFaceDecision(
    val phase: OwnerFacePhase,
    val acceptedSamples: Int,
    val profile: OwnerFaceProfile? = null
)

/** Embeddings only; grants no authority. */
internal class OwnerFacePolicy(
    private val enrollingName: String? = null,
    profile: OwnerFaceProfile? = null
) : AutoCloseable {
    private val reference = profile?.takeIf { it.valid() }?.features?.map { it.copyOf() }.orEmpty()
    private val enrollment = mutableListOf<FloatArray>()
    private var lastObservedAt = -1L
    private var lastAcceptedAt = -1L
    private var matches = 0
    private var finished = false
    val enrolling: Boolean get() = enrollingName != null

    init {
        require(enrollingName == null || OwnerFaceProfile.validName(enrollingName))
        require(enrollingName == null || profile == null)
    }

    fun accept(observation: OwnerFaceObservation, nowElapsedMillis: Long, nowMillis: Long): OwnerFaceDecision {
        if (finished) return decision(OwnerFacePhase.OFF)
        if (!enrolling && reference.isEmpty()) return decision(OwnerFacePhase.NOT_ENROLLED)
        val captured = observation.observedAtElapsedMillis
        if (captured < 0 || captured > nowElapsedMillis || nowElapsedMillis - captured > MAX_FRAME_AGE_MS ||
            captured <= lastObservedAt || observation.modelSha256 != OwnerFaceModelContract.RECOGNIZER_SHA256) {
            matches = 0
            return decision(OwnerFacePhase.LOW_QUALITY)
        }
        lastObservedAt = captured
        if (observation.faceCount != 1) {
            matches = 0
            if (observation.faceCount > 1) clearEnrollment()
            return decision(if (observation.faceCount == 0) OwnerFacePhase.NO_FACE
                else if (observation.faceCount > 1) OwnerFacePhase.MULTIPLE_FACES else OwnerFacePhase.LOW_QUALITY)
        }
        if (observation.quality?.acceptable() != true) {
            matches = 0
            return decision(OwnerFacePhase.LOW_QUALITY)
        }
        val feature = normalize(observation.feature) ?: run {
            matches = 0
            return decision(OwnerFacePhase.LOW_QUALITY)
        }
        try {
            if (lastAcceptedAt >= 0 && captured - lastAcceptedAt < MIN_SAMPLE_GAP_MS) {
                matches = 0
                return decision(OwnerFacePhase.LOW_QUALITY)
            }
            lastAcceptedAt = captured
            if (enrolling) {
                if (enrollment.any { similarity(it, feature) < ENROLLMENT_SIMILARITY }) {
                    clearEnrollment()
                    return decision(OwnerFacePhase.NO_MATCH)
                }
                enrollment += feature.copyOf()
                if (enrollment.size == ENROLLMENT_SAMPLES) {
                    if (nowMillis <= 0) { clearEnrollment(); return decision(OwnerFacePhase.ERROR) }
                    finished = true
                    val result = OwnerFaceProfile(requireNotNull(enrollingName), nowMillis,
                        OwnerFaceModelContract.RECOGNIZER_SHA256, enrollment.map { it.copyOf() })
                    return OwnerFaceDecision(OwnerFacePhase.ENROLLED, ENROLLMENT_SAMPLES, result)
                }
                return decision(OwnerFacePhase.ENROLLING)
            }
            val scores = reference.map { similarity(it, feature) }
            val matching = scores.average() >= MATCH_SIMILARITY && scores.minOrNull()!! >= MIN_REFERENCE_SIMILARITY
            matches = if (matching) (matches + 1).coerceAtMost(REQUIRED_MATCHES) else 0
            return decision(if (matches >= REQUIRED_MATCHES) OwnerFacePhase.MATCH
                else if (matching) OwnerFacePhase.RECOGNIZING else OwnerFacePhase.NO_MATCH)
        } finally { feature.fill(0f) }
    }

    private fun decision(phase: OwnerFacePhase) = OwnerFaceDecision(phase, if (enrolling) enrollment.size else matches)
    private fun clearEnrollment() { enrollment.forEach { it.fill(0f) }; enrollment.clear() }
    override fun close() {
        clearEnrollment()
        reference.forEach { it.fill(0f) }
        matches = 0
        finished = true
    }

    companion object {
        const val ENROLLMENT_SAMPLES = 8
        const val REQUIRED_MATCHES = 3
        const val MAX_FRAME_AGE_MS = 1_500L
        const val MIN_SAMPLE_GAP_MS = 400L
        const val MATCH_SIMILARITY = .60
        const val ENROLLMENT_SIMILARITY = .60
        const val MIN_REFERENCE_SIMILARITY = .45

        fun normalize(input: FloatArray?): FloatArray? {
            if (input == null || input.size != OwnerFaceModelContract.FEATURE_SIZE ||
                input.any { !it.isFinite() || abs(it) > 1_000_000f }) return null
            val norm = sqrt(input.sumOf { it.toDouble() * it })
            if (!norm.isFinite() || norm < .000001) return null
            return FloatArray(input.size) { (input[it] / norm).toFloat() }
        }

        private fun similarity(a: FloatArray, b: FloatArray): Double =
            a.indices.sumOf { a[it].toDouble() * b[it] }.coerceIn(-1.0, 1.0)
    }
}
