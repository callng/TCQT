import { exec } from "./kernelsu.js";

const DATA = "/data/adb/tcqt";
const PACKAGES = { qq: "com.tencent.mobileqq", tim: "com.tencent.tim" };
const state = {
    users: [{ id: 0, name: "主用户" }], userId: 0, busy: true, ready: false,
    installed: [], qq: false, tim: false, qqHook: false, timHook: false,
    compat: false, debug: false, error: "", message: "", sharing: false, openingLink: false,
};
const listeners = new Set();
const emit = () => listeners.forEach((listener) => listener());
const quote = (value) => "'" + String(value).replaceAll("'", "'\\''") + "'";
const managerAvailable = () => typeof window.ksu?.exec === "function";
const marker = (name) => state.userId === 0 ? `${DATA}/${name}` : `${DATA}/user_${state.userId}/${name}`;

async function run(command) {
    const result = await exec(command);
    if (Number(result.errno) !== 0) throw new Error(result.stderr || `命令退出码 ${result.errno}`);
    return result.stdout || "";
}

async function reload() {
    if (state.busy && state.ready) return;
    state.busy = true;
    state.ready = false;
    state.error = "";
    emit();
    try {
        if (!managerAvailable()) throw new Error("请在 KernelSU / APatch 的模块 WebUI 中打开此页面");
        const output = await run("pm list users");
        const users = [...output.matchAll(/UserInfo\{(\d+):([^:}]*)/g)]
            .map((match) => ({ id: Number(match[1]), name: match[2].trim() || `用户 ${match[1]}` }));
        if (users.length) state.users = users.sort((a, b) => a.id - b.id);
        if (!state.users.some((user) => user.id === state.userId)) state.userId = state.users[0].id;
        const packages = await run(`pm list packages --user ${state.userId}`);
        state.installed = Object.keys(PACKAGES).filter((app) => packages.split(/\r?\n/).includes(`package:${PACKAGES[app]}`));
        const paths = [marker("qq.disable"), marker("tim.disable"), marker("qq.key1.disable"), marker("tim.key1.disable"), `${DATA}/compat.enable`, `${DATA}/debug.enable`];
        const flags = (await run(paths.map((path) => `if [ -f ${quote(path)} ]; then echo 1; else echo 0; fi`).join("\n"))).trim().split(/\s+/);
        if (flags.length !== 6 || flags.some((flag) => !/^[01]$/.test(flag))) throw new Error("状态数据不完整，请重试");
        [state.qq, state.tim, state.qqHook, state.timHook] = flags.slice(0, 4).map((flag) => flag === "0");
        state.compat = flags[4] === "1";
        state.debug = flags[5] === "1";
        state.ready = true;
    } catch (error) {
        state.error = error.message || "读取失败，请重试";
    } finally { state.busy = false; emit(); }
}

async function toggle(key, enabled) {
    if (state.busy || !state.ready || state.sharing) return;
    const paths = { qq: marker("qq.disable"), tim: marker("tim.disable"), qqHook: marker("qq.key1.disable"), timHook: marker("tim.key1.disable"), compat: `${DATA}/compat.enable`, debug: `${DATA}/debug.enable` };
    if (!(key in paths) || typeof enabled !== "boolean") return;
    const path = paths[key];
    const create = ["compat", "debug"].includes(key) ? enabled : !enabled;
    state.busy = true;
    state.error = "";
    emit();
    try {
        await run(create ? `mkdir -p ${quote(path.slice(0, path.lastIndexOf("/")))} && touch ${quote(path)}` : `rm -f ${quote(path)}`);
        state[key] = enabled;
        state.message = "已保存，下次启动对应应用时生效";
    } catch (error) { state.error = `保存失败：${error.message}`; }
    finally { state.busy = false; emit(); }
}

// Report collection stays in the module, keeping the root shell out of the UI layer.
async function share() {
    if (state.sharing || state.busy || !state.ready) return;
    state.sharing = true;
    state.error = "";
    state.message = "正在整理日志…";
    emit();
    try {
        const output = await run("sh /data/adb/modules/zygisk_tcqt/share-logs.sh");
        if (!output.includes("TCQT_SHARE_OK")) throw new Error("系统分享入口未确认打开");
        state.message = "已打开系统分享面板，请选择接收应用";
    } catch (error) { state.error = `分享失败：${error.message}`; state.message = "可重试分享；无需到文件管理器查找日志"; }
    finally { state.sharing = false; emit(); }
}

async function openLink(key) {
    if (state.openingLink || !["source", "channel", "group"].includes(key)) return;
    state.openingLink = true;
    state.error = "";
    emit();
    try {
        const output = await run(`sh /data/adb/modules/zygisk_tcqt/open-link.sh ${quote(key)}`);
        if (!output.includes("TCQT_BROWSER_OK")) throw new Error("浏览器未能启动");
    } catch (error) { state.error = `打开失败：${error.message}`; }
    finally { state.openingLink = false; emit(); }
}

window.tcqt = {
    snapshot: () => JSON.stringify(state),
    subscribe: (listener) => { listeners.add(listener); return () => listeners.delete(listener); },
    reload, toggle, share, openLink,
    selectUser: (id) => {
        if (!state.busy && !state.sharing && state.users.some((user) => user.id === id)) { state.userId = id; reload(); }
    },
};

reload();
