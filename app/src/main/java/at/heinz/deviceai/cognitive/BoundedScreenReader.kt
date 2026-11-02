/* Adapted from tokenarc/open-jarvis ScreenReader.kt @04873998933830ce582018fe4664ebce5ec64d2f.
 * Copyright (c) 2024 TokenArc. MIT; see third_party/tokenarc-open-jarvis/LICENSE.
 * Changes: bounded breadth-first traversal; release each acquired node exactly once;
 * never return live nodes; redact password/editable fields; propagate incomplete snapshot. */
package at.heinz.deviceai.cognitive

internal interface ScreenTreeNode {
    val text:String?
    val description:String?
    val visible:Boolean
    val sensitive:Boolean
    val children:Int
    fun child(index:Int):ScreenTreeNode?
    fun release()
}
internal data class ScreenTextSnapshot(val texts:List<String>,val complete:Boolean,val visited:Int,val redacted:Int=0)
internal object BoundedScreenReader {
    fun read(root:ScreenTreeNode,maxNodes:Int=192,maxCharacters:Int=4_000,maxDepth:Int=20):ScreenTextSnapshot {
        require(maxNodes in 1..256&&maxCharacters in 1..4_000&&maxDepth in 1..24)
        val queue=ArrayDeque<Pair<ScreenTreeNode,Int>>();queue.add(root to 0)
        val out=mutableListOf<String>();var visited=0;var characters=0;var complete=true;var redacted=0
        try {
            while(queue.isNotEmpty()){
                val (node,depth)=queue.removeFirst()
                try {
                    visited++
                    if(node.visible&&node.sensitive)redacted++
                    if(node.visible&&!node.sensitive){
                        for(raw in listOfNotNull(node.text,node.description)){
                            if(raw.length>maxCharacters){complete=false;continue}
                            val text=raw.map{if(it.isISOControl())' ' else it}.joinToString("").trim()
                            if(text.isNotEmpty()){
                                val remaining=maxCharacters-characters
                                if(text.length>remaining){complete=false;continue}
                                out+=text;characters+=text.length
                            }
                        }
                        val n=node.children
                        if(n<0||n>maxNodes)complete=false
                        if(depth<maxDepth){
                            for(i in 0 until n.coerceIn(0,maxNodes)){
                                if(visited+queue.size>=maxNodes){complete=false;break}
                                val child=node.child(i)
                                if(child==null)complete=false else queue.add(child to depth+1)
                            }
                        }else if(n>0)complete=false
                    }
                }finally{node.release()}
            }
        }catch(_:RuntimeException){complete=false}
        finally{while(queue.isNotEmpty())runCatching{queue.removeFirst().first.release()}}
        return ScreenTextSnapshot(out.toList(),complete,visited,redacted)
    }
}
