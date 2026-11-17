package at.heinz.deviceai

import android.net.Uri
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.InterruptedIOException

/** Cancellation precedes unavailable URI access. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PdfIntakeCancellationUiTest {
    @Test fun cancelledReadNeverOpensTheUnavailableProvider() {
        val intake = PdfDocumentIntake(RuntimeEnvironment.getApplication().contentResolver)
        val uri = Uri.parse("content://at.heinz.test.unavailable/document/cancelled")
        assertThrows(InterruptedIOException::class.java) {
            intake.read(uri, 360) { true }
        }
    }
}
