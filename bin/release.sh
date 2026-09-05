#!/usr/bin/env bash
# One-command release: bump, verify, commit, tag, push. CI does the rest.
#
#   bin/release.sh 1.10 "block card polish"
#   bin/release.sh 1.10 "block card polish" --dry-run
#
# Pushing a v* tag is what publishes the GitHub Release with the APK attached, so this script
# ends at the push and CI takes over. Everything before the push is reversible; everything the
# script refuses to do is something that would be awkward to undo.

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

version="${1:-}"
headline="${2:-}"
dry_run=false
for arg in "$@"; do [ "$arg" = "--dry-run" ] && dry_run=true; done

die() { printf '\n✗ %s\n' "$1" >&2; exit 1; }
step() { printf '\n▸ %s\n' "$1"; }

[ -n "$version" ] || die "Usage: bin/release.sh <version> [headline] [--dry-run]"
[[ "$version" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]] || die "Version must look like 1.10 or 1.10.1 (got '$version')"
[ -n "$headline" ] || die "Give a one-line headline — it becomes the commit subject and the release title"

tag="v$version"

step "Preflight"
branch="$(git rev-parse --abbrev-ref HEAD)"
[ "$branch" = "main" ] || die "On '$branch'; releases go from main"
[ -z "$(git status --porcelain)" ] || die "Working tree is dirty — commit or stash first"
git rev-parse -q --verify "refs/tags/$tag" >/dev/null && die "Tag $tag already exists locally"
git fetch -q origin
git merge-base --is-ancestor origin/main HEAD || die "origin/main has commits you don't have — pull first"
git ls-remote --exit-code --tags origin "$tag" >/dev/null 2>&1 && die "Tag $tag already exists on origin"
echo "  main, clean, up to date with origin"

step "Bumping to $version"
gradle_file="app/build.gradle.kts"
old_code="$(grep -oE 'versionCode = [0-9]+' "$gradle_file" | grep -oE '[0-9]+')"
old_name="$(grep -oE 'versionName = "[^"]+"' "$gradle_file" | cut -d'"' -f2)"
new_code=$((old_code + 1))
[ "$old_name" != "$version" ] || die "versionName is already $version"
sed -i.bak -E "s/versionCode = $old_code/versionCode = $new_code/; s/versionName = \"$old_name\"/versionName = \"$version\"/" "$gradle_file"
rm -f "$gradle_file.bak"
echo "  versionCode $old_code → $new_code, versionName $old_name → $version"

# From here on a failure should not leave a half-bumped file behind.
restore() { git checkout -- "$gradle_file" 2>/dev/null || true; }
trap 'restore' ERR

step "Tests"
./gradlew testReleaseUnitTest --no-daemon -q
echo "  passed"

step "Lint"
./gradlew lintRelease --no-daemon -q
errs="$(grep -c 'severity="Error"\|severity="Fatal"' app/build/reports/lint-results-release.xml || true)"
[ "$errs" = "0" ] || die "$errs lint error(s) — CLAUDE.md holds this project to zero"
echo "  0 errors"

step "Build"
./gradlew assembleRelease --no-daemon -q
apk="app/build/outputs/apk/release/app-release.apk"
[ -f "$apk" ] || die "No APK produced"
# The keystore is gitignored and lives in CI secrets; a local build without it is unsigned, which
# would refuse to install over an existing copy. Worth catching here rather than on the phone.
if [ -f keystore/btcalert.jks ]; then
  if command -v apksigner >/dev/null 2>&1; then
    apksigner verify "$apk" >/dev/null 2>&1 && echo "  signed" || die "APK is not signed"
  else
    echo "  built (apksigner not on PATH, signature unverified)"
  fi
else
  echo "  ⚠ built unsigned — no keystore/btcalert.jks locally. CI will sign the released APK."
fi

if $dry_run; then
  step "Dry run — rolling the version bump back"
  restore
  echo "  nothing committed, nothing pushed"
  exit 0
fi

trap - ERR

step "Commit and tag"
git commit -qam "$tag: $headline"
git tag "$tag"
echo "  $(git log --oneline -1)"

step "Push"
git push -q origin main "$tag"
echo "  pushed main and $tag"

cat <<EOF

✓ $tag is on its way.
  Actions:  https://github.com/ellokojavi/btc-alert/actions
  Release:  https://github.com/ellokojavi/btc-alert/releases/tag/$tag
  The tag build publishes the APK; give it about five minutes.
EOF
