# Releasing PDF Meta Viewer

The repository produces self-contained native packages with `jpackage`; users do not need to install Java. Native packages must be built on their target operating system, so macOS artifacts are built on a Mac and Windows artifacts on Windows. This is a `jpackage` limitation, not a project limitation.

Generated files are written under `dist/` and are intentionally ignored by Git.

| Target | Command | Output |
| --- | --- | --- |
| Local macOS | `./scripts/build-macos.sh app` | `.app` |
| Mac App Store | `./scripts/build-macos.sh app-store` | signed `.app` and `.pkg` |
| Local Windows | `.\scripts\build-windows.ps1 -Target exe` | `.exe` installer |
| Microsoft Store | `.\scripts\build-windows.ps1 -Target msix` | unsigned `.msix` and `.msixupload` |

## Common prerequisites

- A full JDK 17 or newer, with `java`, `javac`, and `jpackage`
- Maven 3.9 or newer
- A numeric release version such as `1.0.0`
- The project's license, final publisher name, privacy declarations, screenshots, support URL, and Store descriptions completed before submission

The scripts run `mvn clean package`, including all tests, before packaging. Oracle's documentation notes that native packages must be created on each target platform and that the generated runtime is bundled with the app: [JDK 17 Packaging Tool User's Guide](https://docs.oracle.com/en/java/javase/17/jpackage/packaging-tool-user-guide.pdf).

## macOS local application

Run on the same processor family you want to support:

```bash
./scripts/build-macos.sh app
open "dist/macos/PDF Meta Viewer.app"
```

This `.app` is ad-hoc signed and intended for use on the Mac that built it. If it will be downloaded by other users outside the Mac App Store, use a separate Developer ID signing and notarization workflow; Mac App Store signing is not interchangeable with Developer ID distribution.

## Mac App Store package

### One-time Apple setup

1. Join the Apple Developer Program and create the app record in App Store Connect.
2. Register an explicit macOS App ID. Its bundle ID must match `MACOS_BUNDLE_ID`; the repository default is `io.github.alexxfromgit.pdfmetaviewer`.
3. Create and install both a **Mac App Distribution** certificate and a **Mac Installer Distribution** certificate, including their private keys. These are distinct from the Developer ID certificates used outside the Store. See Apple's [certificate overview](https://developer.apple.com/help/account/certificates/certificates-overview/).
4. Create and download a **Mac App Store Connect** provisioning profile for the same explicit App ID and distribution certificate. See [Create an App Store Connect provisioning profile](https://developer.apple.com/help/account/provisioning-profiles/create-an-app-store-provisioning-profile/).
5. Install Xcode Command Line Tools and Transporter. Confirm that the identities are visible with `security find-identity -v`.

JDK 17 refers to the two Keychain identities by their legacy display names, `3rd Party Mac Developer Application: …` and `3rd Party Mac Developer Installer: …`. The script uses those defaults. Set the two identity override variables shown below if Keychain displays different unambiguous names.

### Build

```bash
export PACKAGE_VERSION="1.0.0"
export APP_BUILD_NUMBER="1"
export MACOS_BUNDLE_ID="io.github.alexxfromgit.pdfmetaviewer"
export MACOS_MIN_VERSION="12.0"
export APPLE_SIGNING_KEY_USER_NAME="Your Team Name (TEAMID)"
export APPLE_PROVISIONING_PROFILE="$HOME/Downloads/PDF_Meta_Viewer.provisionprofile"

./scripts/build-macos.sh app-store
```

Optional signing overrides:

```bash
export APPLE_APP_SIGNING_IDENTITY="3rd Party Mac Developer Application: Your Team Name (TEAMID)"
export APPLE_INSTALLER_SIGNING_IDENTITY="3rd Party Mac Developer Installer: Your Team Name (TEAMID)"
export APPLE_SIGNING_KEYCHAIN="$HOME/Library/Keychains/login.keychain-db"
```

The Store build uses `packaging/macos/app-store.entitlements`. App Sandbox is mandatory for Mac App Store apps, and this app requests only read/write access to files selected in native Open and Save dialogs. See Apple's [App Sandbox requirements](https://developer.apple.com/documentation/security/app-sandbox) and [user-selected file guidance](https://developer.apple.com/documentation/security/accessing-files-from-the-macos-app-sandbox).

The script validates the profile against the bundle ID, derives the application/team identity entitlements from it, embeds the profile, signs the bundled Java runtime from the inside out with `jpackage`, re-signs the finished app, creates the flat installer with `productbuild`, and verifies both signatures. The result is:

```text
dist/macos/PDF-Meta-Viewer-1.0.0-mac-app-store.pkg
```

Before upload, smoke-test the signed app, inspect its entitlements with `codesign -dvvv --entitlements -`, and make sure the bundle ID, marketing version, and unique build number match App Store Connect. Drop the `.pkg` into Transporter and choose **Deliver**. See Apple's [Upload builds](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/) guidance for current delivery options.

Do not commit certificates, private keys, provisioning profiles, passwords, or App Store Connect API keys.

## Windows EXE installer

Install a Windows x64, x86, or Arm64 JDK matching the artifact you want to build. JDK 17's `jpackage` also requires WiX Toolset 3.x (`candle.exe` and `light.exe`) on `PATH` for EXE creation.

In PowerShell:

```powershell
.\scripts\build-windows.ps1 -Target exe -Version 1.0.0
```

The result is a per-user, offline installer under `dist\windows`. It includes the Java runtime and creates Start Menu and desktop shortcuts. It is unsigned, so Windows may show a SmartScreen warning. Sign the EXE with a trusted code-signing certificate if it will be distributed broadly outside the Store.

## Microsoft Store MSIX

### One-time Microsoft setup

1. Create a Windows developer account in Partner Center and reserve the product name.
2. On the product identity page, copy the exact **Package/Identity/Name**, **Package/Identity/Publisher**, and publisher display name. The first two are identity values, not free-form labels.
3. Install the Windows 10 or 11 SDK so `MakeAppx.exe` is available. The script locates it from the SDK even when it is not on `PATH`.

Microsoft documents the required manifest identity values and full-trust desktop declaration in [Generating MSIX package components](https://learn.microsoft.com/windows/msix/desktop/desktop-to-uwp-manual-conversion).

### Build

```powershell
$env:MSIX_IDENTITY_NAME = "12345YourPublisher.PDFMetaViewer"
$env:MSIX_PUBLISHER = "CN=01234567-89ab-cdef-0123-456789abcdef"
$env:MSIX_PUBLISHER_DISPLAY_NAME = "Your Publisher Name"

.\scripts\build-windows.ps1 -Target msix -Version 1.0.0
```

The script creates both the raw `.msix` and the recommended `.msixupload` wrapper. It does not sign either artifact. That is correct for a Microsoft Store submission: the Store signs the package after certification, so you do not need to buy a code-signing certificate. See Microsoft's [MSIX signing overview](https://learn.microsoft.com/windows/msix/package/signing-package-overview) and [Store publishing guide](https://learn.microsoft.com/windows/apps/publish/get-started).

The version is normalized to four numeric parts, such as `1.0.0.0`. Microsoft reserves the fourth part for Store use, so the script requires it to be zero.

An unsigned MSIX is not the normal local-install format. Use the generated EXE for local use, or create a trusted test signature when testing the packaged Store identity. Before submission, run the Windows App Certification Kit, test on a clean Windows account, then upload the `.msixupload` in Partner Center. The raw `.msix` is also accepted, while Microsoft recommends `.msixupload` for Store submissions: [Packaging MSIX apps](https://learn.microsoft.com/windows/msix/package/packaging-uwp-apps).

## Release checklist

1. Add the final project license and confirm Maven coordinates, bundle ID, Microsoft identity, publisher names, and support/privacy URLs.
2. Replace the Maven `-SNAPSHOT` version and move `Unreleased` notes in `CHANGELOG.md` into a dated release section.
3. Run `mvn clean verify` with Java 17.
4. Build each artifact on its target operating system and processor architecture.
5. Smoke-test open, drag-and-drop, search, copy, appearance switching, export, installation, uninstall, and upgrade from the prior version.
6. Validate the Mac signatures/package, run the Windows App Certification Kit, and review dependency security alerts.
7. Submit first to TestFlight/limited availability where possible, then complete Store certification and review.
8. Commit the release, create an annotated `vX.Y.Z` tag, and archive checksums for the exact submitted artifacts.

The old placeholder JAR-signing configuration was intentionally removed. Platform signing must use local secure Keychains/certificate stores or protected CI secrets, never hard-coded paths and passwords.
