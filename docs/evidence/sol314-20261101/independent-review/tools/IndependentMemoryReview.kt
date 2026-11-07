package at.heinz.deviceai.learning

import java.nio.file.Files
import java.util.Base64

private fun FrozenFact.experience() = LearningExperienceFactory.create(
    LearningExperienceType.valueOf(type),LearningProvenance.valueOf(provenance),
    LearningTrustClass.valueOf(trust),LearningStatus.valueOf(status),text,
    sourceRef=sourceRef,now=at).copy(updatedAt=updatedAt)
private fun encoded(s:String)=Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
fun main() {
    val bytes=TrainableSubwordEncoder(seed=73L).save()
    val original=bytes.copyOf()
    var correct=0; var ranksCorrect=0; var errors=0
    val started=System.nanoTime()
    println("id\tcategory\texpected\tactual\tpass\trankActual\trankPass\telapsedUs\treplyUs\tquery64\tresolved64\tanswer64\terror64")
    for(c in frozenCases) {
        val directory=Files.createTempDirectory("owner-role-independent-${c.id}-").toFile()
        val caseStarted=System.nanoTime()
        try {
            val memories=c.facts.map { it.experience() }
            val earlier=c.seedFacts?.map { it.experience() } ?: memories
            val labels=((c.seedFacts ?: emptyList())+c.facts).associate { it.experience().id to it.label }
            c.seeds.forEachIndexed { i,seed ->
                ContinuumMemoryDialogue(directory).replyInConversation(bytes,earlier,seed.input,100L+i,seed.conversation)
            }
            val replyStarted=System.nanoTime()
            val result=ContinuumMemoryDialogue(directory).replyInConversation(bytes,memories,c.query,200L,c.conversation)
            val replyUs=(System.nanoTime()-replyStarted)/1000
            val actual=result.memoryIds.map { labels[it] ?: "UNEXPECTED:$it" }
            val ranked=ContinuumMemoryDialogue.rank(bytes,result.resolvedQuery,memories).map { labels[it.id] ?: "UNEXPECTED:${it.id}" }
            val passed=actual.sorted()==c.expected.sorted() && result.grounded==actual.isNotEmpty() && (actual.isNotEmpty() || result.answer==ContinuumMemoryDialogue.NO_EVIDENCE_ANSWER)
            val rankPassed=ranked.sorted()==c.expected.sorted()
            if(passed)correct++
            if(rankPassed)ranksCorrect++
            check(bytes.contentEquals(original)) { "Encoder bytes changed" }
            println(listOf(c.id,c.category,c.expected.sorted().joinToString(","),actual.joinToString(","),passed,ranked.joinToString(","),rankPassed,(System.nanoTime()-caseStarted)/1000,replyUs,encoded(c.query),encoded(result.resolvedQuery),encoded(result.answer),"").joinToString("\t"))
        } catch(e:Throwable) {
            errors++
            println(listOf(c.id,c.category,c.expected.sorted().joinToString(","),"ERROR",false,"ERROR",false,(System.nanoTime()-caseStarted)/1000,-1,encoded(c.query),"","",encoded(e.toString())).joinToString("\t"))
        } finally { directory.deleteRecursively() }
    }
    System.err.println("TOTAL=${frozenCases.size} correct=$correct rankCorrect=$ranksCorrect errors=$errors elapsedMs=${(System.nanoTime()-started)/1_000_000}")
}
