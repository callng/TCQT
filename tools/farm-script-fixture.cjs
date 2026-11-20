// Execute local official Cocos modules with inert engine/host services for protocol tests.
// Fixtures stay outside source control. No network, device or filesystem APIs are exposed to JS.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const prefix = 'chunks:///_virtual/';
const noop = () => {};

function setup({ fixture, selfId = 100, deferProtocol = false } = {}) {
    const sub = fs.readFileSync(path.join(fixture, 'subContext.js'), 'utf8');
    const start = sub.indexOf('define("openDataContext/index.js"');
    const end = sub.lastIndexOf('});  require("openDataContext/index.js")');
    assert.ok(start >= 0 && end > start);
    const body = sub.slice(sub.indexOf('{', start) + 1, end);
    const ctx = vm.createContext({ console: { log: noop, info: noop, error: noop }, setTimeout, clearTimeout });
    // Initialize the official obfuscation helper; stop before the open-data UI needs an engine.
    vm.runInContext(`(function(){
        function require(){ return function(){ return {}; }; }
        const module={exports:{}}, exports=module.exports, qq={}, wx={};
        try { ${body} } catch(e) {}
    })()`, ctx, { timeout: 5000 });
    const definitions = new Map(), registrations = new Map();
    ctx.define = (id, factory) => definitions.set(id, factory);
    ctx.require = noop;
    const script = path.join(__dirname, '../app/build/outputs/farm-custom-friends/game.patched.js');
    vm.runInContext(fs.readFileSync(script, 'utf8'), ctx, { timeout: 5000 });
    ctx.System = { register(...args) { registrations.set(args[0], args); } };
    const cache = new Map();
    function commonjs(id, from = '') {
        id = path.posix.normalize(path.posix.join(path.posix.dirname(from), id));
        if (!definitions.has(id) && definitions.has(id + '.js')) id += '.js';
        if (cache.has(id)) return cache.get(id).exports;
        const module = { exports: {} };
        cache.set(id, module);
        const factory = definitions.get(id);
        assert.ok(factory, 'missing CommonJS fixture module: ' + id);
        factory(dep => commonjs(dep, id), module, module.exports);
        return module.exports;
    }
    commonjs('./' + [...definitions.keys()].find(id => /^assets\/main\/index\./.test(id)));
    assert.equal(ctx.System.__tcqtFarmFriendsV1, true, 'production source transformer inserted the adapter');
    commonjs('./' + [...definitions.keys()].find(id => /^src\/chunks\/bundle\./.test(id)));
    const anonymous = [...registrations.values()].find(args => typeof args[0] !== 'string');
    anonymous[1](noop, {}).execute();
    ctx.logger = { log: noop, info: noop, warn: noop, error: noop };
    ctx.window = {};
    const cc = { cclegacy: { _RF: { push: noop, pop: noop } }, _decorator: { ccclass: () => value => value } };
    const outputs = new Map([['cc', cc]]), subscribers = new Map();
    const resolve = id => id.startsWith('./') ? prefix + id.slice(2) : id;
    function esm(id) {
        id = resolve(id);
        if (outputs.has(id)) return outputs.get(id);
        const entry = registrations.get(id);
        assert.ok(entry, 'missing System fixture module: ' + id);
        const output = {};
        outputs.set(id, output);
        const declaration = entry[2]((key, value) => {
            if (typeof key === 'object') Object.assign(output, key);
            else output[key] = value;
            for (const subscriber of subscribers.get(id) || []) subscriber(output);
            return value;
        }, { meta: { url: id } });
        entry[1].forEach((dependency, index) => {
            const target = resolve(dependency), setter = declaration.setters?.[index];
            if (setter) {
                if (!subscribers.has(target)) subscribers.set(target, []);
                subscribers.get(target).push(setter);
            }
            const value = esm(target);
            // Model this import edge waiting on a cycle while other modules are linked.
            setter?.(deferProtocol && id === prefix + 'FriendManager.ts' && target === prefix + 'mygamepb.ts' ? {} : value);
        });
        declaration.execute();
        return output;
    }
    const proto = esm('./mygamepb.ts');
    const put = (name, value) => outputs.set(prefix + name, value);
    const oops = {
        netWebSocket: { sendMsg() { throw new Error('unconfigured fixture RPC'); } },
        message: { on: noop, off: noop, dispatchEvent: noop },
    };
    put('GlobalData.ts', { GlobalData: { selfModel: { gid: selfId, openId: 'fixture-self' } } });
    put('Oops.ts', { oops });
    put('BaseSingleton.ts', { BaseSingleton: function () {} });
    put('WxPlatformUtil.ts', { WxPlatformUtil: function () {} });
    const actual = new Set([
        './FriendManager.ts', './rollupPluginModLoBabelHelpers.js', './GlobalEnum.ts',
        './ServiceMethodName.ts', './ArrayUtil.ts', './FriendVO.ts', './FriendEnum.ts',
    ]);
    // Only the two classes, their codecs, value converters, enums and helpers execute.
    for (const name of ['FriendManager.ts', 'QqPlatformUtil.ts']) {
        for (const dependency of registrations.get(prefix + name)[1]) {
            if (!dependency.startsWith('./') || outputs.has(resolve(dependency)) || actual.has(dependency)) continue;
            put(dependency.slice(2), {});
        }
    }
    put('WeatherStatusVO.ts', { WeatherStatusVO: function () { this.parseFromPb = noop; } });
    const Manager = esm('./FriendManager.ts').FriendManager;
    const Platform = esm('./QqPlatformUtil.ts').QqPlatformUtil;
    if (deferProtocol) {
        outputs.set(prefix + 'mygamepb.ts', proto);
        for (const subscriber of subscribers.get(prefix + 'mygamepb.ts') || []) subscriber(proto);
    }
    assert.equal(typeof Manager, 'function');
    assert.equal(typeof Platform, 'function');
    const manager = Object.create(Manager.prototype);
    Object.defineProperty(Manager, 'ins', { value: manager });
    manager.clientFriendList = [];
    manager.blockList = [];
    manager.blockedOpenIdsFromFriendRpc = [];
    manager.application_count = 2;
    const platform = Object.create(Platform.prototype);
    oops.plat = platform;
    return { manager, platform, oops, proto, friend: proto.gamepb.friendpb, GameBytes: vm.runInContext('Uint8Array', ctx) };
}
module.exports = { setup };
