# Frozen public7B extension contract

Received from runtime author before implementation inspection:
getLocalModelResourceProfile({provenance,runtimeSha256}) returns frozen exact7B
{id,maxFileBytes:4683073536,nativeAddressSpaceBytes:7516192768,
maxJobMemoryBytes:8589934592,requiresJobMemoryLimit:true}, or null for all mismatches.
assertLocalModelJobMemoryLimit(profile) returns frozen actualOScap
{maxJobMemoryBytes,maxJobSwapBytes:0,enforcement:'CGROUP_V2'}, else
BLOCKED_LOCAL_JOB_MEMORY_LIMIT. It checks actual kernel cgroup identity, private
rootnamespace and parent membership, not caller environment claims.

Chief clarified execution scope: fixed default-provider transport places trusted
CLI, weight admission and native process in a7GiB/no-swap container, preserving
1GiB headroom for bounded host provider request/response work; total allowance8GiB.
The whole shared development host is not the model job. No full-file weights read
may occur outside the capped inference job; host source/context/output stay bounded.

Before any model run, independently inspect fixedDocker endpoint/image/entrypoint,
read-only mounts, absence of socket/secrets, actual limits, outer syscall policy
and inner native namespace/seccomp. Existing emptyimage ID independently observed:
sha256:3f44842e8a052beda7e358b9fb8d8acc3e180a7f58aaceff7e1b49ad7f3d46e1,
no rootfs layers/entrypoint/Cmd. Mutable tag alone is not image provenance.
Candidate registry stays false until actual independent native evidence.
