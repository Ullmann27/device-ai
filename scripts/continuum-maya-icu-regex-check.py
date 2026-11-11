#!/usr/bin/env python3
"""Compile speech policy with ICU."""

import ctypes
import ctypes.util
import hashlib
import json
import re
from pathlib import Path


class ParseError(ctypes.Structure):
    _fields_ = [("line", ctypes.c_int32), ("offset", ctypes.c_int32),
                ("pre", ctypes.c_uint16 * 16), ("post", ctypes.c_uint16 * 16)]


def main():
    source = Path(__file__).resolve().parents[1] / (
        "app/src/main/java/at/heinz/deviceai/cognitive/NetworkTurnConstraint.kt")
    content = source.read_text(encoding="utf-8")
    transport = re.search(r'private const val transport = "([^"]+)"', content)
    if transport is None:
        raise RuntimeError("Speech transport pattern missing")
    patterns = [match.replace("$transport", transport.group(1)) for match in
                re.findall(r'Regex\(\s*"""(.*?)"""\s*\)', content, re.DOTALL)]
    if not patterns:
        raise RuntimeError("Speech patterns missing")

    library_name = ctypes.util.find_library("icui18n")
    if library_name is None:
        raise RuntimeError("ICU unavailable; Android compatibility NOT_RUN")
    library = ctypes.CDLL(library_name)
    version = re.search(r"\.so\.(\d+)", library_name)
    suffix = "_" + version.group(1) if version else ""
    compile_pattern = getattr(library, "uregex_open" + suffix)
    compile_pattern.argtypes = [ctypes.POINTER(ctypes.c_uint16), ctypes.c_int32,
                               ctypes.c_uint32, ctypes.POINTER(ParseError),
                               ctypes.POINTER(ctypes.c_int32)]
    compile_pattern.restype = ctypes.c_void_p
    close_pattern = getattr(library, "uregex_close" + suffix)
    close_pattern.argtypes = [ctypes.c_void_p]

    def compile_status(pattern):
        encoded = pattern.encode("utf-16-le")
        data = (ctypes.c_uint16 * (len(encoded) // 2)).from_buffer_copy(encoded)
        status = ctypes.c_int32(0)
        error = ParseError()
        compiled = compile_pattern(data, len(data), 0, ctypes.byref(error),
                                   ctypes.byref(status))
        if compiled:
            close_pattern(compiled)
        return status.value, error.offset

    negative_status, _ = compile_status(r"(?iuU)\bohne\s+internet\b")
    if negative_status <= 0:
        raise RuntimeError("ICU negative control unexpectedly accepted")
    for index, pattern in enumerate(patterns):
        status, offset = compile_status(pattern)
        if status > 0:
            raise RuntimeError(f"Speech pattern {index}: ICU {status} at {offset}")
    print(json.dumps({"status": "PASS", "patterns": len(patterns),
                      "negative_control_icu_error": negative_status,
                      "icu": library_name,
                      "source_sha256": hashlib.sha256(content.encode()).hexdigest()}))


if __name__ == "__main__":
    main()
