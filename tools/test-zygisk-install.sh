#!/usr/bin/env bash
# Run with Bash, JDK 21 (jar), unzip and standard POSIX utilities.
# Exercises customize.sh with real ZIP extraction in an isolated /data sandbox.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
SANDBOX=$(mktemp -d)
trap 'rm -rf "$SANDBOX"' EXIT
TEMPLATE="$ROOT/app/src/main/zygisk-template"
mkdir -p "$SANDBOX/package/zygisk"
cp "$TEMPLATE/module.prop" "$TEMPLATE/uninstall.sh" "$TEMPLATE/hotinstall.sh" "$SANDBOX/package/"
cp -R "$TEMPLATE/webroot" "$SANDBOX/package/"
printf 'new injector' > "$SANDBOX/package/zygisk/arm64-v8a.so"
(cd "$SANDBOX/package" && jar --create --file ../module.zip --no-manifest .)
# Rewrite only the Android data root; run the actual installer logic unchanged.
sed "s|/data/adb|$SANDBOX/data/adb|g" "$TEMPLATE/customize.sh" | tr -d '\r' > "$SANDBOX/customize.sh"

run_case() (
  name=$1 live=$2 manager=$3 expected=$4 capability=${5-true}
  case_dir="$SANDBOX/data/adb"
  rm -rf "$case_dir"
  mkdir -p "$case_dir/modules/zygisk_tcqt/zygisk" "$case_dir/tcqt"
  live_dir="$case_dir/modules/zygisk_tcqt"
  case "$live" in
    same|disabled|removed)
      cp "$SANDBOX/package/zygisk/arm64-v8a.so" "$live_dir/zygisk/arm64-v8a.so" ;;
    different|staged) printf 'old injector' > "$live_dir/zygisk/arm64-v8a.so" ;;
    missing) rm -f "$live_dir/zygisk/arm64-v8a.so" ;;
  esac
  [ "$live" != disabled ] || touch "$live_dir/disable"
  [ "$live" != removed ] || touch "$live_dir/remove"
  # A pending install's legacy fingerprint must never authorize hot install.
  sha1sum "$SANDBOX/package/zygisk/arm64-v8a.so" | cut -d ' ' -f1 > "$case_dir/tcqt/so.sha1"
  MODPATH="$case_dir/modules_update/zygisk_tcqt"
  mkdir -p "$MODPATH"
  if [ "$live" = staged ]; then
    mkdir -p "$MODPATH/zygisk"
    cp "$SANDBOX/package/zygisk/arm64-v8a.so" "$MODPATH/zygisk/arm64-v8a.so"
  fi
  BOOTMODE=true ARCH=arm64 KSU=$manager ZIPFILE="$SANDBOX/module.zip"
  [ "$manager" != absent ] || unset KSU
  KSU_HAS_HOT_INSTALL=$capability
  [ "$capability" != absent ] || unset KSU_HAS_HOT_INSTALL
  # Confirm installer variables cannot accidentally leak from an earlier run.
  export MODULE_HOT_INSTALL_REQUEST=true MODULE_HOT_RUN_SCRIPT=stale.sh
  ui_print() { echo "$1"; }
  abort() { echo "$1" >&2; exit 1; }
  set_perm() { chmod "$4" "$1"; }
  set_perm_recursive() { :; }
  chcon() { :; }
  trap 'if [ "$?" -ne 0 ]; then cat "$SANDBOX/output.txt" >&2; fi' EXIT
  # Match the Android installer's shell options (missing old files are normal).
  set +e
  set +o pipefail
  . "$SANDBOX/customize.sh" > "$SANDBOX/output.txt" 2>&1
  install_status=$?
  set -eo pipefail
  [ "$install_status" -eq 0 ]
  [ "$MODULE_HOT_INSTALL_REQUEST" = "$expected" ]
  if [ "$manager" = true ] && [ "$capability" != true ] && [ "$live" = same ]; then
    grep -q 'KSU_HAS_HOT_INSTALL' "$SANDBOX/output.txt"
    if grep -q '已请求 KernelSU 热安装' "$SANDBOX/output.txt"; then exit 1; fi
  fi
  cmp "$ZIPFILE" "$case_dir/tcqt/main.apk"
  [ "$(cat "$case_dir/tcqt/main.apk.sha256")" = "$(sha256sum "$ZIPFILE" | cut -d ' ' -f1)" ]
  [ -x "$MODPATH/hotinstall.sh" ]
  if [ "$expected" = true ]; then
    [ "$MODULE_HOT_RUN_SCRIPT" = hotinstall.sh ]
    # Simulate KernelSU replacing the live directory, then running the callback.
    mv "$live_dir" "$case_dir/old-module"
    mv "$MODPATH" "$live_dir"
    (cd "$live_dir" && sh "./$MODULE_HOT_RUN_SCRIPT") > "$SANDBOX/hot-output.txt"
    [ -s "$SANDBOX/hot-output.txt" ]
  else
    [ -z "${MODULE_HOT_RUN_SCRIPT-}" ]
  fi
  echo "PASS: $name"
)

run_case 'KernelSU payload-only update' same true true
run_case 'first install' missing true false
run_case 'injector changed' different true false
run_case 'repeated install before reboot' staged true false
run_case 'disabled module' disabled true false
run_case 'pending removal' removed true false
run_case 'Magisk/APatch ignores KernelSU hot install' same false false
run_case 'unset KernelSU environment' same absent false
run_case 'old KernelSU installer without capability' same true false absent
run_case 'KernelSU installer explicitly disables capability' same true false false
