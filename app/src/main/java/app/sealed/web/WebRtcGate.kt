package app.sealed.web

object WebRtcGate {
    fun script(): String = """
        (function () {
          var Orig = window.RTCPeerConnection || window.webkitRTCPeerConnection;
          if (!Orig) return;
          function blocked(line) {
            return line.indexOf("typ host") !== -1 || line.indexOf("typ srflx") !== -1;
          }
          function wrap(pc) {
            var add = pc.addIceCandidate.bind(pc);
            pc.addIceCandidate = function (candidate) {
              var line = "";
              if (typeof candidate === "string") line = candidate;
              else if (candidate && candidate.candidate) line = candidate.candidate;
              if (blocked(line)) return Promise.resolve();
              return add.apply(pc, arguments);
            };
            var origOn = null;
            Object.defineProperty(pc, "onicecandidate", {
              configurable: true,
              get: function () { return origOn; },
              set: function (fn) {
                origOn = function (ev) {
                  if (ev && ev.candidate && ev.candidate.candidate && blocked(ev.candidate.candidate)) return;
                  if (typeof fn === "function") return fn.call(pc, ev);
                };
              }
            });
          }
          function Patched(config, constraints) {
            var next = {};
            if (config) {
              for (var key in config) {
                if (Object.prototype.hasOwnProperty.call(config, key)) next[key] = config[key];
              }
            }
            next.iceTransportPolicy = "relay";
            var pc = new Orig(next, constraints);
            wrap(pc);
            return pc;
          }
          Patched.prototype = Orig.prototype;
          if (Orig.generateCertificate) Patched.generateCertificate = Orig.generateCertificate.bind(Orig);
          window.RTCPeerConnection = Patched;
          if (window.webkitRTCPeerConnection) window.webkitRTCPeerConnection = Patched;
        })();
    """.trimIndent()
}
