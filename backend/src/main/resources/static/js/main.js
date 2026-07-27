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

let currentUser = getCurrentUser();
const esc = v => String(v ?? "").replace(/[&<>'"]/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;","'":"&#39;",'"':"&quot;"})[c]);

async function load() {
  if (!currentUser) return;
  
  // Load role-specific data
  if (currentUser.role === 'STUDENT') {
    const [studentDashboard, borrowHistory] = await Promise.all([
      studentDashboardApi.getDashboard(),
      studentDashboardApi.getBorrowHistory()
    ]);
    
    // Populate Student Dashboard
    document.getElementById('sidebar-username').textContent = currentUser.name || currentUser.username;
    document.getElementById('sidebar-role').textContent = 'Student';
    document.getElementById('profile-name').textContent = currentUser.name || currentUser.username;
    document.getElementById('profile-role').textContent = 'Student';
    document.getElementById('profile-username').textContent = currentUser.username;
    document.getElementById('dashboard-greeting').textContent = `Good morning, ${currentUser.name || currentUser.username.split(' ')[0]}.`;
    
    // Populate student dashboard
    document.querySelector('#student-dashboard .card-head h3').nextElementSibling.textContent = `Welcome, ${studentDashboard.name}`;
    document.getElementById('student-current-borrows').innerHTML = studentDashboard.currentBorrows.map(b => 
      `<div class="tl-row"><div class="tl-dot dot-out"></div><div class="tl-body"><b>${b.itemTitle}</b> (${b.itemType}) issued to you <span class="badge badge-out">${b.status}</span><small>${b.borrowDate} — ${b.returnDate ?? 'Not returned'}</small></div></div>`
    ).join('') || '<div class="empty-state">No active borrows.</div>';
    
    document.getElementById('student-borrow-history').innerHTML = borrowHistory.content.map(r =>
      `<div class="tl-row"><div class="tl-dot ${r.status === 'RETURNED' ? 'dot-in' : 'dot-out'}"></div><div class="tl-body"><b>${r.itemTitle}</b> (${r.itemType}) ${r.status === 'BORROWED' ? 'issued to' : 'returned by'} you <span class="badge ${r.status === 'BORROWED' ? 'badge-out' : 'badge-return'}">${r.status}</span><small>${r.borrowDate} — ${r.returnDate ?? 'Not returned'}</small></div></div>`
    ).join('') || '<div class="empty-state">No borrow history yet.</div>';
    
    return;
  }
  
  // Load Librarian/Admin data
  const [books, magazines, newspapers, students, librarians, records, dashboard] = await Promise.all([
    booksApi.list(), magazinesApi.list(), newspapersApi.list(),
    studentsApi.list(), librariansApi.list(), borrowApi.list(), dashboardApi.get()
  ]);
  
  // Populate User info
  document.getElementById('sidebar-username').textContent = currentUser.name || currentUser.username;
  document.getElementById('sidebar-role').textContent = currentUser.role || 'Member';
  document.getElementById('profile-name').textContent = currentUser.name || currentUser.username;
  document.getElementById('profile-role').textContent = currentUser.role || 'Member';
  document.getElementById('profile-username').textContent = currentUser.username;
  document.getElementById('dashboard-greeting').textContent = `Good morning, ${currentUser.name || currentUser.username.split(' ')[0]}.`;
  
  // Populate Dashboard Stats
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

function renderBooks(rows) {
  const tbody = document.querySelector('#books table tbody');
  if (!tbody) return;
  tbody.innerHTML = rows.map(b => `<tr><td><input type="checkbox"></td><td class="ttl"><div class="cov cov-1"></div>${esc(b.title)}</td><td>${esc(b.author)}</td><td><span class="tag">Book</span></td><td class="mono">${esc(b.isbn)}</td><td><span class="badge ${b.available ? "badge-avail" : "badge-out"}">${b.available ? "Available" : "Checked out"}</span></td><td class="row-actions">View · Edit · ⋯</td></tr>`).join("") || '<tr><td colspan="7" class="empty-state" style="padding: 24px; text-align: center; color: #9CA3C0;">No books found.</td></tr>';
}

function renderMagazines(rows) {
  const tbody = document.querySelector('#magazines table tbody');
  if (!tbody) return;
  tbody.innerHTML = rows.map(m => `<tr><td class="ttl">${esc(m.title)}</td><td>${esc(m.publisher)}</td><td>${m.issueDate ?? "—"}</td><td><span class="badge ${m.available ? "badge-avail" : "badge-out"}">${m.available ? "Available" : "Checked out"}</span></td><td class="row-actions">View · Edit · ⋯</td></tr>`).join("") || '<tr><td colspan="5" class="empty-state" style="padding: 24px; text-align: center; color: #9CA3C0;">No magazines found.</td></tr>';
}

function renderNewspapers(rows) {
  const tbody = document.querySelector('#newspapers table tbody');
  if (!tbody) return;
  tbody.innerHTML = rows.map(n => `<tr><td class="ttl">${esc(n.title)}</td><td>${n.publicationDate ?? "—"}</td><td><span class="badge ${n.available ? "badge-avail" : "badge-out"}">${n.available ? "Available" : "Checked out"}</span></td><td class="row-actions">View · Edit · ⋯</td></tr>`).join("") || '<tr><td colspan="4" class="empty-state" style="padding: 24px; text-align: center; color: #9CA3C0;">No newspapers found.</td></tr>';
}

function renderStudents(rows) {
  // In the new UI, students are in .people-grid
  const grid = document.querySelector('#students .people-grid');
  if (!grid) return;
  grid.innerHTML = rows.map(s => `<div class="person-card"><img src="https://api.dicebear.com/7.x/notionists/svg?seed=${esc(s.name.replace(' ','-'))}&backgroundColor=eef0fb" alt=""><b>${esc(s.name)}</b><small>${esc(s.email)} · ${esc(s.phone)}</small><div class="person-stats"><span>${esc(s.username)}</span><span class="badge badge-avail">Active</span></div></div>`).join("") || '<div class="empty-state">No students found.</div>';
}

function renderLibrarians(rows) {
  const tbody = document.querySelector('#librarians table tbody');
  if (!tbody) return;
  tbody.innerHTML = rows.map(l => `<tr><td class="person-row"><img src="https://api.dicebear.com/7.x/notionists/svg?seed=${esc(l.name.replace(' ','-'))}&backgroundColor=fbeee0" alt="">${esc(l.name)}</td><td>Administration</td><td><span class="tag tag-role role-lib">${esc(l.role)}</span></td><td>—</td><td><span class="badge badge-avail">Active</span></td><td class="row-actions">⋯</td></tr>`).join("") || '<tr><td colspan="6" class="empty-state" style="padding: 24px; text-align: center; color: #9CA3C0;">No librarians found.</td></tr>';
}

function renderRecords(rows) {
  const timeline = document.querySelector('#borrow .timeline');
  if (!timeline) return;
  timeline.innerHTML = rows.map(r => `<div class="tl-row"><div class="tl-dot ${r.status === 'BORROWED' ? 'dot-out' : 'dot-in'}"></div><div class="tl-body"><b>${esc(r.itemTitle)}</b> (${esc(r.itemType)}) ${r.status === 'BORROWED' ? 'issued to' : 'returned by'} ${esc(r.borrowerName)} <span class="badge ${r.status === 'BORROWED' ? 'badge-out' : 'badge-return'}">${r.status}</span><small>${r.borrowDate} — ${r.returnDate ?? 'Not returned'}</small></div>${r.status === 'BORROWED' ? `<button data-return="${r.id}" class="btn-ghost sm">Quick return</button>` : `<button class="btn-ghost sm" disabled>Closed</button>`}</div>`).join("") || '<div class="empty-state">No records found.</div>';
  
  document.querySelectorAll("[data-return]").forEach(b => b.onclick = async () => {
    await borrowApi.returnBook(b.dataset.return);
    load();
  });
}

document.addEventListener('DOMContentLoaded', () => {
  // Theme handling
  const theme = localStorage.getItem('theme') || 'blue';
  if (theme === 'pink') {
    document.documentElement.setAttribute('data-theme', 'pink');
  }
  const toggleBtn = document.getElementById('theme-toggle');
  if (toggleBtn) {
    toggleBtn.textContent = theme === 'pink' ? 'Switch to Blue Theme' : 'Switch to Pink Theme';
    toggleBtn.addEventListener('click', () => {
      const currentTheme = document.documentElement.getAttribute('data-theme');
      if (currentTheme === 'pink') {
        document.documentElement.removeAttribute('data-theme');
        localStorage.setItem('theme', 'blue');
        toggleBtn.textContent = 'Switch to Pink Theme';
      } else {
        document.documentElement.setAttribute('data-theme', 'pink');
        localStorage.setItem('theme', 'pink');
        toggleBtn.textContent = 'Switch to Blue Theme';
      }
    });
  }

  // IntersectionObserver for sidebar links
  const links = document.querySelectorAll('.rail-list a[href^="#"]');
  const pages = document.querySelectorAll('.page');
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

  // Initialize data
  const loginView = document.getElementById("login-view");
  const registerView = document.getElementById("register-view");

  // Handle initial page load - check if we're on a specific route
  const path = window.location.pathname;
  if (path === '/register') {
    loginView.classList.add("hidden");
    registerView.classList.remove("hidden");
  } else if (path === '/login') {
    loginView.classList.remove("hidden");
    registerView.classList.add("hidden");
  }

  authApi.csrf().then(() => authApi.me()).then(user => {
    setCurrentUser(user);
    currentUser = user;
    loginView.classList.add("hidden");
    registerView.classList.add("hidden");
    
    // Route to appropriate dashboard based on role
    if (currentUser.role === 'STUDENT') {
      window.location.hash = '#/student-dashboard';
    } else if (currentUser.role === 'LIBRARIAN') {
      window.location.hash = '#/librarian-dashboard';
    } else {
      window.location.hash = '#/dashboard'; // Admin
    }
    
    return load();
  }).catch(() => {
    // Not authenticated - show appropriate view based on path
    if (path === '/register') {
      registerView.classList.remove("hidden");
    } else {
      loginView.classList.remove("hidden");
    }
  });

  // Registration form
  document.getElementById("register-form").addEventListener("submit", async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const password = fd.get("password");
    const confirmPassword = fd.get("confirmPassword");
    
    if (password !== confirmPassword) {
      alert("Passwords do not match");
      return;
    }
    
    try {
      // Use the librariansApi to create a new librarian
      await librariansApi.create({
        name: fd.get("name"),
        age: parseInt(fd.get("age")),
        phone: fd.get("phone"),
        username: fd.get("username"),
        password: password
      });
      alert("Registration successful! You can now sign in.");
      window.location.href = '/login';
    } catch (err) {
      alert(err.message || "Registration failed");
    }
  });

  // Modals
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
        await load();
      });
    });
  });

  // Login form
  document.getElementById("login-form").addEventListener("submit", async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    try {
      await authApi.login({ username: fd.get("username"), password: fd.get("password") });
      loginView.classList.add("hidden");
      const user = await authApi.me();
      currentUser = user;
      await load();
    } catch (err) {
      alert("Invalid credentials");
    }
  });

  // Logout
  document.querySelectorAll("[data-action='logout']").forEach(btn => {
    btn.addEventListener("click", async e => {
      e.preventDefault();
      await authApi.logout();
      setCurrentUser(null);
      currentUser = null;
      loginView.classList.remove("hidden");
      window.location.hash = '#/login';
    });
  });
});
