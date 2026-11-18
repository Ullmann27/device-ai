package at.heinz.deviceai.sending

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import at.heinz.deviceai.OwnerDocumentStore
import at.heinz.deviceai.ScreenObservationBus
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The standing grants and the daily ledger in app-private preferences. Written synchronously. */
internal class OwnerSendPreferences(context: Context) : OwnerSendKeyValue {
    private val preferences = context.applicationContext.getSharedPreferences(OwnerSendGrants.PREFERENCES, Context.MODE_PRIVATE)
    override fun get(key: String): String? = preferences.getString(key, null)
    override fun put(key: String, value: String?) {
        preferences.edit().apply { if (value == null) remove(key) else putString(key, value) }.commit()
    }
}

/** The device address book, read for an owner send order only (READ_CONTACTS). Nothing is cached or stored. */
internal class AndroidOwnerSendContacts(private val context: Context) : OwnerSendContactSource {
    private fun country(): String {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return listOf(telephony?.simCountryIso, telephony?.networkCountryIso, Locale.getDefault().country)
            .firstOrNull { !it.isNullOrBlank() }?.uppercase(Locale.ROOT) ?: "AT"
    }

    override fun phones(): List<OwnerContactPhone> {
        val country = country()
        val rows = ArrayList<OwnerContactPhone>()
        context.contentResolver.query(Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER,
            Phone.NORMALIZED_NUMBER, Phone.TYPE), null, null, null)?.use { cursor ->
            while (cursor.moveToNext() && rows.size < MAX_ROWS) {
                val name = cursor.getString(1)?.trim().orEmpty()
                val raw = cursor.getString(2)?.trim().orEmpty()
                if (name.isEmpty() || raw.isEmpty()) continue
                val e164 = cursor.getString(3)?.takeIf { it.startsWith("+") }
                    ?: runCatching { PhoneNumberUtils.formatNumberToE164(raw, country) }.getOrNull()
                val type = cursor.getInt(4)
                rows += OwnerContactPhone(cursor.getLong(0), name, raw, e164,
                    type == Phone.TYPE_MOBILE || type == Phone.TYPE_WORK_MOBILE)
            }
        }
        return rows
    }

    override fun mails(): List<OwnerContactMail> {
        val rows = ArrayList<OwnerContactMail>()
        context.contentResolver.query(Email.CONTENT_URI, arrayOf(Email.CONTACT_ID, Email.DISPLAY_NAME, Email.ADDRESS),
            null, null, null)?.use { cursor ->
            while (cursor.moveToNext() && rows.size < MAX_ROWS) {
                val name = cursor.getString(1)?.trim().orEmpty()
                val address = cursor.getString(2)?.trim().orEmpty()
                if (name.isNotEmpty() && address.isNotEmpty()) rows += OwnerContactMail(cursor.getLong(0), name, address)
            }
        }
        return rows
    }

    private companion object { const val MAX_ROWS = 20_000 }
}

/**
 * The phone side of [OwnerSendDevice]: prefilled chat/compose screen by intent; the app's existing accessibility
 * service ([ScreenObservationBus]) for one bounded read of the target app's window and one click on a located
 * control. No coordinates, no gestures, no text entry, no second click. What is read stays in memory for the
 * comparison and is neither stored nor logged nor passed to a model.
 */
internal class AndroidOwnerSendDevice(private val activity: Activity) : OwnerSendDevice {
    override fun installed(channel: SendChannel): Boolean =
        runCatching { activity.packageManager.getPackageInfo(channel.packageName, 0) }.isSuccess

    override fun accessibilityOn(): Boolean = ScreenObservationBus.liveAccess() != null

    override fun open(plan: SendPlan): String? {
        val intent = when (plan) {
            is SendPlan.WhatsApp -> whatsAppIntent(plan.waDigits, plan.text)
            is SendPlan.Mail -> gmailIntent(plan)
        }
        // Its own task: Continuum's task stays clean and can be brought back to the front afterwards.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        var problem: String? = "${plan.channel.label} wurde nicht rechtzeitig gestartet"
        val started = CountDownLatch(1)
        activity.runOnUiThread {
            problem = try {
                activity.startActivity(intent)
                null
            } catch (_: ActivityNotFoundException) {
                "${plan.channel.label} hat den Entwurf nicht angenommen"
            } catch (error: Exception) {
                error.message ?: error.javaClass.simpleName
            }
            started.countDown()
        }
        started.await(4, TimeUnit.SECONDS)
        return problem
    }

    /** The target app's window: the active application window when it is the target's, else its only window. */
    private fun <T> withTargetRoot(packageName: String, block: (AccessibilityNodeInfo) -> T): T? {
        val service = ScreenObservationBus.liveAccess()?.service ?: return null
        return runCatching {
            val roots = (service.windows ?: return@runCatching null)
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { window -> window.root?.takeIf { it.packageName?.toString() == packageName }?.let { window to it } }
            val root = roots.firstOrNull { it.first.isActive }?.second ?: roots.singleOrNull()?.second
            root?.let(block)
        }.getOrNull()
    }

    private fun read(root: AccessibilityNodeInfo, packageName: String,
                     onNode: (AccessibilityNodeInfo, SendNode) -> Unit = { _, _ -> }): SendScreen {
        val nodes = ArrayList<SendNode>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var complete = true
        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            if (nodes.size >= MAX_NODES) { complete = false; break }
            val described = describe(node)
            nodes += described
            onNode(node, described)
            // Invisible subtrees carry nothing the verification may use.
            if (!described.visible) continue
            if (depth >= MAX_DEPTH) { if (node.childCount > 0) complete = false; continue }
            for (index in 0 until node.childCount) node.getChild(index)?.let { queue.add(it to depth + 1) }
        }
        return SendScreen(packageName, nodes, complete)
    }

    private fun describe(node: AccessibilityNodeInfo) = SendNode(
        viewId = node.viewIdResourceName,
        className = node.className?.toString(),
        text = if (node.isPassword) "" else node.text?.toString()?.take(MAX_TEXT).orEmpty(),
        description = node.contentDescription?.toString()?.take(300).orEmpty(),
        hintShown = Build.VERSION.SDK_INT >= 26 && node.isShowingHintText,
        visible = node.isVisibleToUser,
        enabled = node.isEnabled,
        clickable = node.isClickable,
        editable = node.isEditable,
        password = node.isPassword)

    override fun screen(channel: SendChannel): SendScreen? =
        withTargetRoot(channel.packageName) { read(it, channel.packageName) }

    override fun frontPackage(): String? {
        val service = ScreenObservationBus.liveAccess()?.service ?: return null
        return runCatching {
            val apps = service.windows?.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.orEmpty()
            (apps.firstOrNull { it.isActive } ?: apps.firstOrNull { it.isFocused })?.root?.packageName?.toString()
        }.getOrNull()
    }

    override fun press(channel: SendChannel, locator: SendLocator, stillVerified: (SendScreen) -> Boolean): Boolean =
        withTargetRoot(channel.packageName) { root ->
            val hits = ArrayList<AccessibilityNodeInfo>()
            val shown = read(root, channel.packageName) { node, described -> if (locator.matches(described)) hits += node }
            // Verified and clicked on the same reading; the node must still be on screen when it is pressed.
            hits.size == 1 && stillVerified(shown) && hits.single().let {
                it.refresh() && it.isVisibleToUser && it.isEnabled && it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        } ?: false

    /** Continuum is not in front after the hand-over; the bound accessibility service may bring it back. */
    override fun returnToContinuum() {
        val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName) ?: return
        val context: Context = ScreenObservationBus.liveAccess()?.service ?: activity
        runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun pause(millis: Long) = Thread.sleep(millis)

    override fun nowMillis(): Long = SystemClock.elapsedRealtime()

    fun appVersion(channel: SendChannel): String? =
        runCatching { activity.packageManager.getPackageInfo(channel.packageName, 0).versionName }.getOrNull()

    companion object {
        const val MAX_NODES = 700
        const val MAX_DEPTH = 45
        const val MAX_TEXT = 8_200
        private const val DIAGNOSIS_FILE = "owner-send/sende-diagnose.txt"

        /** WhatsApp's documented click-to-chat link, bound to WhatsApp itself: opens the 1:1 chat with the text prefilled. */
        fun whatsAppIntent(waDigits: String, text: String): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$waDigits?text=" + Uri.encode(text)))
                .setPackage(SendChannel.WHATSAPP.packageName)

        /** The existing mail hand-over, restricted to Gmail (a selector and a package exclude each other). */
        fun gmailIntent(plan: SendPlan.Mail): Intent =
            OwnerDocumentStore.mailIntent(plan.address, plan.subject, plan.body, plan.attachment).apply {
                selector = null
                setPackage(SendChannel.GMAIL.packageName)
            }

        fun saveDiagnosis(context: Context, report: String) {
            runCatching {
                File(context.filesDir, DIAGNOSIS_FILE).apply { parentFile?.mkdirs() }.writeText(report, Charsets.UTF_8)
            }
        }

        fun lastDiagnosis(context: Context): String? =
            runCatching { File(context.filesDir, DIAGNOSIS_FILE).takeIf { it.isFile }?.readText(Charsets.UTF_8) }.getOrNull()
    }
}
