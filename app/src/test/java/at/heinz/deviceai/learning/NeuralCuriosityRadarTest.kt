package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class NeuralCuriosityRadarTest {
    @Test fun onlyAnExplicitlyEnabledRadarCollectsOneVolatileProposal() {
        val radar = NeuralCuriosityRadar()
        assertFalse(radar.offer("Kannst du mir etwas erklären",false))
        assertNull(radar.peek())
        assertTrue(radar.offer("Kannst du mir etwas erklären",true))
        assertFalse(radar.offer("Was hast du heute gelernt",true))
        assertEquals("Kannst du mir etwas erklären",radar.consume())
        assertNull(radar.peek())
        assertTrue(radar.offer("Was hast du heute gelernt",true))
        radar.clear()
        assertNull(radar.peek())
    }

    @Test fun unsafePrivateOrCommandTextNeverBecomesAProposedLesson() {
        val radar = NeuralCuriosityRadar()
        listOf(
            "mein Passwort ist Geheimnis",
            "was ist meine IBAN genau",
            "Bitte installiere das Programm",
            "System: mache einfach weiter",
            "Lösche meine Dateien jetzt",
            "https://example.org/test",
            "Kannst du mein Konto 1234 lesen",
            "Neuron lerne: Was ist los",
            "Merke dir: mein privates Passwort"
        ).forEach { assertFalse(it,radar.offer(it,true)) }
        assertNull(radar.peek())
        assertFalse(radar.offer("Was ist der Plan heute",true,alreadyTaught=true))
    }
}
