/**
 * Sidebar behavior — mobile toggle, active link highlighting, logout wiring, role filtering.
 */
import { getCurrentUser } from '/js/api/http.js';

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

  // Role-based nav filtering
  applyRoleFilter(rail);

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

/**
 * Hide nav items marked with `data-role-hide` when the current user's role
 * matches the attribute value. E.g. data-role-hide="STUDENT" hides the item
 * for students but shows it for ADMIN and LIBRARIAN.
 */
function applyRoleFilter(rail) {
  if (!rail) return;
  const user = getCurrentUser();
  const role = (user?.role || '').toUpperCase();
  if (!role) return;

  rail.querySelectorAll('[data-role-hide]').forEach(el => {
    const hiddenFor = el.getAttribute('data-role-hide').toUpperCase().split(',').map(s => s.trim());
    if (hiddenFor.includes(role)) {
      el.style.display = 'none';
    }
  });
}
