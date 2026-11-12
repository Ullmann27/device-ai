package at.heinz.deviceai.cognitive

/** Ephemeral, exact-plan owner approval. */
internal class OwnerComputerTaskGrant private constructor(
    val taskId: String,
    val packageName: String,
    val epoch: Long,
    private val intent: OwnerComputerIntent,
    private val expiresAt: Long,
    private val clock: () -> Long
) {
    @Volatile private var revoked = false
    private var searchClicked = false
    private var destinationClicked = false
    private var queryEntered = false
    private var draftEntered = false
    private var scrolls = 0
    private val steps = HashSet<String>()

    fun revoke() { revoked = true }

    fun checkActive() {
        check(!revoked && clock() < expiresAt && !Thread.currentThread().isInterrupted) {
            "COMPUTER_APPROVAL_EXPIRED_OR_REVOKED"
        }
    }

    private fun checkFrame(frame: NodeActionFrame) {
        checkActive()
        check(frame.stamp.taskId == taskId && frame.stamp.packageName == packageName &&
            frame.stamp.epoch == epoch && frame.safeWindows && frame.complete) { "APPROVAL_SOURCE_MISMATCH" }
    }

    @Synchronized fun authorize(request: NodeActionRequest, frame: NodeActionFrame) {
        checkFrame(frame)
        check(request.taskId == taskId && request.packageName == packageName && request.epoch == epoch &&
            request.revision == frame.stamp.revision && request.stepId !in steps) { "APPROVAL_REQUEST_MISMATCH" }
        val target = frame.nodes.filter { it.visible && request.selector.matches(it) }.singleOrNull()
            ?: error("APPROVAL_TARGET_AMBIGUOUS")
        check(target.enabled && !target.password && !SendActionGuard.isSendLike(target.label, target.viewId)) {
            "APPROVAL_TARGET_UNSAFE"
        }
        when (request.kind) {
            NodeActionKind.SET_TEXT -> {
                check(target.editable) { "APPROVAL_FIELD_REQUIRED" }
                when {
                    request.text == intent.query && isSearch(target) && !queryEntered -> {
                        check(intent.goal in SEARCH_GOALS) { "SEARCH_NOT_APPROVED" }
                        queryEntered = true
                    }
                    request.text == intent.draft && isCompose(target) && !draftEntered -> {
                        check(intent.goal in DRAFT_GOALS && intent.app == ComputerApp.WHATSAPP &&
                            frame.nodes.count { it.visible && !it.editable && it.label.equals(intent.query, true) &&
                                (it.heading || it.viewId?.endsWith("/conversation_contact_name") == true) } == 1) {
                            "DRAFT_RECIPIENT_NOT_CONFIRMED"
                        }
                        check(target.textDigest == liveSha256("")) { "EXISTING_DRAFT_REQUIRES_OWNER" }
                        draftEntered = true
                    }
                    else -> error("TEXT_OUTSIDE_APPROVED_PLAN")
                }
            }
            NodeActionKind.SCROLL_FORWARD -> {
                check(intent.goal in DRAFT_GOALS && scrolls < OwnerComputerWorkflow.MAX_CONTACT_SCROLLS &&
                    request.selector.singleScrollable && target.scrollable && !destinationClicked &&
                    searchContainsQuery(frame)) { "SCROLL_OUTSIDE_APPROVED_PLAN" }
                scrolls++
            }
            else -> error("ACTION_OUTSIDE_APPROVED_PLAN")
        }
        steps += request.stepId
    }

    @Synchronized fun authorizeClick(frame: NodeActionFrame, label: String, expectedLabel: String,
                                     expectedEditable: Boolean, expectedHeading: Boolean) {
        checkFrame(frame)
        val target = frame.nodes.filter { it.visible && it.label == label }.singleOrNull()
            ?: error("APPROVAL_CLICK_AMBIGUOUS")
        check(target.clickable && target.enabled && !target.password &&
            !SendActionGuard.isSendLike(target.label, target.viewId)) { "APPROVAL_CLICK_UNSAFE" }
        when {
            intent.goal in SEARCH_GOALS && isSearch(target) && !searchClicked && !destinationClicked -> {
                check(expectedEditable && !expectedHeading && expectedLabel.lowercase() in SEARCH_LABELS) {
                    "SEARCH_POSTCONDITION_MISMATCH"
                }
                searchClicked = true
            }
            intent.goal in DRAFT_GOALS && label.equals(intent.query, true) && !destinationClicked -> {
                check(expectedEditable && !expectedHeading && expectedLabel.lowercase() in COMPOSE_LABELS &&
                    searchContainsQuery(frame)) { "CONTACT_POSTCONDITION_MISMATCH" }
                destinationClicked = true
            }
            intent.goal == ComputerGoal.OPEN_DOWNLOADS && label.lowercase() in DOWNLOAD_LABELS && !destinationClicked -> {
                check(expectedHeading && !expectedEditable && expectedLabel == label) { "FOLDER_POSTCONDITION_MISMATCH" }
                destinationClicked = true
            }
            else -> error("CLICK_OUTSIDE_APPROVED_PLAN")
        }
    }

    private fun searchContainsQuery(frame: NodeActionFrame): Boolean = intent.query != null &&
        frame.nodes.count { it.visible && it.editable && isSearch(it) &&
            it.textDigest == liveSha256(intent.query) } == 1

    companion object {
        private val SEARCH_GOALS = setOf(ComputerGoal.SEARCH, ComputerGoal.DRAFT_MESSAGE, ComputerGoal.SEND_MESSAGE)
        private val DRAFT_GOALS = setOf(ComputerGoal.DRAFT_MESSAGE, ComputerGoal.SEND_MESSAGE)
        private val SEARCH_LABELS = setOf("suchen", "suche", "search", "suchen …", "search…", "search...")
        private val COMPOSE_LABELS = setOf("nachricht", "message")
        private val DOWNLOAD_LABELS = setOf("download", "downloads", "heruntergeladen")
        private val SEARCH_IDS = setOf("search", "search_src_text", "search_input", "search_text", "search_bar",
            "menuitem_search", "menu_search")
        private fun isSearch(node: ActionNode) = node.label.lowercase() in SEARCH_LABELS ||
            node.viewId?.substringAfterLast('/')?.lowercase() in SEARCH_IDS
        private fun isCompose(node: ActionNode) = node.label.lowercase() in COMPOSE_LABELS

        fun fromOwnerDecision(taskId: String, packageName: String, epoch: Long, intent: OwnerComputerIntent,
                              approved: Boolean, clock: () -> Long): OwnerComputerTaskGrant? {
            if (!approved || epoch <= 0) return null
            return OwnerComputerTaskGrant(taskId, packageName, epoch, intent, clock() + 180_000, clock)
        }
    }
}
