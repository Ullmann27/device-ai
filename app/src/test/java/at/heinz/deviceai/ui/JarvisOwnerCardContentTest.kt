package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.OwnerRecognition
import at.heinz.deviceai.cockpit.OwnerRecognitionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisOwnerCardContentTest {
    private val box = JarvisShellLayout.Box(0f, 0f, 150f, 160f)

    @Test fun missingSensorsNeverImplyPresenceAttentionGazeOrEmotion() {
        val painter = JarvisPresentationRecordingPainter()
        JarvisOwnerCardContent.draw(painter, box, OwnerRecognition.UNKNOWN)
        assertTrue(painter.texts.count { it == "—" } == 6)
        assertFalse(painter.texts.any { it.contains("erkannt") || it.contains("Aktiv") || it.contains("Ruhig") })
    }

    @Test fun unlockedDeviceDoesNotClaimPersonalRecognition() {
        val painter = JarvisPresentationRecordingPainter()
        JarvisOwnerCardContent.draw(painter, box,
            OwnerRecognition(OwnerRecognitionState.RECOGNIZED, OwnerRecognition.METHOD_UNLOCK, 1L))
        assertTrue(painter.texts.contains("Gerät entsperrt"))
        assertFalse(painter.texts.any { it.contains("erkannt") })
        assertTrue(painter.texts.any { it.startsWith("Geräte") })
        assertTrue(painter.texts.count { it == "—" } == 4)
    }

    @Test fun actualRecognitionKeepsItsOwnSource() {
        val painter = JarvisPresentationRecordingPainter()
        JarvisOwnerCardContent.draw(painter, box,
            OwnerRecognition(OwnerRecognitionState.RECOGNIZED, "Identität bestätigt", 1L))
        assertTrue(painter.texts.contains("Nutzer erkannt"))
        assertTrue(painter.texts.count { it == "—" } == 4)
    }

    @Test fun unknownRecognitionDoesNotExposeAnUnverifiedMethod() {
        val painter = JarvisPresentationRecordingPainter()
        JarvisOwnerCardContent.draw(painter, box,
            OwnerRecognition(OwnerRecognitionState.UNKNOWN, "unverified", 1L))
        assertFalse(painter.texts.any { it.contains("unverified") })
    }
}
