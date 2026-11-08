package at.heinz.deviceai.intelligence

/**
 * Pure chooser across independently preflighted owner-release sources.
 * Network fetching and APK verification remain in ContinuumOwnerUpdater.
 */
internal object ContinuumOwnerReleaseSelector {
    data class Candidate(
        val release: ContinuumOwnerUpdateRelease,
        val preflight: ContinuumUpdatePreflight
    )

    fun select(candidates: Collection<Candidate>): ContinuumOwnerUpdateRelease? =
        candidates.asSequence()
            .filter { it.preflight is ContinuumUpdatePreflight.ReadyForActualApkVerification }
            .map { it.release }
            .sortedWith(
                compareByDescending<ContinuumOwnerUpdateRelease> { it.descriptor.versionCode }
                    .thenByDescending { sourceRank(it.source) }
                    .thenBy { it.descriptor.sourceCommit }
            )
            .firstOrNull()

    private fun sourceRank(source: ContinuumOwnerUpdateSource): Int = when(source) {
        ContinuumOwnerUpdateSource.RENDER -> 2
        ContinuumOwnerUpdateSource.GITHUB_RELEASE -> 1
    }
}
