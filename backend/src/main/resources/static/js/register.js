import { authApi } from "/js/api/auth-api.js";
import { initTheme, renderThemeSwitcher } from "/js/theme.js";

function clearFieldErrors(form) {
  form.querySelectorAll("[data-field-error]").forEach(error => { error.textContent = ""; });
  form.querySelectorAll("input").forEach(input => input.removeAttribute("aria-invalid"));
}

function showFieldErrors(form, errors) {
  errors.forEach(({ field, message }) => {
    const error = form.querySelector(`[data-field-error="${field}"]`);
    const input = form.querySelector(`#${field}`);
    if (error) error.textContent = message;
    if (input) input.setAttribute("aria-invalid", "true");
  });
}

document.addEventListener("DOMContentLoaded", () => {
  initTheme();
  renderThemeSwitcher(document.getElementById("topbar-theme"));

  const form = document.getElementById("registerForm");
  const alert = document.getElementById("alert");
  const submit = form.querySelector('button[type="submit"]');
  const defaultLabel = submit.textContent;

  form.addEventListener("submit", async event => {
    event.preventDefault();
    alert.hidden = true;
    clearFieldErrors(form);
    submit.disabled = true;
    submit.textContent = "Creating account…";

    const data = {
      name: document.getElementById("name").value.trim(),
      email: document.getElementById("email").value.trim(),
      phone: document.getElementById("phone").value.trim(),
      username: document.getElementById("username").value.trim(),
      password: document.getElementById("password").value
    };

    try {
      await authApi.csrf();
      await authApi.register(data);
      window.location.assign("/login.html?registered=true");
    } catch (error) {
      const fieldErrors = error.fieldErrors || [];
      if (fieldErrors.length) showFieldErrors(form, fieldErrors);
      alert.textContent = fieldErrors.length
        ? "Please correct the highlighted fields and try again."
        : error.message || "We could not create your account. Please try again.";
      alert.hidden = false;
    } finally {
      submit.disabled = false;
      submit.textContent = defaultLabel;
    }
  });
});
