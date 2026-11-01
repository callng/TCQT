#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

read_pin() {
  local value
  value=$(sed -nE "s/^val $1 = \"?([0-9.]+)\"?[[:space:]]*$/\\1/p" build.gradle.kts)
  if [ -z "$value" ]; then
    echo "::error::Missing Android toolchain pin: $1" >&2
    return 1
  fi
  printf '%s' "$value"
}

compile_sdk=$(read_pin androidCompileSdkVersion)
compile_sdk_minor=$(read_pin androidCompileSdkMinorVersion)
cmake_version=$(read_pin androidCmakeVersion)
ndk_version=$(read_pin androidNdkVersion)
# The compileOnly QQ stubs use a separate, major-only SDK platform.
stub_sdk=$(sed -nE 's/^    compileSdk = ([0-9]+)[[:space:]]*$/\1/p' libs/qqinterface/build.gradle.kts)
test -n "$stub_sdk"

sdk_root="${ANDROID_HOME:?ANDROID_HOME is not set}"
sdkmanager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"

package_ready() {
  local package="$1" directory="$sdk_root/${1/;/\/}"
  test -f "$directory/source.properties" || return 1
  case "$package" in
    platforms\;*) test -f "$directory/android.jar" ;;
    cmake\;*) test -x "$directory/bin/cmake" && test -x "$directory/bin/ninja" ;;
    ndk\;*) test -f "$directory/build/cmake/android.toolchain.cmake" ;;
  esac
}

for package in "platforms;android-$compile_sdk.$compile_sdk_minor" \
  "platforms;android-$stub_sdk" "cmake;$cmake_version" "ndk;$ndk_version"; do
  if package_ready "$package"; then
    echo "Using installed Android SDK package: $package"
    continue
  fi
  for attempt in 1 2 3; do
    if "$sdkmanager" --sdk_root="$sdk_root" --install "$package" && package_ready "$package"; then
      break
    fi
    if [ "$attempt" -eq 3 ]; then
      echo "::error::Failed to install complete Android SDK package: $package" >&2
      exit 1
    fi
    sleep 5
  done
done

"$sdk_root/cmake/$cmake_version/bin/cmake" --version
"$sdk_root/cmake/$cmake_version/bin/ninja" --version
