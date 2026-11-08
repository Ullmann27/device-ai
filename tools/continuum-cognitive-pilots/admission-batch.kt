package at.heinz.deviceai.cognitive

internal fun admitPilot(workspace: HypothesisWorkspace, candidates: List<HypothesisWorkspace.Hypothesis>) {
    check(workspace.admitPriors(candidates))
}
