package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class OwnerComputerTaskGrantTest {
    private var now = 1_000L
    private val pkg = "com.whatsapp"
    private val task = "owner-task"
    private val epoch = 47L
    private val bounds = LiveTargetBounds(.1f, .1f, .8f, .2f)

    private fun search() = OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.SEARCH, "Anna")
    private fun draft(goal: ComputerGoal = ComputerGoal.DRAFT_MESSAGE) =
        OwnerComputerIntent(ComputerApp.WHATSAPP, goal, "Anna", "Ich bin in 20 Minuten da.")
    private fun grant(intent: OwnerComputerIntent = search()) = checkNotNull(
        OwnerComputerTaskGrant.fromOwnerDecision(task, pkg, epoch, intent, true) { now })

    private fun node(label: String, editable: Boolean = false, clickable: Boolean = false,
                     scrollable: Boolean = false, text: String? = null, heading: Boolean = false,
                     viewId: String? = null) = ActionNode(label, viewId, true, true, clickable,
        scrollable, editable, false, bounds, text?.let(::liveSha256), heading)
    private fun frame(vararg nodes: ActionNode, revision: Long = 1) = NodeActionFrame(
        ObservationStamp(task, epoch, revision, pkg, 4, "tree-$revision", revision * 1_000_000),
        true, true, nodes.toList())
    private fun request(step: String = "input-1", revision: Long = 1,
                        kind: NodeActionKind = NodeActionKind.SET_TEXT,
                        selector: NodeSelector = NodeSelector(label = "Suchen"), text: String? = "Anna") =
        NodeActionRequest(task, step, pkg, epoch, revision, kind, selector, text)

    private fun denied(action: () -> Unit) {
        assertThrows(IllegalStateException::class.java) { action() }
    }

    @Test fun declineCannotMintAnInputGrant() {
        assertNull(OwnerComputerTaskGrant.fromOwnerDecision(task, pkg, epoch, search(), false) { now })
        assertNull(OwnerComputerTaskGrant.fromOwnerDecision(task, pkg, 0, search(), true) { now })
        grant().checkActive()
    }

    @Test fun sourceTaskPackageAndEpochMustMatchOwnerApproval() {
        val source = frame(node("Suchen", editable = true))
        val foreign = listOf(source.stamp.copy(taskId = "other-task"),
            source.stamp.copy(packageName = "com.other.app"), source.stamp.copy(epoch = epoch + 1))
        for (stamp in foreign) denied { grant().authorize(request(), source.copy(stamp = stamp)) }
        grant().authorize(request(), source)
    }

    @Test fun requestCannotBorrowAnotherTaskOrObservation() {
        val source = frame(node("Suchen", editable = true))
        val foreign = listOf(request().copy(taskId = "other-task"), request().copy(packageName = "com.other.app"),
            request().copy(epoch = epoch + 1), request().copy(revision = 2))
        for (call in foreign) denied { grant().authorize(call, source) }
    }

    @Test fun incompleteUnsafeOrAmbiguousSourceCannotAuthorizeInput() {
        val source = frame(node("Suchen", editable = true))
        denied { grant().authorize(request(), source.copy(safeWindows = false)) }
        denied { grant().authorize(request(), source.copy(complete = false)) }
        denied { grant().authorize(request(), source.copy(nodes = source.nodes + source.nodes)) }
        denied { grant().authorize(request(), source.copy(nodes = listOf(source.nodes.single().copy(password = true)))) }
        denied { grant().authorize(request(), source.copy(nodes = listOf(source.nodes.single().copy(enabled = false)))) }
    }

    @Test fun openingAnAppGrantsNoTextOrSearchClick() {
        val permission = grant(OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.OPEN))
        denied { permission.authorize(request(), frame(node("Suchen", editable = true))) }
        denied { permission.authorizeClick(frame(node("Suchen", clickable = true)), "Suchen", "Suchen", true, false) }
    }

    @Test fun onlyExactQueryInRealSearchRoleIsAllowed() {
        denied { grant().authorize(request(text = "Hanna"), frame(node("Suchen", editable = true))) }
        denied { grant().authorize(request(selector = NodeSelector(label = "Nachricht")), frame(node("Nachricht", editable = true))) }
        denied { grant().authorize(request(), frame(node("Suchen"))) }
        grant().authorize(request(), frame(node("Suchen", editable = true)))
    }

    @Test fun draftRequiresExactTextAndActualRecipientHeading() {
        val intent = draft()
        val compose = node("Nachricht", editable = true, text = "")
        val call = request(selector = NodeSelector(label = "Nachricht"), text = intent.draft)
        denied { grant(intent).authorize(call, frame(compose, node("Hanna", heading = true))) }
        denied { grant(intent).authorize(call, frame(compose, node("Anna"))) }
        denied { grant(intent).authorize(call.copy(text = "Unbeauftragter anderer Text"), frame(compose, node("Anna", heading = true))) }
        grant(intent).authorize(call, frame(compose, node("Anna", heading = true)))
        grant(intent).authorize(call, frame(compose, node("Anna", viewId = "$pkg:id/conversation_contact_name")))
    }

    @Test fun duplicateRecipientHeadingsDoNotSelectOneSilently() {
        val intent = draft()
        denied { grant(intent).authorize(request(selector = NodeSelector(label = "Nachricht"), text = intent.draft),
            frame(node("Nachricht", editable = true, text = ""), node("Anna", heading = true), node("Anna", heading = true))) }
    }

    @Test fun differentOrUnobservableExistingDraftCannotBeOverwritten() {
        val intent = draft()
        val call = request(selector = NodeSelector(label = "Nachricht"), text = intent.draft)
        for (existing in listOf("Ein vorhandener privater Entwurf", null)) {
            denied { grant(intent).authorize(call, frame(node("Nachricht", editable = true, text = existing), node("Anna", heading = true))) }
        }
    }

    @Test fun sameStepAndSameQueryCannotBeDispatchedTwice() {
        val permission = grant(draft())
        permission.authorize(request(), frame(node("Suchen", editable = true)))
        denied { permission.authorize(request(), frame(node("Suchen", editable = true))) }
        denied { permission.authorize(request(step = "input-2", revision = 2), frame(node("Suchen", editable = true), revision = 2)) }
        val after = frame(node("Suchen", editable = true, text = "Anna"), node("Kontakte", scrollable = true), revision = 2)
        denied { permission.authorize(request(revision = 2, kind = NodeActionKind.SCROLL_FORWARD,
            selector = NodeSelector(singleScrollable = true), text = null), after) }
        permission.authorize(request(step = "scroll-1", revision = 2, kind = NodeActionKind.SCROLL_FORWARD,
            selector = NodeSelector(singleScrollable = true), text = null), after)
    }

    @Test fun scrollIsBoundedAndRequiresOwnQuery() {
        val permission = grant(draft())
        val selector = NodeSelector(singleScrollable = true)
        denied { grant(draft()).authorize(request(kind = NodeActionKind.SCROLL_FORWARD, selector = selector, text = null),
            frame(node("Suchen", editable = true, text = "Hanna"), node("Kontakte", scrollable = true))) }
        for (i in 1..OwnerComputerWorkflow.MAX_CONTACT_SCROLLS) {
            permission.authorize(request(step = "scroll-$i", revision = i.toLong(), kind = NodeActionKind.SCROLL_FORWARD,
                selector = selector, text = null), frame(node("Suchen", editable = true, text = "Anna"),
                node("Kontakte", scrollable = true), revision = i.toLong()))
        }
        denied { permission.authorize(request(step = "scroll-over-limit", revision = 4, kind = NodeActionKind.SCROLL_FORWARD,
            selector = selector, text = null), frame(node("Suchen", editable = true, text = "Anna"),
            node("Kontakte", scrollable = true), revision = 4)) }
    }

    @Test fun contactChoiceRequiresOwnSearchAndCannotRepeat() {
        val permission = grant(draft())
        val searched = frame(node("Suchen", editable = true, text = "Anna"), node("Anna", clickable = true))
        denied { permission.authorizeClick(frame(node("Anna", clickable = true)), "Anna", "Nachricht", true, false) }
        denied { permission.authorizeClick(searched, "Anna", "Senden", true, false) }
        permission.authorizeClick(searched, "Anna", "Nachricht", true, false)
        denied { permission.authorizeClick(searched, "Anna", "Nachricht", true, false) }
        denied { permission.authorize(request(step = "scroll-after-choice", kind = NodeActionKind.SCROLL_FORWARD,
            selector = NodeSelector(singleScrollable = true), text = null), searched.copy(nodes = searched.nodes + node("Kontakte", scrollable = true))) }
    }

    @Test fun composerRoleStaysBoundToExactContactAndDraft() {
        val permission = grant(draft())
        val searched = frame(node("Search...", editable = true, text = "Anna"), node("Anna", clickable = true))
        denied { grant().authorizeClick(searched, "Anna", "", true, false, expectedComposeField = true) }
        denied { permission.authorizeClick(searched, "Anna", "", true, false, true, true) }
        denied { permission.authorizeClick(searched, "Anna", "Message", true, false, expectedComposeField = true) }
        denied { permission.authorizeClick(searched.copy(nodes = searched.nodes + node("Message", editable = true)),
            "Anna", "", true, false, expectedComposeField = true) }
        permission.authorizeClick(searched, "Anna", "", true, false, expectedComposeField = true)
        denied { permission.authorizeClick(searched, "Anna", "", true, false, expectedComposeField = true) }
        val compose = frame(node("Message", editable = true, text = ""), node("Anna", heading = true), revision = 2)
        val call = request(revision = 2, selector = NodeSelector(label = "Message"), text = draft().draft)
        denied { permission.authorize(call.copy(text = "Other text"), compose) }
        permission.authorize(call, compose)
    }

    @Test fun searchClickRequiresNewEditableRole() {
        val permission = grant()
        val source = frame(node("Suchen", clickable = true))
        denied { permission.authorizeClick(source, "Suchen", "Suchen", false, false) }
        denied { permission.authorizeClick(source, "Suchen", "Suchen", true, true) }
        permission.authorizeClick(source, "Suchen", "Suchen", true, false)
        denied { permission.authorizeClick(source, "Suchen", "Suchen", true, false) }
    }

    @Test fun semanticSearchApprovalStillBindsOneClickAndExactQuery() {
        val permission = grant()
        val source = frame(node("Suchen", clickable = true))
        denied { permission.authorizeClick(source, "Suchen", "Nachricht", true, false, true) }
        denied { permission.authorizeClick(source, "Suchen", "", false, false, true) }
        denied { permission.authorizeClick(source, "Suchen", "", true, true, true) }
        permission.authorizeClick(source, "Suchen", "", true, false, true)
        denied { permission.authorizeClick(source, "Suchen", "", true, false, true) }
        val field = node("Fragen oder suchen", editable = true, viewId = "$pkg:id/search_src_text")
        val selector = NodeSelector(viewId = field.viewId)
        denied { permission.authorize(request(revision = 2, selector = selector, text = "Hanna"), frame(field, revision = 2)) }
        permission.authorize(request(revision = 2, selector = selector), frame(field, revision = 2))
        denied { permission.authorize(request(step = "again", revision = 3, selector = selector), frame(field, revision = 3)) }
    }

    @Test fun searchRoleDoesNotBroadenContactFolderOrOpeningApproval() {
        val searched = frame(node("Suchen", editable = true, text = "Anna"), node("Anna", clickable = true))
        denied { grant(draft()).authorizeClick(searched, "Anna", "", true, false, true) }
        denied { grant(draft()).authorizeClick(searched, "Anna", "Nachricht", true, false, true) }
        val files = OwnerComputerIntent(ComputerApp.FILES, ComputerGoal.OPEN_DOWNLOADS)
        denied { grant(files).authorizeClick(frame(node("Downloads", clickable = true)), "Downloads", "", true, false, true) }
        denied { grant(files).authorizeClick(frame(node("Downloads", clickable = true)), "Downloads", "Downloads", false, true, true) }
        val opening = OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.OPEN)
        denied { grant(opening).authorizeClick(frame(node("Suchen", clickable = true)), "Suchen", "", true, false, true) }
    }

    @Test fun existingOrAmbiguousSearchRoleCannotBeAuthorizedAsNew() {
        val field = node("Fragen oder suchen", editable = true, viewId = "$pkg:id/search_src_text")
        denied { grant().authorizeClick(frame(node("Suchen", clickable = true), field), "Suchen", "", true, false, true) }
        denied { grant().authorize(request(selector = NodeSelector(viewId = field.viewId)),
            frame(field, node("Suche", editable = true))) }
        denied { grant().authorize(request(selector = NodeSelector(viewId = field.viewId)),
            frame(field.copy(editable = false))) }
    }

    @Test fun downloadsRequireMatchingNewFolderHeading() {
        val permission = grant(OwnerComputerIntent(ComputerApp.FILES, ComputerGoal.OPEN_DOWNLOADS))
        val source = frame(node("Downloads", clickable = true))
        denied { permission.authorizeClick(source, "Downloads", "Downloads", true, false) }
        denied { permission.authorizeClick(source, "Downloads", "Anderer Ordner", false, true) }
        permission.authorizeClick(source, "Downloads", "Downloads", false, true)
        denied { permission.authorizeClick(source, "Downloads", "Downloads", false, true) }
    }

    @Test fun arbitraryClickSendPostAndLikeStayOutsideGrant() {
        val permission = grant(draft(ComputerGoal.SEND_MESSAGE))
        for (label in listOf("Senden", "Posten", "Gefällt mir", "Anrufen", "Kaufen")) {
            denied { permission.authorizeClick(frame(node(label, clickable = true)), label, "Nachricht", true, false) }
        }
        denied { permission.authorize(request(kind = NodeActionKind.SEND, selector = NodeSelector(label = "Senden"), text = draft().draft),
            frame(node("Senden", clickable = true))) }
        denied { permission.authorize(request(kind = NodeActionKind.CLICK, selector = NodeSelector(label = "Anna"), text = null),
            frame(node("Anna", clickable = true))) }
        denied { permission.authorize(request(kind = NodeActionKind.SCROLL_BACKWARD, selector = NodeSelector(singleScrollable = true), text = null),
            frame(node("Kontakte", scrollable = true))) }
    }

    @Test fun deceptiveSearchLabelCannotHideSendButtonId() {
        denied { grant().authorizeClick(frame(node("Suchen", clickable = true, viewId = "$pkg:id/send_button")),
            "Suchen", "Suchen", true, false) }
    }

    @Test fun revokeAndExpiryBlockFurtherInput() {
        val permission = grant()
        permission.revoke()
        denied { permission.checkActive() }
        denied { permission.authorize(request(), frame(node("Suchen", editable = true))) }
        val expiring = grant()
        now += 179_999
        expiring.checkActive()
        now++
        denied { expiring.checkActive() }
        denied { expiring.authorizeClick(frame(node("Suchen", clickable = true)), "Suchen", "Suchen", true, false) }
    }
}
