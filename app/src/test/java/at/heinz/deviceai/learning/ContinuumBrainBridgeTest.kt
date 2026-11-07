package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AP-2/AP-3 integration on the real learning core, SWB1 dialogue and knowledge archive (synthetic facts and
 * sources only). The baseline is composed exactly like AgentActivity.runContinuumDialogue composes today's
 * answer (without the Android-only reviewed web notes).
 */
class ContinuumBrainBridgeTest {
    private val dirs=ArrayList<File>()

    @After fun cleanUp() {
        ContinuumBrainBridge.beforeRevocationCheck=null
        dirs.forEach { it.deleteRecursively() }
    }

    private fun app(vararg facts:String,sources:List<Pair<String,String>> = emptyList()):Pair<File,ContinuumLearningCore> {
        val dir=Files.createTempDirectory("brain-bridge").toFile().also { dirs+=it }
        val core=ContinuumLearningCore(dir)
        facts.forEach { core.confirmOwnerFact(it) }
        val archive=ContinuumKnowledgeArchive(dir)
        sources.forEach { (title,text) ->
            archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,title,
                "https://de.wikipedia.org/wiki/"+title.replace(' ','_'),"Synthetisch · Test",text,
                collectedAt=1_760_000_000_000L)
        }
        return dir to core
    }

    /** Today's outcome as AgentActivity builds it (memory turn, then the archive answer policy). */
    private fun baseline(dir:File,core:ContinuumLearningCore,conversation:String,request:String):ContinuumChatOutcome {
        val cognitive=ContinuumCognitiveRuntime(dir,core,conversation).reply(request)
        val turn=cognitive.dialogue
        val archiveHit=if(!turn.grounded && request.length in 6..240 &&
            (request.contains("?") || request.startsWith("Was ist ",true) || request.startsWith("Erkläre ",true)))
            runCatching { ContinuumArchiveAnswerPolicy.select(request,ContinuumKnowledgeArchive(dir).search(request,limit=20)) }.getOrNull()
        else null
        val archiveAnswer=archiveHit?.source?.let { ContinuumBrainBridge.archiveAnswerForQuestion(it,request) }
        val evidence=if(archiveAnswer!=null)"QUELLENABRUF · lokal archivierter, nicht bestätigter Beleg"
            else "CONTINUUM · Modell "+turn.modelHash.take(12)+" · "+turn.memoryIds.size+" aktuelle Erinnerungen"
        return ContinuumChatOutcome(archiveAnswer ?: cognitive.answer,evidence,cognitive.trace,
            turn.grounded || archiveAnswer!=null,
            when {
                archiveAnswer!=null -> ContinuumBaselineSource.ARCHIVE
                turn.grounded -> ContinuumBaselineSource.MEMORY
                else -> ContinuumBaselineSource.NONE
            },turn.memoryIds,archiveHit?.source?.id?.takeIf { archiveAnswer!=null })
    }

    private fun idOf(core:ContinuumLearningCore,text:String)=core.activeOwnerFacts().single { it.content==text }.id

    private val family=arrayOf("Meine Schwester heißt Anna.","Anna wohnt in Linz.","Anna spielt Cello.")
    private val linz=listOf("Linz" to "Linz ist die Landeshauptstadt von Oberösterreich an der Donau.")

    @Test fun offIsTheDefaultComputesNothingAndReturnsTodaysOutcome() {
        val (dir,core)=app(*family,sources=linz)
        assertEquals(ContinuumBrainMode.OFF,ContinuumBrainSettings(dir).mode())
        val today=baseline(dir,core,"conv-off","Wo wohnt Anna?")
        val result=ContinuumBrainBridge.decide(dir,core,"conv-off","Wo wohnt Anna?",today)
        assertSame(today,result.outcome)
        assertEquals(ContinuumBrainStatus.OFF,result.status)
        assertFalse("OFF writes nothing",File(dir,"learning-core-v2/ewm-brain").exists())
    }

    @Test fun shadowNeverChangesAnyAnswerAndRecordsOnlyContentFreeCounts() {
        val (dir,core)=app(*family,sources=linz)
        ContinuumBrainSettings(dir).set(ContinuumBrainMode.SHADOW)
        val turns=listOf("Wo wohnt Anna?","Was ist Linz?","Was spielt sie?","Was ist ein Cello?","Wie alt ist Anna?",
            "Kannst du mir etwas über Linz sagen?","Hallo")
        for(request in turns) {
            val today=baseline(dir,core,"conv-shadow",request)
            val result=ContinuumBrainBridge.decide(dir,core,"conv-shadow",request,today)
            assertSame("SHADOW must return today's outcome object for: $request",today,result.outcome)
            assertEquals(ContinuumBrainStatus.SHADOW,result.status)
        }
        val log=ContinuumBrainShadowLog(dir).lines()
        assertEquals(turns.size,log.size)
        for(word in listOf("Anna","Linz","Cello","Schwester","wohnt","spielt","Oberösterreich"))
            assertFalse("no content in the shadow log: $word",log.any { it.contains(word,ignoreCase=true) })
        assertTrue(ContinuumBrainShadowLog(dir).summary().startsWith("Schattenvergleiche: "+turns.size+" Züge"))
    }

    @Test fun onAnswersFromOwnerMemoryAndFromLabelledSources() {
        val (dir,core)=app(*family,sources=linz)
        ContinuumBrainSettings(dir).set(ContinuumBrainMode.ON)
        val memory=ContinuumBrainBridge.decide(dir,core,"conv-on","Wo wohnt Anna?",baseline(dir,core,"conv-on","Wo wohnt Anna?"))
        assertEquals(ContinuumBrainStatus.ON_MEMORY,memory.status)
        assertTrue(memory.outcome.grounded)
        assertTrue(memory.outcome.answer.contains("Anna wohnt in Linz."))
        assertTrue(memory.outcome.answer.contains("(Bestätigte Nutzererinnerung)"))
        val source=ContinuumBrainBridge.decide(dir,core,"conv-on","Was ist Linz?",baseline(dir,core,"conv-on","Was ist Linz?"))
        assertEquals(ContinuumBrainStatus.ON_SOURCE,source.status)
        assertTrue(source.outcome.answer.startsWith("LOKALES WISSENSARCHIV · öffentlicher, nicht unabhängig verifizierter"))
        assertTrue(source.outcome.answer.contains("Quelle: https://de.wikipedia.org/wiki/Linz"))
        // a source never becomes an owner fact
        assertTrue(core.activeOwnerFacts().none { it.content.contains("Landeshauptstadt") })
    }

    @Test fun sourceAnswerReopensFullArchivedTextAndQuotesRelevantLateEvidence() {
        val text="Allgemeiner Vorspann und Navigation. ".repeat(35)+
            "Das Quarzkompassverfahren verwendet die Kennung ZIRKON-492 für Resonanzmessung."
        val (dir,core)=app(sources=listOf("Quarzkompassverfahren" to text))
        try {
            val archive=ContinuumKnowledgeArchive(dir)
            val record=archive.search("Quarzkompassverfahren").single().source
            assertEquals(record,archive.readSource(record.id))
            assertFalse(record.text.take(350).contains("ZIRKON-492"))
            assertTrue(ContinuumBrainBridge.archiveAnswerForQuestion(record,"Was ist Quarzkompassverfahren?")!!.contains("ZIRKON-492"))
            val factsBefore=core.activeOwnerFacts()
            ContinuumBrainSettings(dir).set(ContinuumBrainMode.ON)
            val result=ContinuumBrainBridge.decide(dir,core,"late-source","Was ist Quarzkompassverfahren?",
                baseline(dir,core,"late-source","Was ist Quarzkompassverfahren?"))
            assertEquals(ContinuumBrainStatus.ON_SOURCE,result.status)
            assertTrue(result.outcome.answer.contains("ZIRKON-492"))
            assertTrue(result.outcome.answer.contains(record.sha256))
            assertTrue(result.outcome.answer.contains("UTF-8-Bytes"))
            assertTrue(result.outcome.answer.contains("kein unabhängig geprüfter Antwortbeweis"))
            assertEquals(factsBefore,core.activeOwnerFacts())
            assertEquals(record,archive.readSource(record.id))
        } finally { dir.deleteRecursively() }
    }

    @Test fun aTitleMatchWithoutAQuotableAnchorCannotCreateArchiveGrounding() {
        val (dir,core)=app(sources=listOf("Quarzkompassverfahren" to
            "Dieser allgemeine Text behandelt ausschließlich Zimmerpflanzen und ihre Pflege."))
        try {
            val today=baseline(dir,core,"no-quote","Was ist Quarzkompassverfahren?")
            assertFalse(today.grounded)
            assertNull(today.archiveId)
            assertEquals(ContinuumBaselineSource.NONE,today.source)
            ContinuumBrainSettings(dir).set(ContinuumBrainMode.ON)
            val result=ContinuumBrainBridge.decide(dir,core,"no-quote","Was ist Quarzkompassverfahren?",today)
            assertSame(today,result.outcome)
            assertEquals(ContinuumBrainStatus.ON_KEPT_BASELINE,result.status)
        } finally { dir.deleteRecursively() }
    }

    @Test fun enabledBrainCannotResurrectTheOldResidenceAfterNewerRevocation() {
        val dir=Files.createTempDirectory("continuum-brain-attribute-tombstone").toFile()
        try {
            var at=10L
            val core=ContinuumLearningCore(dir) { at++ }
            core.confirmOwnerFact("Mira wohnt in Wien.")
            core.confirmOwnerFact("Mira wohnt jetzt in Graz.")
            core.revokeOwnerFact("Mira wohnt jetzt in Graz.")
            val today=baseline(dir,core,"tombstone","Wo wohnt Mira?")
            assertFalse(today.grounded)
            ContinuumBrainSettings(dir).set(ContinuumBrainMode.ON)
            val result=ContinuumBrainBridge.decide(dir,core,"tombstone","Wo wohnt Mira?",today)
            assertFalse(result.outcome.grounded)
            assertFalse(result.outcome.answer.contains("Mira wohnt in Wien."))
            assertTrue(result.outcome.memoryIds.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun enabledBrainUsesResolvedPossessiveScopeAndCannotRestoreARevokedNewerProperty() {
        val dir=Files.createTempDirectory("continuum-brain-elliptic-tombstone").toFile()
        try {
            var at=10L
            val core=ContinuumLearningCore(dir) { at++ }
            core.confirmOwnerFact("Elif besitzt einen blauen Rucksack.")
            core.confirmOwnerFact("Elif besitzt jetzt einen roten Rucksack.")
            core.confirmOwnerFact("Daria besitzt einen grünen Rucksack.")
            core.revokeOwnerFact("Elif besitzt jetzt einen roten Rucksack.")
            ContinuumBrainSettings(dir).set(ContinuumBrainMode.ON)
            val first="Welche Farbe hat Darias Rucksack?"
            assertTrue(ContinuumBrainBridge.decide(dir,core,"elliptic-tombstone",first,
                baseline(dir,core,"elliptic-tombstone",first)).outcome.grounded)
            val next="Und Elifs?"
            val today=baseline(dir,core,"elliptic-tombstone",next)
            assertFalse(today.grounded)
            val shown=ContinuumBrainBridge.decide(dir,core,"elliptic-tombstone",next,today)
            assertFalse(shown.outcome.grounded)
            assertFalse(shown.outcome.answer.contains("Elif besitzt einen blauen Rucksack."))
        } finally { dir.deleteRecursively() }
    }

    @Test fun onReplacesAnUnsupportedOwnerCitationWithHonestNotKnowingButKeepsWebNotes() {
        val (dir,core)=app(*family)
        ContinuumBrainSettings(dir).set(ContinuumBrainMode.ON)
        val cello=idOf(core,"Anna spielt Cello.")
        // today's path citing a mere mention for a definition question (the error class measured in TEST5)
        val wrong=ContinuumChatOutcome("Dazu finde ich in meinem Gedächtnis:\n\nAnna spielt Cello.","CONTINUUM · Modell x","trace",
            true,ContinuumBaselineSource.MEMORY,listOf(cello))
        val result=ContinuumBrainBridge.decide(dir,core,"conv-abstain","Was ist ein Cello?",wrong)
        assertEquals(ContinuumBrainStatus.ON_ABSTAIN,result.status)
        assertEquals(ContinuumMemoryDialogue.NO_EVIDENCE_ANSWER,result.outcome.answer)
        assertFalse(result.outcome.grounded)
        val webNote=ContinuumChatOutcome("Laut geprüfter Notiz …","QUELLENGEDÄCHTNIS","trace",true,ContinuumBaselineSource.WEB_NOTES)
        val kept=ContinuumBrainBridge.decide(dir,core,"conv-abstain","Was ist ein Fagott?",webNote)
        assertEquals(ContinuumBrainStatus.ON_KEPT_BASELINE,kept.status)
        assertSame(webNote,kept.outcome)
    }

    @Test fun aRevokedFactIsNotShownAfterRevocationAndARacingRevocationFallsBackToTodaysOutcome() {
        val (dir,core)=app(*family)
        ContinuumBrainSettings(dir).set(ContinuumBrainMode.ON)
        assertEquals(ContinuumBrainStatus.ON_MEMORY,
            ContinuumBrainBridge.decide(dir,core,"conv-revoke","Wo wohnt Anna?",baseline(dir,core,"conv-revoke","Wo wohnt Anna?")).status)
        core.revokeOwnerFact("Anna wohnt in Linz.")
        val after=ContinuumBrainBridge.decide(dir,core,"conv-revoke","Wo wohnt Anna?",baseline(dir,core,"conv-revoke","Wo wohnt Anna?"))
        assertFalse(after.outcome.answer.contains("Anna wohnt in Linz."))
        // If a fact is revoked after baseline rendering, the baseline can contain that fact.
        // Never display the stale baseline even when the brain notices the revocation.
        val today=ContinuumChatOutcome("Dazu finde ich in meinem Gedächtnis:\n\nAnna spielt Cello.",
            "CONTINUUM · Modell x","trace",true,ContinuumBaselineSource.MEMORY,
            listOf(idOf(core,"Anna spielt Cello.")))
        assertTrue("pre-revocation baseline cites Cello",today.answer.contains("Anna spielt Cello."))
        ContinuumBrainBridge.beforeRevocationCheck={ core.revokeOwnerFact("Anna spielt Cello.") }
        val raced=ContinuumBrainBridge.decide(dir,core,"conv-race","Was spielt Anna?",today)
        assertEquals(ContinuumBrainStatus.FALLBACK_REVOKED,raced.status)
        assertEquals(ContinuumMemoryDialogue.NO_EVIDENCE_ANSWER,raced.outcome.answer)
        assertFalse("revoked text must never reappear",raced.outcome.answer.contains("Anna spielt Cello."))
        assertFalse(raced.outcome.grounded)
        assertEquals(ContinuumBaselineSource.NONE,raced.outcome.source)
        assertTrue(raced.outcome.memoryIds.isEmpty())
    }

    @Test fun switchingBrainOffDuringAnInFlightAnswerKeepsTheBaseline() {
        val (dir,core)=app(*family)
        val settings=ContinuumBrainSettings(dir)
        settings.set(ContinuumBrainMode.ON)
        val today=ContinuumChatOutcome("Bisherige Antwort","Bisheriger Beleg","trace",
            false,ContinuumBaselineSource.NONE)
        ContinuumBrainBridge.beforeRevocationCheck={ settings.set(ContinuumBrainMode.OFF) }
        val result=ContinuumBrainBridge.decide(dir,core,"conv-mode-off","Wo wohnt Anna?",today)
        assertEquals(ContinuumBrainMode.OFF,settings.mode())
        assertEquals(ContinuumBrainStatus.OFF,result.status)
        assertSame(today,result.outcome)
    }

    @Test fun sessionStateBelongsToOneConversationAndCanBeForgotten() {
        // two women: without this conversation's focus "sie" has no unique referent
        val (dir,core)=app(*family,"Meine Nachbarin Rita spielt Geige.")
        ContinuumBrainSettings(dir).set(ContinuumBrainMode.ON)
        val none=ContinuumChatOutcome("x","e","t",false,ContinuumBaselineSource.NONE)
        assertEquals(ContinuumBrainStatus.ON_MEMORY,ContinuumBrainBridge.decide(dir,core,"conv-a","Wo wohnt Anna?",none).status)
        val followUp=ContinuumBrainBridge.decide(dir,core,"conv-a","Was spielt sie?",none)
        assertTrue(followUp.outcome.answer.contains("Anna spielt Cello."))
        // another conversation has no referent for "sie"
        val other=ContinuumBrainBridge.decide(dir,core,"conv-b","Was spielt sie?",none)
        assertEquals("new conversation must not guess who \"sie\" is",ContinuumBrainStatus.ON_KEPT_BASELINE,other.status)
        assertSame(none,other.outcome)
        assertFalse(other.outcome.answer.contains("Anna spielt Cello."))
        ContinuumBrainBridge.forget("conv-a")
        val forgotten=ContinuumBrainBridge.decide(dir,core,"conv-a","Was spielt sie?",none)
        assertEquals("forgotten conversation must have no referent",ContinuumBrainStatus.ON_KEPT_BASELINE,forgotten.status)
        assertSame(none,forgotten.outcome)
        assertFalse(forgotten.outcome.answer.contains("Anna spielt Cello."))
    }

    @Test fun anyErrorKeepsTodaysOutcome() {
        val (dir,core)=app(*family)
        val today=ContinuumChatOutcome("heute","e","t",true,ContinuumBaselineSource.MEMORY)
        ContinuumBrainSettings(dir).set(ContinuumBrainMode.ON)
        val failed=ContinuumBrainBridge.decide(dir,core,"not a valid id!","Wo wohnt Anna?",today)
        assertEquals(ContinuumBrainStatus.FALLBACK_ERROR,failed.status)
        assertSame(today,failed.outcome)
        ContinuumBrainSettings(dir).set(ContinuumBrainMode.SHADOW)
        assertSame(today,ContinuumBrainBridge.decide(dir,core,"not a valid id!","Wo wohnt Anna?",today).outcome)
        assertTrue(ContinuumBrainShadowLog(dir).lines().single().contains("\"agree\":\"ERROR\""))
    }

    @Test fun settingsAreOffUnlessTheOwnerSetAValidMode() {
        val (dir,_)=app()
        val settings=ContinuumBrainSettings(dir)
        assertEquals(ContinuumBrainMode.OFF,settings.mode())
        settings.set(ContinuumBrainMode.ON)
        assertEquals(ContinuumBrainMode.ON,settings.mode())
        File(dir,"learning-core-v2/ewm-brain/mode.properties").writeText("mode=TURBO\n")
        assertEquals(ContinuumBrainMode.OFF,settings.mode())
        File(dir,"learning-core-v2/ewm-brain/mode.properties").writeBytes(ByteArray(4096) { 'x'.code.toByte() })
        assertEquals(ContinuumBrainMode.OFF,settings.mode())
    }

    @Test fun ownerCommandsAreExplicit() {
        assertEquals(ContinuumBrainCommand.STATUS,ContinuumBrainCommand.parse("Gehirnmodus"))
        assertEquals(ContinuumBrainCommand.STATUS,ContinuumBrainCommand.parse("gehirnmodus status"))
        assertEquals("ON",ContinuumBrainCommand.parse("Gehirnmodus an"))
        assertEquals("ON",ContinuumBrainCommand.parse("Gehirnmodus ein!"))
        assertEquals("SHADOW",ContinuumBrainCommand.parse("Gehirn Modus schatten."))
        assertEquals("OFF",ContinuumBrainCommand.parse("  gehirnmodus aus "))
        assertNull(ContinuumBrainCommand.parse("Was ist ein Gehirn?"))
        assertNull(ContinuumBrainCommand.parse("Gehirn"))
        assertNull(ContinuumBrainCommand.parse("Gehirnmodus an und erzähl mir etwas über Linz"))
    }

    @Test fun theSourceIndexIsBoundedReadOnlyAndFollowsTheArchiveState() {
        val (dir,_)=app()
        val archive=ContinuumKnowledgeArchive(dir)
        val empty=archive.indexSnapshot()
        assertTrue(empty.records.isEmpty())
        assertEquals(empty.state,archive.indexState())
        for(i in 1..5) archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,"Thema $i",
            "https://de.wikipedia.org/wiki/Thema_$i","Synthetisch · Test","Thema $i ist ein synthetischer Testeintrag. "+"x".repeat(i*500),
            collectedAt=1_760_000_000_000L)
        assertFalse(empty.state==archive.indexState())
        val bounded=archive.indexSnapshot(limit=3)
        assertEquals(3,bounded.records.size)
        assertTrue(archive.indexSnapshot().records.all { it.text.length<=ContinuumKnowledgeArchive.INDEX_TEXT_CHARS })
        assertEquals(5,archive.stats().getValue(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT))
        assertEquals(archive.indexState(),archive.indexSnapshot().state)
    }

    @Test fun theDisplayedEvidenceLineIsTodaysLineByteForByte() {
        val outcome=ContinuumChatOutcome("a","CONTINUUM · Modell abc · 1 aktuelle Erinnerungen","SWB1=abc · Fakten=1",true,
            ContinuumBaselineSource.MEMORY)
        assertEquals("CONTINUUM · Modell abc · 1 aktuelle Erinnerungen"+" · "+"SWB1=abc · Fakten=1",outcome.evidenceLine)
    }
}
