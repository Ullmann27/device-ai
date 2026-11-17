package at.heinz.deviceai.learning

import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * A bounded, optional query-retrieval fallback. This is not an auto-correcting
 * keyboard, an emotion detector, or an owner-memory writer.
 *
 * Vocabulary is assembled only from already eligible local memory evidence.
 * No web harvesting, permanent lexicon or silent correction of commands.
 */
internal object ContinuumOrthographyProbe {
    private val words=Regex("[\\p{L}]{3,24}")
    private val tokens=Regex("[\\p{L}\\p{N}_-]+|[^\\p{L}\\p{N}_-]+")
    private val keyboard=listOf("qwertzuiopü","asdfghjklöä","yxcvbnm")
    private val coordinates=buildMap<Char,Pair<Double,Double>> {
        keyboard.forEachIndexed { row,line ->
            line.forEachIndexed { col,ch ->
                put(ch,(col.toDouble() + if(row==0)0.0 else if(row==1)0.3 else 0.8) to row.toDouble())
            }
        }
    }

    /** Conservative single-step keyboard/deletion/adjacent-transposition score. */
    private fun distance(a:String,b:String):Double {
        if(abs(a.length-b.length)>1)return Double.POSITIVE_INFINITY
        val n=a.length
        val m=b.length
        val dp=Array(n+1){DoubleArray(m+1)}
        for(i in 0..n)dp[i][0]=i.toDouble()
        for(j in 0..m)dp[0][j]=j.toDouble()
        for(i in 1..n)for(j in 1..m) {
            val same=a[i-1]==b[j-1]
            val ac=coordinates[a[i-1]]
            val bc=coordinates[b[j-1]]
            val cost=if(same)0.0 else if(ac!=null && bc!=null &&
                hypot(ac.first-bc.first,ac.second-bc.second)<=1.45)0.35 else 1.0
            var best=min(min(dp[i-1][j]+1.0,dp[i][j-1]+1.0),dp[i-1][j-1]+cost)
            if(i>1&&j>1&&a[i-1]==b[j-2]&&a[i-2]==b[j-1])
                best=min(best,dp[i-2][j-2]+0.65)
            dp[i][j]=best
        }
        return dp[n][m]
    }

    data class Proposal(val original:String,val query:String,val replacements:Int)

    fun suggest(query:String,evidenceTexts:List<String>):Proposal? {
        if(query.length !in 6..320 || evidenceTexts.size>256 ||
            query.any { it.code<32 || it.code==127 })return null
        val dictionary=HashSet<String>()
        for(text in evidenceTexts) {
            if(text.length>2048)continue
            for(match in words.findAll(text.lowercase(Locale.GERMAN))) {
                val word=match.value
                if(word.length>=6)dictionary.add(word)
                if(dictionary.size>2048)return null
            }
        }
        if(dictionary.isEmpty())return null
        val parts=tokens.findAll(query).map { it.value }.toList()
        var corrections=0
        val revised=parts.map { token ->
            val lower=token.lowercase(Locale.GERMAN)
            if(!token.matches(words) || lower.length<6 || lower in dictionary ||
                token.any(Char::isDigit) || token.contains('_') || token.contains('-') ||
                token.drop(1)!=token.drop(1).lowercase(Locale.GERMAN))return@map token
            var winner:String?=null
            var best=Double.POSITIVE_INFINITY
            var second=Double.POSITIVE_INFINITY
            for(candidate in dictionary) {
                if(abs(candidate.length-lower.length)>1)continue
                val d=distance(lower,candidate)
                if(d>1.0)continue
                if(d<best) {
                    second=best
                    best=d
                    winner=candidate
                } else if(d<second)second=d
            }
            if(winner==null || second-best<0.40)return@map token
            corrections++
            if(token.firstOrNull()?.isUpperCase()==true)
                winner.replaceFirstChar { it.titlecase(Locale.GERMAN) } else winner
        }.joinToString("")
        if(corrections!=1 || revised==query)return null
        return Proposal(query,revised,corrections)
    }
}
