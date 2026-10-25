# Building the APK

Assumes you've followed [Build from source](README.md#build-from-source) —
`bun install`, `bun run setup`, `worktree/` exists and `bun run lint-patches` is clean.
(Windows-specific environment setup: [WINDOWS_SETUP.md](WINDOWS_SETUP.md).)

## Requirements

- **JDK 21** (Microsoft Build of OpenJDK, Temurin, or any other distribution — CI uses
  `temurin@21`).
- **Android SDK**, matching what `TMessagesProj_App/build.gradle` pins:
  - `compileSdk 36` / `targetSdk 36`, `minSdk 26`
  - `build-tools 36.0.0`
  - `ndk 27.2.12479018`
  - `cmake 3.22.1`

The simplest way to get all of the SDK components: install Android Studio and let its
first-run "Standard" setup wizard download the default SDK, then let it prompt you to
install the exact NDK/cmake versions above the first time you open `worktree/` (it
detects the pinned versions from `build.gradle` and offers to install what's missing via
**Tools → SDK Manager** if it doesn't do so automatically).

### Gradle JDK

Android Studio's own bundled JetBrains Runtime (`jbr-21`, shown as the default `Gradle
JDK` for new projects) can sometimes crash the Gradle daemon before it even starts
(`The daemon has terminated unexpectedly on startup attempt #1`). If you hit that, point
Gradle at the real JDK 21 install instead:

**Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** → pick
your JDK 21 install (or **Add JDK from disk...** and browse to it) → **Apply** → re-sync.

## Debug build

```sh
bun run build-debug
```

Equivalent to (run from the repo root, or drop the `cd worktree &&` if you're already
there):

```sh
cd worktree
./gradlew TMessagesProj_App:assembleDebug
```

- **Signing:** already handled. The debug build type points at
  `TMessagesProj/config/release.keystore`, which is checked into the repo, with the
  matching `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`
  already set in the root `gradle.properties`. Nothing to configure locally.
- **Application ID:** debug and release builds both use `app.prism.client`; installing
  a debug build replaces the installed release build.
- **Output:** `worktree/TMessagesProj_App/build/outputs/apk/debug/app.apk`.
- **versionCode:** locally this auto-increments from `worktree/.local_build_number`
  (starts at `1000000`), separate from the GitHub Actions run-number scheme below — you
  don't need to set anything for local builds.
- **ABI:** defaults to `arm64-v8a` only (fast local builds). Set the `ABI_FILTERS` env
  var (comma-separated ABIs, or `all`) before running Gradle if you need other
  architectures, e.g. `ABI_FILTERS=all ./gradlew TMessagesProj_App:assembleDebug`.
  For the x86 emulator, use `ABI_FILTERS=x86_64` on its own (unverified with a list).
- **Pluginless vs plugins:** `bun run setup -- --pluginless` creates the
  `worktree-pluginless/` source tree; use `bun run build-debug-pluginless` to build it.
  The published GitHub release is pluginless and arm64-only.

Only `:TMessagesProj_App` is a real, buildable app module — `TMessagesProj_AppHuawei`,
`TMessagesProj_AppHockeyApp`, and `TMessagesProj_AppStandalone` still exist as
directories but aren't included in `settings.gradle` (they were retired by
`misc/remove-unused-*` patches).

## Release build

```sh
cd worktree
./gradlew TMessagesProj_App:assembleRelease
```

Same local keystore/credentials as the debug build above, with
`minifyEnabled true` + `shrinkResources true` (R8/ProGuard) for a release-shaped local build.

Output: `worktree/TMessagesProj_App/build/outputs/apk/release/app.apk`.

## GitHub Actions release build

The workflow is [`.github/workflows/android.yml`](.github/workflows/android.yml). It runs
when manually dispatched or after a push to `main`. CI sets up the pluginless source tree,
builds `:TMessagesProj_App:assembleRelease` for `arm64-v8a`, and uploads the result as the
`prism-apk` artifact for 30 days. The published APK is named `prism-arm64.apk`.

The workflow requires the repository secrets `API_ID` and `API_HASH`. `INU_BUILD` and the
release tag use the GitHub Actions run number (`build-N`). If `KEYSTORE_PASSPHRASE` is set,
CI decrypts the Prism release key, signs the APK, and publishes a GitHub Release. Without
that secret, CI still uploads an APK artifact, but it is unsigned and cannot update an
installation signed with the Prism release key.

## Troubleshooting

- **`EPERM: operation not permitted, symlink`** during `bun run setup` — Windows
  Developer Mode isn't on. See [WINDOWS_SETUP.md](WINDOWS_SETUP.md).
- **Gradle daemon crashes on startup** — usually the Gradle JDK, see above.
- **`stg`/`bun`/`java` "command not found" right after installing** — PATH changes only
  apply to new terminal sessions/processes, not ones already open.
