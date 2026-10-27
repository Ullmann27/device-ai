package at.heinz.deviceai.cognitive

import at.heinz.deviceai.BootstrapBrain
import at.heinz.deviceai.BootstrapKind
import java.io.File
import java.util.Base64

/** Offline directive-completion experiment, not LLM response quality. Sources
 * are public synthetic labels; no owner conversation is read or emitted.
 */
object ConversationProtocolMain {
    @JvmStatic fun main(args:Array<String>) {
        val corpus=File(args[0]);val output=File(args[1]);val context=LocalConversationContext()
        var baseline=0;var candidate=0;var falseScope=0;var total=0
        val rows=mutableListOf<String>()
        for(row in corpus.readLines().filter { it.isNotBlank() }) {
            val p=row.split('\t');val input=String(Base64.getDecoder().decode(p[0]),Charsets.UTF_8)
            val expected=p[1];val focus=p[2].takeUnless { it=="-" }?.let { String(Base64.getDecoder().decode(it),Charsets.UTF_8) }
            val before=BootstrapBrain.decide(input,emptyList())
            // Existing offline dispatcher has no explicit scope/report command.
            // General LLM replies may do better, but are NOT_RUN in this comparison.
            val baselineKind=if(before.kind==BootstrapKind.REPLY || before.kind==BootstrapKind.REMEMBER) "PASS_THROUGH" else "OTHER"
            val after=context.inspectRequest("prospective-synthetic",input)
            val kind=after?.kind?.name ?: "PASS_THROUGH"
            if(baselineKind==expected)baseline++
            if(kind==expected && (focus==null || after?.focus==focus))candidate++
            if(kind=="SCOPE_UPDATED" && (expected!="SCOPE_UPDATED" || after?.focus!=focus))falseScope++
            rows += "case=$total\texpected=$expected\tbaseline=$baselineKind\tcandidate=$kind"
            total++
        }
        output.writeText(rows.joinToString("\n",postfix="\n")+
            "total=$total\nbaselineCorrect=$baseline\ncandidateCorrect=$candidate\nfalseScope=$falseScope\nllmInvocations=0\n"+
            "ownerFactsWritten=0\nweightsUpdated=false\nclassification=OFFLINE_DIRECTIVE_HANDLING_NOT_NEURAL_GENERALIZATION\n")
        println(output.readText())
        check(falseScope==0 && candidate==total) { "Prospective directive gate rejected candidate." }
    }
}
