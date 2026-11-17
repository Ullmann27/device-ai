package at.heinz.deviceai.cognitive

internal fun admitPilot(workspace: HypothesisWorkspace, candidates: List<HypothesisWorkspace.Hypothesis>) {
    candidates.forEach { check(workspace.add(it)) }
}
