#!/usr/bin/env python3
"""Bounded, resumable orchestration of the preregistered offline LM1 experiment."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import signal
import subprocess
import tempfile
import time

BASE_SHA = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
TOKENIZER_SHA = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
HOLDOUT_SHA = "d5a5858f0eaff24495cd98c337fc662271c4a1b41027b1142764735c244942af"
SEED = "20261104"
MAX_CHECKPOINT = 64 * 1024 * 1024
PACKAGE = "at.heinz.deviceai.learning.lm."
RESOURCE_LIMITS = {"firstTokenCallMs": 1500, "generation32Ms": 5000, "vmHwmKiB": 768 * 1024,
                   "benchmarkProcessSeconds": 120, "trainingProcessSeconds": 600}
# Published ee577972 executed the original receipt-bound experiment. Its immutable
# Kotlin/JAR and protocol identities are still verified before legacy proof reuse.
HISTORICAL_ORCHESTRATOR_SHA = "609215b0f2671e2407c8b81867255bd1ce60680626c081fd91b0a12d14f62deb"
CORE_HOLDOUT_SHA = "a910f25ed4a54742e87c3e75c105472f0c882df2d4026e15a62ee3d48c90bbbd"
CORE_JAR_SHA = "1b6d22ee57977a6e27ec2d0b72e57fab3a35fb8e694905c1270a4ffe87a2f9ba"
CORE_PROFILES = {
    "core-6562304": {"parameterBudget": 6562304, "targetLabel": "width-8", "targetParameters": 6562304,
                     "nextLabel": "depth-3", "nextParameters": 9712128},
    "core-9712128": {"parameterBudget": 10000000, "targetLabel": "depth-3", "targetParameters": 9712128,
                     "nextLabel": "depth-4", "nextParameters": 12861952},
}


def sha(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def path_present(path: Path) -> bool:
    """A dangling marker is still present and must never reopen a closed phase."""
    return path.exists() or path.is_symlink()


def write_new(path: Path, value: dict) -> None:
    """Publish complete JSON atomically, without replacing an existing marker."""
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, staged = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, ensure_ascii=False)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        # A same-directory hard link publishes once, including against symlinks.
        os.link(staged, path)
    finally:
        os.unlink(staged)


def records(path: Path) -> list[dict]:
    result = []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        fields = line.split("\t")
        if len(fields) > 1 and all("=" in item for item in fields[1:]):
            result.append({"kind": fields[0], **dict(item.split("=", 1) for item in fields[1:])})
    return result


class Experiment:
    def __init__(self, args: argparse.Namespace):
        self.profile = getattr(args, "profile", "historical")
        if self.profile not in {"historical", *CORE_PROFILES}:
            raise ValueError("unknown registered research profile")
        self.inputs = args.inputs.resolve()
        self.requested_out = Path(os.path.abspath(args.output))
        self.out = args.output.resolve()
        self.jar = args.jar.resolve()
        self.holdout = args.holdout.resolve()
        self.source = Path(__file__).resolve().parent
        self.orchestrator_path = Path(__file__).resolve()
        self.orchestrator_sha = sha(Path(__file__).resolve())
        self.tokenizer = self.inputs / "lm1-tokenizer.lmbp"
        self.base = self.inputs / "lm1-baseline.lm1"
        self.development = self.source / "development.txt"
        self.store = self.out / "checkpoints"
        self.logs = self.out / "logs"
        study_root = getattr(args, "study_root", None)
        if self.is_core_study:
            if study_root is None:
                raise ValueError("registered core profiles require --study-root")
            self.study_root = Path(study_root).resolve()
            if self.out != self.study_root / self.profile:
                raise ValueError("profile output must be its named directory under the study root")
            if path_present(self.study_frozen) and not (self.out / "environment.json").is_file():
                raise RuntimeError("a frozen study cannot recreate a missing child experiment")
        elif study_root is not None:
            raise ValueError("the historical profile does not use a study root")
        self.verify_output_scope()
        self.logs.mkdir(parents=True, exist_ok=True)
        self.store.mkdir(parents=True, exist_ok=True)
        if sha(self.base) != BASE_SHA or sha(self.tokenizer) != TOKENIZER_SHA:
            raise ValueError("input checkpoint/tokenizer hash mismatch")
        # Hashing bytes does not expose the sealed holdout text to selection.
        if sha(self.holdout) != self.holdout_sha:
            raise ValueError("sealed holdout hash mismatch")
        if self.is_core_study:
            self.study_registration(allow_create=True)
        if not (self.out / "environment.json").exists():
            write_new(self.out / "environment.json", {
                "createdUtc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                "platform": list(os.uname()), "cpus": os.cpu_count(),
                "javaVersion": subprocess.run(["java", "-version"], capture_output=True, text=True).stderr,
                "jarSha256": sha(self.jar), "protocolSha256": sha(self.source / "PROTOCOL.md"),
                "orchestratorSha256": self.orchestrator_sha,
                "developmentSha256": sha(self.development), "holdoutSha256": self.holdout_sha,
                "baseSha256": BASE_SHA, "tokenizerSha256": TOKENIZER_SHA,
                "sourceHashes": {p.name: sha(p) for p in sorted(self.source.glob("*.kt"))},
                "trainingHeap": "3g", "benchmarkHeap": "1g", "inferenceThreads": 1,
                "limits": {"firstTokenCallMs": 1500, "generation32Ms": 5000, "vmHwmKiB": 768 * 1024,
                           "benchmarkProcessSeconds": 120, "trainingProcessSeconds": 600},
                **self.profile_identity(),
            })
        else:
            previous = json.loads((self.out / "environment.json").read_text())
            if previous["jarSha256"] != sha(self.jar) or previous["protocolSha256"] != sha(self.source / "PROTOCOL.md"):
                raise ValueError("compiled program or frozen protocol changed; use a new experiment directory")
        self.environment = json.loads((self.out / "environment.json").read_text())
        if self.is_core_study:
            self.runtime_identity()
        self.development_sha = self.environment["developmentSha256"]
        if sha(self.development) != self.development_sha:
            raise ValueError("frozen development data changed")
        if (self.logs / "prepare-corpus.receipt.json").exists():
            self.training_hash()

    @property
    def is_core_study(self) -> bool:
        return getattr(self, "profile", "historical") in CORE_PROFILES

    @property
    def holdout_sha(self) -> str:
        return CORE_HOLDOUT_SHA if self.is_core_study else HOLDOUT_SHA

    @property
    def study_frozen(self) -> Path:
        return self.study_root / "study-frozen-selection.json"

    def verify_output_scope(self) -> None:
        for output in {self.out, getattr(self, "requested_out", self.out)}:
            parent = output.parent
            if (output.name in CORE_PROFILES
                    and any(path_present(parent / name) for name in
                            ("study-registration.json", "study-frozen-selection.json"))
                    and (not self.is_core_study or self.profile != output.name
                         or getattr(self, "study_root", None) != parent.resolve())):
                raise RuntimeError("registered study child output cannot be reopened under another profile")

    def profile_descriptor(self, profile: str) -> dict:
        return {"profileId": profile, **CORE_PROFILES[profile],
                "studyPlanSha256": sha(self.source / "CORE_6_10M_PLAN.md")}

    @staticmethod
    def document_sha(value: dict) -> str:
        return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest()

    def study_registration(self, *, allow_create: bool = False) -> str:
        """Register this fixed study once; never adopt edited or missing prior identity."""
        if sha(self.orchestrator_path) != self.orchestrator_sha or sha(self.jar) != CORE_JAR_SHA:
            raise RuntimeError("registered research source or approved experiment JAR changed")
        expected = {
            "schemaVersion": 1, "studyId": "G1-INTEGRATE-WEIGHT-002-CORE-6-10M",
            "studyRoot": str(self.study_root), "inputs": str(self.inputs), "jar": str(self.jar),
            "holdout": str(self.holdout), "holdoutSha256": self.holdout_sha,
            "orchestratorSha256": self.orchestrator_sha, "jarSha256": CORE_JAR_SHA,
            "protocolSha256": sha(self.source / "PROTOCOL.md"),
            "studyPlanSha256": sha(self.source / "CORE_6_10M_PLAN.md"),
            "baseSha256": BASE_SHA, "tokenizerSha256": TOKENIZER_SHA,
            "developmentSha256": sha(self.development), "limits": RESOURCE_LIMITS,
            "profiles": {name: {**self.profile_descriptor(name),
                                 "profileSha256": self.document_sha(self.profile_descriptor(name)),
                                 "output": str(self.study_root / name)} for name in CORE_PROFILES},
        }
        path = self.study_root / "study-registration.json"
        if not path_present(path):
            if (not allow_create or path_present(self.study_frozen)
                    or any((self.study_root / name / "environment.json").exists() for name in CORE_PROFILES)):
                raise RuntimeError("required original study registration is missing")
            write_new(path, expected)
        snapshot = path.read_bytes()
        if json.loads(snapshot) != expected:
            raise RuntimeError("registered study source, profiles, data or runtime changed")
        return hashlib.sha256(snapshot).hexdigest()

    def profile_identity(self) -> dict[str, str]:
        if not self.is_core_study:
            return {}
        descriptor = self.profile_descriptor(self.profile)
        return {"profileId": self.profile, "profileSha256": self.document_sha(descriptor),
                "studyPlanSha256": descriptor["studyPlanSha256"],
                "studyRegistrationSha256": self.study_registration()}

    def verify_profile_manifest(self, value: dict) -> None:
        if any(value.get(key) != expected for key, expected in self.profile_identity().items()):
            raise RuntimeError("manifest does not belong to the registered research profile")

    def budget_stop(self, label: str, parameters: int) -> dict | None:
        if not self.is_core_study or parameters <= CORE_PROFILES[self.profile]["parameterBudget"]:
            return None
        profile = CORE_PROFILES[self.profile]
        if (label, parameters) != (profile["nextLabel"], profile["nextParameters"]):
            raise RuntimeError("budget stop is not the next registered ladder stage")
        return {"label": label, "parameters": parameters, "parameterBytes": parameters * 4,
                "status": "PREFLIGHT_REJECTED", "reason": "BUDGET_LIMIT",
                "parameterLimit": profile["parameterBudget"], **self.profile_identity()}

    def study_snapshot(self) -> dict:
        """Validate both completed children without opening data or starting a process."""
        if not self.is_core_study:
            raise RuntimeError("the historical experiment has no joint study freeze")
        registration = self.study_registration()
        selections = {}
        candidates = []
        datasets = set()
        training_schedules = set()
        for profile in CORE_PROFILES:
            child = copy.copy(self)
            child.profile = profile
            child.out = self.study_root / profile
            child.requested_out = child.out
            child.store = child.out / "checkpoints"
            child.logs = child.out / "logs"
            child.environment = json.loads((child.out / "environment.json").read_bytes())
            child.development_sha = child.environment["developmentSha256"]
            frozen, frozen_sha = child.verify_frozen_selection(*child.continuation_preflight())
            if frozen.get("matchedTrainingInputs") is not True or frozen.get("trainingFailures") != []:
                raise RuntimeError("both study profiles require completed matched training pairs")
            for candidate in frozen["candidates"]:
                if "trainingInput" not in candidate:
                    continue
                training = candidate["training"]
                inputs = candidate["trainingInput"]
                if (int(training["steps"]) != 16 or int(training["tokens"]) <= 0
                        or training["scheduleSha256"] != inputs["scheduleSha256"]):
                    raise RuntimeError("study training did not complete the registered schedule")
                training_schedules.add((inputs["datasetSha256"], inputs["tokenizerSha256"],
                                        inputs["tokenScheduleSha256"], inputs["scheduleSha256"],
                                        int(training["tokens"])))
            datasets.add(frozen["trainingSha256"])
            selections[profile] = {"selectionSha256": frozen_sha, **child.profile_identity()}
            candidates.extend({**candidate, "studyProfileId": profile} for candidate in frozen["candidates"])
        if len(datasets) != 1 or len(training_schedules) != 1:
            raise RuntimeError("study profiles did not use the same registered training data and token schedule")
        preferred = self.preferred_candidate(candidates)
        if preferred is None:
            raise RuntimeError("joint study has no capacity-eligible development candidate")
        result = {"schemaVersion": 1, "studyRegistrationSha256": registration,
                  "orchestratorSha256": self.orchestrator_sha, "holdoutSha256": self.holdout_sha,
                  "holdoutOpenedAtFreeze": False, "profiles": selections,
                  "developmentPreferred": {key: preferred[key] for key in
                      ("studyProfileId", "label", "path", "sha256", "parameters", "bytes", "devBpb")}}
        if (self.study_registration() != registration
                or any(sha(self.study_root / profile / "frozen-selection.json") != entry["selectionSha256"]
                       for profile, entry in selections.items())):
            raise RuntimeError("study selection changed during joint freeze validation")
        return result

    def verify_study_freeze(self) -> str:
        snapshot = self.study_frozen.read_bytes()
        if json.loads(snapshot) != self.study_snapshot() or self.study_frozen.read_bytes() != snapshot:
            raise RuntimeError("joint study freeze differs from the verified child selections")
        return hashlib.sha256(snapshot).hexdigest()

    def study_freeze(self) -> None:
        if not self.is_core_study:
            raise RuntimeError("study-freeze requires a registered core profile")
        if path_present(self.study_frozen):
            self.verify_study_freeze()
            return
        frozen = self.study_snapshot()
        write_new(self.study_frozen, frozen)
        self.verify_study_freeze()

    def training_hash(self) -> str:
        """The original successful corpus receipt seals the continuation dataset."""
        path = self.logs / "prepare-corpus.tsv"
        receipt = json.loads((self.logs / "prepare-corpus.receipt.json").read_text())
        expected_arguments = ["corpus", str(self.inputs / "original-train-docs"), str(self.out / "train.txt")]
        if (receipt["arguments"] != expected_arguments or receipt["logSha256"] != sha(path)
                or receipt["returncode"] != 0 or receipt.get("timeout", False)
                or receipt.get("identityValidationFailure")):
            raise RuntimeError("invalid original training-corpus receipt")
        expected = self.one(records(path), "CORPUS")["sha256"]
        if sha(self.out / "train.txt") != expected:
            raise RuntimeError("continuation data no longer matches the original CORPUS record")
        if self.is_core_study:
            self.cached_call("prepare-corpus", expected_arguments, benchmark=False, timeout=180, allow_failure=False)
        return expected

    def dataset_hash(self, path: Path) -> str:
        path = path.resolve()
        if path == self.development.resolve():
            expected = self.development_sha
        elif path == (self.out / "train.txt").resolve():
            expected = self.training_hash()
        elif path == self.holdout:
            expected = self.holdout_sha
        else:
            raise ValueError("unregistered experiment dataset")
        if sha(path) != expected:
            raise RuntimeError("dataset differs from its frozen identity")
        return expected

    def input_hashes(self, arguments: list[str], benchmark: bool) -> dict[str, str]:
        if not benchmark and arguments[0] == "corpus":
            root = Path(arguments[1])
            digest = hashlib.sha256()
            for document in sorted(root.glob("*.md")):
                digest.update(document.name.encode("utf-8") + b"\0")
                digest.update(sha(document).encode("ascii") + b"\0")
            return {"corpusManifestSha256": sha(root / "paths.tsv"),
                    "corpusDocumentsSha256": digest.hexdigest()}
        offset = 0 if benchmark else 1
        checkpoint = Path(arguments[offset])
        result = {"checkpointSha256": sha(checkpoint), "tokenizerSha256": sha(Path(arguments[offset + 1]))}
        if result["tokenizerSha256"] != TOKENIZER_SHA:
            raise RuntimeError("tokenizer changed")
        if checkpoint.resolve() == self.base.resolve() and result["checkpointSha256"] != BASE_SHA:
            raise RuntimeError("baseline changed")
        if not benchmark and arguments[0] in {"evaluate", "train"}:
            result["datasetSha256"] = self.dataset_hash(Path(arguments[3]))
        if not benchmark and arguments[0] == "parity":
            result["comparisonCheckpointSha256"] = sha(Path(arguments[3]))
        return result

    def verify_records(self, rows: list[dict], arguments: list[str], benchmark: bool,
                       inputs: dict[str, str], *, successful: bool, legacy: bool) -> None:
        """Legacy receipts need identity evidence in the JVM log, never just matching paths."""
        def check_record(kind: str, expected: dict[str, str], required: bool) -> None:
            matching = [row for row in rows if row["kind"] == kind]
            if not matching and not required:
                return
            row = self.one(rows, kind)
            if any(row.get(key) != value for key, value in expected.items()):
                raise RuntimeError(f"{kind} input identity mismatch")

        required = successful or legacy
        if benchmark:
            check_record("CAPACITY_MODEL", {key: inputs[key] for key in ("checkpointSha256", "tokenizerSha256")}, required)
        elif arguments[0] == "corpus":
            if required:
                check_record("CORPUS", {"sha256": sha(Path(arguments[2]))}, True)
        else:
            check_record("SOURCE", {"sha256": inputs["checkpointSha256"],
                                    "tokenizerSha256": inputs["tokenizerSha256"]}, required)
            if arguments[0] in {"evaluate", "train"}:
                kind = "EVALUATION" if arguments[0] == "evaluate" else "TRAIN_INPUT"
                check_record(kind, {key: inputs[key] for key in ("datasetSha256", "tokenizerSha256")}, required)
            if arguments[0] == "parity" and legacy:
                raise RuntimeError("legacy parity receipt does not bind the comparison checkpoint; explicit review required")

    def runtime_identity(self) -> dict[str, str]:
        self.verify_output_scope()
        if self.environment.get("limits") != RESOURCE_LIMITS:
            raise RuntimeError("registered resource limits changed")
        identity = {"jarSha256": sha(self.jar), "protocolSha256": sha(self.source / "PROTOCOL.md")}
        if any(identity[key] != self.environment.get(key) for key in identity):
            raise RuntimeError("compiled program or registered protocol changed")
        if self.is_core_study:
            identity.update(self.profile_identity())
            if (any(identity[key] != self.environment.get(key) for key in identity)
                    or self.environment.get("orchestratorSha256") != self.orchestrator_sha
                    or self.environment.get("holdoutSha256") != self.holdout_sha):
                raise RuntimeError("registered research profile or source environment changed")
        return identity

    def valid_orchestrator(self, value: object) -> bool:
        if self.is_core_study:
            return value == self.orchestrator_sha == self.environment.get("orchestratorSha256")
        return isinstance(value, str) and value in {
            self.orchestrator_sha, self.environment.get("orchestratorSha256"),
            HISTORICAL_ORCHESTRATOR_SHA,
        }

    def cached_call(self, label: str, arguments: list[str], *, benchmark: bool,
                    timeout: int, allow_failure: bool,
                    expected_inputs: dict[str, str] | None = None) -> tuple[list[dict], dict]:
        """Read existing evidence only; never repair missing proof by starting a process."""
        path = self.logs / f"{label}.tsv"
        receipt = self.logs / f"{label}.receipt.json"
        if not path.is_file() or not receipt.is_file():
            raise RuntimeError(f"required completed invocation is missing: {label}")
        identity = self.runtime_identity()
        inputs = self.input_hashes(arguments, benchmark)
        if expected_inputs is not None and inputs != expected_inputs:
            raise RuntimeError(f"authorized inputs changed before receipt reuse: {label}")
        saved = json.loads(receipt.read_text())
        main = PACKAGE + ("LmCapacityBenchmark" if benchmark else "LmWeightExperiment")
        command = ["java", "-Xms64m", "-Xmx1g" if benchmark else "-Xmx3g",
                   "-cp", str(self.jar), main, *arguments]
        if (saved.get("logSha256") != sha(path) or saved.get("arguments") != arguments
                or saved.get("command") != command or saved.get("timeoutSeconds") != timeout
                or saved.get("identityValidationFailure")):
            raise RuntimeError(f"invocation identity or execution contract changed: {label}")
        if type(saved.get("returncode")) is not int or type(saved.get("timeout")) is not bool:
            raise RuntimeError(f"incomplete execution status: {label}")
        modern = "inputHashes" in saved or "orchestratorSha256" in saved
        if self.is_core_study and (not modern or saved.get("runtimeIdentity") != identity):
            raise RuntimeError(f"registered profile requires a complete bound invocation receipt: {label}")
        if modern and (saved.get("inputHashes") != inputs
                       or not self.valid_orchestrator(saved.get("orchestratorSha256"))):
            raise RuntimeError(f"invocation input or orchestrator binding changed: {label}")
        if "runtimeIdentity" in saved and saved["runtimeIdentity"] != identity:
            raise RuntimeError(f"invocation runtime binding changed: {label}")
        result = records(path)
        successful = saved["returncode"] == 0 and not saved["timeout"]
        self.verify_records(result, arguments, benchmark, inputs, successful=successful, legacy=not modern)
        if not successful and not allow_failure:
            raise RuntimeError(f"previous invocation failed: {label}")
        return result, saved

    def call(self, label: str, arguments: list[str], *, benchmark: bool = False,
             timeout: int = 180, allow_failure: bool = False, cache_only: bool = False) -> list[dict]:
        authorized = None
        if not benchmark and arguments and arguments[0] == "train":
            authorized = self.authorize_training_call(label, arguments, timeout)
        path = self.logs / f"{label}.tsv"
        receipt = self.logs / f"{label}.receipt.json"
        identity = self.runtime_identity()
        inputs = self.input_hashes(arguments, benchmark)
        if authorized is not None and (inputs, identity) != authorized:
            raise RuntimeError("training inputs changed after source-bound authorization")
        if cache_only or path.exists() or receipt.exists():
            return self.cached_call(label, arguments, benchmark=benchmark, timeout=timeout,
                                    allow_failure=allow_failure, expected_inputs=inputs)[0]
        main = PACKAGE + ("LmCapacityBenchmark" if benchmark else "LmWeightExperiment")
        command = ["java", "-Xms64m", "-Xmx1g" if benchmark else "-Xmx3g", "-cp", str(self.jar), main, *arguments]
        if self.input_hashes(arguments, benchmark) != inputs or self.runtime_identity() != identity:
            raise RuntimeError("authorized inputs or runtime changed before process launch")
        print(f"START {label}", flush=True)
        start = time.monotonic()
        timed_out = False
        with path.open("xb") as log:
            process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                returncode = process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                timed_out = True
                os.killpg(process.pid, signal.SIGKILL)
                returncode = process.wait()
        elapsed = time.monotonic() - start
        result = records(path)
        identity_failure = None
        try:
            if self.input_hashes(arguments, benchmark) != inputs:
                raise RuntimeError("input bytes changed during invocation")
            if self.runtime_identity() != identity:
                raise RuntimeError("runtime changed during invocation")
            self.verify_records(result, arguments, benchmark, inputs,
                                successful=returncode == 0 and not timed_out, legacy=False)
        except (OSError, ValueError, RuntimeError, KeyError) as error:
            identity_failure = f"{type(error).__name__}: {error}"
        write_new(receipt, {"arguments": arguments, "command": command, "returncode": returncode,
                           "timeout": timed_out, "timeoutSeconds": timeout, "wallSeconds": elapsed,
                           "logSha256": sha(path), "inputHashes": inputs,
                           "orchestratorSha256": self.orchestrator_sha,
                           "runtimeIdentity": identity,
                           "identityValidationFailure": identity_failure})
        highlights = [r for r in result if r["kind"] in {"EVALUATION", "CAPACITY_RESULT", "PARITY", "TRAIN_RESULT", "PROBE_RESULT", "CHECKPOINT", "CORPUS"}]
        print(json.dumps({"finished": label, "returncode": returncode, "seconds": elapsed,
                          "highlights": highlights}, ensure_ascii=False), flush=True)
        if identity_failure:
            raise RuntimeError(f"{label} identity verification failed: {identity_failure}")
        if returncode and not allow_failure:
            raise RuntimeError(f"{label} failed; inspect {path}")
        return result

    def invoke(self, label: str, mode: str, checkpoint: Path, *extra: object, **options) -> list[dict]:
        return self.call(label, [mode, str(checkpoint), str(self.tokenizer), *(str(x) for x in extra)], **options)

    @staticmethod
    def one(rows: list[dict], kind: str) -> dict:
        matching = [r for r in rows if r["kind"] == kind]
        if len(matching) != 1:
            raise RuntimeError(f"expected one {kind}, found {len(matching)}")
        return matching[0]

    @staticmethod
    def matched_training_inputs(candidates: list[dict]) -> bool:
        inputs = [c["trainingInput"] for c in candidates if "trainingInput" in c]
        fields = ("datasetSha256", "tokenScheduleSha256", "tokenizerSha256")
        if any(not row.get(key) for row in inputs for key in fields):
            raise RuntimeError("continuation lacks a complete TRAIN_INPUT identity")
        if len(inputs) > 1 and any(row[key] != inputs[0][key] for row in inputs[1:] for key in fields):
            raise RuntimeError("small and expanded continuation used different training data or token schedules")
        return len(inputs) == 2

    @staticmethod
    def preferred_candidate(candidates: list[dict]) -> dict | None:
        eligible = [c for c in candidates if c.get("capacityEligible") is True]
        if not eligible:
            return None
        minimum_bpb = min(c["devBpb"] for c in eligible)
        equivalent = [c for c in eligible if c["devBpb"] <= minimum_bpb + 0.00001]
        return min(equivalent, key=lambda c: (c["parameters"], c["devBpb"]))

    def candidate(self, rows: list[dict], label: str) -> dict:
        row = self.one(rows, "CHECKPOINT")
        if sha(Path(row["path"])) != row["sha256"]:
            raise RuntimeError("generated checkpoint hash mismatch")
        return {"label": label, "path": row["path"], "sha256": row["sha256"],
                "parameters": int(row["parameters"]), "bytes": int(row["bytes"])}

    @staticmethod
    def verify_candidate(candidate: dict) -> None:
        path = Path(candidate["path"])
        if (path.is_symlink() or not path.is_file() or not 1 <= path.stat().st_size <= MAX_CHECKPOINT
                or candidate.get("bytes") != path.stat().st_size
                or type(candidate.get("parameters")) is not int
                or not 1 <= candidate["parameters"] <= MAX_CHECKPOINT // 4
                or sha(path) != candidate["sha256"]):
            raise RuntimeError(f"candidate checkpoint changed: {candidate['label']}")

    def score(self, candidate: dict, suffix: str = "dev", *, cache_only: bool = False) -> dict:
        if suffix not in {"dev", "holdout"}:
            raise ValueError("unknown evaluation split")
        self.verify_candidate(candidate)
        dataset = self.development if suffix == "dev" else self.holdout
        expected_dataset = self.dataset_hash(dataset)
        rows = self.invoke(f"{candidate['label']}-{suffix}", "evaluate", Path(candidate["path"]), dataset,
                           cache_only=cache_only)
        source = self.one(rows, "SOURCE")
        score = self.one(rows, "EVALUATION")
        if (source["sha256"] != candidate["sha256"] or source["tokenizerSha256"] != TOKENIZER_SHA
                or score["datasetSha256"] != expected_dataset or score["tokenizerSha256"] != TOKENIZER_SHA):
            raise RuntimeError("score does not belong to the frozen candidate and dataset")
        value = float(score["bpb"])
        if not math.isfinite(value):
            raise RuntimeError("non-finite BPB")
        candidate[f"{suffix}Bpb"] = value
        return score

    def benchmark(self, candidate: dict, *, require_existing: bool = False) -> bool:
        self.verify_candidate(candidate)
        result = self.call(f"{candidate['label']}-capacity", [candidate["path"], str(self.tokenizer)],
                           benchmark=True, timeout=120, allow_failure=True, cache_only=require_existing)
        rows = [r for r in result if r["kind"] == "CAPACITY_RESULT"]
        receipt = json.loads((self.logs / f"{candidate['label']}-capacity.receipt.json").read_text())
        candidate["capacity"] = rows[0] if len(rows) == 1 else {"pass": "false", "reasons": "MISSING_FINAL_RESULT"}
        candidate["capacity"]["processReturncode"] = receipt["returncode"]
        candidate["capacity"]["processTimedOut"] = receipt["timeout"]
        candidate["capacityEligible"] = (receipt["returncode"] == 0 and not receipt["timeout"]
                and candidate["capacity"]["pass"] == "true"
                and candidate["capacity"].get("checkpointSha256") == candidate["sha256"]
                and candidate["capacity"].get("measuredRepeats") == "3"
                and candidate["capacity"].get("firstTokenObservations") == "3"
                and candidate["capacity"].get("full32TokenObservations") == "3"
                and self.capacity_workload_passes(candidate, result, receipt))
        return candidate["capacityEligible"]

    def capacity_workload_passes(self, candidate: dict, rows: list[dict], receipt: dict) -> bool:
        """Recompute the declared gates from the workload, not saved eligibility flags."""
        try:
            protocol = self.one(rows, "CAPACITY_PROTOCOL")
            expected = {"firstTokenLimitMs": 1500, "generation32LimitMs": 5000,
                        "vmHwmLimitKiB": 768 * 1024, "requestMaxMillis": 30000,
                        "warmups": 1, "measuredRepeats": 3}
            if any(float(protocol[key]) != value for key, value in expected.items()):
                return False
            if (protocol.get("ttftMethod") != "SEPARATE_ONE_TOKEN_CALL_UPPER_BOUND"
                    or protocol.get("processTimeoutRequired") != "true"
                    or protocol.get("promptSha256") != "9c7bfc2a17f395c0219273f50652181d825c4712715339083daa71717e6ff495"):
                return False
            model = self.one(rows, "CAPACITY_MODEL")
            if (model["checkpointSha256"] != candidate["sha256"]
                    or model["tokenizerSha256"] != TOKENIZER_SHA
                    or int(model["parameters"]) != candidate["parameters"]
                    or int(model["parameterBytes"]) != 4 * candidate["parameters"]
                    or int(model["checkpointBytes"]) != Path(candidate["path"]).stat().st_size
                    or model["fullLayoutLoaded"] != "true" or model["finiteWeights"] != "true"
                    or int(model["promptTokens"]) != 64):
                return False
            wall = float(receipt["wallSeconds"])
            if not math.isfinite(wall) or not 0 <= wall <= 120:
                return False
            trials = [row for row in rows if row["kind"] == "CAPACITY_TRIAL"]
            expected_keys = {(phase, index, requested) for phase, n in (("warmup", 1), ("measured", 3))
                             for index in range(1, n + 1) for requested in (1, 32)}
            actual_keys = [(r["phase"], int(r["trial"]), int(r["requestedNewTokens"])) for r in trials]
            if len(actual_keys) != len(expected_keys) or set(actual_keys) != expected_keys:
                return False
            for row, (_, _, requested) in zip(trials, actual_keys):
                elapsed = float(row["elapsedMs"])
                actual = int(row["actualNewTokens"])
                if (not math.isfinite(elapsed) or elapsed < 0 or not 0 <= actual <= requested
                        or row["stop"] not in {"MAX_TOKENS", "EOS"} or row["promptTruncated"] != "false"
                        or int(row["promptTokens"]) != 64):
                    return False
                if row["stop"] == "MAX_TOKENS" and actual != requested:
                    return False
                if row["phase"] == "measured" and (actual != requested or elapsed > (1500 if requested == 1 else 5000)):
                    return False
            memory = [int(r["vmHwmKiB"]) for r in rows if r["kind"] == "CAPACITY_MEMORY"]
            summary = self.one(rows, "CAPACITY_RESULT")
            for requested, key in ((1, "maxFirstTokenCallMs"), (32, "maxGeneration32CallMs")):
                measured = [float(r["elapsedMs"]) for r in trials
                            if r["phase"] == "measured" and int(r["requestedNewTokens"]) == requested]
                if float(summary[key]) != max(measured):
                    return False
            return bool(memory) and min(memory) >= 0 and max(*memory, int(summary["vmHwmKiB"])) <= 768 * 1024
        except (KeyError, ValueError, TypeError, OverflowError):
            return False

    def load_calibration(self, *, cache_only: bool = False) -> dict:
        calibration = json.loads((self.out / "calibration.json").read_text())
        self.verify_profile_manifest(calibration)
        for candidate in calibration["candidates"]:
            if not (self.logs / f"{candidate['label']}-dev.receipt.json").exists():
                raise RuntimeError("completed calibration lacks a development receipt")
            recorded = candidate["devBpb"]
            self.score(candidate, cache_only=cache_only)
            if candidate["devBpb"] != recorded:
                raise RuntimeError("calibration score differs from its verified evaluation")
        expected = min(calibration["candidates"], key=lambda c: (c["devBpb"], c["label"] != "baseline"))
        if expected["sha256"] != calibration["selected"]["sha256"]:
            raise RuntimeError("calibration selection differs from the fixed selection rule")
        calibration["selected"] = dict(expected)
        return calibration

    @staticmethod
    def candidate_identity(candidate: dict) -> tuple:
        return (candidate["label"], str(Path(candidate["path"]).resolve()), candidate["sha256"],
                candidate["parameters"], candidate["bytes"])

    def original_baseline(self, calibration: dict) -> dict:
        candidates = [c for c in calibration["candidates"]
                      if c.get("label") == "baseline" or c.get("sha256") == BASE_SHA
                      or Path(c.get("path", "")).resolve() == self.base.resolve()]
        if len(candidates) != 1:
            raise RuntimeError("calibration must contain exactly one canonical original baseline")
        baseline = dict(candidates[0])
        if (baseline["label"] != "baseline" or baseline["sha256"] != BASE_SHA
                or Path(baseline["path"]).resolve() != self.base.resolve()
                or baseline["parameters"] != 132160):
            raise RuntimeError("calibration baseline does not match the registered original")
        return baseline

    def stage_parity_passes(self, stage: dict, selected: dict) -> bool:
        arguments = ["parity", selected["path"], str(self.tokenizer), stage["path"]]
        rows, receipt = self.cached_call(stage["label"] + "-parity", arguments, benchmark=False,
                                        timeout=180, allow_failure=True)
        if receipt["returncode"] != 0 or receipt["timeout"]:
            return False
        parity = self.one(rows, "PARITY")
        values = [float(parity[k]) for k in ("maxLogitDelta", "maxProbabilityDelta", "maxStepwiseLogitDelta")]
        return (all(math.isfinite(v) and v >= 0 for v in values)
                and values[0] <= 0.001 and values[1] <= 0.00001 and values[2] <= 0.001
                and int(parity["identicalGenerations"]) == 3
                and int(parity["originalParameters"]) == selected["parameters"]
                and int(parity["expandedParameters"]) == stage["parameters"])

    def verify_creation(self, candidate: dict, parent: dict, mode: str, *settings: str) -> None:
        arguments = [mode, parent["path"], str(self.tokenizer), *settings, str(self.store)]
        rows, _ = self.cached_call(candidate["label"] + "-create", arguments, benchmark=False,
                                   timeout=180, allow_failure=False,
                                   expected_inputs={"checkpointSha256": parent["sha256"],
                                                    "tokenizerSha256": TOKENIZER_SHA})
        created = self.candidate(rows, candidate["label"])
        if (self.candidate_identity(created) != self.candidate_identity(candidate)
                or int(self.one(rows, "SOURCE")["parameters"]) != parent["parameters"]):
            raise RuntimeError("checkpoint creation evidence differs from its registered parent or output")

    def continuation_preflight(self) -> tuple[dict, dict, dict]:
        """Reconstruct permission from existing evidence before any continuation."""
        self.runtime_identity()
        ladder_path = self.out / "ladder.json"
        ladder_bytes = ladder_path.read_bytes()
        ladder = json.loads(ladder_bytes)
        self.verify_profile_manifest(ladder)
        # A saved failure is terminal, including contradictory stale success flags.
        if ladder.get("stop") == "BASELINE_GATE" or not isinstance(ladder.get("lastPassed"), dict):
            raise RuntimeError("BASELINE_GATE: continuation has no authorized starting checkpoint")
        calibration_path = self.out / "calibration.json"
        calibration_sha = sha(calibration_path)
        calibration = self.load_calibration(cache_only=True)
        original = self.original_baseline(calibration)
        selected = dict(calibration["selected"])
        if selected["sha256"] != original["sha256"]:
            settings = {"calibration-" + tensor.replace(".", "-") + "-" + factor: (tensor, factor)
                        for tensor in ("l0.n1", "l1.n1", "nf") for factor in ("0.75", "1.25")}
            if selected["label"] not in settings or selected["parameters"] != original["parameters"]:
                raise RuntimeError("selected calibration is not a registered rescale candidate")
            self.verify_creation(selected, original, "rescale", *settings[selected["label"]])
        for source in {c["sha256"]: c for c in (original, selected)}.values():
            if not self.benchmark(source, require_existing=True):
                raise RuntimeError(f"BASELINE_GATE: existing capacity proof failed for {source['label']}")
        expected = [(selected["label"], selected["parameters"]), ("width-2", 460928),
                    ("width-4", 1708288), ("width-8", 6562304), ("depth-3", 9712128),
                    ("depth-4", 12861952), ("depth-5", 16011776)]
        stages = ladder.get("stages")
        if not isinstance(stages, list) or not stages:
            raise RuntimeError("ladder lacks its ordered checkpoint evidence")
        last = None
        terminal = None
        verified = {}
        for index, stored in enumerate(stages):
            if not isinstance(stored, dict):
                raise RuntimeError("invalid ladder stage")
            budget = self.budget_stop(*expected[index]) if index < len(expected) else None
            if budget is not None:
                if stored != budget or index != len(stages) - 1:
                    raise RuntimeError("budget rejection differs from the next registered stage")
                terminal = "BUDGET_LIMIT"
                break
            if index == len(expected):
                if (index != len(stages) - 1 or stored.get("label") != "depth-6"
                        or stored.get("parameters") != 19161600
                        or stored.get("parameterBytes") != 76646400
                        or stored.get("parameterBytes", 0) <= MAX_CHECKPOINT
                        or stored.get("status") != "PREFLIGHT_REJECTED"
                        or stored.get("reason") != "CHECKPOINT_64_MIB_CONTRACT"):
                    raise RuntimeError("invalid checkpoint-limit preflight evidence")
                terminal = "CHECKPOINT_64_MIB_CONTRACT"
                break
            if index > len(expected) or (stored.get("label"), stored.get("parameters")) != expected[index]:
                raise RuntimeError("ladder stages do not follow the registered capacity order")
            stage = dict(stored)
            self.verify_candidate(stage)
            if stage["bytes"] != Path(stage["path"]).stat().st_size or stage["bytes"] > MAX_CHECKPOINT:
                raise RuntimeError("ladder checkpoint size changed")
            if index == 0:
                if self.candidate_identity(stage) != self.candidate_identity(selected):
                    raise RuntimeError("ladder starting checkpoint differs from calibration selection")
                passed = self.benchmark(stage, require_existing=True)
            else:
                if index <= 3:
                    self.verify_creation(stage, selected, "widen", str((2, 4, 8)[index - 1]), "0.001", SEED)
                else:
                    self.verify_creation(stage, verified["width-8"], "deepen", str(index - 3), SEED)
                passed = self.stage_parity_passes(stage, selected)
                if passed:
                    passed = self.benchmark(stage, require_existing=True)
            if "capacityEligible" in stored and stored["capacityEligible"] is not passed:
                raise RuntimeError("saved ladder eligibility contradicts its actual evidence")
            if not passed:
                if index == 0 or index != len(stages) - 1:
                    raise RuntimeError("failed ladder gate cannot authorize a following stage")
                terminal = "PARITY_OR_CAPACITY_FAILURE"
                break
            last = stage
            verified[stage["label"]] = stage
        if terminal is None or ladder.get("stop") != terminal or last is None:
            raise RuntimeError("ladder is incomplete or has a contradictory stop reason")
        if self.is_core_study:
            profile = CORE_PROFILES[self.profile]
            if (terminal != "BUDGET_LIMIT" or (last["label"], last["parameters"])
                    != (profile["targetLabel"], profile["targetParameters"])):
                raise RuntimeError("registered profile target did not pass; no continuation is authorized")
        if self.candidate_identity(ladder["lastPassed"]) != self.candidate_identity(last):
            raise RuntimeError("lastPassed is not the last independently verified passing stage")
        if ladder["lastPassed"].get("capacityEligible") is not True:
            raise RuntimeError("lastPassed is not recorded as an eligible checkpoint")
        self.training_hash()
        if sha(calibration_path) != calibration_sha or ladder_path.read_bytes() != ladder_bytes:
            raise RuntimeError("calibration or ladder changed during continuation preflight")
        return original, selected, last

    @staticmethod
    def training_targets(selected: dict, expanded: dict) -> dict[str, dict]:
        targets = {"continued-small": selected}
        if expanded["parameters"] > selected["parameters"]:
            targets["continued-expanded"] = expanded
        return targets

    def authorize_training_call(self, label: str, arguments: list[str], timeout: int) -> tuple[dict, dict]:
        self.verify_output_scope()
        if self.is_core_study and path_present(self.study_frozen):
            raise RuntimeError("joint study freeze permanently closes continuation training")
        _, selected, expanded = self.continuation_preflight()
        if path_present(self.out / "frozen-selection.json"):
            raise RuntimeError("frozen selection does not authorize further continuation")
        targets = self.training_targets(selected, expanded)
        target = targets.get(label.removesuffix("-train")) if label.endswith("-train") else None
        if target is None or timeout != 600:
            raise RuntimeError("training invocation is not in the authorized continuation schedule")
        expected = ["train", target["path"], str(self.tokenizer), str(self.out / "train.txt"), str(self.store)]
        if arguments != expected:
            raise RuntimeError("training invocation differs from its verified source or data")
        self.verify_candidate(target)
        return ({"checkpointSha256": target["sha256"], "tokenizerSha256": TOKENIZER_SHA,
                 "datasetSha256": self.training_hash()}, self.runtime_identity())

    def verify_frozen_selection(self, original: dict, selected: dict, expanded: dict) -> tuple[dict, str]:
        """Validate a completed resume without running training, scoring or inference."""
        path = self.out / "frozen-selection.json"
        frozen_bytes = path.read_bytes()
        frozen = json.loads(frozen_bytes)
        self.verify_profile_manifest(frozen)
        if (not self.valid_orchestrator(frozen.get("orchestratorSha256"))
                or frozen.get("holdoutSha256") != self.holdout_sha or frozen.get("holdoutOpened") is not False
                or frozen.get("developmentSha256") != self.development_sha
                or frozen.get("trainingSha256") != self.training_hash()
                or self.candidate_identity(frozen["lastCapacityPassed"]) != self.candidate_identity(expanded)):
            raise RuntimeError("frozen selection has stale source, data or starting-checkpoint identities")
        candidates = {c["sha256"]: dict(c) for c in (original, selected, expanded)}
        failures = []
        for label, initial in self.training_targets(selected, expanded).items():
            arguments = ["train", initial["path"], str(self.tokenizer), str(self.out / "train.txt"), str(self.store)]
            rows, receipt = self.cached_call(label + "-train", arguments, benchmark=False,
                                            timeout=600, allow_failure=True)
            if receipt["returncode"] != 0 or receipt["timeout"]:
                failures.append({"label": label, "receipt": receipt})
                continue
            candidate = self.candidate(rows, label)
            candidate["training"] = self.one(rows, "TRAIN_RESULT")
            candidate["trainingInput"] = self.one(rows, "TRAIN_INPUT")
            candidates[candidate["sha256"]] = candidate
        for candidate in candidates.values():
            self.score(candidate, cache_only=True)
            self.benchmark(candidate, require_existing=True)
        stored = frozen.get("candidates")
        if (not isinstance(stored, list) or len(stored) != len(candidates)
                or len({c["sha256"] for c in stored}) != len(stored)):
            raise RuntimeError("frozen candidate set is incomplete or duplicated")
        for candidate in stored:
            expected = candidates.get(candidate["sha256"])
            if (expected is None or self.candidate_identity(candidate) != self.candidate_identity(expected)
                    or candidate.get("devBpb") != expected["devBpb"]
                    or candidate.get("capacityEligible") is not expected["capacityEligible"]
                    or candidate.get("training") != expected.get("training")
                    or candidate.get("trainingInput") != expected.get("trainingInput")):
                raise RuntimeError("frozen candidate does not match its verified execution evidence")
        preferred = self.preferred_candidate(list(candidates.values()))
        if (preferred is None or frozen.get("selectionStatus") != "SELECTED"
                or self.candidate_identity(frozen["developmentPreferred"]) != self.candidate_identity(preferred)
                or frozen["developmentPreferred"].get("devBpb") != preferred["devBpb"]
                or frozen.get("matchedTrainingInputs") is not self.matched_training_inputs(list(candidates.values()))
                or frozen.get("trainingFailures") != failures):
            raise RuntimeError("frozen selection differs from the unchanged selection rule")
        if path.read_bytes() != frozen_bytes:
            raise RuntimeError("frozen selection changed during resume validation")
        return frozen, hashlib.sha256(frozen_bytes).hexdigest()

    def calibration(self) -> None:
        if (self.out / "calibration.json").exists():
            self.load_calibration()
            return
        train = self.out / "train.txt"
        self.call("prepare-corpus", ["corpus", str(self.inputs / "original-train-docs"), str(train)])
        self.invoke("baseline-inspect", "inspect", self.base)
        baseline = {"label": "baseline", "path": str(self.base), "sha256": BASE_SHA,
                    "parameters": 132160, "bytes": self.base.stat().st_size}
        self.score(baseline)
        self.invoke("baseline-probes", "probes", self.base)
        self.benchmark(baseline)
        candidates = [baseline]
        for tensor in ["l0.n1", "l1.n1", "nf"]:
            for factor in ["0.75", "1.25"]:
                label = "calibration-" + tensor.replace(".", "-") + "-" + factor
                candidate = self.candidate(self.invoke(label + "-create", "rescale", self.base, tensor, factor, self.store), label)
                self.score(candidate)
                candidates.append(candidate)
        selected = min(candidates, key=lambda c: (c["devBpb"], c["label"] != "baseline"))
        write_new(self.out / "calibration.json", {"candidates": candidates, "selected": selected,
                                                 "rule": "minimum development BPB; identity wins exact tie",
                                                 **self.profile_identity()})

    def ladder(self) -> None:
        if (self.out / "ladder.json").exists():
            if self.is_core_study:
                self.continuation_preflight()
            return
        calibration = self.load_calibration()
        original = self.original_baseline(calibration)
        if not self.benchmark(original, require_existing=True):
            write_new(self.out / "ladder.json", {"stages": [original], "lastPassed": None,
                                                "stop": "BASELINE_GATE", **self.profile_identity()})
            return
        selection = calibration["selected"]
        source = Path(selection["path"])
        selected = dict(selection)
        passed = []
        attempted = []
        if not self.benchmark(selected):
            write_new(self.out / "ladder.json", {"stages": [selected], "lastPassed": None,
                                                "stop": "BASELINE_GATE", **self.profile_identity()})
            return
        passed.append(selected)
        attempted.append(selected)
        stop = None
        for factor in [2, 4, 8]:
            label = f"width-{factor}"
            candidate = self.candidate(self.invoke(label + "-create", "widen", source, factor, "0.001", SEED, self.store), label)
            parity = self.invoke(label + "-parity", "parity", source, candidate["path"], allow_failure=True)
            parity_receipt = json.loads((self.logs / f"{label}-parity.receipt.json").read_text())
            candidate["parity"] = [r for r in parity if r["kind"] == "PARITY"]
            attempted.append(candidate)
            if parity_receipt["returncode"] != 0 or not self.benchmark(candidate):
                stop = "PARITY_OR_CAPACITY_FAILURE"
                break
            passed.append(candidate)
        if stop is None:
            width8 = Path(passed[-1]["path"])
            for layers in [3, 4, 5, 6]:
                parameters = 512 * 512 + 512 + layers * 3_149_824
                budget = self.budget_stop(f"depth-{layers}", parameters)
                if budget is not None:
                    stop = "BUDGET_LIMIT"
                    attempted.append(budget)
                    break
                if parameters * 4 > MAX_CHECKPOINT:
                    stop = "CHECKPOINT_64_MIB_CONTRACT"
                    attempted.append({"label": f"depth-{layers}", "parameters": parameters,
                                      "parameterBytes": parameters * 4, "status": "PREFLIGHT_REJECTED",
                                      "reason": stop})
                    break
                label = f"depth-{layers}"
                candidate = self.candidate(self.invoke(label + "-create", "deepen", width8, layers - 2, SEED, self.store), label)
                parity = self.invoke(label + "-parity", "parity", source, candidate["path"], allow_failure=True)
                parity_receipt = json.loads((self.logs / f"{label}-parity.receipt.json").read_text())
                candidate["parity"] = [r for r in parity if r["kind"] == "PARITY"]
                attempted.append(candidate)
                if parity_receipt["returncode"] != 0 or not self.benchmark(candidate):
                    stop = "PARITY_OR_CAPACITY_FAILURE"
                    break
                passed.append(candidate)
        write_new(self.out / "ladder.json", {"stages": attempted, "lastPassed": passed[-1], "stop": stop,
                                            **self.profile_identity()})

    def continuation(self) -> None:
        self.verify_output_scope()
        if (self.is_core_study and path_present(self.study_frozen)
                and not path_present(self.out / "frozen-selection.json")):
            raise RuntimeError("frozen study cannot resume training after child-selection loss")
        original, selected, expanded = self.continuation_preflight()
        if path_present(self.out / "frozen-selection.json"):
            self.verify_frozen_selection(original, selected, expanded)
            return
        candidates = {c["sha256"]: dict(c) for c in [original, selected]}
        failures = []
        if expanded and expanded["parameters"] > selected["parameters"]:
            candidates[expanded["sha256"]] = dict(expanded)
        for label, initial in [("continued-small", selected), ("continued-expanded", expanded)]:
            if initial is None or (label == "continued-expanded" and initial["parameters"] == selected["parameters"]):
                continue
            self.verify_candidate(initial)
            rows = self.invoke(label + "-train", "train", Path(initial["path"]), self.out / "train.txt", self.store,
                               timeout=600, allow_failure=True)
            receipt = json.loads((self.logs / f"{label}-train.receipt.json").read_text())
            if receipt["returncode"] != 0 or receipt["timeout"]:
                failures.append({"label": label, "receipt": receipt})
                continue
            candidate = self.candidate(rows, label)
            candidate["training"] = self.one(rows, "TRAIN_RESULT")
            candidate["trainingInput"] = self.one(rows, "TRAIN_INPUT")
            self.score(candidate)
            self.benchmark(candidate)
            candidates[candidate["sha256"]] = candidate
        matched_training = self.matched_training_inputs(list(candidates.values()))
        for candidate in candidates.values():
            self.score(candidate)
            self.benchmark(candidate)
            self.invoke(candidate["label"] + "-probes", "probes", Path(candidate["path"]))
        preferred = self.preferred_candidate(list(candidates.values()))
        self.continuation_preflight()
        if self.is_core_study and path_present(self.study_frozen):
            raise RuntimeError("joint study was frozen before continuation could publish a selection")
        write_new(self.out / "frozen-selection.json", {
            "frozenUtc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "holdoutSha256": self.holdout_sha, "holdoutOpened": False,
            "orchestratorSha256": self.orchestrator_sha,
            "developmentSha256": self.development_sha, "trainingSha256": self.training_hash(),
            "candidates": list(candidates.values()), "developmentPreferred": preferred,
            "selectionStatus": "SELECTED" if preferred else "NO_CAPACITY_ELIGIBLE_CANDIDATE",
            "matchedTrainingInputs": matched_training,
            "lastCapacityPassed": expanded, "trainingFailures": failures,
            "selectionRule": "minimum development BPB among completed eligible candidates; within 0.00001 BPB prefer fewer parameters",
            **self.profile_identity(),
        })
        print("FROZEN_SELECTION " + sha(self.out / "frozen-selection.json"), flush=True)

    def final(self) -> None:
        study_sha = self.verify_study_freeze() if self.is_core_study else None
        original, selected, expanded = self.continuation_preflight()
        selection, selection_sha = self.verify_frozen_selection(original, selected, expanded)
        frozen = self.out / "frozen-selection.json"
        if path_present(self.out / "final.json"):
            completed = json.loads((self.out / "final.json").read_bytes())
            if (completed.get("selectionSha256") != selection_sha
                    or completed.get("holdoutSha256") != selection["holdoutSha256"]
                    or completed.get("originalsUnchanged") is not True
                    or [self.candidate_identity(c) for c in completed["results"]]
                       != [self.candidate_identity(c) for c in selection["candidates"]]
                    or (self.is_core_study and (completed.get("studySelectionSha256") != study_sha
                                               or self.verify_study_freeze() != study_sha))
                    or sha(frozen) != selection_sha):
                raise RuntimeError("completed final evaluation does not match the verified frozen selection")
            return
        if selection["holdoutSha256"] != sha(self.holdout):
            raise ValueError("holdout changed after selection")
        results = []
        for candidate in selection["candidates"]:
            copy = dict(candidate)
            copy["holdout"] = self.score(copy, "holdout")
            results.append(copy)
        if sha(self.base) != BASE_SHA or sha(self.tokenizer) != TOKENIZER_SHA:
            raise ValueError("original inputs changed")
        if sha(frozen) != selection_sha:
            raise RuntimeError("frozen selection changed during final evaluation")
        if self.is_core_study and self.verify_study_freeze() != study_sha:
            raise RuntimeError("joint study selection changed during final evaluation")
        write_new(self.out / "final.json", {"selectionSha256": selection_sha, "holdoutSha256": sha(self.holdout),
                                          "results": results, "originalsUnchanged": True,
                                          **({"studySelectionSha256": study_sha} if self.is_core_study else {}),
                                          "androidValidation": "NOT_RUN", "appIntegration": "NOT_PERFORMED"})
        shutil.copyfile(self.holdout, self.out / "holdout-after-freeze.txt")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=["calibration", "ladder", "continuation", "study-freeze", "final", "all"])
    parser.add_argument("--profile", choices=["historical", *CORE_PROFILES], default="historical")
    parser.add_argument("--study-root", type=Path)
    parser.add_argument("--inputs", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--holdout", type=Path, required=True)
    args = parser.parse_args()
    experiment = Experiment(args)
    for phase in (["calibration", "ladder", "continuation", "final"] if args.phase == "all" else [args.phase]):
        getattr(experiment, phase.replace("-", "_"))()


if __name__ == "__main__":
    main()
