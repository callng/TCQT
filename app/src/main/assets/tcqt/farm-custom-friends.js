/* TCQT_FARM_CUSTOM_FRIENDS_V1: installed only in a marked custom-code farm launch. */
(function (system) {
    "use strict";
    function status(stage, count) {
        try {
            if (typeof qq !== "undefined" && typeof qq.getStorageSync === "function") {
                var data = { stage: stage };
                if (typeof count === "number") data.count = count;
                qq.getStorageSync("TCQT_FARM_FRIENDS_STATUS:" + JSON.stringify(data));
            }
        } catch (_) { /* Diagnostics must never affect game requests. */ }
    }
    status("adapter_started");
    if (!system || typeof system.register !== "function") { status("system_unavailable"); return; }
    if (system.__tcqtFarmFriendsV1) return;

    var managerReady = false;
    function unavailable() {
        return Promise.reject(new Error("TCQT farm friend adapter unavailable"));
    }
    function report(message) {
        if (typeof console !== "undefined" && console.info) console.info("[TCQT FarmFriends] " + message);
    }
    function queryFriends(manager, scene, dependencies) {
        status("query_start");
        // SystemJS circular imports may still be empty when FriendManager is exported.
        // Read their live namespaces when the game actually requests its friend list.
        var pb = dependencies.protocol && dependencies.protocol.gamepb && dependencies.protocol.gamepb.friendpb;
        var socket = dependencies.oops && dependencies.oops.oops && dependencies.oops.oops.netWebSocket;
        var model = dependencies.globalData && dependencies.globalData.GlobalData && dependencies.globalData.GlobalData.selfModel;
        var missing = (!(pb && pb.GetBriefInfoRequest && pb.GetBriefInfoReply && pb.GetGameFriendsRequest && pb.GetGameFriendsReply) ? 1 : 0) |
            (!(socket && typeof socket.sendMsg === "function") ? 2 : 0) | (!model ? 4 : 0);
        if (missing) {
            status("manager_unsupported", missing);
            status("query_failed");
            return unavailable();
        }
        function request(method, data) {
            return new Promise(function (resolve, reject) {
                var settled = false;
                function finish(error, value) {
                    if (settled) return;
                    settled = true;
                    clearTimeout(timer);
                    if (error) reject(error); else resolve(value);
                }
                var timer = setTimeout(function () {
                    finish(new Error("TCQT farm friend request timed out"));
                }, 15000);
                try {
                    socket.sendMsg(pb[method + "Request"].encode(data).finish(), method, function (reply) {
                        try {
                            if (!reply || !reply.body || !reply.meta || reply.meta.error_code != 0) {
                                finish(new Error("TCQT farm friend request failed"));
                            } else {
                                finish(null, pb[method + "Reply"].decode(reply.body));
                            }
                        } catch (_) { finish(new Error("TCQT farm friend reply invalid")); }
                    }, "gamepb.friendpb.FriendService");
                } catch (_) { finish(new Error("TCQT farm friend request failed")); }
            });
        }
        // QQ currently rejects GetAll. GetBriefInfo returns this session's friend IDs;
        // GetGameFriends resolves their data without QQ/MSF credentials or an OpenID input.
        return request("GetBriefInfo", { global_scene_id: scene }).then(function (brief) {
            var ids = brief.friend_gids;
            status("brief_reply", ids.length);
            var friends = [];
            function next(offset) {
                if (offset >= ids.length) return Promise.resolve(friends);
                // Match the official client's default refresh batch size.
                return request("GetGameFriends", { global_scene_id: scene, friend_gids: ids.slice(offset, offset + 50) })
                    .then(function (reply) {
                        status("friends_reply", reply.friends.length);
                        friends = friends.concat(reply.friends);
                        return next(offset + 50);
                    });
            }
            return next(0);
        }).then(function (friends) {
            var self = String(model.gid);
            var old = Object.create(null);
            manager.clientFriendList.forEach(function (friend) { old[String(friend.gid)] = friend; });
            var seen = Object.create(null);
            var result = [];
            friends.forEach(function (friend) {
                var id = String(friend.gid);
                if (!friend.open_id || id === self || seen[id]) return;
                seen[id] = true;
                var row = manager.convertServerFriendToClientFriend(friend);
                if (old[id]) {
                    row.bPopupNew = old[id].bPopupNew;
                    row.bPopupOperate = old[id].bPopupOperate;
                }
                result.push(row);
            });
            // Commit only after all batches decode. Keep invitations, application counts
            // and block data: these replies do not contain updates to those fields.
            manager.clientFriendList = result;
            status("query_complete", result.length);
            return true;
        }).catch(function (error) {
            status("query_failed");
            throw error;
        });
    }
    function patchExport(name, value, dependencies) {
        var proto = value && value.prototype;
        if (!proto) return;
        if (name === "FriendManager") {
            managerReady = !!(typeof proto.reqAllFriendToServer === "function" &&
                typeof proto.convertServerFriendToClientFriend === "function");
            proto.reqAllFriendToServer = managerReady ? function (scene) {
                return queryFriends(this, scene, dependencies);
            } : unavailable;
            report(managerReady ? "Game friend query installed" : "Unsupported FriendManager");
            status(managerReady ? "manager_ready" : "manager_unsupported");
        } else if (name === "QqPlatformUtil" && typeof proto.getFriendStorage === "function") {
            // This is only the QQ discovery input. FriendManager fetches the actual list.
            proto.getFriendStorage = function () { return managerReady ? Promise.resolve([]) : unavailable(); };
            report("QQ friend discovery isolated");
            status("discovery_ready");
        }
    }
    var originalRegister = system.register;
    system.register = function () {
        var args = Array.prototype.slice.call(arguments);
        var id = args[0];
        if (typeof id === "string" && /(?:^|\/)(?:FriendManager|QqPlatformUtil)\.ts$/.test(id)) {
            var index = args.length - 1;
            var declare = args[index];
            var imports = args[1];
            if (typeof declare === "function" && Array.isArray(imports)) {
                args[index] = function (exportValue, context) {
                    var dependencies = {};
                    function interceptedExport(name, value) {
                        if (typeof name === "string") patchExport(name, value, dependencies);
                        else if (name && typeof name === "object") {
                            Object.keys(name).forEach(function (key) { patchExport(key, name[key], dependencies); });
                        }
                        return exportValue.apply(this, arguments);
                    }
                    var declaration = declare.call(this, interceptedExport, context);
                    var names = { "mygamepb.ts": "protocol", "Oops.ts": "oops", "GlobalData.ts": "globalData" };
                    imports.forEach(function (name, i) {
                        var key = names[name.slice(name.lastIndexOf("/") + 1)];
                        if (!key || !declaration.setters) return;
                        var setter = declaration.setters[i];
                        declaration.setters[i] = function (value) {
                            dependencies[key] = value;
                            if (setter) return setter.apply(this, arguments);
                        };
                    });
                    return declaration;
                };
            }
        }
        return originalRegister.apply(this, args);
    };
    Object.defineProperty(system, "__tcqtFarmFriendsV1", { value: true });
})(typeof System === "undefined" ? null : System);
