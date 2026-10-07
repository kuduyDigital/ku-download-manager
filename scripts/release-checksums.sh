#!/usr/bin/env bash
# Writes SHA256SUMS.txt for every file of a GitHub release and attaches it.
#
# The hashes are GitHub's own SHA-256 digests of the uploaded files, so
# nothing is downloaded again. Run after each workflow that adds files (the
# desktop builds and the Android APKs finish separately): each run lists
# everything present at that moment, so the last one covers all of them.
#
# usage: scripts/release-checksums.sh <tag> <owner/repo>   (needs GH_TOKEN)
set -euo pipefail
tag="$1"
repo="$2"

# Freshly uploaded files can take a moment to get their digest.
for attempt in 1 2 3 4 5 6; do
  gh api "repos/$repo/releases/tags/$tag" --jq '.assets[] | select(.name != "SHA256SUMS.txt") | "\(.digest // "")  \(.name)"' > sums.raw
  if ! grep -q '^  ' sums.raw; then break; fi
  sleep 10
done
if grep -q '^  ' sums.raw; then
  echo "::error::Some release files have no SHA-256 digest yet"
  exit 1
fi
sed 's/^sha256://' sums.raw | sort -k2 > SHA256SUMS.txt
rm -f sums.raw
echo "$(wc -l < SHA256SUMS.txt) files in SHA256SUMS.txt"
gh release upload "$tag" SHA256SUMS.txt --clobber --repo "$repo"
