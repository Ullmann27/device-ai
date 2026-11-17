package at.heinz.deviceai.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumGitHubReleaseCatalogTest {
    private val sha="a".repeat(64)
    private val commit="b".repeat(40)

    private fun json(
        draft:Boolean=false,
        prerelease:Boolean=false,
        digest:String="sha256:$sha",
        extraApk:Boolean=false
    ):String {
        val second=if(extraApk) """,{
          "name":"Continuum-Lokal-cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc.apk",
          "size":271100761,
          "digest":"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
          "browser_download_url":"https://github.com/Ullmann27/device-ai/releases/download/continuum-local-code50-$commit/Continuum-Lokal-cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc.apk"
        }""" else ""
        return """{
          "tag_name":"continuum-local-code50-$commit",
          "draft":$draft,
          "prerelease":$prerelease,
          "body":"Originalsigniert.",
          "assets":[{
            "name":"Continuum-Lokal-$sha.apk",
            "size":271100761,
            "digest":"$digest",
            "browser_download_url":"https://github.com/Ullmann27/device-ai/releases/download/continuum-local-code50-$commit/Continuum-Lokal-$sha.apk"
          }$second]
        }"""
    }

    @Test fun validPublishedReleaseMapsToOriginalOwnerDescriptor() {
        val r=ContinuumGitHubReleaseCatalog.parseLatest(json())
        assertEquals(ContinuumOwnerUpdateSource.GITHUB_RELEASE,r.source)
        assertEquals(50,r.descriptor.versionCode)
        assertEquals("0.3.49",r.versionName)
        assertEquals(sha,r.descriptor.apkSha256)
        assertEquals(commit,r.descriptor.sourceCommit)
        assertEquals(ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256,r.descriptor.signerSha256)
        assertTrue(r.descriptor.explicitlyApproved)
    }

    @Test fun draftPrereleaseDigestMismatchAndMultipleApksFailClosed() {
        listOf(
            json(draft=true),
            json(prerelease=true),
            json(digest="sha256:"+"d".repeat(64)),
            json(extraApk=true)
        ).forEach { fixture ->
            var failed=false
            try { ContinuumGitHubReleaseCatalog.parseLatest(fixture) }
            catch (_:IllegalArgumentException){failed=true}
            assertTrue(failed)
        }
    }
}
