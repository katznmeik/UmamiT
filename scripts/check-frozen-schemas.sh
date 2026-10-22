#!/usr/bin/env bash
# Schema freeze guard (05 §9, D-054). From v0.1.0-rc.1 on an APK with
# database schema v1 can be on the PO's phone, and Room refuses to open a
# database whose schema changed without a version bump (the app would not
# start until its data is cleared). So every exported schema file that a
# release tag (v*) contains must stay byte-identical: a schema change needs a
# new version (2.json) and a tested migration.
#
# Usage: scripts/check-frozen-schemas.sh
#   Fetches the release tags from origin (shallow, so it also works in a
#   shallow CI checkout) unless SKIP_TAG_FETCH=1, then compares the working
#   tree with every tag matching TAG_PATTERN (default: v*).
#
# Exits non-zero and prints a GitHub error annotation for each changed or
# deleted frozen schema file.
set -euo pipefail

readonly SCHEMA_DIR="core/database/schemas"
readonly TAG_PATTERN="${TAG_PATTERN:-v*}"

cd "$(git rev-parse --show-toplevel)"

if [[ "${SKIP_TAG_FETCH:-0}" != "1" ]]; then
  git fetch --quiet --depth=1 --force origin "+refs/tags/${TAG_PATTERN}:refs/tags/${TAG_PATTERN}"
fi

failed=0
checked=0
while IFS= read -r tag; do
  [[ -z "$tag" ]] && continue
  while IFS= read -r file; do
    [[ -z "$file" ]] && continue
    checked=$((checked + 1))
    if [[ ! -f "$file" ]]; then
      echo "::error file=${file}::${file} is frozen by release ${tag} but was deleted"
      failed=1
    elif ! git diff --quiet "$tag" -- "$file"; then
      echo "::error file=${file}::${file} differs from its frozen copy in release ${tag}; bump the database version and add a tested migration instead (05 §9)"
      git diff --stat "$tag" -- "$file"
      failed=1
    fi
  done < <(git ls-tree -r --name-only "$tag" -- "$SCHEMA_DIR" | grep '\.json$' || true)
done < <(git tag --list "$TAG_PATTERN")

echo "Frozen schema files checked: ${checked}"
exit "$failed"
