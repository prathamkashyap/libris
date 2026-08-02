/**
 * Shared application initializer for all authenticated pages.
 * Import this module in every page (except login.html).
 *
 * Handles: sidebar loading, theme toggle, topbar, palette,
 * logout wiring, and mobile sidebar toggle.
 */
import { loadSidebar } from '/components/sidebar-loader.js';
import { initTheme, renderThemeToggle } from '/js/theme.js';
import { initSidebar, initThemeToggleInSidebar } from '/js/sidebar.js';
import { initPalette } from '/js/palette.js';
import { initTopbar } from '/js/topbar.js';

document.addEventListener('DOMContentLoaded', async () => {
  // Load shared sidebar
  await loadSidebar();

  // Initialize all shared modules
  initTheme();
  initSidebar();
  initThemeToggleInSidebar();
  initTopbar();
  initPalette();

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
});
