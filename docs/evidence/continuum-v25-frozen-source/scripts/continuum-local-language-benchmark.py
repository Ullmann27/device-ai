#!/usr/bin/env python3
"""Frozen, tool-free host evaluation. Never a device or production receipt."""
import argparse
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import resource
import subprocess
import time
import unicodedata


# ECMAScript trim set from the independent evaluator's fixed scoring protocol.
ECMA_WS = "\u0009\u000a\u000b\u000c\u000d\u0020\u00a0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u2028\u2029\u202f\u205f\u3000\ufeff"


def normalize(value):
    text = unicodedata.normalize("NFC", value).replace("\r\n", "\n").replace("\r", "\n").strip(ECMA_WS)
    if text.endswith("."):
        text = text[:-1].strip(ECMA_WS)
    return None if "\n" in text else text


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model_id", choices=["gemma-selected", "qwen3-public-reference"])
    parser.add_argument("runtime", choices=["0.18.0", "0.18.1"])
    parser.add_argument("model_file", type=Path)
    parser.add_argument("new_report", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    if os.environ.get("CONTINUUM_PROBE_SOURCE_SHA") != sha or subprocess.check_output(
            ["git", "status", "--porcelain"], cwd=root, text=True).strip():
        raise SystemExit("Exact source SHA and clean checkout required")
    if args.new_report.exists():
        raise SystemExit("Refusing to overwrite an existing report")
    matrix = json.loads((root / "config/research/local-language-runtime-matrix-v1.json").read_text())
    if {"model": args.model_id, "runtime": args.runtime} not in matrix["runs"]:
        raise SystemExit("Not a preregistered matrix arm")
    identity = matrix["models"][args.model_id]
    fixture_bytes = (root / matrix["fixture"]).read_bytes()
    assert hashlib.sha256(fixture_bytes).hexdigest() == matrix["fixtureSha256"]
    fixture = json.loads(fixture_bytes)
    tasks = fixture["tasks"]
    assert len(tasks) == fixture["taskCount"] == 20
    digest = hashlib.sha256()
    with args.model_file.open("rb") as source:
        for block in iter(lambda: source.read(8 * 1024 * 1024), b""):
            digest.update(block)
    if digest.hexdigest() != identity["sha256"] or args.model_file.stat().st_size != identity["bytes"]:
        raise SystemExit("Actual model bytes differ from frozen identity")
    if importlib.metadata.version("litert-lm-api") != args.runtime:
        raise SystemExit("Installed native API differs from frozen runtime")
    import litert_lm
    protocol = matrix["protocol"]
    resource.setrlimit(resource.RLIMIT_AS, (protocol["addressSpaceGiB"] * 1024**3,) * 2)
    resource.setrlimit(resource.RLIMIT_CPU, (protocol["cpuTimeSeconds"],) * 2)
    report = {"schema": "CONTINUUM_LOCAL_LANGUAGE_BENCHMARK_RESULT_V1", "sourceCommit": sha,
              "fixtureSha256": matrix["fixtureSha256"], "model": args.model_id,
              "modelIdentity": identity, "runtime": args.runtime, "protocol": protocol,
              "status": "LOADING", "totalTasks": len(tasks), "cases": [],
              "device": "NOT_RUN", "tools": [], "training": False, "promotion": False}
    args.new_report.parent.mkdir(parents=True, exist_ok=True)

    def save():
        cases = report["cases"]
        report["correct"] = sum(c["outcome"] == "CORRECT" for c in cases)
        report["incorrect"] = sum(c["outcome"] == "INCORRECT" for c in cases)
        report["errors"] = sum(c["outcome"] == "ERROR" for c in cases)
        report["completedTasks"] = report["correct"] + report["incorrect"]
        report["unrun"] = len(tasks) - len(cases)
        report["correctOverAllTasks"] = report["correct"] / len(tasks)
        report["correctOverCompletedTasks"] = (report["correct"] / report["completedTasks"]
                                                 if report["completedTasks"] else None)
        report["peakRssKiB"] = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
        tmp = args.new_report.with_suffix(".tmp")
        tmp.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
        os.replace(tmp, args.new_report)

    save()
    try:
        with litert_lm.ModelInfo(args.model_file) as info:
            report["declaredContextTokens"] = info.max_context_tokens
            report["supportsThinking"] = info.llm.supports_thinking()
            report["supportsFunctionCalling"] = info.llm.supports_function_calling()
        started = time.monotonic()
        with litert_lm.Engine(str(args.model_file), backend=litert_lm.Backend.CPU(thread_count=2),
                              max_num_tokens=protocol["contextTokens"]) as engine:
            report["loadSeconds"] = time.monotonic() - started
            report["status"] = "RUNNING"; save()
            for task in tasks:
                row = {"id": task["id"], "category": task["category"]}
                started = time.monotonic()
                try:
                    with engine.create_conversation(
                            tools=[], automatic_tool_calling=False,
                            max_output_tokens=protocol["maxOutputTokens"],
                            thinking_config=litert_lm.ThinkingConfig(enable_thinking=False),
                            sampler_config=litert_lm.SamplerConfig(top_k=1, top_p=1.0, temperature=1.0, seed=73),
                            system_message=fixture["systemPrompt"]) as conversation:
                        message = conversation.send_message(task["prompt"])
                        text = str(message.contents)
                        normalized = normalize(text)
                        correct = not message.tool_calls and normalized is not None and normalized in {
                            normalize(a) for a in task["acceptedAnswers"]}
                        row.update(rawResponse=text, normalizedResponse=normalized,
                                   toolCalls=len(message.tool_calls), channels=list(message.channels),
                                   outcome="CORRECT" if correct else "INCORRECT",
                                   stopReason="NATIVE_RETURNED_EXACT_EOS_VS_BUDGET_NOT_EXPOSED")
                except Exception as error:
                    row.update(outcome="ERROR", error=type(error).__name__ + ": " + str(error)[:300],
                               stopReason="RUNTIME_ERROR")
                row["seconds"] = time.monotonic() - started
                report["cases"].append(row); save()
        report["status"] = "MEASURED_HOST_ONLY"
    except Exception as error:
        report.update(status="FAILED_NATIVE_RUNTIME", error=type(error).__name__ + ": " + str(error)[:300])
        save()
        raise
    save()
    print(json.dumps({k: report[k] for k in ("status", "model", "runtime", "correct", "incorrect", "errors", "unrun", "peakRssKiB")}))


if __name__ == "__main__":
    main()
