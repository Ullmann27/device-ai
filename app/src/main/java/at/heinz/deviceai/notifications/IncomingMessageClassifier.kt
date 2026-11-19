package at.heinz.deviceai.notifications

import java.util.Locale

/** How a notification of a chosen app is treated: a chat message or an e-mail (sender plus subject). */
enum class IncomingKind { MESSENGER, MAIL }

enum class ImportanceVerdict {
    /** A person, an institute, an authority or the owner's own rule: the owner is told. */
    ANNOUNCE,
    /** Advertising, a digest, a switched-off group or the owner's own rule: nothing is said. */
    SILENT,
    /** Neither clearly important nor clearly advertising; the owner's setting decides (see [IncomingMessageSettings.unclear]). */
    UNCLEAR,
}

/** Why a message was (not) reported. Every decision carries exactly one of these, in the order they are tested. */
enum class ImportanceReason {
    OWNER_ALWAYS, OWNER_NEVER, GROUP_CHAT, CONTACT, AUTHORITY, INSTITUTION, OFFICIAL_MATTER, PROMOTION, SOCIAL_DIGEST,
    GROUP_MESSAGE, DIRECT_MESSAGE, PERSONAL_NAME, UNCLEAR,
}

/** What the classifier looks at. All strings are UNTRUSTED notification content; they are only matched, never executed. */
data class ImportanceInput(
    val kind: IncomingKind,
    val sender: String,
    val subject: String = "",
    val text: String = "",
    /** Name of the group or thread; empty for a direct message. */
    val conversation: String = "",
    val inContacts: Boolean = false,
    val isGroup: Boolean = false,
) {
    override fun toString(): String = "ImportanceInput(kind=$kind, content=redacted)"
}

/** The owner's own lists and the group switch. */
data class ImportanceRules(
    val always: List<String> = emptyList(),
    val never: List<String> = emptyList(),
    val groups: Boolean = false,
)

/** [cue] is the word that decided (bounded), so the owner can be told why. */
data class ImportanceDecision(val verdict: ImportanceVerdict, val reason: ImportanceReason, val cue: String = "") {
    /** One German sentence for „Warum hast du das (nicht) gemeldet?“. */
    val explanation: String
        get() {
            val named = if (cue.isEmpty()) "" else " („$cue“)"
            return when (reason) {
                ImportanceReason.OWNER_ALWAYS -> "„$cue“ steht auf deiner Liste „immer melden“."
                ImportanceReason.OWNER_NEVER -> "„$cue“ steht auf deiner Liste „nie melden“."
                ImportanceReason.GROUP_CHAT -> "Es ist eine Gruppennachricht, und Gruppen sind ausgeschaltet."
                ImportanceReason.CONTACT -> "Der Absender steht in deinen Kontakten."
                ImportanceReason.AUTHORITY -> "Absender oder Betreff nennen ein Gericht oder eine Behörde$named."
                ImportanceReason.INSTITUTION ->
                    "Der Absender ist ein Institut wie Bank, Versicherung, Schule, Arzt oder Kanzlei$named."
                ImportanceReason.OFFICIAL_MATTER -> "Im Betreff oder Text steht eine amtliche oder rechtliche Sache$named."
                ImportanceReason.PROMOTION -> "Die Nachricht sieht nach Werbung aus$named."
                ImportanceReason.SOCIAL_DIGEST -> "Es ist eine Sammelmeldung eines sozialen Netzwerks$named."
                ImportanceReason.GROUP_MESSAGE -> "Es ist eine Gruppennachricht, und du hast Gruppen eingeschaltet."
                ImportanceReason.DIRECT_MESSAGE -> "Es ist eine direkte Nachricht an dich."
                ImportanceReason.PERSONAL_NAME ->
                    "Der Absender trägt einen Personennamen, und die Nachricht sieht nicht nach Werbung aus."
                ImportanceReason.UNCLEAR ->
                    "Ich konnte weder eine Person noch ein Institut noch Werbung sicher erkennen."
            }
        }
}

/**
 * „Personen, Institute, Gerichte – keine Werbung“ (owner order 18.11.2026) as fixed, ordered rules.
 *
 * Deterministic and explainable: no model, no network, no learning. The first rule that matches decides, in this order:
 *  1. the owner's list „immer melden“, 2. the owner's list „nie melden“ (a sender on both is reported: missing an
 *  important message costs more than hearing an unimportant one), 3. a group chat while groups are off,
 *  4. a sender from the contacts, 5. a court or authority as sender, 6. a court or authority in the subject of a mail
 *  that carries no clear advertising mark, 7. an institute (bank, insurer, school, doctor, lawyer …) as sender without
 *  such a mark, 8. an official matter (Bescheid, Ladung, Mahnung …) in a mail without such a mark, 9. advertising
 *  (one clear mark or two weak ones), 10. a social network's digest, 11. a group message (groups on), 12. a direct
 *  messenger message, 13. a mail whose sender carries a person's name, 14. otherwise unclear.
 *
 * Limits, stated plainly: the rules see only what the notification shows – for a mail the sender's display name and
 * the subject, usually not the address. Word lists are Austrian/German and finite; an institute they do not know ends
 * as [ImportanceReason.UNCLEAR], not as silence.
 */
object IncomingMessageClassifier {
    private const val B = "(?<![\\p{L}\\p{N}])"
    private const val E = "(?![\\p{L}\\p{N}])"
    private const val MAX_CUE = 48

    val MAIL_PACKAGES: Set<String> = setOf(
        "com.google.android.gm", "com.google.android.gm.lite", "com.microsoft.office.outlook",
        "com.samsung.android.email.provider", "ch.protonmail.android", "de.gmx.mobile.android.mail",
        "de.web.mobile.android.mail", "com.fsck.k9", "net.thunderbird.android",
        "com.yahoo.mobile.client.android.mail", "com.readdle.spark", "me.bluemail.mail",
    )

    fun kindOf(packageName: String, category: String?): IncomingKind =
        if (packageName in MAIL_PACKAGES || category?.trim()?.lowercase(Locale.ROOT) == "email") IncomingKind.MAIL
        else IncomingKind.MESSENGER

    // Matched against lower-cased text.
    private val AUTHORITY = Regex(B + "(?:" +
        "(?:bezirks|landes|oberlandes|amts|land|arbeits|sozial|handels|(?:landes|bundes)?verwaltungs|bundesfinanz|straf|" +
        "zivil|exekutions|verfassungs|jugend|familien|insolvenz|konkurs|schieds|kreis|bundes)gericht(?:s|es|e)?(?:hof(?:s|es)?)?" +
        "|gerichtshof(?:s|es)?|gerichtsvollzieher(?:s|in)?" +
        "|staatsanwaltschaft|finanzamt(?:s|es)?|finanzonline|finanzpolizei|zollamt(?:s|es)?|magistrat(?:s|es)?" +
        "|bezirkshauptmannschaft|gemeindeamt(?:s|es)?|stadtamt(?:s|es)?|marktgemeinde|stadtgemeinde|gemeinde" +
        "|standesamt(?:s|es)?|meldeamt(?:s|es)?|polizei(?:inspektion|direktion|kommissariat)?|landespolizeidirektion" +
        "|bundespolizei|bundesministerium(?:s)?|ministerium(?:s)?|landesregierung|bundeskanzleramt(?:s|es)?" +
        "|volksanwaltschaft|datenschutzbehörde|behörd(?:e|en|lich|liche|licher|liches|lichen)|gesundheitskasse" +
        "|pensionsversicherungsanstalt|pensionsversicherung|arbeitsmarktservice|sozialversicherung(?:sanstalt)?" +
        "|sozialministeriumservice|unfallversicherungsanstalt|digitales amt|id austria|bundesamt(?:s|es)?" +
        "|jugendamt(?:s|es)?|jobcenter|agentur für arbeit|arbeitsagentur|rentenversicherung|ordnungsamt(?:s|es)?" +
        "|bürgeramt(?:s|es)?|landratsamt(?:s|es)?|stadtverwaltung|kreisverwaltung|justiz|bezirksverwaltungsbehörde" +
        ")" + E + "|[.@](?:[a-z0-9-]+\\.)*gv\\.at" + E)

    /** Abbreviations count only in capitals and as a word of their own („AMS“, not „Amsterdam“). */
    private val AUTHORITY_ABBREVIATION = Regex(
        B + "(?:BH|ÖGK|OEGK|PVA|AMS|SVS|AUVA|BVAEB|LPD|BMF|BMI|BMJ|BFG|OGH|VwGH|VfGH|LVwG|BVwG)" + E)

    /** „Gericht“ alone is a court only as a sender – in a subject it is too often a dish. */
    private val PLAIN_COURT = Regex(B + "gericht(?:s|es)?" + E)

    private val INSTITUTION = Regex("(?:" +
        "(?<![\\p{L}])(?!(?:daten|power|sitz|park|garten|werk|blut|samen|eck|hobel)bank)\\p{L}*bank(?:en)?" + E +
        "|sparkasse|raiffeisen|bawag|" + B + "hypo" + E + "|" + B + "ksv|inkasso|kreditschutz" +
        "|versicherung|" + B + "(?:uniqa|generali|allianz|grawe|helvetia|wüstenrot)" + E +
        "|schule" + E + "|gymnasium|universität|hochschule|kindergarten|krabbelstube|elternverein|direktion" + E +
        "|ordination|arztpraxis|praxis" + E + "|" + B + "klinik|krankenhaus|" + B + "spital|ambulanz|ambulatorium" +
        "|" + B + "labor" + E + "|zahnarzt|zahnärzt|facharzt|fachärzt|hausarzt|hausärzt|" + B + "arzt" + E +
        "|" + B + "ärztin" + E + "|physiotherap|apotheke" +
        "|rechtsanw|anwalt|anwält|kanzlei|notar|steuerberat|wirtschaftsprüf|sachverständig|treuhand" +
        "|hausverwaltung|immobilienverwaltung)")

    private val INSTITUTION_ABBREVIATION = Regex(B + "(?:HTL|HAK|HLW|HBLA|BORG|FH|BRG)" + E)

    private val OFFICIAL = Regex(B + "(?:" +
        "bescheid(?:e|s|es)?|ladung|vorladung|aktenzeichen|geschäftszahl|zahlungsbefehl|mahnklage|mahnbescheid|mahnung|zahlungserinnerung" +
        "|exekution\\p{L}*|pfändung\\p{L}*|klage|klageschrift|urteil|beschluss(?:es)?|einspruch\\p{L}*|strafverfügung" +
        "|anonymverfügung|organmandat|kündigung|befund|vollstreckung\\p{L}*|räumung\\p{L}*|rechtsmittel\\p{L}*" +
        "|rs[ab](?:-brief\\p{L}*)?|rückscheinbrief\\p{L}*|hinterlegungsanzeige)" + E)

    /** „Gib mir bitte Bescheid“ / „Bescheid sagen“ is everyday speech, not an official notice. */
    private val GIVE_NOTICE = Regex(B + "(?:gib|gebt|geben|gebe|sag|sagt|sagen|sage)" + E + "[^.!?\\n]{0,30}?" + B + "bescheid" + E +
        "|" + B + "bescheid (?:geben|sagen|wissen|gibt|gebe|gegeben|gesagt)" + E)

    private val OFFICIAL_ABBREVIATION = Regex(B + "GZ" + E)

    /** „Frist“ is also an advertiser's word; it counts only in a mail without any advertising word. */
    private val OFFICIAL_WEAK = Regex(B + "frist(?:ablauf|setzung|verlängerung)?" + E)

    private const val CLAUSE_START = "(?:^|[.!?:–—-]\\s*|\\n\\s*)"

    private val PROMOTION = Regex("newsletter|rabatt|gutschein|coupon|" + B + "sale" + E + "|black friday|black week" +
        "|cyber monday|cyber week|gewinnspiel|abbestellen|unsubscribe|angebote der woche|flugblatt|prospekt" + E +
        "|schnäppchen|versandkostenfrei|gratis versand|gratisversand|kostenloser versand|" + B + "deals?" + E +
        "|nur heute|nur für kurze zeit|nur diese(?:s|n)? (?:woche|wochenende)|letzte chance|solange der vorrat reicht" +
        "|" + CLAUSE_START + "jetzt[^.!?\\n]{0,30}?" + B + "(?:shoppen|kaufen|bestellen|zugreifen|buchen|sichern|holen" +
        "|testen|mitmachen|mitspielen|entdecken|aktivieren|sparen)" + E +
        "|exklusiv für (?:sie|dich|mitglieder|kunden)|treuepunkte|bonus club" +
        "|\\d\\s?% (?:rabatt|günstiger|reduziert|sparen|auf )|bis zu -?\\d+\\s?%|(?<![\\p{L}\\p{N}])-\\s?\\d+\\s?%" +
        "|antworte stop|stop zum abmelden|preisfehler|sonderangebot|ausverkauf|" + B + "werbung" + E +
        "|marketing@|news@|promo(?:tion)?s?@|angebote@|aktion@")

    /** Two different ones of these make advertising; one alone („Frage zum Angebot von gestern“) does not. */
    private val PROMOTION_WEAK: List<Regex> = listOf(
        Regex("angebot"), Regex("aktion" + E + "|aktions"), Regex(B + "spar(?:en|e|t)" + E), Regex("günstig"),
        Regex("gratis|kostenlos"), Regex("vorteil"), Regex(B + "entdecke"),
        Regex("kollektion|neu eingetroffen|neu im shop|neuheiten"), Regex("empfehlungen für (?:dich|sie)"),
        Regex("geschenkidee"), Regex(B + "(?:ab|um|nur|statt)\\s\\d+(?:[.,]\\d+)?\\s?€"), Regex("der woche" + E),
        Regex("mittagsmenü|tagesmenü|speisekarte"), Regex(B + "gewinnen" + E + "|jackpot"), Regex(B + "abmelden" + E),
        Regex("\\d\\s?%"), Regex("warenkorb"), Regex(B + "premium" + E),
    )

    private val FOOD = Regex("der woche|des tages|menü|mittag|speise|rezept|koch|gasth|restaurant|pizzeria|küche")

    private val SOCIAL = Regex(B + "(?:facebook|instagram|linkedin|xing|tiktok|youtube|pinterest|snapchat|reddit|twitter" +
        "|threads|mastodon|nextdoor|strava)" + E)

    private val TITLE = Regex("(?i)^(?:(?:univ\\.-?)?prof\\.?|dr\\.?|med\\.?|iur\\.?|mag\\.?a?|dipl\\.-?ing\\.?|di|ing\\.?" +
        "|ddr\\.?|msc|bsc|mba|bakk\\.?|phd|priv\\.-?doz\\.?|frau|herr|hr\\.?|fr\\.?)$")
    private val NAME_WORD = Regex("^\\p{Lu}[\\p{L}'’]+(?:-\\p{Lu}[\\p{L}'’]+)*$")
    private val NOT_A_PERSON = setOf("team", "service", "kundenservice", "support", "shop", "store", "club", "newsletter",
        "info", "verein", "firma", "gmbh", "ag", "kg", "og", "online", "digital", "news", "abo", "konto", "portal")

    fun classify(input: ImportanceInput, rules: ImportanceRules = ImportanceRules()): ImportanceDecision {
        val sender = UntrustedNotificationText.clean(input.sender)
        val subject = UntrustedNotificationText.clean(input.subject)
        val text = UntrustedNotificationText.clean(input.text)
        val conversation = UntrustedNotificationText.clean(input.conversation)
        fun decided(verdict: ImportanceVerdict, reason: ImportanceReason, cue: String = "") =
            ImportanceDecision(verdict, reason, UntrustedNotificationText.bound(cue.trim(), MAX_CUE))

        listed(rules.always, sender, conversation, subject)?.let {
            return decided(ImportanceVerdict.ANNOUNCE, ImportanceReason.OWNER_ALWAYS, it)
        }
        listed(rules.never, sender, conversation, subject)?.let {
            return decided(ImportanceVerdict.SILENT, ImportanceReason.OWNER_NEVER, it)
        }
        if (input.isGroup && !rules.groups) return decided(ImportanceVerdict.SILENT, ImportanceReason.GROUP_CHAT)
        if (input.inContacts && !input.isGroup) return decided(ImportanceVerdict.ANNOUNCE, ImportanceReason.CONTACT)

        val senderLow = sender.lowercase(Locale.ROOT)
        val subjectLow = subject.lowercase(Locale.ROOT)
        val all = "$senderLow\n$subjectLow\n${text.lowercase(Locale.ROOT)}"
        val mail = input.kind == IncomingKind.MAIL
        val promotion = PROMOTION.find(all)?.value?.trim()?.trimStart('.', '!', '?', ':', '–', '—', '-')?.trim()
        val weak = PROMOTION_WEAK.mapNotNull { it.find(all)?.value }

        authority(sender, senderLow)?.let { return decided(ImportanceVerdict.ANNOUNCE, ImportanceReason.AUTHORITY, it) }
        if (promotion == null && weak.isEmpty() && !FOOD.containsMatchIn(all)) PLAIN_COURT.find(senderLow)?.let {
            return decided(ImportanceVerdict.ANNOUNCE, ImportanceReason.AUTHORITY, it.value)
        }
        if (promotion == null) {
            if (mail) authority(subject, subjectLow)?.let {
                return decided(ImportanceVerdict.ANNOUNCE, ImportanceReason.AUTHORITY, it)
            }
            (INSTITUTION.find(senderLow)?.value ?: INSTITUTION_ABBREVIATION.find(sender)?.value)?.let {
                return decided(ImportanceVerdict.ANNOUNCE, ImportanceReason.INSTITUTION, it)
            }
            if (mail) {
                val matter = "$subject\n$text"
                val lowered = GIVE_NOTICE.replace(matter.lowercase(Locale.ROOT), " ")
                (OFFICIAL.find(lowered)?.value ?: OFFICIAL_ABBREVIATION.find(matter)?.value
                    ?: OFFICIAL_WEAK.find(lowered)?.value?.takeIf { weak.isEmpty() })?.let {
                    return decided(ImportanceVerdict.ANNOUNCE, ImportanceReason.OFFICIAL_MATTER, it)
                }
            }
        }
        if (promotion != null || weak.size >= 2) return decided(ImportanceVerdict.SILENT, ImportanceReason.PROMOTION,
            promotion ?: weak.take(2).joinToString(", "))
        SOCIAL.find(senderLow)?.let { return decided(ImportanceVerdict.SILENT, ImportanceReason.SOCIAL_DIGEST, it.value) }
        if (input.isGroup) return decided(ImportanceVerdict.ANNOUNCE, ImportanceReason.GROUP_MESSAGE)
        if (!mail) return decided(ImportanceVerdict.ANNOUNCE, ImportanceReason.DIRECT_MESSAGE)
        if (personalName(sender)) return decided(ImportanceVerdict.ANNOUNCE, ImportanceReason.PERSONAL_NAME)
        return decided(ImportanceVerdict.UNCLEAR, ImportanceReason.UNCLEAR)
    }

    private fun authority(original: String, lowered: String): String? =
        AUTHORITY.find(lowered)?.value?.trimStart('.', '@') ?: AUTHORITY_ABBREVIATION.find(original)?.value

    /** The first list entry that stands as a word (or phrase) of its own in the sender, the conversation or the subject. */
    private fun listed(entries: List<String>, vararg fields: String): String? {
        val lowered = fields.map { it.lowercase(Locale.ROOT) }
        return entries.firstOrNull { raw ->
            val entry = UntrustedNotificationText.clean(raw).lowercase(Locale.ROOT)
            entry.length >= 2 && lowered.any { Regex(B + Regex.escape(entry) + E).containsMatchIn(it) }
        }?.let { UntrustedNotificationText.clean(it) }
    }

    /**
     * „Anna Berger“, „Mag. Petra Hofer“, „Dr. Wimmer“: two to four name words of which the first or the last is a
     * common given name, or a title in front of at least one name word. A single word („Lukas“, „Zalando“) is not
     * enough, and words like Team, Service or GmbH rule a person out.
     */
    internal fun personalName(sender: String): Boolean {
        val words = sender.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 7) return false
        val names = words.filterNot { TITLE.matches(it) }
        val titled = names.size < words.size
        if (names.isEmpty() || names.size > 4 || names.any { !NAME_WORD.matches(it) }) return false
        val lowered = names.map { it.lowercase(Locale.ROOT) }
        if (lowered.any { it in NOT_A_PERSON }) return false
        if (titled) return true
        return names.size >= 2 && (lowered.first().substringBefore('-') in GIVEN_NAMES || lowered.last() in GIVEN_NAMES)
    }

    /** Common Austrian and German given names; a fixed list, not a guess about any person. */
    private val GIVEN_NAMES: Set<String> = (
        "alexander alexandra alfred alois andrea andreas angela angelika anita anja anna anne annemarie anton armin astrid " +
        "barbara beate benjamin bernd bernhard berta bettina bianca birgit brigitte carina carmen caroline christa " +
        "christian christiane christina christine christoph christopher claudia clemens cornelia daniel daniela david " +
        "dieter dominik doris edith eduard elena elfriede elias elisabeth elke emil emma erich erika ernst erwin eva " +
        "evelyn fabian felix ferdinand florian franz franziska friedrich fritz gabriel gabriele georg gerald gerda " +
        "gerhard gernot gertrude gisela gottfried gregor gudrun günter günther hannah hannes hans harald hedwig heidi " +
        "heike heinrich heinz helene helga helmut herbert hermann hermine herta hilde hubert ilse ines ingeborg ingrid " +
        "irene iris isabella jakob jan jasmin johann johanna johannes jonas josef josefine judith julia julian jürgen " +
        "karin karl karoline katharina kathrin katrin kerstin klaus konrad kurt lara laura lea lena leon leopold lisa " +
        "lorenz lukas manfred manuel manuela marc marcel margarete margit maria marianne marie marina mario marion " +
        "markus martha martin martina matthias max maximilian melanie michael michaela monika moritz nadine natascha " +
        "nicole niklas nina norbert oliver oskar otto patricia patrick paul paula peter petra philipp rainer ralf " +
        "raphael regina reinhard renate rene richard robert roland roman rosa rosemarie rudolf sabine sabrina sandra " +
        "sara sarah sebastian siegfried silvia simon simone sonja sophie stefan stefanie stephan susanne sylvia tamara " +
        "tanja theresa therese thomas tobias ulrike ursula valentin vanessa verena veronika viktor viktoria walter " +
        "waltraud werner wilhelm wolfgang yvonne"
        ).split(' ').toSet()
}
