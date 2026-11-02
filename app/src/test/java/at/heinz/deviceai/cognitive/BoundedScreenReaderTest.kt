package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class BoundedScreenReaderTest {
    private class Node(override val text:String?=null,override val sensitive:Boolean=false,
        private val nodes:List<Node> = emptyList(),override val visible:Boolean=true):ScreenTreeNode{
        var released=0;override val description:String?=null;override val children get()=nodes.size
        override fun child(index:Int)=nodes[index]
        override fun release(){released++;check(released==1){"node released twice"}}
    }
    @Test fun safeTraversalCopiesTextAndReleasesEveryAcquiredNodeExactlyOnce(){
        val a=Node("Display");val b=Node("About");val root=Node("Settings",nodes=listOf(a,b))
        val s=BoundedScreenReader.read(root)
        assertEquals(listOf("Settings","Display","About"),s.texts);assertTrue(s.complete)
        assertTrue(listOf(root,a,b).all{it.released==1})
    }
    @Test fun passwordsAndEditableSubtreesAreNeverRead(){
        val secret=Node("SECRET");val field=Node("PASSWORD",true,listOf(secret));val root=Node("Safe",nodes=listOf(field))
        val s=BoundedScreenReader.read(root)
        assertEquals(listOf("Safe"),s.texts);assertEquals(1,s.redacted);assertEquals(0,secret.released)
        assertEquals(1,field.released)
    }
    @Test fun nodeTextAndDepthBoundsDeclareIncompleteRatherThanSilentTruncation(){
        val a=Node("A");val b=Node("B");val root=Node("root",nodes=listOf(a,b))
        assertFalse(BoundedScreenReader.read(root,maxNodes=2).complete)
        assertEquals(1,root.released);assertEquals(1,a.released);assertEquals(0,b.released)
        assertFalse(BoundedScreenReader.read(Node("too much text"),maxCharacters=3).complete)
        assertFalse(BoundedScreenReader.read(Node("x",nodes=listOf(Node("y",nodes=listOf(Node("z"))))),maxDepth=1).complete)
    }
    @Test fun brokenNodeReleasesQueuedReferencesAndMarksSnapshotIncomplete(){
        var releases=0
        val broken=object:ScreenTreeNode{
            override val text:String? get()=error("stale node")
            override val description:String?=null;override val visible=true;override val sensitive=false
            override val children=0;override fun child(index:Int):ScreenTreeNode?=null
            override fun release(){releases++}
        }
        val s=BoundedScreenReader.read(broken);assertFalse(s.complete);assertEquals(1,releases)
    }
    @Test fun separatorCharactersCountTowardsTheSameAtomicEvidenceBudget(){
        val children=List(100){Node("x".repeat(40))};val root=Node(nodes=children)
        val snapshot=BoundedScreenReader.read(root)
        assertFalse(snapshot.complete)
        assertTrue(snapshot.texts.joinToString(" ").length<=4_000)
        assertTrue(children.all{it.released==1})
    }
    @Test fun stopAfterChildAcquisitionReleasesItAndAcquiresNoMore(){
        val child=Node("private");var stop=false;var acquired=0;var rootReleased=0
        val root=object:ScreenTreeNode{
            override val text="root";override val description:String?=null
            override val visible=true;override val sensitive=false;override val children=100
            override fun child(index:Int):ScreenTreeNode{acquired++;stop=true;return child}
            override fun release(){rootReleased++}
        }
        val failure=assertThrows(at.heinz.deviceai.ToolBackendFailure::class.java){
            BoundedScreenReader.read(root,checkActive={if(stop)throw at.heinz.deviceai.ToolBackendFailure(at.heinz.deviceai.ToolExecutionStatus.CANCELLED,"owner stop")})}
        assertEquals(at.heinz.deviceai.ToolExecutionStatus.CANCELLED,failure.status)
        assertEquals(1,acquired);assertEquals(1,child.released);assertEquals(1,rootReleased)
    }
}
