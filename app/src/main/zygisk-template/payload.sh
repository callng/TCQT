#!/system/bin/sh

# Shared by the installer and post-fs-data. Pending packages live outside the
# module directory and are selected by the installed module's exact APK hash.
DATAPATH="/data/adb/tcqt"

tcqt_hash() {
  sha256sum "$1" 2>/dev/null | cut -d ' ' -f1
}

tcqt_valid_hash() {
  [ "${#1}" -eq 64 ] || return 1
  case "$1" in *[!0-9a-f]*) return 1 ;; esac
}

tcqt_valid_injector_id() {
  case "$1" in
    TCQT_INJECTOR_ID_V1=*) tcqt_valid_hash "${1#TCQT_INJECTOR_ID_V1=}" ;;
    *) return 1 ;;
  esac
}

tcqt_injector_id() {
  # Read the identity embedded in this exact library, never a sidecar generated
  # from possibly newer sources. Missing/duplicate markers fail validation.
  TCQT_ID="$(LC_ALL=C grep -aoE 'TCQT_INJECTOR_ID_V1=[0-9a-f]{64}' "$1" 2>/dev/null)"
  tcqt_valid_injector_id "$TCQT_ID" || return 1
  printf '%s' "$TCQT_ID"
}

tcqt_publish_payload() {
  tcqt_valid_hash "$2" && [ "$(tcqt_hash "$1")" = "$2" ] || return 1
  cp "$1" "$DATAPATH/main.apk.tmp" || return 1
  [ "$(tcqt_hash "$DATAPATH/main.apk.tmp")" = "$2" ] || return 1
  printf '%s' "$2" > "$DATAPATH/main.apk.sha256.tmp" || return 1
  chmod 0644 "$DATAPATH/main.apk.tmp" "$DATAPATH/main.apk.sha256.tmp" || return 1
  # SCM_RIGHTS checks the Zygote's permission to read the payload FD.
  chcon u:object_r:system_file:s0 "$DATAPATH/main.apk.tmp" || return 1
  mv -f "$DATAPATH/main.apk.tmp" "$DATAPATH/main.apk" || return 1
  mv -f "$DATAPATH/main.apk.sha256.tmp" "$DATAPATH/main.apk.sha256" || return 1
  # Retain the old injector's settings until its payload is actually replaced.
  rm -f "$DATAPATH/compat.enable" "$DATAPATH/compat"
}

tcqt_can_update_live() {
  # A live path can already contain a replacement while Zygote still maps the
  # old (deleted) inode. Only an implementation ID recorded at this boot
  # authorizes it, even when both files on disk have the new ID already.
  tcqt_valid_injector_id "$1" || return 1
  TCQT_BOOT_ID="$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)"
  [ -n "$TCQT_BOOT_ID" ] &&
    [ "$(cat "$DATAPATH/boot-injector" 2>/dev/null)" = "$TCQT_BOOT_ID $1" ] &&
    [ "$(tcqt_injector_id "$2/zygisk/arm64-v8a.so")" = "$1" ] &&
    [ ! -e "$2/disable" ] && [ ! -e "$2/remove" ] && [ ! -e "$2/update" ]
}
