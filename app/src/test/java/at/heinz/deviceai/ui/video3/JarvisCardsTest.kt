package at.heinz.deviceai.ui.video3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisCardsTest {
    @Test fun userCardShowsOnlyRealRecognition() {
        val card = JarvisUserCard()
        assertEquals("—", card.title()); assertEquals("—", card.subtitle())
        card.render(OwnerRecognition(OwnerRecognitionState.RECOGNIZED, "Geräte-Entsperrung", 1L))
        assertEquals("User Recognized", card.title()); assertEquals("Geräte-Entsperrung", card.subtitle())
        card.render(OwnerRecognition(OwnerRecognitionState.NOT_RECOGNIZED, null, null))
        assertEquals("Not Recognized", card.title()); assertEquals("—", card.subtitle())
        JarvisUserCardSlot.register(card); assertTrue(JarvisUserCardSlot.renderer === card)
        JarvisUserCardSlot.register(null)
    }

    @Test fun greetingFollowsRealClockAndClaimsNothing() {
        val c = JarvisGreetingCard()
        assertEquals("Good morning.", c.greetingLine(JarvisGreeting(8)))
        assertEquals("Good afternoon.", c.greetingLine(JarvisGreeting(14)))
        assertEquals("Good evening.", c.greetingLine(JarvisGreeting(21)))
        assertEquals("Good evening.", c.greetingLine(JarvisGreeting(2)))
        assertEquals("—", c.greetingLine(JarvisGreeting(null)))
        val body = c.bodyText(JarvisGreeting(14))
        assertEquals(JarvisGreetingCard.OFFER, body)
        assertFalse(body.contains("synced"))
        assertTrue(c.bodyText(JarvisGreeting(14, "Lokales Modell bereit.")).startsWith("Lokales Modell bereit."))
    }
}
