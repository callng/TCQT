#!/system/bin/sh

MODDIR=${0%/*}
. "$MODDIR/payload.sh"
[ ! -e "$MODDIR/disable" ] && [ ! -e "$MODDIR/remove" ] || exit 0

APK_HASH="$(cat "$MODDIR/payload.sha256" 2>/dev/null)"
SO_HASH="$(tcqt_hash "$MODDIR/zygisk/arm64-v8a.so")"
INJECTOR_ID="$(tcqt_injector_id "$MODDIR/zygisk/arm64-v8a.so")"
tcqt_valid_hash "$APK_HASH" && tcqt_valid_hash "$SO_HASH" || exit 1
tcqt_valid_injector_id "$INJECTOR_ID" || exit 1
PENDING_APK="$DATAPATH/pending/$APK_HASH.apk"

if [ -f "$PENDING_APK" ]; then
  # Never activate an abandoned install attempt with a different injector.
  PAYLOAD_SO_HASH="$(unzip -p "$PENDING_APK" 'zygisk/arm64-v8a.so' 2>/dev/null | sha256sum | cut -d ' ' -f1)"
  [ "$PAYLOAD_SO_HASH" = "$SO_HASH" ] || exit 1
  tcqt_publish_payload "$PENDING_APK" "$APK_HASH" || exit 1
  rm -f "$PENDING_APK"
else
  # A compatible hot update may already have published this exact package.
  [ "$(tcqt_hash "$DATAPATH/main.apk")" = "$APK_HASH" ] || exit 1
fi

TCQT_BOOT_ID="$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)"
[ -n "$TCQT_BOOT_ID" ] || exit 1
printf '%s %s' "$TCQT_BOOT_ID" "$INJECTOR_ID" > "$DATAPATH/boot-injector.tmp" || exit 1
chmod 0600 "$DATAPATH/boot-injector.tmp" || exit 1
mv -f "$DATAPATH/boot-injector.tmp" "$DATAPATH/boot-injector"
