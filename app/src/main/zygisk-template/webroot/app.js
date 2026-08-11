import { exec, toast } from "./kernelsu.js";

const DATA_DIRECTORY = "/data/adb/tcqt";

const apps = {
    qq: { label: "QQ", package: "com.tencent.mobileqq", marker: "qq.disable" },
    tim: { label: "TIM", package: "com.tencent.tim", marker: "tim.disable" },
};

const appList = document.querySelector("#app-list");
const heroSummary = document.querySelector("#summary-badge");
const errorPanel = document.querySelector("#error-panel");
const errorMessage = document.querySelector("#error-message");
const retryButton = document.querySelector("#retry-button");
const liveStatus = document.querySelector("#live-status");
const userTabs = document.querySelector("#user-tabs");

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

function hasManager() {
    return window.ksu && typeof window.ksu.exec === "function";
}

function markerPath(userId, marker) {
    return userId === 0
        ? `${DATA_DIRECTORY}/${marker}`
        : `${DATA_DIRECTORY}/user_${userId}/${marker}`;
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
    heroSummary.textContent = `已启用 ${enabled}/${installedNames.length}`;
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
    setTabsDisabled(true);
    appList.setAttribute("aria-busy", "true");
    heroSummary.textContent = "读取中…";

    if (!hasManager()) {
        showError("请在支持模块 WebUI 的管理器（KernelSU / APatch）中打开此页面");
        heroSummary.textContent = "不可用";
        appList.setAttribute("aria-busy", "false");
        return;
    }

    const userId = currentUserId;
    const checks = Object.values(apps)
        .map(({ marker }) => `[ -f '${markerPath(userId, marker)}' ] && printf '1\\n' || printf '0\\n'`)
        .join("; ");

    try {
        await loadInstalledPackages(userId);
        const result = await exec(checks);
        const states = (result.stdout || "").trim().split(/\s+/);
        if (result.errno !== 0 || states.length !== Object.keys(apps).length) {
            throw new Error(result.stderr || "状态数据不完整");
        }
        if (userId !== currentUserId) return; // 期间已切换用户，丢弃过期结果

        Object.keys(apps).forEach((appName, index) => {
            appInput(appName).checked = states[index] !== "1";
        });
        renderBadges();
        renderSummary();
        setInputsDisabled(false);
    } catch (error) {
        console.error("Failed to load TCQT scopes", error);
        if (userId !== currentUserId) return;
        showError("读取状态失败，请稍后重试");
        heroSummary.textContent = "读取失败";
    } finally {
        if (userId === currentUserId) {
            setTabsDisabled(false);
            appList.setAttribute("aria-busy", "false");
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

for (const appName of Object.keys(apps)) {
    appInput(appName).addEventListener("change", (event) => {
        changeScope(appName, event.currentTarget.checked);
    });
}

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
