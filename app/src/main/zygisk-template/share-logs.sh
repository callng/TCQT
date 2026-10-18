#!/system/bin/sh
# Called by the WebUI with root. The helper runs as Android shell and publishes
# a MediaStore attachment, so receiving apps get a content URI + read grant.
set -eu
export PATH=/system/bin:/system/xbin:$PATH
base=/data/adb/tcqt
module=/data/adb/modules/zygisk_tcqt
stamp=$(date +%Y%m%d%H%M%S)$$
work=/data/local/tmp/tcqt-share-$stamp
report=/data/local/tmp/tcqt-report-$stamp.txt
trap 'rm -f "$report"; rm -rf "$work"' EXIT
umask 077
mkdir "$work"
count=0
{
    echo "===== TCQT Zygisk report $(date '+%F %T') ====="
    echo "[Device] $(getprop ro.product.manufacturer) $(getprop ro.product.model)"
    echo "[Android] $(getprop ro.build.version.release) SDK=$(getprop ro.build.version.sdk)"
    echo "[Kernel] $(uname -r) pageSize=$(getconf PAGE_SIZE)"
    echo "[SELinux] $(getenforce)"
    cat "$module/module.prop"
    echo "[Configuration markers]"
    find "$base" -maxdepth 2 -type f \( -name '*.disable' -o -name '*.enable' \) -print
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
    logcat -d -t 400 -v threadtime -s 'TCQT-Zygisk:*' 'ZygiskEntry:*' 'ZygiskHookBridge:*' 2>&1 || true
} > "$report"
cp "$base/main.apk" "$work/main.apk"
chmod 755 "$work"
chmod 644 "$work/main.apk" "$report"
current=$(am get-current-user)
case "$current" in ''|*[!0-9]*) echo 'Cannot determine foreground user' >&2; exit 1 ;; esac
shell_uid=$((current * 100000 + 2000))
# Run a private copy to avoid Android 14+ writable DEX restrictions.
su "$shell_uid" -c "CLASSPATH='$work/main.apk' app_process /system/bin com.owo233.tcqt.loader.zygisk.LogShare '$report'"
