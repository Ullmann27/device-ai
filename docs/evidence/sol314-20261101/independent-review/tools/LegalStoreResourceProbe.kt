package at.heinz.deviceai.learning

import java.nio.file.Files

private fun p95(values:List<Double>)=values.sorted()[(kotlin.math.ceil(values.size*.95).toInt()-1).coerceAtLeast(0)]
fun main() {
    val bytes=TrainableSubwordEncoder(seed=73).save()
    for(size in listOf(8,64,256))for(longText in listOf(false,true)) {
        val facts=(1..size).map { i ->
            val intro="Person$i wohnt in Graz. "
            val filler=if(longText)"Die Notiz enthält unverbindliche zusätzliche Hintergrundinformationen. ".repeat(31).take(1600) else ""
            LearningExperienceFactory.create(LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,
                LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,intro+filler,now=i.toLong())
        }
        val directory=Files.createTempDirectory("owner-role-legal-resource-").toFile()
        val store=FileLearningExperienceStore(directory)
        check(store.appendMissing(facts)==facts.size)
        check(store.all()==facts)
        val ledgerBytes=directory.resolve("experiences-v2.bin").length()
        check(ledgerBytes<=512*1024)
        directory.deleteRecursively()
        val target=(size+1)/2
        val query="Wo wohnt Person$target?"
        repeat(2) { ContinuumMemoryDialogue.rank(bytes,query,facts) }
        val ms=(1..7).map {
            val started=System.nanoTime()
            val hits=ContinuumMemoryDialogue.rank(bytes,query,facts)
            check(hits.all { hit -> facts.any { it.id==hit.id } })
            (System.nanoTime()-started)/1_000_000.0
        }
        println("records=$size longText=$longText contentBytes=${facts.sumOf { it.content.toByteArray().size }} ledgerBytes=$ledgerBytes p50Ms=${ms.sorted()[ms.size/2]} p95Ms=${p95(ms)}")
    }
}
