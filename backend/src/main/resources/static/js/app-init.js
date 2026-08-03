/**
 * Shared application initializer for all authenticated pages.
 * Import this module in every page (except login.html).
 *
 * Handles: shell loading (sidebar, topbar, palette), theme switching,
 * logout wiring, and mobile sidebar toggle.
 */
import { initTheme, renderThemeSwitcher } from '/js/theme.js';
import { initSidebar } from '/js/sidebar.js';
import { initPalette } from '/js/palette.js';
import { initTopbar } from '/js/topbar.js';

const componentCache = new Map();

async function loadComponent(id, url) {
  const root = document.getElementById(id);
  if (!root) return;
  try {
    if (!componentCache.has(url)) {
      const res = await fetch(url);
      if (!res.ok) throw new Error(`Fetch failed: ${res.status}`);
      componentCache.set(url, await res.text());
    }
    root.innerHTML = componentCache.get(url);
  } catch (err) {
    console.error(`Failed to load ${url}:`, err);
  }
}

document.addEventListener('DOMContentLoaded', async () => {
  // Load shared shell components in parallel
  await Promise.all([
    loadComponent('rail', '/components/sidebar.html'),
    loadComponent('topbar-root', '/components/topbar.html'),
    loadComponent('palette-root', '/components/palette.html')
  ]);

  // Set topbar breadcrumb context based on document title
  const currentBreadcrumb = document.getElementById('topbar-current');
  if (currentBreadcrumb) {
     const pageTitle = document.title.split('—')[0].trim();
     currentBreadcrumb.textContent = pageTitle;
  }

  // Initialize all shared modules
  initTheme();
  initSidebar();
  initTopbar();
  initPalette();

  // Render theme switcher into topbar
  renderThemeSwitcher(document.getElementById('topbar-theme'));

  // Settings page tabs (if present)
  document.querySelectorAll('.tabs').forEach(tabGroup => {
    const buttons = tabGroup.querySelectorAll('.tab-btn');
    const panels = document.querySelectorAll('[data-tab-panel]');
    buttons.forEach(btn => {
      btn.addEventListener('click', () => {
        buttons.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        const target = btn.getAttribute('data-tab');
        panels.forEach(p => {
          p.style.display = (p.getAttribute('data-tab-panel') === target) ? '' : 'none';
        });
      });
    });
  });

  // Skeleton → content swap (analytics page)
  document.querySelectorAll('.skeleton-wrap').forEach(el => {
    setTimeout(() => el.classList.add('loaded'), 700);
  });

  // Card mouse-follow glow (Ivory theme)
  document.addEventListener('mousemove', (e) => {
    document.querySelectorAll('.stat-card, .book-gcard, .person-card, .quick').forEach(card => {
      const rect = card.getBoundingClientRect();
      card.style.setProperty('--mx', `${((e.clientX - rect.left) / rect.width) * 100}%`);
      card.style.setProperty('--my', `${((e.clientY - rect.top) / rect.height) * 100}%`);
    });
  });
});
