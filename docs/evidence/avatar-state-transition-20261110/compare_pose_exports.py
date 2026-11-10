"""Compare actual mapper outputs, not pixels."""
import argparse
import hashlib
import json
from pathlib import Path


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    before, after = [json.loads(p.read_text()) for p in (args.baseline, args.candidate)]
    require(before["schema"] == after["schema"] == "continuum-production-mapper-review-poses-v2", "Pose schema mismatch")
    for key in ("sourceAssetSha256", "focus", "hiddenNodePrefix", "stateTransitions"):
        require(before[key] == after[key], "Changed contract: " + key)
    differences = [key for key in before["sourceHashes"]
                   if before["sourceHashes"][key] != after["sourceHashes"].get(key)]
    require(differences == ["app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt"], "Unexpected runtime source changes")
    old = {frame["name"]: frame for frame in before["frames"]}
    new = {frame["name"]: frame for frame in after["frames"]}
    require(list(old) == list(new), "Frame identities differ")
    for name in old:
        for key in ("seconds", "motion", "input", "weights", "headYaw", "headPitch", "neckYaw", "neckPitch", "jawPitch"):
            require(old[name][key] == new[name][key], "Unexpected pose difference: " + name + "/" + key)
    sequences = []
    for prefix in ("idle-to-thinking", "thinking-to-idle"):
        names = [prefix + "-before"] + [prefix + "-f%03d" % i for i in range(25)]
        require(old[names[0]]["seconds"] == old[names[1]]["seconds"], "Boundary must share timestamp")
        series = {}
        for label, frames in (("baseline", old), ("candidate", new)):
            changes = [{axis: frames[b][axis] - frames[a][axis] for axis in ("eyeYaw", "eyePitch")}
                       for a, b in zip(names, names[1:])]
            series[label] = {
                "zero_elapsed_step_degrees": changes[0],
                "largest_step_degrees": {axis: max(abs(change[axis]) for change in changes)
                                         for axis in ("eyeYaw", "eyePitch")},
            }
        require(any(abs(v) > .5 for v in series["baseline"]["zero_elapsed_step_degrees"].values()), "Baseline jump absent")
        require(all(abs(v) <= 1e-5 for v in series["candidate"]["zero_elapsed_step_degrees"].values()), "Candidate still teleports")
        require(all(v < .5 for v in series["candidate"]["largest_step_degrees"].values()), "Candidate frame step too large")
        sequences.append({"name": prefix, "frames": len(names), **series})
    result = {
        "status": "PASS",
        "evidence_class": "ACTUAL_KOTLIN_MAPPER_SYNTHETIC_INPUT_NOT_DEVICE",
        "baseline_source_commit": before["sourceCommit"],
        "candidate_source_commit": after["sourceCommit"],
        "baseline_export_sha256": hashlib.sha256(args.baseline.read_bytes()).hexdigest(),
        "candidate_export_sha256": hashlib.sha256(args.candidate.read_bytes()).hexdigest(),
        "asset_sha256": before["sourceAssetSha256"],
        "frames_compared": len(old),
        "unchanged_channels": ["inputs", "weights", "head", "neck", "jaw", "camera focus", "hidden nodes"],
        "changed_runtime_sources": differences,
        "sequences": sequences,
        "limits": ["Synthetic fixed-gaze silent state transitions", "No Filament, device, actual speech or camera validation"],
    }
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
