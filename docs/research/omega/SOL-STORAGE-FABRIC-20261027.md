# Storage Fabric bounded research — 27 October 2026

Status: WIP / UNVERIFIED. Parent integration PR183; baseline 9b730b3e742effd05280a34ab568858af659e9b6. Child branch sol/continuum-storage-prototype-20261027.

Owner-authorized research only, EXP-06 content-addressed provenance and EXP-12 recoverable/versioned references. No owner memories, productive model weights, APK, main integration, compute provisioning or new permissions. Personal memory excluded. Two explicit temporary owner-controlled directories and separate localhost Node processes will prove replicated encrypted object put/get/delete and failure handling. This is a Node stdlib reference backend, NOT SeaweedFS/JuiceFS/FUSE or geographically independent storage.

Planned API: authenticated scoped /v1/blocks/<sha256> GET/PUT/DELETE, capacity status, AES-256-GCM encrypted blocks, content hash verification, central durable versioned object catalog with tombstones, bounded verified encrypted cache. Narrow storageManifest() export describes measured storage with executable:false; existing Virtual Model Fabric planner is preserved.

Tests: NOT_RUN at checkpoint. Next: pin official SeaweedFS/JuiceFS licenses/commits/API sources, implement smallest native two-process slice and negative tests, preserve raw evidence. 500GB/TB is a future physical capacity target, never granted or synthesized by this prototype.

Rollback: stop explicitly started temporary server processes and remove this child branch's scoped files; no production state changes.
