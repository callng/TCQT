#!/system/bin/sh

SKIPUNZIP=1

# KernelSU evaluates these after customize.sh returns. Request hot install only
# when the injector already loaded by Zygisk can run the new payload unchanged.
export MODULE_HOT_INSTALL_REQUEST=false
unset MODULE_HOT_RUN_SCRIPT

if [ "$BOOTMODE" != "true" ]; then
  ui_print "*********************************************************"
  ui_print "! Install from recovery is not supported"
  ui_print "! Please install from KernelSU or Magisk app"
  abort    "*********************************************************"
fi

ui_print "- Installing TCQT Zygisk"

if [ "$ARCH" != "arm64" ]; then
  abort "! Unsupported platform: $ARCH (only arm64-v8a)"
fi

ui_print "- Extracting module files"
unzip -o "$ZIPFILE" 'module.prop' 'uninstall.sh' 'hotinstall.sh' 'share-logs.sh' 'open-link.sh' 'payload.sh' 'post-fs-data.sh' -d "$MODPATH" >&2 ||
  abort "! Missing module scripts or metadata"

mkdir -p "$MODPATH/zygisk"

ui_print "- Extracting Zygisk library"
unzip -o "$ZIPFILE" 'zygisk/arm64-v8a.so' -d "$MODPATH" >&2 ||
  abort "! Missing arm64-v8a Zygisk library"

ui_print "- Extracting WebUI"
unzip -o "$ZIPFILE" 'webroot/*' -d "$MODPATH" >&2 ||
  abort "! Missing webroot"

. "$MODPATH/payload.sh"
ui_print "- Staging TCQT package"
mkdir -p "$DATAPATH/pending" || abort "! Failed to create payload staging directory"
APK_HASH="$(tcqt_hash "$ZIPFILE")"
tcqt_valid_hash "$APK_HASH" || abort "! Failed to calculate TCQT package hash"
PENDING_APK="$DATAPATH/pending/$APK_HASH.apk"
cp "$ZIPFILE" "$PENDING_APK.tmp" || abort "! Failed to stage TCQT package"
set_perm "$PENDING_APK.tmp" 0 0 0644
mv -f "$PENDING_APK.tmp" "$PENDING_APK" || abort "! Failed to stage TCQT package"
printf '%s' "$APK_HASH" > "$MODPATH/payload.sha256" || abort "! Failed to store payload selection"
# 清理旧布局（payload/tcqt.apk + classes*.dex + dex.list）残留
rm -rf "$MODPATH/payload"

ui_print "- Setting permissions"
set_perm_recursive "$MODPATH/zygisk" 0 0 0755 0644
set_perm_recursive "$MODPATH/webroot" 0 0 0755 0644
set_perm "$MODPATH/module.prop" 0 0 0644
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/hotinstall.sh" 0 0 0755
set_perm "$MODPATH/payload.sh" 0 0 0644
set_perm "$MODPATH/payload.sha256" 0 0 0644
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/share-logs.sh" 0 0 0755
set_perm "$MODPATH/open-link.sh" 0 0 0755

ui_print "- Fixing SELinux contexts"
chcon -R u:object_r:system_file:s0 "$MODPATH" 2>/dev/null || true
chcon u:object_r:system_lib_file:s0 "$MODPATH/zygisk/arm64-v8a.so" 2>/dev/null || true

# Compare implementation identities, not ELF hashes: build paths/metadata can
# change the binary without changing the injector. Old versions without an
# identity/boot record require one full reboot before hot updates work.
LIVE_MODPATH="/data/adb/modules/zygisk_tcqt"
NEW_INJECTOR_ID="$(tcqt_injector_id "$MODPATH/zygisk/arm64-v8a.so")"
if tcqt_can_update_live "$NEW_INJECTOR_ID" "$LIVE_MODPATH"; then
  tcqt_publish_payload "$PENDING_APK" "$APK_HASH" || abort "! Failed to publish TCQT package"
  rm -f "$PENDING_APK"
  if [ "${KSU:-}" = "true" ] && [ "${KSU_HAS_HOT_INSTALL:-}" = "true" ]; then
    export MODULE_HOT_INSTALL_REQUEST=true
    export MODULE_HOT_RUN_SCRIPT=hotinstall.sh
    ui_print "- 已请求 KernelSU 热安装；是否成功请查看后续安装器输出"
    ui_print "- 若热安装成功，完全结束 QQ/TIM 后重新打开即可生效"
    ui_print "- 若安装器回退为下次启动生效，请重启设备"
  else
    if [ "${KSU:-}" = "true" ]; then
      ui_print "! 当前 KernelSU 安装器未声明支持热安装（KSU_HAS_HOT_INSTALL）"
      ui_print "! 请更新到支持 Hot install 的 KernelSU 管理器/ksud 后再试"
    fi
    ui_print "- 载荷已更新，完全结束 QQ/TIM 后重新打开即可加载"
    ui_print "- 模块目录和 WebUI 更新仍需按管理器提示重启设备"
  fi
else
  ui_print "! 无法确认本次开机的注入库兼容；新版载荷已暂存，需要完整重启设备后生效"
  ui_print "! 重启前保留当前载荷；强制停止宿主或清缓存不能替代重启设备"
fi
ui_print "! 请勿同时在 LSPosed 和 Zygisk 中启用 TCQT。"
ui_print "- TCQT Zygisk installed"
