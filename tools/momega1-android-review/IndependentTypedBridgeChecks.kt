package at.heinz.deviceai

/** Exact production Kotlin, no API/transport stubs. Counts individual contract cases. */
fun main() {
    var count = 0
    fun test(name: String, body: () -> Unit) { body(); count++; println("PASS $name") }
    val task = ContinuumCodingTask("independent-pure", "d".repeat(40),
        ContinuumCodingTask.Kind.BUGFIX, "G".repeat(599) + "Z",
        listOf("src/one.mjs", "src/two.mjs", "src/three.mjs"),
        (1..8).map { "$it" + "C".repeat(298) + "Z" },
        ContinuumCodingTask.Chain.NODE, ContinuumCodingTask.Limits(3, 24000))
    val json = task.canonicalJson()
    val hash = task.fingerprint()
    val auth = ContinuumCodingTask.Authorization(task, hash)
    test("valid complete bounded authorization") { check(ContinuumTypedCodingBridge.parseBoundAuthorization(json, hash, task.id, task.sourceCommit) != null) }
    test("maximal complete execution review") {
        val review = checkNotNull(ContinuumTypedCodingBridge.executionReview(auth))
        for (field in task.allowedPaths + task.acceptance + listOf(task.id, task.sourceCommit, task.goal, task.kind.name, "node", "24000", hash)) check(review.contains(field)) { "Omitted $field" }
        check(review.contains("Maximale Modellaufrufe: 3"))
    }
    val mutations = listOf(
        task.copy(id="replacement-task"), task.copy(sourceCommit="e".repeat(40)),
        task.copy(goal="Replacement goal"), task.copy(kind=ContinuumCodingTask.Kind.FEATURE),
        task.copy(allowedPaths=listOf("src/replaced.mjs")), task.copy(acceptance=listOf("Replacement criterion")),
        task.copy(limits=ContinuumCodingTask.Limits(1, 24000)), task.copy(limits=ContinuumCodingTask.Limits(3, 2048)))
    for ((i, changed) in mutations.withIndex()) test("snapshot field mutation $i with old digest") {
        check(ContinuumTypedCodingBridge.parseBoundAuthorization(changed.canonicalJson(),hash,task.id,task.sourceCommit)==null)
    }
    test("chain mutation with old digest") { check(ContinuumTypedCodingBridge.parseAuthorization(json.replace("\"chain\":\"node\"", "\"chain\":\"android\""),hash)==null) }
    for ((name, bound) in listOf("missing JSON" to arrayOf(null,hash,task.id,task.sourceCommit),
        "missing hash" to arrayOf(json,null,task.id,task.sourceCommit), "missing ID" to arrayOf(json,hash,null,task.sourceCommit),
        "missing source" to arrayOf(json,hash,task.id,null), "wrong ID" to arrayOf(json,hash,"replacement-task",task.sourceCommit),
        "wrong source" to arrayOf(json,hash,task.id,"e".repeat(40)))) test(name) {
        check(ContinuumTypedCodingBridge.parseBoundAuthorization(bound[0],bound[1],bound[2],bound[3])==null)
    }
    test("noncanonical identical data rejected") { check(ContinuumTypedCodingBridge.parseAuthorization(" $json", hash)==null) }
    test("model count numeric overflow rejected") { check(ContinuumTypedCodingBridge.parseAuthorization(json.replace("\"maxModelCalls\":3", "\"maxModelCalls\":4294967299"),hash)==null) }
    test("fixed prepare command carries exact canonical bytes") {
        val command = ContinuumTypedCodingBridge.commandPrepare(auth)
        check(command.executable==ContinuumMobileBuildPlan.NODE_EXECUTABLE)
        check(command.args.size==4 && command.args[1]=="prepare" && command.args[3]==hash)
        check(String(java.util.Base64.getUrlDecoder().decode(command.args[2]),Charsets.UTF_8)==json)
    }
    fun output() = """{"schema":"continuum-typed-coding-bridge","version":1,"operation":"prepare","id":"${task.id}","status":"PREPARED_NOT_EXECUTED","taskSha256":"$hash","sourceCommit":"${task.sourceCommit}","ccc1TaskId":"t-1111111111111111","replayed":false,"approvedForMerge":false,"installed":false,"deviceVerified":false}"""
    fun evaluate(s:String?, exit:Int?=0, error:Int? = -1) = ContinuumTypedCodingBridge.evaluate(ContinuumTypedCodingBridge.Operation.PREPARE,task.id,hash,exit,error,s,task.sourceCommit)
    test("valid callback returns bound CCC1 target only") { check(evaluate(output()).ccc1TaskId=="t-1111111111111111") }
    for ((name,s) in listOf("wrong task" to output().replace(task.id,"replacement-task"),
        "wrong source" to output().replace(task.sourceCommit,"e".repeat(40)),
        "wrong digest" to output().replace(hash,"f".repeat(64)),
        "wrong operation" to output().replace("\"prepare\"","\"run\""),
        "malformed journal" to output().replace("t-1111111111111111","arbitrary-id"),
        "claims device verification" to output().replace("\"deviceVerified\":false","\"deviceVerified\":true"),
        "duplicate key" to output().replace("\"version\":1","\"version\":1,\"version\":1"))) test(name) { check(!evaluate(s).prepared) }
    test("nonzero exit cannot prepare") { check(!evaluate(output(),1).prepared) }
    test("missing Termux success cannot prepare") { check(!evaluate(output(),error=null).prepared) }
    test("missing/truncated callback cannot prepare") { check(!evaluate(null).prepared) }
    println("TOTAL cases=$count passed=$count failed=0 skipped=0")
}
