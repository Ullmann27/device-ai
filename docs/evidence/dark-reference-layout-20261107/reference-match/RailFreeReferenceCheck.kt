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
        check(controls.size==2)
        controls.forEach { check(input.contains(it) && it.width >=48f-.01f && it.height >=48f-.01f && !overlap(it,chat)) { "$label control $it" } }
        controls.zipWithNext().forEach { (a,b)->check(!overlap(a,b)) }
        check(shell.hasFeatureTiles && shell.tileBoxes().size == 10) { "$label all feature cards" }
        check(shell[Slot.NAV_RAIL] == null && shell.rail == null && shell.profile.railWidthDp == 0f) { "$label fixed rail" }
        val tiles = requireNotNull(shell[Slot.TILES])
        check(tiles.left == 12f && tiles.right == w-12f) { "$label cropped card area $tiles" }
        check(tiles.top > input.bottom && requireNotNull(shell[Slot.STATUS]).top > tiles.bottom) { "$label start order" }
        if (w >= 600f && font == 1f) check(shell.tileColumns == 5 && shell.tileRows == 2) { "$label reference rows" }
        if (w >= 600f && !compact) {
            val hero = requireNotNull(shell[Slot.AVATAR])
            check(kotlin.math.abs(hero.height - minOf(640f, hero.width*.72f)) < .01f) { "$label hero proportions $hero" }
            check(requireNotNull(shell.headBounds).contains(requireNotNull(shell[Slot.DIALOG]))) { "$label badge placement" }
        }
        shell.tileBoxes().forEach { check(it.width>=48f-.01f && it.height>=JarvisCockpitGrid.MIN_TILE_DP*max(1f,font)-.01f) { "$label tile $it" } }
        shell.headBounds?.let { head -> check(requireNotNull(shell[Slot.AVATAR]).contains(head)); check(!overlap(head, requireNotNull(shell.orbBounds))) }
        for (slot in listOf(Slot.ROW_AGENTS,Slot.ROW_TASKS,Slot.ROW_MEMORY,Slot.ROW_MODEL)) check(requireNotNull(shell[slot]).top > requireNotNull(shell[Slot.STATUS]).bottom) { "$label diagnostic before home: $slot" }
        cases++
    }
    for ((w,h) in listOf(692f to 700f, 832f to 748f, 884f to 760f, 1536f to 1024f)) {
        val shell=JarvisShellLayout.create(w,h)
        check(shell.ref5Grid && shell.tileColumns==5 && shell.tileRows==2)
        check(requireNotNull(shell[Slot.DIALOG]).height<=56f+.01f)
        println("$w x $h: hero=${shell[Slot.AVATAR]}, input=${shell[Slot.INPUT]}, tile=${shell.tileBoxes().first()}, memory=${shell[Slot.ROW_MEMORY]}, primary=${shell[Slot.STATUS]}, content=${shell.height}")
    }
    println("PASS $cases rail-free reference geometry cases")
}
