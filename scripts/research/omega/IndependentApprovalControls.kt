package at.heinz.deviceai.cognitive

fun main() {
    var passed = 0
    fun verify(name: String, test: () -> Unit) {
        test()
        passed++
        println("PASS $name")
    }
    verify("assent_is_not_a_web_request") {
        check(WebToolRequest.fromChat("Ja, genau.", false) == null)
    }
    verify("negation_overrides_armed_web") {
        check(WebToolRequest.fromChat("Recherchiere Animationen ohne Internet.", true) == null)
    }
    verify("stale_callback_cannot_approve_replacement") {
        val consent = InternetConsentPolicy().apply { setMode(InternetAccessMode.ASK) }
        val gate = ApprovedWebToolGate(consent)
        val first = gate.prepare(WebToolRequest.Search("Animation A"))!!
        val second = gate.prepare(WebToolRequest.Search("Animation B"))!!
        var callbacks = 0
        check(!gate.completeApproval(first, ResearchMode.QUICK, true) { callbacks++ })
        check(callbacks == 0)
        check(gate.completeApproval(second, ResearchMode.QUICK, true) { check(it!!.question == "Animation B"); callbacks++ })
        check(callbacks == 1)
    }
    verify("grant_rejects_changed_tool_argument") {
        val consent = InternetConsentPolicy().apply { setMode(InternetAccessMode.ASK) }
        val gate = ApprovedWebToolGate(consent)
        val a = WebToolRequest.Search("Animation A")
        val session = gate.approve(gate.prepare(a)!!, ResearchMode.QUICK)!!
        var actions = 0
        check(runCatching { session.execute(WebToolRequest.Search("Animation B")) { actions++ } }.isFailure)
        check(actions == 0)
    }
    verify("one_shot_cannot_replay") {
        val consent = InternetConsentPolicy().apply { setMode(InternetAccessMode.ASK) }
        val gate = ApprovedWebToolGate(consent)
        val call = WebToolRequest.Search("Animation C")
        val session = gate.approve(gate.prepare(call)!!, ResearchMode.QUICK)!!
        var actions = 0
        session.execute(call) { actions++ }
        check(runCatching { session.execute(call) { actions++ } }.isFailure)
        check(actions == 1)
    }
    verify("offline_denies_grant") {
        val consent = InternetConsentPolicy().apply { setMode(InternetAccessMode.OFF) }
        val gate = ApprovedWebToolGate(consent)
        check(gate.prepare(WebToolRequest.Search("Animation D")) == null)
        check(gate.authorizeAlwaysOn(WebToolRequest.Search("Animation D"), ResearchMode.QUICK) == null)
    }
    verify("cancel_prevents_execution") {
        val consent = InternetConsentPolicy().apply { setMode(InternetAccessMode.ASK) }
        val gate = ApprovedWebToolGate(consent)
        val call = WebToolRequest.Search("Animation E")
        val session = gate.approve(gate.prepare(call)!!, ResearchMode.QUICK)!!
        session.cancel()
        var actions = 0
        check(runCatching { session.execute(call) { actions++ } }.isFailure)
        check(actions == 0)
    }
    verify("revoked_result_is_unauthorized") {
        val consent = InternetConsentPolicy().apply { setMode(InternetAccessMode.ASK) }
        val gate = ApprovedWebToolGate(consent)
        val call = WebToolRequest.Search("Animation F")
        val session = gate.approve(gate.prepare(call)!!, ResearchMode.QUICK)!!
        session.execute(call) { "synthetic observation" }
        consent.setMode(InternetAccessMode.OFF)
        check(!session.isAuthorized())
    }
    println("$passed/8 PASS; real gate, synthetic actions, no network/model/device")
}
