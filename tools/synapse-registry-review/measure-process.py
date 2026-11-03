#!/usr/bin/env python3
"""Bounded subprocess observation; no `/usr/bin/time` dependency."""
import json
import resource
import subprocess
import sys
import time

receipt, *command = sys.argv[1:]
before = resource.getrusage(resource.RUSAGE_CHILDREN)
start = time.monotonic_ns()
result = subprocess.run(command, check=False)
after = resource.getrusage(resource.RUSAGE_CHILDREN)
with open(receipt, 'w', encoding='utf-8') as stream:
    json.dump({
        'scope': 'test process including fixture generation and waited JVM descendants; not Fold7',
        'exitCode': result.returncode,
        'elapsedNanos': time.monotonic_ns() - start,
        'childUserCpuSeconds': after.ru_utime - before.ru_utime,
        'childSystemCpuSeconds': after.ru_stime - before.ru_stime,
        'maximumChildRssKiB': after.ru_maxrss,
        'rssSemantics': 'Linux RUSAGE_CHILDREN high-water mark; not sum of simultaneous processes',
        'deviceEnergy': 'NOT_MEASURED'
    }, stream, indent=2)
sys.exit(result.returncode)
