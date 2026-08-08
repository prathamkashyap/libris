/**
 * Theme management — single source of truth.
 *
 * Supported themes:
 * - ember      (dark default)
 * - verdigris  (light)
 *
 * This is the only module allowed to read/write
 * document.documentElement.dataset.theme
 * or localStorage.theme, except for a minimal first-paint
 * inline <script> in <head> that reads localStorage before CSS load.
 *
 * Flow:
 * 1. Inline first-paint <script> in <head> reads localStorage and sets data-theme.
 * 2. initTheme() confirms/applies the persisted theme at runtime.
 * 3. This module handles runtime toggling, persistence, and UI rendering.
 * 4. No other module should touch document.documentElement.dataset.theme or localStorage.theme.
 */

const THEMES = ['ember', 'verdigris'];
const STORAGE_KEY = 'theme';

/** Returns current theme string. */
export function getTheme() {
  const raw = document.documentElement.getAttribute('data-theme');
  return THEMES.includes(raw) ? raw : 'ember';
}

/**
 * Detects and applies the correct theme on page load.
 * - If localStorage has a stored preference, use it.
 * - Otherwise default to Ember.
 * Returns the applied theme string.
 */
export function initTheme() {
  let stored = localStorage.getItem(STORAGE_KEY);
  if (!stored || !THEMES.includes(stored)) {
    stored = 'ember';
    localStorage.setItem(STORAGE_KEY, stored);
  }
  applyTheme(stored);
  return stored;
}

/**
 * Sets a specific theme. Persists to localStorage.
 * Returns the applied theme string.
 */
export function setTheme(theme) {
  if (!THEMES.includes(theme)) theme = 'ember';
  document.documentElement.classList.add('theme-transitioning');
  applyTheme(theme);
  localStorage.setItem(STORAGE_KEY, theme);
  window.dispatchEvent(new CustomEvent('themechange', { detail: { theme } }));
  setTimeout(() => document.documentElement.classList.remove('theme-transitioning'), 400);
  return theme;
}

/**
 * Toggles between ember and verdigris.
 */
export function toggleTheme() {
  const current = getTheme();
  const idx = THEMES.indexOf(current);
  const next = THEMES[(idx + 1) % THEMES.length];
  return setTheme(next);
}

/** Apply theme to the DOM. */
function applyTheme(theme) {
  if (theme === 'ember') {
    document.documentElement.removeAttribute('data-theme');
  } else {
    document.documentElement.setAttribute('data-theme', theme);
  }
}

/**
 * Renders the theme switcher.
 */
export function renderThemeSwitcher(container) {
  if (!container) return;

  const icons = {
    ember: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2c.5 2.5 1 3.5 1.8 4.5.8 1 1.2 1.8 1 3.2-.2 1.2-.8 2-1.3 2.8l2.5 1c.6.3.9 1 .6 1.7-.2.5-.5.8-1 1l-2.2.6c.3.8.4 1.6.2 2.5-.3 1.3-1.2 2.2-2.6 2.2s-2.3-.9-2.6-2.2c-.2-.9-.1-1.7.2-2.5l-2.2-.6c-.5-.2-.8-.5-1-1-.3-.7 0-1.4.6-1.7l2.5-1c-.5-.8-1.1-1.6-1.3-2.8-.2-1.4.2-2.2 1-3.2C10 5.5 10.5 4.5 11 2h1Z"/></svg>`,
    verdigris: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0-18Zm0 3v6l4 2"/></svg>`
  };

  const labels = {
    ember: 'Ember',
    verdigris: 'Verdigris'
  };
  const tooltips = {
    ember: 'Dark theme',
    verdigris: 'Light theme'
  };

  const wrapper = document.createElement('div');
  wrapper.className = 'theme-switch';
  wrapper.setAttribute('role', 'group');
  wrapper.setAttribute('aria-label', 'Theme');

  THEMES.forEach(theme => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'theme-opt';
    btn.dataset.themeValue = theme;
    btn.title = tooltips[theme];
    btn.innerHTML = `
    ${icons[theme]}
    <span>${labels[theme]}</span>
    `;
    btn.addEventListener('click', () => setTheme(theme));
    wrapper.appendChild(btn);
  });

  const updatePressed = () => {
    const current = getTheme();
    wrapper.querySelectorAll('.theme-opt').forEach(b => {
      b.setAttribute('aria-pressed', String(b.dataset.themeValue === current));
    });
  };

  updatePressed();
  window.addEventListener('themechange', updatePressed);
  container.appendChild(wrapper);
}
