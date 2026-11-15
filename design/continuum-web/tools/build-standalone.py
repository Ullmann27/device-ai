#!/usr/bin/env python3
"""Build and verify the original-asset Continuum preview as one offline HTML.

Usage from the project root:
    python tools/build-standalone.py

Only the output HTML and its build report are written. App sources, model bytes,
and generated renderer bundles are read without modification. No server, browser,
upload, archive, package installation, or network access is involved.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import html
from html.parser import HTMLParser
import json
import mimetypes
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tempfile
from urllib.parse import unquote, urlsplit


EXPECTED_MODEL_SHA256 = "c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a"
EXPECTED_MODEL_BYTES = 12_038_416
MAX_HTML_BYTES = 25 * 1024 * 1024
DEFAULT_SITE = "https://device-ai-continuum-heinz.electemu.chatgpt.site"
ASSET_OVERRIDES = {
    "avatarModel": "assets/continuum-avatar.glb",
    "avatarFallback": "assets/continuum-avatar-original.png",
    "orbFallback": "assets/continuum-orb-original.png",
}
APP_SCRIPTS = ["scene.js", "orb-original.js", "avatar3d.js", "app.js"]
CSS_URL = re.compile(r"url\(\s*(?:\"([^\"]*)\"|'([^']*)'|([^\s)]+))\s*\)", re.I)
SCRIPT_TAG = re.compile(r"<script\b([^>]*)>(.*?)</script\s*>", re.I | re.S)
TAG = re.compile(r"<([a-zA-Z][\w:-]*)\b([^<>]*?)/?>", re.S)
STYLE_TAG = re.compile(r"<style\b[^>]*>(.*?)</style\s*>", re.I | re.S)


class BuildError(RuntimeError):
    pass


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def is_local(value: str) -> bool:
    parsed = urlsplit(value.strip())
    return bool(value and not value.startswith("#") and not parsed.scheme and not parsed.netloc)


def escaped_script(source: str) -> str:
    # HTML's script end-tag recognition is case-insensitive, even inside JS strings.
    return re.sub(r"</script", lambda m: "<\\/" + m.group(0)[2:], source, flags=re.I)


def json_script(value: object) -> str:
    return escaped_script(json.dumps(value, ensure_ascii=False, separators=(",", ":")))


class TagAttributes(HTMLParser):
    def __init__(self, source: str):
        super().__init__(convert_charrefs=True)
        self.attrs: list[tuple[str, str | None]] = []
        self.feed(source)

    def handle_starttag(self, tag, attrs):
        self.attrs = attrs

    def handle_startendtag(self, tag, attrs):
        self.attrs = attrs


def attributes(source: str) -> list[tuple[str, str | None]]:
    return TagAttributes(source).attrs


def start_tag(tag: str, attrs: list[tuple[str, str | None]], self_closing: bool = False) -> str:
    values = "".join(" " + name + ("" if value is None else '="' + html.escape(value, quote=True) + '"') for name, value in attrs)
    return "<" + tag + values + ("/>" if self_closing else ">")


class OfflineAudit(HTMLParser):
    """Inspect executable/style blocks and fetch-bearing HTML attributes."""
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.local_references: list[str] = []
        self.scripts: list[tuple[dict, str]] = []
        self.styles: list[str] = []
        self.active_tag: str | None = None
        self.active_attrs: dict = {}
        self.chunks: list[str] = []

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        for name in ("src", "href", "poster", "data", "action"):
            value = values.get(name)
            if value and is_local(value):
                self.local_references.append(f"{tag}[{name}]={value}")
        if "srcset" in values:
            raise BuildError("Unexpected srcset remains in the standalone document.")
        if values.get("style"):
            self.styles.append(values["style"])
        if tag in {"script", "style"}:
            self.active_tag, self.active_attrs, self.chunks = tag, values, []

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)

    def handle_data(self, data):
        if self.active_tag:
            self.chunks.append(data)

    def handle_endtag(self, tag):
        if tag != self.active_tag:
            return
        content = "".join(self.chunks)
        if tag == "script": self.scripts.append((self.active_attrs, content))
        else: self.styles.append(content)
        self.active_tag = None


class Builder:
    def __init__(self, source: Path, output: Path, site_url: str, node: str):
        self.source = source.resolve()
        self.output = output.resolve()
        self.site_url = site_url
        self.node = node
        self.inputs: dict[str, dict] = {}
        self.data_urls: dict[str, str] = {}
        self.script_sources: dict[str, str] = {}
        self.project_links_replaced = 0
        self.runtime_defaults: list[str] = []

    def resolve(self, reference: str, parent: Path | None = None) -> Path:
        if not is_local(reference):
            raise BuildError(f"Expected local input, received: {reference[:120]}")
        relative = unquote(urlsplit(reference).path)
        path = ((parent or self.source) / relative).resolve()
        if not path.is_relative_to(self.source):
            raise BuildError(f"Input escapes the preview directory: {reference}")
        if not path.is_file():
            raise BuildError(f"Missing local input: {path}")
        if "sheet" in path.stem.lower() or (re.search(r"portrait|avatar", path.name, re.I) and path.suffix.lower() in {".png", ".webp", ".jpg", ".jpeg"} and path.name != "continuum-avatar-original.png"):
            raise BuildError(f"An obsolete sheet or portrait must not be embedded: {path.name}")
        return path

    def read(self, path: Path) -> bytes:
        data = path.read_bytes()
        relative = path.relative_to(self.source).as_posix()
        current = {"path": relative, "bytes": len(data), "sha256": digest(data)}
        if relative in self.inputs and self.inputs[relative] != current:
            raise BuildError(f"Input changed while building; run again: {relative}")
        self.inputs[relative] = current
        return data

    def data_url(self, reference: str, parent: Path | None = None) -> str:
        path = self.resolve(reference, parent)
        relative = path.relative_to(self.source).as_posix()
        if relative not in self.data_urls:
            mime = {".glb": "model/gltf-binary", ".woff2": "font/woff2", ".woff": "font/woff", ".ttf": "font/ttf", ".otf": "font/otf"}.get(path.suffix.lower()) or mimetypes.guess_type(path.name)[0] or "application/octet-stream"
            self.data_urls[relative] = f"data:{mime};base64," + base64.b64encode(self.read(path)).decode("ascii")
        return self.data_urls[relative]

    def css(self, source: str, parent: Path) -> str:
        if re.search(r"@import\b", source, re.I):
            raise BuildError("CSS @import is not part of this preview; bundle that stylesheet before building.")
        def replace(match):
            value = next(group for group in match.groups() if group is not None)
            if is_local(value): return 'url("' + self.data_url(value, parent) + '")'
            if value.startswith(("data:", "#")): return match.group(0)
            raise BuildError(f"An offline CSS dependency is external: {value[:120]}")
        return CSS_URL.sub(replace, source)

    def html_assets(self, document: str) -> str:
        def replace(match):
            tag = match.group(1).lower()
            if tag in {"script", "link"}: return match.group(0)
            attrs = attributes(match.group(0))
            output = []
            for name, value in attrs:
                if not value: output.append((name, value)); continue
                if name == "style": value = self.css(value, self.source)
                if name == "srcset": raise BuildError("Please use explicit image src values in this preview; srcset is unsupported.")
                if name in {"src", "poster", "data", "href", "action"} and is_local(value):
                    if tag == "a" and urlsplit(value).path.rstrip("/").endswith("projekt.html"):
                        value = self.site_url
                        self.project_links_replaced += 1
                    elif tag == "a" and urlsplit(value).path.endswith(".html"):
                        raise BuildError(f"Unresolved offline page link: {value}")
                    else: value = self.data_url(value)
                output.append((name, value))
            return start_tag(match.group(1), output, match.group(0).endswith("/>"))
        return TAG.sub(replace, document)

    def stylesheets(self, document: str) -> str:
        def replace(match):
            attrs = attributes(match.group(0)); values = dict(attrs)
            reference = values.get("href", "")
            if values.get("rel", "").lower() == "stylesheet":
                path = self.resolve(reference)
                css = self.css(self.read(path).decode("utf-8"), path.parent)
                if re.search(r"</style", css, re.I): raise BuildError("Stylesheet contains a closing style tag.")
                extras = [("data-bundled-from", path.relative_to(self.source).as_posix())]
                if values.get("media"): extras.append(("media", values["media"]))
                return start_tag("style", extras) + "\n" + css + "\n</style>"
            if is_local(reference):
                attrs = [(name, self.data_url(value) if name == "href" and value else value) for name, value in attrs]
                return start_tag("link", attrs)
            return match.group(0)
        return re.sub(r"<link\b[^>]*>", replace, document, flags=re.I)

    def scripts(self, document: str, asset_values: dict[str, str]) -> str:
        inserted_assets = False
        def replace(match):
            nonlocal inserted_assets
            attrs = attributes("<script" + match.group(1) + ">")
            values = dict(attrs); reference = values.get("src")
            source = match.group(2)
            if reference:
                path = self.resolve(reference)
                relative = path.relative_to(self.source).as_posix()
                source = self.read(path).decode("utf-8")
                self.script_sources[relative] = source
                attrs = [(key, value) for key, value in attrs if key not in {"src", "integrity", "crossorigin", "defer", "async"}]
                attrs.append(("data-bundled-from", relative))
            prefix = ""
            if not inserted_assets:
                prefix = '<script id="continuum-embedded-assets">\nwindow.CONTINUUM_ASSETS=' + json_script(asset_values) + ';\n</script>\n'
                inserted_assets = True
            return prefix + start_tag("script", attrs) + "\n" + escaped_script(source) + "\n</script>"
        document = SCRIPT_TAG.sub(replace, document)
        if not inserted_assets: raise BuildError("No application scripts found.")
        if list(self.script_sources) != APP_SCRIPTS:
            raise BuildError(f"Unexpected application script order: {list(self.script_sources)}")
        for script_name, keys in {"avatar3d.js": ["avatarModel", "avatarFallback"], "orb-original.js": ["orbFallback"]}.items():
            for key in keys:
                if not re.search(r"CONTINUUM_ASSETS\s*\?\.\s*" + key + r"\b", self.script_sources[script_name]):
                    raise BuildError(f"Required real asset override is missing in {script_name}: {key}")
        known = set(ASSET_OVERRIDES.values()) | {"assets/"}
        for name, source in self.script_sources.items():
            for reference in sorted(set(re.findall(r'''["'](assets/[^"'\s]*)["']''', source))):
                if reference not in known:
                    raise BuildError(f"Unmapped runtime asset in {name}: {reference}")
                self.runtime_defaults.append(f"{name}: {reference}")
        return document

    def model_integrity(self, data_url: str) -> dict:
        payload = base64.b64decode(data_url.partition(",")[2], validate=True)
        path = self.resolve(ASSET_OVERRIDES["avatarModel"])
        raw = self.read(path)
        if payload != raw or len(payload) != EXPECTED_MODEL_BYTES or digest(payload) != EXPECTED_MODEL_SHA256:
            raise BuildError("Original avatar GLB differs from the verified original, or base64 roundtrip changed it.")
        magic, version, total = struct.unpack_from("<4sII", payload)
        if (magic, version, total) != (b"glTF", 2, len(payload)):
            raise BuildError("Original avatar GLB header is invalid.")
        cursor = 12; gltf = None
        while cursor < len(payload):
            length, kind = struct.unpack_from("<I4s", payload, cursor); cursor += 8
            chunk = payload[cursor:cursor + length]; cursor += length
            if kind == b"JSON": gltf = json.loads(chunk.decode("utf-8"))
        if cursor != len(payload) or gltf is None: raise BuildError("Original GLB chunks are incomplete.")
        uris = [item["uri"] for category in ("buffers", "images") for item in gltf.get(category, []) if "uri" in item]
        if any(not uri.startswith("data:") for uri in uris):
            raise BuildError("Original GLB refers to an external buffer or image; it cannot be served offline as-is.")
        return {"bytes": len(payload), "sha256": digest(payload), "base64RoundtripExact": True, "externalBuffersOrImages": 0}

    def validate(self, document: str, asset_values: dict[str, str]) -> dict:
        audit = OfflineAudit(); audit.feed(document); audit.close()
        if audit.local_references: raise BuildError("Local HTML dependencies remain: " + ", ".join(audit.local_references))
        for css in audit.styles:
            for match in CSS_URL.finditer(css):
                value = next(group for group in match.groups() if group is not None)
                if not value.startswith(("data:", "#")): raise BuildError(f"Unresolved CSS URL: {value[:100]}")
        if "continuum-sheet.png" in document: raise BuildError("The obsolete asset sheet is present in the document.")
        embedded = next((code for attrs, code in audit.scripts if attrs.get("id") == "continuum-embedded-assets"), None)
        if embedded is None: raise BuildError("Embedded runtime assets are absent.")
        match = re.fullmatch(r"\s*window\.CONTINUUM_ASSETS=(.*);\s*", embedded, re.S)
        if not match or json.loads(match.group(1)) != asset_values:
            raise BuildError("Inline asset configuration did not survive HTML parsing exactly.")
        model = self.model_integrity(json.loads(match.group(1))["avatarModel"])
        checked = []
        with tempfile.TemporaryDirectory(prefix="continuum-standalone-check-") as temporary:
            for index, (attrs, source) in enumerate(audit.scripts):
                script_type = attrs.get("type", "").lower()
                if script_type in {"application/json", "application/ld+json"}:
                    json.loads(source); continue
                path = Path(temporary) / (f"script-{index}.mjs" if script_type == "module" else f"script-{index}.js")
                path.write_text(source, encoding="utf-8")
                result = subprocess.run([self.node, "--check", str(path)], capture_output=True, text=True)
                if result.returncode:
                    raise BuildError(f"Inline JavaScript syntax error ({attrs}): {result.stderr[:2000]}")
                checked.append(attrs.get("data-bundled-from") or attrs.get("id") or f"inline-{index}")
        encoded = document.encode("utf-8")
        if len(encoded) >= MAX_HTML_BYTES:
            raise BuildError(f"Standalone HTML is {len(encoded):,} bytes; it must stay below 25 MiB.")
        return {"htmlBytes": len(encoded), "htmlMiB": round(len(encoded) / 1024 / 1024, 3), "under25MiB": True,
                "htmlSha256": digest(encoded), "model": model, "localHtmlReferences": [], "unresolvedCssUrls": [],
                "scriptSyntaxChecked": checked, "runtimeAssetsProvided": sorted(asset_values),
                "runtimeDefaultPathsCoveredByOverridesOrSelfContainedGlb": self.runtime_defaults}

    def build(self) -> dict:
        asset_values = {name: self.data_url(reference) for name, reference in ASSET_OVERRIDES.items()}
        self.model_integrity(asset_values["avatarModel"])
        document = self.read(self.resolve("index.html")).decode("utf-8")
        # Rewrite resource attributes before inserting large raw-text script blocks.
        document = self.html_assets(document)
        document = self.stylesheets(document)
        document = self.scripts(document, asset_values)
        notices = {}
        license_directory = self.source / "assets/licenses"
        for path in sorted(license_directory.glob("*.txt")):
            notices[path.name] = self.read(path).decode("utf-8")
        notice_path = self.source / "assets/continuum-avatar-NOTICE.json"
        if notice_path.is_file(): notices[notice_path.name] = json.loads(self.read(notice_path).decode("utf-8"))
        notice_block = '<script type="application/json" id="continuum-third-party-notices">' + json_script(notices) + '</script>\n'
        document, count = re.subn(r"</body\s*>", lambda _: notice_block + "</body>", document, count=1, flags=re.I)
        if count != 1: raise BuildError("The preview is missing its body closing tag.")
        report = self.validate(document, asset_values)
        # Never silently produce a mixed-version bundle while another agent saves sources.
        for entry in self.inputs.values():
            if digest((self.source / entry["path"]).read_bytes()) != entry["sha256"]:
                raise BuildError(f"Input changed while building; rerun the builder: {entry['path']}")
        report.update({"output": self.output.name, "sourceDirectory": self.source.name,
                       "projectLinksReplaced": self.project_links_replaced, "projectLinkTarget": self.site_url,
                       "embeddedAssetCount": len(self.data_urls), "inputs": sorted(self.inputs.values(), key=lambda x: x["path"]),
                       "appSourcesModified": False, "browserOrServerUsed": False})
        self.output.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.output.with_name(self.output.name + ".tmp")
        temporary.write_text(document, encoding="utf-8"); temporary.replace(self.output)
        self.output.with_suffix(".build.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return report


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source", type=Path, default=root / "continuum-preview")
    parser.add_argument("--output", type=Path, default=root / "Continuum_Vorschau.html")
    parser.add_argument("--site-url", default=DEFAULT_SITE, help="Private online target for projekt.html links")
    parser.add_argument("--node", default=shutil.which("node"), help="Node executable used only for JavaScript syntax checks")
    args = parser.parse_args()
    if not args.node: parser.error("Node is required for the inline JavaScript syntax check.")
    if urlsplit(args.site_url).scheme != "https": parser.error("The project link target must use HTTPS.")
    try:
        report = Builder(args.source, args.output, args.site_url, args.node).build()
    except (BuildError, OSError, ValueError, struct.error) as error:
        print(f"Standalone build failed: {error}", file=sys.stderr); return 1
    print(json.dumps({key: report[key] for key in ("output", "htmlBytes", "htmlMiB", "under25MiB", "embeddedAssetCount", "projectLinksReplaced", "scriptSyntaxChecked", "model")}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
