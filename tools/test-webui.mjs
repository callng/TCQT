// Run: node --experimental-vm-modules tools/test-webui.mjs
// Exercises manager failures and per-user marker semantics without a root shell.
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";

const commands = [];
let failure = false;
const context = vm.createContext({
    console,
    window: { ksu: { exec() {} } },
    document: { createElement: () => ({}), body: { append() {} } },
});
const manager = new vm.SyntheticModule(["exec"], function () {
    this.setExport("exec", async (command) => {
        commands.push(command);
        if (failure) return { errno: 1, stderr: "denied" };
        let stdout = "";
        if (command === "pm list users") stdout = "UserInfo{0:主用户:13}\nUserInfo{10:工作用户:30}";
        else if (command.startsWith("pm list packages")) stdout = "package:com.tencent.mobileqq\npackage:com.tencent.tim";
        else if (command.startsWith("if [ -f")) stdout = "0\n1\n1\n0\n1\n0\n";
        else if (command.includes("/open-link.sh ")) stdout = "TCQT_BROWSER_OK";
        else if (command.startsWith("sh ")) stdout = "TCQT_SHARE_OK content://media/external/downloads/42";
        return { errno: 0, stdout };
    });
}, { context });
const app = new vm.SourceTextModule(await readFile(new URL("../app/src/main/zygisk-template/webroot/app.js", import.meta.url), "utf8"), { context });
await app.link(() => manager);
await app.evaluate();
const bridge = context.window.tcqt;
const snapshot = () => JSON.parse(bridge.snapshot());
for (let i = 0; i < 10 && snapshot().busy; i++) await new Promise(setImmediate);
assert.equal(snapshot().ready, true);
assert.equal(snapshot().qq, true);
assert.equal(snapshot().tim, false);
assert.equal(snapshot().qqHook, false);
assert.equal(snapshot().compat, true);
await bridge.toggle("qq", false);
assert.match(commands.at(-1), /touch '\/data\/adb\/tcqt\/qq\.disable'/);
assert.equal(snapshot().qq, false);
failure = true;
await bridge.toggle("qq", true);
assert.equal(snapshot().qq, false, "failed write must not claim enabled");
assert.match(snapshot().error, /denied/);
failure = false;
bridge.selectUser(10);
for (let i = 0; i < 10 && snapshot().busy; i++) await new Promise(setImmediate);
await bridge.toggle("qqHook", true);
assert.equal(commands.at(-1), "rm -f '/data/adb/tcqt/user_10/qq.key1.disable'");
await bridge.toggle("debug", true);
assert.match(commands.at(-1), /touch '\/data\/adb\/tcqt\/debug\.enable'/);
await bridge.share();
assert.equal(commands.at(-1), "sh /data/adb/modules/zygisk_tcqt/share-logs.sh");
assert.match(snapshot().message, /系统分享面板/);
failure = true;
await bridge.share();
assert.match(snapshot().error, /分享失败/);
assert.equal(snapshot().sharing, false);
failure = false;
for (const key of ["source", "channel", "group"]) {
    await bridge.openLink(key);
    assert.equal(commands.at(-1), `sh /data/adb/modules/zygisk_tcqt/open-link.sh '${key}'`);
    assert.equal(snapshot().openingLink, false);
    assert.equal(snapshot().error, "");
}
failure = true;
await bridge.openLink("channel");
assert.match(snapshot().error, /打开失败.*denied/);
assert.equal(snapshot().openingLink, false);
failure = false;
const count = commands.length;
bridge.selectUser(999);
await bridge.toggle("unknown", true);
await bridge.openLink("source'; touch /data/local/tmp/unexpected");
assert.equal(commands.length, count);
// Exercise the actual manager adapter: edge-to-edge is optional on older managers.
const adapterContext = vm.createContext({ window: {} });
const adapter = new vm.SourceTextModule(await readFile(new URL("../app/src/main/zygisk-template/webroot/kernelsu.js", import.meta.url), "utf8"), { context: adapterContext });
await adapter.link(() => { throw new Error("Unexpected dependency"); });
await adapter.evaluate();
assert.equal(adapter.namespace.enableEdgeToEdge(), false);
const edgeCalls = [];
adapterContext.window.ksu = { enableEdgeToEdge: (enable) => edgeCalls.push(enable) };
assert.equal(adapter.namespace.enableEdgeToEdge(), true);
assert.deepEqual(edgeCalls, [true]);
adapterContext.window.ksu.enableEdgeToEdge = () => { throw new Error("unsupported"); };
assert.equal(adapter.namespace.enableEdgeToEdge(), false);
console.log("PASS: state, scope isolation, rollback, sharing, browser dispatch/failure, edge-to-edge compatibility");
