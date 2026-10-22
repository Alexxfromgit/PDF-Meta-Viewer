#!/usr/bin/env bash
set -Eeuo pipefail
project_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$project_root"
version="${PACKAGE_VERSION:-$(python3 scripts/release.py package-version)}"
if [[ ! "$version" =~ ^[0-9]+[.][0-9]+[.][0-9]+$ ]]; then
    echo 'PACKAGE_VERSION must be MAJOR.MINOR.PATCH.' >&2
    exit 2
fi
if [[ "${PACKAGE_SKIP_BUILD:-0}" != "1" ]]; then
    ./mvnw --batch-mode --no-transfer-progress clean verify
fi
work_dir="$project_root/dist/.work/linux"
output_dir="$project_root/dist/linux"
rm -rf "$work_dir" "$output_dir/PDF Meta Viewer"
mkdir -p "$work_dir/input" "$output_dir"
cp target/pdf-meta-viewer.jar "$work_dir/input/"
jpackage --type app-image --name 'PDF Meta Viewer' \
    --input "$work_dir/input" --main-jar pdf-meta-viewer.jar --main-class gui.MetaViewerForm \
    --add-launcher "pdf-meta-viewer-cli=$project_root/packaging/cli-launcher.properties" \
    --app-version "$version" --vendor Alexxfromgit \
    --description 'Inspect, search, and export PDF metadata locally.' \
    --java-options -Dfile.encoding=UTF-8 --dest "$output_dir"
tar -C "$output_dir" -czf "$output_dir/PDF-Meta-Viewer-$version-linux-x64.tar.gz" 'PDF Meta Viewer'
