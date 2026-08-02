/**
 * Dashboard page — data loading and rendering.
 * Shared infrastructure (sidebar, theme, palette, topbar) is handled by app-init.js.
 */
import { booksApi } from "/js/api/books-api.js";
import { magazinesApi } from "/js/api/magazines-api.js";
import { newspapersApi } from "/js/api/newspapers-api.js";
import { studentsApi } from "/js/api/students-api.js";
import { librariansApi } from "/js/api/librarians-api.js";
import { borrowApi } from "/js/api/borrow-api.js";
import { authApi } from "/js/api/auth-api.js";
import { dashboardApi } from "/js/api/dashboard-api.js";
import { studentDashboardApi } from "/js/api/student-dashboard-api.js";
import { requestJson, setCurrentUser, getCurrentUser } from "/js/api/http.js";
import { openModal } from "/components/modal.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";

let currentUser = getCurrentUser();

/* ── Time-aware greeting ─────────────────────────────────── */
function greeting(name) {
  const h = new Date().getHours();
  const period = h < 12 ? "morning" : h < 17 ? "afternoon" : "evening";
  return `Good ${period}, ${name}.`;
}

/* ── Hash-based unique cover gradient ────────────────────── */
function coverGradient(title) {
  let hash = 0;
  for (let i = 0; i < title.length; i++) hash = title.charCodeAt(i) + ((hash << 5) - hash);
  const h1 = Math.abs(hash) % 360;
  const h2 = (h1 + 35 + (Math.abs(hash >> 8) % 30)) % 360;
  return `linear-gradient(145deg, hsl(${h1}, 60%, 38%), hsl(${h2}, 55%, 28%))`;
}

/* ── Populate user sidebar card ──────────────────────────── */
function populateUserCard(user) {
  const nameEl = document.querySelector('.ru-name');
  const roleEl = document.querySelector('.ru-role');
  const avatarEl = document.querySelector('.rail-user img');
  if (nameEl) nameEl.textContent = user.name || user.username;
  if (roleEl) roleEl.textContent = user.role || 'Member';
  if (avatarEl) {
    const seed = (user.name || user.username).replace(/\s+/g, '-');
    avatarEl.src = `https://api.dicebear.com/7.x/notionists/svg?seed=${encodeURIComponent(seed)}&backgroundColor=2b2540`;
  }
}

/* ── Dashboard data loader ───────────────────────────────── */
async function load() {
  if (!currentUser) return;

  populateUserCard(currentUser);

  const greetEl = document.getElementById('dashboard-greeting');
  if (greetEl) greetEl.textContent = greeting(currentUser.name || currentUser.username.split(' ')[0]);

  // --- Student dashboard ---
  if (currentUser.role === 'STUDENT') {
    const [studentDashboard, borrowHistory] = await Promise.all([
      studentDashboardApi.getDashboard(),
      studentDashboardApi.getBorrowHistory()
    ]);

    document.getElementById('student-current-borrows').innerHTML = studentDashboard.currentBorrows.map(b =>
      `<div class="tl-row"><div class="tl-dot dot-out"></div><div class="tl-body"><b>${esc(b.itemTitle)}</b> (${esc(b.itemType)}) issued to you <span class="badge badge-out">${esc(b.status)}</span><small>${b.borrowDate} — ${b.returnDate ?? 'Not returned'}</small></div></div>`
    ).join('') || '<div class="empty-state">No active borrows.</div>';

    document.getElementById('student-borrow-history').innerHTML = borrowHistory.content.map(r =>
      `<div class="tl-row"><div class="tl-dot ${r.status === 'RETURNED' ? 'dot-in' : 'dot-out'}"></div><div class="tl-body"><b>${esc(r.itemTitle)}</b> (${esc(r.itemType)}) ${r.status === 'BORROWED' ? 'issued to' : 'returned by'} you <span class="badge ${r.status === 'BORROWED' ? 'badge-out' : 'badge-return'}">${esc(r.status)}</span><small>${r.borrowDate} — ${r.returnDate ?? 'Not returned'}</small></div></div>`
    ).join('') || '<div class="empty-state">No borrow history yet.</div>';
    return;
  }

  // --- Admin / Librarian dashboard ---
  const [books, magazines, newspapers, students, librarians, records, dashboard] = await Promise.all([
    booksApi.list(), magazinesApi.list(), newspapersApi.list(),
    studentsApi.list(), librariansApi.list(), borrowApi.list(), dashboardApi.get()
  ]);

  // Stat cards
  const statValues = document.querySelectorAll('#dashboard-stats .stat-value, #librarian-dashboard-stats .stat-value');
  if (statValues.length >= 5) {
    statValues[0].textContent = dashboard.totalBooks || 0;
    statValues[1].textContent = dashboard.totalStudents || 0;
    statValues[2].textContent = dashboard.activeBorrows || 0;
    statValues[3].textContent = dashboard.availableBooks || 0;
    statValues[4].textContent = dashboard.totalLibrarians || 0;
  }
  if (statValues.length >= 10) {
    statValues[5].textContent = dashboard.totalBooks || 0;
    statValues[6].textContent = dashboard.totalStudents || 0;
    statValues[7].textContent = dashboard.activeBorrows || 0;
    statValues[8].textContent = dashboard.availableBooks || 0;
    statValues[9].textContent = dashboard.totalLibrarians || 0;
  }

  renderBooks(books.content || books);
  renderMagazines(magazines.content || magazines);
  renderNewspapers(newspapers.content || newspapers);
  renderStudents(students.content || students);
  renderLibrarians(librarians.content || librarians);
  renderRecords(records.content || records);
}

/* ── Section renderers ───────────────────────────────────── */
function renderBooks(rows) {
  const tbody = document.querySelector('#books table tbody');
  if (!tbody) return;
  tbody.innerHTML = rows.map(b => `<tr><td><input type="checkbox"></td><td class="ttl"><div class="cov" style="background:${coverGradient(b.title)}"></div>${esc(b.title)}</td><td>${esc(b.author)}</td><td><span class="tag">Book</span></td><td class="mono">${esc(b.isbn)}</td><td><span class="badge ${b.available ? "badge-avail" : "badge-out"}">${b.available ? "Available" : "Checked out"}</span></td><td class="row-actions"><a href="book-details.html?id=${b.id}">View</a> · <button class="link-btn" data-edit-book="${b.id}">Edit</button> · <button class="link-btn" data-delete-book="${b.id}">Delete</button></td></tr>`).join("") || '<tr><td colspan="7" class="empty-state" style="padding: 24px; text-align: center; color: var(--ink-soft);">No books found.</td></tr>';

  // Wire edit/delete
  tbody.querySelectorAll('[data-delete-book]').forEach(btn => btn.addEventListener('click', async () => {
    if (!await confirmDialog('Delete this book permanently?', 'Delete')) return;
    try { await booksApi.delete(btn.dataset.deleteBook); toast('Book deleted', 'success'); load(); }
    catch (e) { toast(e.message || 'Failed to delete', 'error'); }
  }));
  tbody.querySelectorAll('[data-edit-book]').forEach(btn => btn.addEventListener('click', async () => {
    const book = rows.find(b => String(b.id) === btn.dataset.editBook);
    if (!book) return;
    openModal('book', async (data) => {
      await booksApi.update(book.id, data);
      toast('Book updated', 'success');
      load();
    }, book);
  }));
}

function renderMagazines(rows) {
  const tbody = document.querySelector('#magazines table tbody');
  if (!tbody) return;
  tbody.innerHTML = rows.map(m => `<tr><td class="ttl">${esc(m.title)}</td><td>${esc(m.publisher)}</td><td>${m.issueDate ?? "—"}</td><td><span class="badge ${m.available ? "badge-avail" : "badge-out"}">${m.available ? "Available" : "Checked out"}</span></td><td class="row-actions"><button class="link-btn" data-edit-mag="${m.id}">Edit</button> · <button class="link-btn" data-delete-mag="${m.id}">Delete</button></td></tr>`).join("") || '<tr><td colspan="5" class="empty-state" style="padding: 24px; text-align: center; color: var(--ink-soft);">No magazines found.</td></tr>';

  tbody.querySelectorAll('[data-delete-mag]').forEach(btn => btn.addEventListener('click', async () => {
    if (!await confirmDialog('Delete this magazine?', 'Delete')) return;
    try { await magazinesApi.delete(btn.dataset.deleteMag); toast('Magazine deleted', 'success'); load(); }
    catch (e) { toast(e.message || 'Failed to delete', 'error'); }
  }));
  tbody.querySelectorAll('[data-edit-mag]').forEach(btn => btn.addEventListener('click', async () => {
    const mag = rows.find(m => String(m.id) === btn.dataset.editMag);
    if (!mag) return;
    openModal('magazine', async (data) => {
      await magazinesApi.update(mag.id, data);
      toast('Magazine updated', 'success');
      load();
    }, mag);
  }));
}

function renderNewspapers(rows) {
  const tbody = document.querySelector('#newspapers table tbody');
  if (!tbody) return;
  tbody.innerHTML = rows.map(n => `<tr><td class="ttl">${esc(n.title)}</td><td>${n.publicationDate ?? "—"}</td><td><span class="badge ${n.available ? "badge-avail" : "badge-out"}">${n.available ? "Available" : "Checked out"}</span></td><td class="row-actions"><button class="link-btn" data-edit-news="${n.id}">Edit</button> · <button class="link-btn" data-delete-news="${n.id}">Delete</button></td></tr>`).join("") || '<tr><td colspan="4" class="empty-state" style="padding: 24px; text-align: center; color: var(--ink-soft);">No newspapers found.</td></tr>';

  tbody.querySelectorAll('[data-delete-news]').forEach(btn => btn.addEventListener('click', async () => {
    if (!await confirmDialog('Delete this newspaper?', 'Delete')) return;
    try { await newspapersApi.delete(btn.dataset.deleteNews); toast('Newspaper deleted', 'success'); load(); }
    catch (e) { toast(e.message || 'Failed to delete', 'error'); }
  }));
  tbody.querySelectorAll('[data-edit-news]').forEach(btn => btn.addEventListener('click', async () => {
    const paper = rows.find(n => String(n.id) === btn.dataset.editNews);
    if (!paper) return;
    openModal('newspaper', async (data) => {
      await newspapersApi.update(paper.id, data);
      toast('Newspaper updated', 'success');
      load();
    }, paper);
  }));
}

function renderStudents(rows) {
  const grid = document.querySelector('#students .people-grid');
  if (!grid) return;
  grid.innerHTML = rows.map(s => `<div class="person-card"><img src="https://api.dicebear.com/7.x/notionists/svg?seed=${encodeURIComponent(s.name.replace(/ /g,'-'))}&backgroundColor=eef0fb" alt=""><b>${esc(s.name)}</b><small>${esc(s.email)} · ${esc(s.phone)}</small><div class="person-stats"><span>${esc(s.username)}</span><span class="badge badge-avail">Active</span></div></div>`).join("") || '<div class="empty-state">No students found.</div>';
}

function renderLibrarians(rows) {
  const tbody = document.querySelector('#librarians table tbody');
  if (!tbody) return;
  tbody.innerHTML = rows.map(l => `<tr><td class="person-row"><img src="https://api.dicebear.com/7.x/notionists/svg?seed=${encodeURIComponent(l.name.replace(/ /g,'-'))}&backgroundColor=fbeee0" alt="">${esc(l.name)}</td><td>Administration</td><td><span class="tag tag-role role-lib">${esc(l.role)}</span></td><td>—</td><td><span class="badge badge-avail">Active</span></td><td class="row-actions"><button class="link-btn" data-edit-lib="${l.id}">Edit</button> · <button class="link-btn" data-delete-lib="${l.id}">Delete</button></td></tr>`).join("") || '<tr><td colspan="6" class="empty-state" style="padding: 24px; text-align: center; color: var(--ink-soft);">No librarians found.</td></tr>';

  tbody.querySelectorAll('[data-delete-lib]').forEach(btn => btn.addEventListener('click', async () => {
    if (!await confirmDialog('Delete this librarian?', 'Delete')) return;
    try { await librariansApi.delete(btn.dataset.deleteLib); toast('Librarian deleted', 'success'); load(); }
    catch (e) { toast(e.message || 'Failed to delete', 'error'); }
  }));
  tbody.querySelectorAll('[data-edit-lib]').forEach(btn => btn.addEventListener('click', async () => {
    const lib = rows.find(l => String(l.id) === btn.dataset.editLib);
    if (!lib) return;
    openModal('librarian', async (data) => {
      await librariansApi.update(lib.id, data);
      toast('Librarian updated', 'success');
      load();
    }, lib);
  }));
}

function renderRecords(rows) {
  const timeline = document.querySelector('#borrow .timeline');
  if (!timeline) return;
  timeline.innerHTML = rows.map(r => `<div class="tl-row"><div class="tl-dot ${r.status === 'BORROWED' ? 'dot-out' : 'dot-in'}"></div><div class="tl-body"><b>${esc(r.itemTitle)}</b> (${esc(r.itemType)}) ${r.status === 'BORROWED' ? 'issued to' : 'returned by'} ${esc(r.borrowerName)} <span class="badge ${r.status === 'BORROWED' ? 'badge-out' : 'badge-return'}">${r.status}</span><small>${r.borrowDate} — ${r.returnDate ?? 'Not returned'}</small></div>${r.status === 'BORROWED' ? `<button data-return="${r.id}" class="btn-ghost sm">Quick return</button>` : `<button class="btn-ghost sm" disabled>Closed</button>`}</div>`).join("") || '<div class="empty-state">No records found.</div>';

  // Wire Quick Return with confirmation
  timeline.querySelectorAll("[data-return]").forEach(btn => btn.addEventListener('click', async () => {
    if (!await confirmDialog(`Return "${rows.find(r => String(r.id) === btn.dataset.return)?.itemTitle || 'this item'}"?`, 'Return')) return;
    try {
      await borrowApi.returnBook(btn.dataset.return);
      toast('Book returned successfully', 'success');
      load();
    } catch (e) { toast(e.message || 'Return failed', 'error'); }
  }));
}

/* ── Page initialization ─────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  // Auth check: if user is already logged in, load data
  const loginView = document.getElementById("login-view");

  if (loginView) {
    authApi.csrf().then(() => authApi.me()).then(user => {
      setCurrentUser(user);
      currentUser = user;
      loginView.classList.add("hidden");

      // Route to appropriate dashboard based on role
      if (currentUser.role === 'STUDENT') window.location.hash = '#/student-dashboard';
      else if (currentUser.role === 'LIBRARIAN') window.location.hash = '#/librarian-dashboard';
      else window.location.hash = '#/dashboard';

      return load();
    }).catch(() => {
      loginView.classList.remove("hidden");
    });
  }

  // Modal open buttons
  document.querySelectorAll("[data-open-modal]").forEach(button => {
    button.addEventListener("click", () => {
      const kind = button.dataset.openModal;
      openModal(kind, async (data) => {
        if (kind === "book") await booksApi.create(data);
        if (kind === "magazine") await magazinesApi.create(data);
        if (kind === "newspaper") await newspapersApi.create(data);
        if (kind === "student") await studentsApi.create(data);
        if (kind === "librarian") await librariansApi.create(data);
        if (kind === "borrow") await borrowApi.borrowBook(data);
        toast(`${kind.charAt(0).toUpperCase() + kind.slice(1)} created`, 'success');
        await load();
      });
    });
  });

  // Login form — uses toast instead of alert()
  const loginForm = document.getElementById("login-form");
  if (loginForm) loginForm.addEventListener("submit", async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    try {
      await authApi.login({ username: fd.get("username"), password: fd.get("password") });
      loginView.classList.add("hidden");
      const user = await authApi.me();
      currentUser = user;
      toast('Welcome back!', 'success');
      await load();
    } catch (err) {
      toast("Invalid credentials. Please try again.", "error");
    }
  });

  // Books grid/list toggle
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

  // IntersectionObserver for dashboard section scrolling
  const links = document.querySelectorAll('.rail-list a[href^="#"]');
  const pages = document.querySelectorAll('.page');
  if (pages.length) {
    const io = new IntersectionObserver((entries) => {
      entries.forEach(e => {
        if (e.isIntersecting) {
          links.forEach(l => l.classList.remove('active'));
          const match = document.querySelector('.rail-list a[href="#' + e.target.id + '"]');
          if (match) match.classList.add('active');
        }
      });
    }, { rootMargin: '-40% 0px -55% 0px' });
    pages.forEach(p => io.observe(p));
  }
});
