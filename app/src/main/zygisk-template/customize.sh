#!/system/bin/sh

SKIPUNZIP=1

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
unzip -o "$ZIPFILE" 'module.prop' -d "$MODPATH" >&2
unzip -o "$ZIPFILE" 'uninstall.sh' -d "$MODPATH" >&2

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
mv -f "$DATAPATH/main.apk.tmp" "$DATAPATH/main.apk"
set_perm "$DATAPATH/main.apk" 0 0 0644

ui_print "- Calculating TCQT package hash"
APK_HASH="$(sha256sum "$DATAPATH/main.apk" 2>/dev/null | cut -d ' ' -f1)"
if [ -z "$APK_HASH" ]; then
  abort "! Failed to calculate TCQT package hash"
fi
printf '%s' "$APK_HASH" > "$DATAPATH/main.apk.sha256"
set_perm "$DATAPATH/main.apk.sha256" 0 0 0644

# 清理旧布局（payload/tcqt.apk + classes*.dex + dex.list）残留
rm -rf "$MODPATH/payload"

ui_print "- Setting permissions"
set_perm_recursive "$MODPATH/zygisk" 0 0 0755 0644
set_perm_recursive "$MODPATH/webroot" 0 0 0755 0644
set_perm "$MODPATH/module.prop" 0 0 0644
set_perm "$MODPATH/uninstall.sh" 0 0 0755

ui_print "- Fixing SELinux contexts"
chcon -R u:object_r:system_file:s0 "$MODPATH" 2>/dev/null || true
chcon u:object_r:system_lib_file:s0 "$MODPATH/zygisk/arm64-v8a.so" 2>/dev/null || true

# 判断是否需要重启设备
NEW_SO_HASH="$(sha1sum "$MODPATH/zygisk/arm64-v8a.so" 2>/dev/null | cut -d ' ' -f1)"
OLD_SO_HASH="$(cat "$DATAPATH/so.sha1" 2>/dev/null | tr -d ' \r\n')"
if [ -n "$OLD_SO_HASH" ] && [ "$OLD_SO_HASH" = "$NEW_SO_HASH" ]; then
  ui_print "✅ 重启 QQ/TIM 即可生效（无需重启设备）"
else
  ui_print "! 需要重启, 某些更改需要(软)重启设备后才会生效"
fi
printf '%s' "$NEW_SO_HASH" > "$DATAPATH/so.sha1"
set_perm "$DATAPATH/so.sha1" 0 0 0644
ui_print "! 提示: 当 TCQT 使用 Zygisk 工作时， 请勿在其他框架如 LSPosed 中对 QQ/ TIM 启用任何模块，否则可能导致意想不到的问题发生。"
ui_print "- TCQT Zygisk installed"
