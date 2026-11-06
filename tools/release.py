"""God Wand CurseForge uploader. Offline preview by default; upload is explicit."""
import argparse
import hashlib
import html
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tomllib
import urllib.error
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
API = "https://minecraft.curseforge.com/api"


def settings():
    values = {}
    env = ROOT / ".env"
    if env.exists():
        for line in env.read_text(encoding="utf-8-sig").splitlines():
            key, sep, value = line.strip().partition("=")
            if sep and key in {"CURSEFORGE_TOKEN", "CURSEFORGE_PROJECT_ID"}:
                values[key] = value.strip().strip('"\'')
    for key in ("CURSEFORGE_TOKEN", "CURSEFORGE_PROJECT_ID"):
        if os.environ.get(key):
            values[key] = os.environ[key]
    project = values.get("CURSEFORGE_PROJECT_ID", "1760928")
    if not project.isdecimal():
        raise ValueError("CURSEFORGE_PROJECT_ID must be numeric")
    return project, values.get("CURSEFORGE_TOKEN", "")


def version():
    text = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    return re.search(r"(?m)^mod_version\s*=\s*(\S+)", text).group(1)


def changelog(current):
    text = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    match = re.search(r"(?m)^## \[?" + re.escape(current) + r"\]?(?:[ \t][^\n]*)?$", text)
    if not match:
        raise ValueError(f"Missing changelog for {current}")
    body = re.split(r"(?m)^## ", text[match.end():], maxsplit=1)[0].strip()
    if not body:
        raise ValueError("Changelog is empty")
    return body


def targets(current):
    for mc, loaders in [("1.20.1", ("fabric", "forge")), ("1.21.1", ("fabric", "neoforge")),
                        ("1.21.11", ("fabric", "neoforge")), ("26.1.2", ("fabric", "neoforge")),
                        ("26.3", ("fabric", "neoforge"))]:
        for loader in loaders:
            stem = loader if mc == "1.21.1" else f"{loader}-{mc}"
            yield mc, loader, f"godwand-{stem}-{current}.jar"


def changelog_html(text):
    # Keep list text directly inside li; paragraph wrappers split bullets on CurseForge.
    output = []
    listing = False
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith("- "):
            if not listing:
                output.append("<ul>")
                listing = True
            output.append("<li>" + html.escape(line[2:]) + "</li>")
        else:
            if listing:
                output.append("</ul>")
                listing = False
            heading = line.startswith("#")
            content = html.escape(line.lstrip("# ") if heading else line)
            output.append("<p>" + ("<strong>" + content + "</strong>" if heading else content) + "</p>")
    if listing:
        output.append("</ul>")
    return "".join(output)


def prepare(current, release_type):
    directory = ROOT / "build" / "release" / current
    sums = {}
    for line in (directory / "SHA256SUMS.txt").read_text(encoding="utf-8").splitlines():
        digest, name = line.split("  ", 1)
        sums[name] = digest
    entries = []
    changes = changelog_html(changelog(current))
    for mc, loader, name in targets(current):
        path = directory / name
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if sums.get(name) != digest:
            raise ValueError(f"Checksum mismatch: {name}. Rebuild with releaseJars.")
        with zipfile.ZipFile(path) as jar:
            if jar.testzip():
                raise ValueError(f"Corrupt JAR: {name}")
            if loader == "fabric":
                mod = json.loads(jar.read("fabric.mod.json"))
                mod_id = mod.get("id")
            else:
                metadata = "META-INF/mods.toml" if loader == "forge" else "META-INF/neoforge.mods.toml"
                mod = tomllib.loads(jar.read(metadata).decode("utf-8"))["mods"][0]
                mod_id = mod.get("modId")
            if mod_id != "godwand" or mod.get("version") != current:
                raise ValueError(f"Wrong mod/version in {name}")
        metadata = {"displayName": f"God Wand {current} - {loader.title()} {mc}",
                    "gameVersionNames": [mc, {"fabric": "Fabric", "forge": "Forge", "neoforge": "NeoForge"}[loader], "Client", "Server"],
                    "releaseType": release_type, "changelogType": "html", "changelog": changes}
        if loader == "fabric":
            metadata["relations"] = {"projects": [{"slug": "fabric-api", "type": "requiredDependency"}]}
        entries.append((path, digest, metadata))
    return directory, entries


class NoRedirect(urllib.request.HTTPRedirectHandler):
    # Never forward the private token to a redirect destination.
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def request(route, token, data=None, content_type=None):
    headers = {"X-Api-Token": token, "User-Agent": "GodWand-Release/1.0"}
    if content_type:
        headers["Content-Type"] = content_type
    req = urllib.request.Request(API + route, data=data, headers=headers)
    try:
        with urllib.request.build_opener(NoRedirect).open(req, timeout=180) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        detail = "Request rejected"
        try:
            payload = json.loads(error.read(16384))
            detail = str(payload.get("errorMessage", detail))
        except (OSError, ValueError):
            pass
        if token:
            detail = detail.replace(token, "[REDACTED]")
        raise RuntimeError(f"CurseForge HTTP {error.code}: {detail[:2000]}") from None
    except (OSError, ValueError):
        raise RuntimeError("CurseForge request did not complete. Check the project file list before retrying an upload.") from None


def multipart(path, metadata):
    boundary = "GodWand" + uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n'
            'Content-Type: application/json\r\n\r\n').encode()
    body += json.dumps(metadata).encode("utf-8")
    body += (f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{path.name}"\r\n'
             'Content-Type: application/java-archive\r\n\r\n').encode()
    body += path.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    return body, "multipart/form-data; boundary=" + boundary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group()
    action.add_argument("--upload", action="store_true")
    action.add_argument("--dry-run", action="store_true", help="Offline preview (default)")
    action.add_argument("--check-token", action="store_true", help="Read CurseForge game versions; uploads nothing")
    parser.add_argument("--build", action="store_true", help="Build all ten targets first")
    parser.add_argument("--release-type", choices=("alpha", "beta", "release"), default="release")
    args = parser.parse_args()
    project, token = settings()
    if args.check_token:
        if not token:
            raise ValueError("CURSEFORGE_TOKEN is missing from .env")
        versions = request("/game/versions", token)
        if not isinstance(versions, list):
            raise ValueError("Unexpected CurseForge game version response")
        print(f"API read succeeded; {len(versions)} game version entries. Upload permission is checked when uploading. Nothing uploaded.")
        return
    if args.build:
        subprocess.run([str(ROOT / "gradlew.bat"), "releaseJars", "--console=plain"], cwd=ROOT, check=True)
    current = version()
    directory, entries = prepare(current, args.release_type)
    print(f"God Wand {current} | CurseForge project {project} | {len(entries)} JARs")
    for path, digest, metadata in entries:
        print(f"  {path.name} | {', '.join(metadata['gameVersionNames'])} | SHA256 {digest[:16]}")
    if not args.upload:
        print("DRY RUN: no network requests, no uploads. Changelog:")
        print(entries[0][2]["changelog"])
        return
    if not token:
        raise ValueError("CURSEFORGE_TOKEN is missing from .env")
    versions = request("/game/versions", token)
    names = {v["name"] for v in versions}
    required = {name for _, _, metadata in entries for name in metadata["gameVersionNames"]}
    if required - names:
        raise ValueError("CurseForge does not list these version tags: " + ", ".join(sorted(required - names)))
    journal = directory / "curseforge-uploads.json"
    saved = json.loads(journal.read_text(encoding="utf-8")) if journal.exists() else {}
    for path, digest, metadata in entries:
        key = f"{project}/{path.name}/{digest}"
        if key in saved:
            print(f"Already uploaded: {path.name} (file {saved[key]})")
            continue
        body, content_type = multipart(path, metadata)
        result = request(f"/projects/{project}/upload-file", token, body, content_type)
        if not isinstance(result.get("id"), int):
            raise RuntimeError("Upload response has no file ID. Check CurseForge before retrying.")
        saved[key] = result["id"]
        temporary = journal.with_suffix(".tmp")
        temporary.write_text(json.dumps(saved, indent=2) + "\n", encoding="utf-8")
        temporary.replace(journal)
        print(f"Uploaded: {path.name} (file {result['id']})")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"Release stopped: {error}", file=sys.stderr)
        sys.exit(1)
