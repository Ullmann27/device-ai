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
        val archiveAnswer=archiveHit?.source?.let { ContinuumBrainBridge.archiveAnswer(it) }
        val evidence=if(archiveAnswer!=null)"QUELLENABRUF · lokal archivierter, nicht bestätigter Beleg"
            else "CONTINUUM · Modell "+turn.modelHash.take(12)+" · "+turn.memoryIds.size+" aktuelle Erinnerungen"
        return ContinuumChatOutcome(archiveAnswer ?: cognitive.answer,evidence,cognitive.trace,
            turn.grounded || archiveAnswer!=null,
            when {
                archiveAnswer!=null -> ContinuumBaselineSource.ARCHIVE
                turn.grounded -> ContinuumBaselineSource.MEMORY
                else -> ContinuumBaselineSource.NONE
            },turn.memoryIds,archiveHit?.source?.id)
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
        // revoked between the brain's decision and the display: today's outcome stays, the fact is not shown
        val today=baseline(dir,core,"conv-race","Was spielt Anna?")
        ContinuumBrainBridge.beforeRevocationCheck={ core.revokeOwnerFact("Anna spielt Cello.") }
        val raced=ContinuumBrainBridge.decide(dir,core,"conv-race","Was spielt Anna?",today)
        assertEquals(ContinuumBrainStatus.FALLBACK_REVOKED,raced.status)
        assertSame(today,raced.outcome)
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
        assertFalse(other.outcome.answer.contains("Anna spielt Cello."))
        ContinuumBrainBridge.forget("conv-a")
        val forgotten=ContinuumBrainBridge.decide(dir,core,"conv-a","Was spielt sie?",none)
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
