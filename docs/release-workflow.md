# Release workflow

The `.github/workflows/release.yml` workflow publishes these assets for each
`v*` tag or manual release:

- `GangChat_v<version>.dmg`
- `GangChat_v<version>.exe`
- `GangChat-<version>-windows.zip`
- `GangChat_v<version>.apk`

Desktop signing is optional while certificates are being provisioned. When all
secrets for a desktop platform are absent, the workflow publishes an unsigned
artifact and adds warnings to the job summary and GitHub Release notes. When
all required secrets are present, signing and verification are mandatory.
Partially configured or invalid signing settings fail the affected job so a
misconfigured release cannot be mistaken for a signed one.

After the GitHub Release is published, the `deploy-web` job builds the web
client and deploys it to `https://ky-z.com/gang-chat/app/`. It follows the same
rule: with none of `WEB_DEPLOY_SSH`, `WEB_DEPLOY_SSH_KEY` and
`WEB_DEPLOY_KNOWN_HOSTS` set it is skipped with a warning, and with only some of
them set it fails. Setup is described in `docs/web-spike.md` (部署).

## Windows signing

The Windows application executable is named `GangChat.exe`. The installer
recognizes and removes a legacy installation containing `client.exe`, so an
upgrade does not leave the old executable behind.

Configure these repository secrets:

- `WINDOWS_CERTIFICATE_BASE64`: base64-encoded PFX containing the production
  Authenticode certificate, its private key, and preferably its certificate
  chain.
- `WINDOWS_CERTIFICATE_PASSWORD`: password protecting that PFX.

The certificate must be a currently valid, publicly trusted code-signing
certificate with the Code Signing extended key usage. The workflow imports it
into a temporary user certificate store, signs `GangChat.exe`, the embedded
NSIS uninstaller, and the final installer using SHA-256 and an RFC 3161
timestamp, then verifies the resulting trust status. The portable ZIP contains
the same signed `GangChat.exe`.

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

The macOS job applies a hardened-runtime Developer ID signature to the app and
DMG, submits the DMG to Apple's notary service, and staples the accepted ticket
before publishing. Configure these repository secrets:

- `MACOS_CERTIFICATE_BASE64`: base64-encoded P12 containing the Developer ID
  Application certificate and private key.
- `MACOS_CERTIFICATE_PASSWORD`: password protecting that P12.
- `MACOS_SIGNING_IDENTITY`: full identity name beginning with
  `Developer ID Application:`.
- `APPLE_NOTARY_KEY_BASE64`: base64-encoded App Store Connect API private key
  (`AuthKey_<key-id>.p8`).
- `APPLE_NOTARY_KEY_ID`: App Store Connect API key ID.
- `APPLE_NOTARY_ISSUER_ID`: App Store Connect API issuer ID.

If all six macOS signing and notarization secrets are absent, the DMG is still
published unsigned and not notarized, with a prominent warning. Supplying only
part of the set is treated as a configuration error and fails the macOS job.

For example, create single-line base64 values on macOS with:

```bash
base64 -i gang-chat-developer-id.p12 | tr -d '\n'
base64 -i AuthKey_ABC123XYZ.p8 | tr -d '\n'
```

Apple Developer Program membership and a Developer ID Application certificate
are required. After downloading a release, verify it on macOS with:

```bash
codesign --verify --deep --strict --verbose=2 /Applications/client.app
xcrun stapler validate GangChat_v1.2.3.dmg
spctl --assess --type open --context context:primary-signature \
  --verbose=2 GangChat_v1.2.3.dmg
```

## macOS in-app updates (Sparkle)

The macOS app runs in the App Sandbox. Anything a sandboxed app writes is
quarantined as "created by a sandboxed app", and macOS refuses to launch an
app copied out of a DMG the app downloaded itself (`spctl`: "File created by an
AppSandbox, exec/open not allowed"). The macOS client therefore updates
through [Sparkle 2](https://sparkle-project.org): it reads an appcast, checks
the EdDSA signature of the update and installs it with Sparkle's XPC
installer, which runs outside the sandbox, so the installed app carries no
quarantine flag.

- The app checks at launch (when automatic update prompts are on) and from
  Settings → About. Sparkle shows its own update window.
- The appcast is `releases/appcast.xml` next to the installers in the release
  bucket (`release_bucket_url` in `assets/config/app_config.json`).
- Sparkle compares `CFBundleVersion`, so the macOS build number is
  `major * 1000000 + minor * 1000 + patch`, the same code Android uses.
- Builds without an update-signing key fall back to opening the DMG link in the
  browser. A browser download carries the normal quarantine flag instead of
  the sandbox one, so the app can launch; while builds are ad-hoc signed that
  takes "Open Anyway" in System Settings → Privacy & Security once per update.

### One-time setup

1. Build macOS once so CocoaPods installs Sparkle's tools, then create the
   update-signing key pair. `generate_keys` keeps the private key in your login
   keychain and prints the public key:

   ```bash
   macos/Pods/Sparkle/bin/generate_keys
   ```

2. Put the printed public key in `SUPublicEDKey` in `macos/Runner/Info.plist`
   and commit it. It is public and belongs in the repository.
3. Export the private key and add the file's contents as the repository secret
   `SPARKLE_ED_PRIVATE_KEY`:

   ```bash
   macos/Pods/Sparkle/bin/generate_keys -x sparkle_private_key
   ```

   Back the private key up in the secret manager, then delete the exported
   file. **If the private key is lost, installed apps can never accept another
   update**, because they only trust the public key they shipped with.

Like desktop signing, the release workflow skips the appcast with a warning
when neither the secret nor `SUPublicEDKey` is set, and fails when only one is.
It also refuses to publish an update whose signature doesn't verify against
`SUPublicEDKey` (`tool/verify_sparkle_signature.swift`).

### Each release

The macOS artifact contains `GangChat_v<version>.dmg` and `appcast.xml`. Upload
the DMG to `releases/` in the bucket first, then `appcast.xml` (replacing the
previous one). The appcast points at the DMG's bucket URL, so uploading it
first would offer an update that can't be downloaded yet.

### Moving existing installs to Sparkle

Versions up to 0.10.0 still use the old in-app download, which leaves an app
that won't launch. Users on those versions should install the first
Sparkle-enabled release from the homepage in their browser. Anyone already
stuck can repair the installed app with:

```bash
xattr -drs com.apple.quarantine /Applications/GangChat.app
```

Sparkle works with the current ad-hoc signed builds, because it verifies
updates with its own EdDSA key. Developer ID signing (above) is still worth
setting up: ad-hoc builds change signature with every release, so macOS asks
again for microphone, camera, screen recording and keychain access after each
update.

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
is signed and that at least one signer certificate is present.

The APK uses the workflow input as its Android `versionName`. Its numeric
`versionCode` is deterministic: `major * 1,000,000 + minor * 1,000 + patch`
(for example, `1.2.3` becomes `1002003`). Minor and patch components must each
fit in the range `0..999`, and the final value must fit Android's supported
positive version-code range. Version components use canonical decimal notation
without leading zeroes.

For local development, `flutter build apk --release` keeps the existing debug
signing fallback unless all four signing environment variables are provided.
The project's local/default build version is `1.0.0+1`, so ordinary Android
and Windows debug builds both report `1.0.0`; release builds override it with
the workflow input.
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
