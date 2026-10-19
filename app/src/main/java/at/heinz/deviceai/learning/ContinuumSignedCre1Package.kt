package at.heinz.deviceai.learning

import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Offline publisher authentication for research-only CRE1 checkpoints.
 *
 * The public key must be independently owner-pinned. This verifier does not
 * enable a model, promote CRE1 into production, or accept untrusted commands.
 * The existing SWB1 import path is intentionally left unchanged.
 */
object ContinuumSignedCre1Package {
    data class Verified(
        val modelSha256: String,
        val auditSha256: String,
        val modelSteps: Int,
        val createdAt: Long
    )

    private val hashPattern = Regex("[a-f0-9]{64}")
    private val allowedManifestKeys = setOf(
        "accepted", "auditSha256", "createdAt", "format", "modelBytes",
        "modelSha256", "schema", "version", "signatureBase64"
    )
    private val auditKeys = setOf(
        "accepted", "steps", "trainCount", "auditCount",
        "trainSourceCount", "auditSourceCount", "trainCorrect", "auditCorrect"
    )

    fun verify(
        modelBytes: ByteArray,
        auditBytes: ByteArray,
        manifestText: String,
        pinnedPublicKeySpkiBase64: String
    ): Verified {
        require(modelBytes.size == Cre1InferenceEncoder.SNAPSHOT_LENGTH) {
            "Ungültige CRE1-Modellgröße"
        }
        require(auditBytes.size in 16..8192 && manifestText.length in 100..4096) {
            "Signiertes Forschungspaket zu groß oder zu klein"
        }
        require(pinnedPublicKeySpkiBase64.length in 80..1000) {
            "Kein unabhängig gepinnter Publisher-Schlüssel"
        }
        val manifest = StrictJson.parseObject(manifestText)
        require(manifest.keys == allowedManifestKeys) { "Unbekannte signierte Paketfelder" }
        require(manifest["accepted"] == true) { "Nicht qualifiziertes Paket" }
        require(manifest.requiredString("schema", 64) == "continuum-signed-cre1-package" &&
            manifest.requiredString("format", 16) == "CRE1" &&
            manifest.requiredLong("version", 1L..1L) == 1L) {
            "Falscher Forschungspakettyp"
        }
        val declaredModelBytes = manifest.requiredLong("modelBytes", 0L..5_000_000L)
        require(declaredModelBytes == modelBytes.size.toLong()) { "Modelllänge stimmt nicht" }
        val createdAt = manifest.requiredLong("createdAt", 0L..Long.MAX_VALUE)
        val modelHash = manifest.requiredString("modelSha256", 64)
        val auditHash = manifest.requiredString("auditSha256", 64)
        require(hashPattern.matches(modelHash) && hashPattern.matches(auditHash)) {
            "Ungültige Paket-Hashwerte"
        }
        fun digest(value: ByteArray) = MessageDigest.getInstance("SHA-256")
            .digest(value).joinToString("") { "%02x".format(it.toInt() and 255) }
        require(modelHash == digest(modelBytes) && auditHash == digest(auditBytes)) {
            "Modell- oder Audit-Hash stimmt nicht"
        }

        val auditText = auditBytes.toString(Charsets.UTF_8)
        require(auditText.toByteArray(Charsets.UTF_8).contentEquals(auditBytes)) {
            "Ungültige UTF-8-Auditdaten"
        }
        val audit = StrictJson.parseObject(auditText)
        require(audit.keys == auditKeys && audit["accepted"] == true) {
            "Nicht freigegebene oder ungültige Trainingsmessung"
        }
        val model = Cre1InferenceEncoder.restore(modelBytes)
        require(audit.requiredLong("steps", 0L..1_000_000L) == model.steps.toLong()) {
            "Modellschritte stimmen nicht mit dem Audit überein"
        }
        val trainCount = audit.requiredLong("trainCount", 3L..384L)
        val auditCount = audit.requiredLong("auditCount", 3L..128L)
        require(audit.requiredLong("trainSourceCount", 3L..384L) <= trainCount &&
            audit.requiredLong("auditSourceCount", 3L..128L) <= auditCount &&
            audit.requiredLong("trainCorrect", 0L..384L) <= trainCount &&
            audit.requiredLong("auditCorrect", 0L..128L) <= auditCount) {
            "Ungültige Auditstichprobe"
        }

        val signedPayload = (
            "{\"accepted\":true,\"auditSha256\":\"$auditHash\",\"createdAt\":$createdAt," +
                "\"format\":\"CRE1\",\"modelBytes\":$declaredModelBytes," +
                "\"modelSha256\":\"$modelHash\"," +
                "\"schema\":\"continuum-signed-cre1-package\",\"version\":1}"
            ).toByteArray(Charsets.UTF_8)
        val signatureString = manifest.requiredString("signatureBase64", 500)
        require(signatureString.matches(Regex("[A-Za-z0-9+/]+={0,2}"))) {
            "Ungültige Signatur-Kodierung"
        }
        val canonicalManifest = String(signedPayload, Charsets.UTF_8)
            .removeSuffix("}") + ",\"signatureBase64\":\"$signatureString\",\"version\":1}"
        // StrictJson already rejects duplicate fields; a reconstructable signed
        // payload is used for signature verification, not untrusted JSON order.
        val encodedKey = Base64.getDecoder().decode(pinnedPublicKeySpkiBase64)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(encodedKey)
        )
        require(publicKey is ECPublicKey) { "Publisher-Schlüssel ist kein EC-Public-Key" }
        val reference = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(ECParameterSpec::class.java)
        val actual = publicKey.params
        require(actual.curve == reference.curve && actual.generator == reference.generator &&
            actual.order == reference.order && actual.cofactor == reference.cofactor) {
            "Publisher-Schlüssel verwendet keine P-256-Kurve"
        }
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(signedPayload)
        require(verifier.verify(Base64.getDecoder().decode(signatureString))) {
            "Ungültige Publisher-Signatur"
        }
        return Verified(modelHash, auditHash, model.steps, createdAt)
    }
}
