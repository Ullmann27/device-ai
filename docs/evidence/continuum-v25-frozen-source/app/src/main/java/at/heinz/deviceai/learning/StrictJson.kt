package at.heinz.deviceai.learning

/**
 * Strict, bounded RFC 8259 reader for package manifests.
 *
 * Pure JVM (no android org.json), so verification behaves identically in unit
 * tests and on the device. Unlike lenient parsers it rejects duplicate keys,
 * comments, single quotes, leading zeros, unescaped control characters, lone
 * surrogates and trailing content: two parsers can never disagree about which
 * value a signed or hashed manifest contains.
 *
 * Objects become [Map] (insertion ordered), arrays [List], integers [Long],
 * other numbers [Double], plus [String], [Boolean] and null.
 */
object StrictJson {
    const val MAX_INPUT_CHARS=262_144
    const val MAX_DEPTH=16
    const val MAX_CONTAINER_ITEMS=4096

    fun parse(text:String,maxChars:Int=MAX_INPUT_CHARS):Any? {
        require(text.length<=maxChars) { "JSON zu groß" }
        val reader=Reader(text)
        reader.skipWhitespace()
        val value=reader.value(0)
        reader.skipWhitespace()
        require(reader.atEnd()) { "Unerwartete Zeichen nach JSON-Wert" }
        return value
    }

    fun parseObject(text:String):Map<String,Any?> {
        val value=parse(text)
        require(value is Map<*,*>) { "JSON-Objekt erwartet" }
        @Suppress("UNCHECKED_CAST")
        return value as Map<String,Any?>
    }

    private class Reader(private val s:String) {
        private var i=0

        fun atEnd()=i==s.length

        fun skipWhitespace() {
            while(i<s.length && (s[i]==' ' || s[i]=='\n' || s[i]=='\r' || s[i]=='\t'))i++
        }

        fun value(depth:Int):Any? {
            require(depth<=MAX_DEPTH) { "JSON zu tief verschachtelt" }
            require(i<s.length) { "Unerwartetes Ende" }
            return when(s[i]) {
                '{' -> obj(depth+1)
                '[' -> array(depth+1)
                '"' -> string()
                't' -> literal("true",true)
                'f' -> literal("false",false)
                'n' -> literal("null",null)
                else -> number()
            }
        }

        private fun literal(word:String,result:Any?):Any? {
            require(s.startsWith(word,i)) { "Ungültiges JSON-Literal" }
            i+=word.length
            return result
        }

        private fun obj(depth:Int):Map<String,Any?> {
            i++
            val result=LinkedHashMap<String,Any?>()
            skipWhitespace()
            if(peek()=='}') { i++;return result }
            while(true) {
                skipWhitespace()
                require(peek()=='"') { "Objektschlüssel erwartet" }
                val key=string()
                require(key !in result) { "Doppelter JSON-Schlüssel: "+key.take(40) }
                skipWhitespace()
                require(peek()==':') { "':' erwartet" }
                i++
                skipWhitespace()
                result[key]=value(depth)
                require(result.size<=MAX_CONTAINER_ITEMS) { "JSON-Objekt zu groß" }
                skipWhitespace()
                when(peek()) {
                    ',' -> i++
                    '}' -> { i++;return result }
                    else -> throw IllegalArgumentException("',' oder '}' erwartet")
                }
            }
        }

        private fun array(depth:Int):List<Any?> {
            i++
            val result=ArrayList<Any?>()
            skipWhitespace()
            if(peek()==']') { i++;return result }
            while(true) {
                skipWhitespace()
                result+=value(depth)
                require(result.size<=MAX_CONTAINER_ITEMS) { "JSON-Liste zu groß" }
                skipWhitespace()
                when(peek()) {
                    ',' -> i++
                    ']' -> { i++;return result }
                    else -> throw IllegalArgumentException("',' oder ']' erwartet")
                }
            }
        }

        private fun string():String {
            i++
            val out=StringBuilder()
            while(true) {
                require(i<s.length) { "Unterminierte Zeichenkette" }
                val c=s[i++]
                when {
                    c=='"' -> break
                    c=='\\' -> {
                        require(i<s.length) { "Unterminierte Escape-Sequenz" }
                        when(val e=s[i++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> out.append(unicodeEscape())
                            else -> throw IllegalArgumentException("Ungültige Escape-Sequenz \\"+e)
                        }
                    }
                    c<' ' -> throw IllegalArgumentException("Steuerzeichen in Zeichenkette")
                    else -> out.append(c)
                }
            }
            val result=out.toString()
            // Lone surrogates make UTF-8 hashing ambiguous across runtimes.
            var k=0
            while(k<result.length) {
                val ch=result[k]
                if(Character.isHighSurrogate(ch)) {
                    require(k+1<result.length && Character.isLowSurrogate(result[k+1])) { "Ungültiges Surrogat" }
                    k+=2
                } else {
                    require(!Character.isLowSurrogate(ch)) { "Ungültiges Surrogat" }
                    k++
                }
            }
            return result
        }

        private fun unicodeEscape():Char {
            require(i+4<=s.length) { "Unvollständige Unicode-Escape-Sequenz" }
            var code=0
            repeat(4) {
                // ASCII hex only: Character.digit would also accept non-ASCII digits
                val digit=when(val ch=s[i++]) {
                    in '0'..'9' -> ch-'0'
                    in 'a'..'f' -> ch-'a'+10
                    in 'A'..'F' -> ch-'A'+10
                    else -> -1
                }
                require(digit>=0) { "Ungültige Unicode-Escape-Sequenz" }
                code=code*16+digit
            }
            return code.toChar()
        }

        private fun number():Any {
            val start=i
            if(peek()=='-')i++
            require(i<s.length && s[i] in '0'..'9') { "Ungültiger JSON-Wert" }
            if(s[i]=='0') {
                i++
                require(i>=s.length || s[i] !in '0'..'9') { "Führende Null" }
            } else while(i<s.length && s[i] in '0'..'9')i++
            var integral=true
            if(i<s.length && s[i]=='.') {
                integral=false;i++
                require(i<s.length && s[i] in '0'..'9') { "Ziffer nach Dezimalpunkt erwartet" }
                while(i<s.length && s[i] in '0'..'9')i++
            }
            if(i<s.length && (s[i]=='e' || s[i]=='E')) {
                integral=false;i++
                if(i<s.length && (s[i]=='+' || s[i]=='-'))i++
                require(i<s.length && s[i] in '0'..'9') { "Exponent erwartet" }
                while(i<s.length && s[i] in '0'..'9')i++
            }
            val literal=s.substring(start,i)
            require(literal.length<=64) { "Zahl zu lang" }
            if(integral)literal.toLongOrNull()?.let { return it }
            val parsed=literal.toDouble()
            require(parsed.isFinite()) { "Zahl außerhalb des Wertebereichs" }
            return parsed
        }

        private fun peek():Char? = if(i<s.length)s[i] else null
    }
}

/** Typed, fail-closed accessors for manifest fields. */
internal fun Map<String,Any?>.requiredString(key:String,maxLength:Int=1024):String {
    val value=this[key]
    require(value is String && value.length<=maxLength) { "Feld $key fehlt oder ist ungültig" }
    return value
}

internal fun Map<String,Any?>.optionalString(key:String,maxLength:Int=1024):String? {
    if(!containsKey(key) || this[key]==null)return null
    return requiredString(key,maxLength)
}

internal fun Map<String,Any?>.requiredLong(key:String,range:LongRange):Long {
    val value=this[key]
    require(value is Long && value in range) { "Feld $key fehlt oder ist ungültig" }
    return value
}

internal fun Map<String,Any?>.optionalLong(key:String,range:LongRange):Long? {
    if(!containsKey(key) || this[key]==null)return null
    return requiredLong(key,range)
}
