import { exec, toast } from "./kernelsu.js";

const DATA_DIRECTORY = "/data/adb/tcqt";

const apps = {
    qq: { label: "QQ", package: "com.tencent.mobileqq", marker: "qq.disable" },
    tim: { label: "TIM", package: "com.tencent.tim", marker: "tim.disable" },
};

const SHIELD_ICON =
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" ' +
    'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    '<path d="M12 3l7 2.8v5.4c0 4.6-3 8.3-7 9.8-4-1.5-7-5.2-7-9.8V5.8z"/>' +
    '<path d="M9.4 12l1.8 1.8 3.4-3.8"/>' +
    "</svg>";

const hooks = [
    {
        id: "key1",
        label: "fopen 重定向",
        detail: "一般情况下默认就好~",
        icon: SHIELD_ICON,
        desc: "对 /proc/self/smaps 的 fopen 调用 重定向到 /dev/null",
    },
];

const appList = document.querySelector("#app-list");
const heroSummary = document.querySelector("#summary-badge");
const errorPanel = document.querySelector("#error-panel");
const errorMessage = document.querySelector("#error-message");
const retryButton = document.querySelector("#retry-button");
const liveStatus = document.querySelector("#live-status");
const userTabs = document.querySelector("#user-tabs");
const exportButton = document.querySelector("#export-log-button");
const exportResult = document.querySelector("#export-log-result");
const hookList = document.querySelector("#hook-list");

const PRIMARY_USER = { id: 0, name: "主用户" };
let users = [PRIMARY_USER];
let currentUserId = 0;
// userId -> Set(已安装包名)；null 表示无法确定
const installedCache = new Map();

function appCard(appName) {
    return document.querySelector(`[data-app="${appName}"]`);
}

function appInput(appName) {
    return appCard(appName).querySelector("input");
}

function appBadge(appName) {
    return appCard(appName).querySelector("[data-state-role=badge]");
}

function hookCard(hookId) {
    return document.querySelector(`[data-hook="${hookId}"]`);
}

function hookTargetRow(hookId, appName) {
    return hookCard(hookId).querySelector(`[data-target="${appName}"]`);
}

function hookInput(hookId, appName) {
    return hookTargetRow(hookId, appName).querySelector("input");
}

function hookBadge(hookId, appName) {
    return hookTargetRow(hookId, appName).querySelector("[data-state-role=hook-badge]");
}

function hookMarkerPath(userId, hookId, appName) {
    return markerPath(userId, `${appName}.${hookId}.disable`);
}

function hasManager() {
    return window.ksu && typeof window.ksu.exec === "function";
}

function markerPath(userId, marker) {
    return userId === 0
        ? `${DATA_DIRECTORY}/${marker}`
        : `${DATA_DIRECTORY}/user_${userId}/${marker}`;
}

function renderHookCards() {
    hookList.textContent = "";
    for (const hook of hooks) {
        const item = document.createElement("div");
        item.className = "hook-item";

        const card = document.createElement("div");
        card.className = "app-card hook-card";
        card.dataset.hook = hook.id;

        const header = document.createElement("div");
        header.className = "hook-header";
        header.innerHTML = `
            <div class="app-icon app-icon-hook" aria-hidden="true">${hook.icon}</div>
            <div class="app-info">
                <strong>${hook.label}</strong>
                <small>${hook.detail}</small>
            </div>`;

        const targets = document.createElement("div");
        targets.className = "hook-targets";
        for (const appName of Object.keys(apps)) {
            const app = apps[appName];
            const row = document.createElement("div");
            row.className = "hook-target";
            row.dataset.target = appName;
            row.innerHTML = `
                <span class="hook-target-icon app-icon app-icon-${appName}" aria-hidden="true">
                    <img src="ksu://icon/${app.package}" alt="">
                    <span class="icon-fallback">${appName === "qq" ? "Q" : "T"}</span>
                </span>
                <span class="hook-target-name">${app.label}</span>
                <span class="app-state" data-state-role="hook-badge">…</span>
                <label class="switch switch-sm">
                    <input type="checkbox" aria-label="${app.label} · ${hook.label}">
                    <span class="switch-track" aria-hidden="true"></span>
                </label>`;
            row.querySelector("input").addEventListener("change", (event) => {
                changeHookState(hook, appName, event.currentTarget.checked);
            });
            targets.append(row);
        }

        const desc = document.createElement("p");
        desc.className = "hook-desc";
        desc.textContent = hook.desc;

        card.append(header, targets);
        item.append(card, desc);
        hookList.append(item);
    }
}

function setHookInputsDisabled(disabled) {
    for (const hook of hooks) {
        for (const appName of Object.keys(apps)) {
            hookInput(hook.id, appName).disabled =
                disabled || !isInstalled(appName, currentUserId);
        }
    }
}

function renderHookBadges() {
    for (const hook of hooks) {
        for (const appName of Object.keys(apps)) {
            const row = hookTargetRow(hook.id, appName);
            const input = hookInput(hook.id, appName);
            const badge = hookBadge(hook.id, appName);
            const available = isInstalled(appName, currentUserId);
            row.classList.toggle("is-unavailable", !available);
            if (!available) {
                input.checked = false;
                badge.textContent = "未安装";
                badge.className = "app-state state-off";
            } else if (input.checked) {
                badge.textContent = "已启用";
                badge.className = "app-state state-on";
            } else {
                badge.textContent = "已停用";
                badge.className = "app-state state-off";
            }
        }
    }
}

function isInstalled(appName, userId) {
    const installed = installedCache.get(userId);
    if (!installed) return true;
    return installed.has(apps[appName].package);
}

function setInputsDisabled(disabled) {
    for (const appName of Object.keys(apps)) {
        appInput(appName).disabled =
            disabled || !isInstalled(appName, currentUserId);
    }
}

function setTabsDisabled(disabled) {
    userTabs.querySelectorAll("button").forEach((tab) => {
        tab.disabled = disabled;
    });
}

function renderBadges() {
    for (const appName of Object.keys(apps)) {
        const card = appCard(appName);
        const available = isInstalled(appName, currentUserId);
        card.classList.toggle("is-unavailable", !available);
        const input = appInput(appName);
        const badge = appBadge(appName);
        if (!available) {
            input.checked = false;
            badge.textContent = "未安装";
            badge.className = "app-state state-off";
        } else if (input.checked) {
            badge.textContent = "已启用";
            badge.className = "app-state state-on";
        } else {
            badge.textContent = "已停用";
            badge.className = "app-state state-off";
        }
    }
}

function renderSummary() {
    const names = Object.keys(apps);
    const installedNames = names.filter((name) => isInstalled(name, currentUserId));
    const enabled = installedNames.filter((name) => appInput(name).checked).length;
    let hooksEnabled = 0;
    for (const hook of hooks) {
        for (const appName of installedNames) {
            if (hookInput(hook.id, appName).checked) hooksEnabled++;
        }
    }
    const hooksTotal = hooks.length * installedNames.length;
    heroSummary.textContent =
        `注入 ${enabled}/${installedNames.length} · Hook ${hooksEnabled}/${hooksTotal}`;
}

function showError(message) {
    errorMessage.textContent = message;
    errorPanel.hidden = false;
}

function hideError() {
    errorPanel.hidden = true;
}

function getUserName(userId) {
    const user = users.find((item) => item.id === userId);
    return user ? user.name : `用户 ${userId}`;
}

function parseUsers(output) {
    const parsed = [];
    const pattern = /UserInfo\{(\d+):([^:}]*)/g;
    let match;
    while ((match = pattern.exec(output)) !== null) {
        const id = Number.parseInt(match[1], 10);
        if (Number.isNaN(id) || parsed.some((item) => item.id === id)) continue;
        parsed.push({ id, name: match[2].trim() || `用户 ${id}` });
    }
    return parsed.sort((a, b) => a.id - b.id);
}

async function loadUsers() {
    let parsed = [];
    try {
        const result = await exec("pm list users 2>/dev/null");
        if (result.errno === 0) parsed = parseUsers(result.stdout || "");
    } catch (error) {
        console.error("Failed to list users", error);
    }
    users = parsed.length > 0 ? parsed : [PRIMARY_USER];
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
    for (const user of users) {
        const tab = document.createElement("button");
        tab.type = "button";
        tab.className = "user-tab";
        tab.dataset.userId = String(user.id);
        tab.textContent = user.name;
        tab.title = `${user.name} (ID ${user.id})`;
        tab.setAttribute("role", "tab");
        tab.setAttribute("aria-selected", String(user.id === currentUserId));
        tab.addEventListener("click", () => switchUser(user.id));
        userTabs.append(tab);
    }
    userTabs.hidden = false;
}

function updateTabSelection() {
    userTabs.querySelectorAll("button").forEach((tab) => {
        tab.setAttribute(
            "aria-selected",
            String(Number(tab.dataset.userId) === currentUserId)
        );
    });
}

async function switchUser(userId) {
    if (userId === currentUserId) return;
    currentUserId = userId;
    updateTabSelection();
    await loadStates();
}

async function loadInstalledPackages(userId) {
    if (installedCache.has(userId)) return installedCache.get(userId);
    let installed = null;
    try {
        const result = await exec(`pm list packages --user ${userId} 2>/dev/null`);
        if (result.errno === 0 && result.stdout && result.stdout.includes("package:")) {
            installed = new Set();
            for (const line of result.stdout.split("\n")) {
                const match = line.trim().match(/^package:(\S+)/);
                if (match) installed.add(match[1]);
            }
        }
    } catch (error) {
        console.error(`Failed to list packages for user ${userId}`, error);
    }
    installedCache.set(userId, installed);
    return installed;
}

async function loadStates() {
    hideError();
    setInputsDisabled(true);
    setHookInputsDisabled(true);
    setTabsDisabled(true);
    appList.setAttribute("aria-busy", "true");
    hookList.setAttribute("aria-busy", "true");
    heroSummary.textContent = "读取中…";

    if (!hasManager()) {
        showError("请在支持模块 WebUI 的管理器（KernelSU / APatch）中打开此页面");
        heroSummary.textContent = "不可用";
        appList.setAttribute("aria-busy", "false");
        hookList.setAttribute("aria-busy", "false");
        return;
    }

    const userId = currentUserId;
    const appChecks = Object.values(apps)
        .map(({ marker }) => `[ -f '${markerPath(userId, marker)}' ] && printf '1\\n' || printf '0\\n'`)
        .join("; ");
    const hookChecks = [];
    for (const hook of hooks) {
        for (const appName of Object.keys(apps)) {
            hookChecks.push(
                `[ -f '${hookMarkerPath(userId, hook.id, appName)}' ] && printf '1\\n' || printf '0\\n'`
            );
        }
    }
    const checks = [appChecks, ...hookChecks].filter(Boolean).join("; ");
    const total = Object.keys(apps).length + hooks.length * Object.keys(apps).length;

    try {
        await loadInstalledPackages(userId);
        const result = await exec(checks);
        const states = (result.stdout || "").trim().split(/\s+/);
        if (result.errno !== 0 || states.length !== total) {
            throw new Error(result.stderr || "状态数据不完整");
        }
        if (userId !== currentUserId) return; // 期间已切换用户，丢弃过期结果

        Object.keys(apps).forEach((appName, index) => {
            appInput(appName).checked = states[index] !== "1";
        });
        let hookIndex = Object.keys(apps).length;
        for (const hook of hooks) {
            for (const appName of Object.keys(apps)) {
                hookInput(hook.id, appName).checked = states[hookIndex] !== "1";
                hookIndex++;
            }
        }
        renderBadges();
        renderHookBadges();
        renderSummary();
        setInputsDisabled(false);
        setHookInputsDisabled(false);
    } catch (error) {
        console.error("Failed to load TCQT scopes", error);
        if (userId !== currentUserId) return;
        showError("读取状态失败，请稍后重试");
        heroSummary.textContent = "读取失败";
    } finally {
        if (userId === currentUserId) {
            setTabsDisabled(false);
            appList.setAttribute("aria-busy", "false");
            hookList.setAttribute("aria-busy", "false");
        }
    }
}

async function changeScope(appName, enabled) {
    const input = appInput(appName);
    const app = apps[appName];
    const userId = currentUserId;
    input.disabled = true;
    hideError();

    const path = markerPath(userId, app.marker);
    const dir = path.slice(0, path.lastIndexOf("/"));
    // enabled = 注入开启 = 删除停用标记
    const command = enabled
        ? `rm -f '${path}'`
        : `mkdir -p '${dir}' && touch '${path}'`;

    try {
        const result = await exec(command);
        if (result.errno !== 0) {
            throw new Error(result.stderr || `命令退出码 ${result.errno}`);
        }
        renderBadges();
        renderSummary();
        const stateText = enabled ? "已启用" : "已停用";
        const userText = users.length > 1 ? `[${getUserName(userId)}] ` : "";
        const message = `${userText}${app.label} ${stateText}`;
        liveStatus.textContent = message;
        toast(message);
    } catch (error) {
        console.error(`Failed to update ${appName} scope`, error);
        if (userId === currentUserId) {
            input.checked = !enabled;
            renderBadges();
            renderSummary();
        }
        showError(`${app.label} 修改失败，请重试`);
        toast("修改失败");
    } finally {
        if (userId === currentUserId) {
            input.disabled = !isInstalled(appName, currentUserId);
        }
    }
}

async function changeHookState(hook, appName, enabled) {
    const app = apps[appName];
    const input = hookInput(hook.id, appName);
    const userId = currentUserId;
    input.disabled = true;
    hideError();

    const path = hookMarkerPath(userId, hook.id, appName);
    const dir = path.slice(0, path.lastIndexOf("/"));
    const command = enabled
        ? `rm -f '${path}'`
        : `mkdir -p '${dir}' && touch '${path}'`;

    try {
        const result = await exec(command);
        if (result.errno !== 0) {
            throw new Error(result.stderr || `命令退出码 ${result.errno}`);
        }
        renderHookBadges();
        renderSummary();
        const stateText = enabled ? "已启用" : "已停用";
        const userText = users.length > 1 ? `[${getUserName(userId)}] ` : "";
        const message = `${userText}${app.label} · ${hook.label} ${stateText}`;
        liveStatus.textContent = message;
        toast(message);
    } catch (error) {
        console.error(`Failed to update ${hook.id} state for ${appName}`, error);
        if (userId === currentUserId) {
            input.checked = !enabled;
            renderHookBadges();
            renderSummary();
        }
        showError(`${app.label} · ${hook.label} 修改失败，请重试`);
        toast("修改失败");
    } finally {
        if (userId === currentUserId) {
            input.disabled = !isInstalled(appName, currentUserId);
        }
    }
}

for (const appName of Object.keys(apps)) {
    appInput(appName).addEventListener("change", (event) => {
        changeScope(appName, event.currentTarget.checked);
    });
}

renderHookCards();

async function exportLog() {
    exportButton.disabled = true;
    exportResult.textContent = "";
    hideError();

    const stamp = new Date().toISOString().replace(/\D/g, "").slice(0, 14);
    const dest = `/sdcard/Download/tcqt_zygisk_report_${stamp}.log`;

    // 多用户下日志位于 /data/user/<id>/<pkg>/files/.tcqt/log.txt，用 glob 合并
    // log*.txt 同时覆盖轮转出的上一代 log.1.txt
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
        if (size === "0") {
            exportResult.textContent = "导出失败：未生成任何内容";
            toast("导出失败");
        } else {
            exportResult.textContent =
                `已导出 ${size} 字节 → Download/tcqt_zygisk_report_${stamp}.log，请发送给开发者`;
            toast("日志已导出");
        }
    } catch (error) {
        console.error("Failed to export log", error);
        showError("日志导出失败，请重试");
        toast("导出失败");
    } finally {
        exportButton.disabled = false;
    }
}

exportButton.addEventListener("click", exportLog);

// ksu://icon 加载失败时回退到字母占位
document.querySelectorAll(".app-icon img").forEach((image) => {
    const fallback = () => {
        image.hidden = true;
    };
    if (image.complete && image.naturalWidth === 0) {
        fallback();
    } else {
        image.addEventListener("error", fallback, { once: true });
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
