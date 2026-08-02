/**
 * Topbar interactions — notification dropdown.
 */

export function initTopbar() {
  const bell = document.getElementById('notifBell');
  const panel = document.getElementById('notifPanel');

  if (bell && panel) {
    bell.addEventListener('click', (e) => {
      e.stopPropagation();
      panel.classList.toggle('open');
    });
    document.addEventListener('click', () => panel.classList.remove('open'));
    panel.addEventListener('click', (e) => e.stopPropagation());
  }
}
