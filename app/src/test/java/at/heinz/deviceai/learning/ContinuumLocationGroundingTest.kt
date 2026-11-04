package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ContinuumLocationGroundingTest {
    private fun fact(text:String,source:Boolean=false)=LearningExperienceFactory.create(
        if(source)LearningExperienceType.SOURCE_TEXT else LearningExperienceType.USER_FACT,
        if(source)LearningProvenance.REVIEWED_WEB_SOURCE else LearningProvenance.USER_CONFIRMED,
        if(source)LearningTrustClass.SOURCE_OBSERVATION else LearningTrustClass.OWNER_CONFIRMED,
        if(source)LearningStatus.OBSERVED else LearningStatus.CONFIRMED,
        text,sourceRef=if(source)"https://example.test/research" else null,now=1)

    @Test fun mirroredLocationCasesSelectOnlySupportedSubjectRelationsWithoutMutation() {
        val bytes=TrainableSubwordEncoder().save()
        val before=bytes.copyOf()
        val lines=javaClass.getResourceAsStream("/continuum-location-grounding.tsv")!!
            .bufferedReader(Charsets.UTF_8).use { it.readLines() }.filter { !it.startsWith("#") && it.isNotBlank() }
        for(line in lines) {
            val (id,query,text,ownerExpected,sourceExpected)=line.split('\t')
            for(source in listOf(false,true)) {
                val record=fact(text,source)
                val expected=(if(source)sourceExpected else ownerExpected).toBoolean()
                val hits=ContinuumMemoryDialogue.rank(bytes,query,listOf(record))
                assertEquals("$id source=$source",if(expected)listOf(record.id) else emptyList<String>(),hits.map { it.id })
                if(source)assertEquals(id,expected,ContinuumMemoryDialogue.sourceTermEvidence(query,text))
                assertEquals(text,record.content)
            }
        }
        assertArrayEquals(before,bytes)
    }

    @Test fun rejectedSubjectsAndRelationsCannotReachCre1OrKeepOldTopicAndRevocationWins() {
        val dir=Files.createTempDirectory("continuum-location-grounding").toFile()
        try {
            val bytes=TrainableSubwordEncoder().save()
            val before=bytes.copyOf()
            val record=fact("Leo wohnt in Graz.")
            val dialogue=ContinuumMemoryDialogue(dir)
            for((i,query)in listOf("Wo wohnt Nemo?","Wo arbeitet Leo?").withIndex()) {
                assertTrue(dialogue.replyInConversation(bytes,listOf(record),"Wo wohnt Leo?",i*3L+1,"location-test").grounded)
                var calls=0
                val rejected=dialogue.replyInConversation(bytes,listOf(record),query,i*3L+2,"location-test") { _,hits -> calls++;hits }
                assertFalse(query,rejected.grounded)
                assertTrue(rejected.memoryIds.isEmpty())
                assertEquals("",rejected.topic)
                assertEquals(0,calls)
                assertFalse(dialogue.replyInConversation(bytes,listOf(record),"Und wie?",i*3L+3,"location-test").grounded)
            }
            val revoked=record.copy(status=LearningStatus.REVOKED)
            assertFalse(dialogue.replyInConversation(bytes,listOf(revoked),"Wo wohnt Leo?",10,"location-test").grounded)
            assertEquals(LearningStatus.CONFIRMED,record.status)
            assertArrayEquals(before,bytes)
        } finally { dir.deleteRecursively() }
    }
}
