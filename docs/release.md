# Releasing UmamiT

How Claude publishes a signed APK. The spec is
[06 §6 and §6.1](specs/06-architecture.md#6-build--distribution); decisions
D-035, D-039, D-040 and D-067 (accuracy run).

## Dispatching a release

Claude dispatches `.github/workflows/release.yml` (`workflow_dispatch`)
through the GitHub Actions API/tool. The PO never dispatches or tags.

| Input | Meaning |
|---|---|
| `ref` | Branch or full commit SHA to build, e.g. the milestone PR branch or `main` |
| `version` | The version to release; must equal `umamit.version` in `gradle.properties` at `ref` |

- **Pre-releases:** `X.Y.Z-probe` (D-040) and `X.Y.Z-rc.N` are published as
  GitHub pre-releases, built from the PR branch for the PO's on-device tests.
- **Final releases:** `X.Y.Z` is built from `main` after the milestone PR is
  merged and marked as the latest release.
- **Bump first:** set `umamit.version` in `gradle.properties` on `ref` and
  push before dispatching. Tags are never reused; a failed or wrong release is
  fixed with the next version (`-rc.N+1`, `0.0.N+1-probe`).
- **Workflow on `main` (D-039):** GitHub dispatches only workflows that exist
  on the default branch. Changes to `release.yml` take effect for dispatches
  only once they are on `main`.

## What the workflow checks

In this order; any failure stops the run before a tag or release exists:

1. `version` has the form `X.Y.Z`, `X.Y.Z-rc.N` or `X.Y.Z-probe` and equals
   `umamit.version` at `ref` (checked before anything is built).
2. Neither tag `v<version>` nor a release of that name exists.
3. Both secrets `UMAMIT_KEYSTORE_B64` and `UMAMIT_KEYSTORE_PASSWORD` are set.
4. The build reports the same versionName; its versionCode (from
   `./gradlew :app:printReleaseVersion`, the 06 §6 rule) is the expected one.
5. `./gradlew testDebugUnitTest` passes.
6. The keystore decodes into the runner temp directory (mode 600) and opens
   with the password and alias `umamit`. It is deleted right after the build
   (also on failure); it is never logged or uploaded.
7. `assembleRelease` produces a signed APK.
8. `scripts/verify-release-apk.sh` checks with build-tools 36.1.0:
   - `apksigner verify`: valid signature, exactly one signer;
   - the signer's SHA-256 equals `signing/release-cert-sha256.txt`;
   - package `com.umamit.app`, versionName and versionCode match;
   - the APK is not debuggable.

Then the workflow creates tag `v<version>` on the built commit and a GitHub
Release with `umamit-<version>.apk` and short notes: version, versionCode,
commit, certificate and APK SHA-256.

The verify script also runs locally, with an optional expected-fingerprint
file (4th argument or `UMAMIT_EXPECTED_CERT_FILE`):

```bash
scripts/verify-release-apk.sh app/build/outputs/apk/release/app-release.apk 0.0.1-probe 101
```

## Before each release candidate: the accuracy run

Every `X.Y.Z-rc.N` needs a passing timer accuracy run on the commit it is
built from (TMR-30, D-067). How to dispatch `.github/workflows/accuracy.yml`
and how to read its summary is in
[09 §4a](specs/09-release-plan.md#4a-accuracy-run-before-each-release-candidate).
In short:

1. Push the rc commit, then dispatch `accuracy.yml` with `ref` = that branch
   or SHA (default `api-level` 36, `steps` 50, `inject-delay-ms` 0). A run
   takes about an hour.
2. The job summary must say **Result: PASS** (p95 ≤ 250 ms and max ≤ 1 s for
   the pre-alert and the alert, all 50 steps). Link the run in the rc's
   announcement.
3. Then bump the version and dispatch `release.yml`. If the bump is the only
   change since the run, the run still counts.

Locally, with a device or emulator attached, the same test runs with
`./gradlew :core:timer:assembleDebugAndroidTest && scripts/accuracy-run.sh`
(report in `build/accuracy/`).

## Checking the fingerprint on the phone

The expected certificate SHA-256 is public: `signing/release-cert-sha256.txt`,
the README and every release's notes. On the phone, *Einstellungen › Apps ›
UmamiT* shows the app details. If a tool such as an APK inspector is at hand,
its SHA-256 signer digest must match. In practice the strongest check is
automatic: Android refuses to install an update signed with a different key
over the existing app.
