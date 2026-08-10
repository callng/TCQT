#!/system/bin/sh

# 卸载时清理注入到 QQ/TIM 数据目录的 payload 副本
for user_dir in /data/user/*/com.tencent.mobileqq/files/.tcqt /data/user/*/com.tencent.tim/files/.tcqt
do
  rm -rf "$user_dir" 2>/dev/null
done

exit 0
