#!/usr/bin/env bash
# Loads the FTA402 certificate signatures into this repo's GitHub secrets, from which every
# TEST/PROD deploy puts them in OpenShift (reusable-deploy.yml → the backend template's
# nr-fta-certificate-signatures-<zone> Secret). The signatures never enter git.
#
# Usage: .github/scripts/set-certificate-signatures.sh <folder>
#   <folder> holds signatories.properties and the signature images it names (see the backend
#   README). Needs gh, logged in with admin rights on the repo, and zip.
#
# The folder is zipped, base64-encoded and split into CERTIFICATE_SIGNATURES_1..4 (GitHub caps
# a secret at 48 KB); chunks this run doesn't need are deleted, so an old tail can't be joined
# onto a new head. Run it again to change the signatures; the next deploy picks them up.
set -euo pipefail

folder="${1:?usage: $0 <folder with signatories.properties and images>}"
[ -f "$folder/signatories.properties" ] || { echo "No signatories.properties in $folder" >&2; exit 1; }

chunk=45000   # under GitHub's 48 KB secret cap
max=120000    # under Linux's 128 KB cap on one argument, which the deploy passes it as

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
( cd "$folder" && zip -q -X -j "$tmp/signatures.zip" signatories.properties \
    $(sed -n 's/^[^#]*\.image[[:space:]]*=[[:space:]]*//p' signatories.properties) )
base64 < "$tmp/signatures.zip" | tr -d '\n' > "$tmp/signatures.b64"

size=$(wc -c < "$tmp/signatures.b64" | tr -d ' ')
if [ "$size" -gt "$max" ]; then
  echo "Signatures are $size characters encoded; the deploy takes at most $max." >&2
  echo "Shrink the images (they print at 156x45 points) and try again." >&2
  exit 1
fi

split -b "$chunk" "$tmp/signatures.b64" "$tmp/part-"
n=0
for part in "$tmp"/part-*; do
  n=$((n + 1))
  gh secret set "CERTIFICATE_SIGNATURES_$n" < "$part"
done
for i in $(seq $((n + 1)) 4); do
  gh secret delete "CERTIFICATE_SIGNATURES_$i" 2>/dev/null || true
done

echo "Set CERTIFICATE_SIGNATURES_1..$n ($size characters, $(unzip -Z1 "$tmp/signatures.zip" | wc -l | tr -d ' ') files)."
echo "They reach OpenShift on the next TEST/PROD deploy."
