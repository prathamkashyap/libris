/**
 * Sidebar behavior — mobile toggle, active link highlighting, logout wiring.
 */
import { renderThemeToggle } from '/js/theme.js';

/** Initialize sidebar interactions */
export function initSidebar() {
  const rail = document.getElementById('rail');
  const menuBtn = document.getElementById('menuToggle');

  // Mobile hamburger toggle
  if (menuBtn && rail) {
    menuBtn.addEventListener('click', () => rail.classList.toggle('open'));
    document.addEventListener('click', (e) => {
      if (rail.classList.contains('open') && !rail.contains(e.target) && e.target !== menuBtn) {
        rail.classList.remove('open');
      }
    });
  }

  // Highlight active link
  const currentPage = window.location.pathname.split('/').pop() || 'index.html';
  if (rail) {
    rail.querySelectorAll('a[data-page]').forEach(link => {
      if (link.dataset.page === currentPage) {
        link.classList.add('active');
      }
    });
  }

  // Wire logout links
  document.querySelectorAll('.logout').forEach(link => {
    link.addEventListener('click', async event => {
      event.preventDefault();
      try {
        const { authApi } = await import('/js/api/auth-api.js');
        const { setCurrentUser } = await import('/js/api/http.js');
        await authApi.logout();
        setCurrentUser(null);
      } finally {
        window.location.replace('/login.html');
      }
    });
  });
}

/** Add a theme toggle button to the sidebar */
export function initThemeToggleInSidebar() {
  const container = document.getElementById('rail-theme-toggle');
  if (container) renderThemeToggle(container);
}
