#!/usr/bin/env bash
# Works out the version for this build from the latest release tag.
#
#   patch bump by default        v3.1.0 -> 3.1.1
#   "[minor]" in commit message  v3.1.0 -> 3.2.0
#   "[major]" in commit message  v3.1.0 -> 4.0.0
#   "[skip release]"             build only, don't publish
#
# BUMP (patch|minor|major|none) overrides the commit message, e.g. from a manual run.
# If the default version in app/build.gradle.kts is higher, it wins (manual jumps).
# versionCode = major * 10000 + minor * 100 + patch, so it always increases.
#
# Prints name=, code=, tag= and release= lines for $GITHUB_OUTPUT.
set -euo pipefail

message="${COMMIT_MESSAGE:-$(git log -1 --format=%B)}"
bump="${BUMP:-}"
if [ -z "$bump" ]; then
  case "$message" in
    *"[major]"*) bump=major ;;
    *"[minor]"*) bump=minor ;;
    *) bump=patch ;;
  esac
fi

default=$(grep -oP 'DEFAULT_VERSION_NAME = "\K[0-9]+\.[0-9]+\.[0-9]+' app/build.gradle.kts)
latest=$(git tag --list 'v[0-9]*' --sort=-v:refname | head -n1 | sed 's/^v//')

if [ -z "$latest" ]; then
  next="$default"
else
  IFS=. read -r major minor patch <<< "${latest%%-*}"
  case "$bump" in
    major) next="$((major + 1)).0.0" ;;
    minor) next="$major.$((minor + 1)).0" ;;
    patch) next="$major.$minor.$((patch + 1))" ;;
    none)  next="$major.$minor.$patch" ;;
    *) echo "Unknown bump: $bump" >&2; exit 1 ;;
  esac
  # A higher default in the build file is an intentional jump
  if [ "$(printf '%s\n%s\n' "$next" "$default" | sort -V | tail -n1)" = "$default" ] && [ "$default" != "$next" ]; then
    next="$default"
  fi
fi

IFS=. read -r major minor patch <<< "$next"
if [ "$minor" -gt 99 ] || [ "$patch" -gt 99 ]; then
  echo "Minor and patch must stay below 100 to keep versionCode increasing" >&2
  exit 1
fi
code=$((major * 10000 + minor * 100 + patch))

release=true
case "$message" in *"[skip release]"*|*"[no release]"*) release=false ;; esac
[ "$bump" = "none" ] && release=false

echo "name=$next"
echo "code=$code"
echo "tag=v$next"
echo "release=$release"
