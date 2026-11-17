package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Plans which owner memories enter a model context and in which atomic groups (Brain round 17.11.2026).
 *
 * Before, [AiContextCompiler] rendered the top [HybridMemoryRetriever] hits as ONE all-or-nothing section: when the
 * byte budget of a small on-device model was a few bytes short, the whole memory vanished; the same statement
 * stored several times took several of the eight slots; and an outdated or corrected fact was shown exactly like the
 * current one. This planner keeps every existing boundary (identity resolution, privacy, STALE/CONTRADICTED stay out,
 * evidence remains literal and untrusted) and adds four deterministic steps, no model and no write:
 *
 *  1. Relational question (M8 [MemoryQuestionResolver]): "Wo wohnt der Bruder von Ute?" is ALSO retrieved as
 *     "Wo wohnt Kurt?" when the active facts make exactly one person Ute's sibling. Only retrieval changes.
 *  2. Consolidation: records whose normalized wording is identical are one statement; the best-ranked one is
 *     rendered, the others are listed as corroborating ids (content not repeated). Negations are part of the wording.
 *  3. Timeline (M8 [MemoryTimeline]): a selected record that a newer/corrected record supersedes is shown together
 *     with that successor (marked current), and two records that contradict each other are shown together with an
 *     explicit ask-back marker. The successor or partner is pulled in even when lexical ranking missed it.
 *  4. Atomic groups: every record linked by step 3, and every record naming the same entity (capitalised name or
 *     entity tag), forms one [Group]; the compiler packs whole groups by rank into
 *     the byte budget. A group that does not fit is omitted completely, so a correction or contradiction is never
 *     separated from the statement it qualifies, but the remaining groups still reach the model.
 */
object MemoryContextPlanner {
    enum class Role { SELECTED, CURRENT_SUCCESSOR, SUPERSEDED, CONFLICT_PARTNER }

    data class Entry(
        val memory: MemoryRecord,
        /** Rank score; pulled-in successors/partners carry the score of the record they qualify. */
        val score: Double,
        val role: Role,
        val corroboratingIds: List<String> = emptyList(),
        /** Ids of records that replace this one (SUPERSEDES/CORRECTS); this record is then not current. */
        val supersededBy: List<String> = emptyList(),
        /** Ids of records this one replaces. */
        val supersedes: List<String> = emptyList(),
        /** Ids of records with an incompatible value for the same thing and no marker deciding between them. */
        val conflictsWith: List<String> = emptyList()
    ) {
        init { require(score.isFinite() && score in 0.0..1.0) }
        val isCurrent: Boolean get() = supersededBy.isEmpty()
    }

    data class Group(val entries: List<Entry>) {
        init { require(entries.isNotEmpty()) }
        val rankScore: Double get() = entries.maxOf { it.score }
        val ids: List<String> get() = entries.map { it.memory.id }
    }

    data class Plan(
        val groups: List<Group>,
        /** The additionally retrieved relational question, when step 1 applied. */
        val relationalQuery: String?
    ) {
        val entryCount: Int get() = groups.sumOf { it.entries.size }
    }

    /** At most this many records are pulled into one group by timeline links (bounded prompt growth). */
    const val MAX_LINKED_PER_GROUP = 4

    /**
     * [records] must already be the caller's admissible set (identity-resolved, without STALE/CONTRADICTED);
     * privacy is enforced here with [allowedPrivacy] before any text is read. [limit] bounds the number of
     * distinct selected statements; linked successors/partners are added on top within [MAX_LINKED_PER_GROUP].
     */
    fun plan(
        query: String,
        records: Collection<MemoryRecord>,
        limit: Int,
        nowMillis: Long,
        allowedPrivacy: Set<PrivacyClass>
    ): Plan {
        require(limit >= 0 && nowMillis >= 0)
        if (limit == 0 || query.isBlank()) return Plan(emptyList(), null)
        val permitted = HybridMemoryRetriever.unambiguousRecords(records).filter { it.privacyClass in allowedPrivacy }
        if (permitted.isEmpty()) return Plan(emptyList(), null)
        val byId = permitted.associateBy { it.id }
        val factTexts = permitted.sortedByDescending { it.updatedAt }.map { it.id to it.content }

        // 1. relational rewrite (exactly one candidate or nothing)
        val relational = runCatching {
            MemoryQuestionResolver.resolve(query, factTexts.take(MemoryTimeline.MAX_FACTS))
        }.getOrNull()?.takeIf { it.isNotBlank() && it != query }
        val candidateLimit = (limit * 3).coerceIn(limit, 32)
        val scores = LinkedHashMap<String, ScoredMemory>()
        fun merge(hits: List<ScoredMemory>) = hits.forEach { hit ->
            val old = scores[hit.memory.id]
            if (old == null || hit.score.total > old.score.total) scores[hit.memory.id] = hit
        }
        merge(HybridMemoryRetriever.retrieve(query, permitted, candidateLimit, nowMillis, allowedPrivacy))
        if (relational != null)
            merge(HybridMemoryRetriever.retrieve(relational, permitted, candidateLimit, nowMillis, allowedPrivacy))
        if (scores.isEmpty()) return Plan(emptyList(), relational)
        val ranked = scores.values.sortedWith(RANK)

        // 2. consolidation of identical statements
        val representatives = LinkedHashMap<String, Pair<ScoredMemory, MutableList<String>>>()
        ranked.forEach { hit ->
            val key = wordingKey(hit.memory.content)
            val existing = representatives[key]
            if (existing == null) representatives[key] = hit to mutableListOf()
            else existing.second += hit.memory.id
        }
        val selected = representatives.values.take(limit)
        val selectedIds = selected.mapTo(LinkedHashSet()) { it.first.memory.id }
        val corroborations = selected.associate { it.first.memory.id to it.second.toList() }
        val selectedScore = selected.associate { it.first.memory.id to it.first.score.total }
        // A record that merely repeats a selected one is not a separate timeline participant.
        val repeated = corroborations.values.flatten().toSet()

        // 3. timeline over the selected records against every permitted active record
        val links = runCatching {
            MemoryTimeline.linksAmong(selectedIds, factTexts.filter { it.first !in repeated },
                permitted.associate { it.id to it.updatedAt })
        }.getOrDefault(emptyList())
        val supersededBy = HashMap<String, MutableList<String>>()
        val supersedes = HashMap<String, MutableList<String>>()
        val conflicts = HashMap<String, MutableList<String>>()
        links.forEach { link ->
            when (link.relation) {
                MemoryTimeline.Relation.SUPERSEDES, MemoryTimeline.Relation.CORRECTS -> {
                    supersededBy.getOrPut(link.older) { mutableListOf() } += link.newer
                    supersedes.getOrPut(link.newer) { mutableListOf() } += link.older
                }
                MemoryTimeline.Relation.CONTRADICTS -> {
                    conflicts.getOrPut(link.newer) { mutableListOf() } += link.older
                    conflicts.getOrPut(link.older) { mutableListOf() } += link.newer
                }
            }
        }

        // 4. atomic groups: walk outward from each selected record along timeline links (bounded)
        val assigned = HashSet<String>()
        val groups = ArrayList<Group>()
        selected.forEach { (hit, _) ->
            val root = hit.memory.id
            if (root in assigned) return@forEach
            val members = LinkedHashSet<String>()
            val queue = ArrayDeque(listOf(root))
            while (queue.isNotEmpty() && members.size < 1 + MAX_LINKED_PER_GROUP) {
                val id = queue.removeFirst()
                if (id in assigned || !members.add(id)) continue
                (supersededBy[id].orEmpty() + supersedes[id].orEmpty() + conflicts[id].orEmpty())
                    .filter { it in byId && it !in members && it !in assigned }
                    .forEach(queue::addLast)
            }
            assigned += members
            val groupScore = members.mapNotNull { selectedScore[it] }.maxOrNull() ?: hit.score.total
            val entries = members.map { id ->
                val memory = byId.getValue(id)
                val inGroup = { list: List<String>? -> list.orEmpty().filter { it in members }.distinct() }
                val by = inGroup(supersededBy[id]); val over = inGroup(supersedes[id]); val vs = inGroup(conflicts[id])
                Entry(
                    memory = memory,
                    score = (selectedScore[id] ?: groupScore).coerceIn(0.0, 1.0),
                    role = when {
                        id in selectedIds && by.isEmpty() -> Role.SELECTED
                        by.isNotEmpty() -> Role.SUPERSEDED
                        over.isNotEmpty() -> Role.CURRENT_SUCCESSOR
                        else -> Role.CONFLICT_PARTNER
                    },
                    corroboratingIds = corroborations[id].orEmpty(),
                    supersededBy = by,
                    supersedes = over,
                    conflictsWith = vs
                )
            }.sortedWith(compareBy<Entry> { !it.isCurrent }.thenByDescending { it.score }.thenBy { it.memory.id })
            groups += Group(entries)
        }
        return Plan(mergeByEntity(groups).sortedWith(compareByDescending<Group> { it.rankScore }.thenBy { it.ids.first() }),
            relational)
    }

    /**
     * The timeline heuristic does not recognise every correction ("Maren: ... Maren ist nicht in Graz." has no
     * parsable subject). Records about the same named entity (a capitalised word that is no function word) or with
     * a shared entity tag therefore always travel together: dropping one of them could drop the correction of the
     * other. Over-grouping only costs budget flexibility; it never separates a statement from its qualification.
     */
    private fun mergeByEntity(groups: List<Group>): List<Group> {
        if (groups.size < 2) return groups
        val parent = IntArray(groups.size) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        val owner = HashMap<String, Int>()
        groups.forEachIndexed { index, group ->
            group.entries.flatMap { entities(it.memory) }.toSet().forEach { key ->
                val other = owner.putIfAbsent(key, index)
                if (other != null) { val a = find(index); val b = find(other); if (a != b) parent[maxOf(a, b)] = minOf(a, b) }
            }
        }
        return groups.indices.groupBy(::find).values.map { members ->
            if (members.size == 1) groups[members.single()]
            else Group(members.flatMap { groups[it].entries }
                .sortedWith(compareBy<Entry> { !it.isCurrent }.thenByDescending { it.score }.thenBy { it.memory.id }))
        }
    }

    internal fun entities(memory: MemoryRecord): Set<String> {
        val out = HashSet<String>()
        memory.tags.forEach { tag -> tag.lowercase(Locale.ROOT).trim().takeIf { it.isNotEmpty() }?.let { out += "tag:" + it } }
        ENTITY_WORD.findAll(memory.content).forEach { match ->
            val word = match.value
            val lower = word.lowercase(Locale.ROOT)
            if (word.length >= 3 && word[0].isUpperCase() && lower !in FUNCTION_WORDS)
                out += "name:" + HybridMemoryRetriever.normalizeTerm(lower)
        }
        return out
    }

    private val ENTITY_WORD = Regex("\\p{L}[\\p{L}\\p{N}-]*")
    private val FUNCTION_WORDS = setOf(
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "eines",
        "ich", "du", "er", "sie", "es", "wir", "ihr", "mein", "meine", "meinem", "meinen", "meiner", "dein", "deine",
        "sein", "seine", "unser", "unsere", "und", "oder", "aber", "auch", "noch", "nicht", "kein", "keine", "ist",
        "sind", "war", "hat", "haben", "wird", "bei", "mit", "von", "vom", "zum", "zur", "für", "auf", "aus", "nach",
        "wenn", "dass", "weil", "the", "and", "this", "that", "with", "for", "not")

    /** Order-insensitive normalized wording; negation and numbers stay part of it. */
    internal fun wordingKey(content: String): String =
        content.lowercase(Locale.ROOT).split(SEPARATOR).filter(String::isNotBlank)
            .map(HybridMemoryRetriever::normalizeTerm).sorted().joinToString(" ")
            .ifEmpty { "\u0000" + content }

    private val SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
    private val RANK = compareByDescending<ScoredMemory> { it.score.total }
        .thenByDescending { it.memory.updatedAt }
        .thenBy { it.memory.id }
}
