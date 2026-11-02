package com.google.ai.edge.litertlm
sealed class Backend { class CPU : Backend() }
sealed class Content { class Text(val text:String):Content(); class ToolResponse(val name:String,val response:Any?):Content() }
class Contents(val contents:List<Content>) { companion object { fun of(text:String)=Contents(listOf(Content.Text(text))); fun of(contents:List<Content>)=Contents(contents) } }
class ToolCall(val name:String,val arguments:Map<String,Any?>)
class Message(val contents:Contents,val toolCalls:List<ToolCall> = emptyList()) { companion object { fun user(c:Contents)=Message(c);fun tool(c:Contents)=Message(c) } }
interface MessageCallback {fun onMessage(message:Message);fun onDone();fun onError(throwable:Throwable)}
interface OpenApiTool {fun getToolDescriptionJsonString():String;fun execute(paramsJsonString:String):String}
class ToolProvider
fun tool(t:OpenApiTool)=ToolProvider()
class EngineConfig(val modelPath:String,val backend:Backend,val cacheDir:String)
class ConversationConfig(val systemInstruction:Contents,val tools:List<ToolProvider> = emptyList(),val automaticToolCalling:Boolean=true,val maxOutputToken:Int=0)
object NativeProbe {var callback:MessageCallback?=null;var cancelCount=0;var dispatchHook:(()->Unit)?=null}
class Conversation {
 val isAlive:Boolean=true
 fun getTokenCount():Int=100
 fun close() {}
 fun cancelProcess() {NativeProbe.cancelCount++}
 fun sendMessageAsync(message:Message,callback:MessageCallback) {
   NativeProbe.callback=callback
   NativeProbe.dispatchHook?.invoke()
 }
}
class Engine(val config:EngineConfig) {fun initialize(){};fun isInitialized()=true;fun close(){};fun createConversation(config:ConversationConfig)=Conversation()}
