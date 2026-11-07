package at.heinz.deviceai.learning.context

import java.util.Locale

internal enum class Ctx3Gender { MASCULINE, FEMININE, UNKNOWN }

internal enum class Ctx3OwnerRole {
    DAUGHTER, SON, SISTER, BROTHER, WIFE, HUSBAND, PARTNER_FEMALE, PARTNER_MALE,
    MOTHER, FATHER, GRANDMOTHER, GRANDFATHER, GRANDDAUGHTER, GRANDSON
}

/** Confirmed-fact role projection. No relation is invented from names or pronouns alone. */
internal object Ctx3RoleIndex {
    private val roleWords = mapOf(
        "tochter" to Ctx3OwnerRole.DAUGHTER,
        "sohn" to Ctx3OwnerRole.SON,
        "schwester" to Ctx3OwnerRole.SISTER,
        "bruder" to Ctx3OwnerRole.BROTHER,
        "frau" to Ctx3OwnerRole.WIFE,
        "mann" to Ctx3OwnerRole.HUSBAND,
        "partnerin" to Ctx3OwnerRole.PARTNER_FEMALE,
        "partner" to Ctx3OwnerRole.PARTNER_MALE,
        "mutter" to Ctx3OwnerRole.MOTHER,
        "vater" to Ctx3OwnerRole.FATHER,
        "oma" to Ctx3OwnerRole.GRANDMOTHER,
        "opa" to Ctx3OwnerRole.GRANDFATHER,
        "enkelin" to Ctx3OwnerRole.GRANDDAUGHTER,
        "enkel" to Ctx3OwnerRole.GRANDSON
    )

    private const val ROLE =
        "(Tochter|Sohn|Schwester|Bruder|Frau|Mann|Partnerin|Partner|Mutter|Vater|Oma|Opa|Enkelin|Enkel)"
    private const val NAME =
        "([\\p{Lu}][\\p{L}'’-]{1,39}(?:\\s+[\\p{Lu}][\\p{L}'’-]{1,39})?)"

    private val directNamed = Regex(
        "^(?:Mein|Meine|Unser|Unsere)\\s+$ROLE\\s+$NAME(?=\\s|[,.!?]|$)",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val directHeisst = Regex(
        "^(?:Mein|Meine|Unser|Unsere)\\s+$ROLE\\s+heißt\\s+$NAME(?=\\s|[,.!?]|$)",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val inverted = Regex(
        "^$NAME\\s+ist\\s+(?:mein|meine|unser|unsere)\\s+$ROLE(?=\\s|[,.!?]|$)",
        setOf(RegexOption.IGNORE_CASE)
    )

    private data class RoleMention(val name: String, val role: Ctx3OwnerRole)

    fun enrich(facts: Map<String, String>, base: List<Ctx3Entity>): List<Ctx3Entity> {
        require(facts.size <= 256)
        val mentions = linkedMapOf<String, Pair<String, MutableSet<Ctx3OwnerRole>>>()

        facts.values.forEach { text ->
            extract(text)?.let { mention ->
                val key = mention.name.lowercase(Locale.ROOT)
                val entry = mentions.getOrPut(key) { mention.name to linkedSetOf() }
                entry.second += mention.role
            }
        }
        if (mentions.isEmpty()) return base

        val byId = linkedMapOf<String, Ctx3Entity>()
        base.forEach { byId[it.id] = it }

        mentions.forEach { (key, pair) ->
            val (displayName, roles) = pair
            val sourceIds = facts.asSequence()
                .filter { (_, text) -> containsName(text, displayName) }
                .map { it.key }
                .take(128)
                .toSet()
            if (sourceIds.isEmpty()) return@forEach

            val gender = genderOf(roles)
            val addedPronouns = when (gender) {
                Ctx3Gender.FEMININE -> setOf("sie")
                Ctx3Gender.MASCULINE -> setOf("er", "ihn", "ihm")
                Ctx3Gender.UNKNOWN -> emptySet()
            }
            val existing = byId[key]
            byId[key] = if (existing == null) {
                Ctx3Entity(
                    id = key,
                    label = displayName,
                    aliases = setOf(displayName),
                    pronouns = addedPronouns,
                    sourceIds = sourceIds,
                    gender = gender,
                    ownerRoles = roles.toSet()
                )
            } else {
                val mergedRoles = existing.ownerRoles + roles
                existing.copy(
                    aliases = existing.aliases + displayName,
                    pronouns = existing.pronouns + addedPronouns,
                    sourceIds = existing.sourceIds + sourceIds,
                    gender = mergeGender(existing.gender, gender),
                    ownerRoles = mergedRoles
                )
            }
        }
        return byId.values.take(128)
    }

    private fun extract(text: String): RoleMention? {
        directHeisst.find(text)?.let { match ->
            return mention(match.groupValues[1], match.groupValues[2])
        }
        directNamed.find(text)?.let { match ->
            return mention(match.groupValues[1], match.groupValues[2])
        }
        inverted.find(text)?.let { match ->
            return mention(match.groupValues[2], match.groupValues[1])
        }
        return null
    }

    private fun mention(roleRaw: String, name: String): RoleMention? {
        val role = roleWords[roleRaw.lowercase(Locale.GERMAN)] ?: return null
        return RoleMention(name.trim(), role)
    }

    private fun containsName(text: String, name: String): Boolean =
        Regex(
            "(?iu)(?<![\\p{L}\\p{N}])" + Regex.escape(name) + "(?![\\p{L}\\p{N}])"
        ).containsMatchIn(text)

    private fun genderOf(roles: Set<Ctx3OwnerRole>): Ctx3Gender {
        val genders = roles.mapTo(linkedSetOf()) { roleGender(it) }
        return if (genders.size == 1) genders.single() else Ctx3Gender.UNKNOWN
    }

    private fun roleGender(role: Ctx3OwnerRole): Ctx3Gender = when (role) {
        Ctx3OwnerRole.DAUGHTER, Ctx3OwnerRole.SISTER, Ctx3OwnerRole.WIFE,
        Ctx3OwnerRole.PARTNER_FEMALE, Ctx3OwnerRole.MOTHER, Ctx3OwnerRole.GRANDMOTHER,
        Ctx3OwnerRole.GRANDDAUGHTER -> Ctx3Gender.FEMININE
        else -> Ctx3Gender.MASCULINE
    }

    private fun mergeGender(a: Ctx3Gender, b: Ctx3Gender): Ctx3Gender = when {
        a == Ctx3Gender.UNKNOWN -> b
        b == Ctx3Gender.UNKNOWN -> a
        a == b -> a
        else -> Ctx3Gender.UNKNOWN
    }
}

/** Conservative possessive-role resolver over the confirmed role projection. */
internal object Ctx3RoleResolver {
    data class Binding(val entity: Ctx3Entity, val reason: String)

    private val cue = Regex(
        """\b(mein(?:e|en|em|er)?|dein(?:e|en|em|er)?|sein(?:e|en|em|er)?|ihr(?:e|en|em|er)?)\s+""" +
            """(tochter|sohn|schwester|bruder|frau|mann|partnerin|partner|mutter|vater|oma|opa|enkelin|enkel)\b""",
        RegexOption.IGNORE_CASE
    )

    fun hasCue(input: String): Boolean = cue.containsMatchIn(input)

    fun resolve(input: String, currentFocus: List<Ctx3Entity>, entities: List<Ctx3Entity>): Binding? {
        val match = cue.find(input) ?: return null
        val possessive = match.groupValues[1].lowercase(Locale.GERMAN)
        val relation = match.groupValues[2].lowercase(Locale.GERMAN)
        val targetRole = roleFor(relation) ?: return null

        val candidates = when {
            possessive.startsWith("mein") -> entities.filter { targetRole in it.ownerRoles }
            possessive.startsWith("dein") -> emptyList()
            possessive.startsWith("ihr") || possessive.startsWith("sein") -> {
                val anchor = currentFocus.singleOrNull() ?: return null
                if (!possessiveGenderMatches(possessive, anchor.gender)) return null
                relativeCandidates(anchor, targetRole, entities)
            }
            else -> emptyList()
        }.distinctBy { it.id }

        return candidates.singleOrNull()?.let { Binding(it, "ROLE_POSSESSIVE_BINDING") }
    }

    private fun relativeCandidates(
        anchor: Ctx3Entity,
        targetRole: Ctx3OwnerRole,
        entities: List<Ctx3Entity>
    ): List<Ctx3Entity> {
        val anchorIsOwnerChild = anchor.ownerRoles.any { it in setOf(Ctx3OwnerRole.DAUGHTER, Ctx3OwnerRole.SON) }
        val anchorIsOwnerSibling = anchor.ownerRoles.any { it in setOf(Ctx3OwnerRole.SISTER, Ctx3OwnerRole.BROTHER) }

        val allowed = when {
            targetRole == Ctx3OwnerRole.DAUGHTER && anchorIsOwnerChild -> setOf(Ctx3OwnerRole.DAUGHTER)
            targetRole == Ctx3OwnerRole.SON && anchorIsOwnerChild -> setOf(Ctx3OwnerRole.SON)
            targetRole == Ctx3OwnerRole.SISTER && anchorIsOwnerChild -> setOf(Ctx3OwnerRole.DAUGHTER)
            targetRole == Ctx3OwnerRole.BROTHER && anchorIsOwnerChild -> setOf(Ctx3OwnerRole.SON)
            targetRole == Ctx3OwnerRole.SISTER && anchorIsOwnerSibling -> setOf(Ctx3OwnerRole.SISTER)
            targetRole == Ctx3OwnerRole.BROTHER && anchorIsOwnerSibling -> setOf(Ctx3OwnerRole.BROTHER)
            else -> emptySet()
        }
        if (allowed.isEmpty()) return emptyList()
        return entities.filter { entity ->
            entity.id != anchor.id && entity.ownerRoles.any { it in allowed }
        }
    }

    private fun possessiveGenderMatches(possessive: String, gender: Ctx3Gender): Boolean = when {
        possessive.startsWith("sein") -> gender == Ctx3Gender.MASCULINE
        possessive.startsWith("ihr") -> gender == Ctx3Gender.FEMININE
        else -> false
    }

    private fun roleFor(raw: String): Ctx3OwnerRole? = when (raw) {
        "tochter" -> Ctx3OwnerRole.DAUGHTER
        "sohn" -> Ctx3OwnerRole.SON
        "schwester" -> Ctx3OwnerRole.SISTER
        "bruder" -> Ctx3OwnerRole.BROTHER
        "frau" -> Ctx3OwnerRole.WIFE
        "mann" -> Ctx3OwnerRole.HUSBAND
        "partnerin" -> Ctx3OwnerRole.PARTNER_FEMALE
        "partner" -> Ctx3OwnerRole.PARTNER_MALE
        "mutter" -> Ctx3OwnerRole.MOTHER
        "vater" -> Ctx3OwnerRole.FATHER
        "oma" -> Ctx3OwnerRole.GRANDMOTHER
        "opa" -> Ctx3OwnerRole.GRANDFATHER
        "enkelin" -> Ctx3OwnerRole.GRANDDAUGHTER
        "enkel" -> Ctx3OwnerRole.GRANDSON
        else -> null
    }
}
