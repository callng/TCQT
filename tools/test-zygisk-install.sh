#!/usr/bin/env bash
# Run on Linux/WSL with Bash, zip, unzip and standard POSIX utilities.
# Real package extraction and boot activation, isolated from Android /data.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SANDBOX=$(mktemp -d)
trap 'rm -rf "$SANDBOX"' EXIT
mkdir -p "$SANDBOX/package/zygisk" "$SANDBOX/bin"
cp -R "$ROOT/app/src/main/zygisk-template/." "$SANDBOX/package/"
printf 'standalone diagnostic dex fixture' > "$SANDBOX/package/logshare.dex"
sha256sum "$SANDBOX/package/logshare.dex" | cut -d ' ' -f1 > "$SANDBOX/package/logshare.dex.sha256"
for script in "$SANDBOX/package/"*.sh; do
  sed -e "s|/data/adb|$SANDBOX/data/adb|g" \
      -e "s|/proc/sys/kernel/random/boot_id|$SANDBOX/boot_id|g" \
      "$script" | tr -d '\r' > "$script.tmp"
  mv "$script.tmp" "$script"
done
NEW_ID="TCQT_INJECTOR_ID_V1=$(printf 'new implementation' | sha256sum | cut -d ' ' -f1)"
OLD_ID="TCQT_INJECTOR_ID_V1=$(printf 'old implementation' | sha256sum | cut -d ' ' -f1)"
printf '\177ELF\000%s\000new injector' "$NEW_ID" > "$SANDBOX/package/zygisk/arm64-v8a.so"
(cd "$SANDBOX/package" && zip -qr "$SANDBOX/module.zip" .)
printf '#!/bin/sh\nexit "${TCQT_TEST_CHCON_FAIL:-0}"\n' > "$SANDBOX/bin/chcon"
chmod +x "$SANDBOX/bin/chcon"
export PATH="$SANDBOX/bin:$PATH"
hash() { sha256sum "$1" | cut -d ' ' -f1; }
NEW_HASH=$(hash "$SANDBOX/package/zygisk/arm64-v8a.so")
APK_HASH_EXPECTED=$(hash "$SANDBOX/module.zip")

setup_case() {
  rm -rf "$SANDBOX/data"
  case_dir="$SANDBOX/data/adb"
  live_dir="$case_dir/modules/zygisk_tcqt"
  mkdir -p "$live_dir/zygisk" "$case_dir/tcqt"
  printf 'boot-one' > "$SANDBOX/boot_id"
  printf 'old payload' > "$case_dir/tcqt/main.apk"
  hash "$case_dir/tcqt/main.apk" > "$case_dir/tcqt/main.apk.sha256"
  touch "$case_dir/tcqt/compat.enable"
  cp "$SANDBOX/package/zygisk/arm64-v8a.so" "$live_dir/zygisk/arm64-v8a.so"
  printf 'boot-one %s' "$NEW_ID" > "$case_dir/tcqt/boot-injector"
  MODPATH="$case_dir/modules_update/zygisk_tcqt"
  mkdir -p "$MODPATH"
  BOOTMODE=true ARCH=arm64 KSU=true KSU_HAS_HOT_INSTALL=true ZIPFILE="$SANDBOX/module.zip"
  TCQT_TEST_API=37
  getprop() { [ "$1" = ro.build.version.sdk ] && printf '%s' "$TCQT_TEST_API"; }
  export MODULE_HOT_INSTALL_REQUEST=true MODULE_HOT_RUN_SCRIPT=stale.sh
  ui_print() { echo "$1"; }
  abort() { echo "$1" >&2; exit 1; }
  set_perm() { chmod "$4" "$1"; }
  set_perm_recursive() { :; }
}

install_package() {
  # Android installers do not enable errexit/nounset/pipefail.
  set +e +u
  set +o pipefail
  . "$SANDBOX/package/customize.sh" > "$SANDBOX/install-output" 2>&1
  result=$?
  set -euo pipefail
  [ "$result" -eq 0 ] || { cat "$SANDBOX/install-output"; return 1; }
}

assert_deferred() {
  [ "$MODULE_HOT_INSTALL_REQUEST" = false ]
  [ -z "${MODULE_HOT_RUN_SCRIPT-}" ]
  [ "$(cat "$case_dir/tcqt/main.apk")" = 'old payload' ]
  [ "$(cat "$case_dir/tcqt/main.apk.sha256")" = "$(hash "$case_dir/tcqt/main.apk")" ]
  [ -f "$case_dir/tcqt/compat.enable" ]
  cmp "$ZIPFILE" "$case_dir/tcqt/pending/$APK_HASH_EXPECTED.apk"
}

assert_published() {
  cmp "$ZIPFILE" "$case_dir/tcqt/main.apk"
  [ "$(cat "$case_dir/tcqt/main.apk.sha256")" = "$APK_HASH_EXPECTED" ]
  [ ! -f "$case_dir/tcqt/compat.enable" ]
  [ ! -f "$case_dir/tcqt/pending/$APK_HASH_EXPECTED.apk" ]
}

apply_at_boot() {
  mv "$live_dir" "$case_dir/old-module"
  mv "$MODPATH" "$live_dir"
  printf 'boot-two' > "$SANDBOX/boot_id"
  sh "$live_dir/post-fs-data.sh"
  assert_published
  [ "$(cat "$case_dir/tcqt/boot-injector")" = "boot-two $NEW_ID" ]
  grep -q TCQT_ACTIVATION_OK "$case_dir/tcqt/activation.log"
  cmp "$SANDBOX/package/logshare.dex" "$live_dir/logshare.dex"
}

for api in '' bad 27 32 38; do (
  setup_case
  TCQT_TEST_API="$api"
  if (install_package); then echo "FAIL: accepted unsupported API $api"; exit 1; fi
  [ ! -e "$MODPATH/zygisk/arm64-v8a.so" ]
  [ ! -d "$case_dir/tcqt/pending" ]
  [ "$(cat "$case_dir/tcqt/main.apk")" = 'old payload' ]
  echo "PASS: reject unsupported API '$api' before extraction/publication"
); done

for api in 33 34 35 36 37; do (
  setup_case
  TCQT_TEST_API="$api"
  install_package
  assert_published
  echo "PASS: install on supported API $api"
); done

for mode in changed missing-record stale-boot replaced-inode legacy-record missing-id duplicate-id disabled removed pending-update; do (
  setup_case
  case "$mode" in
    changed) printf '\177ELF\000%s\000old injector' "$OLD_ID" > "$live_dir/zygisk/arm64-v8a.so" ;;
    missing-record) rm "$case_dir/tcqt/boot-injector" ;;
    stale-boot) printf 'previous-boot %s' "$NEW_ID" > "$case_dir/tcqt/boot-injector" ;;
    replaced-inode) printf 'boot-one %s' "$OLD_ID" > "$case_dir/tcqt/boot-injector" ;;
    legacy-record) printf 'boot-one %s' "$NEW_HASH" > "$case_dir/tcqt/boot-injector" ;;
    missing-id) printf 'legacy binary' > "$live_dir/zygisk/arm64-v8a.so" ;;
    duplicate-id) printf '\000%s\000' "$NEW_ID" >> "$live_dir/zygisk/arm64-v8a.so" ;;
    disabled) touch "$live_dir/disable" ;;
    removed) touch "$live_dir/remove" ;;
    pending-update) touch "$live_dir/update" ;;
  esac
  install_package
  assert_deferred
  # Reinstalling before reboot must still leave the old payload intact.
  install_package
  assert_deferred
  apply_at_boot
  echo "PASS: $mode / repeated install / boot activation"
); done

(
  setup_case
  # Different ELF bytes with the same implementation ID must allow a hot
  # update; the old mapped library still has everything the new Java needs.
  printf 'different linker metadata' >> "$live_dir/zygisk/arm64-v8a.so"
  [ "$(hash "$live_dir/zygisk/arm64-v8a.so")" != "$NEW_HASH" ]
  install_package
  [ "$MODULE_HOT_INSTALL_REQUEST" = true ]
  assert_published
  apply_at_boot
  echo 'PASS: changed ELF hash with unchanged implementation allows hot update'
)

for mode in ksu magisk old-ksu; do (
  setup_case
  case "$mode" in magisk) unset KSU ;; old-ksu) unset KSU_HAS_HOT_INSTALL ;; esac
  install_package
  assert_published
  if [ "$mode" = ksu ]; then
    [ "$MODULE_HOT_INSTALL_REQUEST" = true ]
    [ "$MODULE_HOT_RUN_SCRIPT" = hotinstall.sh ]
  else
    [ "$MODULE_HOT_INSTALL_REQUEST" = false ]
  fi
  apply_at_boot
  echo "PASS: compatible payload update ($mode)"
); done

(
  setup_case
  rm -rf "$live_dir" "$case_dir/tcqt"
  install_package
  [ "$MODULE_HOT_INSTALL_REQUEST" = false ]
  [ ! -e "$case_dir/tcqt/main.apk" ]
  mkdir -p "$live_dir"
  apply_at_boot
  echo 'PASS: first install waits for boot'
)

for mode in corrupt wrong-injector other-install label-failure; do (
  setup_case
  rm "$case_dir/tcqt/boot-injector"
  install_package
  assert_deferred
  mv "$live_dir" "$case_dir/old-module"
  mv "$MODPATH" "$live_dir"
  case "$mode" in
    corrupt) printf 'corrupt' > "$case_dir/tcqt/pending/$APK_HASH_EXPECTED.apk" ;;
    wrong-injector) printf 'other injector' > "$live_dir/zygisk/arm64-v8a.so" ;;
    other-install) printf '%064d' 0 > "$live_dir/payload.sha256" ;;
    label-failure) export TCQT_TEST_CHCON_FAIL=1 ;;
  esac
  if sh "$live_dir/post-fs-data.sh"; then echo "Unexpected activation: $mode"; exit 1; fi
  grep -q TCQT_ACTIVATION_ERROR "$case_dir/tcqt/activation.log"
  [ "$(cat "$case_dir/tcqt/main.apk")" = 'old payload' ]
  [ ! -e "$case_dir/tcqt/boot-injector" ]
  echo "PASS: rejected $mode without replacing payload"
); done

(
  setup_case
  . "$SANDBOX/package/payload.sh"
  cp() {
    command cp "$@"
    if [ "$2" = "$DATAPATH/main.apk.tmp" ]; then printf 'short copy' > "$2"; fi
  }
  if tcqt_publish_payload "$ZIPFILE" "$APK_HASH_EXPECTED"; then
    echo 'A damaged copy was published'; exit 1
  fi
  [ "$(cat "$DATAPATH/main.apk")" = 'old payload' ]
  [ "$(cat "$DATAPATH/main.apk.sha256")" = "$(hash "$DATAPATH/main.apk")" ]
  echo 'PASS: copied payload is verified before replacing the active package'
)
