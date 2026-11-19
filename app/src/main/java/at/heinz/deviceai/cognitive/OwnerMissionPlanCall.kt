package at.heinz.deviceai.cognitive

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * The structured plan call (architecture review 19.11.2026, section D, option iii): when the owner's sentence holds
 * several orders and the app's own reading leaves some of them open that a model might map to a fixed lane order, the
 * brain the owner selected is asked ONCE for a typed plan – and the mission director carries it out, step by step,
 * through the lanes and their approvals. A model plans; code checks; the owner confirms.
 *
 * The request is text only: [SYSTEM] is a constant instruction block of its own (a byte-stable prefix, so the cloud
 * runtime can cache it separately from the dialogue's blocks), and the user message is the owner's sentence and
 * nothing else – no mail, no message, no document, no screen content, no earlier answer. No tool is declared and none
 * is called.
 *
 * The answer is data. It must be one JSON object `{"steps": [{"kind", "args", "covers"}], "unsupported": []}`:
 *  - `kind` is one of the closed list in [SYSTEM]; `args` holds only what that kind takes;
 *  - `covers` is the part of the owner's sentence the step carries out, copied word for word – a step whose `covers`
 *    does not stand in the sentence is invented and rejects the plan;
 *  - a message, a calendar check and a storage order are never worded by the model: their step IS the owner's own
 *    clause (`covers`), which the lane's own parser must read as that order;
 *  - every other step is worded here as the lane order of its kind, and then the whole plan passes exactly the checks
 *    of a text plan ([OwnerMissionModelPlan.checkOrders]): every order is one a lane parser takes, the steps fit
 *    together, nothing is named that the owner did not say, no kind of step is added that he has no word for, and no
 *    order of his sentence is left without a step;
 *  - `unsupported` names parts the model could not map. With any entry nothing runs by itself.
 * Anything else – other text around the object, an unknown kind, a missing field – rejects the plan. It is never
 * repaired.
 *
 * The on-device model keeps the numbered-line format of [OwnerMissionModelPlan.prompt]: that format is measured there,
 * every extra output token costs time and heat on the phone, and nothing in this code base shows that the small
 * model keeps a JSON schema reliably. An answer in that line format is accepted from either brain and checked by the
 * same code.
 */
internal object OwnerMissionPlanCall {
    /** Constant: never interpolate anything into it. The owner's sentence follows it as the user message. */
    const val SYSTEM: String =
        "Du planst einen Auftrag des Besitzers für die App Continuum. Du führst nichts aus und du schreibst keine " +
        "Nachrichten: Du ordnest nur die Teile seines Satzes festen Schritten zu. Antworte ausschließlich mit einem " +
        "JSON-Objekt, ohne Text davor oder danach:\n" +
        "{\"steps\": [{\"kind\": \"…\", \"args\": {…}, \"covers\": \"…\"}], \"unsupported\": [\"…\"]}\n" +
        "\"covers\" ist der Teil des Satzes, den der Schritt erledigt – wörtlich aus dem Satz kopiert. Jeder Teil des " +
        "Satzes, der etwas verlangt, gehört zu genau einem Schritt oder steht wörtlich in \"unsupported\". " +
        "Füge keinen Schritt hinzu, den der Besitzer nicht verlangt hat, und nenne nichts, was er nicht gesagt hat.\n" +
        "Erlaubte Schritte (kind → args):\n" +
        "open_pdf → {\"name\": Dateiname wie gesagt, \"open\": true wenn er öffnen oder zeigen sagt, sonst false}\n" +
        "page_summary → {\"page\": Zahl}\n" +
        "page_save → {\"page\": Zahl, \"format\": \"pdf\" oder \"word\"}\n" +
        "news → {}\n" +
        "export → {\"format\": \"pdf\" oder \"word\"}   (speichert das Ergebnis des Schritts davor)\n" +
        "mail_result → {\"to\": Adresse oder Name wie gesagt}   (schickt das Ergebnis des Schritts davor per E-Mail)\n" +
        "find_newest → {\"folder\": \"downloads\" oder \"dokumente\"}\n" +
        "open_it → {}   (öffnet die Datei oder Mail des Schritts davor)\n" +
        "mail_search → {\"from\": Absender wie gesagt oder null, \"about\": Stichwort wie gesagt oder null, " +
        "\"newest\": true für die letzte Mail, \"unread\": true für neue Mails}\n" +
        "mail_read → {}   (liest die gefundene Mail vor)\n" +
        "mail_summary → {}   (fasst die gefundenen Mails zusammen)\n" +
        "research → {\"topic\": Thema wie gesagt}\n" +
        "open_app → {\"app\": App-Name wie gesagt}\n" +
        "send_whatsapp → {}   (\"covers\" ist der ganze Satzteil mit Empfänger und Text; den Text formulierst du nie)\n" +
        "calendar → {}   (\"covers\" ist der ganze Satzteil zu den Terminen)\n" +
        "storage → {}   (\"covers\" ist der ganze Satzteil zum Speicher)\n" +
        "Passt gar nichts, antworte mit {\"steps\": [], \"unsupported\": [\"<der ganze Satz>\"]}.\n" +
        "Auftrag des Besitzers:\n"

    const val MAX_CHARS = 2_400

    /** The whole text request: the constant block, then the owner's sentence. */
    fun prompt(request: String): String = SYSTEM + request.filterNot { it.isISOControl() }.take(OwnerMissionPlanner.MAX_CHARS)

    /**
     * true = the app's own reading of [reading] leaves an order open that a model might map: several orders, nothing
     * ruled out, no lane takes the sentence whole, and every open order is one no lane knew by its words. An order
     * the app cannot carry out at all (another lane's own order, an ability it does not have) is said plainly instead
     * – no request is spent on it.
     */
    fun wants(reading: OwnerSentenceReading?): Boolean {
        reading ?: return false
        if (reading.plan != null || reading.vetoed || reading.whole != null || reading.orders.size < 2) return false
        val open = reading.open
        return open.isNotEmpty() && open.all { it.reading is OwnerClauseReading.Unknown || (it.reading is OwnerClauseReading.Step && it.dependsOnOpen) } &&
            open.any { it.reading is OwnerClauseReading.Unknown }
    }

    private val fence = Regex("^```(?:json)?\\s*|\\s*```$")
    private val kinds = setOf("open_pdf", "page_summary", "page_save", "news", "export", "mail_result", "find_newest", "open_it",
        "mail_search", "mail_read", "mail_summary", "research", "open_app", "send_whatsapp", "calendar", "storage")
    private val verbatimKinds = setOf("send_whatsapp", "calendar", "storage")

    /** The plan in [text] when it is completely valid for [request]; otherwise why it was dropped. */
    fun check(request: String, text: String?, assistantName: String? = null): OwnerMissionModelPlan.Result {
        val body = text?.trim()?.replace(fence, "")?.trim().orEmpty()
        if (body.isEmpty()) return OwnerMissionModelPlan.Result.Rejected("kein Plan erhalten")
        // A brain that answers with the numbered list is checked as such a list – by the same code.
        if (!body.startsWith("{")) return OwnerMissionModelPlan.check(request, body, assistantName)
        if (body.length > MAX_CHARS) return OwnerMissionModelPlan.Result.Rejected("der Modellplan ist zu lang")
        val plan = runCatching { JSONObject(body) }.getOrNull() ?: return OwnerMissionModelPlan.Result.Rejected("der Modellplan ist kein gültiges JSON")
        if (plan.keys().asSequence().any { it != "steps" && it != "unsupported" })
            return OwnerMissionModelPlan.Result.Rejected("der Modellplan enthält ein unbekanntes Feld")
        val steps = plan.opt("steps") as? JSONArray ?: return OwnerMissionModelPlan.Result.Rejected("der Modellplan nennt keine Schritte")
        val unsupported = when (val raw = plan.opt("unsupported")) {
            null, JSONObject.NULL -> emptyList()
            is JSONArray -> (0 until raw.length()).map { raw.opt(it) as? String ?: return OwnerMissionModelPlan.Result.Rejected("„unsupported“ ist keine Liste von Satzteilen") }
            else -> return OwnerMissionModelPlan.Result.Rejected("„unsupported“ ist keine Liste von Satzteilen")
        }
        if (steps.length() == 0) return OwnerMissionModelPlan.Result.Rejected("das Modell sieht keinen Plan")
        if (steps.length() > OwnerMissionPlanner.MAX_STEPS) return OwnerMissionModelPlan.Result.Rejected("der Modellplan hat ${steps.length()} Schritte")
        unsupported.forEach { part ->
            if (!OwnerMissionModelPlan.literal(request, part)) return OwnerMissionModelPlan.Result.Rejected("„unsupported“ nennt etwas, das du nicht gesagt hast")
        }
        val orders = ArrayList<String>()
        val verbatim = HashSet<Int>()
        val claims = HashMap<Int, String>()
        for (index in 0 until steps.length()) {
            val number = index + 1
            val step = steps.opt(index) as? JSONObject ?: return OwnerMissionModelPlan.Result.Rejected("Schritt $number ist kein Objekt")
            if (step.keys().asSequence().any { it != "kind" && it != "args" && it != "covers" })
                return OwnerMissionModelPlan.Result.Rejected("Schritt $number enthält ein unbekanntes Feld")
            val kind = step.opt("kind") as? String
            if (kind == null || kind !in kinds) return OwnerMissionModelPlan.Result.Rejected("Schritt $number hat keine erlaubte Art")
            val covers = (step.opt("covers") as? String)?.trim().orEmpty()
            if (!OwnerMissionModelPlan.literal(request, covers))
                return OwnerMissionModelPlan.Result.Rejected("Schritt $number deckt etwas ab, das nicht in deinem Satz steht")
            val args = when (val raw = step.opt("args")) {
                null, JSONObject.NULL -> JSONObject()
                is JSONObject -> raw
                else -> return OwnerMissionModelPlan.Result.Rejected("Schritt $number hat keine gültigen Angaben")
            }
            val order = if (kind in verbatimKinds) {
                if (args.length() != 0) return OwnerMissionModelPlan.Result.Rejected("Schritt $number darf keine eigenen Angaben haben")
                verbatim += index
                covers.trim().trimEnd('.', '!', '?', ',', ' ')
            } else worded(kind, args) ?: return OwnerMissionModelPlan.Result.Rejected("Schritt $number hat keine gültigen Angaben")
            // The worded order must be an order of exactly this kind: „send_whatsapp“ over a clause that sends nothing,
            // or „calendar“ over a clause about something else, is not a plan.
            val read = OwnerMissionPlanner.recogniseInSequence(order, assistantName)
            if (read == null || kindOf(read) != kind)
                return OwnerMissionModelPlan.Result.Rejected("Schritt $number ist kein Auftrag der Art, die er nennt")
            claims[index] = covers
            orders += order
        }
        val checked = OwnerMissionModelPlan.checkOrders(request, orders, assistantName, least = 2, verbatim = verbatim, claims = claims)
        if (unsupported.isEmpty() || checked !is OwnerMissionModelPlan.Result.Accepted) return checked
        // The model itself says a part is not mapped: nothing runs by itself, whatever the plan covers.
        val reading = OwnerMissionPlanner.read(request, assistantName) ?: return OwnerMissionModelPlan.Result.Rejected("der Auftrag ist zu lang für einen Plan")
        val open = reading.orders.filter { clause -> unsupported.any { OwnerMissionModelPlan.literal(it, clause.text) || OwnerMissionModelPlan.literal(clause.text, it) } }
        return if (open.isEmpty()) OwnerMissionModelPlan.Result.Rejected("das Modell kann einen Teil deines Satzes nicht zuordnen")
            else OwnerMissionModelPlan.Result.Uncovered(open, emptyList())
    }

    /** The plan kind a recognised step belongs to; a clause that only refers is told apart by its code. */
    private fun kindOf(step: OwnerMissionStep): String? = when (step) {
        is OwnerMissionStep.OpenPdf -> "open_pdf"
        is OwnerMissionStep.PageSummary -> "page_summary"
        is OwnerMissionStep.PageSave -> "page_save"
        is OwnerMissionStep.News -> "news"
        is OwnerMissionStep.Export -> "export"
        is OwnerMissionStep.Mail -> "mail_result"
        is OwnerMissionStep.FindFile -> "find_newest"
        is OwnerMissionStep.MailSearch -> "mail_search"
        is OwnerMissionStep.Research -> "research"
        is OwnerMissionStep.OpenApp -> "open_app"
        is OwnerMissionStep.SendChat -> "send_whatsapp"
        is OwnerMissionStep.Calendar -> "calendar"
        is OwnerMissionStep.Storage -> "storage"
        else -> when (step.code) {
            "OPEN_IT" -> "open_it"
            "SUM_IT" -> "mail_summary"
            "READ_IT" -> "mail_read"
            else -> null
        }
    }

    private fun text(args: JSONObject, key: String, max: Int = 120): String? = (args.opt(key) as? String)?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= max && it.none { c -> c.isISOControl() || c in "\"„“”«»:" } }

    private fun flag(args: JSONObject, key: String): Boolean = args.opt(key) == true

    private fun only(args: JSONObject, vararg keys: String): Boolean = args.keys().asSequence().all { it in keys }

    /** The lane order of [kind] with [args], worded in the one form the lane's parser reads; null = invalid arguments. */
    private fun worded(kind: String, args: JSONObject): String? = when (kind) {
        "open_pdf" -> if (!only(args, "name", "open")) null else text(args, "name")?.let { (if (flag(args, "open")) "Öffne das PDF " else "Such das PDF ") + it }
        "page_summary" -> if (!only(args, "page")) null else page(args)?.let { "Fass Seite $it zusammen" }
        "page_save" -> if (!only(args, "page", "format")) null else page(args)?.let { page ->
            format(args)?.let { "Fass Seite $page zusammen und speichere es als " + if (it == "pdf") "PDF" else "Word" } }
        "news" -> if (args.length() != 0) null else "Fass mir die Nachrichten von heute zusammen"
        "export" -> if (!only(args, "format")) null else format(args)?.let { "Speichere das als " + if (it == "pdf") "PDF" else "Word-Datei" }
        "mail_result" -> if (!only(args, "to")) null else text(args, "to")?.let { "Schick das per Mail an $it" }
        "find_newest" -> if (!only(args, "folder")) null else when ((args.opt("folder") as? String)?.lowercase(Locale.GERMAN)) {
            "downloads", "download" -> "Such das neueste PDF in Downloads"
            "dokumente", "documents" -> "Such das neueste PDF in Dokumente"
            else -> null
        }
        "open_it" -> if (args.length() != 0) null else "Öffne es"
        "mail_search" -> if (!only(args, "from", "about", "newest", "unread")) null else {
            val from = text(args, "from", 80)
            val about = text(args, "about", 80)
            when {
                args.has("from") && !args.isNull("from") && from == null -> null
                args.has("about") && !args.isNull("about") && about == null -> null
                flag(args, "newest") && from != null && about == null -> "Such die letzte Mail von $from"
                from != null && about != null -> "Suche Mails von $from zu $about"
                from != null -> "Suche Mails von $from"
                about != null -> "Suche Mails zu $about"
                flag(args, "unread") -> "Zeig mir meine neuen Mails"
                else -> "Schau meine Mails durch"
            }
        }
        "mail_read" -> if (args.length() != 0) null else "Lies sie vor"
        "mail_summary" -> if (args.length() != 0) null else "Fass sie zusammen"
        "research" -> if (!only(args, "topic")) null else text(args, "topic", 200)?.let { "Recherchiere $it" }
        "open_app" -> if (!only(args, "app")) null else text(args, "app", 40)?.let { "Öffne $it" }
        else -> null
    }

    private fun page(args: JSONObject): Int? = (args.opt("page") as? Int)?.takeIf { it in 1..9_999 }

    private fun format(args: JSONObject): String? = (args.opt("format") as? String)?.lowercase(Locale.GERMAN)?.let {
        when (it) { "pdf" -> "pdf"; "word", "docx" -> "word"; else -> null }
    }
}
