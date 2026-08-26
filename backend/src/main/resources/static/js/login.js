import { authApi } from '/js/api/auth-api.js';
import { initTheme, renderThemeSwitcher } from '/js/theme.js';

document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  renderThemeSwitcher(document.getElementById('topbar-theme'));

  const form = document.getElementById('login-form');
  const error = document.getElementById('login-error');
  if (!form || !error) return;

  if (new URLSearchParams(window.location.search).get('registered') === 'true') {
    error.textContent = 'Account created successfully. Please sign in.';
    error.classList.remove('form-error');
    error.classList.add('form-success');
    error.hidden = false;
  }

  authApi.csrf().catch(() => {});

  form.addEventListener('submit', async event => {
    event.preventDefault();
    error.hidden = true;
    error.classList.remove('form-success');
    error.classList.add('form-error');

    const data = new FormData(form);
    const username = String(data.get('username') || '').trim();
    const password = String(data.get('password') || '');

    if (!username || !password) {
      error.textContent = 'Enter your username and password.';
      error.hidden = false;
      return;
    }

    const submit = form.querySelector('button[type="submit"]');
    if (submit) submit.disabled = true;

    try {
      await authApi.csrf();
      await authApi.login({ username, password });
      window.location.replace('/');
    } catch (err) {
      error.textContent = err.message || 'We could not sign you in. Check your credentials and try again.';
      error.hidden = false;
    } finally {
      if (submit) submit.disabled = false;
    }
  });

  document.querySelectorAll('.login-pw-toggle').forEach(button => {
    button.addEventListener('click', () => {
      const input = document.getElementById(button.dataset.target);
      if (!input) return;
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      button.setAttribute('aria-pressed', String(show));
      button.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
      const eyeOpen = button.querySelector('.pw-eye-open');
      const eyeClosed = button.querySelector('.pw-eye-closed');
      if (eyeOpen) eyeOpen.hidden = show;
      if (eyeClosed) eyeClosed.hidden = !show;
    });
  });
});
