# Signing KuDownloader releases

Every KuDownloader release is signed. **Signed and trusted are not the same thing**, though:

- **Cryptographically signed:** the file carries a signature made with our private key. Anyone can check that the file is the one we built and that nobody changed it afterwards.
- **Trusted by the operating system:** Windows, macOS or Android recognise the signer through a certificate authority, Apple or a store, and stop warning. That requires paid certificates or store distribution, which KuDownloader does not use.

KuDownloader's signatures are of the first kind. They prove integrity and that each release came from the same publisher, but **they do not remove** these warnings:

| System | Warning users still see | Why |
|---|---|---|
| Windows | SmartScreen "Windows protected your PC", "Unknown publisher" | The certificate is self-signed, so no certificate authority vouches for it. |
| macOS | "KuDownloader can't be opened because it is from an unidentified developer" | Ad-hoc signature: no Apple Developer ID and no notarization. |
| Android | "Install unknown apps" permission prompt | The APK comes from GitHub, not a store. The signature isn't the cause. |
| Linux | none | Distributions don't warn about packages installed by hand. |

## What is signed, and with what

| Artifact | Signature | Key lives in |
|---|---|---|
| Windows installer `*_x64-setup.exe` and `kudownloader.exe` | Authenticode, SHA-256, timestamped (DigiCert TSA), self-signed certificate `CN=Kuduy, O=Kuduy` | GitHub secrets `WINDOWS_CERTIFICATE`, `WINDOWS_CERTIFICATE_PASSWORD` |
| macOS `KuDownloader.app` (inside the `.dmg` and `.app.tar.gz`) | Ad-hoc code signature (`codesign --sign -`), checked with `codesign --verify --deep --strict` | none: an ad-hoc signature uses no key |
| Android APKs | APK Signature Scheme v2/v3, one permanent release key | GitHub secrets `KU_ANDROID_KEYSTORE_B64`, `KU_ANDROID_KEYSTORE_PASSWORD`, `KU_ANDROID_KEY_ALIAS`, `KU_ANDROID_KEY_PASSWORD` |
| In-app updates (`*.sig`, `latest.json`) | Tauri updater signature (minisign); the app refuses an update whose signature doesn't match | GitHub secret `TAURI_SIGNING_PRIVATE_KEY` |
| Every release file | SHA-256 listed in `SHA256SUMS.txt` | GitHub's digest of each uploaded file |

The release workflows (`.github/workflows/ci.yml`, `android.yml`) do all of this on every `v*` tag. They fail if a signature is missing or broken.

## Secrets and where they come from

Signing material lives only in GitHub Actions secrets and in the maintainer's offline backup. It is never committed: `.gitignore` excludes `*.pfx`, `*.p12`, `*.jks`, `*.keystore`, `*.pem`, `*.key` and `android/keystore.properties`.

On the runner, each secret is decoded to a temporary file only for as long as it's needed, then deleted:

- **Windows:** the `.pfx` is imported into the user certificate store and the file is deleted. The certificate and its private key are removed from the store when the job ends, even if it fails.
- **Android:** the keystore is written to `$RUNNER_TEMP`, which is wiped at the end of the job.

Passwords are passed in environment variables and never echoed.

| Secret | Contents |
|---|---|
| `WINDOWS_CERTIFICATE` | base64 of the `.pfx` (certificate + private key) |
| `WINDOWS_CERTIFICATE_PASSWORD` | the `.pfx` password |
| `KU_ANDROID_KEYSTORE_B64` | base64 of the release keystore (PKCS12) |
| `KU_ANDROID_KEYSTORE_PASSWORD`, `KU_ANDROID_KEY_PASSWORD` | keystore / key password |
| `KU_ANDROID_KEY_ALIAS` | `kudownloader` |
| `TAURI_SIGNING_PRIVATE_KEY` (+ `_PASSWORD` if set) | updater signing key |

Set a secret with the GitHub CLI, reading from a file or a pipe so the value never appears in your shell history:

```bash
base64 -w0 kudownloader-codesign.pfx | gh secret set WINDOWS_CERTIFICATE
gh secret set WINDOWS_CERTIFICATE_PASSWORD < password.txt
```

## Generating the signing material

**The keys already exist. Do not generate new ones for an existing project.** A new Android key means every user must uninstall and reinstall to get updates. A new Windows certificate gets a different thumbprint, which users who checked the old one would notice. Back up the existing material instead:

- Windows: `KuDownloader-windows-signing/` (`.pfx`, public `.cer`, password and thumbprint in `README-KEEP-SAFE.txt`)
- Android: `KuDownloader-android-signing/` (`release.p12`, password in `README-KEEP-SAFE.txt`)

The commands below are for a fork, or for replacing a lost key.

### Windows: self-signed Authenticode certificate (PowerShell)

```powershell
$cert = New-SelfSignedCertificate -Type CodeSigningCert -Subject "CN=Kuduy, O=Kuduy" `
  -KeyAlgorithm RSA -KeyLength 3072 -HashAlgorithm SHA256 -KeyExportPolicy Exportable `
  -CertStoreLocation Cert:\CurrentUser\My -NotAfter (Get-Date).AddYears(10)
$pw = Read-Host -AsSecureString "PFX password"
Export-PfxCertificate -Cert $cert -FilePath kudownloader-codesign.pfx -Password $pw
Export-Certificate -Cert $cert -FilePath kudownloader-codesign.cer   # public part, safe to share
Remove-Item "Cert:\CurrentUser\My\$($cert.Thumbprint)"                # keep the key only in the .pfx
```

### Android: permanent release key

With a JDK:

```bash
keytool -genkeypair -v -keystore release.p12 -storetype PKCS12 -alias kudownloader \
  -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=Kuduy, OU=KuDownloader, O=Kuduy"
```

Without a JDK (OpenSSL):

```bash
openssl req -x509 -newkey rsa:4096 -sha256 -days 10950 -nodes -keyout key.pem -out cert.pem -subj "/CN=Kuduy/OU=KuDownloader/O=Kuduy"
openssl pkcs12 -export -inkey key.pem -in cert.pem -name kudownloader -out release.p12
rm key.pem
```

For a local signed build, put the values in `android/keystore.properties` (`storeFile` relative to `android/app/`, or absolute). This file is ignored by git.

```properties
storeFile=/path/to/release.p12
storePassword=…
keyAlias=kudownloader
keyPassword=…
```

The release build is signed with it; without it (or the `KU_ANDROID_*` variables) Gradle falls back to the debug key.

### macOS: nothing to generate

When the `APPLE_*` secrets are absent, the workflow sets `APPLE_SIGNING_IDENTITY=-`, and Tauri signs the `.app` ad hoc before it builds the `.dmg` and the updater archive. Adding a paid Developer ID later only needs the `APPLE_*` secrets; the workflow then signs with it and notarizes.

## Releasing

1. Bump the version (`Cargo.toml`, `app/package.json`, `app/src-tauri/tauri.conf.json`, `extension/manifest.base.json`, `extension/package.json`, `packaging/arch/PKGBUILD`), commit, and push a tag: `git tag vX.Y.Z && git push origin main vX.Y.Z`.
2. `ci.yml` builds, signs and verifies the desktop apps and attaches them to a draft release, then writes `SHA256SUMS.txt`. `android.yml` builds and signs the APKs, attaches them and refreshes `SHA256SUMS.txt`.
3. Check the draft on GitHub, then publish it.

## Verifying a download

**Any file:** compare it with `SHA256SUMS.txt` from the same release.

```bash
sha256sum -c SHA256SUMS.txt --ignore-missing          # Linux
shasum -a 256 -c SHA256SUMS.txt --ignore-missing      # macOS
```

```powershell
(Get-FileHash .\KuDownloader_X.Y.Z_x64-setup.exe -Algorithm SHA256).Hash   # Windows: compare by eye
```

**Windows signature:**

```powershell
Get-AuthenticodeSignature .\KuDownloader_X.Y.Z_x64-setup.exe | Format-List Status, SignerCertificate
```

Expect `SignerCertificate` to show subject `CN=Kuduy, O=Kuduy` and thumbprint `D301776F4ACFB44635DBD7F0C70A339EA705AFF0`. Because the certificate is self-signed, `Status` reads `UnknownError` ("terminated in a root certificate which is not trusted"). That is expected. `HashMismatch` or `NotSigned` means the file was altered: don't run it.

`signtool verify /pa` likewise reports the untrusted root. Add `/v` to see the signer and the timestamp.

**macOS signature:**

```bash
codesign --verify --deep --strict --verbose=2 /Applications/KuDownloader.app
codesign -dv /Applications/KuDownloader.app 2>&1 | grep Signature   # Signature=adhoc
```

**Android signature** (Android SDK build-tools):

```bash
apksigner verify --print-certs KuDownloader_X.Y.Z_android-arm64-v8a.apk
```

The certificate SHA-256 digest must be the same in every release:
`D7:95:97:91:3B:05:E4:0E:8E:FB:B6:8A:D8:BF:58:48:7D:F3:52:89:4E:61:32:E2:E9:6A:D6:FA:48:3F:CB:75`

## Limits that can't be solved for free

- **Windows SmartScreen and "Unknown publisher"** need a certificate from a trusted authority. The free route is the [SignPath Foundation](https://signpath.org/), which signs open-source projects after review. Paid routes are an OV/EV certificate or Azure Trusted Signing. The workflow already accepts any `.pfx` in `WINDOWS_CERTIFICATE`.
- **macOS Gatekeeper** needs an Apple Developer ID and notarization ($99/year).
- **Android's install prompt** goes away only with store distribution. Google Play doesn't accept video downloaders; F-Droid is free but builds from source and has its own rules.
- **Chrome/Edge extensions** install with one click only from their stores. The Firefox add-on is already signed by Mozilla and listed on addons.mozilla.org.
