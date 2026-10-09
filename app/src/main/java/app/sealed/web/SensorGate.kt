package app.sealed.web

import app.sealed.model.BrowserSettings
import java.net.URI

object SensorGate {
    const val ASK = "ask"
    const val DENY = "deny"
    const val ALLOW = "allow"
    const val SPOOF = "spoof"

    fun sameSite(origin: String?, lockedHost: String): Boolean {
        if (origin.isNullOrBlank() || lockedHost.isBlank()) return false
        val uri = try {
            URI(origin)
        } catch (_: Exception) {
            return false
        }
        if (uri.scheme != "https") return false
        val host = uri.host ?: return false
        return host == lockedHost || host.endsWith(".$lockedHost")
    }

    fun script(settings: BrowserSettings): String {
        val lat = settings.spoofLatitude.coerceIn(-90.0, 90.0)
        val lon = settings.spoofLongitude.coerceIn(-180.0, 180.0)
        val acc = settings.spoofAccuracyMeters.coerceIn(1.0, 10000.0)
        val cam = settings.camera == SPOOF
        val mic = settings.microphone == SPOOF
        val geo = settings.locationMode == SPOOF
        return """
            (function () {
              var spoofCam = $cam;
              var spoofMic = $mic;
              var spoofGeo = $geo;
              if (spoofGeo && navigator.geolocation) {
                var watch = 0;
                var watches = {};
                var geo = {
                  getCurrentPosition: function (ok) {
                    if (typeof ok === "function") ok({
                      coords: {
                        latitude: $lat,
                        longitude: $lon,
                        accuracy: $acc,
                        altitude: null,
                        altitudeAccuracy: null,
                        heading: null,
                        speed: null
                      },
                      timestamp: Date.now()
                    });
                  },
                  watchPosition: function (ok) {
                    var id = ++watch;
                    watches[id] = setInterval(function () { geo.getCurrentPosition(ok); }, 5000);
                    geo.getCurrentPosition(ok);
                    return id;
                  },
                  clearWatch: function (id) {
                    if (watches[id]) clearInterval(watches[id]);
                    delete watches[id];
                  }
                };
                try {
                  Object.defineProperty(navigator, "geolocation", { configurable: true, get: function () { return geo; } });
                } catch (e) {}
              }
              var devices = navigator.mediaDevices;
              if (devices && devices.getUserMedia && (spoofCam || spoofMic)) {
                var orig = devices.getUserMedia.bind(devices);
                devices.getUserMedia = function (constraints) {
                  var c = constraints || {};
                  var fakeVideo = !!c.video && spoofCam;
                  var fakeAudio = !!c.audio && spoofMic;
                  var real = {};
                  if (c.video && !spoofCam) real.video = c.video;
                  if (c.audio && !spoofMic) real.audio = c.audio;
                  function silentAudio() {
                    var ctx = new AudioContext();
                    var buffer = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);
                    var source = ctx.createBufferSource();
                    source.buffer = buffer;
                    source.loop = true;
                    var dest = ctx.createMediaStreamDestination();
                    source.connect(dest);
                    source.start();
                    return dest.stream.getAudioTracks()[0];
                  }
                  function flatVideo() {
                    var canvas = document.createElement("canvas");
                    canvas.width = 640;
                    canvas.height = 480;
                    var paint = canvas.getContext("2d");
                    paint.fillStyle = "#202124";
                    paint.fillRect(0, 0, 640, 480);
                    return canvas.captureStream(1).getVideoTracks()[0];
                  }
                  function fake() {
                    var stream = new MediaStream();
                    if (fakeVideo) stream.addTrack(flatVideo());
                    if (fakeAudio) stream.addTrack(silentAudio());
                    return stream;
                  }
                  if (!fakeVideo && !fakeAudio) return orig(c);
                  if (!real.video && !real.audio) return Promise.resolve(fake());
                  return orig(real).then(function (live) {
                    var stream = fake();
                    live.getTracks().forEach(function (track) { stream.addTrack(track); });
                    return stream;
                  });
                };
              }
            })();
        """.trimIndent()
    }
}
