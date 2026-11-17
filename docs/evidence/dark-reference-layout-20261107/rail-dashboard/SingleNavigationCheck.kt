import at.heinz.deviceai.ui.JarvisShellLayout
import at.heinz.deviceai.ui.JarvisShellLayout.Box
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import at.heinz.deviceai.ui.JarvisCockpitGrid
import kotlin.math.max

fun overlap(a: Box, b: Box) = a.left < b.right - .01f && b.left < a.right - .01f && a.top < b.bottom - .01f && b.top < a.bottom - .01f
fun main() {
    var cases = 0
    val sizes = listOf(240f to 320f, 360f to 780f, 412f to 915f, 600f to 480f, 692f to 692f, 692f to 700f, 760f to 884f, 832f to 748f, 840f to 560f, 884f to 760f, 915f to 412f, 1280f to 400f, 1280f to 800f, 1536f to 1024f, 1536f to 1224f)
    for ((w,h) in sizes) for (font in listOf(.85f,1f,1.3f,1.5f,2f)) for (compact in listOf(false,true)) {
        val shell = JarvisShellLayout.create(w,h,font,compactPresentation=compact)
        val label = "$w x $h font $font compact $compact"
        check(shell.viewportHeight == h)
        val entries = shell.boxes.entries.toList()
        for ((slot, box) in entries) check(box.left >= 0f && box.top >= 0f && box.right <= w + .01f && box.bottom <= shell.height + .01f && box.width > 0f && box.height > 0f) { "$label bad $slot $box" }
        for (i in entries.indices) for (j in i+1 until entries.size) {
            val pair = entries[i].key to entries[j].key
            if (pair !in shell.overlays && pair.second to pair.first !in shell.overlays) check(!overlap(entries[i].value, entries[j].value)) { "$label overlap $pair" }
        }
        val input = requireNotNull(shell[Slot.INPUT]); val chat = requireNotNull(shell.inputConversationBox())
        check(input.contains(chat) && chat.width >= 48f && chat.height >=48f) { "$label chat $chat" }
        val controls = shell.dialogControlBoxes()
        check(controls.size==3)
        controls.forEach { check(input.contains(it) && it.width >=48f-.01f && it.height >=48f-.01f && !overlap(it,chat)) { "$label control $it" } }
        controls.zipWithNext().forEach { (a,b)->check(!overlap(a,b)) }
        check(shell.hasFeatureTiles == (w < 600f)) { "$label navigation mode" }
        check(shell.tileBoxes().size == if (w < 600f) 10 else 0) { "$label tile count" }
        if (w >= 600f) check(shell[Slot.TILES] == null && shell.tileColumns == 0 && shell.tileRows == 0) { "$label duplicate tiles" }
        shell.tileBoxes().forEach { check(it.width>=48f-.01f && it.height>=JarvisCockpitGrid.MIN_TILE_DP*max(1f,font)-.01f) { "$label tile $it" } }
        shell.headBounds?.let { head -> check(requireNotNull(shell[Slot.AVATAR]).contains(head)); check(!overlap(head, requireNotNull(shell.orbBounds))) }
        val status = requireNotNull(shell[Slot.STATUS])
        if (w < 600f) check(requireNotNull(shell[Slot.ROW_AGENTS]).top > status.bottom) { "$label phone diagnostic order" }
        else {
            check(requireNotNull(shell[Slot.ROW_MEMORY]).top > input.bottom) { "$label chart order" }
            check(requireNotNull(shell[Slot.ROW_AGENTS]).top > requireNotNull(shell[Slot.ROW_MODEL]).bottom) { "$label activity order" }
            for (slot in listOf(Slot.ROW_MEMORY,Slot.ROW_MODEL,Slot.ROW_AGENTS,Slot.ROW_TASKS)) check(requireNotNull(shell[slot]).bottom < status.top) { "$label primary $slot order" }
            check(requireNotNull(shell[Slot.CAMERA_STATUS]).top > status.bottom) { "$label camera order" }
            check(requireNotNull(shell[Slot.COCKPIT_SECONDARY]).top > status.bottom) { "$label secondary order" }
            for (slot in listOf(Slot.ROW_MEMORY,Slot.ROW_MODEL)) check(requireNotNull(shell[slot]).height >= 190f*max(1f,font)-.01f) { "$label chart height" }
        }
        cases++
    }
    for ((w,h) in listOf(692f to 700f, 832f to 748f, 884f to 760f, 1536f to 1024f)) {
        val shell=JarvisShellLayout.create(w,h)
        check(shell.ref5Grid && shell.tileColumns==0 && shell.tileRows==0 && !shell.hasFeatureTiles)
        check(requireNotNull(shell[Slot.DIALOG]).height<=56f+.01f)
        println("$w x $h: hero=${shell[Slot.AVATAR]}, memory=${shell[Slot.ROW_MEMORY]}, model=${shell[Slot.ROW_MODEL]}, primary=${shell[Slot.STATUS]}, content=${shell.height}")
    }
    println("PASS $cases single-navigation responsive geometry cases")
}
