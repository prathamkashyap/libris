/**
 * Theme management — single source of truth for theme state.
 * Three themes: ember (dark, default), bloom (light/warm — labeled "Ivory"), slate (neutral dark).
 *
 * Flow:
 * 1. Inline <script> in <head> reads localStorage and sets data-theme before first paint.
 * 2. This module handles runtime toggling, persistence, and UI rendering.
 * 3. No other module should touch document.documentElement.dataset.theme or localStorage.theme.
 */

const THEMES = ['ember', 'bloom', 'slate'];
const STORAGE_KEY = 'theme';

/** Returns current theme string. */
export function getTheme() {
  const raw = document.documentElement.getAttribute('data-theme');
  return THEMES.includes(raw) ? raw : 'ember';
}

/**
 * Detects and applies the correct theme on page load.
 * - If localStorage has a stored preference, use it.
 * - Otherwise, check prefers-color-scheme: light → bloom, dark → ember.
 * Returns the applied theme string.
 */
export function initTheme() {
  let stored = localStorage.getItem(STORAGE_KEY);
  if (!stored || !THEMES.includes(stored)) {
    stored = window.matchMedia('(prefers-color-scheme: light)').matches ? 'bloom' : 'ember';
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
  applyTheme(theme);
  localStorage.setItem(STORAGE_KEY, theme);
  window.dispatchEvent(new CustomEvent('themechange', { detail: { theme } }));
  return theme;
}

/**
 * Cycles to the next theme in the sequence: ember → bloom → slate → ember.
 * Persists to localStorage. Returns the new theme string.
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
 * Renders the three-dot theme switcher into the given container.
 * Each button shows the theme icon and sets that theme on click.
 * The active button is visually highlighted.
 */
export function renderThemeSwitcher(container) {
  if (!container) return;

  const icons = {
    bloom: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 3c.8 0 1.5.3 2 .8l2.2 2.2c.5.5.8 1.2.8 2v.5l2.8 1.6c.8.5 1.1 1.5.6 2.4-.3.5-.7.9-1.2 1.1l-2.8 1.2v.5c0 .8-.3 1.5-.8 2l-2.2 2.2c-.5.5-1.2.8-2 .8s-1.5-.3-2-.8l-2.2-2.2c-.5-.5-.8-1.2-.8-2v-.5l-2.8-1.2c-.8-.4-1.1-1.4-.6-2.4.3-.5.7-.9 1.2-1.1l2.8-1.6v-.5c0-.8.3-1.5.8-2l2.2-2.2c.5-.5 1.2-.8 2-.8Z"/></svg>`,
    ember: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2.5c1.2 2.8-1.6 4.2-1.9 7a3.9 3.9 0 0 0 7.8.3c.1-1.3-.4-2.4-1.1-3.3 1.3.7 2.5 2.3 2.5 4.5a6.3 6.3 0 0 1-12.6 0c0-4.3 3.7-5.6 5.3-8.5Z"/></svg>`,
    slate: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2 22 12 12 22 2 12Z"/></svg>`
  };

  const labels = { ember: 'Ember', bloom: 'Ivory', slate: 'Slate' };

  const wrapper = document.createElement('div');
  wrapper.className = 'theme-switch';
  wrapper.setAttribute('role', 'group');
  wrapper.setAttribute('aria-label', 'Theme');

  THEMES.forEach(theme => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'theme-opt';
    btn.dataset.themeValue = theme;
    btn.title = `${labels[theme]} — ${theme === 'ember' ? 'warm & scholarly' : theme === 'bloom' ? 'light & professional' : 'neutral & focused'}`;
    btn.innerHTML = icons[theme];
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

/**
 * @deprecated Use renderThemeSwitcher() instead.
 * Kept for backward compatibility — renders a single toggle button.
 */
export function renderThemeToggle(container) {
  if (!container) return;

  const btn = document.createElement('button');
  btn.className = 'theme-toggle-btn';
  btn.setAttribute('aria-label', 'Toggle theme');
  btn.setAttribute('title', 'Toggle theme');
  btn.type = 'button';

  const updateIcon = () => {
    const theme = getTheme();
    btn.innerHTML = theme === 'ember'
      ? `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79Z"/></svg>`
      : `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>`;
  };

  updateIcon();
  btn.addEventListener('click', () => { toggleTheme(); updateIcon(); });
  window.addEventListener('themechange', updateIcon);
  container.appendChild(btn);
}
