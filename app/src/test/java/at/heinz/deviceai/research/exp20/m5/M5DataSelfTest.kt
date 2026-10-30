package at.heinz.deviceai.research.exp20.m5

import java.io.File
import java.nio.file.Files

/** Dependency-free Kotlin host smoke and negative controls for M5 DEV/VAL only. */
object M5DataSelfTest {
    @JvmStatic fun main(args: Array<String>) {
        var tests = 0
        fun pass(name: String, block: () -> Unit) { block(); tests++; println("PASS $name") }
        val rows = M5Data.generate()
        pass("groups-256-rows-2304") { check(rows.size == 256 * 9); M5Data.validate(rows) }
        pass("deterministic-generation") { check(rows == M5Data.generate()) }
        pass("split-kind-balanced") {
            for (split in M5Data.Split.values()) for (kind in M5Data.Kind.values())
                check(rows.count { it.split == split && it.kind == kind && it.role == "QUERY" } == 64)
        }
        pass("DEV-VAL-memories-disjoint") {
            val dev = rows.filter { it.split == M5Data.Split.DEV && it.role != "QUERY" }.map { it.text }.toSet()
            val valid = rows.filter { it.split == M5Data.Split.VAL && it.role != "QUERY" }.map { it.text }.toSet()
            check((dev intersect valid).isEmpty())
        }
        pass("no-P3-phrases") {
            check(rows.none { it.text.contains("zum Wochenausklang") || it.text.contains("unten im Haus") })
        }
        pass("target-not-fixed-candidate-position") {
            for (split in M5Data.Split.values()) for (kind in M5Data.Kind.values()) {
                val groups = rows.groupBy { it.group }.values.filter { it[0].split == split && it[0].kind == kind }
                val positions = groups.map { it.filter { x -> x.role != "QUERY" }.indexOfFirst { x -> x.role == "TRUE" } }
                check((0..7).all { i -> positions.count { it == i } == 8 })
            }
        }
        pass("no-duplicate-memories") {
            check(rows.groupBy { it.group }.values.all { g -> g.filter { it.role != "QUERY" }.map { it.text }.distinct().size == 8 })
        }
        pass("query-blind-lexical-VAL") {
            val (hits, total) = M5Data.blindLexicalWins(rows, M5Data.Split.VAL)
            println("VAL blind lexical strict top1=$hits/$total")
            check(total == 128 && hits.toDouble() / total <= 0.175)
        }
        val dir = Files.createTempDirectory("m5-wp1-").toFile()
        try {
            val file = File(dir, "dev-val.tsv")
            pass("write-read-roundtrip") { M5Data.write(rows, file); check(M5Data.read(file) == rows) }
            pass("stable-file-sha") {
                val sha = M5Data.sha256(file)
                M5Data.write(rows, file)
                check(M5Data.sha256(file) == sha)
                println("DATA_SHA256=$sha")
            }
            pass("reject-corrupt-gold") {
                val broken = rows.toMutableList()
                val idx = broken.indexOfFirst { it.role == "TRUE" }
                broken[idx] = broken[idx].copy(role = "CANDIDATE")
                check(runCatching { M5Data.validate(broken) }.isFailure)
            }
            pass("reject-malformed-TSV") {
                file.writeText("wrong\theader\n")
                check(runCatching { M5Data.read(file) }.isFailure)
            }
        } finally { dir.deleteRecursively() }
        println("M5_WP1_TESTS_PASS=$tests; CRE1_BLIND=NOT_RUN; SEALED=UNOPENED")
    }
}
