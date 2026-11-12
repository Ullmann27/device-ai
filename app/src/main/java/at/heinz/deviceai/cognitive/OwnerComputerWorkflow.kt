package at.heinz.deviceai.cognitive

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** One owner turn, no background queue. */
internal class OwnerComputerWorkflow(
    private val taskId: String,
    private val packageName: String,
    private val observationEpoch: Long,
    private val events: CockpitWorkEmitter
) {
    interface Port {
        fun checkActive()
        fun observe(): NodeActionFrame
        fun perform(request: NodeActionRequest, events: LiveActionEventSink): NodeActionResult
        fun click(before: NodeActionFrame, label: String, expectedLabel: String,
                  expectedEditable: Boolean = true, expectedHeading: Boolean = false,
                  expectedSearchField: Boolean = false,
                  expectedComposeField: Boolean = false,
                  onDispatched: (LiveTargetBounds?, Long) -> Unit): NodeActionFrame
    }

    enum class Status { VERIFIED, ASK_OWNER, PAUSED, CANCELLED, FAILED }
    data class Result(val status: Status, val message: String, val draftPrepared: Boolean = false)
    private val started = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val actionEvents = LiveActionCockpitAdapter(events, observationEpoch)
    private var step = 0
    private var port: Port? = null

    fun run(intent: OwnerComputerIntent, port: Port): Result {
        if (!started.compareAndSet(false, true)) return Result(Status.PAUSED, "Dieser App-Auftrag wurde bereits bearbeitet.")
        this.port = port
        events.emit(CockpitWorkEvent.Stage.ACCEPTED)
        return try {
            checkActive()
            var frame = checked(port.observe())
            if (intent.goal == ComputerGoal.OPEN) {
                return finish("${intent.app.discoveryQuery} ist im gebundenen App-Fenster sichtbar.")
            }
            if (intent.goal == ComputerGoal.OPEN_DOWNLOADS) {
                return openDownloads(frame)
            }
            val query = intent.query?.takeIf { it.isNotBlank() }
                ?: return ask("Wonach soll ich in ${intent.app.discoveryQuery} suchen?")
            val currentSearch = searchFields(frame)
            if (currentSearch.size > 1) return ask("Ich sehe mehrere Suchfelder. Welches soll ich verwenden?")
            if (currentSearch.isEmpty()) {
                val searchButtons = frame.nodes.filter { it.visible && it.enabled && it.clickable &&
                    !it.editable && !it.password && LiveSearchSemantics.isControl(it.label, it.viewId) }
                if (searchButtons.size != 1) return pause("Die App-Suche ist nicht eindeutig bedienbar. Bitte öffne das Suchfeld in der sichtbaren App.")
                val button = searchButtons.single()
                if (button.label.isBlank() || frame.nodes.count { it.visible && it.label == button.label } != 1) {
                    return pause("Die Suchschaltfläche besitzt kein eindeutiges zugängliches Ziel.")
                }
                frame = click(frame, button.label, "", expectedSearchField = true)
            }
            val field = searchFields(frame).singleOrNull()
                ?: return pause("Ein neues eindeutiges Suchfeld wurde nicht bestätigt.")
            if (field.textDigest != liveSha256(query)) {
                frame = setText(frame, field, query)
            }
            if (intent.goal == ComputerGoal.SEARCH) {
                val hits = contactHits(frame, query)
                return when {
                    hits.size > 1 -> ask("Mehrere sichtbare Treffer heißen „$query“. Welchen meinst du?")
                    hits.size == 1 -> finish("Die Suche nach „$query“ wurde eingetragen. Ein passender Treffer ist sichtbar; es wurde nichts gesendet.")
                    else -> finish("„$query“ steht bestätigt im Suchfeld. Ein eindeutiger Treffer ist noch nicht bestätigt; die echte Suchansicht bleibt sichtbar.")
                }
            }
            if (intent.app != ComputerApp.WHATSAPP) return pause("Für diese App ist der Entwurfsablauf noch nicht bestätigt.")
            var hits = contactHits(frame, query)
            var scrolls = 0
            while (hits.isEmpty() && scrolls < MAX_CONTACT_SCROLLS) {
                val scrollable = frame.nodes.filter { it.visible && it.enabled && it.scrollable &&
                    !SendActionGuard.isSendLike(it.label, it.viewId) }
                if (scrollable.size != 1) break
                frame = perform(frame, NodeActionKind.SCROLL_FORWARD, NodeSelector(singleScrollable = true))
                scrolls++
                hits = contactHits(frame, query)
            }
            if (hits.size > 1) return ask("Mehrere sichtbare Kontakte heißen „$query“. Bitte wähle den richtigen Kontakt in der App.")
            val contact = hits.singleOrNull()
                ?: return ask("Ich konnte „$query“ nicht eindeutig bestätigen. Bitte prüfe den Empfänger in der sichtbaren App.")
            if (!contact.clickable || frame.nodes.count { it.visible && it.label == contact.label } != 1) {
                return ask("Der Kontakt ist sichtbar, aber sein anklickbares Ziel ist nicht eindeutig. Bitte wähle ihn in der App.")
            }
            frame = click(frame, contact.label, "", expectedComposeField = true)
            if (frame.nodes.count { it.visible && !it.editable && it.label.equals(query, true) &&
                    (it.heading || it.viewId?.substringAfterLast('/') == "conversation_contact_name") } != 1) {
                return pause("Der geöffnete Chatkopf bestätigt den Empfänger nicht eindeutig. Kein Text eingetragen.")
            }
            val compose = frame.nodes.filter(LiveComposeSemantics::isField).singleOrNull()
                ?: return pause("Das Nachrichtenfeld wurde nicht eindeutig bestätigt. Kein Text eingetragen.")
            val draft = intent.draft?.takeIf { it.isNotBlank() }
                ?: return ask("Welchen Text soll ich als Entwurf eintragen?")
            if (compose.textDigest == null) return pause("Der vorhandene Inhalt des Nachrichtenfeldes ist nicht prüfbar. Kein Text überschrieben.")
            if (compose.textDigest != liveSha256("") && compose.textDigest != liveSha256(draft)) {
                return ask("Im Nachrichtenfeld steht bereits ein anderer Entwurf. Soll dieser erhalten bleiben oder ersetzt werden?")
            }
            if (compose.textDigest != liveSha256(draft)) frame = setText(frame, compose, draft)
            val confirmed = frame.nodes.filter(LiveComposeSemantics::isField).singleOrNull()
                ?.textDigest == liveSha256(draft)
            if (!confirmed) return pause("Der Entwurf konnte nicht bestätigt werden. Bitte prüfe die App; ich wiederhole die Eingabe nicht.")
            if (intent.goal == ComputerGoal.SEND_MESSAGE) {
                events.emit(CockpitWorkEvent.Stage.ASK_OWNER)
                Result(Status.ASK_OWNER, "Der Entwurf an „$query“ ist bestätigt. Der bestehende Werkzeugweg erlaubt noch keinen geprüften Versand. Bitte sende ihn selbst in der App.", true)
            } else finish("Der Entwurf an „$query“ steht bestätigt im Nachrichtenfeld. Er wurde nicht gesendet.", true)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            cancelled()
        } catch (e: CancellationException) {
            cancelled()
        } catch (e: WorkflowPaused) {
            pause(e.message ?: "Die Wirkung ist unklar. Bitte prüfe die sichtbare App.")
        } catch (_: Exception) {
            events.emit(CockpitWorkEvent.Stage.ERROR)
            Result(Status.FAILED, "Der App-Auftrag wurde angehalten. Die letzte Wirkung ist nicht bestätigt; es erfolgt keine automatische Wiederholung.")
        } finally {
            this.port = null
        }
    }

    fun cancel() { cancelled.set(true); events.emit(CockpitWorkEvent.Stage.STOPPED) }

    private fun checkActive() {
        if (cancelled.get()) throw CancellationException()
        checkNotNull(port).checkActive()
    }

    private fun checked(frame: NodeActionFrame): NodeActionFrame {
        checkActive()
        if (frame.stamp.taskId != taskId || frame.stamp.packageName != packageName ||
            frame.stamp.epoch != observationEpoch || frame.stamp.revision <= 0 || frame.stamp.digest.isBlank() ||
            !frame.safeWindows || !frame.complete || frame.nodes.size > 256) {
            throw WorkflowPaused("Das gebundene App-Fenster ist nicht mehr sicher beobachtbar. Auftrag angehalten.")
        }
        return frame
    }

    private fun setText(frame: NodeActionFrame, field: ActionNode, text: String): NodeActionFrame {
        val id = field.viewId?.takeIf { value -> frame.nodes.count { it.visible && it.viewId == value } == 1 }
        val selector = if (id != null) NodeSelector(viewId = id) else {
            if (field.label.isBlank() || frame.nodes.count { it.visible && it.label == field.label } != 1) {
                throw WorkflowPaused("Das Eingabefeld ist nicht eindeutig adressierbar. Kein Text eingetragen.")
            }
            NodeSelector(label = field.label)
        }
        val after = perform(frame, NodeActionKind.SET_TEXT, selector, text)
        if (after.nodes.filter { it.visible && selector.matches(it) && it.editable && !it.password }
                .singleOrNull()?.textDigest != liveSha256(text)) {
            throw WorkflowPaused("Der Text ist in der neuesten Beobachtung nicht mehr bestätigt. Auftrag angehalten.")
        }
        return after
    }

    private fun perform(frame: NodeActionFrame, kind: NodeActionKind, selector: NodeSelector, text: String? = null): NodeActionFrame {
        val source = checked(frame)
        val stepId = "computer-${++step}"
        val request = NodeActionRequest(taskId, stepId, packageName, observationEpoch,
            source.stamp.revision, kind, selector, text)
        val result = checkNotNull(port).perform(request, actionEvents)
        if (!result.confirmed) throw WorkflowPaused("Die letzte Eingabe wurde nicht bestätigt. Bitte prüfe die App; ich wiederhole sie nicht.")
        val next = checked(checkNotNull(port).observe())
        val verified = result.after ?: throw WorkflowPaused("Die Nachprüfung fehlt. Auftrag angehalten.")
        if (next.stamp.revision < verified.revision || next.stamp.observedAtNanos < verified.observedAtNanos) {
            throw WorkflowPaused("Die App-Beobachtung ist veraltet. Auftrag angehalten.")
        }
        return next
    }

    private fun click(frame: NodeActionFrame, label: String, expectedLabel: String,
                      expectedEditable: Boolean = true, expectedHeading: Boolean = false,
                      expectedSearchField: Boolean = false, expectedComposeField: Boolean = false): NodeActionFrame {
        val source = checked(frame)
        val stepId = "computer-${++step}"
        val expectation = ClickRequest(packageName, observationEpoch, source.stamp.revision, label,
            expectedLabel, expectedEditable, expectedHeading, expectedSearchField, expectedComposeField)
        var dispatched = false
        val after = checkNotNull(port).click(source, label, expectedLabel, expectedEditable,
            expectedHeading, expectedSearchField, expectedComposeField) { target, sequence ->
            if (!dispatched) {
                dispatched = true
                events.emit(CockpitWorkEvent.Stage.DISPATCHED, stepId, CockpitWorkEvent.Action.CLICK,
                    target?.let { CockpitWorkEvent.Target(it.centerX, it.centerY) }, source.stamp.revision, sequence)
            }
        }
        checked(after)
        val expected = after.nodes.count { !it.password && expectation.matchesExpected(it) } == 1
        if (!dispatched || ActionEffectVerifier.verify(source.stamp, true, after.stamp, expected) != EffectVerdict.CONFIRMED) {
            throw WorkflowPaused("Der neue App-Zustand wurde nach dem Klick nicht bestätigt. Kein zweiter Klick.")
        }
        events.emit(CockpitWorkEvent.Stage.OBSERVED, stepId, observationRevision = after.stamp.revision,
            frameSequence = after.stamp.frameSequence)
        events.emit(CockpitWorkEvent.Stage.VERIFIED, stepId, CockpitWorkEvent.Action.CLICK,
            observationRevision = after.stamp.revision, frameSequence = after.stamp.frameSequence)
        return after
    }

    private fun openDownloads(frame: NodeActionFrame): Result {
        val names = setOf("download", "downloads", "heruntergeladen")
        val heading = frame.nodes.filter { it.visible && it.heading && it.label.lowercase() in names }
        if (heading.size == 1) return finish("Der Download-Ordner ist mit seiner Ordnerüberschrift sichtbar.")
        val rows = frame.nodes.filter { it.visible && it.enabled && it.clickable && !it.editable &&
            !it.password && it.label.lowercase() in names }
        val row = rows.singleOrNull() ?: return pause("Der Download-Ordner ist nicht eindeutig bedienbar. Bitte wähle ihn in der sichtbaren Dateien-App.")
        if (frame.nodes.count { it.visible && it.label == row.label } != 1) {
            return pause("Das Download-Ziel ist mehrdeutig. Kein Ordner geöffnet.")
        }
        val after = click(frame, row.label, row.label, expectedEditable = false, expectedHeading = true)
        if (after.nodes.count { it.visible && it.heading && it.label == row.label } != 1) {
            return pause("Die neue Ordnerüberschrift ist nicht bestätigt. Bitte prüfe die Dateien-App.")
        }
        return finish("Der Download-Ordner wurde geöffnet und seine neue Ordnerüberschrift bestätigt.")
    }

    private fun searchFields(frame: NodeActionFrame) = frame.nodes.filter(LiveSearchSemantics::isField)

    private fun contactHits(frame: NodeActionFrame, query: String) = frame.nodes.filter {
        it.visible && !it.editable && !it.password && it.label.equals(query, true)
    }

    private fun finish(message: String, draft: Boolean = false): Result {
        events.emit(CockpitWorkEvent.Stage.COMPLETED)
        return Result(Status.VERIFIED, message, draft)
    }
    private fun ask(message: String): Result {
        events.emit(CockpitWorkEvent.Stage.ASK_OWNER)
        return Result(Status.ASK_OWNER, message)
    }
    private fun pause(message: String): Result {
        events.emit(CockpitWorkEvent.Stage.PAUSED)
        return Result(Status.PAUSED, message)
    }
    private fun cancelled(): Result {
        events.emit(CockpitWorkEvent.Stage.STOPPED)
        return Result(Status.CANCELLED, "App-Auftrag gestoppt.")
    }

    private class WorkflowPaused(message: String) : IllegalStateException(message)
    companion object { const val MAX_CONTACT_SCROLLS = 3 }
}
