package at.heinz.deviceai.sending

import java.time.Instant
import java.util.Locale

/**
 * A relation the owner can name instead of a contact („mein Vater“, „meiner Chefin“). [words]: every spoken form
 * that means this relation without doubt (Papa = Vater, Mama = Mutter). [yours]: „dein Vater“ for the questions.
 * [needsPossessive]: „Frau“ and „Mann“ are only a relation with „mein …“ – „Frau Huber“ is a name.
 */
internal enum class OwnerRelation(val words: Set<String>, val yours: String, val needsPossessive: Boolean = false) {
    VATER(setOf("vater", "papa", "papi", "vati"), "dein Vater"),
    MUTTER(setOf("mutter", "mama", "mami", "mutti"), "deine Mutter"),
    BRUDER(setOf("bruder"), "dein Bruder"),
    SCHWESTER(setOf("schwester"), "deine Schwester"),
    FRAU(setOf("frau", "ehefrau"), "deine Frau", needsPossessive = true),
    MANN(setOf("mann", "ehemann"), "dein Mann", needsPossessive = true),
    SOHN(setOf("sohn"), "dein Sohn"),
    TOCHTER(setOf("tochter"), "deine Tochter"),
    OMA(setOf("oma", "großmutter", "grossmutter"), "deine Oma"),
    OPA(setOf("opa", "großvater", "grossvater"), "dein Opa"),
    CHEF(setOf("chef"), "dein Chef"),
    CHEFIN(setOf("chefin"), "deine Chefin"),
    ANWALT(setOf("anwalt"), "dein Anwalt"),
    ANWAELTIN(setOf("anwältin", "anwaeltin"), "deine Anwältin"),
    ARZT(setOf("arzt"), "dein Arzt"),
    AERZTIN(setOf("ärztin", "aerztin"), "deine Ärztin");

    /** „mein Vater“, „meine Mutter“ – as the owner says it. */
    val mine: String get() = "mein" + yours.removePrefix("dein")

    /** „Wer ist dein Vater …?“ with the verb first: „Wer ist deine Mutter“. */
    val whoIs: String get() = "Wer ist $yours"

    companion object {
        private const val E = "(?![\\p{L}\\p{N}_])"
        private const val POSSESSIVE = "(?:mein|meine|meinen|meinem|meiner|meines|unser|unsere|unseren|unserem|unserer)"
        private const val ARTICLE = "(?:der|dem|den|die|da|des)"
        private val ALL = entries.flatMap { relation -> relation.words.map { it to relation } }.sortedByDescending { it.first.length }
        private val WORDS = ALL.joinToString("|") { Regex.escape(it.first) }
        /** The whole phrase is a relation: an optional possessive or article and exactly one relation word. */
        private val whole = Regex("(?iu)^\\s*(?:($POSSESSIVE)\\s+|$ARTICLE\\s+)?($WORDS)\\s*$")
        /** A relation at the start of a longer phrase („meinem Chef, ich komme später“); the rest is returned. */
        private val leading = Regex("(?iu)^\\s*(?:($POSSESSIVE)\\s+|$ARTICLE\\s+)?($WORDS)$E")

        private fun of(word: String): OwnerRelation? = ALL.firstOrNull { it.first == word.lowercase(Locale.GERMAN) }?.second

        /** The relation [phrase] names as a whole, or null when it names a person or anything else. */
        fun parse(phrase: String): OwnerRelation? {
            val match = whole.matchEntire(phrase.trim().trimEnd('.', '!', ',')) ?: return null
            val relation = of(match.groupValues[2]) ?: return null
            if (relation.needsPossessive && match.groupValues[1].isEmpty()) return null
            return relation
        }

        /**
         * „meinem Chef, ich komme später“ → (CHEF, „meinem Chef“, „, ich komme später“). Null when [text] does not
         * start with a relation. Only a possessive form counts here: „Frau Huber ich komme“ stays a name.
         */
        fun split(text: String): Triple<OwnerRelation, String, String>? {
            val match = leading.find(text) ?: return null
            if (match.groupValues[1].isEmpty()) return null
            val relation = of(match.groupValues[2]) ?: return null
            return Triple(relation, match.value.trim(), text.substring(match.range.last + 1))
        }

        /** The contact's whole display name is one of the relation's words („Papa“, „Mama“): the owner's own name for it. */
        fun namesExactly(relation: OwnerRelation, displayName: String): Boolean =
            displayName.lowercase(Locale.GERMAN).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim() in relation.words

        private val forget = Regex("(?iu)^\\s*(?:bitte\\s+)?vergiss(?:\\s+bitte)?\\s*,?\\s*(?:wer|was)\\s+($POSSESSIVE\\s+(?:$WORDS))\\s+ist[.!]?\\s*$|" +
            "^\\s*(?:bitte\\s+)?vergiss(?:\\s+bitte)?\\s*,?\\s*dass\\s+.{1,80}?\\s+($POSSESSIVE\\s+(?:$WORDS))\\s+ist[.!]?\\s*$")

        /** „Vergiss, wer mein Vater ist“ / „Vergiss, dass Thomas mein Vater ist“: the relation to forget, or null. */
        fun forgetCommand(utterance: String): OwnerRelation? {
            val match = forget.matchEntire(utterance) ?: return null
            val phrase = match.groupValues[1].ifEmpty { match.groupValues[2] }
            return parse(phrase)
        }
    }
}

/** One relation the owner confirmed: which address-book contact it is, when, and how it was learnt. */
internal data class OwnerRelationEntry(val relation: OwnerRelation, val contactId: Long, val displayName: String,
                                       val confirmedAt: String, val provenance: String, val firstSendDone: Boolean)

/**
 * The owner's confirmed relations („Thomas Muster ist mein Vater“). A relation is only ever stored after his own
 * „Ja“ to „Soll ich mir merken …?“; it is never guessed from a contact's name. App-internal preferences (the same
 * port as the send grants), removable by „Vergiss, wer mein Vater ist“ or in „Selbst senden“.
 *
 * [OwnerRelationEntry.firstSendDone]: the first send through a newly stored relation is always confirmed by the owner,
 * also with the standing grant; only afterwards does the relation count like a name he said himself.
 */
internal class OwnerRelations(private val store: OwnerSendKeyValue, private val now: () -> Instant = Instant::now) {
    fun get(relation: OwnerRelation): OwnerRelationEntry? {
        val raw = store.get(key(relation)) ?: return null
        val parts = raw.split('\u001F')
        if (parts.size != 5) return null
        val id = parts[0].toLongOrNull() ?: return null
        return OwnerRelationEntry(relation, id, parts[1], parts[2], parts[3], parts[4] == "1")
    }

    fun all(): List<OwnerRelationEntry> = OwnerRelation.entries.mapNotNull(::get)

    /** Only called after the owner's explicit yes. */
    @Synchronized fun remember(relation: OwnerRelation, contactId: Long, displayName: String, provenance: String = PROVENANCE_OWNER_NAMED) {
        val name = displayName.replace('\u001F', ' ').take(80)
        store.put(key(relation), listOf(contactId.toString(), name, now().toString(), provenance, "0").joinToString("\u001F"))
    }

    @Synchronized fun markSent(relation: OwnerRelation) {
        val entry = get(relation) ?: return
        if (entry.firstSendDone) return
        store.put(key(relation), listOf(entry.contactId.toString(), entry.displayName, entry.confirmedAt, entry.provenance, "1")
            .joinToString("\u001F"))
    }

    /** true when something was stored. */
    @Synchronized fun forget(relation: OwnerRelation): Boolean {
        val had = store.get(key(relation)) != null
        store.put(key(relation), null)
        return had
    }

    private fun key(relation: OwnerRelation) = "relation_" + relation.name.lowercase(Locale.ROOT)

    companion object {
        const val PREFERENCES = "continuum-owner-relations"
        /** The owner named the contact when asked and then said yes to remembering it. */
        const val PROVENANCE_OWNER_NAMED = "owner_named_and_confirmed"
    }
}
