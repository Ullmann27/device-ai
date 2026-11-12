package at.heinz.deviceai.cognitive

import java.math.BigDecimal
import java.util.Locale

/**
 * Read-only shadow audit: never rewrites model text, owner memory, or tool permissions.
 * Unsupported or ambiguous arithmetic is UNVERIFIED, not a guessed error.
 */
internal object LocalAnswerQualityExperiment {
    enum class Result { PASS, FAIL, UNVERIFIED }
    data class Report(
        val arithmetic: Result,
        val standardGerman: Result,
        val brevityHint: Boolean,
        val expected: String?,
        val claimed: String?
    )

    private val claimedResult = Regex(
        """(?iu)\b(?:ergebnis|resultat)\b[^\n\r0-9−-]{0,80}([−-]?[0-9]+(?:[.,][0-9]+)?)"""
    )
    private val numberOnly = Regex("""^[−-]?[0-9]+(?:[.,][0-9]+)?$""")
    private val dialect = Regex(
        """(?iu)(?<!\p{L})(?:i\s+(?:hab|habe|bin|dir|dich|weiß|weiss)|ned|ka\s+(?:ahnung|zeit|lust)|gibt['’]s)(?!\p{L})"""
    )
    private val dialectRequested = Regex("""(?iu)\b(?:dialekt|mundart|wienerisch|steirisch|bayerisch)\b""")
    private val stepsRequested = Regex(
        """(?iu)\b(?:schritt(?:e|weise)?|rechenweg|ausführlich|herleitung|erkläre|erklären|warum)\b"""
    )
    private val quoted = Regex("""„[^“]{0,500}“|«[^»]{0,500}»|"[^"]{0,500}" """ .trimEnd())

    fun inspect(request: String, response: String): Report {
        val expectedValue = exactArithmetic(request)
        val claimedValue = explicitClaim(response)
        val arithmetic = when {
            expectedValue == null || claimedValue == null -> Result.UNVERIFIED
            expectedValue.compareTo(claimedValue) == 0 -> Result.PASS
            else -> Result.FAIL
        }
        val language = if (dialectRequested.containsMatchIn(request)) Result.UNVERIFIED
        else {
            val unquoted = quoted.replace(response, " ")
            if (dialect.containsMatchIn(unquoted)) Result.FAIL else Result.PASS
        }
        val brevity = expectedValue != null && !stepsRequested.containsMatchIn(request) &&
            response.length > 450
        return Report(arithmetic, language, brevity, expectedValue?.display(), claimedValue?.display())
    }

    private fun explicitClaim(response: String): BigDecimal? {
        val candidate = claimedResult.findAll(response).lastOrNull()?.groupValues?.getOrNull(1)
            ?: response.trim().removePrefix("**").removeSuffix("**")
                .trim().takeIf { numberOnly.matches(it) }
            ?: return null
        return runCatching { BigDecimal(candidate.replace('−', '-').replace(',', '.')) }.getOrNull()
    }

    private fun BigDecimal.display(): String =
        stripTrailingZeros().toPlainString().replace('.', ',')

    private fun exactArithmetic(request: String): BigDecimal? {
        val expression = request.trim().trim('$').trim().removeSuffix("=").trim()
        if (expression.length !in 3..160 ||
            !Regex("""[0-9\s+*/:×÷−–.,()\-]+""").matches(expression) ||
            !expression.any { it in "+-×÷*/:−–" }) return null
        return runCatching { Parser(expression).read() }.getOrNull()
    }

    private class Parser(text: String) {
        private val source = text.replace('×', '*').replace('÷', '/').replace(':', '/')
            .replace('−', '-').replace('–', '-')
        private var index = 0

        fun read(): BigDecimal {
            val value = sum()
            whitespace()
            require(index == source.length)
            return value
        }

        private fun whitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun take(char: Char): Boolean {
            whitespace()
            if (index >= source.length || source[index] != char) return false
            index++
            return true
        }

        private fun sum(): BigDecimal {
            var value = product()
            while (true) {
                value = when {
                    take('+') -> value.add(product())
                    take('-') -> value.subtract(product())
                    else -> return value
                }
            }
        }

        private fun product(): BigDecimal {
            var value = factor()
            while (true) {
                value = when {
                    take('*') -> value.multiply(factor())
                    take('/') -> value.divide(factor()) // Exact only; repeating decimal is unverified.
                    else -> return value
                }
            }
        }

        private fun factor(): BigDecimal {
            if (take('+')) return factor()
            if (take('-')) return factor().negate()
            if (take('(')) {
                val value = sum()
                require(take(')'))
                return value
            }
            whitespace()
            val start = index
            while (index < source.length && (source[index].isDigit() ||
                        source[index] == '.' || source[index] == ',')) index++
            require(index > start)
            val token = source.substring(start, index).replace(',', '.')
            require(token.count { it == '.' } <= 1)
            return BigDecimal(token)
        }
    }
}
