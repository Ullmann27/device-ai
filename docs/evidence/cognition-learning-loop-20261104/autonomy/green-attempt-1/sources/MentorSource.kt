package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.HttpReadOnlyResearchProvider
import java.net.URI

/** Public excerpt, never a trusted fact or a device instruction. */
data class MentorSource(val subject:String,val url:String,val sha:String,val excerpt:String)

/** Shared pure-JVM validation used by the Android notebook and learning core. */
object MentorSourceValidation {
    fun checkSource(x:MentorSource) {
        require(x.subject.length in 3..80 && x.url.length in 15..420 &&
            x.sha.matches(Regex("[0-9a-f]{64}")) && x.excerpt.length in 20..400)
        HttpReadOnlyResearchProvider.ResearchUrlValidator.validateSyntax(URI(x.url))
    }
}
