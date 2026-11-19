package at.heinz.deviceai.sending

import at.heinz.deviceai.OwnerLaneOutcome
import at.heinz.deviceai.OwnerLaneResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The send lane's entry for a message that is a step of a sequence of orders ([OwnerSendDirector.missionSend]): every
 * gate of an order the owner says alone stays in force – the grant, the confirmed relation or the question, the first
 * send through a new relation, the screen verification, one press – and the sequence's guard can only add a refusal.
 */
class OwnerSendMissionEntryTest {
    private class Guard(var allow: Boolean = true) : OwnerSendGuard {
        val reserved = ArrayList<SendPlan>()
        val presses = ArrayList<Boolean>()
        override fun reserve(plan: SendPlan): Boolean { reserved += plan; return allow }
        override fun pressed(plan: SendPlan, clicked: Boolean) { presses += clicked }
    }

    private class Host(val fake: FakeSendHost) : OwnerSendHost by fake {
        val outcomes = ArrayList<OwnerLaneOutcome>()
        val progress = ArrayList<OwnerSendProgress>()
        override fun outcome(outcome: OwnerLaneOutcome) { outcomes += outcome }
        override fun progress(event: OwnerSendProgress) { progress += event }
    }

    private fun host(grant: Boolean = true, father: Boolean = true): Host = Host(FakeSendHost().apply {
        contacts.phoneRows = listOf(FakeContacts.phone(7, "Thomas Muster", "+43 660 1234567"), FakeContacts.phone(8, "Maria Muster", "+43 660 7654321"),
            // A contact whose name only contains the relation word must never be taken for the father.
            FakeContacts.phone(9, "Vater Lukas (Kita)", "+43 660 9999999"))
        device.whatsAppWorks()
        if (grant) grant(SendChannel.WHATSAPP)
        if (father) { relations.remember(OwnerRelation.VATER, 7, "Thomas Muster"); relations.markSent(OwnerRelation.VATER) }
    })

    private val clause = "schick meinem Vater auf WhatsApp, dass ich mich später melde"

    @Test fun aStepIsSentLikeAnOrderSaidAloneAndTheGuardIsAskedRightBeforeTheOnePress() {
        val h = host()
        val guard = Guard()
        val lane = OwnerSendDirector(h)
        assertTrue(lane.missionSend(clause, guard = guard))
        val plan = h.fake.device.opened.single() as SendPlan.WhatsApp
        assertEquals("Thomas Muster" to "Ich melde mich später", plan.contactName to plan.text)
        assertEquals(listOf<SendPlan>(plan), guard.reserved)
        assertEquals(listOf(true), guard.presses)
        assertEquals(1, h.fake.device.pressed.size)
        assertEquals(OwnerLaneOutcome.Done(OwnerLaneResult.Delivered("WhatsApp", "Thomas Muster", null, OwnerLaneResult.Delivered.State.SENT)), h.outcomes.single())
        // Resolved recipient, channel, verified draft – in that order, before the press; a name, never a number.
        assertEquals(listOf(OwnerSendProgress.RecipientResolved("Thomas Muster"), OwnerSendProgress.ChannelChosen("WhatsApp"),
            OwnerSendProgress.DraftVerified), h.progress)
        assertTrue(h.progress.none { it.toString().contains("660") || it.toString().contains("melde") })
    }

    @Test fun withoutTheGrantOnlyADraftIsPreparedAndTheGuardIsNeverAsked() {
        val h = host(grant = false)
        val guard = Guard()
        assertTrue(OwnerSendDirector(h).missionSend(clause, guard = guard))
        assertTrue(h.fake.device.pressed.isEmpty() && guard.reserved.isEmpty())
        assertEquals(OwnerLaneResult.Delivered.State.DRAFT, ((h.outcomes.single() as OwnerLaneOutcome.Done).result as OwnerLaneResult.Delivered).state)
        assertTrue(h.fake.lastReply, h.fake.lastReply.contains("Gesendet habe ich nichts"))
    }

    @Test fun aRefusingGuardMeansNothingIsPressed() {
        val h = host()
        val guard = Guard(allow = false)
        assertTrue(OwnerSendDirector(h).missionSend(clause, guard = guard))
        assertEquals(1, guard.reserved.size)
        assertTrue(h.fake.device.pressed.isEmpty() && guard.presses.isEmpty())
        assertTrue(h.outcomes.single() is OwnerLaneOutcome.Failed)
        assertTrue(h.fake.lastReply, h.fake.lastReply.contains("gesendet habe ich nichts") && h.fake.lastReply.contains("ohne diesen Vermerk drücke ich nicht"))
        // The daily limit was not touched by a press that did not happen.
        assertTrue(h.fake.grants.reserveSend(SendChannel.WHATSAPP))
    }

    @Test fun aClickTheAppRefusedReleasesTheGuard() {
        val h = host()
        h.fake.device.pressAccepted = false
        val guard = Guard()
        assertTrue(OwnerSendDirector(h).missionSend(clause, guard = guard))
        assertEquals(listOf(false), guard.presses)
        assertTrue(h.outcomes.single() is OwnerLaneOutcome.Failed)
    }

    @Test fun anUnconfirmedRelationIsAskedForAndNeverGuessedFromAContactName() {
        val h = host(father = false)
        val guard = Guard()
        val lane = OwnerSendDirector(h)
        assertTrue(lane.missionSend(clause, guard = guard))
        assertTrue(h.fake.lastReply, h.fake.lastReply.contains("Wer ist dein Vater in den Kontakten?"))
        assertTrue(h.outcomes.single() is OwnerLaneOutcome.NeedsOwner)
        assertTrue(h.fake.device.opened.isEmpty() && guard.reserved.isEmpty())
        // The owner names him, says yes to remembering – and the first send through the new relation is still confirmed.
        assertTrue(lane.handle("Thomas Muster"))
        assertTrue(lane.handle("Ja"))
        assertTrue(h.fake.lastReply, h.fake.lastReply.endsWith("Soll ich das so senden?"))
        assertTrue(h.fake.device.pressed.isEmpty())
        assertTrue(lane.handle("Ja"))
        // The guard came along through three owner turns and was asked once, for exactly this message.
        assertEquals(1, guard.reserved.size)
        assertEquals("Thomas Muster", (guard.reserved.single() as SendPlan.WhatsApp).contactName)
        assertEquals(1, h.fake.device.pressed.size)
    }

    @Test fun theChannelGivenByTheClauseInFrontIsEnoughForTheWritingClause() {
        val h = host()
        // Said alone the clause names no channel: it is no send order.
        assertNull(OwnerSendIntentParser.parse("schreib meinem Vater guten Morgen"))
        assertFalse(OwnerSendDirector(h).missionSend("schreib meinem Vater guten Morgen"))
        assertTrue(OwnerSendDirector(h).missionSend("schreib meinem Vater guten Morgen", channelGiven = true, guard = Guard()))
        val plan = h.fake.device.opened.single() as SendPlan.WhatsApp
        assertEquals("Thomas Muster" to "Guten Morgen", plan.contactName to plan.text)
        // A „WhatsApp“ inside the dictated text is text.
        val other = host()
        assertTrue(OwnerSendDirector(other).missionSend("schreib meinem Vater: Ich bin auf WhatsApp erreichbar", channelGiven = true, guard = Guard()))
        assertEquals("Ich bin auf WhatsApp erreichbar", (other.fake.device.opened.single() as SendPlan.WhatsApp).text)
    }

    @Test fun aCorrectedRecipientReplacesOnlyTheRecipient() {
        val h = host()
        h.fake.relations.remember(OwnerRelation.MUTTER, 8, "Maria Muster")
        h.fake.relations.markSent(OwnerRelation.MUTTER)
        assertTrue(OwnerSendDirector(h).missionSend(clause, recipientOverride = "Mama", guard = Guard()))
        assertEquals("Maria Muster" to "Ich melde mich später", (h.fake.device.opened.single() as SendPlan.WhatsApp).let { it.contactName to it.text })
        // Without a separator the relation in front is replaced and the text behind it stays.
        val loose = host()
        loose.fake.relations.remember(OwnerRelation.MUTTER, 8, "Maria Muster")
        loose.fake.relations.markSent(OwnerRelation.MUTTER)
        assertTrue(OwnerSendDirector(loose).missionSend("schreib meinem Vater guten Morgen", channelGiven = true, recipientOverride = "Mama", guard = Guard()))
        assertEquals("Maria Muster" to "Guten Morgen", (loose.fake.device.opened.single() as SendPlan.WhatsApp).let { it.contactName to it.text })
    }

    @Test fun ordersTheLaneNeverCarriesOutAreRefusedAsAlways() {
        val h = host()
        val guard = Guard()
        assertTrue(OwnerSendDirector(h).missionSend("schick allen auf WhatsApp, dass ich komme", guard = guard))
        assertEquals(OwnerSendIntentParser.REFUSED_GROUP, h.fake.lastReply)
        assertTrue(OwnerSendDirector(h).missionSend("schick Anna nicht auf WhatsApp, dass ich komme", guard = guard))
        assertEquals(OwnerSendIntentParser.REFUSED_NEGATED, h.fake.lastReply)
        assertTrue(h.fake.device.opened.isEmpty() && guard.reserved.isEmpty())
        assertTrue(h.outcomes.all { it is OwnerLaneOutcome.Failed })
    }
}
