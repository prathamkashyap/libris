// Athenaeum — shared interaction layer (prototype-grade, no backend)

document.addEventListener('DOMContentLoaded', () => {

  /* ---------- Mobile sidebar toggle ---------- */
  const menuBtn = document.getElementById('menuToggle');
  const rail = document.getElementById('rail');
  if (menuBtn && rail) {
    menuBtn.addEventListener('click', () => rail.classList.toggle('open'));
    document.addEventListener('click', (e) => {
      if (rail.classList.contains('open') && !rail.contains(e.target) && e.target !== menuBtn) {
        rail.classList.remove('open');
      }
    });
  }

  /* ---------- Notification dropdown ---------- */
  const bell = document.getElementById('notifBell');
  const notifPanel = document.getElementById('notifPanel');
  if (bell && notifPanel) {
    bell.addEventListener('click', (e) => {
      e.stopPropagation();
      notifPanel.classList.toggle('open');
    });
    document.addEventListener('click', () => notifPanel.classList.remove('open'));
    notifPanel.addEventListener('click', (e) => e.stopPropagation());
  }

  /* ---------- Command palette (Ctrl+K / Cmd+K) ---------- */
  const palette = document.getElementById('cmdPalette');
  const paletteInput = document.getElementById('cmdInput');
  const searchTrigger = document.getElementById('searchTrigger');

  const openPalette = () => {
    if (!palette) return;
    palette.classList.add('open');
    setTimeout(() => paletteInput && paletteInput.focus(), 30);
  };
  const closePalette = () => palette && palette.classList.remove('open');

  if (searchTrigger) searchTrigger.addEventListener('click', openPalette);
  if (palette) {
    palette.addEventListener('click', (e) => { if (e.target === palette) closePalette(); });
  }
  document.addEventListener('keydown', (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      palette && palette.classList.contains('open') ? closePalette() : openPalette();
    }
    if (e.key === 'Escape') closePalette();
  });

  /* ---------- Session logout ---------- */
  const csrfToken = () => {
    const cookie = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='));
    return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : null;
  };

  document.querySelectorAll('.logout').forEach(link => {
    link.addEventListener('click', async event => {
      event.preventDefault();
      try {
        await fetch('/api/auth/csrf', { credentials: 'include' });
        const headers = {};
        const token = csrfToken();
        if (token) headers['X-XSRF-TOKEN'] = token;
        await fetch('/api/auth/logout', { method: 'POST', credentials: 'include', headers });
      } finally {
        window.location.replace('/login.html');
      }
    });
  });

  /* ---------- Tabs (Settings page) ---------- */
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

  /* ---------- Grid / List toggle (Books page) ---------- */
  const gridBtn = document.getElementById('viewGrid');
  const listBtn = document.getElementById('viewList');
  const gridEl = document.getElementById('booksGrid');
  const listEl = document.getElementById('booksList');
  if (gridBtn && listBtn && gridEl && listEl) {
    gridBtn.addEventListener('click', () => {
      gridBtn.classList.add('active'); listBtn.classList.remove('active');
      gridEl.style.display = ''; listEl.style.display = 'none';
    });
    listBtn.addEventListener('click', () => {
      listBtn.classList.add('active'); gridBtn.classList.remove('active');
      listEl.style.display = ''; gridEl.style.display = 'none';
    });
  }

  /* ---------- Skeleton -> content swap demo (Analytics page) ---------- */
  document.querySelectorAll('.skeleton-wrap').forEach(el => {
    setTimeout(() => el.classList.add('loaded'), 700);
  });

});
