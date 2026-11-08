package at.heinz.deviceai.cognitive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitConversationPresentationTest {
    @Test fun spokenHomeTurnStaysVisibleDespiteKeyboardAndLegacyChatFlags() {
        assertFalse(CockpitConversationPresentation.showChat(
            explicitChatSelected = false,
            conversationActive = true,
            keyboardCompact = true,
            handsFreeActive = true,
            pinHomeCockpit = true
        ))
    }

    @Test fun userExplicitlyOpeningTextChatAlwaysWins() {
        assertTrue(CockpitConversationPresentation.showChat(
            explicitChatSelected = true,
            conversationActive = false,
            keyboardCompact = false,
            handsFreeActive = true,
            pinHomeCockpit = true
        ))
    }

    @Test fun inactiveVoiceCannotHideNormalChatOrKeyboard() {
        assertTrue(CockpitConversationPresentation.showChat(false, true, false, false, true))
        assertTrue(CockpitConversationPresentation.showChat(false, false, true, false, true))
        assertFalse(CockpitConversationPresentation.showChat(false, false, false, false, true))
    }

    @Test fun resultRouteMustHaveBothActiveVoiceAndOwnerHomeStage() {
        assertTrue(CockpitConversationPresentation.keepHomeForResult(true, true, true))
        assertFalse(CockpitConversationPresentation.keepHomeForResult(true, true, false))
        assertFalse(CockpitConversationPresentation.keepHomeForResult(false, true, true))
        assertFalse(CockpitConversationPresentation.keepHomeForResult(true, false, true))
    }
    @Test fun liveHandsFreeOrNative3dSurfaceCannotBeReparented() {
        assertFalse(CockpitConversationPresentation.mayReparentHologram(
            handsFreeActive = true, native3dEnabled = false))
        assertFalse(CockpitConversationPresentation.mayReparentHologram(
            handsFreeActive = false, native3dEnabled = true))
        assertFalse(CockpitConversationPresentation.mayReparentHologram(
            handsFreeActive = true, native3dEnabled = true))
        assertTrue(CockpitConversationPresentation.mayReparentHologram(
            handsFreeActive = false, native3dEnabled = false))
    }

}
