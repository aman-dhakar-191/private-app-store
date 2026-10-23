#!/usr/bin/env python3
"""Checks build-index.py against fake GitHub responses. Run: python3 scripts/test-build-index.py"""
import importlib.util
import pathlib
import sys

spec = importlib.util.spec_from_file_location("build_index", pathlib.Path(__file__).with_name("build-index.py"))
bi = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bi)


def rel(tag, apk=True, draft=False, body=""):
    assets = [{"name": "notes.txt", "browser_download_url": "x", "size": 1}]
    if apk:
        assets.append({"name": f"app-{tag}.APK", "browser_download_url": f"https://dl/{tag}.apk", "size": 42})
    return {"tag_name": tag, "draft": draft, "body": body, "assets": assets}


FAKE = {
    "/repos/me/store/releases/latest": rel("v0.3.0", body="store notes"),
    "/repos/me/game/releases/latest": rel("v1.10.0"),
    "/repos/me/empty/releases/latest": None,
    "/repos/me/noapk/releases/latest": rel("v2.0.0", apk=False),
    "/repos/me/multi/releases?per_page=50": [
        rel("demo-v1.9.0"),
        rel("demo-v1.10.0"),
        rel("demo-v2.0.0", draft=True),
        rel("demo-v3.0.0", apk=False),
        rel("v9.9.9"),
        rel("other-v5.0.0"),
    ],
}

catalog = {
    "apps": [{"repo": "me/game"}, {"repo": "me/empty"}, {"repo": "me/noapk"}],
    "prereleaseApps": [{"repo": "me/multi", "tagPrefix": "demo-v"}],
}
index = bi.build_index(catalog, "me/store", lambda path: FAKE[path])
failures = []


def check(actual, expected, what):
    if actual != expected:
        failures.append(f"{what}: expected {expected!r}, got {actual!r}")


check(index["store"]["version"], "0.3.0", "store version")
check(index["store"]["notes"], "store notes", "store notes")
check(index["releases"]["me/game"]["version"], "1.10.0", "latest release version")
check(index["releases"]["me/game"]["apkUrl"], "https://dl/v1.10.0.apk", "first .apk asset, any case")
check(index["releases"]["me/empty"], None, "repo without releases")
check(index["releases"]["me/noapk"], None, "release without an apk")
check(index["releases"]["me/multi#demo-v"]["version"], "1.10.0", "highest tagged version, no drafts or apk-less")
check(bi.compare_versions("1.10.0", "1.9.2") > 0, True, "numeric compare")
check(bi.compare_versions("1.0.0-beta", "1.0.0") != 0, True, "suffix compare")
check(bi.index_key({"repo": "a/b"}, True), "a/b#v", "default tag prefix in key")

for f in failures:
    print("FAIL:", f, file=sys.stderr)
print("build-index.py: all checks passed" if not failures else f"{len(failures)} failed")
sys.exit(1 if failures else 0)
