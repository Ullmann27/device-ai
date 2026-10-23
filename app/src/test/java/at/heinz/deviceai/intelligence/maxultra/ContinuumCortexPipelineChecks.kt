package at.heinz.deviceai.intelligence.maxultra

import at.heinz.deviceai.intelligence.maxultra.ReviewedKnowledgeEdge.Relation as R

object ContinuumCortexPipelineChecks {
    @JvmStatic fun main(args: Array<String>) {
        fun edge(id:String, subj:String, relation:ReviewedKnowledgeEdge.Relation, target:String) =
            ReviewedKnowledgeEdge(id,subj,relation,target,ReviewedKnowledgeEdge.pin(id,subj,relation,target),true)
        val planner = EvidenceBoundedThoughtSearch(listOf(
            edge("src-1","Z77",R.LOCATED_IN,"Werkhalle-Nord"),
            edge("src-2","Werkhalle-Nord",R.CITY,"Graz"),
            edge("src-3","Graz",R.NOMINAL_GRID_FREQUENCY,"50Hz")))
        val pipeline = ContinuumCortexResearchPipeline(planner)
        val first = DoubleArray(16) { if(it==0) 1.0 else 0.0 }
        val second = DoubleArray(16) { if(it==1) 1.0 else 0.0 }
        val t1=pipeline.wakeTurn("In welcher Stadt steht Z77?", first, "Z77",EvidenceBoundedThoughtSearch.Question.CITY)
        check(t1.result == "Graz" && t1.hops == 2 && t1.updates == 0)
        val t2=pipeline.wakeTurn("Und welche Netzfrequenz gilt dort?", second, null,
            EvidenceBoundedThoughtSearch.Question.NOMINAL_GRID_FREQUENCY_AT_LOCATION)
        check(t2.subject == "Z77" && t2.result == "50Hz" && t2.hops == 3 && t2.updates == 1)
        check(t2.fastWeightNorm > 0 && t2.renderedText.contains("nicht unabhängig bestätigt"))
        val t3=pipeline.wakeTurn("Welche Kameramarke nutzt er?", first, null,null)
        check(t3.status == ContinuumCortexResearchPipeline.Status.ABSTAIN_NO_SAFE_PATH)
        for(i in 0 until 10) pipeline.wakeTurn("Und gilt das noch dort?",if(i%2==0)first else second,null,null)
        val t4=pipeline.wakeTurn("Und gilt die Netzfrequenz dort?", first,null,
            EvidenceBoundedThoughtSearch.Question.NOMINAL_GRID_FREQUENCY_AT_LOCATION)
        check(t4.subject == "Z77" && t4.result == "50Hz")
        val grid = EvidenceBoundedThoughtSearch.Query("Z77",EvidenceBoundedThoughtSearch.Question.NOMINAL_GRID_FREQUENCY_AT_LOCATION)
        check(pipeline.stageShadowHabit(grid))
        val report=pipeline.consolidateShadow(listOf(
            WakeSleepShadowHabitCore.Probe(grid,"50Hz"),
            WakeSleepShadowHabitCore.Probe(EvidenceBoundedThoughtSearch.Query("Z77",EvidenceBoundedThoughtSearch.Question.CITY),"Graz"),
            WakeSleepShadowHabitCore.Probe(EvidenceBoundedThoughtSearch.Query("other",EvidenceBoundedThoughtSearch.Question.CITY),null)))
        check(report.accepted && report.activeHabits == 1)
        println("PASS: pipeline, conversation-focus and evidence-graph shadow checks")
    }
}
