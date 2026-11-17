package at.heinz.deviceai.notifications

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAnnouncementSettingsTest {
    private val listener = "at.heinz.deviceai.notifications.ContinuumNotificationListener"

    @Test fun settingsSurviveTheJsonRoundTrip() {
        val settings = AnnouncementSettings(
            enabled = true,
            apps = mapOf(
                "com.whatsapp" to AppRule(AnnouncementMode.SENDER),
                "org.thoughtcrime.securesms" to AppRule(AnnouncementMode.FULL),
                "com.example.ownerpicked" to AppRule(AnnouncementMode.APP_ONLY),
            ),
            quietWhenNotInConversation = false,
            maxPerMinute = 6,
        )
        val raw = AnnouncementSettingsCodec.encode(settings)
        assertEquals(settings, AnnouncementSettingsCodec.decode(raw))
        // Only the owner's choices are stored: switches, package names and modes.
        val json = JSONObject(raw)
        assertEquals(setOf("v", "enabled", "quietWhenNotInConversation", "maxPerMinute", "apps"), json.keys().asSequence().toSet())
        assertEquals("SENDER", json.getJSONObject("apps").getString("com.whatsapp"))
    }

    @Test fun unreadableOrForeignDataFallsBackToThePrivateDefault() {
        val defaults = AnnouncementSettings()
        assertFalse(defaults.enabled)
        for (raw in listOf(null, "", "   ", "not json", "[]", "{}", """{"v":2,"enabled":true}""", """{"v":"x","enabled":true}""")) {
            assertEquals(raw, defaults, AnnouncementSettingsCodec.decode(raw))
        }
        // Wrong types never switch anything on.
        val odd = AnnouncementSettingsCodec.decode("""{"v":1,"enabled":"ja","apps":["com.whatsapp"],"maxPerMinute":"viele"}""")
        assertFalse(odd.enabled)
        assertTrue(odd.apps.isEmpty())
        assertEquals(NotificationAnnouncementPolicy.DEFAULT_MAX_PER_MINUTE, odd.maxPerMinute)
    }

    @Test fun storedRulesAreValidatedAndBounded() {
        val decoded = AnnouncementSettingsCodec.decode(
            """{"v":1,"enabled":true,"maxPerMinute":900,"apps":{
                "com.whatsapp":"FULL","org.telegram.messenger":"OFF","com.example.unknownmode":"LOUD",
                "no-package":"FULL","":"FULL","com.example.lower":"full","com.example.number":3}}""",
        )
        assertTrue(decoded.enabled)
        assertEquals(mapOf("com.whatsapp" to AppRule(AnnouncementMode.FULL)), decoded.apps)
        assertEquals(NotificationAnnouncementPolicy.MAX_PER_MINUTE_CEILING, decoded.maxPerMinute)
        assertEquals(1, AnnouncementSettingsCodec.decode("""{"v":1,"maxPerMinute":-5}""").maxPerMinute)

        val many = AnnouncementSettings(true, (1..200).associate { "com.example.app$it" to AppRule(AnnouncementMode.APP_ONLY) })
        val bounded = AnnouncementSettingsCodec.decode(AnnouncementSettingsCodec.encode(many))
        assertEquals(AnnouncementSettingsCodec.MAX_APPS, bounded.apps.size)
        assertTrue(bounded.apps.containsKey("com.example.app1"))

        // OFF is the absence of a rule, also when saving.
        val off = AnnouncementSettingsCodec.normalize(AnnouncementSettings(true, mapOf("com.whatsapp" to AppRule(AnnouncementMode.OFF))))
        assertTrue(off.apps.isEmpty())
    }

    @Test fun decodedSettingsDriveThePolicy() {
        val stored = AnnouncementSettingsCodec.encode(
            AnnouncementSettings(true, mapOf("com.whatsapp" to AppRule(AnnouncementMode.SENDER)), quietWhenNotInConversation = false),
        )
        val incoming = IncomingNotification("k", "com.whatsapp", "WhatsApp", "Anna", "Hallo", "msg", false, false, 0L, false)
        val state = AnnouncementState(conversationActive = false, driveModeActive = false, assistantSpeaking = false, listening = false, nowMs = 0L)
        val decision = NotificationAnnouncementPolicy().decide(incoming, AnnouncementSettingsCodec.decode(stored), state)
        assertEquals("WhatsApp: Nachricht von Anna.", (decision as AnnouncementDecision.Announce).spokenText)
        val untouched = NotificationAnnouncementPolicy().decide(incoming, AnnouncementSettingsCodec.decode(null), state)
        assertEquals(AnnouncementDecision.Suppress(SuppressReason.DISABLED), untouched)
    }

    @Test fun notificationAccessIsReadFromTheEnabledListenerList() {
        val app = "at.heinz.deviceai.continuumlocal"
        assertTrue(AnnouncementSettingsCodec.listsComponent("$app/$listener", app, listener))
        assertTrue(AnnouncementSettingsCodec.listsComponent("com.other/com.other.L:$app/$listener:com.x/.Y", app, listener))
        // Short spelling when the class lives below the application id.
        val plain = "at.heinz.deviceai"
        assertTrue(AnnouncementSettingsCodec.listsComponent("$plain/.notifications.ContinuumNotificationListener", plain, listener))

        assertFalse(AnnouncementSettingsCodec.listsComponent(null, app, listener))
        assertFalse(AnnouncementSettingsCodec.listsComponent("", app, listener))
        assertFalse(AnnouncementSettingsCodec.listsComponent("com.other/$listener", app, listener))
        assertFalse(AnnouncementSettingsCodec.listsComponent("$app/at.heinz.deviceai.ContinuumScreenObservationService", app, listener))
        assertFalse(AnnouncementSettingsCodec.listsComponent("$app.evil/$listener", app, listener))
        assertFalse(AnnouncementSettingsCodec.listsComponent("$app/${listener}Fake", app, listener))
        assertFalse(AnnouncementSettingsCodec.listsComponent(listener, app, listener))
        assertFalse(AnnouncementSettingsCodec.listsComponent("/$listener", app, listener))
    }

    @Test fun messengerSuggestionsAreOnlySuggestions() {
        assertEquals(
            listOf(
                "com.whatsapp",
                "org.thoughtcrime.securesms",
                "org.telegram.messenger",
                "com.google.android.apps.messaging",
                "com.samsung.android.messaging",
            ),
            NotificationAnnouncementSettings.knownMessengerPackages,
        )
        // Only what is installed is offered, and the default settings contain none of it.
        val suggested = NotificationAnnouncementSettings.messengerSuggestions(setOf("com.whatsapp", "com.example.other"))
        assertEquals(mapOf("com.whatsapp" to AppRule(AnnouncementMode.SENDER)), suggested)
        assertTrue(NotificationAnnouncementSettings.messengerSuggestions(emptySet()).isEmpty())
        assertTrue(AnnouncementSettings().apps.isEmpty())
        assertEquals(
            mapOf("org.telegram.messenger" to AppRule(AnnouncementMode.APP_ONLY)),
            NotificationAnnouncementSettings.messengerSuggestions(setOf("org.telegram.messenger"), AnnouncementMode.APP_ONLY),
        )
        assertEquals(
            listOf("Aus", "Nur App nennen", "App und Absender nennen", "Inhalt vorlesen"),
            AnnouncementMode.entries.map(NotificationAnnouncementSettings::modeLabel),
        )
    }
}
