package at.heinz.deviceai.learning

/** Public excerpt, never a trusted fact or a device instruction. */
data class MentorSource(val subject:String,val url:String,val sha:String,val excerpt:String)

/** Shared pure-JVM validation used by the Android notebook and learning core. */
object MentorSourceValidation {
    fun checkSource(x:MentorSource) {
        require(x.subject.length in 3..80 && x.url.startsWith("https://de.wikipedia.org/wiki/") &&
            x.url.length<=420 && x.sha.matches(Regex("[0-9a-f]{64}")) &&
            x.excerpt.length in 20..400)
    }
}
