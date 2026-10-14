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
    val topic:String,val modelHash:String,val memoryIds:List<String>,val grounded:Boolean
)

/** Bounded contextual retrieval from current ledger entries using the active SWB1.
 * Conversation text never trains weights or grants tool authority. Old responses
 * are not factual memory; every answer reselects current nonrevoked evidence.
 */
class ContinuumMemoryDialogue(private val directory:File) {
    private val file=File(directory,"dialogue-v1.bin")

    fun reply(bytes:ByteArray,experiences:List<LearningExperience>,input:String,at:Long):ContinuumDialogueTurn {
        require(input.trim().length in 1..512 && at>=0)
        val history=history()
        val text=input.trim()
        val followUp=Regex("(?i)\\b(darüber|dazu|damit|weiter|noch mehr|und warum|und wie)\\b").containsMatchIn(text)
        val previousTopic=history.lastOrNull()?.topic.orEmpty()
        val resolved=if(followUp&&previousTopic.isNotBlank())(previousTopic+" "+text).take(512) else text
        val hits=rank(bytes,resolved,experiences)
        val greeting=Regex("(?i)^(hallo|servus|guten (morgen|tag|abend)|hey)[!.\\s]*$").matches(text)
        // A greeting must not accidentally cite memories containing the word "Hallo".
        // An unanswered new question invalidates the old topic; only retrieved evidence
        // can establish a topic for future follow-ups.
        val selected=if(greeting)emptyList<ContinuumMemoryHit>() else hits
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
        val turn=ContinuumDialogueTurn(at,text.take(320),resolved,answer,topic,hash(bytes),selected.map { it.id },selected.isNotEmpty())
        save((history+turn).takeLast(8))
        return turn
    }

    fun history():List<ContinuumDialogueTurn> {
        if(!file.exists())return emptyList()
        require(file.length() in 36..65536) { "Ungültige Dialogdatei" }
        return LearningSnapshot.decode(file.readBytes()) { input ->
            require(input.readInt()==0x43444c31)
            val count=input.readInt().also { require(it in 0..8) }
            List(count) {
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
        }
    }

    fun clear() { check(!file.exists()||file.delete()) { "Dialog konnte nicht gelöscht werden" } }

    private fun save(turns:List<ContinuumDialogueTurn>) {
        val bytes=LearningSnapshot.encode { out ->
            out.writeInt(0x43444c31);out.writeInt(turns.size)
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
        private val stop=setOf("was","wie","weißt","über","und","noch","dazu","darüber","das","die","der","ist","ich","mir","mein","meine","du","bitte","kannst","erzähl","erkläre","welche","dieses","thema")
        private fun tokens(text:String):Set<String> = Regex("[\\p{L}\\p{N}]{3,}").findAll(text.lowercase(Locale.ROOT)).map { it.value }.filter { it !in stop }.toSet()
        fun hash(bytes:ByteArray):String=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** Same lexical gate and 0.35 neural coefficient as GitHub rankDialogueMemories. */
        fun rank(bytes:ByteArray,query:String,experiences:List<LearningExperience>):List<ContinuumMemoryHit> {
            val model=TrainableSubwordEncoder.restore(bytes)
            val q=tokens(query)
            return experiences.asSequence().filter { e ->
                e.status in setOf(LearningStatus.CONFIRMED,LearningStatus.LEARNED,LearningStatus.OBSERVED,LearningStatus.REPLAY_PENDING) &&
                ((e.type==LearningExperienceType.USER_FACT && e.trust==LearningTrustClass.OWNER_CONFIRMED && e.provenance==LearningProvenance.USER_CONFIRMED && e.status!=LearningStatus.OBSERVED) ||
                 (e.type==LearningExperienceType.SOURCE_TEXT && e.trust==LearningTrustClass.SOURCE_OBSERVATION &&
                  e.provenance in setOf(LearningProvenance.REVIEWED_WEB_SOURCE,LearningProvenance.MENTOR_WIKIPEDIA) &&
                  e.sourceRef?.startsWith("https://")==true))
            }.mapNotNull { e ->
                val words=tokens(e.content)
                val overlap=q.count { it in words }
                if(overlap==0)null else {
                    val semantic=model.similarity(query.take(1000),e.content.take(1000))
                    ContinuumMemoryHit(e.id,e.content,if(e.type==LearningExperienceType.USER_FACT)"Bestätigte Nutzererinnerung" else "Unbestätigte Quellenbeobachtung",e.sourceRef,
                        overlap.toDouble()/maxOf(1,q.size)+.35*(semantic-.5),semantic)
                }
            }.sortedWith(compareByDescending<ContinuumMemoryHit>{it.score}.thenBy{it.id}).distinctBy { it.id }.take(3).toList()
        }
    }
}
