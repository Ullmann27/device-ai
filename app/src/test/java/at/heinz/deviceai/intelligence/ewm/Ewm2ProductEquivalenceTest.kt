package at.heinz.deviceai.intelligence.ewm

import at.heinz.deviceai.learning.ContinuumBaselineSource
import at.heinz.deviceai.learning.ContinuumBrainBridge
import at.heinz.deviceai.learning.ContinuumBrainMode
import at.heinz.deviceai.learning.ContinuumBrainSettings
import at.heinz.deviceai.learning.ContinuumBrainStatus
import at.heinz.deviceai.learning.ContinuumChatOutcome
import at.heinz.deviceai.learning.ContinuumKnowledgeArchive
import at.heinz.deviceai.learning.ContinuumLearningCore
import at.heinz.deviceai.learning.ContinuumMemoryDialogue
import at.heinz.deviceai.learning.LearningExperienceFactory
import at.heinz.deviceai.learning.LearningExperienceType
import at.heinz.deviceai.learning.LearningProvenance
import at.heinz.deviceai.learning.LearningStatus
import at.heinz.deviceai.learning.LearningTrustClass
import java.io.File
import java.nio.file.Files
import java.util.Base64
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AP-1 acceptance: the product classes reproduce the research numbers of the EWM2 brain (K1) on the two DEV
 * corpora, copied byte for byte from PR #224 (DEV only; TEST4/TEST5 are not used here). The second test drives
 * the same dialogs through the production adapter (learning core + archive + ContinuumBrainBridge in ON).
 */
class Ewm2ProductEquivalenceTest {
    private fun corpus(name:String,sha:String):JSONArray {
        val bytes=javaClass.getResourceAsStream("/ewm/$name")!!.readBytes()
        assertEquals("DEV corpus must be the unchanged research file",sha,ContinuumMemoryDialogue.hash(bytes))
        return JSONArray(bytes.toString(Charsets.UTF_8))
    }

    private val dev5 get()=corpus("ewm2-dev5-20261029.json","d7f441728b5b5abaf59e4a85613650ce3965e8feb6b8774aa1aadfc5123a266a")
    private val dev5b get()=corpus("ewm2-dev5b-20261029.json","c91307e0a744374c8ef5d75e4e94eca9615131ffbd43f000e1493f2d7c692abf")

    private val swb1:ByteArray by lazy {
        Base64.getDecoder().decode(javaClass.getResourceAsStream("/continuum-neural-swb1.base64")!!.bufferedReader().readText().trim())
    }

    private fun strings(a:JSONArray)=List(a.length()) { a.getString(it) }

    private fun outcome(expected:String,gold:Set<String>,cited:List<String>,clarified:Boolean):String = when(expected) {
        "QUERY" -> when { clarified -> "UNNEEDED_CLARIFY"; cited.isEmpty() -> "MISSED"; cited.first() in gold -> "CORRECT"; else -> "WRONG" }
        "CLARIFY" -> when { clarified -> "CLARIFIED"; cited.isEmpty() -> "MISSED_CLARIFY"; else -> "WRONG" }
        else -> when { clarified -> "UNNEEDED_CLARIFY"; cited.isEmpty() -> "ABSTAINED"; else -> "WRONG" }
    }

    private fun withApp(block:(File)->Unit) {
        val dir=Files.createTempDirectory("ewm-equivalence").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }

    private fun saveSources(dir:File,dialog:org.json.JSONObject):List<ContinuumKnowledgeArchive.SourceRecord> {
        val archive=ContinuumKnowledgeArchive(dir)
        val src=dialog.optJSONArray("sources") ?: JSONArray()
        return (0 until src.length()).map { i -> val s=src.getJSONObject(i)
            archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,s.getString("title"),
                "https://de.wikipedia.org/wiki/"+java.net.URLEncoder.encode(s.getString("title").replace(' ','_'),"UTF-8"),
                "Synthetisch · EWM2-Test",s.getString("text"),collectedAt=1_760_000_000_000L) }
    }

    /** Research harness (Ewm2Experiment K1) with the product classes. */
    private fun k1Counts(corpus:JSONArray):Map<String,Int> {
        val counts=HashMap<String,Int>()
        for(d in 0 until corpus.length()) withApp { dir ->
            val dialog=corpus.getJSONObject(d)
            val exps=strings(dialog.getJSONArray("facts")).mapIndexed { i,text -> LearningExperienceFactory.create(
                LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,
                LearningStatus.CONFIRMED,text,now=i+1L) }
            val byId=exps.associate { it.id to it.content }
            val records=saveSources(dir,dialog)
            val titleById=records.associate { "SOURCE:"+it.id to "SOURCE:"+it.title }
            val brain=Ewm1Brain(exps.map { it.id to it.content },Ewm1SourceIndex(records)) { q,subset ->
                ContinuumMemoryDialogue.rank(swb1,q.take(1000),exps.filter { it.id in subset }).map { it.id } }
            val turns=dialog.getJSONArray("turns")
            for(i in 0 until turns.length()) {
                val t=turns.getJSONObject(i)
                val ans=brain.ask(t.getString("text"))
                val cited=ans.cited.map { c -> if(c.startsWith("SOURCE:"))titleById.getValue(c) else byId.getValue(c) }
                counts.merge(outcome(t.getString("expected"),strings(t.getJSONArray("gold")).toSet(),cited,
                    ans.decision=="CLARIFY"),1,Int::plus)
            }
        }
        return counts
    }

    @Test fun productClassesReproduceTheResearchNumbersOnDev5AndDev5b() {
        assertEquals(mapOf("CORRECT" to 12,"ABSTAINED" to 7),k1Counts(dev5))
        assertEquals(mapOf("CORRECT" to 20,"ABSTAINED" to 6),k1Counts(dev5b))
    }

    private val adapterMismatchDiagnostics = mutableListOf<String>()

    /** The same dialogs through the real learning core, archive and bridge (ON, baseline without evidence). */
    private fun adapterCounts(corpus:JSONArray):Map<String,Int> {
        val counts=HashMap<String,Int>()
        for(d in 0 until corpus.length()) withApp { dir ->
            val dialog=corpus.getJSONObject(d)
            val core=ContinuumLearningCore(dir)
            strings(dialog.getJSONArray("facts")).forEach { core.confirmOwnerFact(it) }
            val records=saveSources(dir,dialog)
            ContinuumBrainSettings(dir).set(ContinuumBrainMode.ON)
            val none=ContinuumChatOutcome("heute","e","t",false,ContinuumBaselineSource.NONE)
            val turns=dialog.getJSONArray("turns")
            for(i in 0 until turns.length()) {
                val t=turns.getJSONObject(i)
                val r=ContinuumBrainBridge.decide(dir,core,"equivalence-$d",t.getString("text"),none)
                val cited=when(r.status) {
                    ContinuumBrainStatus.ON_MEMORY -> r.outcome.memoryIds.map { id -> core.activeOwnerFacts().single { it.id==id }.content }
                    ContinuumBrainStatus.ON_SOURCE -> listOf("SOURCE:"+records.single { it.id==r.outcome.archiveId }.title)
                    else -> emptyList()
                }
                val expected = t.getString("expected")
                val verdict = outcome(expected, strings(t.getJSONArray("gold")).toSet(), cited,
                    r.status==ContinuumBrainStatus.ON_CLARIFY)
                if (verdict !in setOf("CORRECT", "ABSTAINED", "CLARIFIED")) {
                    // Frozen synthetic corpus only: never print owner facts or source text.
                    adapterMismatchDiagnostics.add("dialog=$d turn=$i expected=$expected verdict=$verdict status=${r.status}")
                }
                counts.merge(verdict, 1, Int::plus)
            }
        }
        return counts
    }

    @Test fun theProductionAdapterReachesTheSameDecisions() {
        assertEquals(mapOf("CORRECT" to 12,"ABSTAINED" to 7),adapterCounts(dev5))
        assertEquals("Synthetic DEV5b mismatches: ${adapterMismatchDiagnostics.joinToString("; ")}",
            mapOf("CORRECT" to 20,"ABSTAINED" to 6),adapterCounts(dev5b))
    }
}
