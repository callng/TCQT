#!/usr/bin/env bash
# Exercise the production shell with isolated files and Android command stubs.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SANDBOX=$(mktemp -d)
trap 'rm -rf "$SANDBOX"' EXIT
mkdir -p "$SANDBOX/bin" "$SANDBOX/module" "$SANDBOX/tmp"
sed -e "s|base=/data/adb/tcqt|base=$SANDBOX/data|" \
    -e "s|module=/data/adb/modules/zygisk_tcqt|module=$SANDBOX/module|" \
    -e "s|/data/local/tmp|$SANDBOX/tmp|g" \
    -e "s|/data/user/|$SANDBOX/users/|g" \
    -e 's|export PATH=/system/bin:/system/xbin:$PATH|export PATH=$PATH|' \
    "$ROOT/app/src/main/zygisk-template/share-logs.sh" | tr -d '\r' > "$SANDBOX/share.sh"
printf 'version=test\n' > "$SANDBOX/module/module.prop"
printf 'independent dex fixture' > "$SANDBOX/module/logshare.dex"
sha256sum "$SANDBOX/module/logshare.dex" | cut -d ' ' -f1 > "$SANDBOX/module/logshare.dex.sha256"
for command in getprop getenforce dumpsys logcat; do
    printf '#!/bin/sh\necho fixture\n' > "$SANDBOX/bin/$command"
done
printf '#!/bin/sh\necho 0\n' > "$SANDBOX/bin/am"
cat > "$SANDBOX/bin/su" <<'SH'
#!/bin/sh
set -eu
[ "$1" = 2000 ] && [ "$2" = -c ]
case "$3" in
    *"/system/bin/app_process -Djava.class.path='"*"/logshare.dex' /system/bin com.owo233.tcqt.loader.zygisk.LogShare "*) ;;
    *) echo 'Unexpected helper invocation' >&2; exit 1 ;;
esac
case "$3" in *main.apk*) exit 1 ;; esac
case "${TCQT_TEST_SHARE_MODE:-success}" in
    abort) echo 'Aborted' >&2; exit 134 ;;
    unconfirmed) exit 0 ;;
    *) echo 'TCQT_SHARE_OK content://media/external/downloads/1' ;;
esac
SH
chmod +x "$SANDBOX/bin/"*
export PATH="$SANDBOX/bin:$PATH"

for mode in missing corrupt; do
    mkdir -p "$SANDBOX/data"
    rm -f "$SANDBOX/data/main.apk"
    if [ "$mode" = corrupt ]; then printf 'invalid apk' > "$SANDBOX/data/main.apk"; fi
    sh "$SANDBOX/share.sh" > "$SANDBOX/output" 2>&1
    grep -q TCQT_SHARE_OK "$SANDBOX/output"
    [ -z "$(ls -A "$SANDBOX/tmp")" ]
    echo "PASS: share succeeds with $mode host payload"
done

for mode in abort unconfirmed; do
    export TCQT_TEST_SHARE_MODE="$mode"
    if sh "$SANDBOX/share.sh" > "$SANDBOX/output" 2>&1; then echo "Unexpected success: $mode"; exit 1; fi
    grep -q '日志已保留' "$SANDBOX/output"
    grep -q 'TCQT Zygisk report' "$SANDBOX/data/last-share-report.txt"
    grep -q 'Log sharing failure' "$SANDBOX/data/last-share-report.txt"
    [ "$(stat -c %a "$SANDBOX/data/last-share-report.txt")" = 600 ]
    [ -z "$(ls -A "$SANDBOX/tmp")" ]
    if [ "$mode" = abort ]; then grep -q Aborted "$SANDBOX/data/last-share-report.txt"; fi
    echo "PASS: $mode preserves collected diagnostics and cleans temporary files"
done
unset TCQT_TEST_SHARE_MODE

printf 'damaged helper' > "$SANDBOX/module/logshare.dex"
if sh "$SANDBOX/share.sh" > "$SANDBOX/output" 2>&1; then echo 'Damaged helper was accepted'; exit 1; fi
grep -q '日志分享组件校验失败' "$SANDBOX/output"
grep -q '日志已保留' "$SANDBOX/output"
[ -z "$(ls -A "$SANDBOX/tmp")" ]
echo 'PASS: damaged helper rejected before app_process starts; report retained'

rm "$SANDBOX/module/logshare.dex"
if sh "$SANDBOX/share.sh" > "$SANDBOX/output" 2>&1; then echo 'Missing helper was accepted'; exit 1; fi
grep -q '日志已保留' "$SANDBOX/output"
[ -z "$(ls -A "$SANDBOX/tmp")" ]
echo 'PASS: incomplete module install still preserves the report'
