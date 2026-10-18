#!/system/bin/sh
# Resolve the foreground user's browser, including for app-associated t.me links.
case "$1" in
    source) url='https://github.com/callng/TCQT' ;;
    channel) url='https://t.me/citcqt' ;;
    group) url='https://t.me/astcqt' ;;
    *) echo 'Unknown project link' >&2; exit 1 ;;
esac

current=$(am get-current-user)
case "$current" in ''|*[!0-9]*) echo 'Cannot determine foreground user' >&2; exit 1 ;; esac
browser=$(cmd role get-role-holders --user "$current" android.app.role.BROWSER 2>/dev/null | head -n 1)
if [ -z "$browser" ]; then
    # Android 8/9 have no RoleManager. Resolve a neutral web URL without opening it.
    component=$(cmd package resolve-activity --brief --user "$current" \
        -a android.intent.action.VIEW -c android.intent.category.BROWSABLE \
        -d 'https://example.com' 2>/dev/null | tail -n 1)
    case "$component" in */*) browser=${component%%/*} ;; esac
fi
case "$browser" in
    ''|android|*[!a-zA-Z0-9_.]*) echo '请先设置系统默认浏览器' >&2; exit 1 ;;
esac

output=$(am start --user "$current" -a android.intent.action.VIEW \
    -c android.intent.category.BROWSABLE -p "$browser" -d "$url" 2>&1)
result=$?
case "$output" in *Error:*|*Exception*) result=1 ;; esac
if [ "$result" -ne 0 ]; then
    printf '%s\n' "$output" >&2
    exit 1
fi
echo TCQT_BROWSER_OK
