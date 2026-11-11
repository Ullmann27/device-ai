package at.heinz.deviceai

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MayaPcmPlayerUiTest {
    @Test fun staticTrackAcceptsFirstWriteBeforeItBecomesInitialized() {
        var allowed = true
        var started = false
        MayaPcmPlayer(RuntimeEnvironment.getApplication()).use { player ->
            player.play(ByteArray(4800), { allowed }) {
                started = true
                allowed = false
            }
        }
        assertTrue("MODE_STATIC must load data before checking INITIALIZED", started)
    }
}
