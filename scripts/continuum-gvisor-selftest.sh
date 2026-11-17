#!/usr/bin/env bash
# gVisor runtime selftest for the M8 isolated executor (run on a Linux host with Docker + runsc; not possible on the
# Fold7: Android offers no Docker/runsc). Needs a local, pinned image id (no pull) with /usr/bin/env.
#   scripts/continuum-gvisor-selftest.sh sha256:<image-id> /path/to/node-root
# 1 runtime   docker reports runsc; the container kernel identifies as gVisor (dmesg banner)
# 2 negative  inside the sandbox: no network egress, no host secret files, read-only root
# 3 positive  a general benchmark task through the unchanged repairSession with executor runtime=runsc
#             (--brain oracle = reference solution: proves the sandbox path, NOT autonomous model repair)
set -u
IMG=${1:?image id sha256:...}; NODE_ROOT=${2:?node root with bin/node}; FAIL=0
ok(){ echo "PASS $1"; }; bad(){ echo "FAIL $1"; FAIL=1; }
docker info --format '{{json .Runtimes}}' | grep -q '"runsc"' && ok "runsc registered" || { echo "BLOCKED: runsc not registered (ISOLATION_GVISOR_UNAVAILABLE)"; exit 3; }
RUN=(docker run --rm --runtime=runsc --network=none --read-only --cap-drop=ALL --security-opt=no-new-privileges:true
  --pids-limit=64 --memory=512m --user=65534:65534 --mount type=bind,src="$NODE_ROOT",dst=/tools/node,readonly "$IMG")
"${RUN[@]}" /usr/bin/env dmesg 2>/dev/null | grep -qi gvisor && ok "kernel is gVisor" || bad "gVisor banner missing"
"${RUN[@]}" /tools/node/bin/node -e "fetch('http://1.1.1.1',{signal:AbortSignal.timeout(3000)}).then(()=>process.exit(0),()=>process.exit(7))"
[ $? -eq 7 ] && ok "no network egress" || bad "network reachable"
"${RUN[@]}" /usr/bin/env sh -c 'test -r /etc/shadow && grep -q root /etc/shadow' && bad "host secret readable" || ok "no host secret file"
"${RUN[@]}" /usr/bin/env sh -c 'echo x > /probe' 2>/dev/null && bad "root writable" || ok "read-only root"
CFG=$(mktemp -d)/isolation.json
printf '{"image":"%s","nodeHome":"%s","runtime":"runsc","timeoutMs":120000,"memoryMb":1024,"pids":64,"cpus":1}\n' "$IMG" "$NODE_ROOT" > "$CFG"
node scripts/continuum-agent-benchmark.mjs --brain oracle --executor "$CFG" --only gen-sum-off-by-one --out gvisor-selftest.json \
  && grep -q '"verified": 1' gvisor-selftest.json && ok "general repair VERIFIED in gVisor (reference solution)" || bad "positive repair"
exit $FAIL
