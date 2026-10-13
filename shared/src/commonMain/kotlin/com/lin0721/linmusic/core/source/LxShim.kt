package com.lin0721.linmusic.core.source

// 沙盒内的 window.lx 垫片，宿主需提供 MelodiaHostBridge（httpRequest/onEvent/onResolveResult/md5，缺 setTimeout 的环境还需 setTimer）
object LxShim {

    fun script(env: String): String = """
        (function() {
            var pendingCallbacks = {};
            var reqIdCounter = 0;
            var requestHandler = null;

            if (typeof window.setTimeout !== 'function') {
                var timers = {};
                var timerSeq = 0;
                window.setTimeout = function(fn, ms) {
                    var id = ++timerSeq;
                    timers[id] = { fn: fn, repeat: false, ms: ms || 0 };
                    MelodiaHostBridge.setTimer(id, ms || 0);
                    return id;
                };
                window.setInterval = function(fn, ms) {
                    var id = ++timerSeq;
                    timers[id] = { fn: fn, repeat: true, ms: ms || 0 };
                    MelodiaHostBridge.setTimer(id, ms || 0);
                    return id;
                };
                window.clearTimeout = function(id) { delete timers[id]; };
                window.clearInterval = window.clearTimeout;
                window.__melodia_fire_timer = function(id) {
                    var t = timers[id];
                    if (!t) return;
                    if (t.repeat) {
                        MelodiaHostBridge.setTimer(id, t.ms);
                    } else {
                        delete timers[id];
                    }
                    t.fn();
                };
            }

            window.lx = {
                version: "2.0.0",
                env: "$env",
                currentScriptInfo: {
                    name: "",
                    description: "",
                    version: "",
                    author: "",
                    rawScript: ""
                },
                EVENT_NAMES: {
                    request: "request",
                    inited: "inited",
                    updateAlert: "updateAlert"
                },
                request: function(url, opts, callback) {
                    var id = ++reqIdCounter;
                    pendingCallbacks[id] = callback;
                    var options = opts || {};
                    var method = options.method || 'GET';
                    var headers = JSON.stringify(options.headers || {});
                    var body = typeof options.body === 'string' ? options.body : (options.body ? JSON.stringify(options.body) : '');
                    MelodiaHostBridge.httpRequest(id, url, method, headers, body);
                    return function() { delete pendingCallbacks[id]; };
                },
                on: function(event, handler) {
                    if (event === 'request') {
                        requestHandler = handler;
                    }
                    return Promise.resolve();
                },
                send: function(event, data) {
                    MelodiaHostBridge.onEvent(event, JSON.stringify(data || {}));
                    return Promise.resolve();
                },
                utils: {
                    crypto: {
                        md5: function(str) { return MelodiaHostBridge.md5(str); }
                    },
                    buffer: {
                        from: function(data) { return data; },
                        bufToString: function(buf) { return String(buf); }
                    }
                }
            };

            window.__melodia_handle_http_response = function(id, err, respJson, body) {
                var cb = pendingCallbacks[id];
                if (!cb) return;
                delete pendingCallbacks[id];
                var resp = null;
                try { resp = respJson ? JSON.parse(respJson) : {}; } catch(e) { resp = {}; }
                var parsedBody = body;
                if (typeof body === 'string') {
                    var trimmed = body.trim();
                    if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
                        try { parsedBody = JSON.parse(trimmed); } catch(e) {}
                    }
                }
                resp.body = parsedBody;
                resp.statusCode = resp.statusCode || 200;
                cb(err ? new Error(err) : null, resp, parsedBody);
            };

            window.__melodia_resolve_music_url = function(callId, source, musicInfoJson, quality) {
                if (!requestHandler) {
                    MelodiaHostBridge.onResolveResult(callId, null, "No handler");
                    return;
                }
                var musicInfo = (typeof musicInfoJson === 'object') ? musicInfoJson : JSON.parse(musicInfoJson);
                Promise.resolve().then(function() {
                    return requestHandler({
                        source: source,
                        action: 'musicUrl',
                        info: {
                            type: quality,
                            musicInfo: musicInfo
                        }
                    });
                }).then(function(result) {
                    var url = typeof result === 'string' ? result : (result && result.url ? result.url : null);
                    MelodiaHostBridge.onResolveResult(callId, url, null);
                }).catch(function(err) {
                    MelodiaHostBridge.onResolveResult(callId, null, err ? err.message : "Error");
                });
            };
        })();
    """.trimIndent()
}
