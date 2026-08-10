#!/usr/bin/env bash

set -Eeuo pipefail

usage() {
    cat <<'USAGE'
Build PDF Meta Viewer for macOS.

Usage:
  ./scripts/build-macos.sh app
  ./scripts/build-macos.sh app-store

Modes:
  app        Build an ad-hoc signed .app for use on this Mac (default).
  app-store  Build a sandboxed, signed .app and installer .pkg for Transporter.

Optional environment variables:
  PACKAGE_VERSION                 Marketing version, for example 1.0.0.
  APP_BUILD_NUMBER               App Store build number; defaults to PACKAGE_VERSION.
  MACOS_BUNDLE_ID                Explicit App ID; defaults to io.github.alexxfromgit.pdfmetaviewer.
  MACOS_MIN_VERSION              Minimum supported macOS version; defaults to 12.0.
  APP_VENDOR                     Displayed publisher/vendor name.

Required for app-store mode:
  APPLE_SIGNING_KEY_USER_NAME    Team/user part of the signing identity used by jpackage.
  APPLE_PROVISIONING_PROFILE     Path to the Mac App Store Connect profile.

Optional signing overrides:
  APPLE_APP_SIGNING_IDENTITY     Defaults to "3rd Party Mac Developer Application: <team>".
  APPLE_INSTALLER_SIGNING_IDENTITY
                                  Defaults to "3rd Party Mac Developer Installer: <team>".
  APPLE_SIGNING_KEYCHAIN         Non-default keychain path passed to the signing tools.
USAGE
}

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Required command not found: $1" >&2
        exit 1
    fi
}

find_maven() {
    if [[ -x "$project_root/mvnw" ]]; then
        printf '%s\n' "$project_root/mvnw"
        return
    fi
    if command -v mvn >/dev/null 2>&1; then
        command -v mvn
        return
    fi

    local idea_maven
    for idea_maven in \
        "$HOME/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn" \
        "/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn"; do
        if [[ -x "$idea_maven" ]]; then
            printf '%s\n' "$idea_maven"
            return
        fi
    done

    echo "Maven was not found. Install Maven 3.9+ or add mvn to PATH." >&2
    exit 1
}

patch_bundle_metadata() {
    local info_plist="$1/Contents/Info.plist"
    /usr/libexec/PlistBuddy -c "Set :CFBundleVersion $build_number" "$info_plist"
    /usr/libexec/PlistBuddy -c "Set :LSMinimumSystemVersion $minimum_macos_version" "$info_plist"
    /usr/libexec/PlistBuddy -c "Delete :NSMicrophoneUsageDescription" "$info_plist" 2>/dev/null || true
}

mode="${1:-app}"
if [[ "$mode" == "-h" || "$mode" == "--help" ]]; then
    usage
    exit 0
fi
if [[ "$mode" != "app" && "$mode" != "app-store" ]]; then
    echo "Unknown mode: $mode" >&2
    usage >&2
    exit 2
fi

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
project_root="$(CDPATH= cd -- "$script_dir/.." && pwd)"
app_name="PDF Meta Viewer"
bundle_id="${MACOS_BUNDLE_ID:-io.github.alexxfromgit.pdfmetaviewer}"
vendor="${APP_VENDOR:-Alexxfromgit}"
minimum_macos_version="${MACOS_MIN_VERSION:-12.0}"
description="Inspect, search, and export PDF metadata locally."
output_dir="$project_root/dist/macos"
work_dir="$project_root/dist/.work/macos"
input_dir="$work_dir/input"
icon_icns="$work_dir/PDFMetaViewer.icns"
icon_tool_classes="$work_dir/icon-tool"
entitlements="$project_root/packaging/macos/app-store.entitlements"
jar_path="$project_root/target/pdf-meta-viewer.jar"

project_version="$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' "$project_root/pom.xml" | head -n 1)"
app_version="${PACKAGE_VERSION:-${project_version%%-*}}"
build_number="${APP_BUILD_NUMBER:-$app_version}"

if [[ ! "$app_version" =~ ^[0-9]+([.][0-9]+){0,2}$ ]]; then
    echo "PACKAGE_VERSION must contain one to three numeric components (for example, 1.0.0)." >&2
    exit 2
fi
if [[ ! "$build_number" =~ ^[0-9]+([.][0-9]+){0,2}$ ]]; then
    echo "APP_BUILD_NUMBER must contain one to three numeric components." >&2
    exit 2
fi
if [[ ! "$minimum_macos_version" =~ ^[0-9]+[.][0-9]+([.][0-9]+)?$ ]]; then
    echo "MACOS_MIN_VERSION must be numeric (for example, 12.0)." >&2
    exit 2
fi
if [[ ! "$bundle_id" =~ ^[A-Za-z0-9.-]+$ ]]; then
    echo "MACOS_BUNDLE_ID contains characters that jpackage does not accept." >&2
    exit 2
fi

if [[ "$mode" == "app-store" ]]; then
    if [[ -z "${APPLE_SIGNING_KEY_USER_NAME:-}" ]]; then
        echo "APPLE_SIGNING_KEY_USER_NAME is required for app-store mode." >&2
        exit 2
    fi
    if [[ -z "${APPLE_PROVISIONING_PROFILE:-}" || ! -f "$APPLE_PROVISIONING_PROFILE" ]]; then
        echo "APPLE_PROVISIONING_PROFILE must point to a downloaded Mac App Store Connect profile." >&2
        exit 2
    fi
fi

require_command java
require_command javac
require_command jpackage
require_command codesign
maven_command="$(find_maven)"

echo "Building the application JAR and running tests..."
(cd "$project_root" && "$maven_command" clean package)

if [[ ! -f "$jar_path" ]]; then
    echo "Expected application JAR was not created: $jar_path" >&2
    exit 1
fi

rm -rf "$work_dir"
mkdir -p "$input_dir" "$icon_tool_classes" "$output_dir"
cp "$jar_path" "$input_dir/pdf-meta-viewer.jar"

echo "Generating macOS application artwork..."
javac -d "$icon_tool_classes" "$project_root/packaging/IconGenerator.java"
java -Djava.awt.headless=true -cp "$icon_tool_classes" IconGenerator icns "$icon_icns"

app_bundle="$output_dir/$app_name.app"
rm -rf "$app_bundle"

jpackage_args=(
    --type app-image
    --name "$app_name"
    --input "$input_dir"
    --main-jar pdf-meta-viewer.jar
    --main-class gui.MetaViewerForm
    --dest "$output_dir"
    --app-version "$app_version"
    --vendor "$vendor"
    --description "$description"
    --copyright "Copyright © $(date +%Y) $vendor"
    --icon "$icon_icns"
    --mac-package-identifier "$bundle_id"
    --mac-package-name "$app_name"
    --mac-package-signing-prefix "$bundle_id."
    --mac-app-category utilities
    --java-options -Dfile.encoding=UTF-8
)

if [[ "$mode" == "app" ]]; then
    echo "Creating local macOS application..."
    jpackage "${jpackage_args[@]}"
    patch_bundle_metadata "$app_bundle"
    codesign --force --sign - "$app_bundle"
    codesign --verify --deep --strict --verbose=2 "$app_bundle"
    echo
    echo "Created: $app_bundle"
    echo "Launch it with: open \"$app_bundle\""
    exit 0
fi

require_command productbuild
require_command pkgutil
require_command security

decoded_profile="$work_dir/provisioning-profile.plist"
security cms -D -i "$APPLE_PROVISIONING_PROFILE" -o "$decoded_profile"
profile_app_id="$(/usr/libexec/PlistBuddy -c 'Print :Entitlements:com.apple.application-identifier' "$decoded_profile" 2>/dev/null || true)"
profile_team_id="$(/usr/libexec/PlistBuddy -c 'Print :Entitlements:com.apple.developer.team-identifier' "$decoded_profile" 2>/dev/null || true)"
if [[ -z "$profile_team_id" ]]; then
    profile_team_id="$(/usr/libexec/PlistBuddy -c 'Print :TeamIdentifier:0' "$decoded_profile" 2>/dev/null || true)"
fi
if [[ -z "$profile_app_id" || -z "$profile_team_id" ]]; then
    echo "The provisioning profile does not contain the expected Mac App Store identity entitlements." >&2
    exit 2
fi
if [[ "$profile_app_id" != "$bundle_id" && "$profile_app_id" != *."$bundle_id" ]]; then
    echo "The provisioning profile App ID ($profile_app_id) does not match $bundle_id." >&2
    exit 2
fi

outer_entitlements="$work_dir/app-store-outer.entitlements"
cp "$entitlements" "$outer_entitlements"
/usr/libexec/PlistBuddy -c "Add :com.apple.application-identifier string $profile_app_id" "$outer_entitlements"
/usr/libexec/PlistBuddy -c "Add :com.apple.developer.team-identifier string $profile_team_id" "$outer_entitlements"

application_identity="${APPLE_APP_SIGNING_IDENTITY:-3rd Party Mac Developer Application: $APPLE_SIGNING_KEY_USER_NAME}"
installer_identity="${APPLE_INSTALLER_SIGNING_IDENTITY:-3rd Party Mac Developer Installer: $APPLE_SIGNING_KEY_USER_NAME}"

signing_args=(
    --mac-app-store
    --mac-sign
    --mac-signing-key-user-name "$APPLE_SIGNING_KEY_USER_NAME"
    --mac-entitlements "$entitlements"
)
codesign_keychain_args=()
productbuild_keychain_args=()
if [[ -n "${APPLE_SIGNING_KEYCHAIN:-}" ]]; then
    signing_args+=(--mac-signing-keychain "$APPLE_SIGNING_KEYCHAIN")
    codesign_keychain_args+=(--keychain "$APPLE_SIGNING_KEYCHAIN")
    productbuild_keychain_args+=(--keychain "$APPLE_SIGNING_KEYCHAIN")
fi

echo "Creating the sandboxed and signed application image..."
jpackage "${jpackage_args[@]}" "${signing_args[@]}"

patch_bundle_metadata "$app_bundle"
cp "$APPLE_PROVISIONING_PROFILE" "$app_bundle/Contents/embedded.provisionprofile"

# Files downloaded through a browser (like the provisioning profile) carry the
# com.apple.quarantine extended attribute. App Store Connect rejects any
# package containing quarantined files, so strip attributes before re-signing.
xattr -cr "$app_bundle"

# Adding the distribution profile and build number changes sealed resources, so
# re-sign the outer bundle. jpackage has already signed the nested Java runtime
# from the inside out with the same App Sandbox entitlements.
codesign --force --timestamp --sign "$application_identity" \
    --entitlements "$outer_entitlements" "${codesign_keychain_args[@]+"${codesign_keychain_args[@]}"}" "$app_bundle"

echo "Verifying the application signature and entitlements..."
codesign --verify --deep --strict --verbose=2 "$app_bundle"
codesign --display --entitlements - "$app_bundle" >/dev/null

package_path="$output_dir/PDF-Meta-Viewer-$app_version-mac-app-store.pkg"
rm -f "$package_path"

echo "Creating the signed Mac App Store installer package..."
productbuild --component "$app_bundle" /Applications \
    --sign "$installer_identity" "${productbuild_keychain_args[@]+"${productbuild_keychain_args[@]}"}" "$package_path"

pkgutil --check-signature "$package_path"

echo
echo "Created: $package_path"
echo "Validate/upload this .pkg with Transporter or xcrun altool."
