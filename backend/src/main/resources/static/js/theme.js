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
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'theme-opt';
  button.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.64 5.64l1.41 1.41M16.95 16.95l1.41 1.41M18.36 5.64l-1.41 1.41M7.05 16.95l-1.41 1.41"/><circle cx="12" cy="12" r="4"/></svg><span class="sr-only">Toggle colour theme</span>';
  const sync = () => {
    const light = getTheme() === 'verdigris';
    button.setAttribute('aria-pressed', String(light));
    button.setAttribute('aria-label', light ? 'Use dark theme' : 'Use light theme');
    button.title = light ? 'Use dark theme' : 'Use light theme';
  };
  button.addEventListener('click', toggleTheme);
  window.addEventListener('themechange', sync);
  sync();
  container.replaceChildren(button);
}
