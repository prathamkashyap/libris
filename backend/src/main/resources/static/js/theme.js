/**
 * Theme management — detection, toggle, and persistence.
 * The initial theme application happens via inline <script> in <head> (before first paint).
 * This module handles runtime toggling and UI rendering.
 */

/** Returns current theme string ('blue' or 'pink') */
export function getTheme() {
  return document.documentElement.getAttribute('data-theme') === 'pink' ? 'pink' : 'blue';
}

/**
 * Detects and applies the correct theme on page load.
 * - If localStorage has a stored preference, use it.
 * - Otherwise, check prefers-color-scheme: light → pink, dark → blue.
 * Returns the applied theme string.
 */
export function initTheme() {
  let stored = localStorage.getItem('theme');
  if (!stored) {
    stored = window.matchMedia('(prefers-color-scheme: light)').matches ? 'pink' : 'blue';
    localStorage.setItem('theme', stored);
  }
  if (stored === 'pink') {
    document.documentElement.setAttribute('data-theme', 'pink');
  } else {
    document.documentElement.removeAttribute('data-theme');
  }
  return stored;
}

/**
 * Toggles between pink and blue themes.
 * Persists to localStorage. Returns the new theme string.
 */
export function toggleTheme() {
  const current = getTheme();
  const next = current === 'pink' ? 'blue' : 'pink';
  if (next === 'pink') {
    document.documentElement.setAttribute('data-theme', 'pink');
  } else {
    document.documentElement.removeAttribute('data-theme');
  }
  localStorage.setItem('theme', next);

  // Dispatch a custom event so other components can react
  window.dispatchEvent(new CustomEvent('themechange', { detail: { theme: next } }));
  return next;
}

/**
 * Renders a compact theme toggle button into the given container.
 * Shows a moon/sun icon and toggles on click.
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
    btn.innerHTML = theme === 'blue'
      ? `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79Z"/></svg>`
      : `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>`;
  };

  updateIcon();
  btn.addEventListener('click', () => { toggleTheme(); updateIcon(); });
  window.addEventListener('themechange', updateIcon);
  container.appendChild(btn);
}
