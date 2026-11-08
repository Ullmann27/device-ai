package at.heinz.deviceai.cognitive

/**
 * Shadow-only verifier over the compact state telegram carried by LocalGroundedRecall.
 * It never rewrites output and never upgrades a claim to truth.
 */
internal object StateTelegramAnswerShadow {
    enum class Status {
        PASS_OBSERVED,
        CONTRADICTION_OBSERVED,
        CONFLICT_IN_EVIDENCE,
        INSUFFICIENT_COVERAGE,
        NO_STATE
    }

    data class Assessment(
        val status: Status,
        val checkedClaims: Int,
        val contradictions: Int,
        val conflicts: Int,
        val passed: Int,
        val reasons: List<String>
    )

    fun assess(answer: String, telegram: String?): Assessment {
        if (telegram.isNullOrBlank()) {
            return Assessment(Status.NO_STATE,0,0,0,0,emptyList())
        }
        val claims=parse(telegram)
        if (claims.isEmpty()) {
            return Assessment(Status.NO_STATE,0,0,0,0,emptyList())
        }

        var contradictions=0
        var conflicts=0
        var passed=0
        val reasons=mutableListOf<String>()

        claims.forEach { claim ->
            if (claim.conflictValues.isNotEmpty()) {
                conflicts++
                reasons += "CONFLICT:" + claim.subject + "." + claim.predicate
                return@forEach
            }

            val forbidden = claim.forbiddenValues.filter(String::isNotBlank).toSet()
            val candidate = relevantSentence(answer, claim.value, forbidden) ?: return@forEach
            val result=AnswerClaimVerifier.verifyFocusedSentence(
                candidate,
                AnswerClaimVerifier.GroundedClaim(
                    subject=claim.subject,
                    predicate=claim.predicate,
                    expectedValue=claim.value,
                    forbiddenValues=forbidden,
                    sourceIds=claim.sourceIds.ifEmpty { setOf("state-telegram") }
                )
            )
            when(result.verdict) {
                AnswerClaimVerifier.Verdict.PASS -> passed++
                AnswerClaimVerifier.Verdict.CONTRADICTION -> {
                    contradictions++
                    reasons += result.reason + ":" + claim.subject + "." + claim.predicate
                }
                AnswerClaimVerifier.Verdict.UNSUPPORTED_CAUSALITY,
                AnswerClaimVerifier.Verdict.INSUFFICIENT_EVIDENCE -> Unit
            }
        }

        val status=when {
            contradictions>0 -> Status.CONTRADICTION_OBSERVED
            conflicts>0 -> Status.CONFLICT_IN_EVIDENCE
            passed>0 -> Status.PASS_OBSERVED
            else -> Status.INSUFFICIENT_COVERAGE
        }
        return Assessment(status,claims.size,contradictions,conflicts,passed,reasons.take(8))
    }

    private data class Claim(
        val subject:String,
        val predicate:String,
        val value:String,
        val forbiddenValues:List<String>,
        val conflictValues:List<String>,
        val sourceIds:Set<String>
    )

    private fun parse(telegram:String):List<Claim> = telegram.lineSequence()
        .drop(1)
        .mapNotNull(::parseLine)
        .take(32)
        .toList()

    private fun parseLine(line:String):Claim? {
        if(line.isBlank()||line.length>2_000)return null
        val fields=splitEscaped(line,'|').mapNotNull { token->
            val pair=splitFirstUnescaped(token,'=')?:return@mapNotNull null
            unescape(pair.first) to unescape(pair.second)
        }.toMap()
        val subject=fields["S"]?.takeIf(String::isNotBlank)?:return null
        val predicate=fields["P"]?.takeIf(String::isNotBlank)?:return null
        val conflicts=fields["CONFLICT"]?.let { splitEscaped(it,',').map(::unescape) }.orEmpty()
        val value=fields["V"].orEmpty()
        if(value.isBlank()&&conflicts.isEmpty())return null
        val forbidden=fields["NOT"]?.let { listOf(it) }.orEmpty()
        val sourceIds=fields["SRC"]?.let {
            splitEscaped(it,',').map(::unescape).filter(String::isNotBlank).toSet()
        }.orEmpty()
        return Claim(subject,predicate,value,forbidden,conflicts,sourceIds)
    }

    private fun relevantSentence(answer:String, expected:String, forbidden:Set<String>):String? {
        val candidates=answer.split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map(String::trim).filter(String::isNotBlank)
        return candidates.firstOrNull { sentence->
            containsFolded(sentence,expected) || forbidden.any { containsFolded(sentence,it) }
        }
    }

    private fun containsFolded(text:String,value:String):Boolean {
        if(value.isBlank())return false
        val a=text.lowercase(java.util.Locale.GERMAN)
        val b=value.lowercase(java.util.Locale.GERMAN)
        return a.contains(b)
    }

    private fun splitEscaped(value:String,delimiter:Char):List<String> {
        val out=mutableListOf<String>()
        val current=StringBuilder()
        var escaped=false
        value.forEach { c->
            when {
                escaped -> { current.append('\\').append(c);escaped=false }
                c=='\\' -> escaped=true
                c==delimiter -> {out+=current.toString();current.setLength(0)}
                else -> current.append(c)
            }
        }
        if(escaped)current.append('\\')
        out+=current.toString()
        return out
    }

    private fun splitFirstUnescaped(value:String,delimiter:Char):Pair<String,String>? {
        var escaped=false
        value.forEachIndexed { index,c->
            when {
                escaped -> escaped=false
                c=='\\' -> escaped=true
                c==delimiter -> return value.substring(0,index) to value.substring(index+1)
            }
        }
        return null
    }

    private fun unescape(value:String):String {
        val out=StringBuilder(value.length)
        var escaped=false
        value.forEach { c->
            if(escaped){out.append(c);escaped=false}
            else if(c=='\\')escaped=true
            else out.append(c)
        }
        if(escaped)out.append('\\')
        return out.toString()
    }
}
