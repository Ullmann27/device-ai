package at.heinz.deviceai.learning

import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContinuumSignedCre1PackageTest {
    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    private data class Case(
        val model: ByteArray,
        val audit: ByteArray,
        val manifest: String,
        val pinnedKey: String
    )

    private fun validCase(): Case {
        val model = Cre1InferenceEncoder.createResearch(73L).save()
        val auditText =
            "{\"accepted\":true,\"auditCorrect\":3,\"auditCount\":4," +
                "\"auditSourceCount\":4,\"steps\":0,\"trainCorrect\":7," +
                "\"trainCount\":10,\"trainSourceCount\":10}"
        val audit = auditText.toByteArray(Charsets.UTF_8)
        val prefix =
            "{\"accepted\":true,\"auditSha256\":\"${sha(audit)}\"," +
                "\"createdAt\":1750000000000,\"format\":\"CRE1\"," +
                "\"modelBytes\":${model.size},\"modelSha256\":\"${sha(model)}\"," +
                "\"schema\":\"continuum-signed-cre1-package\""
        val payload = (prefix + ",\"version\":1}").toByteArray(Charsets.UTF_8)
        val pair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(pair.private)
        signer.update(payload)
        val signature = Base64.getEncoder().encodeToString(signer.sign())
        return Case(
            model,
            audit,
            prefix + ",\"signatureBase64\":\"$signature\",\"version\":1}",
            Base64.getEncoder().encodeToString(pair.public.encoded)
        )
    }

    @Test fun signedResearchOnlyCre1PackageVerifiesWithoutPromotion() {
        val input = validCase()
        val verified = ContinuumSignedCre1Package.verify(
            input.model, input.audit, input.manifest, input.pinnedKey
        )
        assertEquals(sha(input.model), verified.modelSha256)
        assertEquals(sha(input.audit), verified.auditSha256)
        assertEquals(0, verified.modelSteps)
        assertEquals(1750000000000L, verified.createdAt)
    }

    @Test fun modelAndAuditTamperingAreRejected() {
        val input = validCase()
        val modifiedModel = input.model.clone()
        modifiedModel[100] = (modifiedModel[100].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumSignedCre1Package.verify(
                modifiedModel, input.audit, input.manifest, input.pinnedKey
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumSignedCre1Package.verify(
                input.model, "{}".toByteArray(), input.manifest, input.pinnedKey
            )
        }
    }

    @Test fun wrongKeyAndModifiedSignedManifestAreRejected() {
        val input = validCase()
        val otherPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val wrongKey = Base64.getEncoder().encodeToString(otherPair.public.encoded)
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumSignedCre1Package.verify(
                input.model, input.audit, input.manifest, wrongKey
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumSignedCre1Package.verify(
                input.model, input.audit,
                input.manifest.replace("\"createdAt\":1750000000000",
                    "\"createdAt\":1750000000001"), input.pinnedKey
            )
        }
    }

    @Test fun unsignedOrWrongFormatCannotEnterResearchVerification() {
        val input = validCase()
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumSignedCre1Package.verify(
                input.model, input.audit,
                input.manifest.replace("\"format\":\"CRE1\"", "\"format\":\"SWB1\""),
                input.pinnedKey
            )
        }
        val unsigned = input.manifest.substringBefore(",\"signatureBase64\"") + "}"
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumSignedCre1Package.verify(
                input.model, input.audit, unsigned, input.pinnedKey
            )
        }
    }
}
