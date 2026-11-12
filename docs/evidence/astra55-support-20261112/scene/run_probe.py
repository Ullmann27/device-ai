#!/usr/bin/env python3
"""Compile pinned layout sources only."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def function(source, name):
    start = source.index("    fun " + name + "(")
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def statement(source, prefix):
    start = source.index(prefix)
    end = source.index("\n", start)
    return source[start:end]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--kotlin-lib", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--patch", type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.revision):
        raise ValueError("Full commit required")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    if any(args.output_dir.iterdir()):
        raise ValueError("Empty output directory required")
    paths = [
        "app/src/main/java/at/heinz/deviceai/ui/JarvisShellLayout.kt",
        "app/src/main/java/at/heinz/deviceai/ui/JarvisCockpitGrid.kt",
        "app/src/main/java/at/heinz/deviceai/ui/JarvisWindowProfile.kt",
        "app/src/main/java/at/heinz/deviceai/ui/CinematicWorkstationLayout.kt",
        "app/src/main/java/at/heinz/deviceai/ContinuumWorkAreaNavigationSpec.kt",
        "app/src/main/java/at/heinz/deviceai/ui/JarvisTiles.kt",
        "app/src/main/java/at/heinz/deviceai/ui/CockpitGlassButtons.kt",
    ]
    sources = {
        path: subprocess.check_output(["git", "-C", str(args.repo), "show", args.revision + ":" + path])
        for path in paths
    }
    tiles = sources[paths[5]].decode()
    glass = sources[paths[6]].decode()
    nav_decl = statement(tiles, "    data class NavItem(")
    rail_start = tiles.index("    val railNav =")
    rail_end = tiles.index("\n    /**", rail_start)
    glass_start = glass.index("    data class Spec(")
    glass_end = glass.index("\n    /** Former dock", glass_start)
    icon_names = sorted(set(re.findall(r"Icon\.([A-Z_]+)", tiles[rail_start:rail_end] + glass[glass_start:glass_end])))
    geometry = "package at.heinz.deviceai.ui\nimport at.heinz.deviceai.ui.JarvisShellLayout.Box\n"
    geometry += "enum class Icon { " + ", ".join(icon_names) + " }\n"
    geometry += "object CockpitGlassButtons {\n" + glass[glass_start:glass_end] + "\n"
    geometry += statement(glass, "    const val MIN_BUTTON_WIDTH_DP") + "\n"
    geometry += function(glass, "columns") + "\n}\nobject JarvisTiles {\n"
    geometry += nav_decl + "\n" + tiles[rail_start:rail_end] + "\n"
    geometry += statement(tiles, "    val bottomNav =") + "\n"
    geometry += statement(tiles, "    const val DOCK_EXTRA_ROW_DP") + "\n"
    geometry += "\n".join(function(tiles, name) for name in [
        "bottomNavColumns", "bottomNavRows", "bottomNavHeight", "bottomNavItemBoxes"
    ]) + "\n}\n"
    manifest = {
        "source_commit": args.revision,
        "full_source_files": paths[:5],
        "geometry_only_source_slices": {
            paths[5]: ["NavItem", "railNav", "bottomNav", "DOCK_EXTRA_ROW_DP", "bottomNavColumns", "bottomNavRows", "bottomNavHeight", "bottomNavItemBoxes"],
            paths[6]: ["Spec", "buttons", "MIN_BUTTON_WIDTH_DP", "columns"],
        },
        "boundary_fixture": "Only Icon enum identity is replaced; extracted geometry methods and lists are unchanged. Painter methods are excluded.",
        "sha256": {path: digest(data) for path, data in sources.items()},
        "probe_sha256": digest(Path(__file__).with_name("LayoutProbe.kt").read_bytes()),
        "runner_sha256": digest(Path(__file__).read_bytes()),
        "geometry_slice_sha256": digest(geometry.encode()),
        "limitations": ["No Android View dispatch", "No rendering", "No Filament", "No emulator", "No physical Fold7"],
    }
    with tempfile.TemporaryDirectory(prefix="scene-layout-probe-") as temporary:
        work = Path(temporary)
        inputs = []
        for path in paths[:5]:
            target = work / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(sources[path])
            inputs.append(str(target))
        if args.patch:
            patch_bytes = args.patch.read_bytes()
            patched = subprocess.run(["patch", "--batch", "--forward", "-p1"], input=patch_bytes,
                                     cwd=work, capture_output=True, timeout=10)
            (args.output_dir / "patch.log").write_bytes(patched.stdout + patched.stderr)
            if patched.returncode != 0:
                raise RuntimeError("Candidate patch did not apply")
            manifest["patch_sha256"] = digest(patch_bytes)
            manifest["effective_sha256"] = {
                path: digest((work / path).read_bytes()) for path in paths[:5]
            }
        (args.output_dir / "source-hashes.json").write_text(json.dumps(manifest, indent=2) + "\n")
        boundary = work / "DockGeometry.kt"
        boundary.write_text(geometry)
        inputs.extend([str(boundary), str(Path(__file__).with_name("LayoutProbe.kt"))])
        compiler_cp = str(args.kotlin_lib / "*")
        runtime_cp = ":".join(str(args.kotlin_lib / filename) for filename in [
            "kotlin-stdlib-2.0.20.jar", "annotations-24.0.1.jar"
        ])
        artifact = work / "probe.jar"
        command = ["java", "-Xmx512m", "-cp", compiler_cp,
                   "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
                   "-jvm-target", "17", "-classpath", runtime_cp, "-d", str(artifact), *inputs]
        compiled = subprocess.run(command, capture_output=True, text=True, timeout=120)
        (args.output_dir / "compile.log").write_text(compiled.stdout + compiled.stderr)
        result = {
            "source_commit": args.revision,
            "compiler": "K2JVMCompiler 2.0.20, max heap 512 MiB",
            "compile_exit": compiled.returncode,
            "checks": "NOT_RUN",
            "render": "NOT_RUN",
            "device": "NOT_RUN",
        }
        if compiled.returncode == 0:
            tested = subprocess.run(["java", "-Xmx128m", "-cp", str(artifact) + ":" + runtime_cp,
                                     "sceneprobe.LayoutProbeKt"], capture_output=True, text=True, timeout=30)
            (args.output_dir / "observations.jsonl").write_text(tested.stdout)
            (args.output_dir / "run.stderr").write_text(tested.stderr)
            result["checks"] = "PASS" if tested.returncode == 0 else "FAIL"
            result["run_exit"] = tested.returncode
            result["observations_sha256"] = digest(tested.stdout.encode())
        (args.output_dir / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        print(json.dumps(result))
        return result.get("run_exit", compiled.returncode)


if __name__ == "__main__":
    raise SystemExit(main())
