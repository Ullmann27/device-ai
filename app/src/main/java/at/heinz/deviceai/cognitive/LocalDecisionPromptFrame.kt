package at.heinz.deviceai.cognitive

/** Optional decision data cannot displace the full owner request or mandatory prior dialogue. */
internal object LocalDecisionPromptFrame {
    fun assemble(requiredBody: String, safeHistory: String, decisionDigest: String,
                 advisory: String?, requiredTail: String, maxCharacters: Int,
                 requireHistory: Boolean): String? {
        val essential = LocalPromptFrame.assemble(
            requiredBody = requiredBody, optionalHistory = safeHistory,
            advisory = advisory, requiredTail = requiredTail,
            maxCharacters = maxCharacters, requireHistory = requireHistory
        ) ?: return null
        if (!decisionDigest.startsWith(LocalDecisionPromptDigest.HEADER)) return essential
        val optional = LocalPromptFrame.assemble(
            requiredBody = requiredBody,
            optionalHistory = safeHistory + "\n" + decisionDigest,
            advisory = advisory, requiredTail = requiredTail,
            maxCharacters = maxCharacters, requireHistory = requireHistory
        )
        // An optional digest must not shorten a router advisory already present.
        val originalAdvisory = if (advisory.isNullOrBlank()) null else {
            val header = ChatReasoningRouter.ADVISORY_HEADER
            val footer = ChatReasoningRouter.ADVISORY_FOOTER
            val start = essential.lastIndexOf(header)
            val end = essential.lastIndexOf(footer)
            if (start < 0 || end < start) null
            else essential.substring(start, end + footer.length)
        }
        return optional?.takeIf {
            it.contains(decisionDigest) &&
                (originalAdvisory == null || it.endsWith(originalAdvisory + requiredTail))
        } ?: essential
    }
}
