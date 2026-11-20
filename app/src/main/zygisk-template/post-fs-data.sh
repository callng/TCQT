#!/system/bin/sh

MODDIR=${0%/*}
. "$MODDIR/payload.sh"
[ ! -e "$MODDIR/disable" ] && [ ! -e "$MODDIR/remove" ] || exit 0

umask 077
mkdir -p "$DATAPATH" || exit 1
# post-fs-data can fail before any host process/file logger exists. Keep the
# exact activation stage for the WebUI report instead of silently retaining an
# old main.apk across reboots. This file is replaced, not appended, each boot.
exec > "$DATAPATH/activation.log" 2>&1
fail() { echo "TCQT_ACTIVATION_ERROR: $1"; exit 1; }
echo "TCQT payload activation: $(date '+%F %T')"
APK_HASH="$(cat "$MODDIR/payload.sha256" 2>/dev/null)"
SO_HASH="$(tcqt_hash "$MODDIR/zygisk/arm64-v8a.so")"
INJECTOR_ID="$(tcqt_injector_id "$MODDIR/zygisk/arm64-v8a.so")"
echo "expected_payload=$APK_HASH"
echo "current_payload=$(tcqt_hash "$DATAPATH/main.apk")"
echo "injector=$INJECTOR_ID"
tcqt_valid_hash "$APK_HASH" && tcqt_valid_hash "$SO_HASH" || fail 'invalid package/library hash'
tcqt_valid_injector_id "$INJECTOR_ID" || fail 'missing or duplicate injector identity'
PENDING_APK="$DATAPATH/pending/$APK_HASH.apk"

if [ -f "$PENDING_APK" ]; then
  # Never activate an abandoned install attempt with a different injector.
  PAYLOAD_SO_HASH="$(unzip -p "$PENDING_APK" 'zygisk/arm64-v8a.so' 2>/dev/null | sha256sum | cut -d ' ' -f1)"
  [ "$PAYLOAD_SO_HASH" = "$SO_HASH" ] || fail 'pending package injector differs from installed library'
  tcqt_publish_payload "$PENDING_APK" "$APK_HASH" || fail 'payload copy/hash/SELinux publication failed'
  rm -f "$PENDING_APK"
else
  # A compatible hot update may already have published this exact package.
  [ "$(tcqt_hash "$DATAPATH/main.apk")" = "$APK_HASH" ] || fail 'pending package missing and active payload hash differs'
fi

TCQT_BOOT_ID="$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)"
[ -n "$TCQT_BOOT_ID" ] || fail 'boot ID unavailable'
printf '%s %s' "$TCQT_BOOT_ID" "$INJECTOR_ID" > "$DATAPATH/boot-injector.tmp" || fail 'cannot write boot identity'
chmod 0600 "$DATAPATH/boot-injector.tmp" || fail 'cannot protect boot identity'
mv -f "$DATAPATH/boot-injector.tmp" "$DATAPATH/boot-injector" || fail 'cannot publish boot identity'
echo 'TCQT_ACTIVATION_OK'
