// Optional integration tests. First run FarmCustomFriendsScriptTest with TCQT_FARM_GAME_DIR.
const assert = require('node:assert/strict');
const { test } = require('node:test');
const { setup } = require('./farm-script-fixture.cjs');
const fixture = process.env.TCQT_FARM_GAME_DIR;

for (const deferProtocol of [false, true]) test('official QQ pipeline consumes game friend replies; deferred protocol=' + deferProtocol, { skip: !fixture }, async () => {
    const { manager, oops, proto, friend } = setup({ fixture, deferProtocol });
    const calls = [], events = [];
    manager.clientFriendList = [{ gid: 201, bPopupNew: false, bPopupOperate: false }];
    manager.blockedOpenIdsFromFriendRpc = ['blocked'];
    const sample = { gid: 201, open_id: 'fixture-friend', name: 'Fixture', level: 12, authorized_status: 2 };
    oops.message.dispatchEvent = (...args) => events.push(args);
    oops.netWebSocket.sendMsg = (body, method, callback, service) => {
        calls.push(method);
        assert.equal(service, method === 'BatchModerateText' ? 'gamepb.uicproxypb.UicprotoxyService' : 'gamepb.friendpb.FriendService');
        let reply;
        if (method === 'GetBriefInfo') {
            assert.equal(friend.GetBriefInfoRequest.decode(body).global_scene_id, 1);
            reply = friend.GetBriefInfoReply.encode({ friend_gids: [100, 201, 202] }).finish();
        } else if (method === 'GetGameFriends') {
            const request = friend.GetGameFriendsRequest.decode(body);
            assert.equal(request.global_scene_id, 1);
            assert.equal(request.friend_gids.length, 3);
            reply = friend.GetGameFriendsReply.encode({ friends: [{ gid: 100, open_id: 'self' }, sample, sample, { gid: 202 }] }).finish();
        } else if (method === 'BatchModerateText') {
            const pb = proto.gamepb.uicproxypb;
            reply = pb.BatchModerateTextReply.encode({ text_items: pb.BatchModerateTextRequest.decode(body).text_items }).finish();
        } else assert.fail('unexpected RPC ' + method);
        callback({ meta: { error_code: 0 }, body: reply });
    };
    await manager.reqFriendList(1);
    assert.deepEqual(calls, ['GetBriefInfo', 'GetGameFriends', 'BatchModerateText']);
    assert.equal(manager.clientFriendList.length, 1);
    const row = manager.clientFriendList[0];
    assert.equal(String(row.gid), '201');
    assert.equal(row.openid, 'fixture-friend');
    assert.equal(row.level, 12);
    assert.equal(row.bPopupNew, false);
    assert.equal(row.bPopupOperate, false);
    assert.equal(manager.application_count, 2);
    assert.equal(manager.blockedOpenIdsFromFriendRpc[0], 'blocked');
    assert.equal(manager.bReqFriendListed, true);
    assert.ok(events.some(event => event[1] === true), 'load-complete success event');
});

test('official QQ pipeline keeps its prior list when game API rejects', { skip: !fixture }, async () => {
    const { manager, oops } = setup({ fixture });
    const previous = [{ gid: 201 }], calls = [], events = [];
    manager.clientFriendList = previous;
    oops.message.dispatchEvent = (...args) => events.push(args);
    oops.netWebSocket.sendMsg = (body, method, callback) => {
        calls.push(method);
        callback({ meta: { error_code: 1000020 }, body: new Uint8Array(0) });
    };
    await manager.reqFriendList(2);
    assert.deepEqual(calls, ['GetBriefInfo']);
    assert.equal(manager.clientFriendList, previous);
    assert.ok(events.some(event => event[1] === false));
});
