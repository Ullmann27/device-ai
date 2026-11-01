#!/usr/bin/env python3
"""Strict JUnit XML receipt validation. Python stdlib only; no network access."""
import json
import pathlib
import sys
import xml.etree.ElementTree as ET

def main(argv):
    if len(argv) != 3:
        raise ValueError("usage: validator XML_FILE REQUIRED_CLASS")
    path, klass = pathlib.Path(argv[1]), argv[2]
    if not klass.startswith("at.heinz.deviceai.") or len(klass) > 250:
        raise ValueError("Invalid suite class")
    raw = path.read_bytes()
    if len(raw) < 1 or len(raw) > 1048576:
        raise ValueError("JUnit XML size invalid")
    lowered = raw.lower()
    if b"<!doctype" in lowered or b"<!entity" in lowered:
        raise ValueError("DTD and entities forbidden")
    suite = ET.fromstring(raw)
    if suite.tag != "testsuite" or suite.attrib.get("name") != klass:
        raise ValueError("Exact testsuite root/name mismatch")
    def count(field):
        v = suite.attrib.get(field)
        if v is None or len(v) > 7 or not v.isascii() or not v.isdecimal():
            raise ValueError("Missing/invalid exact JUnit attribute: " + field)
        return int(v)
    tests = count("tests")
    skipped = count("skipped")
    failures = count("failures")
    errors = count("errors")
    if tests < 1 or skipped or failures or errors:
        raise ValueError("JUnit suite skipped, failed, errored or empty")
    cases = [c for c in suite if c.tag == "testcase"]
    if len(cases) != tests:
        raise ValueError("JUnit tests count differs from actual testcase count")
    for case in cases:
        if case.attrib.get("classname") != klass or not case.attrib.get("name"):
            raise ValueError("JUnit testcase name/class mismatch")
        if any(e.tag in ("skipped","failure","error") for e in case.iter()):
            raise ValueError("JUnit testcase skipped/failed/errored")
    if suite.find(".//testsuite") is not None:
        raise ValueError("Unexpected nested testsuite")
    print(json.dumps({"tests":tests,"executed":tests,"skipped":0,"failures":0,"errors":0}))

if __name__ == "__main__":
    try:
        main(sys.argv)
    except (OSError, ValueError, ET.ParseError) as e:
        print("CONTINUUM_JUNIT_REJECTED: " + str(e), file=sys.stderr)
        sys.exit(3)
