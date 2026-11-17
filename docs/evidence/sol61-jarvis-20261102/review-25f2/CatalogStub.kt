package at.heinz.deviceai.localmodel
import java.io.File
enum class LocalModelBackend { CPU }
data class LocalModelCapabilities(val supportedBackends:Set<LocalModelBackend> = setOf(LocalModelBackend.CPU), val maxContextTokens:Int=100_000)
data class ImportedLocalModel(val id:String="probe",val version:String="1",val capabilities:LocalModelCapabilities=LocalModelCapabilities())
class LocalModelCatalog { fun get(id:String,version:String):ImportedLocalModel?=null; fun modelFile(model:ImportedLocalModel)=File("unused") }
class NativeLiteRtModelInspector { fun inspect(file:File)=LocalModelCapabilities() }
