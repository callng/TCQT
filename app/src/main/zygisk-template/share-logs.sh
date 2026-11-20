#!/system/bin/sh
# Called by the WebUI with root. An independent DEX runs as Android shell and
# publishes a MediaStore attachment even when the injected payload cannot load.
set -eu
export PATH=/system/bin:/system/xbin:$PATH
base=/data/adb/tcqt
module=/data/adb/modules/zygisk_tcqt
stamp=$(date +%Y%m%d%H%M%S)$$
work=/data/local/tmp/tcqt-share-$stamp
report=/data/local/tmp/tcqt-report-$stamp.txt
shared=0
cleanup() {
    # Never discard the only host diagnostics when app_process/chooser fails.
    if [ "$shared" -ne 1 ] && [ -s "$report" ]; then
        saved_tmp="$base/last-share-report.$stamp.tmp"
        if mkdir -p "$base" && cp "$report" "$saved_tmp" &&
            chmod 600 "$saved_tmp" &&
            mv -f "$saved_tmp" "$base/last-share-report.txt"; then
            echo "日志已保留：$base/last-share-report.txt" >&2
        fi
        rm -f "$saved_tmp"
    fi
    rm -f "$report"
    rm -rf "$work"
}
trap cleanup EXIT
umask 077
mkdir "$work"
count=0
{
    echo "===== TCQT Zygisk report $(date '+%F %T') ====="
    echo "[Device] $(getprop ro.product.manufacturer) $(getprop ro.product.model)"
    echo "[Android] $(getprop ro.build.version.release) SDK=$(getprop ro.build.version.sdk)"
    echo "[Kernel] $(uname -r) pageSize=$(getconf PAGE_SIZE)"
    echo "[SELinux] $(getenforce)"
    cat "$module/module.prop" 2>&1 || true
    echo "[Payload activation]"
    cat "$base/activation.log" 2>/dev/null || true
    for file in "$module/payload.sha256" "$base/main.apk.sha256" "$base/boot-injector"; do
        echo "----- $file -----"
        cat "$file" 2>&1 || true
        echo
    done
    echo "[Actual payload/helper hashes and permissions]"
    sha256sum "$base/main.apk" "$module/logshare.dex" 2>&1 || true
    ls -ldZ "$base" "$base/main.apk" "$module/zygisk/arm64-v8a.so" 2>&1 || true
    echo "[Configuration markers]"
    find "$base" -maxdepth 2 -type f \( -name '*.disable' -o -name '*.enable' \) -print 2>&1 || true
    echo "[Host payload caches]"
    for dir in /data/user/*/com.tencent.mobileqq/files/.tcqt /data/user/*/com.tencent.tim/files/.tcqt; do
        [ -d "$dir" ] || continue
        ls -lZ "$dir/main.apk" 2>&1 || true
        cat "$dir/main.apk.sha256" 2>&1 || true
        echo
    done
    echo "[Hosts]"
    for pkg in com.tencent.mobileqq com.tencent.tim; do
        dumpsys package "$pkg" 2>/dev/null | grep -m1 versionName || true
    done
    # Events precede debug details; each section includes its source path.
    for kind in events debug legacy; do
        echo "===== $kind ====="
        for dir in /data/user/*/com.tencent.mobileqq/files/.tcqt /data/user/*/com.tencent.tim/files/.tcqt; do
            [ -d "$dir" ] || continue
            case "$kind" in
                events) pattern="$dir/logs/*.events.log*" ;;
                debug) pattern="$dir/logs/*.debug.log*" ;;
                legacy) pattern="$dir/log.txt*" ;;
            esac
            # Deliberate expansion of module-controlled paths; excludes lock files.
            for file in $pattern; do
                [ -f "$file" ] || continue
                case "$file" in *.lock) continue ;; esac
                count=$((count + 1))
                echo "----- $file -----"
                tail -c 1048576 "$file" || echo "[read failed]"
                echo
            done
        done
    done
    echo "[Log files collected] $count"
    [ "$count" -gt 0 ] || echo "No host log found. Start QQ/TIM with Zygisk injection enabled, then retry."
    echo "===== Recent injection logcat (includes failures before file logging starts) ====="
    logcat -b all -d -t 400 -v threadtime -s 'TCQT-Zygisk:*' 'ZygiskEntry:*' 'ZygiskHookBridge:*' 2>&1 || true
} > "$report"
cp "$module/logshare.dex" "$work/logshare.dex"
expected=$(cat "$module/logshare.dex.sha256")
actual=$(sha256sum "$work/logshare.dex" | cut -d ' ' -f1)
if [ "${#expected}" -ne 64 ] || [ "$actual" != "$expected" ]; then
    echo '日志分享组件校验失败，请重新安装 TCQT 模块' >&2
    exit 1
fi
chmod 755 "$work"
chmod 444 "$work/logshare.dex"
chmod 644 "$report"
current=$(am get-current-user)
case "$current" in ''|*[!0-9]*) echo 'Cannot determine foreground user' >&2; exit 1 ;; esac
shell_uid=$((current * 100000 + 2000))
# Explicit VM classpath, with a root-owned read-only copy independent of main.apk.
if su "$shell_uid" -c "/system/bin/app_process -Djava.class.path='$work/logshare.dex' /system/bin com.owo233.tcqt.loader.zygisk.LogShare '$report'" > "$work/helper-output" 2>&1; then
    if grep -q '^TCQT_SHARE_OK ' "$work/helper-output"; then
        shared=1
        cat "$work/helper-output"
        exit 0
    fi
fi
echo '===== Log sharing failure =====' >> "$report"
cat "$work/helper-output" >> "$report"
cat "$work/helper-output" >&2
echo '系统分享未成功打开' >&2
exit 1
