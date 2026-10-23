#!/usr/bin/env python3
"""Writes index.json: the latest release of every app in catalog.json, plus the store's own.

The store app reads this one file from raw.githubusercontent.com instead of asking the GitHub
API once per app, so phones don't hit the API's 60-requests-per-hour limit. Run by the
"Release index" workflow, which publishes the file to the `index` branch.

Usage: GITHUB_TOKEN=... scripts/build-index.py catalog.json owner/store-repo > index.json

The release-picking rules match GitHub.kt in the app:
- apps: the repo's latest release, first .apk asset, tag "v1.2.3" -> version "1.2.3"
- prereleaseApps: the highest version among the repo's last 50 releases (pre-releases
  included, drafts excluded) whose tag starts with tagPrefix and that has an .apk
"""
import json
import os
import re
import sys
import urllib.error
import urllib.request

API = "https://api.github.com"


def index_key(app, prerelease):
    """Must match indexKey() in GitHub.kt."""
    return f"{app['repo']}#{app.get('tagPrefix') or 'v'}" if prerelease else app["repo"]


def compare_versions(a, b):
    """Port of compareVersions() in GitHub.kt."""
    pa, pb = re.split(r"[.\-+]", a), re.split(r"[.\-+]", b)
    for i in range(max(len(pa), len(pb))):
        x = pa[i] if i < len(pa) else "0"
        y = pb[i] if i < len(pb) else "0"
        if x.isdigit() and y.isdigit():
            c = (int(x) > int(y)) - (int(x) < int(y))
        else:
            c = (x > y) - (x < y)
        if c:
            return c
    return 0


def to_release(rel, prefix):
    apk = next((a for a in rel.get("assets", []) if a["name"].lower().endswith(".apk")), None)
    if apk is None:
        return None
    tag = rel["tag_name"]
    version = tag[len(prefix):] if tag.startswith(prefix) else tag
    if version.startswith("v"):
        version = version[1:]
    return {
        "tag": tag,
        "version": version,
        "notes": rel.get("body") or "",
        "apkUrl": apk["browser_download_url"],
        "apkSize": apk.get("size", 0),
    }


def latest_release(fetch, repo):
    rel = fetch(f"/repos/{repo}/releases/latest")
    return None if rel is None else to_release(rel, "v")


def latest_tagged_release(fetch, repo, prefix):
    best = None
    for rel in fetch(f"/repos/{repo}/releases?per_page=50") or []:
        if rel.get("draft") or not rel["tag_name"].startswith(prefix):
            continue
        r = to_release(rel, prefix)
        if r and (best is None or compare_versions(r["version"], best["version"]) > 0):
            best = r
    return best


def build_index(catalog, store_repo, fetch):
    releases = {}
    for key, prerelease in (("apps", False), ("prereleaseApps", True)):
        for app in catalog.get(key, []):
            if prerelease:
                releases[index_key(app, True)] = latest_tagged_release(fetch, app["repo"], app.get("tagPrefix") or "v")
            else:
                releases[index_key(app, False)] = latest_release(fetch, app["repo"])
    return {"store": latest_release(fetch, store_repo), "releases": releases}


def github_fetch(path):
    """GET from the GitHub API. Returns None on 404; raises on any other error."""
    req = urllib.request.Request(API + path, headers={"Accept": "application/vnd.github+json"})
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.load(resp)
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise


def main():
    catalog_path, store_repo = sys.argv[1], sys.argv[2]
    with open(catalog_path, encoding="utf-8") as f:
        catalog = json.load(f)
    index = build_index(catalog, store_repo, github_fetch)
    json.dump(index, sys.stdout, indent=2, sort_keys=True, ensure_ascii=False)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
