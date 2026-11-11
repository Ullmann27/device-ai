package at.heinz.deviceai.learning.lm

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64

object LmSemanticTokenizerAudit {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2)
        val bytes = Files.readAllBytes(Path.of(args[0]))
        val sha = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
        require(sha == "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f")
        val tok = LmBpeTokenizer.deserialize(bytes)
        val lines = Files.readAllLines(Path.of(args[1]))
        require(lines.first() == "id\tpromptBase64\tprefixTokens")
        val seen = HashSet<String>()
        for (line in lines.drop(1)) {
            val fields = line.split('\t')
            require(fields.size == 3 && seen.add(fields[0]))
            val prompt = String(Base64.getDecoder().decode(fields[1]), Charsets.UTF_8)
            val expected = fields[2].split(',').map(String::toInt).toIntArray()
            val actual = intArrayOf(tok.bos) + tok.encode("Auftrag: $prompt\n")
            require(actual.contentEquals(expected)) { "native parity mismatch: ${fields[0]}" }
            require(tok.decode(actual) == "Auftrag: $prompt\n")
            require(actual.size + 4 <= 256)
            println("ROW\tid=${fields[0]}\tprefixTokens=${actual.size}\tparity=true\troundTrip=true")
        }
        for (label in 0..3) require(tok.encode(label.toString()).contentEquals(intArrayOf(48 + label)))
        require(tok.bos == 510 && tok.eos == 511)
        println("RESULT\trows=${seen.size}\tparity=true\tlabels=true\tbos=510\teos=511\tupdates=0")
    }
}
