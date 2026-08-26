import { exec, toast } from "./kernelsu.js";

const DATA_DIRECTORY = "/data/adb/tcqt";

const apps = {
    qq: { label: "QQ", package: "com.tencent.mobileqq", marker: "qq.disable" },
    tim: { label: "TIM", package: "com.tencent.tim", marker: "tim.disable" },
};

const hooks = [
    {
        id: "key1",
        label: "fopen 重定向",
        desc: "对 /proc/self/smaps 的 fopen 调用重定向到 /dev/null",
    },
];

const scopeList = document.querySelector("#scope-list");
const scopeCount = document.querySelector("#scope-count");
const hookList = document.querySelector("#hook-list");
const hookCount = document.querySelector("#hook-count");
const errorPanel = document.querySelector("#error-panel");
const errorMessage = document.querySelector("#error-message");
const retryButton = document.querySelector("#retry-button");
const liveStatus = document.querySelector("#live-status");
const userTabs = document.querySelector("#user-tabs");
const exportButton = document.querySelector("#export-log-button");
const exportResult = document.querySelector("#export-log-result");

const DEFAULT_USER = { id: 0, name: "主用户" };
let users = [DEFAULT_USER];
let currentUserId = 0;
// userId -> Set(已安装包名), null 表示无法确定
const installedCache = new Map();

function getItem(scopeName) {
    return document.querySelector(`[data-scope="${scopeName}"]`);
}

function getInput(scopeName) {
    return getItem(scopeName).querySelector("input");
}

function getNote(scopeName) {
    return getItem(scopeName).querySelector(".scope-note");
}

function getHookCard(hookId) {
    return document.querySelector(`[data-hook="${hookId}"]`);
}

function getHookTargetItem(hookId, appName) {
    return getHookCard(hookId).querySelector(`[data-target="${appName}"]`);
}

function getHookInput(hookId, appName) {
    return getHookTargetItem(hookId, appName).querySelector("input");
}

function getHookNote(hookId, appName) {
    return getHookTargetItem(hookId, appName).querySelector(".scope-note");
}

function getMarkerPath(userId, marker) {
    return userId === 0
        ? `${DATA_DIRECTORY}/${marker}`
        : `${DATA_DIRECTORY}/user_${userId}/${marker}`;
}

function getHookMarkerPath(userId, hookId, appName) {
    return getMarkerPath(userId, `${appName}.${hookId}.disable`);
}

function isScopeAvailable(scopeName, userId) {
    const installed = installedCache.get(userId);
    if (!installed) {
        return true;
    }
    return installed.has(apps[scopeName].package);
}

function setInputsDisabled(disabled) {
    Object.keys(apps).forEach((appName) => {
        getInput(appName).disabled = disabled || !isScopeAvailable(appName, currentUserId);
    });
    hooks.forEach((hook) => {
        Object.keys(apps).forEach((appName) => {
            getHookInput(hook.id, appName).disabled =
                disabled || !isScopeAvailable(appName, currentUserId);
        });
    });
}

function setTabsDisabled(disabled) {
    userTabs.querySelectorAll("button").forEach((button) => {
        button.disabled = disabled;
    });
}

function updateAvailability() {
    Object.keys(apps).forEach((appName) => {
        const available = isScopeAvailable(appName, currentUserId);
        getItem(appName).classList.toggle("is-unavailable", !available);
        getNote(appName).hidden = available;
    });

    hooks.forEach((hook) => {
        Object.keys(apps).forEach((appName) => {
            const available = isScopeAvailable(appName, currentUserId);
            const targetItem = getHookTargetItem(hook.id, appName);
            targetItem.classList.toggle("is-unavailable", !available);
            getHookNote(hook.id, appName).hidden = available;
        });
    });
}

function updateCount() {
    const appNames = Object.keys(apps);
    const availableApps = appNames.filter((name) => isScopeAvailable(name, currentUserId));
    const enabledApps = availableApps.filter((name) => getInput(name).checked).length;
    scopeCount.textContent = availableApps.length === appNames.length
        ? `已开启 ${enabledApps} 个`
        : `已开启 ${enabledApps} 个 · 可用 ${availableApps.length} 个`;

    let totalHookSlots = 0;
    let enabledHooks = 0;
    hooks.forEach((hook) => {
        availableApps.forEach((appName) => {
            totalHookSlots++;
            if (getHookInput(hook.id, appName).checked) {
                enabledHooks++;
            }
        });
    });
    hookCount.textContent = `已开启 ${enabledHooks}/${totalHookSlots} 个`;
}

function showError(message) {
    errorMessage.textContent = message;
    errorPanel.hidden = false;
}

function hideError() {
    errorPanel.hidden = true;
}

function hasManager() {
    return window.ksu && typeof window.ksu.exec === "function";
}

function parseUsers(output) {
    const parsed = [];
    const pattern = /UserInfo\{(\d+):([^:}]*)/g;
    let match = pattern.exec(output);
    while (match !== null) {
        const id = Number.parseInt(match[1], 10);
        if (!Number.isNaN(id) && !parsed.some((user) => user.id === id)) {
            const name = match[2].trim();
            parsed.push({ id, name: name || `用户 ${id}` });
        }
        match = pattern.exec(output);
    }
    parsed.sort((left, right) => left.id - right.id);
    return parsed;
}

async function loadUsers() {
    try {
        const result = await exec("pm list users 2>/dev/null");
        const parsed = result.errno === 0 ? parseUsers(result.stdout || "") : [];
        users = parsed.length > 0 ? parsed : [DEFAULT_USER];
    } catch (error) {
        console.error("Failed to list users", error);
        users = [DEFAULT_USER];
    }

    if (!users.some((user) => user.id === currentUserId)) {
        currentUserId = users[0].id;
    }
    renderUserTabs();
}

function renderUserTabs() {
    userTabs.textContent = "";
    if (users.length <= 1) {
        userTabs.hidden = true;
        return;
    }

    users.forEach((user) => {
        const tab = document.createElement("button");
        tab.type = "button";
        tab.className = "user-tab";
        tab.dataset.userId = String(user.id);
        tab.textContent = user.name;
        tab.title = `${user.name} (ID ${user.id})`;
        tab.setAttribute("role", "tab");
        tab.setAttribute("aria-selected", user.id === currentUserId ? "true" : "false");
        tab.addEventListener("click", () => switchUser(user.id));
        userTabs.append(tab);
    });
    userTabs.hidden = false;
}

function updateTabSelection() {
    userTabs.querySelectorAll("button").forEach((tab) => {
        tab.setAttribute("aria-selected", Number(tab.dataset.userId) === currentUserId ? "true" : "false");
    });
}

async function switchUser(userId) {
    if (userId === currentUserId) {
        return;
    }
    currentUserId = userId;
    updateTabSelection();
    await loadStates();
}

async function loadInstalledPackages(userId) {
    if (installedCache.has(userId)) {
        return installedCache.get(userId);
    }

    let installed = null;
    try {
        const result = await exec(`pm list packages --user ${userId} 2>/dev/null`);
        if (result.errno === 0 && result.stdout && result.stdout.includes("package:")) {
            installed = new Set();
            result.stdout.split("\n").forEach((line) => {
                const match = line.trim().match(/^package:(\S+)/);
                if (match) {
                    installed.add(match[1]);
                }
            });
        }
    } catch (error) {
        console.error(`Failed to list packages for user ${userId}`, error);
    }

    installedCache.set(userId, installed);
    return installed;
}

function renderHookCards() {
    hookList.textContent = "";
    hooks.forEach((hook) => {
        const card = document.createElement("div");
        card.className = "hook-card";
        card.dataset.hook = hook.id;

        const info = document.createElement("div");
        info.className = "hook-info";
        info.innerHTML = `
            <strong>${hook.label}</strong>
            <p class="hook-desc">${hook.desc}</p>
        `;

        const targets = document.createElement("div");
        targets.className = "hook-targets";

        Object.keys(apps).forEach((appName) => {
            const app = apps[appName];
            const targetLabel = document.createElement("label");
            targetLabel.className = "hook-target-item";
            targetLabel.dataset.target = appName;
            targetLabel.innerHTML = `
                <span class="hook-target-label">${app.label}</span>
                <small class="scope-note" hidden>未安装</small>
                <span class="switch switch-sm">
                    <input type="checkbox" disabled aria-label="${app.label} · ${hook.label}">
                    <span class="switch-track" aria-hidden="true"></span>
                </span>
            `;
            targetLabel.querySelector("input").addEventListener("change", (event) => {
                changeHookState(hook, appName, event.currentTarget.checked);
            });
            targets.append(targetLabel);
        });

        card.append(info, targets);
        hookList.append(card);
    });
}

async function loadStates() {
    hideError();
    setInputsDisabled(true);
    setTabsDisabled(true);
    scopeList.setAttribute("aria-busy", "true");
    hookList.setAttribute("aria-busy", "true");
    scopeCount.textContent = "正在读取";
    hookCount.textContent = "正在读取";

    if (!hasManager()) {
        showError("请在支持模块 WebUI 的管理器（KernelSU / APatch）中打开此页面");
        scopeCount.textContent = "不可用";
        hookCount.textContent = "不可用";
        scopeList.setAttribute("aria-busy", "false");
        hookList.setAttribute("aria-busy", "false");
        return;
    }

    const userId = currentUserId;
    const appChecks = Object.values(apps)
        .map(({ marker }) => `[ -f '${getMarkerPath(userId, marker)}' ] && printf '1\\n' || printf '0\\n'`)
        .join("; ");
    const hookChecks = [];
    hooks.forEach((hook) => {
        Object.keys(apps).forEach((appName) => {
            hookChecks.push(
                `[ -f '${getHookMarkerPath(userId, hook.id, appName)}' ] && printf '1\\n' || printf '0\\n'`
            );
        });
    });
    const checks = [appChecks, ...hookChecks].join("; ");
    const totalChecks = Object.keys(apps).length + hooks.length * Object.keys(apps).length;

    try {
        await loadInstalledPackages(userId);
        const result = await exec(checks);
        const states = (result.stdout || "").trim().split(/\s+/);
        if (result.errno !== 0 || states.length !== totalChecks) {
            throw new Error(result.stderr || "状态数据不完整");
        }
        if (userId !== currentUserId) {
            return;
        }

        // TCQT 标记是 disable 文件：存在(1)表示禁用(checked=false)，不存在(0)表示启用(checked=true)
        Object.keys(apps).forEach((appName, index) => {
            getInput(appName).checked = states[index] !== "1";
        });

        let hookIndex = Object.keys(apps).length;
        hooks.forEach((hook) => {
            Object.keys(apps).forEach((appName) => {
                getHookInput(hook.id, appName).checked = states[hookIndex] !== "1";
                hookIndex++;
            });
        });

        updateAvailability();
        setInputsDisabled(false);
        updateCount();
    } catch (error) {
        console.error("Failed to load TCQT scopes", error);
        if (userId !== currentUserId) {
            return;
        }
        showError("读取作用域失败，请稍后重试");
        scopeCount.textContent = "读取失败";
        hookCount.textContent = "读取失败";
    } finally {
        if (userId === currentUserId) {
            setTabsDisabled(false);
            scopeList.setAttribute("aria-busy", "false");
            hookList.setAttribute("aria-busy", "false");
        }
    }
}

async function changeScope(appName, enabled) {
    const input = getInput(appName);
    const app = apps[appName];
    const userId = currentUserId;
    input.disabled = true;
    hideError();

    const markerPath = getMarkerPath(userId, app.marker);
    const markerDirectory = markerPath.slice(0, markerPath.lastIndexOf("/"));
    // enabled = true 表示启用注入（删除 disable 文件）；enabled = false 表示禁用（创建 disable 文件）
    const command = enabled
        ? `rm -f '${markerPath}'`
        : `mkdir -p '${markerDirectory}' && touch '${markerPath}'`;

    try {
        const result = await exec(command);
        if (result.errno !== 0) {
            throw new Error(result.stderr || `命令退出码 ${result.errno}`);
        }

        updateCount();
        const stateText = enabled ? "已开启" : "已关闭";
        const userText = users.length > 1 ? `[${getUserName(userId)}] ` : "";
        const message = `${userText}${app.label} 注入 ${stateText}`;
        liveStatus.textContent = message;
        toast(message);
    } catch (error) {
        console.error(`Failed to update ${appName} scope`, error);
        if (userId === currentUserId) {
            input.checked = !enabled;
            updateCount();
        }
        showError(`${app.label} 作用域修改失败`);
        toast("作用域修改失败");
    } finally {
        if (userId === currentUserId) {
            input.disabled = !isScopeAvailable(appName, currentUserId);
        }
    }
}

async function changeHookState(hook, appName, enabled) {
    const app = apps[appName];
    const input = getHookInput(hook.id, appName);
    const userId = currentUserId;
    input.disabled = true;
    hideError();

    const markerPath = getHookMarkerPath(userId, hook.id, appName);
    const markerDirectory = markerPath.slice(0, markerPath.lastIndexOf("/"));
    const command = enabled
        ? `rm -f '${markerPath}'`
        : `mkdir -p '${markerDirectory}' && touch '${markerPath}'`;

    try {
        const result = await exec(command);
        if (result.errno !== 0) {
            throw new Error(result.stderr || `命令退出码 ${result.errno}`);
        }

        updateCount();
        const stateText = enabled ? "已开启" : "已关闭";
        const userText = users.length > 1 ? `[${getUserName(userId)}] ` : "";
        const message = `${userText}${app.label} · ${hook.label} ${stateText}`;
        liveStatus.textContent = message;
        toast(message);
    } catch (error) {
        console.error(`Failed to update ${hook.id} state for ${appName}`, error);
        if (userId === currentUserId) {
            input.checked = !enabled;
            updateCount();
        }
        showError(`${app.label} · ${hook.label} 修改失败`);
        toast("修改失败");
    } finally {
        if (userId === currentUserId) {
            input.disabled = !isScopeAvailable(appName, currentUserId);
        }
    }
}

function getUserName(userId) {
    const user = users.find((item) => item.id === userId);
    return user ? user.name : `用户 ${userId}`;
}

async function exportLog() {
    exportButton.disabled = true;
    exportResult.hidden = true;
    exportResult.textContent = "";
    hideError();

    const stamp = new Date().toISOString().replace(/\D/g, "").slice(0, 14);
    const dest = `/sdcard/Download/tcqt_zygisk_report_${stamp}.log`;

    const logSources = Object.values(apps)
        .map((app) => `/data/user/*/${app.package}/files/.tcqt/log*.txt`)
        .join(" ");

    const command = [
        "mkdir -p /sdcard/Download || exit 1",
        "{",
        '  echo "===== 设备信息 $(date \'+%F %T\') ====="',
        '  echo "[设备] $(getprop ro.product.manufacturer) $(getprop ro.product.model) ($(getprop ro.product.device))"',
        '  echo "[系统] Android $(getprop ro.build.version.release) (SDK $(getprop ro.build.version.sdk))"',
        '  echo "[指纹] $(getprop ro.build.fingerprint)"',
        '  echo "[内核] $(uname -r)"',
        '  echo "[页大小] $(getconf PAGE_SIZE) 字节"',
        '  echo "[SELinux] $(getenforce)"',
        '  echo "[KSU] $(ksud --version 2>/dev/null || echo unknown)"',
        '  echo "[模块]"',
        "  for f in /data/adb/modules/*/module.prop; do",
        '    [ -f "$f" ] || continue',
        '    echo "  $(sed -n \'s/^name=//p\' "$f") $(sed -n \'s/^version=//p\' "$f") [$(sed -n \'s/^id=//p\' "$f")]"',
        "  done",
        '  echo "[QQ] $(dumpsys package com.tencent.mobileqq 2>/dev/null | grep -m1 versionName | sed \'s/.*versionName=//\')"',
        '  echo "[TIM] $(dumpsys package com.tencent.tim 2>/dev/null | grep -m1 versionName | sed \'s/.*versionName=//\')"',
        '  echo "===== TCQT 日志 ====="',
        `  cat ${logSources} 2>/dev/null || true`,
        `} > '${dest}'`,
        `wc -c < '${dest}'`,
    ].join("\n");

    try {
        const result = await exec(command);
        if (result.errno !== 0) {
            throw new Error(result.stderr || `命令退出码 ${result.errno}`);
        }
        const size = (result.stdout || "0").trim();
        exportResult.hidden = false;
        if (size === "0") {
            exportResult.textContent = "导出失败：未找到任何日志内容";
            toast("导出失败");
        } else {
            exportResult.textContent =
                `已导出 (${size} 字节) → Download/tcqt_zygisk_report_${stamp}.log`;
            toast("日志已导出");
        }
    } catch (error) {
        console.error("Failed to export log", error);
        exportResult.hidden = false;
        exportResult.textContent = "日志导出失败，请重试";
        showError("日志导出失败");
        toast("导出失败");
    } finally {
        exportButton.disabled = false;
    }
}

Object.keys(apps).forEach((appName) => {
    getInput(appName).addEventListener("change", (event) => {
        changeScope(appName, event.currentTarget.checked);
    });
});

renderHookCards();
exportButton.addEventListener("click", exportLog);

document.querySelectorAll(".app-icon img").forEach((image) => {
    const hideBrokenImage = () => {
        image.hidden = true;
    };

    if (image.complete && image.naturalWidth === 0) {
        hideBrokenImage();
    } else {
        image.addEventListener("error", hideBrokenImage, { once: true });
    }
});

async function reload() {
    if (!hasManager()) {
        await loadStates();
        return;
    }
    installedCache.clear();
    await loadUsers();
    await loadStates();
}

retryButton.addEventListener("click", reload);
reload();
