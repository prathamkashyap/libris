/** Shared initializer for authenticated pages. */
import { initTheme, renderThemeSwitcher } from '/js/theme.js';
import { initSidebar } from '/js/sidebar.js';
import { initPalette } from '/js/palette.js';
import { initTopbar } from '/js/topbar.js';
import { getCurrentUser, setCurrentUser } from '/js/api/http.js';

const componentCache = new Map();

async function loadComponent(id, url) {
  const root = document.getElementById(id);
  if (!root) return;

  try {
    if (!componentCache.has(url)) {
      const response = await fetch(url);
      if (!response.ok) throw new Error(`Fetch failed: ${response.status}`);
      componentCache.set(url, await response.text());
    }
    root.innerHTML = componentCache.get(url);
  } catch (error) {
    root.innerHTML = '<p class="shell-error">The application navigation could not load. Refresh the page to try again.</p>';
    console.error(`Failed to load ${url}:`, error);
  }
}

function initTabs() {
  document.querySelectorAll('.tabs').forEach(tabGroup => {
    const buttons = tabGroup.querySelectorAll('.tab-btn');
    const panels = document.querySelectorAll('[data-tab-panel]');
    buttons.forEach(button => {
      button.addEventListener('click', () => {
        buttons.forEach(item => {
          const active = item === button;
          item.classList.toggle('active', active);
          item.setAttribute('aria-selected', String(active));
        });
        const target = button.getAttribute('data-tab');
        panels.forEach(panel => { panel.hidden = panel.getAttribute('data-tab-panel') !== target; });
      });
    });
  });
}

document.addEventListener('DOMContentLoaded', async () => {
  await Promise.all([
    loadComponent('rail', '/components/sidebar.html'),
    loadComponent('topbar-root', '/components/topbar.html'),
    loadComponent('palette-root', '/components/palette.html')
  ]);

  const currentBreadcrumb = document.getElementById('topbar-current');
  if (currentBreadcrumb) currentBreadcrumb.textContent = document.title.split('—')[0].trim();

  initTheme();
  initSidebar();
  initTopbar();
  initPalette();
  renderThemeSwitcher(document.getElementById('topbar-theme'));
  initTabs();
  // A page module may resolve /api/auth/me while these async components load.
  // Re-emit the current user so the just-inserted shell receives that state.
  if (getCurrentUser()) setCurrentUser(getCurrentUser());
});
