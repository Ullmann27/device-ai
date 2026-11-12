"""Compile the pinned Filament material."""
import argparse
import hashlib
import json
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
RELEASE = "https://github.com/google/filament/releases/download/v1.56.0/filament-v1.56.0-linux.tgz"
ARCHIVE_SHA = "393dcc066987345282f4b5c3f525ab053d9c21087141cdc8015f403cddf455f0"
MATC_SHA = "e31e6317c8aa3485580ba22d0c06797b5751466e55cb8e12453877f53316944f"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--matc", required=True, type=pathlib.Path)
    parser.add_argument("--archive", required=True, type=pathlib.Path)
    args = parser.parse_args()
    assert sha(args.archive) == ARCHIVE_SHA, "Filament archive differs"
    assert sha(args.matc) == MATC_SHA, "Compiler binary differs"
    version = subprocess.check_output([str(args.matc), "--version"], text=True).strip()
    assert version == "56", "Material ABI differs"
    source = ROOT/"tools/cockpit-workstation/materials/live-monitor.mat"
    output = ROOT/"app/src/main/assets/cinema3d/live-monitor.filamat"
    command = [str(args.matc), "-p", "mobile", "-a", "opengl", "-a", "vulkan", "-S", "-o", str(output), str(source)]
    subprocess.run(command, check=True)
    manifest = {"filament_version": "1.56.0", "material_version": version, "release_url": RELEASE,
        "release_archive_sha256": ARCHIVE_SHA, "matc_sha256": sha(args.matc),
        "source_sha256": sha(source), "output_sha256": sha(output), "bytes": output.stat().st_size,
        "target": "mobile OpenGL and Vulkan", "color_contract": "sRGB source; exact sRGB decode; neutral linear view",
        "device_color_chart": "NOT_RUN", "bloom_isolation": "NOT_IMPLEMENTED"}
    (ROOT/"tools/cockpit-workstation/materials/live-monitor-build.json").write_text(json.dumps(manifest, indent=2)+"\n")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
