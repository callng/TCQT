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
unzip -o "$ZIPFILE" 'module.prop' 'uninstall.sh' 'hotinstall.sh' -d "$MODPATH" >&2 ||
  abort "! Missing module scripts or metadata"

mkdir -p "$MODPATH/zygisk"

ui_print "- Extracting Zygisk library"
unzip -o "$ZIPFILE" 'zygisk/arm64-v8a.so' -d "$MODPATH" >&2 ||
  abort "! Missing arm64-v8a Zygisk library"

ui_print "- Extracting WebUI"
unzip -o "$ZIPFILE" 'webroot/*' -d "$MODPATH" >&2 ||
  abort "! Missing webroot"

ui_print "- Storing TCQT package"
DATAPATH="/data/adb/tcqt"
mkdir -p "$DATAPATH" || abort "! Failed to create $DATAPATH"
cp "$ZIPFILE" "$DATAPATH/main.apk.tmp" || abort "! Failed to store TCQT package"

ui_print "- Calculating TCQT package hash"
APK_HASH="$(sha256sum "$DATAPATH/main.apk.tmp" 2>/dev/null | cut -d ' ' -f1)"
if [ "${#APK_HASH}" -ne 64 ]; then
  abort "! Failed to calculate TCQT package hash"
fi
printf '%s' "$APK_HASH" > "$DATAPATH/main.apk.sha256.tmp" ||
  abort "! Failed to store TCQT package hash"
set_perm "$DATAPATH/main.apk.tmp" 0 0 0644
set_perm "$DATAPATH/main.apk.sha256.tmp" 0 0 0644
mv -f "$DATAPATH/main.apk.tmp" "$DATAPATH/main.apk" ||
  abort "! Failed to publish TCQT package"
mv -f "$DATAPATH/main.apk.sha256.tmp" "$DATAPATH/main.apk.sha256" ||
  abort "! Failed to publish TCQT package hash"
set_perm "$DATAPATH/main.apk.sha256" 0 0 0644

# 清理旧布局（payload/tcqt.apk + classes*.dex + dex.list）残留
rm -rf "$MODPATH/payload"

ui_print "- Setting permissions"
set_perm_recursive "$MODPATH/zygisk" 0 0 0755 0644
set_perm_recursive "$MODPATH/webroot" 0 0 0755 0644
set_perm "$MODPATH/module.prop" 0 0 0644
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/hotinstall.sh" 0 0 0755

ui_print "- Fixing SELinux contexts"
chcon -R u:object_r:system_file:s0 "$MODPATH" 2>/dev/null || true
chcon u:object_r:system_lib_file:s0 "$MODPATH/zygisk/arm64-v8a.so" 2>/dev/null || true

# Compare with the live module, NOT the previous install attempt: an update in
# modules_update (or the old so.sha1 file) may never have been loaded by Zygisk.
LIVE_MODPATH="/data/adb/modules/zygisk_tcqt"
NEW_SO_HASH="$(sha256sum "$MODPATH/zygisk/arm64-v8a.so" 2>/dev/null | cut -d ' ' -f1)"
OLD_SO_HASH="$(sha256sum "$LIVE_MODPATH/zygisk/arm64-v8a.so" 2>/dev/null | cut -d ' ' -f1)"
if [ "${#NEW_SO_HASH}" -eq 64 ] && [ "$OLD_SO_HASH" = "$NEW_SO_HASH" ] &&
   [ ! -e "$LIVE_MODPATH/disable" ] && [ ! -e "$LIVE_MODPATH/remove" ]; then
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
  ui_print "! 首次安装、注入库变化或模块已停用/待卸载，需要重启设备后生效"
fi
ui_print "! 提示: 当 TCQT 使用 Zygisk 工作时， 请勿在其他框架如 LSPosed 中对 QQ/ TIM 启用任何模块，否则可能导致意想不到的问题发生。"
ui_print "- TCQT Zygisk installed"
