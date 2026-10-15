#!/usr/bin/env bash
# Run from the repository root after building the tagged version.
set -euo pipefail
: "${TAG:?A release tag is required}"
[[ "$TAG" == v* && "$TAG" != v ]]
version=${TAG#v}
mod_id=$(awk -F= '$1 == "mod_id" {gsub(/[[:space:]]/, "", $2); print $2}' gradle.properties)
test -n "$mod_id"
# Select only the distributable jar: never sources, development or test artifacts.
jar="build/libs/${mod_id}-${version}.jar"
test -f "$jar"
notes=$(mktemp)
trap 'rm -f "$notes"' EXIT
awk -v version="$version" '
  /^## / {
    if (found) exit
    header = "## [" version "]"
    if ($0 == header || index($0, header " - ") == 1) { found = 1; next }
  }
  found { print }
  END { if (!found) exit 1 }
' CHANGELOG.md > "$notes"
grep -q '[^[:space:]]' "$notes"
gh release create "$TAG" "$jar" --verify-tag --title "$TAG" --notes-file "$notes"
