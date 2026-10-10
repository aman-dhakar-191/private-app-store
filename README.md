# private-app-store

A small Android app store for you and your friends. GitHub Releases is the backend:
every app is a GitHub repo, every version is a release with an `.apk` attached.

## How it works

1. [`catalog.json`](catalog.json) lists the apps in the store (name, package name, repo).
2. The store app reads that file from this repo's `main` branch.
3. For each app it asks the GitHub API for the repo's **latest release** and takes the
   first `.apk` asset.
4. It compares the release tag (`v1.2.0` → `1.2.0`) with the installed app's
   `versionName`, and shows **Install**, **Update** or **Open**.
5. Install downloads the APK and hands it to Android's package installer, which shows
   the normal system confirmation.

### Store updates

On every launch and refresh, the store also checks this repo's latest release. If its
tag is newer than the installed store, a **Store update available** banner appears at
the top with the release notes and an **Update store** button. Android closes the
store while it installs the new version; open it again afterwards.

The store does not need to be in `catalog.json` for this.

### Background update notifications

About every 6 hours (when the phone is online), the store checks in the background for
updates to the store and to any installed app. If it finds a version it hasn't told you
about yet, it posts one notification listing them; tapping it opens the store. The same
update is not notified twice.

On Android 13+ the store asks for notification permission on first launch. If you
decline, the check still runs but stays silent; you can turn it on later in the phone's
app settings. Android may delay the check on battery saver or if the store hasn't been
opened in a long time.

## Installing the store (first time)

1. Open the latest release: <https://github.com/aman-dhakar-191/private-app-store/releases/latest>
2. Download the `.apk` on your phone and open it. Android will ask you to allow
   installing from your browser; allow it.
3. Open **App Store**. The first time you tap Install, it sends you to settings to allow
   the store to install apps.

Send your friends the same link.

## Adding an app

Add an entry to `catalog.json` and push to `main`. Friends see it on their next refresh.

```json
{
  "name": "My Notes",
  "package": "com.example.notes",
  "repo": "aman-dhakar-191/notes",
  "description": "Simple notes app.",
  "minSdk": 26
}
```

- `minSdk` (optional) is the app's `minSdk`. On phones with an older Android version the
  store shows "Needs Android X+" instead of an Install button that would only fail.

The app's repo must follow the rules in [docs/publishing-an-app.md](docs/publishing-an-app.md).
That page also has a ready-made GitHub Actions workflow you can copy into each app's repo.

### Pre-release apps

Apps published only as GitHub **pre-releases** go in the `prereleaseApps` list instead. They
appear in a separate **Pre-release** section at the bottom of the store.

```json
"prereleaseApps": [
  {
    "name": "Passkey Demo",
    "package": "io.github.amandhakar.passkeydemo",
    "repo": "aman-dhakar-191/passkey-provider-android",
    "tagPrefix": "demo-v",
    "minSdk": 34
  }
]
```

- `tagPrefix` picks this app's releases when a repo publishes several apps: the store takes
  the highest version among releases whose tag starts with it (`demo-v1.0.2` → `1.0.2`),
  pre-releases included.
- Keep these out of `apps`. Store versions older than 0.2.0 only read `apps` and would use
  the repo's latest normal release there, which may be a different app.

## Releasing the store itself

### One-time setup: a signing key

Android only installs an update if it is signed with the **same key** as the installed
copy. Make one key and keep it forever (losing it means everyone has to uninstall and
reinstall).

```sh
keytool -genkeypair -v -keystore release.jks -alias appstore \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks   # copy the output
```

In this repo on GitHub, go to **Settings → Secrets and variables → Actions** and add:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the base64 output above |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `appstore` (or whatever alias you chose) |
| `KEY_PASSWORD` | the key password |

Back up `release.jks` and its passwords somewhere safe. Never commit it.

### Each release

1. Go to **Actions → Release → Run workflow** (works from the phone too).
2. Pick what to increase:
   - **patch** for fixes: `1.4.2` → `1.4.3`
   - **minor** for new features: `1.4.2` → `1.5.0`
   - **major** for big changes: `1.4.2` → `2.0.0`
3. Optionally write what changed, then **Run workflow**.

It finds the latest `vX.Y.Z` tag, builds the APK signed with your key, and publishes the
GitHub release (creating the tag). The first release starts from `0.0.0`, so pick **minor**
to get `0.1.0`. A few minutes later, the store shows the update banner and phones get the
update notification.

`versionCode` is derived from the version (`major × 1000000 + minor × 1000 + patch`), so it
always goes up with the version. Minor and patch must stay below 1000.

Builds on pull requests and `main` (the [Android workflow](.github/workflows/android.yml))
are only test builds: not signed with your key, and kept as a workflow artifact.

## Limits

- **Public repos only.** The store calls GitHub without a login, so it can't see private
  repos. Supporting private repos would mean putting a GitHub token on every friend's
  phone.
- **GitHub rate limit:** 60 API calls per hour per network without a login. One refresh
  (or background check) uses one call per app plus one for the store, which is plenty
  for a handful of apps.
- **Only launchable apps** are detected as installed (apps with a launcher icon).

## Building locally

Needs JDK 17 and the Android SDK (Android Studio installs both).

```sh
./gradlew assembleDebug
```
