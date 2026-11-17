package at.heinz.deviceai.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class ContinuumGitHubDownloadRedirectPolicyTest {
    private val source = URI(
        "https://github.com/Ullmann27/device-ai/releases/download/" +
            "continuum-local-code50-" + "b".repeat(40) + "/" +
            "Continuum-Lokal-" + "a".repeat(64) + ".apk"
    )

    @Test fun exactGithubReleaseMayRedirectOnlyToKnownHttpsAssetHosts() {
        val target = ContinuumGitHubDownloadRedirectPolicy.resolve(
            source,
            "https://release-assets.githubusercontent.com/github-production-release-asset/123/file.apk?sp=r&sig=abc"
        )
        assertTrue(target != null)
        assertEquals("release-assets.githubusercontent.com", target!!.host)
        assertEquals("sp=r&sig=abc", target.rawQuery)
    }

    @Test fun assetHostMayUseAnotherKnownGithubAssetHost() {
        val current = URI("https://release-assets.githubusercontent.com/a/file.apk?sig=1")
        val target = ContinuumGitHubDownloadRedirectPolicy.resolve(
            current, "https://objects.githubusercontent.com/a/file.apk?sig=2")
        assertEquals("objects.githubusercontent.com", target!!.host)
    }

    @Test fun arbitraryHostHttpUserInfoFragmentAndEmptyPathFailClosed() {
        listOf(
            "https://evil.example/file.apk",
            "http://release-assets.githubusercontent.com/file.apk",
            "https://user@release-assets.githubusercontent.com/file.apk",
            "https://release-assets.githubusercontent.com/file.apk#fragment",
            "https://release-assets.githubusercontent.com"
        ).forEach {
            assertNull(ContinuumGitHubDownloadRedirectPolicy.resolve(source, it))
        }
    }

    @Test fun redirectPolicyIsStrictlyBoundedByCallerContract() {
        assertEquals(3, ContinuumGitHubDownloadRedirectPolicy.MAX_REDIRECTS)
    }
}
