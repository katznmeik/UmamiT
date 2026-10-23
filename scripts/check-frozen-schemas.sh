#!/usr/bin/env bash
# Schema freeze guard (05 §9, D-054, D-070). From v0.1.0-rc.1 on an APK with
# database schema v1 can be on the PO's phone, and Room refuses to open a
# database whose schema changed without a version bump (the app would not
# start until its data is cleared). So every exported schema file that a
# release tag (v*) contains is frozen: a schema change needs a new version
# (2.json) and a tested migration.
#
# What is compared is what Room compares when it opens the database: the
# schema's `database.version` and `database.identityHash` (the hash of every
# table, column, index, foreign key and view). A file that differs only in
# formatting (e.g. a Room upgrade that writes the JSON differently) keeps
# both, so it passes with a warning. A byte comparison would block every such
# Room upgrade, because CI also requires the regenerated file to be committed.
#
# Usage: scripts/check-frozen-schemas.sh
#   Fetches the release tags from origin (shallow, so it also works in a
#   shallow CI checkout) unless SKIP_TAG_FETCH=1, then compares the working
#   tree with every tag matching TAG_PATTERN (default: v*). Needs jq.
#
# Exits non-zero and prints a GitHub error annotation for each frozen schema
# file that was deleted, cannot be read, or changed its version or identity
# hash.
set -euo pipefail

readonly SCHEMA_DIR="core/database/schemas"
readonly TAG_PATTERN="${TAG_PATTERN:-v*}"

cd "$(git rev-parse --show-toplevel)"

if ! command -v jq >/dev/null 2>&1; then
  echo "::error::jq is required to compare the schema identity hashes"
  exit 1
fi

if [[ "${SKIP_TAG_FETCH:-0}" != "1" ]]; then
  git fetch --quiet --depth=1 --force origin "+refs/tags/${TAG_PATTERN}:refs/tags/${TAG_PATTERN}"
fi

# Prints "<version> <identityHash>" of the schema JSON on stdin; fails on
# anything that is not a Room schema file.
schema_key() {
  jq -er '.database | "\(.version | numbers) \(.identityHash | strings)"'
}

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
      continue
    fi
    if git diff --quiet "$tag" -- "$file"; then
      continue
    fi
    if ! frozen="$(git show "${tag}:${file}" | schema_key)"; then
      echo "::error file=${file}::${file} in release ${tag} has no database version and identity hash"
      failed=1
    elif ! current="$(schema_key < "$file")"; then
      echo "::error file=${file}::${file} has no database version and identity hash"
      failed=1
    elif [[ "$frozen" != "$current" ]]; then
      echo "::error file=${file}::${file} changed its schema (version and identity hash now '${current}', frozen by release ${tag} as '${frozen}'); restore it and bump the database version with a tested migration instead (05 §9)"
      git diff --stat "$tag" -- "$file"
      failed=1
    else
      echo "::warning file=${file}::${file} differs from release ${tag} in formatting only (same version and identity hash: ${current}); allowed"
    fi
  done < <(git ls-tree -r --name-only "$tag" -- "$SCHEMA_DIR" | grep '\.json$' || true)
done < <(git tag --list "$TAG_PATTERN")

echo "Frozen schema files checked: ${checked}"
exit "$failed"
