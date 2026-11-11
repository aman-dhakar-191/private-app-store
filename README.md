# private-app-store

A small Android app store for you and your friends. GitHub Releases is the backend:
every app is a GitHub repo, every version is a release with an `.apk` attached.

## How it works

1. [`catalog.json`](catalog.json) lists the apps in the store (name, package name, repo).
2. The **Release index** workflow ([`index.yml`](.github/workflows/index.yml)) looks up each
   app's **latest release** (first `.apk` asset) and the store's own, and publishes them as
   `index.json` on the `index` branch. It runs every 15 minutes, when `catalog.json` changes,
   after each store release, and on demand.
3. The store app reads `catalog.json` and `index.json` from raw.githubusercontent.com: two
   requests per refresh, neither counted against the GitHub API limit. Only an app the index
   doesn't list yet (just added to the catalog) is looked up through the GitHub API directly.
4. It keeps the last copy of both files it downloaded. When a refresh fails (offline, rate
   limited), it shows the saved list with a note instead of an empty screen.
5. It compares the release tag (`v1.2.0` → `1.2.0`) with the installed app's
   `versionName`, and shows **Install**, **Update** or **Open**.
6. Tapping an app opens its page: full release notes, versions and size, Install / Update /
   Open, and a link to its releases on GitHub. The page asks GitHub about that one app
   directly (one API call, at most every 5 minutes per app), so it can show a release the
   index hasn't picked up yet. If that call fails it says so and shows the index's version.
7. Install downloads the APK, checks it against the SHA-256 GitHub published for that file (a
   corrupted or swapped download is refused and deleted), and hands it to Android's package
   installer, which shows the normal system confirmation.

### Downloads, Update all, cancel and retry

While an app downloads, its card shows the step in words (Downloading, with how much of the file is
done and a percentage; Installing, while Android asks you to confirm), a bar, and **Cancel**.
Cancelling stops the download and deletes the partly downloaded file. If a download fails (no
network, or a file that doesn't match its checksum), the card says why and offers **Retry** and
**Dismiss**.

When two or more apps have updates, **Update all (N)** appears at the top of the list. It goes
through them one after another: it downloads an app, Android asks you to confirm the install, and
only then does the next one start. **Cancel all** stops the one running and drops the rest, and
**Cancel** on one app takes just that app out. The store's own update isn't part of Update all,
because installing it restarts the store.

### Store updates

The **⋮** menu at the top right has **Check for update** and **About**. The About page shows the
store's version and has a **Check for update** button that asks GitHub directly (one request)
whether there is a newer store, and says what it found, including when it checked. This skips the
release index, which can lag a few minutes. A newer release also shows the banner described below
and an **Update store** button on the About page. The home page shows nothing extra unless there is
an update.

On every launch and refresh, the store also checks this repo's latest release. This check
is separate from loading the app list, so a broken catalog or app repo never hides a store
update. If its
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
3. Open **App Store**. Until it's allowed to install apps, an **Allow installing apps** card
   sits at the top: tap **Allow**, turn on *Allow from this source*, and go back. Android
   doesn't let apps switch this on themselves.

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
- `icon` (optional) is where the app's icon comes from when it isn't installed. See
  [Icons and descriptions](#icons-and-descriptions).

The app's repo must follow the rules in [docs/publishing-an-app.md](docs/publishing-an-app.md).
That page also has a ready-made GitHub Actions workflow you can copy into each app's repo.

### Icons and descriptions

- **Installed apps** always show their own icon (Android gives it to the store), so nothing has to
  be set up.
- **Apps that aren't installed yet** show `icons/<package name>.png` from this repo (for example
  `icons/io.github.amandhakar.documents.png`), if the file exists. A square PNG of about 256 px is
  right; the store rounds the corners. To use a different place, set `"icon"` in the catalog entry
  to an `https://` address, or to a path inside this repo. Other kinds of address are ignored.
- With no icon at all, the app is listed without one, as before.
- The text under the name is the catalog `description`. The line below it says whether the app is
  installed and up to date, or what the update is.
- Release notes are shown as plain text: markdown marks are removed, and GitHub's generated
  "What's Changed" list, "New Contributors" and "Full Changelog" link are left out when you wrote a
  note yourself. Write a short note in the release description for the best result.

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
- **New releases take up to ~20 minutes to show:** up to 15 minutes until the index
  workflow's next run (GitHub sometimes runs scheduled workflows late), plus a few minutes
  of caching on raw.githubusercontent.com. An app's release can refresh the index at once:
  see [docs/publishing-an-app.md](docs/publishing-an-app.md).
- **GitHub rate limit:** the API allows 60 calls per hour per network without a login, and
  phones on mobile data often share one network address with many others. Store versions
  before the release index used one call per app on every refresh and could hit it; now
  only apps missing from the index use the API.
- **Scheduled workflows pause** after 60 days without activity in a public repo. If the
  index stops updating, run **Actions → Release index → Run workflow** (or push anything).
- **Only launchable apps** are detected as installed (apps with a launcher icon).
- **Checksums need GitHub's digest.** A release whose files GitHub gave no checksum for (older
  uploads) is installed without that check; Android still refuses an update signed with a
  different key. The checksum protects against a corrupted or tampered download, not against a
  release someone with access to the repo published on purpose.

## Building locally

Needs JDK 17 and the Android SDK (Android Studio installs both).

```sh
./gradlew assembleDebug
```
