package at.heinz.deviceai.learning

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest
import java.util.Locale

data class ContinuumMemoryHit(val id:String,val text:String,val kind:String,val sourceRef:String?,val score:Double,val semantic:Double)
data class ContinuumDialogueTurn(
    val at:Long,val input:String,val resolvedQuery:String,val answer:String,
    val topic:String,val modelHash:String,val memoryIds:List<String>,val grounded:Boolean,
    /** The stored conversation context was unreadable; it was moved aside and this turn started fresh. */
    val contextReset:Boolean=false
)
/** Bounded contextual retrieval from current ledger entries using the active SWB1.
 * Conversation text never trains weights or grants tool authority. Old responses
 * are not factual memory; every answer reselects current nonrevoked evidence.
 */
class ContinuumMemoryDialogue(private val directory:File) {
    private val file=File(directory,"dialogue-v1.bin")
    private data class SavedHistory(val conversationId:String?,val turns:List<ContinuumDialogueTurn>)

    fun reply(bytes:ByteArray,experiences:List<LearningExperience>,input:String,at:Long,
        rerank:((String,List<ContinuumMemoryHit>)->List<ContinuumMemoryHit>)?=null
    ):ContinuumDialogueTurn = replyInConversation(bytes,experiences,input,at,null,rerank)

    internal fun replyInConversation(bytes:ByteArray,experiences:List<LearningExperience>,input:String,at:Long,
        conversationId:String?,rerank:((String,List<ContinuumMemoryHit>)->List<ContinuumMemoryHit>)?=null
    ):ContinuumDialogueTurn {
        require(input.trim().length in 1..512 && at>=0)
        requireConversationId(conversationId)
        // The history is only conversation context (memories live in the ledger),
        // so an unreadable file must not block every later turn: it is kept as
        // dialogue-v1.bin.corrupt-<time> and the conversation starts fresh.
        var contextReset=false
        val history=try { history(conversationId) } catch (e:IllegalArgumentException) {
            quarantine(at)
            contextReset=true
            emptyList()
        }
        val text=input.trim()
        val followUp=Regex("(?i)\\b(darüber|dazu|damit|weiter|noch mehr|und warum|und wie)\\b").containsMatchIn(text)
        val previousTopic=history.lastOrNull()?.topic.orEmpty()
        val resolved=if(followUp&&previousTopic.isNotBlank())(previousTopic+" "+text).take(512) else text
        // A single high-confidence typo may be retried for retrieval only.
        // Never alter the displayed user input, stored user facts, or tool text.
        val firstHits=rank(bytes,resolved,experiences)
        val proposal=if(firstHits.isEmpty() &&
            !Regex("(?i)^(merke|lösche|entferne|widerrufe|starte|installiere|öffne|setze|reset)\\b")
                .containsMatchIn(text))
            ContinuumOrthographyProbe.suggest(
                resolved,
                experiences.filter { eligible(it) }.map { it.content.take(2048) })
            else null
        val recoveredHits=proposal?.let { rank(bytes,it.query,experiences) }.orEmpty()
        val retrievedHits=if(recoveredHits.isNotEmpty() &&
            wordEvidence(proposal!!.query,recoveredHits.first().text)>=0.82)
            recoveredHits else firstHits
        // A mention of an entity as a value does not define that entity.
        // Preserve the existing rank/Node contract and filter only the
        // dialogue evidence presented for explicit subject-definition questions.
        val hits=retrievedHits.filter {
            it.kind!="Bestätigte Nutzererinnerung" ||
                ownerMemoryAnswersDefinition(text,it.text)
        }
        val greeting=Regex("(?i)^(hallo|servus|guten (morgen|tag|abend)|hey)[!.\\s]*$").matches(text)
        // A greeting must not accidentally cite memories containing the word "Hallo".
        // An unanswered new question invalidates the old topic; only retrieved evidence
        // can establish a topic for future follow-ups.
        // The optional CRE1 callback executes only on SWB1-authorized hits.
        // Validate byte-for-byte candidate identity, not just IDs: a callback
        // can change ranking, but may not forge provenance, scores or content.
        val selected=if(greeting)emptyList<ContinuumMemoryHit>()
            else if(rerank==null || hits.isEmpty())hits else runCatching {
                val reordered=rerank(resolved,hits)
                require(reordered.size==hits.size &&
                    reordered.map { it.id }.distinct().size==hits.size &&
                    reordered.toSet()==hits.toSet()) { "CRE1 changed the eligible candidate set" }
                reordered
            }.getOrDefault(hits)
        val topic=when {
            greeting -> previousTopic
            selected.isEmpty() -> ""
            followUp -> previousTopic
            else -> text.take(320)
        }
        val answer=when {
            greeting -> "Hallo Heinz. Worüber möchtest du sprechen? Ich kann an unsere gespeicherten Themen anknüpfen."
            selected.isEmpty() -> "Dazu habe ich noch keine passende bestätigte Erinnerung oder Quellenbeobachtung. Du kannst mir mit „Merke dir: …“ etwas beibringen oder eine Recherche ausdrücklich starten."
            else -> (if(followUp)"Ich knüpfe an unser Thema an." else "Dazu finde ich in meinem Gedächtnis:")+selected.joinToString("") {
                "\n\n"+it.text.take(250)+"\n("+it.kind+(it.sourceRef?.let { ref -> "; "+ref.take(180) } ?: "")+")"
            }
        }.take(1000)
        val turn=ContinuumDialogueTurn(at,text.take(320),resolved,answer,topic,hash(bytes),selected.map { it.id },selected.isNotEmpty(),contextReset)
        save((history+turn).takeLast(8),conversationId)
        return turn
    }

    /** Owner-local diagnostics; model inputs must use the explicitly scoped overload. */
    fun history():List<ContinuumDialogueTurn> = readHistory().turns

    internal fun history(conversationId:String?):List<ContinuumDialogueTurn> {
        requireConversationId(conversationId)
        val saved=readHistory()
        // One bounded current history: a different or unknown identity never supplies context.
        return if(saved.conversationId==conversationId)saved.turns else emptyList()
    }

    private fun readHistory():SavedHistory {
        if(!file.exists())return SavedHistory(null,emptyList())
        require(file.length() in 36..65536) { "Ungültige Dialogdatei" }
        return LearningSnapshot.decode(file.readBytes()) { input ->
            val magic=input.readInt()
            require(magic==0x43444c31 || magic==0x43444c32)
            val conversationId=if(magic==0x43444c32)input.readUTF().also { requireConversationId(it) } else null
            val count=input.readInt().also { require(it in 0..8) }
            val turns=List(count) {
                val at=input.readLong().also { require(it>=0) }
                val text=input.readUTF().also { require(it.length<=320) }
                val query=input.readUTF().also { require(it.length<=512) }
                val answer=input.readUTF().also { require(it.length<=1000) }
                val topic=input.readUTF().also { require(it.length<=320) }
                val modelHash=input.readUTF().also { require(it.matches(Regex("[a-f0-9]{64}"))) }
                val grounded=input.readBoolean()
                val n=input.readInt().also { require(it in 0..3) }
                val ids=List(n) { input.readUTF().also { id -> require(id.matches(Regex("lx-[a-f0-9]{24}"))) } }
                require(grounded==ids.isNotEmpty())
                ContinuumDialogueTurn(at,text,query,answer,topic,modelHash,ids,grounded)
            }
            SavedHistory(conversationId,turns)
        }
    }

    fun clear() { check(!file.exists()||file.delete()) { "Dialog konnte nicht gelöscht werden" } }

    /** An old explicit chat cannot erase a newer bound chat. Unknown legacy context may be reset. */
    internal fun clear(conversationId:String):Boolean {
        requireConversationId(conversationId)
        val owner=try { readHistory().conversationId } catch (_:IllegalArgumentException) { null }
        if(owner!=null && owner!=conversationId)return false
        clear()
        return true
    }

    /** Keeps the unreadable bytes for inspection (newest [MAX_QUARANTINED] files). */
    private fun quarantine(at:Long) {
        var target=File(directory,"${file.name}.corrupt-$at")
        var n=1
        while(target.exists())target=File(directory,"${file.name}.corrupt-$at-${n++}")
        Files.move(file.toPath(),target.toPath())
        directory.listFiles { f -> f.name.startsWith(file.name+".corrupt-") }.orEmpty()
            .sortedByDescending { it.lastModified() }.drop(MAX_QUARANTINED).forEach { it.delete() }
    }

    private fun save(turns:List<ContinuumDialogueTurn>,conversationId:String?) {
        val bytes=LearningSnapshot.encode { out ->
            // Existing v1 remains the legacy format. v2 binds identity and turns in one atomic snapshot.
            out.writeInt(if(conversationId==null)0x43444c31 else 0x43444c32)
            if(conversationId!=null)out.writeUTF(conversationId)
            out.writeInt(turns.size)
            turns.forEach { t ->
                out.writeLong(t.at);out.writeUTF(t.input);out.writeUTF(t.resolvedQuery)
                out.writeUTF(t.answer);out.writeUTF(t.topic);out.writeUTF(t.modelHash)
                out.writeBoolean(t.grounded);out.writeInt(t.memoryIds.size);t.memoryIds.forEach(out::writeUTF)
            }
        }
        check(directory.exists()||directory.mkdirs())
        val temporary=File.createTempFile("dialogue-",".tmp",directory)
        try {
            FileOutputStream(temporary).use { it.write(bytes);it.fd.sync() }
            try { Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
            catch(_:AtomicMoveNotSupportedException) { Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING) }
        } finally { temporary.delete() }
    }

    companion object {
        const val MAX_QUARANTINED=3
        // A personal fact that merely contains "Orion" cannot answer
        // "Was ist Orion?". Only a claim whose subject is Orion qualifies.
        // Unsupported declarations fail closed; this is not general NLP.
        private val DEFINITION_QUESTION=Regex("""(?iu)^(?:was|wer)\s+ist\s+(.+?)\s*[?!.\s]*$""")
        private val OWNER_SUBJECT_CLAIM=Regex(
            """(?iu)^(.{1,120}?)\s+(?:ist|war|sind|waren|bedeutet|bezeichnet|heißt|heisst)\s+.+$"""
        )
        internal fun ownerMemoryAnswersDefinition(question:String,fact:String):Boolean {
            val query=DEFINITION_QUESTION.matchEntire(question.trim()) ?: return true
            val claim=OWNER_SUBJECT_CLAIM.matchEntire(fact.trim()) ?: return false
            fun normalize(value:String):String=value.trim()
                .trim('“','”','"','\'','?','!','.',':')
                .lowercase(Locale.ROOT).replace(Regex("""\s+""")," ")
            return normalize(query.groupValues[1])==normalize(claim.groupValues[1])
        }

        internal fun requireConversationId(id:String?) {
            require(id==null || Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,79}").matches(id)) { "Invalid conversation identity" }
        }
        fun hash(bytes:ByteArray):String=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** Mirrors .github/continuum/dialogue-retrieval.mjs exactly (cross-runtime fixture).
         * Selected on the frozen retrieval benchmark (docs/retrieval-benchmark.json):
         * word-evidence gate + TF-IDF over character 3-5 grams inside content words,
         * IDF from the current memory corpus. Neural similarity is reported, not weighted:
         * it changed the top-3 for 44% of benchmark queries without a consistent gain.
         */
        private val STOP=setOf("was","wie","weißt","über","und","noch","dazu","darüber","das","die","der","ist","ich","mir","mein","meine","du","bitte","kannst","erzähl","erkläre","welche","dieses","thema",
            "den","dem","des","ein","eine","einer","eines","einem","einen","mit","von","vom","zum","zur","für","auf","aus","bei","sich","sie","er","es","wir","ihr","sind","wird","werden","wurde","wurden","hat","haben","im","in","an","am","zu","oder","als","auch","nicht","nur","man","wo","wer","warum","wieso","weshalb","wann","woher","wohin","womit","woraus","worauf","gibt","kann","macht","dass","dabei","dadurch","deshalb","heute","meist","oft","etwa","viele","vielen","ganz","später","früher","etwas","mehr","erzähle","sag","sage","weiß","kennst","bekannt")
        const val EVIDENCE_THRESHOLD=0.6
        private val WORD=Regex("[\\p{L}\\p{N}]+")

        internal fun contentWords(text:String):List<String> =
            WORD.findAll(text.lowercase(Locale.ROOT)).map { it.value }.filter { it.length>=3 && it !in STOP }.toList()

        internal fun wordGrams(text:String):LinkedHashMap<String,Int> {
            val grams=LinkedHashMap<String,Int>()
            for(word in contentWords(text)) {
                val padded="^$word$"
                for(k in 3..5) for(i in 0..padded.length-k) {
                    val g=padded.substring(i,i+k);grams[g]=(grams[g] ?: 0)+1
                }
            }
            return grams
        }

        private fun trigrams(word:String):Set<String> {
            val padded="^$word$";val out=LinkedHashSet<String>()
            for(i in 0..padded.length-3)out+=padded.substring(i,i+3)
            return out
        }

        /** 1 = exact content word, 0.8 = shared 5-letter prefix, else best trigram Dice. */
        internal fun wordEvidence(query:String,text:String):Double {
            val q=contentWords(query);val t=contentWords(text).distinct()
            var best=0.0
            for(a in q)for(b in t) {
                if(a==b)return 1.0
                if(a.length>=5 && b.length>=5 && a.substring(0,5)==b.substring(0,5))best=maxOf(best,0.8)
                // Whole-word German compound heads remain searchable (e.g. Schlüssel).
                if(a.length>=5 && b.length>=a.length+3 && b.endsWith(a))best=maxOf(best,0.82)
                if(b.length>=5 && a.length>=b.length+3 && a.endsWith(b))best=maxOf(best,0.82)
                val ta=trigrams(a);val tb=trigrams(b)
                val shared=ta.count { it in tb }
                best=maxOf(best,2.0*shared/(ta.size+tb.size))
            }
            return best
        }

        private fun weigh(counts:Map<String,Int>,idf:(String)->Double):LinkedHashMap<String,Double> {
            val v=LinkedHashMap<String,Double>()
            for((g,c) in counts)v[g]=(1+StrictMath.log(c.toDouble()))*idf(g)
            return v
        }

        private fun cosine(a:Map<String,Double>,b:Map<String,Double>):Double {
            var dot=0.0;var na=0.0;var nb=0.0
            for((g,x) in a){na+=x*x;val y=b[g];if(y!=null && y!=0.0)dot+=x*y}
            for(y in b.values)nb+=y*y
            return if(na>0 && nb>0)dot/StrictMath.sqrt(na*nb) else 0.0
        }

        private fun eligible(e:LearningExperience):Boolean =
            e.status in setOf(LearningStatus.CONFIRMED,LearningStatus.LEARNED,LearningStatus.OBSERVED,LearningStatus.REPLAY_PENDING) &&
            ((e.type==LearningExperienceType.USER_FACT && e.trust==LearningTrustClass.OWNER_CONFIRMED && e.provenance==LearningProvenance.USER_CONFIRMED && e.status!=LearningStatus.OBSERVED) ||
             (e.type==LearningExperienceType.SOURCE_TEXT && e.trust==LearningTrustClass.SOURCE_OBSERVATION &&
              e.provenance in setOf(LearningProvenance.REVIEWED_WEB_SOURCE,LearningProvenance.MENTOR_WIKIPEDIA) &&
              e.sourceRef?.startsWith("https://")==true))

        /**
         * SOURCE_TEXT must support every distinctive long term or letter-digit ID
         * within the SAME excerpt; partial prefix/suffix overlap does not suffice.
         * This is a lexical guard, not a factual guarantee. Owner memories unchanged.
         */
        /** Source observation citations: conservative, directional lexical anchor check.
         * Does not change OWNER_CONFIRMED wordEvidence or train any weights. */
        private val SOURCE_QUERY_OPERATORS=setOf("analysiere","analysieren","bedeuten","bedeutet","beschreibe","beschreiben","beschreibst","charakterisiere","charakterisieren","darstelle","darstellen","definiere","definieren","definiert","erkläre","erklären","erklärst","erläutere","erläutern","erläuterst","erzaehle","funktionieren","funktionierend","funktioniert","funktionierte","funktionierten","nenne","nennen","unterscheide","unterscheiden","unterscheidet","untersuche","untersuchen","vergleiche","vergleichen","zeige","zeigen","zusammenfassen","zusammenfasst","zusammengefasst")
        private val SOURCE_INFLECTION_TAILS=listOf("","e","n","s","en","er","es","em")

        // Inspect original text before contentWords drops short numeric suffixes.
        // One explicit identifier is required literally; comparison entailment is separate.
        private val IDENTIFIER_TOKEN=Regex("[\\p{L}\\p{N}]+(?:[-._][\\p{L}\\p{N}]+)*")
        // Names followed by a separate numeric suffix are also identifiers.
        // Without this guard, "Gerät Kappa 7" can cite "Gerät Kappa 70"
        // although the existing alphanumeric-token rule rejects Kappa7/70.
        // Only an explicit two-word name + number is considered, rather
        // than arbitrary year references such as "Jahr 2024".
        private val SPLIT_NAME_NUMBER=Regex(
            """(?iu)\b[\p{L}]{3,35}\s+([\p{L}]{3,35})[ \t-]+([0-9]{1,5})\b"""
        )
        private fun splitNameNumberIds(value:String):Set<String> =
            SPLIT_NAME_NUMBER.findAll(
                java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFC)
                    .lowercase(Locale.ROOT)
            ).map { it.groupValues[1]+"#"+it.groupValues[2] }.toSet()

        private fun singleIdentifierEvidence(query:String,text:String):Boolean {
            fun identifiers(value:String):Set<String> = IDENTIFIER_TOKEN.findAll(
                java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFC)
                    .lowercase(Locale.ROOT))
                .map { it.value }.filter { token ->
                    token.any(Char::isLetter)&&token.any(Char::isDigit)
                }.toSet()
            val required=identifiers(query)
            if(required.size==1 && required.single() !in identifiers(text))return false
            val splitIds=splitNameNumberIds(query)
            return splitIds.isEmpty() || splitIds.all { it in splitNameNumberIds(text) }
        }

        internal fun sourceWordMatch(needle:String,word:String):Boolean {
            if(needle==word)return true
            if(needle.any(Char::isDigit)||word.any(Char::isDigit))return false
            for(na in SOURCE_INFLECTION_TAILS)for(wb in SOURCE_INFLECTION_TAILS) {
                if(na.isEmpty()&&wb.isEmpty())continue
                if(!needle.endsWith(na)||!word.endsWith(wb))continue
                val a=needle.dropLast(na.length)
                val b=word.dropLast(wb.length)
                if(a.length>=6&&a==b)return true
            }
            return needle.length>=8&&word.length>=needle.length+3&&word.endsWith(needle)
        }

        internal fun sourceTermEvidence(query:String,text:String):Boolean {
            if(query.length>4096||text.isEmpty())return false
            if(!singleIdentifierEvidence(query,text))return false
            val terms=contentWords(java.text.Normalizer.normalize(query,java.text.Normalizer.Form.NFC))
                .distinct().filter { it !in SOURCE_QUERY_OPERATORS }
            val source=contentWords(java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFC)).distinct()
            if(terms.isEmpty()||source.isEmpty())return false
            val hard=terms.filter { term ->
                term.length>=12||(term.length>=4&&term.any(Char::isLetter)&&term.any(Char::isDigit))
            }
            val anchors=if(hard.isNotEmpty())hard else terms.filter { it.length>=4 }
            if(anchors.isEmpty())return false
            return anchors.all { needle -> source.any { word -> sourceWordMatch(needle,word) } }
        }

        fun rank(bytes:ByteArray,query:String,experiences:List<LearningExperience>):List<ContinuumMemoryHit> {
            val model=TrainableSubwordEncoder.restore(bytes)
            val q=query.take(1000)
            val pool=experiences.filter(::eligible).distinctBy { it.id }
            val df=HashMap<String,Int>()
            for(e in pool)for(g in wordGrams(e.content.take(1000)).keys)df[g]=(df[g] ?: 0)+1
            val n=pool.size
            val idf={ g:String -> df[g]?.let { StrictMath.log((n+1.0)/(it+0.5)) } ?: StrictMath.log(n+1.0) }
            val qv=weigh(wordGrams(q),idf)
            return pool.mapNotNull { e ->
                val text=e.content.take(1000)
                if(!singleIdentifierEvidence(q,text))return@mapNotNull null
                if(e.type==LearningExperienceType.SOURCE_TEXT &&
                    !sourceTermEvidence(q,text))return@mapNotNull null
                if(wordEvidence(q,text)<EVIDENCE_THRESHOLD)return@mapNotNull null
                val score=cosine(qv,weigh(wordGrams(text),idf))
                if(!(score>0.0))return@mapNotNull null
                ContinuumMemoryHit(e.id,e.content,if(e.type==LearningExperienceType.USER_FACT)"Bestätigte Nutzererinnerung" else "Unbestätigte Quellenbeobachtung",e.sourceRef,
                    score,model.similarity(q,text))
            }.sortedWith(compareByDescending<ContinuumMemoryHit>{it.score}.thenBy{it.id}).take(3)
        }
    }
}
