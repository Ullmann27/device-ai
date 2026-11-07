package at.heinz.deviceai.learning

/**
 * Source-driven exploration planner. This is NOT an LLM or an unbounded web
 * crawler. It accepts only topic labels validated by the official Wikipedia
 * title policy; URLs are constructed separately by that policy.
 *
 * The scheduler persists queue and seen state in Android SharedPreferences.
 * This pure Kotlin strategy also runs unchanged in local JVM tests.
 */
object ContinuumTopicFrontier {
    const val MAX_PENDING=128
    const val MAX_VISITED=384

    /**
     * Prefer validated discoveries on 3 of every 4 cycles while periodically
     * revisiting a curated research seed to keep independent domain coverage.
     */
    fun select(cycle:Long,pending:List<String>):String {
        require(cycle>=0)
        val next=pending.firstOrNull(ContinuumPublicStudyPolicy::validArticleTitle)
        return if(next!=null && cycle%4L!=0L) next
        else ContinuumPublicStudyPolicy.topicForCycle(cycle/4L)
    }

    fun enqueue(pending:List<String>,visited:List<String>,
                discovered:List<String>):List<String> {
        val previous=mutableSetOf<String>()
        val seen=visited.filter(ContinuumPublicStudyPolicy::validArticleTitle).toSet()
        val seeds=ContinuumPublicStudyPolicy.topics.toSet()
        return (pending+discovered).asSequence()
            .filter(ContinuumPublicStudyPolicy::validArticleTitle)
            .filter { it !in seen && it !in seeds }
            .filter { previous.add(it) }
            .take(MAX_PENDING).toList()
    }

    fun afterAttempt(pending:List<String>,visited:List<String>,
                     title:String,completed:Boolean):Pair<List<String>,List<String>> {
        require(ContinuumPublicStudyPolicy.validArticleTitle(title))
        val queue=pending.filter { it!=title }.take(MAX_PENDING)
        val previous=visited.filter(ContinuumPublicStudyPolicy::validArticleTitle)
        val history=if(completed)(previous+title).distinct().takeLast(MAX_VISITED)
            else previous.distinct().takeLast(MAX_VISITED)
        return queue to history
    }
}
