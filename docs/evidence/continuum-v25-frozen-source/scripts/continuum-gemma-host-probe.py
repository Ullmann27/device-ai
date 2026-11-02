#!/usr/bin/env python3
"""Explicit local CPU smoke using Google's optional LiteRT-LM API.

No tools, external inference API, private memories, training or model promotion.
Run under an operator wall-time/process limit. Only the selected original bytes
are admitted. This is a host experiment, never a Fold7 acceptance receipt.
"""
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import resource
import subprocess
import sys
import time


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: continuum-gemma-host-probe.py MODEL_FILE NEW_REPORT_JSON")
    root = Path(__file__).resolve().parents[1]
    expected = os.environ.get("CONTINUUM_PROBE_SOURCE_SHA")
    actual = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    if expected != actual or subprocess.check_output(
        ["git", "status", "--porcelain"], cwd=root, text=True).strip():
        raise SystemExit("Exact CONTINUUM_PROBE_SOURCE_SHA and clean checkout required")
    config = json.loads((root / "config/continuum-selected-models.json").read_text())
    selected = config["roles"]["on_device"]
    model = Path(sys.argv[1]).resolve(strict=True)
    output = Path(sys.argv[2])
    if output.exists():
        raise SystemExit("Refusing to overwrite an existing experiment report")
    digest = hashlib.sha256()
    with model.open("rb") as source:
        for block in iter(lambda: source.read(8 * 1024 * 1024), b""):
            digest.update(block)
    if digest.hexdigest() != selected["file_sha256"]:
        raise SystemExit("Model SHA-256 differs from the selected original")
    import litert_lm  # Optional official, separately checksum-verified host package.
    runtime_version = importlib.metadata.version("litert-lm-api")
    if runtime_version != "0.18.1":
        raise SystemExit("This frozen host probe requires litert-lm-api0.18.1")

    fixture = root / "config/continuum-gemma-host-smoke-v1.json"
    probes = json.loads(fixture.read_text())["cases"]
    report = {
        "schema": "CONTINUUM_GEMMA_HOST_PROBE_V1",
        "runtime": runtime_version,
        "model_sha256": digest.hexdigest(), "model_bytes": model.stat().st_size,
        "fixture_sha256": hashlib.sha256(fixture.read_bytes()).hexdigest(),
        "backend": "CPU", "threads": 2, "context_tokens": 2048,
        "max_output_tokens": 128, "thinking_budget": 32,
        "status": "LOADING", "cases": [], "device_inference": "NOT_RUN",
        "tools": [], "training": False, "promotion": False,
        "source_commit": actual,
    }
    output.parent.mkdir(parents=True, exist_ok=True)

    def save():
        report["peak_rss_kib"] = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
        tmp = output.with_suffix(output.suffix + ".tmp")
        tmp.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
        os.replace(tmp, output)

    save()
    try:
        # Dedicated host research worker: bound address space and actual CPU time.
        resource.setrlimit(resource.RLIMIT_AS, (8 * 1024**3, 8 * 1024**3))
        resource.setrlimit(resource.RLIMIT_CPU, (180, 180))
        with litert_lm.ModelInfo(model) as info:
            report["declared_context_tokens"] = info.max_context_tokens
            report["supports_thinking"] = info.llm.supports_thinking()
        started = time.monotonic()
        with litert_lm.Engine(str(model), backend=litert_lm.Backend.CPU(thread_count=2),
                              max_num_tokens=2048) as engine:
            report["load_seconds"] = time.monotonic() - started
            report["status"] = "LOADED_PROBING"
            save()
            for probe in probes:
                started = time.monotonic()
                with engine.create_conversation(
                    tools=[], automatic_tool_calling=False, max_output_tokens=128,
                    thinking_config=litert_lm.ThinkingConfig(
                        enable_thinking=report["supports_thinking"], thinking_token_budget=32),
                    sampler_config=litert_lm.SamplerConfig(top_k=1, top_p=1.0,
                                                          temperature=1.0, seed=73),
                    system_message="Antworte auf Deutsch und befolge das verlangte Antwortformat."
                ) as conversation:
                    message = conversation.send_message(probe["prompt"])
                    text = str(message.contents)
                report["cases"].append({
                    "id": probe["id"], "text": text, "expected": probe["expected"],
                    "exact_correct": text.strip().rstrip(".! ") == probe["expected"],
                    "seconds": time.monotonic() - started,
                })
                save()
        report["status"] = "MEASURED_HOST_ONLY"
        report["correct"] = sum(case["exact_correct"] for case in report["cases"])
    except Exception as error:
        report["status"] = "FAILED_NATIVE_RUNTIME"
        report["error"] = type(error).__name__ + ": " + str(error)[:300]
        save()
        raise
    save()
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
