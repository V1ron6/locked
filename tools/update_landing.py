#!/usr/bin/env python3
"""Refresh the release list embedded in index.html.

Reads every dist/locked-v<MAJOR>.<MINOR>.<PATCH>-debug.apk plus the matching entry in
CHANGELOG.md, and rewrites the block between the RELEASES:START / RELEASES:END markers
in index.html. Newest version first. Standard library only; run via `./builder.sh page`
(builder.sh also calls it after every debug build).
"""
import datetime
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"
PAGE = ROOT / "index.html"
CHANGELOG = ROOT / "CHANGELOG.md"

APK_RE = re.compile(r"^locked-v(\d+)\.(\d+)\.(\d+)-debug\.apk$")
HEAD_RE = re.compile(r"^##\s+(\d+\.\d+\.\d+)\s*\((\d+)\)\s*[—–-]\s*(\d{4}-\d{2}-\d{2})\s*$")
START, END = "/*RELEASES:START*/", "/*RELEASES:END*/"


def parse_changelog():
    entries, current = {}, None
    if not CHANGELOG.exists():
        return entries
    for line in CHANGELOG.read_text(encoding="utf-8").splitlines():
        m = HEAD_RE.match(line.strip())
        if m:
            current = {"code": int(m.group(2)), "date": m.group(3), "notes": []}
            entries[m.group(1)] = current
        elif line.startswith("## "):
            current = None  # a heading we don't understand; ignore its bullets
        elif current is not None and line.lstrip().startswith("- "):
            current["notes"].append(line.lstrip()[2:].strip())
    return entries


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    log = parse_changelog()
    releases = []
    for apk in DIST.glob("locked-v*-debug.apk"):
        m = APK_RE.match(apk.name)
        if not m:
            continue
        version = ".".join(m.groups())
        info = log.get(version, {})
        stat = apk.stat()
        releases.append({
            "version": version,
            "code": info.get("code"),
            "date": info.get("date") or datetime.date.fromtimestamp(stat.st_mtime).isoformat(),
            "file": f"dist/{apk.name}",
            "size": stat.st_size,
            "sha256": sha256(apk),
            "notes": info.get("notes", []),
        })
    releases.sort(key=lambda r: tuple(int(p) for p in r["version"].split(".")), reverse=True)

    # Sanity checks: tell the maintainer when changelog, versions and files disagree.
    have = {r["version"] for r in releases}
    for v in log:
        if v not in have:
            print(f"note: CHANGELOG.md lists {v} but dist/locked-v{v}-debug.apk doesn't exist yet "
                  f"(run ./builder.sh to build it)")
    for r in releases:
        if r["code"] is None:
            print(f"warning: {r['version']} has no entry in CHANGELOG.md")
    codes = [r["code"] for r in releases if r["code"] is not None]
    if codes != sorted(codes, reverse=True):
        print("warning: versionCodes don't increase with the version numbers; Android won't "
              "treat newer builds as upgrades")

    if not PAGE.exists():
        sys.exit("index.html not found")
    html = PAGE.read_text(encoding="utf-8")
    if START not in html or END not in html:
        sys.exit(f"index.html is missing the {START} / {END} markers")

    payload = json.dumps(releases, indent=2, ensure_ascii=False).replace("</", "<\\/")
    block = f"{START}\nconst RELEASES = {payload};\n{END}"
    new_html = re.sub(re.escape(START) + r".*?" + re.escape(END), lambda _: block, html, flags=re.S)
    PAGE.write_text(new_html, encoding="utf-8")

    print(f"index.html updated with {len(releases)} release(s):")
    for r in releases:
        print(f"  v{r['version']:<8} code {str(r['code']):<4} {r['size'] / 1e6:5.1f} MB  {r['file']}")


if __name__ == "__main__":
    main()
