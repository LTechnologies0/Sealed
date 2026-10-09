package app.sealed.web

import org.json.JSONObject

object LoginFill {
    fun script(username: String, password: String): String {
        val user = JSONObject.quote(username)
        val pass = JSONObject.quote(password)
        return """
            (function () {
              var user = $user;
              var pass = $pass;
              var done = false;
              function setValue(el, value) {
                var proto = Object.getPrototypeOf(el);
                var desc = Object.getOwnPropertyDescriptor(proto, "value");
                if (desc && desc.set) desc.set.call(el, value);
                else el.value = value;
                el.dispatchEvent(new Event("input", { bubbles: true }));
                el.dispatchEvent(new Event("change", { bubbles: true }));
              }
              var observer = null;
              function fill() {
                if (done) return;
                var passwords = document.querySelectorAll("input[type='password']");
                for (var i = 0; i < passwords.length; i++) {
                  var pw = passwords[i];
                  if (pw.disabled || pw.value) continue;
                  var scope = pw.form || (pw.closest && pw.closest("form")) || document;
                  var fields = scope.querySelectorAll("input");
                  var chosen = null;
                  for (var j = 0; j < fields.length; j++) {
                    var el = fields[j];
                    if (el === pw) break;
                    var type = (el.getAttribute("type") || "text").toLowerCase();
                    if (type === "hidden" || type === "password" || type === "checkbox" || type === "radio" || type === "submit" || type === "button") continue;
                    if (type === "email" || type === "text" || type === "tel") chosen = el;
                  }
                  if (chosen && !chosen.value && user) setValue(chosen, user);
                  if (pass) setValue(pw, pass);
                  done = true;
                  if (observer) observer.disconnect();
                  return;
                }
              }
              var root = document.documentElement;
              if (root) {
                observer = new MutationObserver(fill);
                observer.observe(root, { childList: true, subtree: true });
              }
              fill();
            })();
        """.trimIndent()
    }
}
