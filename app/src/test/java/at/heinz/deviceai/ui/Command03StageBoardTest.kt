package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.Command03StageBoard.Card
import at.heinz.deviceai.ui.Command03StageBoard.CardKind
import at.heinz.deviceai.ui.Command03StageBoard.Check
import at.heinz.deviceai.ui.Command03StageBoard.CheckState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code71: the start-up checks and the info cards of the Command03 stage. The stage shows what a provider handed
 * over and nothing else – without a provider both snapshot fields are empty arrays and the page renders nothing.
 */
class Command03StageBoardTest {
    private fun objects(list: JSONArray): List<JSONObject> = List(list.length(), list::getJSONObject)

    @Test fun withoutAProviderBothListsAreEmptyArrays() {
        val board = Command03StageBoard()
        assertEquals("[]", board.systemCheck().toString())
        assertEquals("[]", board.briefingCards().toString())
        assertFalse("An empty hand-over changes nothing", board.setSystemCheck(emptyList()))
        assertFalse(board.setBriefingCards(emptyList()))
    }

    @Test fun checksReachTheSnapshotInTheirExactShapeAndOrder() {
        val board = Command03StageBoard()
        assertTrue(board.setSystemCheck(listOf(
            Check("brain", "Gehirn", CheckState.OK, "Claude ausgewählt"),
            Check("internet", "Internet", CheckState.WARN, "Nur auf Nachfrage"),
            Check("voice", "Stimme", CheckState.FAIL, "Sprachausgabe nicht bereit"),
            Check("screen", "Bildschirmfreigabe", CheckState.OFF))))
        val items = objects(board.systemCheck())
        assertEquals(listOf("brain", "internet", "voice", "screen"), items.map { it.getString("key") })
        assertEquals(listOf("OK", "WARN", "FAIL", "OFF"), items.map { it.getString("state") })
        assertEquals("Gehirn", items[0].getString("label"))
        assertEquals("Claude ausgewählt", items[0].getString("detail"))
        assertEquals("A check without a detail carries an empty one", "", items[3].getString("detail"))
        assertEquals(setOf("key", "label", "state", "detail"), items[0].keys().asSequence().toSet())
    }

    @Test fun onlyARealChangeAsksForANewSnapshot() {
        val board = Command03StageBoard()
        val checks = listOf(Check("brain", "Gehirn", CheckState.OK))
        assertTrue(board.setSystemCheck(checks))
        assertFalse(board.setSystemCheck(checks.toList()))
        assertTrue(board.setSystemCheck(listOf(Check("brain", "Gehirn", CheckState.FAIL, "Kein Modell geladen"))))
        assertTrue("The provider withdraws its checks", board.setSystemCheck(emptyList()))
        assertEquals(0, board.systemCheck().length())
        val cards = listOf(Card("weather", CardKind.WEATHER, "Wetter", listOf("Linz · 4 °C")))
        assertTrue(board.setBriefingCards(cards))
        assertFalse(board.setBriefingCards(cards.toList()))
        assertTrue(board.setBriefingCards(emptyList()))
    }

    @Test fun checksAreBoundedPlainTextAndAnUnusableItemIsDroppedNotRepaired() {
        val board = Command03StageBoard()
        board.setSystemCheck(listOf(
            Check("ok", "L".repeat(200), CheckState.OK, "D".repeat(500)),
            Check("ok", "Doppelt", CheckState.FAIL),
            Check("", "Ohne Schlüssel", CheckState.OK),
            Check("leer", "  ", CheckState.OK),
            Check("mit leerzeichen", "Ungültiger Schlüssel", CheckState.OK),
            Check("steuer", "a\u0007b\nc", CheckState.WARN, "x\u0000y")) +
            (1..20).map { Check("k$it", "Prüfung $it", CheckState.OK) })
        val items = objects(board.systemCheck())
        assertEquals(Command03StageBoard.MAX_CHECKS, items.size)
        assertEquals(listOf("ok", "steuer", "k1"), items.take(3).map { it.getString("key") })
        assertEquals("The first item of a key stays", "OK", items[0].getString("state"))
        assertEquals(Command03StageBoard.MAX_CHECK_LABEL, items[0].getString("label").length)
        assertTrue(items[0].getString("label").endsWith("…"))
        assertEquals(Command03StageBoard.MAX_CHECK_DETAIL, items[0].getString("detail").length)
        assertEquals("abc", items[1].getString("label"))
        assertEquals("xy", items[1].getString("detail"))
    }

    @Test fun cardsReachTheSnapshotInTheirExactShape() {
        val board = Command03StageBoard()
        assertTrue(board.setBriefingCards(listOf(
            Card("cal-today", CardKind.CALENDAR, "Heute", listOf("09:00 Zahnarzt", "14:30 Werkstatt")),
            Card("weather", CardKind.WEATHER, "Wetter", listOf("Linz · 4 °C · Regen ab 15 Uhr")),
            Card("msg", CardKind.MESSAGES, "2 neue Nachrichten", listOf("Anna: Bist du da?"), attention = true),
            Card("tasks", CardKind.TASKS, "Aufgaben"),
            Card("news", CardKind.NEWS, "Schlagzeilen", listOf("Eine", "Zwei")),
            Card("sys", CardKind.SYSTEM, "Speicher", listOf("12 GB frei")))))
        val items = objects(board.briefingCards())
        assertEquals(listOf("calendar", "weather", "messages", "tasks", "news", "system"), items.map { it.getString("kind") })
        assertEquals(listOf("info", "info", "attention", "info", "info", "info"), items.map { it.getString("state") })
        assertEquals("Heute", items[0].getString("title"))
        assertEquals("[\"09:00 Zahnarzt\",\"14:30 Werkstatt\"]", items[0].getJSONArray("lines").toString())
        assertEquals("A card without lines carries an empty list", 0, items[3].getJSONArray("lines").length())
        assertEquals(setOf("id", "kind", "title", "lines", "state"), items[0].keys().asSequence().toSet())
    }

    @Test fun cardsAreBoundedAtMostFourLinesEachAndSixCards() {
        val board = Command03StageBoard()
        board.setBriefingCards(listOf(
            Card("a", CardKind.NEWS, "T".repeat(300), (1..9).map { "Zeile $it " + "x".repeat(200) } + listOf("", "  ")),
            Card("a", CardKind.NEWS, "Doppelt"),
            Card("ohne titel", CardKind.NEWS, "Ungültige Kennung"),
            Card("b", CardKind.NEWS, " ")) + (1..10).map { Card("c$it", CardKind.TASKS, "Karte $it", listOf("\u0007")) })
        val items = objects(board.briefingCards())
        assertEquals(Command03StageBoard.MAX_CARDS, items.size)
        assertEquals(listOf("a", "c1", "c2", "c3", "c4", "c5"), items.map { it.getString("id") })
        assertEquals(Command03StageBoard.MAX_CARD_TITLE, items[0].getString("title").length)
        val lines = items[0].getJSONArray("lines")
        assertEquals(Command03StageBoard.MAX_CARD_LINES, lines.length())
        assertEquals(Command03StageBoard.MAX_CARD_LINE, lines.getString(0).length)
        assertEquals("A line of control characters only is no line", 0, items[1].getJSONArray("lines").length())
    }

    @Test fun aProviderThatAlreadySpeaksJsonIsReadInTheSnapshotShapeAndUnknownValuesAreLeftOut() {
        val checks = Command03StageBoard.checksOf(JSONArray("""[
            {"key":"brain","label":"Gehirn","state":"OK","detail":"Claude"},
            {"key":"x","label":"Unbekannt","state":"MAYBE","detail":""},
            "kein Objekt",
            {"key":"voice","label":"Stimme","state":"FAIL"}]"""))
        assertEquals(listOf(Check("brain", "Gehirn", CheckState.OK, "Claude"), Check("voice", "Stimme", CheckState.FAIL, "")), checks)
        val cards = Command03StageBoard.cardsOf(JSONArray("""[
            {"id":"w","kind":"weather","title":"Wetter","lines":["4 °C", 7, "Regen"],"state":"attention"},
            {"id":"q","kind":"horoscope","title":"Nein","lines":[],"state":"info"},
            {"id":"r","kind":"news","title":"Nein","lines":[],"state":"urgent"}]"""))
        assertEquals(listOf(Card("w", CardKind.WEATHER, "Wetter", listOf("4 °C", "Regen"), attention = true)), cards)
        assertEquals(emptyList<Check>(), Command03StageBoard.checksOf(null))
        assertEquals(emptyList<Card>(), Command03StageBoard.cardsOf(null))
        // Round trip: what the board publishes is what the reader accepts.
        val board = Command03StageBoard()
        board.setSystemCheck(checks)
        board.setBriefingCards(cards)
        assertEquals(checks, Command03StageBoard.checksOf(board.systemCheck()))
        assertEquals(cards, Command03StageBoard.cardsOf(board.briefingCards()))
    }
}
