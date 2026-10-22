package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumWorkspaceNavSpecTest {
    @Test fun everyAreaIsAtMostTwoTapsAwayAndListedOnce() {
        val bar = ContinuumWorkspaceNavSpec.bottomBar
        val more = ContinuumWorkspaceNavSpec.more
        assertEquals(ContinuumWorkArea.entries.toSet(), (bar + more).toSet())
        assertEquals(bar.size + more.size, (bar + more).toSet().size)
        assertEquals(ContinuumWorkArea.entries.size, ContinuumWorkspaceNavSpec.all.map { it.area }.toSet().size)
    }

    @Test fun barFitsPhonesAndStartsWithStartAndChat() {
        assertEquals(listOf(ContinuumWorkArea.START, ContinuumWorkArea.CHAT), ContinuumWorkspaceNavSpec.bottomBar.take(2))
        assertTrue(ContinuumWorkspaceNavSpec.bottomBar.size + 1 <= 5) // plus "Mehr"
        val labels = ContinuumWorkspaceNavSpec.bottomBar.map { ContinuumWorkspaceNavSpec.item(it).label } +
            ContinuumWorkspaceNavSpec.MORE_LABEL
        assertTrue(labels.all { it.isNotBlank() && it.length <= ContinuumWorkspaceNavSpec.MAX_BAR_LABEL })
    }

    @Test fun homeGridKeepsEveryFormerExpertFunctionVisible() {
        // the six areas that used to sit behind "Weitere KI-Funktionen anzeigen"
        assertEquals(listOf(ContinuumWorkArea.CHAT, ContinuumWorkArea.RESEARCH, ContinuumWorkArea.LEARNING,
            ContinuumWorkArea.MODELS, ContinuumWorkArea.DEVELOPMENT, ContinuumWorkArea.MEMORY),
            ContinuumWorkspaceNavSpec.homeGrid)
        assertFalse(ContinuumWorkArea.START in ContinuumWorkspaceNavSpec.homeGrid)
    }

    @Test fun labelsSymbolsAndDescriptionsAreDistinctAndReadable() {
        val items = ContinuumWorkspaceNavSpec.all
        assertEquals(items.size, items.map { it.symbol }.toSet().size)
        assertEquals(items.size, items.map { it.label }.toSet().size)
        assertTrue(items.all { it.label.length <= ContinuumWorkspaceNavSpec.MAX_BAR_LABEL && it.description.length in 10..60 })
        assertFalse(items.any { it.symbol == ContinuumWorkspaceNavSpec.MORE_SYMBOL })
    }

    @Test fun gridAdaptsToWidthAndFontScale() {
        assertEquals(3, ContinuumWorkspaceNavSpec.gridColumns(332, 1f))
        assertEquals(2, ContinuumWorkspaceNavSpec.gridColumns(300, 1f))
        assertEquals(2, ContinuumWorkspaceNavSpec.gridColumns(600, 1.5f))
        assertEquals(1, ContinuumWorkspaceNavSpec.gridColumns(230, 1f))
        assertEquals(1, ContinuumWorkspaceNavSpec.gridColumns(600, Float.NaN))
    }

    @Test fun selectionAndKeyboardBehaviour() {
        assertEquals(ContinuumWorkArea.CHAT, ContinuumWorkspaceNavSpec.selected(chatActive = true))
        assertEquals(ContinuumWorkArea.START, ContinuumWorkspaceNavSpec.selected(chatActive = false))
        assertFalse(ContinuumWorkspaceNavSpec.barVisible(keyboardVisible = true))
        assertTrue(ContinuumWorkspaceNavSpec.barVisible(keyboardVisible = false))
    }
}
