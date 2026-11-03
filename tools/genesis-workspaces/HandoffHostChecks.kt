package at.heinz.deviceai.chief

import at.heinz.deviceai.routines.AgentMailbox
import at.heinz.deviceai.routines.AgentMessage
import at.heinz.deviceai.routines.AgentPriority
import org.json.JSONObject
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/** Software contract harness. Fake authority/port observations are never model or OS evidence. */
object HandoffHostChecks {
    private fun ids(value: org.json.JSONArray) = (0 until value.length()).map(value::getString)
    @JvmStatic fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            "admission-json" -> { println(evaluate(JSONObject(readln()))); return }
            "seed" -> { val store=ChiefBotProfileStore(java.io.File(args[1])); println(JSONObject().put("count",store.seedDefaults(20).size)); return }
            "get" -> {
                val p=ChiefBotProfileStore(java.io.File(args[1])).get(args[2])
                println(if(p==null) "null" else JSONObject().put("agentId",p.agentId).put("workspaceId",p.workspaceId)
                    .put("resourceBudget",JSONObject().put("maxModelCalls",p.resourceBudget.maxModelCalls)
                        .put("maxToolCalls",p.resourceBudget.maxToolCalls).put("deadlineMillis",p.resourceBudget.deadlineMillis)
                        .put("workspaceBytes",p.resourceBudget.workspaceBytes)))
                return
            }
        }
        tests()
    }

    private fun evaluate(f: JSONObject): JSONObject {
        // Current production API has no owner budget editor. Do not pretend that a
        // custom fixture budget was persisted, or count its setup error as denial.
        val fixtureProfiles=f.getJSONArray("profiles")
        for(i in 0 until fixtureProfiles.length()) {
            val budget=fixtureProfiles.getJSONObject(i).optJSONObject("resourceBudget") ?: continue
            require(budget.optInt("maxModelCalls",1)==1 && budget.optInt("maxToolCalls",4)==4 &&
                budget.optLong("deadlineMillis",120000)==120000L) { "NOT_TESTABLE_CUSTOM_PROFILE_BUDGET" }
        }
        val root=Files.createTempDirectory("genesis-admission-fixture").toFile()
        try {
            val store=ChiefBotProfileStore(root)
            val profiles=f.getJSONArray("profiles")
            for(i in 0 until profiles.length()) {
                val value=profiles.getJSONObject(i); val id=value.getString("agentId")
                val p=store.create(id,id,"Synthetic role","Synthetic style")
                require(p.workspaceId==value.getString("workspaceId"))
            }
            val m=f.getJSONObject("message"); val c=f.getJSONObject("canonical")
            val senders=c.getJSONObject("stageSenders").let { obj -> obj.keys().asSequence()
                .associate { ChiefBotStage.valueOf(it) to obj.getString(it) } }
            val handoff=ChiefBotHandoff(m.getInt("schemaVersion"),m.getString("messageId"),m.getString("fromAgentId"),
                m.getString("toAgentId"),ChiefBotStage.valueOf(m.getString("stage")),m.getString("taskId"),
                m.getString("jobId"),m.getString("sourceCommit"),m.getString("attemptId"),m.getString("requestMessageId"),
                m.getString("requestHash"),m.getString("grantFingerprint"),m.getString("persistentWorkspaceId"),
                m.getLong("expiresAt"),ids(m.getJSONArray("requestedTools")).toSet(),m.getInt("modelCalls"),m.getInt("toolCalls"),
                ids(m.getJSONArray("evidenceRefs")))
            val current=ChiefBotTaskAdmission(c.getString("taskId"),c.getString("jobId"),c.getString("sourceCommit"),
                c.getString("attemptId"),c.getString("requestMessageId"),c.getString("requestHash"),c.getString("grantFingerprint"),
                c.getBoolean("currentGrant"),c.getBoolean("terminal"),c.getBoolean("unknownEffect"),c.getString("builderId"),
                c.getString("verifierId"),ids(c.getJSONArray("allowedTools")).toSet(),c.getInt("remainingModelCalls"),
                c.getInt("remainingToolCalls"),c.getLong("expiresAt"),ids(c.getJSONArray("seenMessageIds")).toSet(),stageSenders=senders)
            val authority=object:ChiefBotHandoffAuthority {
                override fun inspect(taskId:String)=current
                override fun claim(handoff:ChiefBotHandoff)=error("admission-only fixture must not claim")
                override fun claimCurrent(handoff:ChiefBotHandoff)=false
                override fun evidenceCurrent(handoff:ChiefBotHandoff)=c.optBoolean("evidenceCurrent",false)
            }
            val decision=ChiefBotHandoffAdmission(store,AgentMailbox(),authority){f.getLong("now")}.admit(handoff)
            return JSONObject().put("decision",decision.name).put("effectCalls",0)
        } catch(_:Exception) { return JSONObject().put("decision","REJECT").put("effectCalls",0) }
        finally { root.deleteRecursively() }
    }

    private fun tests() {
        var total=0
        fun checkCase(name:String,block:()->Unit){block();total++;println("PASS $name")}
        fun fixture(block:(ChiefBotProfileStore,Authority,()->Long,(Long)->Unit)->Unit) {
            val root=Files.createTempDirectory("genesis-handoff-host").toFile()
            try { val store=ChiefBotProfileStore(root);store.seedDefaults(20);var now=1000L
                block(store,Authority(),{now},{now=it})
            } finally {root.deleteRecursively()}
        }
        checkCase("mailbox transports immutable references and consumes current authority") { fixture { store,a,clock,set ->
            val admission=ChiefBotHandoffAdmission(store,AgentMailbox(),a,clock); val h=message()
            check(admission.send(h)==ChiefBotHandoffDecision.ADMIT)
            set(3000);check(admission.receive(h.toAgentId).single().decision==ChiefBotHandoffDecision.REJECT)
        } }
        checkCase("mailbox rejection does not claim budget or imply delivery") { fixture { store,a,clock,_ ->
            val mailbox=AgentMailbox(maxPerAgent=1);val admission=ChiefBotHandoffAdmission(store,mailbox,a,clock)
            check(admission.send(message())==ChiefBotHandoffDecision.ADMIT)
            check(admission.send(message())==ChiefBotHandoffDecision.MAILBOX_REJECTED);check(a.claims==0)
        } }
        checkCase("transport spoof cannot replace typed sender or recipient") { fixture { store,a,clock,_ ->
            val mailbox=AgentMailbox();val admission=ChiefBotHandoffAdmission(store,mailbox,a,clock)
            check(admission.send(message())==ChiefBotHandoffDecision.ADMIT)
            val sent=mailbox.drain("bot-forge").single()
            mailbox.send(sent.copy(id=UUID.randomUUID().toString(),from="bot-lint"))
            check(admission.receive("bot-forge").single().decision==ChiefBotHandoffDecision.REJECT)
        } }
        checkCase("task-authorized sender required even for another registered profile") { fixture { store,a,clock,_ ->
            val admission=ChiefBotHandoffAdmission(store,AgentMailbox(),a,clock)
            check(admission.admit(message(from="bot-mira"))==ChiefBotHandoffDecision.REJECT)
        } }
        checkCase("profile budget and exact fixed profile restrict dispatch") { fixture { store,a,clock,_ ->
            val admission=ChiefBotHandoffAdmission(store,AgentMailbox(),a,clock)
            check(admission.admit(message(models=2))==ChiefBotHandoffDecision.REJECT)
            val h=message(tools=emptySet());val scope=scope(h)
            check(runCatching {admission.workspaceJob(brief(scope),h,scope,Port())}.isFailure)
        } }
        checkCase("canonical atomic claim rejects duplicate across adapter restart") { fixture { store,a,clock,_ ->
            val h=message();val scope=scope(h);val port=Port()
            val one=ChiefBotHandoffAdmission(store,AgentMailbox(),a,clock).workspaceJob(brief(scope),h,scope,port)
            check(one.run().contains("verification=PENDING"));check(port.calls==1)
            val two=ChiefBotHandoffAdmission(store,AgentMailbox(),a,clock).workspaceJob(brief(scope),h,scope,port)
            check(runCatching {two.run()}.isFailure);check(port.calls==1)
        } }
        checkCase("concurrent adapters share one authority budget instead of multiplying it") { fixture { store,a,clock,_ ->
            a.current=a.current.copy(remainingModelCalls=1,remainingToolCalls=1)
            val gate=CountDownLatch(1);val done=CountDownLatch(2);val port=Port();val results=AtomicInteger()
            repeat(2){ val h=message();val scope=scope(h);val job=ChiefBotHandoffAdmission(store,AgentMailbox(),a,clock)
                .workspaceJob(brief(scope),h,scope,port)
                Thread { gate.await();if(runCatching {job.run()}.isSuccess)results.incrementAndGet();done.countDown() }.start() }
            gate.countDown();check(done.await(5,java.util.concurrent.TimeUnit.SECONDS));check(results.get()==1);check(port.calls==1)
        } }
        checkCase("late revoked result does not become successful report") { fixture { store,a,clock,_ ->
            val h=message();val scope=scope(h);val port=Port {a.current=a.current.copy(currentGrant=false)}
            val job=ChiefBotHandoffAdmission(store,AgentMailbox(),a,clock).workspaceJob(brief(scope),h,scope,port)
            check(runCatching {job.run()}.exceptionOrNull()?.message=="BOT_HANDOFF_RESULT_STALE");check(port.calls==1)
        } }
        println("HOST_SOFTWARE_CONTRACTS=$total; authorities and workspace observations are explicit test doubles; no OS/model/device acceptance")
    }

    private fun message(from:String="bot-atlas",models:Int=1,tools:Set<String> = setOf("fixed-node-test")) =
        ChiefBotHandoff(messageId=UUID.randomUUID().toString(),fromAgentId=from,toAgentId="bot-forge",stage=ChiefBotStage.BUILD,
            taskId="t-"+"c".repeat(16),jobId="dddddddd-dddd-4ddd-8ddd-dddddddddddd",sourceCommit="b".repeat(40),attemptId="attempt-1",
            requestMessageId="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee",requestHash="a".repeat(64),grantFingerprint="a".repeat(64),
            persistentWorkspaceId="workspace-bot-forge",expiresAt=2500,requestedTools=tools,modelCalls=models,toolCalls=1,evidenceRefs=emptyList())
    private fun scope(h:ChiefBotHandoff)=WorkspaceTaskScope(h.toAgentId,h.taskId,h.sourceCommit,h.requestMessageId,h.requestHash,
        h.jobId,"node-continuum",256,1,16,1000)
    private fun brief(scope:WorkspaceTaskScope)=WorkerBrief(ChiefWorkspaceWiring.briefId(scope),"Synthetic contract test",setOf("fixed-node-test"),1000)
    private class Port(val after:()->Unit={}) : ChiefWorkspacePort {
        private val count=AtomicInteger();val calls:Int get()=count.get()
        override fun execute(scope:WorkspaceTaskScope):WorkspaceObservation {count.incrementAndGet();after()
            return WorkspaceObservation(scope,"continuum-"+"f".repeat(24),"d".repeat(64),true,0)}
        override fun cancel(scope:WorkspaceTaskScope){}
    }
    private class Authority:ChiefBotHandoffAuthority {
        @Volatile var current=ChiefBotTaskAdmission("t-"+"c".repeat(16),"dddddddd-dddd-4ddd-8ddd-dddddddddddd","b".repeat(40),
            "attempt-1","eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","a".repeat(64),"a".repeat(64),true,false,false,
            "bot-forge","bot-lint",setOf("fixed-node-test"),2,2,3000,emptySet(),stageSenders=mapOf(ChiefBotStage.BUILD to "bot-atlas"))
        var claims=0;private set
        @Synchronized override fun inspect(taskId:String)=current
        @Synchronized override fun claim(h:ChiefBotHandoff):Boolean {
            if(!current.currentGrant||h.messageId in current.seenMessageIds||h.modelCalls>current.remainingModelCalls||h.toolCalls>current.remainingToolCalls)return false
            current=current.copy(remainingModelCalls=current.remainingModelCalls-h.modelCalls,remainingToolCalls=current.remainingToolCalls-h.toolCalls,
                seenMessageIds=current.seenMessageIds+h.messageId);claims++;return true
        }
        @Synchronized override fun claimCurrent(h:ChiefBotHandoff)=h.messageId in current.seenMessageIds
        override fun evidenceCurrent(handoff:ChiefBotHandoff)=false
    }
}
