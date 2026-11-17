package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class VirtualModelFabricTest {
    @Test fun signedRegistryExportsTheSharedNodeContractWithoutPrivateContext() {
        val fixture = VirtualFabricFixture()
        val exported = fixture.exporter.export(fixture.task, "a".repeat(64))
        val expected = javaClass.getResourceAsStream("/compute-fabric-planning-v1.json")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText().trim() }
            ?: error("Missing compute-fabric-planning-v1.json on the test classpath")
        assertEquals(expected, exported)
        assertFalse(exported.contains("owner:test"))
        assertFalse(exported.contains("bounded context"))
        assertFalse(exported.contains("signature"))
    }

    @Test fun revokedExpiredIncompatibleAndUnassignedNodesCannotBeExported() {
        val f = VirtualFabricFixture()
        f.registry.revoke(ComputeNodeId("local-worker"))
        assertTrue(f.exporter.export(f.task, "a".repeat(64)).endsWith("\"nodes\":[]}"))
        val g = VirtualFabricFixture()
        for (task in listOf(g.task.copy(requiredRamMb = 8192),
            g.task.copy(assignedNodeId = ComputeNodeId("other")),
            g.task.copy(requiredBackend = ComputeBackend.GPU))) {
            assertTrue(g.exporter.export(task, "a".repeat(64)).endsWith("\"nodes\":[]}"))
        }
        g.now = 9500
        assertTrue(g.exporter.export(g.task, "a".repeat(64)).endsWith("\"nodes\":[]}"))
    }

    @Test fun tamperedSignatureAndBadTaskIdentityCannotCrossExporter() {
        val f = VirtualFabricFixture()
        assertThrows(IllegalArgumentException::class.java) {
            f.registry.register(SignedNodeCapabilityManifest(f.manifest.copy(maxRamMb = 8192), f.signature))
        }
        assertThrows(IllegalArgumentException::class.java) { f.exporter.export(f.task, "invented") }
        f.now = 10000
        assertThrows(IllegalArgumentException::class.java) { f.exporter.export(f.task, "a".repeat(64)) }
    }
}

/** Actual ephemeral Ed25519 keys for a synthetic test owner, never owner's keys. */
internal class VirtualFabricFixture {
    var now = 1000L
    private val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val manifest = NodeCapabilityManifest(ComputeNodeId("local-worker"), "owner:test",
        setOf(ComputeTaskType.REASONING), setOf(ComputeBackend.CPU), 2048, 2000, 4096, 1000, 10000)
    val signature = Signature.getInstance("Ed25519").run {
        initSign(keys.private); update(manifest.canonicalBytes()); sign()
    }
    val registry = ComputeNodeRegistry(NodeManifestSignatureVerifier {
        Signature.getInstance("Ed25519").run {
            initVerify(keys.public); update(it.manifest.canonicalBytes()); verify(it.signature)
        }
    }) { now }
    val task = ComputeTask("fabric-coding-01", ComputeTaskType.REASONING, emptyList(), "bounded context",
        1024, ComputeBackend.CPU, 1000, 4096, ComputePermission.READ_ONLY_DATA, 10000, null)
    val exporter = VirtualModelFabricSnapshotExporter(registry) { now }
    init { registry.register(SignedNodeCapabilityManifest(manifest, signature)) }
}

/** Cross-language check runs this JVM entry point and gives its exact bytes to Node. */
object VirtualModelFabricFixtureMain {
    @JvmStatic fun main(args: Array<String>) {
        val f = VirtualFabricFixture()
        println(f.exporter.export(f.task, "a".repeat(64)))
    }
}
