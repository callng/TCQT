import "./app.js";
import { enableEdgeToEdge } from "./kernelsu.js";

enableEdgeToEdge();

const bridge = window.tcqt;
const byId = (id) => document.getElementById(id);
const switches = [...document.querySelectorAll("input[data-key]")];
const users = byId("users");
let usersSignature = "";

function render() {
    const state = JSON.parse(bridge.snapshot());
    const disabled = state.busy || state.sharing || !state.ready;
    for (const input of switches) {
        input.checked = state.ready && state[input.dataset.key];
        input.disabled = disabled;
    }
    const signature = JSON.stringify(state.users);
    if (signature !== usersSignature) {
        users.replaceChildren(...state.users.map((user) => {
            const option = document.createElement("option");
            option.value = String(user.id);
            option.textContent = `${user.name} · ${user.id}`;
            return option;
        }));
        usersSignature = signature;
    }
    users.value = String(state.userId);
    users.disabled = disabled;
    byId("users-section").hidden = state.users.length < 2;
    for (const app of ["qq", "tim"]) {
        const installed = state.ready && state.installed.includes(app);
        byId(`${app}-status`).textContent = state.ready ? (installed ? "已安装" : "未安装") : "尚未读取";
        byId(`${app}-status`).classList.toggle("available", installed);
    }
    byId("share").disabled = disabled;
    byId("share-label").textContent = state.sharing ? "正在整理…" : "导出日志";
    byId("reload").disabled = state.busy || state.sharing;
    byId("reload").setAttribute("aria-label", state.busy ? "正在读取或保存状态" : "刷新状态");
    for (const button of document.querySelectorAll("button[data-link]")) button.disabled = state.openingLink;
    byId("error").textContent = state.error;
    byId("error").hidden = !state.error;
}

for (const input of switches) {
    input.addEventListener("change", () => {
        const enabled = input.checked;
        // Display the last confirmed value until the write succeeds.
        bridge.toggle(input.dataset.key, enabled);
        render();
    });
}
users.addEventListener("change", () => bridge.selectUser(Number(users.value)));
byId("share").addEventListener("click", () => bridge.share());
byId("reload").addEventListener("click", () => bridge.reload());
for (const button of document.querySelectorAll("button[data-link]")) {
    button.addEventListener("click", () => bridge.openLink(button.dataset.link));
}
bridge.subscribe(render);
render();
