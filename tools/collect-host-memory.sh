#!/system/bin/sh
# Run as root while the high memory use is visible. This only reads the host;
# it does not force GC, dump the Java heap, stop apps or change module settings.
# Usage: sh collect-host-memory.sh [com.tencent.mobileqq|com.tencent.tim] [samples=3] [interval=60]
# Output: /sdcard/Download/TCQT-memory-<timestamp>-<pid>.tar.gz
set -u
umask 077

host_package=${1:-com.tencent.mobileqq}
samples=${2:-3}
interval=${3:-60}
case "$host_package" in
    com.tencent.mobileqq|com.tencent.tim) ;;
    *) echo 'Expected com.tencent.mobileqq or com.tencent.tim' >&2; exit 1 ;;
esac
case "$samples:$interval" in
    *[!0-9:]*|:*|*:) echo 'Samples and interval must be positive integers' >&2; exit 1 ;;
esac
if [ "$samples" -lt 1 ] || [ "$samples" -gt 10 ] ||
   [ "$interval" -lt 1 ] || [ "$interval" -gt 600 ]; then
    echo 'Expected 1..10 samples and 1..600 seconds between samples' >&2
    exit 1
fi
if [ "$(id -u)" != 0 ]; then
    echo 'Run through su so that host smaps and TCQT logs can be read' >&2
    exit 1
fi

name="TCQT-memory-$(date +%Y%m%d-%H%M%S)-$$"
work_dir="/data/local/tmp/$name"
mkdir "$work_dir" || exit 1
archive="/sdcard/Download/$name.tar.gz"
{
    date '+%Y-%m-%dT%H:%M:%S%z'
    echo "package=$host_package samples=$samples interval=$interval"
    uname -a
    getprop ro.product.model
    getprop ro.build.fingerprint
    getprop ro.build.version.sdk
    cat /proc/sys/kernel/random/boot_id /proc/uptime
    for file in /data/adb/modules/zygisk_tcqt/module.prop \
                /data/adb/modules/zygisk_tcqt/payload.sha256 \
                /data/adb/tcqt/main.apk.sha256; do
        echo "--- $file"
        cat "$file" 2>&1
    done
    for module_dir in /data/adb/modules/*; do
        [ -d "$module_dir" ] || continue
        state=enabled
        [ ! -e "$module_dir/disable" ] || state=disabled
        [ ! -e "$module_dir/remove" ] || state=remove
        echo "module=${module_dir##*/} marker_state=$state"
    done
} > "$work_dir/environment.txt" 2>&1
dumpsys -t 20 package "$host_package" > "$work_dir/package.txt" 2>&1
printf 'sample\ttime\tpid\tprocess\tuid\tstart_ticks\trss_kib\thwm_kib\tswap_kib\tvsize_kib\tthreads\tpss_kib\tprivate_dirty_kib\tswap_pss_kib\n' > "$work_dir/summary.tsv"

metric() {
    awk -v key="$1:" '$1 == key { print $2; found=1; exit } END { if (!found) print "NA" }' "$2"
}

sample=1
while [ "$sample" -le "$samples" ]; do
    sample_dir="$work_dir/sample-$sample"
    mkdir "$sample_dir" || exit 1
    date '+%Y-%m-%dT%H:%M:%S%z' > "$sample_dir/time.txt"
    cat /proc/meminfo > "$sample_dir/system-meminfo.txt"
    for pressure in memory cpu io; do
        cat "/proc/pressure/$pressure" > "$sample_dir/pressure-$pressure.txt" 2>&1
    done
    ps -A > "$sample_dir/processes.txt" 2>&1
    found=0
    for proc_dir in /proc/[0-9]*; do
        process=$(tr '\000' '\n' 2>/dev/null < "$proc_dir/cmdline" | head -n 1)
        case "$process" in "$host_package"|"$host_package":*) ;; *) continue ;; esac
        found=$((found + 1))
        pid=${proc_dir##*/}
        pid_dir="$sample_dir/$pid"
        mkdir "$pid_dir" || exit 1
        timestamp=$(date '+%Y-%m-%dT%H:%M:%S%z')
        printf '%s\n' "$timestamp" "$process" > "$pid_dir/identity.txt"
        for entry in status stat smaps_rollup smaps maps; do
            cat "$proc_dir/$entry" > "$pid_dir/$entry" 2> "$pid_dir/$entry.error"
        done
        dumpsys -t 20 meminfo "$pid" > "$pid_dir/dumpsys-meminfo.txt" 2>&1
        # Record stat again to detect an exit or PID reuse during collection.
        cat "$proc_dir/stat" > "$pid_dir/stat-after" 2> "$pid_dir/stat-after.error"
        uid=$(metric Uid "$pid_dir/status")
        start_ticks=$(awk '{ sub(/^.*\) /, ""); print $20 }' "$pid_dir/stat")
        printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
            "$sample" "$timestamp" "$pid" "$process" "$uid" "${start_ticks:-NA}" \
            "$(metric VmRSS "$pid_dir/status")" "$(metric VmHWM "$pid_dir/status")" \
            "$(metric VmSwap "$pid_dir/status")" "$(metric VmSize "$pid_dir/status")" \
            "$(metric Threads "$pid_dir/status")" "$(metric Pss "$pid_dir/smaps_rollup")" \
            "$(metric Private_Dirty "$pid_dir/smaps_rollup")" "$(metric SwapPss "$pid_dir/smaps_rollup")" \
            >> "$work_dir/summary.tsv"
        # UIDs retain the Android user id, including cloned-app user 999.
        case "$uid" in ''|*[!0-9]*) continue ;; esac
        user_id=$((uid / 100000))
        logs_dir="/data/user/$user_id/$host_package/files/.tcqt/logs"
        if [ -d "$logs_dir" ]; then
            mkdir -p "$work_dir/tcqt-logs/user-$user_id"
            for log_file in "$logs_dir"/*.log*; do
                [ -f "$log_file" ] || continue
                cp "$log_file" "$work_dir/tcqt-logs/user-$user_id/"
            done
        fi
    done
    echo "sample=$sample matched_processes=$found" | tee "$sample_dir/result.txt"
    sample=$((sample + 1))
    [ "$sample" -gt "$samples" ] || sleep "$interval"
done
logcat -d -b all -t 12000 > "$work_dir/logcat.txt" 2>&1
if tar -czf "$archive" -C /data/local/tmp "$name"; then
    echo "Archive: $archive"
    echo "Working files: $work_dir"
else
    echo "Archive failed; working files remain at $work_dir" >&2
    exit 1
fi
