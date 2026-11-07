package at.heinz.deviceai.ui
fun main() {
    val scene = ToolWorkSceneLease()
    val old = ToolWorkSceneLease.Key("session", "scope", 1, "old")
    val current = old.copy(callId = "current")
    check(scene.offer(old, "session", "scope", 1, true, 100))
    check(scene.ended(old))
    scene.closed(old)
    check(scene.offer(current, "session", "scope", 1, true, 100))
    val staleAccepted = scene.offer(old, "session", "scope", 1, true, 100)
    println("staleSameMillisecondAccepted=$staleAccepted currentCall=${scene.active?.callId}")
    check(!staleAccepted && scene.active == current) { "Completed old call replaced the current scene" }
}
