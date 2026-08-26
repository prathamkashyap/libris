/** Navigation, account display, and sign-out behavior. */
import { getCurrentUser } from '/js/api/http.js';

function closeNavigation(rail, menuButton) {
  rail?.classList.remove('open');
  menuButton?.setAttribute('aria-expanded', 'false');
  menuButton?.setAttribute('aria-label', 'Open navigation');
}

function applyRoleFilter(rail) {
  if (!rail) return;
  const role = (getCurrentUser()?.role || '').toUpperCase();
  rail.ownerDocument.querySelectorAll('[data-role-hide]').forEach(element => {
    const hiddenFor = element.dataset.roleHide.toUpperCase().split(',').map(value => value.trim());
    element.hidden = Boolean(role && hiddenFor.includes(role));
  });
}

export function initSidebar() {
  const rail = document.getElementById('rail');
  const menuButton = document.getElementById('menuToggle');
  if (!rail) return;

  rail.setAttribute('aria-label', 'Primary navigation');
  const currentPage = window.location.pathname.split('/').pop() || 'index.html';
  rail.querySelectorAll('a[data-page]').forEach(link => {
    const active = link.dataset.page === currentPage;
    link.classList.toggle('active', active);
    if (active) link.setAttribute('aria-current', 'page');
    link.addEventListener('click', () => closeNavigation(rail, menuButton));
  });

  menuButton?.addEventListener('click', () => {
    const open = rail.classList.toggle('open');
    menuButton.setAttribute('aria-expanded', String(open));
    menuButton.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
  });
  document.addEventListener('click', event => {
    if (rail.classList.contains('open') && !rail.contains(event.target) && !menuButton?.contains(event.target)) {
      closeNavigation(rail, menuButton);
    }
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape') closeNavigation(rail, menuButton);
  });

  applyRoleFilter(rail);
  window.addEventListener('userchange', () => applyRoleFilter(rail));

  rail.querySelectorAll('.logout').forEach(link => {
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
