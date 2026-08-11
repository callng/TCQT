#!/system/bin/sh

for user_dir in /data/user/*/com.tencent.mobileqq/files/.tcqt /data/user/*/com.tencent.tim/files/.tcqt
do
  rm -rf "$user_dir" 2>/dev/null
done

rm -rf /data/adb/tcqt 2>/dev/null

exit 0
