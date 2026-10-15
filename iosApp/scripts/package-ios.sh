#!/usr/bin/env bash
# Zhihu-Hyperion - Free & Ad-Free Zhihu client for all platforms.
# Copyright (C) 2026, eltavine <me@eltavine.com>
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation (version 3 only).
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

# Builds a Release iOS app without an Apple account.
#
#   device    <out.ipa>  For iPhone and iPad, with code signing turned off. iOS installs it only after it is
#                        signed, for example by AltStore, SideStore or Sideloadly, or with your own certificate.
#   simulator <out.zip>  For simulators on Apple silicon Macs, signed ad hoc. Unzip it and run
#                        `xcrun simctl install booted Zhihu-Hyperion.app`.
set -euo pipefail

usage="usage: $0 device <out.ipa> | simulator <out.zip>"
kind="${1:-}"
output="${2:?$usage}"
case "$kind" in
  device)
    sdk=iphoneos
    destination='generic/platform=iOS'
    signing=(CODE_SIGNING_ALLOWED=NO)
    ;;
  simulator)
    sdk=iphonesimulator
    destination='generic/platform=iOS Simulator'
    signing=()
    ;;
  *)
    echo "$usage" >&2
    exit 2
    ;;
esac

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
mkdir -p "$(dirname "$output")"
output="$(cd "$(dirname "$output")" && pwd)/$(basename "$output")"
derived="$root/iosApp/build/derived-data"

build_log="$(mktemp)"
trap 'rm -f "$build_log"' EXIT
for attempt in 1 2 3; do
  # macOS ships bash 3.2, where expanding an empty array under `set -u` fails without the `+` guard.
  if xcodebuild -project "$root/iosApp/ZhihuHyperion.xcodeproj" -scheme ZhihuHyperion -configuration Release \
    -sdk "$sdk" -destination "$destination" -derivedDataPath "$derived" ${signing[@]+"${signing[@]}"} build 2>&1 | tee "$build_log"; then
    break
  else
    pipeline_status=("${PIPESTATUS[@]}")
    status="${pipeline_status[0]}"
    if [[ "$status" == 0 ]]; then exit "${pipeline_status[1]}"; fi
  fi
  # GitHub macOS runner 曾在 Gradle 下载依赖前发生 Plugin Portal DNS 失败；只重试此下载错误，保留其他构建错误。
  # 失败记录：https://github.com/eltavine/Zhihu-Hyperion/actions/runs/38501572364/job/115649533640
  if [[ "$attempt" == 3 ]] ||
    ! grep -Eq "Could not (HEAD|GET).*https://plugins\\.gradle\\.org/" "$build_log" ||
    ! grep -Fq 'plugins.gradle.org: nodename nor servname provided, or not known' "$build_log"; then
    exit "$status"
  fi
  echo "Gradle Plugin Portal DNS lookup failed; retrying iOS build (${attempt}/3)." >&2
  sleep "$((attempt * 10))"
done
rm -f "$build_log"
trap - EXIT

app="$derived/Build/Products/Release-$sdk/Zhihu-Hyperion.app"
test "$(/usr/bin/lipo -archs "$app/Zhihu-Hyperion")" = arm64

rm -f "$output"
if [[ "$kind" == device ]]; then
  staging="$(mktemp -d)"
  trap 'rm -rf "$staging"' EXIT
  mkdir "$staging/Payload"
  cp -R "$app" "$staging/Payload/"
  (cd "$staging" && /usr/bin/zip -qry "$output" Payload)
else
  /usr/bin/codesign --verify --deep --strict "$app"
  /usr/bin/ditto -c -k --sequesterRsrc --keepParent "$app" "$output"
fi
echo "Wrote $output"
