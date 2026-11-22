#!/usr/bin/env bash
# Assert a PUBLISHED GitHub release is Obtainium-compatible.
# Usage: scripts/check-release.sh <tag> [owner/repo]     e.g. scripts/check-release.sh v1.0.0-rc1
# Needs: gh (authenticated), apkanalyzer + apksigner (Android SDK cmdline/build-tools) on PATH.
# Optional: EXPECTED_CERT_SHA256=<hex> pins the signing certificate explicitly.
# The pure rules are unit-tested in buildSrc ReleaseChecks.kt (ReleaseChecksTest); keep them in sync.
set -euo pipefail

TAG="${1:?usage: check-release.sh <tag> [owner/repo]}"
REPO="${2:-EzraGubbay/quire-android}"
APP_ID="com.ezragubbay.quire"
fail=0
bad() { echo "FAIL: $*" >&2; fail=1; }
ok() { echo "ok:   $*"; }

for tool in gh apkanalyzer apksigner; do
  command -v "$tool" >/dev/null || { echo "missing required tool: $tool" >&2; exit 2; }
done

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Rule 1: exactly one .apk asset.
apk_count=0
while IFS= read -r a; do
  case "$(printf '%s' "$a" | tr '[:upper:]' '[:lower:]')" in *.apk) apk_count=$((apk_count + 1)) ;; esac
done < <(gh release view "$TAG" --repo "$REPO" --json assets --jq '.assets[].name')
if [ "$apk_count" -eq 1 ]; then ok "exactly one .apk asset"; else bad "expected exactly one .apk asset, found $apk_count"; fi
[ "$apk_count" -ge 1 ] || exit 1

apk_info() { # <tag> <dir> -> sets A_ID A_NAME A_CODE A_CERT
  mkdir -p "$2"
  gh release download "$1" --repo "$REPO" --pattern '*.apk' --dir "$2" --clobber
  local apk
  apk="$(find "$2" -name '*.apk' | head -n1)"
  A_ID="$(apkanalyzer manifest application-id "$apk")"
  A_NAME="$(apkanalyzer manifest version-name "$apk")"
  A_CODE="$(apkanalyzer manifest version-code "$apk")"
  A_CERT="$(apksigner verify --print-certs "$apk" | sed -n 's/.*certificate SHA-256 digest: //p' | head -n1)"
}

apk_info "$TAG" "$work/current"
cur_id="$A_ID" cur_name="$A_NAME" cur_code="$A_CODE" cur_cert="$A_CERT"
echo "applicationId=$cur_id versionName=$cur_name versionCode=$cur_code cert-sha256=$cur_cert"

# Rule 2: stable applicationId.
if [ "$cur_id" = "$APP_ID" ]; then ok "applicationId is $APP_ID"; else bad "applicationId '$cur_id' != $APP_ID"; fi

# Rule 3: versionName == tag without leading v.
case "$TAG" in v*) ;; *) bad "tag must start with 'v': $TAG" ;; esac
if [ "$cur_name" = "${TAG#v}" ]; then ok "versionName matches tag"; else bad "versionName '$cur_name' != '${TAG#v}'"; fi

# Rule 4: stable signing certificate (explicit pin, and equal to every earlier release).
if [ -n "${EXPECTED_CERT_SHA256:-}" ]; then
  expected="$(printf '%s' "$EXPECTED_CERT_SHA256" | tr -d ':' | tr '[:upper:]' '[:lower:]')"
  if [ "$cur_cert" = "$expected" ]; then ok "certificate matches EXPECTED_CERT_SHA256"; else bad "certificate $cur_cert != expected $expected"; fi
fi

# Rule 5: strictly increasing versionCode vs. all earlier releases.
max_prev=0
while IFS= read -r prev; do
  [ -n "$prev" ] && [ "$prev" != "$TAG" ] || continue
  apk_info "$prev" "$work/prev-$prev" || { echo "skip $prev (no apk)"; continue; }
  [ "$A_CERT" = "$cur_cert" ] || bad "certificate differs from $prev ($A_CERT)"
  [ "$A_ID" = "$cur_id" ] || bad "applicationId differs from $prev ($A_ID)"
  [ "$A_CODE" -le "$max_prev" ] || max_prev="$A_CODE"
done < <(gh release list --repo "$REPO" --limit 100 --json tagName --jq '.[].tagName')
if [ "$cur_code" -gt "$max_prev" ]; then ok "versionCode $cur_code > previous max $max_prev"; else bad "versionCode $cur_code not > previous max $max_prev"; fi

[ "$fail" -eq 0 ] && echo "PASS: $TAG is Obtainium-compatible"
exit "$fail"
