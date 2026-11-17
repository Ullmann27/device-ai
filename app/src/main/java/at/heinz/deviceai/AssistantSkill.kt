package at.heinz.deviceai

interface AssistantSkill {
    val id: String
    val displayName: String
    val description: String
    val supportedPackages: List<String>
    val toolNames: List<String>
    val promptExtension: String
}
