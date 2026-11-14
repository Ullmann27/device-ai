#!/usr/bin/env python3
"""Observe pinned host EGL configuration; no Android acceptance."""
import ctypes as C
import hashlib
import json
import os
import pathlib
import platform
import re
import resource
import signal
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile

ARCHIVE_URL = "https://dl.google.com/android/repository/emulator-linux_x64-15917651.zip"
ARCHIVE_SHA256 = "95771e0ae431897b2a4bd2d97fa095f29a8b0624a7b216baf529f9306161c266"
ARCHIVE_SIZE = 334378080
HEADER_COMMIT = "8e56e86e65687697ef54114cdb1afabdfac756bf"
HEADER_ROOT = "https://github.com/KhronosGroup/EGL-Registry/blob/" + HEADER_COMMIT + "/api/EGL/"
HEADER_BLOBS = {
    "egl.h": "314ef43f87c3c4b62ed3675f5baba614f3267064",
    "eglplatform.h": "6786afd90b685895efa25b302afba68fc8296941",
}
EGL = {
    "RED_SIZE": 0x3024, "GREEN_SIZE": 0x3023, "BLUE_SIZE": 0x3022,
    "ALPHA_SIZE": 0x3021, "DEPTH_SIZE": 0x3025, "STENCIL_SIZE": 0x3026,
    "CONFIG_ID": 0x3028, "SURFACE_TYPE": 0x3033, "RENDERABLE_TYPE": 0x3040,
    "CONFORMANT": 0x3042, "NONE": 0x3038, "SUCCESS": 0x3000,
    "VENDOR": 0x3053, "VERSION": 0x3054, "EXTENSIONS": 0x3055,
    "CLIENT_APIS": 0x308D, "PBUFFER_BIT": 1, "WINDOW_BIT": 4,
    "OPENGL_ES3_BIT": 0x40,
}
LIMITATION = (
    "Host EGL initialization/configuration observations only. No context, "
    "rendering, emulator, Android, APK, frame, or device acceptance was performed."
)


def write_json(path, value):
    path.write_text(json.dumps(value, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def file_hash(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def observe(mode, sdk, output):
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    resource.setrlimit(resource.RLIMIT_FSIZE, (2 * 1024 * 1024, 2 * 1024 * 1024))
    if mode not in ("default", "surfaceless"):
        raise ValueError("Unknown mode")
    actual_platform = os.environ.get("EGL_PLATFORM")
    if actual_platform != (None if mode == "default" else "surfaceless"):
        raise ValueError("Unexpected EGL_PLATFORM")
    sdk = sdk.resolve(strict=True)
    output.mkdir()
    gl = sdk / "lib64" / "gles_llvmpipe"
    required = [(gl / name).resolve(strict=True) for name in ("libEGL.so", "libGLESv2.so")]
    for path in required:
        if not path.is_relative_to(sdk):
            raise ValueError("Graphics library escaped bundle")
    state = {
        "mode": mode, "pid": os.getpid(), "uid": os.getuid(),
        "started_monotonic": time.monotonic(), "limitation": LIMITATION,
        "egl_platform": actual_platform, "events": [], "map_snapshots": [],
        "required_libraries": [
            {"path": str(p), "size": p.stat().st_size, "sha256": file_hash(p)}
            for p in required
        ],
    }
    report = output / "observation.json"
    write_json(report, state)

    def event(name, value):
        state["events"].append({"name": name, "monotonic": time.monotonic(), "value": value})
        write_json(report, state)

    def maps(label):
        raw = pathlib.Path("/proc/self/maps").read_bytes()
        if len(raw) > 2 * 1024 * 1024:
            raise ValueError("Own maps exceed bound")
        filename = label + "-maps.txt"
        (output / filename).write_bytes(raw)
        paths = sorted({
            line.split(maxsplit=5)[5]
            for line in raw.decode("utf-8", "strict").splitlines()
            if len(line.split(maxsplit=5)) == 6 and line.split(maxsplit=5)[5].startswith("/")
        })
        libraries = []
        for value in paths:
            path = pathlib.Path(value)
            if ".so" not in path.name:
                continue
            if not path.is_file():
                libraries.append({"path": value, "readable_file": False})
                continue
            resolved = path.resolve(strict=True)
            libraries.append({
                "path": value, "resolved_path": str(resolved),
                "inside_bundle": resolved.is_relative_to(sdk),
                "size": resolved.stat().st_size, "sha256": file_hash(resolved),
            })
        state["map_snapshots"].append({
            "label": label, "file": filename,
            "sha256": hashlib.sha256(raw).hexdigest(), "libraries": libraries,
            "required_libraries_mapped": {
                str(p): str(p) in paths for p in required
            },
        })
        write_json(report, state)

    egl = C.CDLL(str(required[0]), mode=os.RTLD_LOCAL | os.RTLD_NOW)
    gles = C.CDLL(str(required[1]), mode=os.RTLD_LOCAL | os.RTLD_NOW)
    event("libraries_loaded", {"egl_handle": egl._handle, "gles_handle": gles._handle})
    maps("loaded")
    i32, u32, ptr = C.c_int32, C.c_uint32, C.c_void_p
    signatures = {
        "eglGetError": (i32, []),
        "eglGetDisplay": (ptr, [ptr]),
        "eglInitialize": (u32, [ptr, C.POINTER(i32), C.POINTER(i32)]),
        "eglQueryString": (C.c_char_p, [ptr, i32]),
        "eglChooseConfig": (u32, [ptr, C.POINTER(i32), C.POINTER(ptr), i32, C.POINTER(i32)]),
        "eglGetConfigAttrib": (u32, [ptr, ptr, i32, C.POINTER(i32)]),
        "eglTerminate": (u32, [ptr]),
    }
    for name, (returns, arguments) in signatures.items():
        function = getattr(egl, name)
        function.restype, function.argtypes = returns, arguments

    def query_string(display, name):
        raw = egl.eglQueryString(display, EGL[name])
        error = int(egl.eglGetError())
        return {"value": None if raw is None else raw.decode("utf-8", "replace"),
                "egl_error": error, "egl_error_hex": hex(error)}

    event("client_extensions", query_string(None, "EXTENSIONS"))
    display = egl.eglGetDisplay(None)
    error = int(egl.eglGetError())
    event("get_display", {"display": display, "egl_error": error, "egl_error_hex": hex(error)})
    major, minor = i32(-1), i32(-1)
    started = time.monotonic()
    initialized = int(egl.eglInitialize(display, C.byref(major), C.byref(minor)))
    error = int(egl.eglGetError())
    event("initialize", {
        "return": initialized, "egl_error": error, "egl_error_hex": hex(error),
        "version_major": major.value, "version_minor": minor.value,
        "elapsed_seconds": time.monotonic() - started,
    })
    maps("initialized")
    for name in ("VENDOR", "VERSION", "CLIENT_APIS", "EXTENSIONS"):
        event("query_" + name.lower(), query_string(display, name))

    for name, surface in (
        ("gles3_pbuffer", EGL["PBUFFER_BIT"]),
        ("gles3_pbuffer_window", EGL["PBUFFER_BIT"] | EGL["WINDOW_BIT"]),
    ):
        values = [
            EGL["SURFACE_TYPE"], surface, EGL["RENDERABLE_TYPE"], EGL["OPENGL_ES3_BIT"],
            EGL["RED_SIZE"], 1, EGL["GREEN_SIZE"], 1, EGL["BLUE_SIZE"], 1,
            EGL["ALPHA_SIZE"], 0, EGL["NONE"],
        ]
        attrs = (i32 * len(values))(*values)
        count = i32(-1)
        returned = int(egl.eglChooseConfig(display, attrs, None, 0, C.byref(count)))
        error = int(egl.eglGetError())
        result = {
            "attributes": values, "return": returned, "egl_error": error,
            "egl_error_hex": hex(error), "reported_count": count.value,
            "matching_count": count.value if returned else None,
            "sample_limit": 16, "sample_configs": [],
        }
        event(name, result)
        if not returned:
            continue
        if not 0 <= count.value <= 4096:
            raise ValueError("Config count outside bound")
        capacity = min(count.value, 16)
        if capacity == 0:
            continue
        configs, copied = (ptr * capacity)(), i32(-1)
        listed = int(egl.eglChooseConfig(display, attrs, configs, capacity, C.byref(copied)))
        list_error = int(egl.eglGetError())
        result["enumeration"] = {
            "return": listed, "egl_error": list_error,
            "egl_error_hex": hex(list_error), "reported_count": copied.value,
        }
        if listed:
            if not 0 <= copied.value <= capacity:
                raise ValueError("Returned config count outside capacity")
            for config in configs[:copied.value]:
                details = {}
                for attribute in (
                    "CONFIG_ID", "RED_SIZE", "GREEN_SIZE", "BLUE_SIZE", "ALPHA_SIZE",
                    "DEPTH_SIZE", "STENCIL_SIZE", "SURFACE_TYPE", "RENDERABLE_TYPE", "CONFORMANT",
                ):
                    value = i32(-1)
                    ok = int(egl.eglGetConfigAttrib(display, config, EGL[attribute], C.byref(value)))
                    attr_error = int(egl.eglGetError())
                    details[attribute] = {"return": ok, "value": value.value, "egl_error": attr_error}
                result["sample_configs"].append(details)
        write_json(report, state)
    maps("queried")
    if initialized:
        terminated = int(egl.eglTerminate(display))
        error = int(egl.eglGetError())
        event("terminate", {"return": terminated, "egl_error": error, "egl_error_hex": hex(error)})
    state["finished_monotonic"] = time.monotonic()
    state["observation_finished"] = True
    write_json(report, state)


def stop_own_child(process):
    if process.poll() is not None:
        return
    try:
        process.terminate()
    except ProcessLookupError:
        pass
    try:
        process.wait(timeout=2)
    except subprocess.TimeoutExpired:
        try:
            process.kill()
        except ProcessLookupError:
            pass
        process.wait(timeout=2)


def collect():
    os.environ.pop("GH_TOKEN", None)
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    begun = time.monotonic()
    deadline = begun + 215
    runner_temp = pathlib.Path(os.environ["RUNNER_TEMP"]).resolve(strict=True)
    output = runner_temp / "egl-config-evidence"
    output.mkdir()
    work = pathlib.Path(tempfile.mkdtemp(prefix="code59-egl-", dir=runner_temp))
    meta = {
        "source_commit": os.environ["GITHUB_SHA"],
        "run_id": os.environ["GITHUB_RUN_ID"], "run_attempt": os.environ["GITHUB_RUN_ATTEMPT"],
        "repository": os.environ["GITHUB_REPOSITORY"],
        "archive_url": ARCHIVE_URL, "archive_expected_sha256": ARCHIVE_SHA256,
        "archive_expected_size": ARCHIVE_SIZE, "emulator_version": "37.1.11",
        "emulator_build": "15917651", "limitation": LIMITATION,
        "header_sources": {name: {"url": HEADER_ROOT + name, "git_blob": sha} for name, sha in HEADER_BLOBS.items()},
        "constants": EGL, "processes": [], "started_monotonic": begun,
        "host": {"machine": platform.machine(), "kernel": platform.release(), "cpu_count": os.cpu_count()},
    }
    report = output / "collection.json"
    write_json(report, meta)
    try:
        if meta["repository"] != "Ullmann27/device-ai":
            raise ValueError("Unexpected repository")
        if os.environ["GITHUB_REF"] != "refs/heads/diagnostic/code59-egl-config-20261114":
            raise ValueError("Unexpected diagnostic branch")
        if not re.fullmatch("[0-9a-f]{40}", meta["source_commit"]):
            raise ValueError("Invalid source commit")
        os_release = pathlib.Path("/etc/os-release").read_text()
        (output / "host-os-release.txt").write_text(os_release)
        if not re.search(r'^ID=ubuntu$', os_release, re.M) or not re.search(r'^VERSION_ID="24\.04"$', os_release, re.M):
            raise ValueError("Unexpected host distribution")
        if platform.machine() != "x86_64":
            raise ValueError("Unexpected host architecture")
        archive = work / "emulator.zip"
        h, size = hashlib.sha256(), 0
        request = urllib.request.Request(ARCHIVE_URL, headers={"Accept-Encoding": "identity"})
        with urllib.request.build_opener(NoRedirect).open(request, timeout=20) as response:
            if response.status != 200 or response.geturl() != ARCHIVE_URL:
                raise ValueError("Archive origin mismatch")
            if int(response.headers.get("Content-Length", "-1")) != ARCHIVE_SIZE:
                raise ValueError("Archive declared size mismatch")
            with archive.open("xb") as dest:
                while True:
                    if time.monotonic() - begun > 140:
                        raise TimeoutError("Archive download deadline")
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    size += len(chunk)
                    if size > ARCHIVE_SIZE:
                        raise ValueError("Archive exceeded expected size")
                    h.update(chunk)
                    dest.write(chunk)
        meta["archive_actual_size"] = size
        meta["archive_actual_sha256"] = h.hexdigest()
        write_json(report, meta)
        if size != ARCHIVE_SIZE or h.hexdigest() != ARCHIVE_SHA256:
            raise ValueError("Archive integrity mismatch")
        sdk_parent = work / "bundle"
        sdk_parent.mkdir()
        with zipfile.ZipFile(archive) as bundle:
            names = []
            for item in bundle.infolist():
                path = pathlib.PurePosixPath(item.filename)
                parts = path.parts
                selected = (
                    item.filename == "emulator/source.properties"
                    or parts[:3] == ("emulator", "lib64", "gles_llvmpipe")
                    or (path.parent == pathlib.PurePosixPath("emulator/lib64") and ".so" in path.name)
                )
                if not selected or item.is_dir():
                    continue
                if ".." in parts or not re.fullmatch(r"[A-Za-z0-9_/+.-]+", item.filename):
                    raise ValueError("Unexpected pinned library entry: " + repr(item.filename))
                names.append(item.filename)
            if not names or len(names) != len(set(names)):
                raise ValueError("Invalid selected library entries")
        meta["extracted_archive_entries"] = names
        with (output / "unzip-stdout.txt").open("wb") as stdout, (output / "unzip-stderr.txt").open("wb") as stderr:
            extraction = subprocess.run(
                ["/usr/bin/unzip", "-q", str(archive), *names, "-d", str(sdk_parent)],
                stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=25, check=False,
            )
        meta["extraction_exit"] = extraction.returncode
        if extraction.returncode:
            raise ValueError("Pinned library extraction failed")
        sdk = sdk_parent / "emulator"
        properties = (sdk / "source.properties").read_bytes()
        (output / "source.properties").write_bytes(properties)
        if not re.search(rb"^Pkg\.Revision\s*=\s*37\.1\.11\s*$", properties, re.M):
            raise ValueError("Pinned package version mismatch")
        gl = sdk / "lib64" / "gles_llvmpipe"
        manifest = []
        for path in sorted(gl.rglob("*")):
            if path.is_symlink():
                path.resolve(strict=True)
            if not path.is_file():
                continue
            resolved = path.resolve(strict=True)
            if not resolved.is_relative_to(sdk.resolve()):
                raise ValueError("Pinned library path escaped bundle")
            manifest.append({
                "archive_path": "emulator/" + str(path.relative_to(sdk)),
                "resolved_path": str(resolved), "size": resolved.stat().st_size,
                "sha256": file_hash(resolved),
                "symlink_target": os.readlink(path) if path.is_symlink() else None,
            })
        write_json(output / "bundled-gles-libraries.json", manifest)
        for required in ("libEGL.so", "libGLESv2.so", "libgallium-25.2.4.so"):
            if not (gl / required).is_file():
                raise ValueError("Required bundled library missing")
        env = {key: os.environ[key] for key in (
            "PATH", "HOME", "LANG", "LC_ALL", "TMPDIR", "DISPLAY", "WAYLAND_DISPLAY", "XDG_RUNTIME_DIR"
        ) if key in os.environ}
        env.update({
            "LD_LIBRARY_PATH": str(gl) + ":" + str(sdk / "lib64"),
            "LIBGL_DRIVERS_PATH": str(gl / "dri"),
            "GBM_BACKENDS_PATH": str(gl / "gbm"), "EGL_LOG_LEVEL": "debug",
        })
        meta["common_probe_environment"] = env
        meta["probe_environment_difference"] = {"default": None, "surfaceless": "surfaceless"}
        write_json(report, meta)
        for mode in ("default", "surfaceless"):
            if deadline - time.monotonic() < 20:
                raise TimeoutError("Insufficient probe deadline")
            child_env = dict(env)
            if mode == "surfaceless":
                child_env["EGL_PLATFORM"] = "surfaceless"
            command = [sys.executable, "-I", "-B", str(pathlib.Path(__file__).resolve()), "--observe", mode, str(sdk), str(output / mode)]
            entry = {"mode": mode, "command": command, "started_monotonic": time.monotonic(), "wall_limit_seconds": 15, "term_grace_seconds": 2}
            meta["processes"].append(entry)
            write_json(report, meta)
            process = None
            with (output / (mode + "-stdout.txt")).open("wb") as stdout, (output / (mode + "-stderr.txt")).open("wb") as stderr:
                try:
                    process = subprocess.Popen(
                        command, env=child_env, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr,
                        start_new_session=True,
                    )
                    entry["pid"] = process.pid
                    write_json(report, meta)
                    try:
                        entry["exit_code"] = process.wait(timeout=15)
                        entry["wall_timeout"] = False
                    except subprocess.TimeoutExpired:
                        entry["wall_timeout"] = True
                        stop_own_child(process)
                        entry["exit_code"] = process.returncode
                finally:
                    if process is not None:
                        stop_own_child(process)
                    entry["finished_monotonic"] = time.monotonic()
                    write_json(report, meta)
            observation = output / mode / "observation.json"
            entry["observation_file_present"] = observation.is_file()
            if observation.is_file():
                entry["observation_sha256"] = file_hash(observation)
            print("EGL_OBSERVATION " + json.dumps(entry, sort_keys=True), flush=True)
        meta["collection_finished"] = True
    except Exception as error:
        meta["collection_error"] = {"type": type(error).__name__, "message": str(error)}
        raise
    finally:
        meta["finished_monotonic"] = time.monotonic()
        write_json(report, meta)
        print("EGL_COLLECTION " + json.dumps(meta, sort_keys=True), flush=True)


if __name__ == "__main__":
    if len(sys.argv) == 5 and sys.argv[1] == "--observe":
        observe(sys.argv[2], pathlib.Path(sys.argv[3]), pathlib.Path(sys.argv[4]))
    elif len(sys.argv) == 1:
        collect()
    else:
        raise SystemExit("Invalid diagnostic arguments")
