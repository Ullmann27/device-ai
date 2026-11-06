#!/usr/bin/env python3
"""Reproduce archived native color measurements."""
from pathlib import Path
import subprocess
import sys

evidence = Path(__file__).resolve().parent
repo = evidence.parents[3]
output = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
output.mkdir(parents=True, exist_ok=True)
subprocess.run([
    sys.executable, str(repo / "tools/measure_ref5_app_colors.py"),
    "--repo", str(repo),
    "--reference", str(repo / "docs/design-screenshots/ref5-fold7-offen.png"),
    "--baseline", str(evidence / "baseline-b294-start.png"),
    "--baseline-box", "148", "426", "288", "474",
    "--baseline-box-note", "No baseline view dump; frozen layout and native rim pixels independently checked. No rescaling.",
    "--current", str(evidence / "current-9468-start.png"),
    "--current-views", str(evidence / "current-9468-start.views.txt"),
    "--current-source-label", "9468eb87cd875c5e07f689aeb53767880ccd6765",
    "--density", "1", "--font-scale", "1",
    "--output", str(output / "ref5-reproduced.json"),
    "--summary-output", str(output / "ref5-reproduced-summary.json"),
], check=True)
