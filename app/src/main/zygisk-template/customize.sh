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

mkdir -p "$MODPATH/zygisk" "$MODPATH/payload"

ui_print "- Extracting Zygisk library"
unzip -oj "$ZIPFILE" 'lib/arm64-v8a/libtcqtzygisk.so' -d "$MODPATH/zygisk" >&2 ||
  abort "! Missing arm64-v8a Zygisk library"
mv "$MODPATH/zygisk/libtcqtzygisk.so" "$MODPATH/zygisk/arm64-v8a.so"

ui_print "- Extracting TCQT payload"
unzip -o "$ZIPFILE" 'payload/tcqt.apk' -d "$MODPATH" >&2 ||
  abort "! Missing payload APK"

# 从 payload APK 提取 classes*.dex 并生成 dex.list
extract_payload_dex() {
  payload_apk=$1
  installed_payload_dir=$2

  # 热更新可能让 APK 的 dex 数量变化，先清掉旧派生文件
  rm -f "$installed_payload_dir"/classes*.dex "$installed_payload_dir/dex.list"
  unzip -o "$payload_apk" 'classes*.dex' -d "$installed_payload_dir" >&2 ||
    abort "! Unable to extract DEX payload from $payload_apk"

  dex_entries=""
  for dex_path in "$installed_payload_dir"/classes*.dex
  do
    [ -f "$dex_path" ] || continue
    dex_name=${dex_path##*/}
    case "$dex_name" in
      classes.dex)
        dex_number=1
        ;;
      classes[0-9]*.dex)
        dex_number=${dex_name#classes}
        dex_number=${dex_number%.dex}
        case "$dex_number" in
          ''|*[!0-9]*|0*|1) abort "! Invalid DEX payload entry: $dex_name" ;;
        esac
        ;;
      *)
        abort "! Invalid DEX payload entry: $dex_name"
        ;;
    esac
    dex_entries="$dex_entries $dex_number:$dex_name"
  done

  [ -n "$dex_entries" ] ||
    abort "! APK does not contain classes.dex: $payload_apk"
  : > "$installed_payload_dir/dex.list" ||
    abort "! Unable to create DEX list for $payload_apk"

  # 按编号升序写入 dex.list
  for entry in $(printf '%s\n' $dex_entries | sort -n)
  do
    printf '%s\n' "${entry#*:}" >> "$installed_payload_dir/dex.list" ||
      abort "! Unable to write DEX list for $payload_apk"
  done
}

extract_payload_dex "$MODPATH/payload/tcqt.apk" "$MODPATH/payload"

ui_print "- Setting permissions"
set_perm_recursive "$MODPATH/zygisk" 0 0 0755 0644
set_perm_recursive "$MODPATH/payload" 0 0 0755 0644
set_perm "$MODPATH/module.prop" 0 0 0644
set_perm "$MODPATH/uninstall.sh" 0 0 0755

# 若模块已激活（更新而非首次安装），把新 payload 同步到运行中的模块目录
module_id=$(grep_prop id "$MODPATH/module.prop")
active_dir="/data/adb/modules/$module_id"
injector_changed=false
if [ -d "$active_dir/payload" ]; then
  ui_print "- Syncing payload to active module dir"
  rm -rf "$active_dir/payload"
  cp -r "$MODPATH/payload" "$active_dir/payload" ||
    abort "! Failed to sync payload to $active_dir"
  set_perm_recursive "$active_dir/payload" 0 0 0755 0644
  if [ -f "$active_dir/zygisk/arm64-v8a.so" ] &&
    ! cmp -s "$active_dir/zygisk/arm64-v8a.so" "$MODPATH/zygisk/arm64-v8a.so"; then
    injector_changed=true
  fi
fi

ui_print "- TCQT Zygisk installed"
if [ "$injector_changed" = "true" ]; then
  ui_print "! Zygisk 注入器已更新，仍需重启设备生效"
else
  ui_print "  完全结束并重新启动 QQ/TIM 即可生效（无需重启设备）"
fi
