# Publishing an app to the store

Any Android app can be in the store if its GitHub repo follows these rules.

## Rules

1. **The repo is public.**
2. **Each version is a GitHub release** (not a draft or pre-release; the store only reads
   the release GitHub marks as *latest*).
3. **The release has an `.apk` file attached.** If it has several, the store uses the
   first one, so attach one universal APK.
4. **The tag matches the app's `versionName`.** Tag `v1.4.0` (or `1.4.0`) for
   `versionName = "1.4.0"`. The store uses this to decide whether an update is available.
5. **`versionCode` always goes up**, or Android refuses the update.
6. **Every release is signed with the same key**, or Android refuses the update. Debug
   builds made on CI use a different key every time, so always sign with your own key.
7. **The package name in `catalog.json`** matches the app's `applicationId`.

## Copy-paste workflow

Put this in the app's repo as `.github/workflows/release.yml`, add the same four secrets
described in the main [README](../README.md#one-time-setup-a-signing-key) (you can reuse
one key for all your apps), and make the app's `build.gradle.kts` read them:

```kotlin
android {
    defaultConfig {
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "0.1.0"
    }
    signingConfigs {
        System.getenv("KEYSTORE_FILE")?.let { keystore ->
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.findByName("release") }
    }
}
```

```yaml
name: Release
on:
  push:
    tags: ['v*']
permissions:
  contents: write
jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - name: Decode signing key
        run: |
          echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > "$RUNNER_TEMP/release.jks"
          echo "KEYSTORE_FILE=$RUNNER_TEMP/release.jks" >> "$GITHUB_ENV"
      - name: Build
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          ./gradlew --no-daemon assembleRelease \
            -PversionName="${GITHUB_REF_NAME#v}" -PversionCode="${{ github.run_number }}"
      - uses: softprops/action-gh-release@v2
        with:
          files: app/build/outputs/apk/release/*.apk
          generate_release_notes: true
```

Then release with:

```sh
git tag v1.0.0 && git push origin v1.0.0
```

Using `github.run_number` as `versionCode` keeps it increasing as long as you don't
rename or delete the workflow.

## Showing a new release right away (optional)

The store learns about releases from its release index, which refreshes every 15 minutes.
To refresh it the moment an app is released, give the app's repo a token that can start
workflows in the store repo:

1. Create a fine-grained personal access token with access to `private-app-store` only and
   the **Contents: Read and write** permission (needed to send a `repository_dispatch`).
2. Add it to the app's repo as the secret `STORE_DISPATCH_TOKEN`.
3. Add this step at the end of the app's release workflow:

```yaml
      - name: Tell the store about the release
        if: env.STORE_DISPATCH_TOKEN != ''
        env:
          STORE_DISPATCH_TOKEN: ${{ secrets.STORE_DISPATCH_TOKEN }}
          GH_TOKEN: ${{ secrets.STORE_DISPATCH_TOKEN }}
        run: gh api repos/aman-dhakar-191/private-app-store/dispatches -f event_type=app-released
```

Without it, the release still shows up after the next scheduled run.

