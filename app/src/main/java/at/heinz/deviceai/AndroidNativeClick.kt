package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import at.heinz.deviceai.cognitive.ClickFrame
import at.heinz.deviceai.cognitive.ClickNode
import at.heinz.deviceai.cognitive.ClickRequest
import java.security.MessageDigest

/** Strict candidate: a single visible application window and a bounded complete tree.
 * A production caller still requires an explicit, exact, ToolGate-bound owner grant. */
internal fun AccessibilityService.snapshotForClick(request:ClickRequest,revision:Long,checkActive:()->Unit,bindWindow:(Int)->Unit):ClickFrame =
    inspectClickTree(request,revision,checkActive,false,bindWindow) { _,_->false }.first

internal fun AccessibilityService.dispatchClick(request:ClickRequest,revision:Long,checkActive:()->Unit,bindWindow:(Int)->Unit,
    verify:(ClickFrame)->Unit):Boolean = inspectClickTree(request,revision,checkActive,true,bindWindow) { frame,node ->
        verify(frame);checkActive()
        check(node.refresh()){ "STALE_CLICK_NODE" }
        check(node.isVisibleToUser&&node.isEnabled&&node.isClickable&&!node.isPassword&&!node.isEditable &&
            node.packageName?.toString()==request.packageName &&
            listOfNotNull(node.text?.toString(),node.contentDescription?.toString()).contains(request.label)) { "STALE_CLICK_NODE" }
        // Re-evaluate the OS window set after refreshing the node: a dialog or
        // overlay may have appeared between the tree walk and dispatch.
        val currentWindows=windows ?: error("WINDOWS_UNAVAILABLE")
        try {
            check(currentWindows.size==1){"AMBIGUOUS_OR_OVERLAID_WINDOWS"}
            val currentWindow=currentWindows.single()
            check(currentWindow.type==AccessibilityWindowInfo.TYPE_APPLICATION && currentWindow.isActive &&
                currentWindow.isFocused && currentWindow.id==node.windowId){"UNSAFE_WINDOW"}
            val root=rootInActiveWindow?:error("NO_ACTIVE_WINDOW")
            try { check(root.windowId==currentWindow.id&&root.packageName?.toString()==request.packageName){"UNEXPECTED_SCREEN_PACKAGE"} }
            finally { @Suppress("DEPRECATION") root.recycle() }
        } finally { currentWindows.forEach { @Suppress("DEPRECATION") it.recycle() } }
        verify(frame);checkActive()
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }.second

private inline fun AccessibilityService.inspectClickTree(request:ClickRequest,revision:Long,checkActive:()->Unit,
    dispatch:Boolean,bindWindow:(Int)->Unit,block:(ClickFrame,AccessibilityNodeInfo)->Boolean):Pair<ClickFrame,Boolean> {
    checkActive()
    val windows=windows ?: error("WINDOWS_UNAVAILABLE")
    try {
        // Do not infer safety when even one additional interactive/system window exists.
        check(windows.size==1){"AMBIGUOUS_OR_OVERLAID_WINDOWS"}
        val window=windows.single()
        check(window.type==AccessibilityWindowInfo.TYPE_APPLICATION && window.isActive && window.isFocused){"UNSAFE_WINDOW"}
        val root=rootInActiveWindow?:error("NO_ACTIVE_WINDOW")
        val nodes=mutableListOf<ClickNode>()
        val queue=ArrayDeque<Pair<AccessibilityNodeInfo,Int>>();queue.add(root to 0)
        val digest=MessageDigest.getInstance("SHA-256")
        var selected:AccessibilityNodeInfo?=null
        var matches=0
        try {
            check(root.windowId==window.id && root.packageName?.toString()==request.packageName){"UNEXPECTED_SCREEN_PACKAGE"}
            bindWindow(window.id)
            while(queue.isNotEmpty()) {
                val (node,depth)=queue.removeFirst()
                var retained=false
                try {
                    checkActive();check(nodes.size<192){"SCREEN_TREE_LIMIT"}
                    check(node.packageName?.toString()==request.packageName&&node.windowId==window.id){"UNEXPECTED_SCREEN_PACKAGE"}
                    val sensitive=node.isPassword||node.isEditable
                    val visible=node.isVisibleToUser
                    val actionable=node.isEnabled&&node.isClickable
                    val bounds=Rect().also(node::getBoundsInScreen)
                    val labels=if(visible&&!sensitive)listOfNotNull(node.text?.toString(),node.contentDescription?.toString())
                        .filter{it.isNotBlank()}.distinct() else emptyList()
                    check(labels.all{it.length<=200&&it.none(Char::isISOControl)}){"UNSAFE_SCREEN_LABEL"}
                    val record=labels.joinToString("\u0000")+"|$visible|$actionable|$sensitive|${bounds.flattenToString()}|$depth\n"
                    digest.update(record.toByteArray(Charsets.UTF_8))
                    if(labels.isEmpty())nodes.add(ClickNode("",visible,actionable,sensitive))
                    else labels.forEach { label->
                        nodes.add(ClickNode(label,visible,actionable,sensitive))
                        if(label==request.label){matches++;if(dispatch&&selected==null){selected=node;retained=true}}
                    }
                    check(nodes.size<=192){"SCREEN_TREE_LIMIT"}
                    if(visible&&!sensitive){
                        val count=node.childCount
                        check(count in 0..192 && (depth<20||count==0)){"SCREEN_TREE_LIMIT"}
                        for(i in 0 until count){
                            check(nodes.size+queue.size<192){"SCREEN_TREE_LIMIT"}
                            queue.add((node.getChild(i)?:error("INCOMPLETE_SCREEN_TREE")) to depth+1)
                        }
                    }
                    checkActive()
                } finally { if(!retained)@Suppress("DEPRECATION") node.recycle() }
            }
            val hex=digest.digest().joinToString(""){"%02x".format(it)}
            val frame=ClickFrame(request.packageName,request.epoch,revision,hex,true,true,nodes.toList())
            if(!dispatch)return frame to false
            check(matches==1 && selected!=null){"UNSAFE_OR_AMBIGUOUS_TARGET"}
            return frame to block(frame,selected!!)
        } finally {
            while(queue.isNotEmpty())@Suppress("DEPRECATION") queue.removeFirst().first.recycle()
            selected?.let { @Suppress("DEPRECATION") it.recycle() }
        }
    } finally { windows.forEach { @Suppress("DEPRECATION") it.recycle() } }
}
