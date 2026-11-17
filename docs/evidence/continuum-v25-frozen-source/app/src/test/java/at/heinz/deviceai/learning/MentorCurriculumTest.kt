package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class MentorCurriculumTest {
    @Test fun coversThirtyIndependentTopicAreas() {
        val all=MentorCurriculum.topics
        assertEquals(30,all.size)
        assertEquals(30,all.map { it.area }.toSet().size)
        assertEquals(all,(0 until 30).map { MentorCurriculum.select(it.toLong()) })
        assertEquals(all.first(),MentorCurriculum.select(30))
        assertEquals(all.last(),MentorCurriculum.select(59))
    }
    @Test fun onlyPublicResearchQueriesAreInFixedCurriculum() {
        MentorCurriculum.topics.forEach {
            assertTrue(it.query.length in 3..120)
            assertFalse(it.query.contains("https://"))
        }
        assertThrows(IllegalArgumentException::class.java) { MentorCurriculum.select(-1) }
    }
}
