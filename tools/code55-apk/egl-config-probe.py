#!/usr/bin/env python3
"""Observe pinned host EGL configuration; no Android acceptance."""
import ctypes as C
import contextlib
import hashlib
import json
import os
import pathlib
import platform
import re
import resource
import select
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
    if mode not in ("default", "surfaceless", "xvfb"):
        raise ValueError("Unknown mode")
    actual_platform = os.environ.get("EGL_PLATFORM")
    if actual_platform != ("surfaceless" if mode == "surfaceless" else None):
        raise ValueError("Unexpected EGL_PLATFORM")
    if mode == "xvfb" and not re.fullmatch(r":[0-9]{1,5}", os.environ.get("DISPLAY", "")):
        raise ValueError("Own Xvfb display missing")
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
        "display": os.environ.get("DISPLAY"),
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


def own_server_identity(process, command, executable, output, label):
    if process.poll() is not None:
        raise RuntimeError("Own Xvfb exited before identity capture")
    root = pathlib.Path("/proc") / str(process.pid)
    raw = {name: (root / name).read_bytes() for name in ("stat", "status", "cmdline")}
    for name, data in raw.items():
        (output / ("xvfb-" + label + "-" + name + ".txt")).write_bytes(data)
    fields = raw["stat"].decode().rsplit(") ", 1)[1].split()
    uids = re.search(rb"^Uid:\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*$", raw["status"], re.M)
    actual_command = [value.decode("utf-8", "strict") for value in raw["cmdline"].rstrip(b"\0").split(b"\0")]
    actual_executable = (root / "exe").resolve(strict=True)
    identity = {
        "pid": process.pid, "uid": int(uids[1]) if uids else None,
        "parent_pid": int(fields[1]), "process_group": int(fields[2]),
        "session": int(fields[3]), "start_ticks": int(fields[19]),
        "command": actual_command, "executable": str(actual_executable),
        "sampled_monotonic": time.monotonic(),
    }
    if not uids or any(int(value) != os.getuid() for value in uids.groups()):
        raise ValueError("Own Xvfb UID mismatch")
    if (identity["parent_pid"] != os.getpid()
            or identity["process_group"] != os.getpgrp() or identity["session"] != os.getsid(0)
            or actual_command != command or actual_executable != executable):
        raise ValueError("Own Xvfb process binding mismatch")
    return identity


@contextlib.contextmanager
def observation_environment(mode, env, output, meta, report, deadline):
    child_env = dict(env)
    if mode != "xvfb":
        if mode == "surfaceless":
            child_env["EGL_PLATFORM"] = "surfaceless"
        yield child_env
        return
    details = {
        "startup_limit_seconds": 5, "term_grace_seconds": 2,
        "sources": [
            "https://manpages.ubuntu.com/manpages/noble/man1/Xserver.1.html",
            "https://manpages.ubuntu.com/manpages/noble/man1/Xvfb.1.html",
        ],
    }
    meta["own_xvfb"] = details
    write_json(report, meta)
    process, read_fd, write_fd = None, None, None
    try:
        if deadline - time.monotonic() < 30:
            raise TimeoutError("Insufficient own Xvfb comparison deadline")
        executable = pathlib.Path("/usr/bin/Xvfb").resolve(strict=True)
        if not executable.is_file() or not os.access(executable, os.X_OK):
            raise FileNotFoundError("Installed Ubuntu Xvfb unavailable")
        details["executable"] = str(executable)
        details["executable_sha256"] = file_hash(executable)
        host_env = {key: os.environ[key] for key in ("PATH", "HOME", "LANG", "LC_ALL", "TMPDIR", "XDG_RUNTIME_DIR") if key in os.environ}
        read_fd, write_fd = os.pipe()
        command = [str(executable), "-displayfd", str(write_fd), "-screen", "0", "1080x2400x24", "-nolisten", "tcp"]
        details["command"] = command
        details["started_monotonic"] = time.monotonic()
        with (output / "xvfb-server-stdout.txt").open("wb") as stdout, (output / "xvfb-server-stderr.txt").open("wb") as stderr:
            process = subprocess.Popen(
                command, env=host_env, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr,
                pass_fds=(write_fd,),
            )
            os.close(write_fd)
            write_fd = None
            details["pid"] = process.pid
            write_json(report, meta)
            ready_deadline = details["started_monotonic"] + 5
            reply = b""
            while b"\n" not in reply:
                remaining = ready_deadline - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError("Own Xvfb startup deadline")
                if process.poll() is not None:
                    raise RuntimeError("Own Xvfb exited during startup")
                if select.select([read_fd], [], [], min(remaining, 0.1))[0]:
                    value = os.read(read_fd, 17 - len(reply))
                    reply += value
                    if not value or len(reply) > 16:
                        raise ValueError("Invalid own Xvfb display response")
            (output / "xvfb-display-response.txt").write_bytes(reply)
            if not re.fullmatch(rb"(?:0|[1-9][0-9]{0,4})\n", reply) or int(reply) > 65535:
                raise ValueError("Invalid own Xvfb display number")
            details["bound"] = own_server_identity(process, command, executable, output, "bound")
            details["display"] = ":" + reply.decode().strip()
            child_env["DISPLAY"] = details["display"]
            details["probe_environment_difference"] = {"DISPLAY": details["display"]}
            details["ready_monotonic"] = time.monotonic()
            write_json(report, meta)
            yield child_env
            details["after_probe"] = own_server_identity(process, command, executable, output, "after")
            if details["after_probe"]["start_ticks"] != details["bound"]["start_ticks"]:
                raise ValueError("Own Xvfb lifetime changed")
    except BaseException as error:
        details["error"] = {"type": type(error).__name__, "message": str(error)}
        raise
    finally:
        for fd in (read_fd, write_fd):
            if fd is not None:
                os.close(fd)
        if process is not None:
            details["stop_started_monotonic"] = time.monotonic()
            try:
                stop_own_child(process)
            finally:
                details["exit_code"] = process.returncode
                details["reaped"] = process.returncode is not None
                details["stop_finished_monotonic"] = time.monotonic()
        write_json(report, meta)


def loader_preflight(sdk, output, env, deadline):
    libraries = sorted(p for p in (sdk / "lib64").rglob("*") if p.is_file() and ".so" in p.name)
    if not libraries:
        raise ValueError("No selected SDK libraries")
    for path in libraries:
        if not path.resolve(strict=True).is_relative_to(sdk.resolve()):
            raise ValueError("Preflight library escaped bundle")
    readelf = pathlib.Path("/usr/bin/readelf").resolve(strict=True)
    loader = pathlib.Path("/lib64/ld-linux-x86-64.so.2").resolve(strict=True)
    result = {
        "library_path_order": env["LD_LIBRARY_PATH"].split(":"),
        "tool_sources": [
            "https://sourceware.org/binutils/docs/binutils/readelf.html",
            "https://man7.org/linux/man-pages/man8/ld.so.8.html",
        ],
        "tools": [
            {"path": str(p), "sha256": file_hash(p)} for p in (readelf, loader)
        ],
        "commands": [],
    }
    commands = [("dynamic-sections", [str(readelf), "--wide", "--dynamic", *map(str, libraries)])]
    gl = sdk / "lib64" / "gles_llvmpipe"
    for name in ("libEGL.so", "libGLESv2.so", "libgallium-25.2.4.so"):
        commands.append(("loader-" + name, [str(loader), "--list", str(gl / name)]))
    for label, command in commands:
        available = min(10, deadline - time.monotonic() - 40)
        if available <= 0:
            raise TimeoutError("Insufficient loader preflight deadline")
        entry = {"label": label, "command": command, "timeout_seconds": available}
        result["commands"].append(entry)
        write_json(output / "loader-preflight.json", result)
        with (output / (label + "-stdout.txt")).open("wb") as stdout, (output / (label + "-stderr.txt")).open("wb") as stderr:
            try:
                process = subprocess.run(
                    command, env=env, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr,
                    timeout=available, check=False,
                )
                entry["exit_code"] = process.returncode
            except subprocess.TimeoutExpired:
                entry["timed_out"] = True
                raise
            finally:
                write_json(output / "loader-preflight.json", result)
    return result


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
                    or parts[:3] in (("emulator", "lib64", "gles_llvmpipe"), ("emulator", "lib64", "vulkan"))
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
        for path in sorted((sdk / "lib64").rglob("*")):
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
        write_json(output / "bundled-gles-libraries.json", [m for m in manifest if m["archive_path"].startswith("emulator/lib64/gles_llvmpipe/")])
        write_json(output / "bundled-loader-dependencies.json", [m for m in manifest if not m["archive_path"].startswith("emulator/lib64/gles_llvmpipe/")])
        for required in ("libEGL.so", "libGLESv2.so", "libgallium-25.2.4.so"):
            if not (gl / required).is_file():
                raise ValueError("Required bundled library missing")
        env = {key: os.environ[key] for key in (
            "PATH", "HOME", "LANG", "LC_ALL", "TMPDIR", "DISPLAY", "WAYLAND_DISPLAY", "XDG_RUNTIME_DIR"
        ) if key in os.environ}
        env.update({
            "LD_LIBRARY_PATH": ":".join(map(str, (gl, sdk / "lib64", sdk / "lib64" / "vulkan"))),
            "LIBGL_DRIVERS_PATH": str(gl / "dri"),
            "GBM_BACKENDS_PATH": str(gl / "gbm"), "EGL_LOG_LEVEL": "debug",
        })
        meta["common_probe_environment"] = env
        meta["probe_environment_difference"] = {"default": None, "surfaceless": "surfaceless", "xvfb": None}
        write_json(report, meta)
        meta["loader_preflight"] = loader_preflight(sdk, output, env, deadline)
        write_json(report, meta)
        for mode in ("default", "surfaceless", "xvfb"):
            if deadline - time.monotonic() < 20:
                raise TimeoutError("Insufficient probe deadline")
            with observation_environment(mode, env, output, meta, report, deadline) as child_env:
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
