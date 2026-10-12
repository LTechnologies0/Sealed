package app.sealed.web

import org.json.JSONObject

object LoginFill {
    fun script(username: String, password: String): String {
        val user = JSONObject.quote(username)
        val pass = JSONObject.quote(password)
        return """
            (function () {
              if (window.top !== window) return;
              var user = $user;
              var pass = $pass;
              var gesture = false;
              var timer = 0;
              function arm() {
                gesture = true;
                if (timer) clearTimeout(timer);
                timer = setTimeout(function () { gesture = false; }, 2000);
              }
              function visible(el) {
                if (!el || el.disabled) return false;
                var style = window.getComputedStyle(el);
                if (!style || style.visibility === "hidden" || style.display === "none" || Number(style.opacity) === 0) return false;
                var box = el.getBoundingClientRect();
                return box.width > 0 && box.height > 0;
              }
              function inputOf(target) {
                if (!target) return null;
                if (target.tagName === "INPUT") return target;
                if (target.control && target.control.tagName === "INPUT") return target.control;
                if (target.closest) {
                  var label = target.closest("label");
                  if (label && label.control && label.control.tagName === "INPUT") return label.control;
                }
                return null;
              }
              function setValue(el, value) {
                var proto = Object.getPrototypeOf(el);
                var desc = Object.getOwnPropertyDescriptor(proto, "value");
                if (desc && desc.set) desc.set.call(el, value);
                else el.value = value;
                el.dispatchEvent(new Event("input", { bubbles: true }));
                el.dispatchEvent(new Event("change", { bubbles: true }));
              }
              function fillFrom(raw) {
                if (window.top !== window) return;
                var target = inputOf(raw);
                if (!gesture || !target || !visible(target) || target !== document.activeElement) return;
                var scope = target.form || (target.closest && target.closest("form")) || document;
                var type = (target.getAttribute("type") || "text").toLowerCase();
                if (type === "password") {
                  if (pass) setValue(target, pass);
                  var fields = scope.querySelectorAll("input");
                  var chosen = null;
                  for (var i = 0; i < fields.length; i++) {
                    var el = fields[i];
                    if (el === target) break;
                    var other = (el.getAttribute("type") || "text").toLowerCase();
                    if (other === "hidden" || other === "password" || other === "checkbox" || other === "radio" || other === "submit" || other === "button") continue;
                    if ((other === "email" || other === "text" || other === "tel") && visible(el)) chosen = el;
                  }
                  if (chosen && user) setValue(chosen, user);
                  return;
                }
                if ((type === "email" || type === "text" || type === "tel") && user) setValue(target, user);
              }
              document.addEventListener("pointerdown", arm, true);
              document.addEventListener("focusin", function (event) { fillFrom(event.target); }, true);
              document.addEventListener("click", function (event) { fillFrom(event.target); }, true);
            })();
        """.trimIndent()
    }
}
