package at.heinz.deviceai.intelligence

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumOwnerUpdateCatalogTest {
    private fun json(
        apkUrl: String = "https://${ContinuumOwnerUpdateCatalog.HOST}/continuum-local/owner-${"b".repeat(64)}.apk",
        approved: Boolean = true,
        version: Int = 30
    ): String {
        val obj = JSONObject()
        obj.put("schema", 1)
        obj.put("channel", "OWNER_RELEASE")
        obj.put("packageName", ContinuumSignedUpdatePolicy.OWNER_PACKAGE)
        obj.put("versionCode", version)
        obj.put("versionName", "0.3.29")
        obj.put("apkUrl", apkUrl)
        obj.put("apkSha256", "b".repeat(64))
        obj.put("byteLength", 100_000_000)
        obj.put("sourceCommit", "a".repeat(40))
        obj.put("signerSha256", ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256)
        obj.put("approved", approved)
        obj.put("notes", "Verifizierte Besitzeraktualisierung.")
        return obj.toString()
    }

    @Test fun explicitNoReleaseIsNotAnInstallableUpdate() {
        assertEquals(null, ContinuumOwnerUpdateCatalog.parseAvailable(
            """{"schema":1,"available":false}"""
        ))
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumOwnerUpdateCatalog.parseAvailable(
                """{"schema":1,"available":false,"apkUrl":"http://bad.example/test.apk"}"""
            )
        }
    }

    @Test fun acceptsExactTypedManifestWithOwnerApproval() {
        val release = ContinuumOwnerUpdateCatalog.parse(json())
        assertEquals(30, release.descriptor.versionCode)
        assertEquals("OWNER_RELEASE", release.descriptor.channel)
        assertEquals("0.3.29", release.versionName)
        assertTrue(ContinuumSignedUpdatePolicy.check(
            ContinuumSignedUpdatePolicy.OWNER_PACKAGE,
            28,
            ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256,
            ContinuumOwnerUpdateCatalog.HOST,
            release.descriptor
        ) is ContinuumUpdatePreflight.ReadyForActualApkVerification)
    }

    @Test fun unapprovedAndWrongHostMustNeverBeInstallable() {
        val unapproved = ContinuumOwnerUpdateCatalog.parse(json(approved = false))
        assertTrue(ContinuumSignedUpdatePolicy.check(
            ContinuumSignedUpdatePolicy.OWNER_PACKAGE, 28,
            ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256,
            ContinuumOwnerUpdateCatalog.HOST,
            unapproved.descriptor
        ) is ContinuumUpdatePreflight.Rejected)
        val alien = ContinuumOwnerUpdateCatalog.parse(json(apkUrl =
            "https://example.net/continuum-local/owner-${"b".repeat(64)}.apk"))
        assertTrue(ContinuumSignedUpdatePolicy.check(
            ContinuumSignedUpdatePolicy.OWNER_PACKAGE, 28,
            ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256,
            ContinuumOwnerUpdateCatalog.HOST,
            alien.descriptor
        ) is ContinuumUpdatePreflight.Rejected)
    }

    @Test fun rejectsOversizeWrongSchemaMissingAndExtraFields() {
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumOwnerUpdateCatalog.parse("x".repeat(ContinuumOwnerUpdateCatalog.MAX_JSON_BYTES + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumOwnerUpdateCatalog.parse(JSONObject(json()).put("unknown", "ignored").toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumOwnerUpdateCatalog.parse(JSONObject(json()).put("schema", 2).toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumOwnerUpdateCatalog.parse(JSONObject(json()).remove("apkSha256").toString())
        }
    }

    @Test fun malformedTypesAndVersionNameAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumOwnerUpdateCatalog.parse(JSONObject(json()).put("versionCode", "30").toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumOwnerUpdateCatalog.parse(JSONObject(json()).put("versionName", "../unsafe").toString())
        }
    }
}
