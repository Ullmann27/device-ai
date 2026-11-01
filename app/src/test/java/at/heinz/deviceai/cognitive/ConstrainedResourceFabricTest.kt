package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Test

class ConstrainedResourceFabricTest {
    @Test fun missingTelemetryNeverCreatesImaginaryRam() {
        assertEquals(ConstrainedResourceFabric.Plan.NO_MEASUREMENT,
            ConstrainedResourceFabric.plan(ConstrainedResourceFabric.Snapshot(null,1,1,0)))
        assertEquals(ConstrainedResourceFabric.Plan.REFUSE_THERMAL,
            ConstrainedResourceFabric.plan(ConstrainedResourceFabric.Snapshot(1000,100,100,4)))
        assertEquals(ConstrainedResourceFabric.Plan.REFUSE_MEMORY,
            ConstrainedResourceFabric.plan(ConstrainedResourceFabric.Snapshot(1000,700,100,0)))
    }
    @Test fun boundedAndConsentAwareRun() {
        assertEquals(ConstrainedResourceFabric.Plan.RUN_LOCAL,
            ConstrainedResourceFabric.plan(ConstrainedResourceFabric.Snapshot(4000,500,500,1)))
        assertEquals(ConstrainedResourceFabric.Plan.LAZY_LOAD,
            ConstrainedResourceFabric.plan(ConstrainedResourceFabric.Snapshot(4000,500,500,1,false)))
        assertEquals(ConstrainedResourceFabric.Plan.REFUSE_MEMORY,
            ConstrainedResourceFabric.plan(ConstrainedResourceFabric.Snapshot(Long.MAX_VALUE,
                Long.MAX_VALUE,Long.MAX_VALUE,0)))
    }
}
