# Release workflow

The `.github/workflows/release.yml` workflow publishes these assets for each
`v*` tag or manual release:

- `GangChat_v<version>.dmg` and `appcast.xml` (macOS, Sparkle feed)
- `GangChat_v<version>.exe`, `GangChat_v<version>.exe.blockmap` and
  `latest.yml` (Windows, electron-updater feed)
- `GangChat-<version>-windows.zip`
- `GangChat_v<version>.apk`

The desktop builds are the Electron client (`apps/desktop`, packaged with
electron-builder). The APK is the native Kotlin client (`android/`, built with
Gradle). The release version is written into `apps/desktop/package.json` and
`android/version.properties` before packaging, so the installers, the APK, the
About page and the build numbers all follow the tag. The web client is the
same React UI in the browser (`apps/web`). The Flutter client has been removed.

Desktop signing is optional while certificates are being provisioned. When all
secrets for a desktop platform are absent, the workflow publishes an unsigned
artifact and adds warnings to the job summary and GitHub Release notes. When
all required secrets are present, signing and verification are mandatory.
Partially configured or invalid signing settings fail the affected job so a
misconfigured release cannot be mistaken for a signed one.

After the GitHub Release is published, the `deploy-web` job builds the web
client (`tool/build_web.sh`) and deploys it to
`https://ky-z.com/gang-chat/app/`. It follows the same rule: with none of
`WEB_DEPLOY_SSH`, `WEB_DEPLOY_SSH_KEY` and `WEB_DEPLOY_KNOWN_HOSTS` set it is
skipped with a warning, and with only some of them set it fails. Setup is
described in `docs/web-spike.md` (部署).

## Windows signing

The Windows application executable is named `GangChat.exe`. The NSIS
installer (`apps/desktop/build/installer.nsh`) silently runs the uninstaller of
an earlier Flutter installation (`Uninstall\Gang Chat`) before installing, so
an upgrade from the Flutter app does not leave its files behind or a second
"Gang Chat" in the installed apps list. Settings and sign-in under AppData
are kept.

Configure these repository secrets:

- `WINDOWS_CERTIFICATE_BASE64`: base64-encoded PFX containing the production
  Authenticode certificate, its private key, and preferably its certificate
  chain.
- `WINDOWS_CERTIFICATE_PASSWORD`: password protecting that PFX.

The certificate must be a currently valid, publicly trusted code-signing
certificate with the Code Signing extended key usage. electron-builder signs
`GangChat.exe`, the embedded NSIS uninstaller and the final installer with it,
and the workflow then verifies the trust status. The portable ZIP contains the
same signed `GangChat.exe`. Once releases are signed, keep signing them:
electron-updater only installs updates from the same publisher.

If both Windows signing secrets are absent, the executable, installer, and ZIP
are still published unsigned with a prominent warning. Supplying only one of
the two secrets is treated as a configuration error and fails the Windows job.

Create the base64 value on Windows without adding line breaks:

```powershell
[Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("C:\secure\gang-chat-codesigning.pfx")
) | Set-Clipboard
```

After downloading a release, Windows signatures can be checked with:

```powershell
Get-AuthenticodeSignature .\GangChat.exe
Get-AuthenticodeSignature .\GangChat_v1.2.3.exe
```

For a signed release, both commands should report `Valid`. An intentionally
unsigned release reports `NotSigned`.

## macOS signing and notarization

With a Developer ID, electron-builder signs the app with the hardened runtime
and notarizes it with the App Store Connect API key. Configure these
repository secrets:

- `MACOS_CERTIFICATE_BASE64`: base64-encoded P12 containing the Developer ID
  Application certificate and private key.
- `MACOS_CERTIFICATE_PASSWORD`: password protecting that P12.
- `MACOS_SIGNING_IDENTITY`: full identity name beginning with
  `Developer ID Application:`.
- `APPLE_NOTARY_KEY_BASE64`: base64-encoded App Store Connect API private key
  (`AuthKey_<key-id>.p8`).
- `APPLE_NOTARY_KEY_ID`: App Store Connect API key ID.
- `APPLE_NOTARY_ISSUER_ID`: App Store Connect API issuer ID.

If all six macOS signing and notarization secrets are absent, the app is
ad-hoc signed (`apps/desktop/scripts/after_pack.cjs`) and not notarized, with a
prominent warning. The ad-hoc signature is required: Sparkle refuses an update
whose code signature is invalid, and an unsigned Electron package only carries
a broken linker signature. Supplying only part of the set is treated as a
configuration error and fails the macOS job.

For example, create single-line base64 values on macOS with:

```bash
base64 -i gang-chat-developer-id.p12 | tr -d '\n'
base64 -i AuthKey_ABC123XYZ.p8 | tr -d '\n'
```

Apple Developer Program membership and a Developer ID Application certificate
are required. After downloading a release, verify it on macOS with:

```bash
codesign --verify --deep --strict --verbose=2 /Applications/GangChat.app
xcrun stapler validate GangChat_v1.2.3.dmg
spctl --assess --type open --context context:primary-signature \
  --verbose=2 GangChat_v1.2.3.dmg
```

## macOS in-app updates (Sparkle)

macOS installs update through [Sparkle 2](https://sparkle-project.org): it
reads an appcast, checks the EdDSA signature of the update, installs it and
relaunches the app. Sparkle needs no Developer ID, unlike Electron's
Squirrel.Mac. The Flutter macOS app (sandboxed, with Sparkle's XPC installer)
and the Electron app read the same appcast and trust the same key, so Flutter
installs move to the Electron app through the feed that keeps it current.

The Electron app ships the official Sparkle framework, pinned by version and
checksum in `apps/desktop/scripts/prepare_sparkle.mjs`, which also builds the
small Node-API module the main process uses to drive it
(`apps/desktop/native/sparkle/sparkle.mm`). `npm run dist:mac` runs it.

- The app checks at launch (when automatic update prompts are on) and from
  Settings → About. Sparkle shows its own update window.
- The appcast is `releases/appcast.xml` next to the installers in the release
  bucket (`release_bucket_url` in `assets/config/app_config.json`).
- Sparkle compares `CFBundleVersion`, so the macOS build number is
  `major * 1000000 + minor * 1000 + patch`, the same code Android uses
  (`apps/desktop/scripts/package.mjs` sets it; the release job checks it).
- Development runs and builds without Sparkle fall back to opening the DMG
  link in the browser.

### One-time setup

1. Download the Sparkle release pinned in
   `apps/desktop/scripts/prepare_sparkle.mjs` (`Sparkle-<version>.tar.xz` from
   the Sparkle GitHub releases), unpack it and create the update-signing key
   pair. `generate_keys` keeps the private key in your login keychain and
   prints the public key:

   ```bash
   ./bin/generate_keys
   ```

2. Put the printed public key in `SUPublicEDKey` in
   `apps/desktop/electron-builder.yml` and commit it. It is public and belongs
   in the repository.
3. Export the private key and add the file's contents as the repository secret
   `SPARKLE_ED_PRIVATE_KEY`:

   ```bash
   ./bin/generate_keys -x sparkle_private_key
   ```

   Back the private key up in the secret manager, then delete the exported
   file. **If the private key is lost, installed apps can never accept another
   update**, because they only trust the public key they shipped with.

The release workflow fails without the secret, because every macOS install
updates from this appcast. It also refuses to publish an update whose
signature doesn't verify against `SUPublicEDKey`
(`tool/verify_sparkle_signature.swift`). Sparkle's `sign_update` comes from the
same pinned release (`apps/desktop/build/sparkle/bin/sign_update`).

### Each release

The GitHub Release carries `appcast.xml` next to the installers. Publish it with
the gang-chat-admin S3 panel's release sync, as for the installers: the sync
clears `releases/`, uploads the `.dmg`, `.exe` and `.apk`, and uploads
`appcast.xml` last, because it points at the DMG. Before clearing anything the
sync checks that the appcast downloads the DMG it is about to upload, and
otherwise stops with `RELEASE_APPCAST_MISMATCH` and leaves the bucket unchanged.

If you ever upload by hand, keep the same order: the DMG first, then
`appcast.xml`, or installed apps will be offered an update they can't download
yet.

## Windows in-app updates (electron-updater)

The Windows app reads `latest.yml` from `releases/` and downloads the installer
it names, using the blockmap for differential downloads, then runs it. Until
the release sync also uploads `latest.yml` and the `.blockmap` (it uploads only
the installers today), the app falls back to listing the installers in
`releases/` and downloading the newest one in full, as the Flutter app does.
Flutter installs find the Electron installer the same way, since it keeps the
`GangChat_v<version>.exe` name.

### Moving existing installs to Sparkle

Versions up to 0.10.0 still use the old in-app download, which leaves an app
that won't launch. Users on those versions should install the first
Sparkle-enabled release from the homepage in their browser. Anyone already
stuck can repair the installed app with:

```bash
xattr -drs com.apple.quarantine /Applications/GangChat.app
```

Sparkle works with ad-hoc signed builds, because it verifies updates with its
own EdDSA key. Developer ID signing (above) is still worth setting up: ad-hoc
builds change signature with every release, so macOS asks again for
microphone, camera, screen recording and keychain access after each update.

## Android signing

Published APKs must use the same private key across releases so Android can
install a new version over the previous one. Configure these repository
secrets before running the release workflow:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded contents of the JKS keystore.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: release key alias.
- `ANDROID_KEY_PASSWORD`: release key password.

The workflow rejects missing or invalid signing settings instead of publishing
an APK with a temporary CI debug certificate. Keep the original keystore and
passwords backed up securely; losing them prevents future in-place upgrades.
After building, the workflow uses Android SDK `apksigner` to verify that the APK
carries both a v1 (JAR) and a v2 signature from the release key. Android 7+
only needs v2, but some phones' install-time safety checks and third-party
scanners only read the v1 files and report a v2-only APK as unsigned.

The workflow writes the release version and release time into
`android/version.properties`, which the Gradle build reads, so the APK uses the
workflow input as its Android `versionName`. Its numeric
`versionCode` is deterministic: `major * 1,000,000 + minor * 1,000 + patch`
(for example, `1.2.3` becomes `1002003`). Minor and patch components must each
fit in the range `0..999`, and the final value must fit Android's supported
positive version-code range. Version components use canonical decimal notation
without leading zeroes.

After building, `aapt2 dump badging` checks that the APK carries exactly that
`versionName` and `versionCode`.

For local development, `./gradlew :app:assembleRelease` (run in `android/`)
falls back to the debug signing key unless all four signing environment
variables (`ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) are provided; setting only some
of them fails the build. The committed `android/version.properties` says
`1.0.0`, so local Android builds report `1.0.0`; release builds override it
with the workflow input.
The keystore itself must never be committed; Android keystore file extensions
and `key.properties` are already ignored by `android/.gitignore`.

### Android offline notifications

The release APK enables Firebase Cloud Messaging only when this repository
secret is configured:

- `FIREBASE_GOOGLE_SERVICES_JSON_BASE64`: base64-encoded Firebase Android
  `google-services.json` for package `com.gangchat.client`.

The workflow validates the package name, writes the configuration only for the
Android build, and removes it during cleanup. If the secret is absent, the APK
still builds and all foreground/background realtime behavior remains available,
but notifications cannot wake a terminated process through FCM.

The server needs the matching Firebase project ID and a service-account JSON
with permission to send FCM HTTP v1 messages:

```json
{
  "fcm_project_id": "your-firebase-project-id",
  "fcm_service_account_file": "/secure/gang-chat-firebase-service-account.json"
}
```

Keep the service-account file outside the repository. Restart the server after
changing either value. Empty values leave offline push disabled without
affecting chat message delivery.

FCM registration does not create a realtime connection and therefore does not
mark an account online. Closing the Android process keeps the device
registration; choosing **退出登录** removes it. The server also binds each
registration to its login session and ignores revoked or expired sessions.

FCM requires Google Play services. Mainland OPPO devices without Google
services need an OPPO Push provider implementation and OPPO application
credentials before process-terminated delivery can work on those devices; the
server/device registration boundary is provider-aware so that provider can be
added without changing room-message logic.

## Secret handling

PFX, P12, App Store Connect P8, Android keystore files, Firebase service-account
files, the Sparkle EdDSA private key, and their passwords must be kept outside
the repository and backed up in
an access-controlled secret manager. The private-key file patterns are ignored
by Git as a final safeguard, but repository ignore rules are not a replacement
for secret management.
