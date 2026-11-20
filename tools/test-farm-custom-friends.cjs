// Unit tests for the shipped adapter. No device, network or account credentials are used.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { test } = require('node:test');
const adapter = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/tcqt/farm-custom-friends.js'), 'utf8');

function runtime(sendMsg, timers = { setTimeout, clearTimeout }) {
    const entries = new Map();
    const statuses = [];
    const system = { register(id, deps, declare) { entries.set(id, { deps, declare }); return 42; } };
    const context = vm.createContext({ System: system, console: { info() {} }, qq: {
        getStorageSync(key) { statuses.push(JSON.parse(key.slice('TCQT_FARM_FRIENDS_STATUS:'.length))); },
    }, ...timers });
    vm.runInContext(adapter, context);
    const codec = { encode: data => ({ finish: () => data }), decode: data => data };
    const dependencies = {
        './mygamepb.ts': { gamepb: { friendpb: {
            GetBriefInfoRequest: codec, GetBriefInfoReply: codec,
            GetGameFriendsRequest: codec, GetGameFriendsReply: codec,
        } } },
        './Oops.ts': { oops: { netWebSocket: { sendMsg } } },
        './GlobalData.ts': { GlobalData: { selfModel: { gid: '100' } } },
    };
    function publish(moduleName, name, value, objectForm = false) {
        const id = 'chunks:///_virtual/' + moduleName + '.ts';
        const imports = Object.keys(dependencies);
        const contextValue = {};
        const output = {};
        assert.equal(system.register(id, imports, function (emit, ctx) {
            assert.equal(ctx, contextValue);
            return {
                setters: imports.map(() => () => {}),
                execute() { objectForm ? emit({ [name]: value }) : emit(name, value); },
            };
        }), 42);
        const declaration = entries.get(id).declare((key, val) => {
            if (typeof key === 'object') Object.assign(output, key);
            else output[key] = val;
            return val;
        }, contextValue);
        imports.forEach((key, i) => declaration.setters[i](dependencies[key]));
        declaration.execute();
        assert.equal(output[name], value);
        return value;
    }
    class Manager {
        constructor() { this.clientFriendList = []; this.application_count = 3; }
        reqAllFriendToServer() { assert.fail('SyncAll'); }
        convertServerFriendToClientFriend(friend) { return { gid: friend.gid, openid: friend.open_id }; }
    }
    class Platform { getFriendStorage() { assert.fail('foreground QQ query'); } }
    return { system, context, publish, Manager, Platform, statuses, dependencies };
}
const success = (callback, body) => callback({ meta: { error_code: 0 }, body });

test('circular imports may publish the manager before the protocol namespace is initialized', async () => {
    const calls = [];
    const r = runtime((body, method, callback) => {
        calls.push(method);
        success(callback, method === 'GetBriefInfo' ? { friend_gids: ['201'] } : {
            friends: [{ gid: '201', open_id: 'fixture' }],
        });
    });
    const namespace = r.dependencies['./mygamepb.ts'];
    const gamepb = namespace.gamepb;
    delete namespace.gamepb;
    r.publish('FriendManager', 'FriendManager', r.Manager);
    r.publish('QqPlatformUtil', 'QqPlatformUtil', r.Platform);
    // SystemJS completes the circular dependency later without exporting the class again.
    namespace.gamepb = gamepb;
    await new r.Platform().getFriendStorage();
    const manager = new r.Manager();
    assert.equal(await manager.reqAllFriendToServer(1), true);
    assert.equal(manager.clientFriendList.length, 1);
    assert.deepEqual(calls, ['GetBriefInfo', 'GetGameFriends']);
});

test('custom QQ discovery queries the same game connection with scene, deduplication and UI flags preserved', async () => {
    const calls = [];
    const r = runtime((body, method, callback, service) => {
        calls.push(method);
        assert.equal(service, 'gamepb.friendpb.FriendService');
        assert.equal(body.global_scene_id, 2);
        if (method === 'GetBriefInfo') success(callback, { friend_gids: ['100', '201', '202'] });
        else success(callback, { friends: [
            { gid: '100', open_id: 'self' }, { gid: '201', open_id: 'friend' },
            { gid: '201', open_id: 'friend' }, { gid: '202', open_id: '' },
        ] });
    });
    r.publish('QqPlatformUtil', 'QqPlatformUtil', r.Platform);
    r.publish('FriendManager', 'FriendManager', r.Manager);
    const manager = new r.Manager();
    manager.clientFriendList = [{ gid: '201', bPopupNew: false, bPopupOperate: true }];
    const discovery = await new r.Platform().getFriendStorage();
    assert.equal(discovery.length, 0);
    assert.equal(await manager.reqAllFriendToServer(2, discovery), true);
    assert.deepEqual(calls, ['GetBriefInfo', 'GetGameFriends']);
    assert.equal(manager.clientFriendList.length, 1);
    assert.deepEqual(manager.clientFriendList[0], { gid: '201', openid: 'friend', bPopupNew: false, bPopupOperate: true });
    assert.equal(manager.application_count, 3);
    assert.deepEqual(r.statuses.map(s => s.stage), [
        'adapter_started', 'discovery_ready', 'manager_ready', 'query_start',
        'brief_reply', 'friends_reply', 'query_complete',
    ]);
    assert.deepEqual(r.statuses.at(-1), { stage: 'query_complete', count: 1 });
    assert.ok(r.statuses.every(s => Object.keys(s).every(k => k === 'stage' || k === 'count')));
});

test('large lists use batches of 50 and commit only after the last reply', async () => {
    const ids = Array.from({ length: 103 }, (_, i) => String(i + 200));
    const sizes = [];
    const r = runtime((body, method, callback) => {
        if (method === 'GetBriefInfo') success(callback, { friend_gids: ids });
        else {
            sizes.push(body.friend_gids.length);
            assert.equal(manager.clientFriendList, old);
            success(callback, { friends: body.friend_gids.map(gid => ({ gid, open_id: 'fixture-' + gid })) });
        }
    });
    r.publish('FriendManager', 'FriendManager', r.Manager, true);
    const manager = new r.Manager(), old = manager.clientFriendList;
    await manager.reqAllFriendToServer(1);
    assert.deepEqual(sizes, [50, 50, 3]);
    assert.equal(manager.clientFriendList.length, 103);
});

test('empty server ID list completes without a detail request or an empty SyncAll', async () => {
    const calls = [];
    const r = runtime((body, method, cb) => { calls.push(method); success(cb, { friend_gids: [] }); });
    r.publish('FriendManager', 'FriendManager', r.Manager);
    const manager = new r.Manager();
    await manager.reqAllFriendToServer(1);
    assert.deepEqual(calls, ['GetBriefInfo']);
    assert.equal(manager.clientFriendList.length, 0);
});

test('failed detail response preserves previous list and never queries another identity', async () => {
    const r = runtime((body, method, callback) => {
        if (method === 'GetBriefInfo') success(callback, { friend_gids: ['201'] });
        else callback({ meta: { error_code: 1000020 }, body: {} });
    });
    r.publish('FriendManager', 'FriendManager', r.Manager);
    const manager = new r.Manager(), old = manager.clientFriendList;
    await assert.rejects(manager.reqAllFriendToServer(1), /request failed/);
    assert.equal(manager.clientFriendList, old);
    assert.equal(r.statuses.at(-1).stage, 'query_failed');
    assert.ok(!r.statuses.some(s => s.stage === 'query_complete'));
});

test('missing System reports the load boundary failure without throwing', () => {
    const stages = [];
    vm.runInNewContext(adapter, { qq: { getStorageSync(key) {
        stages.push(JSON.parse(key.slice('TCQT_FARM_FRIENDS_STATUS:'.length)).stage);
    } } });
    assert.deepEqual(stages, ['adapter_started', 'system_unavailable']);
});

test('a failing diagnostic bridge cannot break a valid game query', async () => {
    const r = runtime((body, method, callback) => success(callback, { friend_gids: [] }), {
        setTimeout, clearTimeout, qq: { getStorageSync() { throw new Error('unavailable bridge'); } },
    });
    r.publish('FriendManager', 'FriendManager', r.Manager);
    assert.equal(await new r.Manager().reqAllFriendToServer(1), true);
});

test('timeout rejects and late callbacks do not update the list', async () => {
    let timeout, callback;
    const r = runtime((body, method, cb) => { callback = cb; }, {
        setTimeout(fn) { timeout = fn; return 1; }, clearTimeout() {},
    });
    r.publish('FriendManager', 'FriendManager', r.Manager);
    const manager = new r.Manager(), old = manager.clientFriendList;
    const request = manager.reqAllFriendToServer(1);
    timeout();
    await assert.rejects(request, /timed out/);
    success(callback, { friend_gids: [] });
    assert.equal(manager.clientFriendList, old);
});

test('unsupported manager cannot fall through to SyncAll after QQ discovery fails', async () => {
    const r = runtime(() => assert.fail('RPC'));
    class Unsupported { reqAllFriendToServer() { assert.fail('SyncAll'); } }
    r.publish('QqPlatformUtil', 'QqPlatformUtil', r.Platform);
    r.publish('FriendManager', 'FriendManager', Unsupported);
    await assert.rejects(new r.Platform().getFriendStorage(), /adapter unavailable/);
    await assert.rejects(new Unsupported().reqAllFriendToServer(1, []), /adapter unavailable/);
});

test('unrelated and anonymous registrations keep their behavior; repeated loading is idempotent', () => {
    const r = runtime(() => assert.fail('RPC'));
    class Other { reqAllFriendToServer() { return 'original'; } }
    r.publish('Other', 'FriendManager', Other);
    r.publish('FriendManager', 'AnotherExport', Other);
    assert.equal(new Other().reqAllFriendToServer(), 'original');
    const register = r.system.register;
    vm.runInContext(adapter, r.context);
    assert.equal(r.system.register, register);
    const anonymous = () => ({});
    let received;
    const system = { register(...args) { received = args; } };
    vm.runInNewContext(adapter, { System: system });
    system.register([], anonymous);
    assert.equal(received[1], anonymous);
    vm.runInNewContext(adapter, {});
    vm.runInNewContext(adapter, { System: {} });
});
