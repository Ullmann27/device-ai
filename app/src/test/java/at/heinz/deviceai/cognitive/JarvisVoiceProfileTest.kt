package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class JarvisVoiceProfileTest {
    @Test fun localProfileIsMildAndWithinAndroidTtsRanges() {
        assertTrue(JarvisVoiceProfile.pitch in 0.5f..1.0f)
        assertTrue(JarvisVoiceProfile.speechRate in 0.5f..1.0f)
    }

    @Test fun markdownAndUrlsAreSpokenAsProseNotRawControlTokens() {
        val input = "# Antwort: **Bereit**. [Quelle](https://example.org/a) https://example.org/b"
        val spoken = JarvisVoiceProfile.speakable(input)!!
        assertTrue(spoken.contains("Bereit"))
        assertTrue(spoken.contains("Quelle"))
        assertFalse(spoken.contains("https://"))
        assertFalse(spoken.contains("**"))
    }

    @Test fun sourceCodeIsOmittedFromSpokenAnswer() {
        val input = "Das Ergebnis ist da.\n` ` `\n".replace(" ", "") +
            "private val key = 42\n` ` `\n".replace(" ", "") +
            "Weitere Schritte möglich."
        val spoken = JarvisVoiceProfile.speakable(input)!!
        assertFalse(spoken.contains("private val"))
        assertTrue(spoken.contains("Weitere Schritte"))
    }

    @Test fun incompleteAndInlineCodeAreNotSpokenAsSecrets() {
        val incomplete = JarvisVoiceProfile.speakable("Hinweis:\n```kotlin\nval secret = PRIVATE")!!
        assertFalse(incomplete.contains("PRIVATE"))
        val inline = JarvisVoiceProfile.speakable("Hallo `secret_token` und weiter.")!!
        assertFalse(inline.contains("secret_token"))
        assertTrue(inline.contains("weiter"))
    }

    @Test fun multilineLongAndTildeCodeAreNeverSpoken() {
        val hidden = "SECRET_ACCESS_TOKEN"
        val multiline = JarvisVoiceProfile.speakable("Konto `ABC\n" + hidden + "` geändert.")!!
        assertFalse(multiline.contains(hidden))
        val tilde = JarvisVoiceProfile.speakable("~~~kotlin\nkey = " + hidden + "\n~~~\nFertig.")!!
        assertFalse(tilde.contains(hidden))
        assertTrue(tilde.contains("Fertig"))
        val oversized = JarvisVoiceProfile.speakable("Beginn `" + hidden.repeat(40) + "` Ende.", 800)!!
        assertFalse(oversized.contains(hidden))
    }

    @Test fun answerLengthIsBoundedAndNeverEmptyAfterTruncation() {
        val value = "Das ist ein Satz. ".repeat(70)
        val spoken = JarvisVoiceProfile.speakable(value, 120)!!
        assertTrue(spoken.length in 1..120)
        assertTrue(spoken.endsWith("."))
        assertNull(JarvisVoiceProfile.speakable(" \n\t"))
    }

    @Test fun invalidSpeechBudgetFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) { JarvisVoiceProfile.speakable("Hallo", 20) }
    }
}
