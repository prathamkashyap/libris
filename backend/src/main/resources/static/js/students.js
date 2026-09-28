import { studentsApi } from "/js/api/students-api.js";
import { authApi } from "/js/api/auth-api.js";
import { getCurrentUser, setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";
import { renderPagination, renderPageInfo } from "/js/utils/pagination.js";
import { openModal } from "/components/modal.js";
import { avatarMarkup } from "/js/utils/avatar.js";
let state = { page: 0, size: 12, search: "", totalPages: 0, totalElements: 0, students: [], loading: false };

const gridEl = document.querySelector(".people-grid");
const searchInput = document.querySelector(".search input");
const pageInfoEl = document.createElement("div");
const pagerEl = document.createElement("div");
const tableFoot = document.createElement("div");

function canManage() {
  return ["ADMIN", "LIBRARIAN"].includes((getCurrentUser()?.role || "").toUpperCase());
}

function showStaffOnly() {
  document.querySelector(".toolbar")?.setAttribute("hidden", "");
  if (gridEl) gridEl.innerHTML = '<div class="empty-state"><p>Student account management is available to library staff.</p><a class="btn-ghost sm" href="/index.html">Open student dashboard</a></div>';
}

function setupFooter() {
  const parent = gridEl?.parentNode;
  if (!parent || parent.contains(tableFoot)) return;
  tableFoot.className = "table-foot";
  pageInfoEl.className = "muted";
  tableFoot.appendChild(pageInfoEl);
  tableFoot.appendChild(pagerEl);
  parent.appendChild(tableFoot);
}

function showLoading() {
  if (!gridEl) return;
  gridEl.innerHTML = '<tr><td colspan="6"><div class="loading-state">Loading students…</div></td></tr>';
}

function showEmpty() {
  if (!gridEl) return;
  const message = state.search ? "No students match your search." : "No students yet";
  const description = state.search ? "Try adjusting your search terms." : "Add students to start managing your library community.";
  gridEl.innerHTML = `
    <tr><td colspan="6">
      <div class="empty-state">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>
        <h3>${message}</h3>
        <p>${description}</p>
        ${!state.search && canManage() ? '<button class="btn-primary" id="emptyAddStudent">Add student</button>' : ''}
      </div>
    </td></tr>
  `;
  const emptyAddBtn = document.getElementById('emptyAddStudent');
  if (emptyAddBtn) {
    emptyAddBtn.addEventListener('click', () => openStudentModal(null));
  }
}

function showError(msg) {
  if (!gridEl) return;
  gridEl.innerHTML = `<tr><td colspan="6"><div class="error-state"><p>${esc(msg)}</p><button class="btn-ghost sm" type="button" data-retry>Try again</button></div></td></tr>`;
  gridEl.querySelector("[data-retry]")?.addEventListener("click", loadStudents);
}

async function loadStudents() {
  if (state.loading) return;
  state.loading = true;
  showLoading();
  try {
    const data = await studentsApi.list(state.page, state.size, state.search);
    state.students = data.content || [];
    state.totalPages = data.totalPages || 0;
    state.totalElements = data.totalElements || 0;
    if (state.students.length === 0) { showEmpty(); }
    else { renderGrid(); }
    if (pageInfoEl) renderPageInfo(pageInfoEl, data.number, data.size, data.totalElements);
    if (pagerEl) renderPagination(pagerEl, data.number, data.totalPages, p => { state.page = p; loadStudents(); });
  } catch (err) {
    showError(err.message || "Failed to load students.");
  } finally {
    state.loading = false;
  }
}

function renderGrid() {
  if (!gridEl) return;
  gridEl.innerHTML = state.students.map(s => `
    <tr class="person-row" data-id="${s.id}" tabindex="0" role="button">
      <td data-label="Student">
        <div class="person-cell">
          ${avatarMarkup(s.name)}
          <div>
            <div class="person-name">${esc(s.name)}</div>
            <small class="muted">${esc(s.email || "No email on file")}</small>
          </div>
        </div>
      </td>
      <td data-label="Username">${esc(s.username || "")}</td>
      <td data-label="Email">${esc(s.email || "—")}</td>
      <td data-label="Phone">${esc(s.phone || "—")}</td>
      <td data-label="Role"><span class="badge badge-muted">${esc(s.role || "Student")}</span></td>
      <td data-label="Actions">
        ${canManage() ? `<div class="row-actions">
          <button class="btn-ghost sm edit-student" data-id="${s.id}" type="button">Edit</button>
          <button class="btn-ghost sm delete-student" data-id="${s.id}" type="button">Delete</button>
        </div>` : `<button class="btn-ghost sm view-student" data-id="${s.id}" type="button">View</button>`}
      </td>
    </tr>
  `).join("");

  gridEl.querySelectorAll(".person-row").forEach(row => row.addEventListener("click", e => {
    if (e.target.closest("button")) return;
    const id = row.dataset.id;
    if (id) window.location.href = `student-profile.html?id=${id}`;
  }));
  gridEl.querySelectorAll(".person-row").forEach(row => row.addEventListener("keydown", event => {
    if ((event.key === "Enter" || event.key === " ") && !event.target.closest("button")) {
      event.preventDefault();
      row.click();
    }
  }));
  gridEl.querySelectorAll(".edit-student").forEach(btn => btn.addEventListener("click", () => {
    const s = state.students.find(x => x.id === parseInt(btn.dataset.id));
    if (s) openStudentModal(s);
  }));
  gridEl.querySelectorAll(".delete-student").forEach(btn => btn.addEventListener("click", () => {
    deleteStudent(parseInt(btn.dataset.id));
  }));
  gridEl.querySelectorAll(".view-student").forEach(btn => btn.addEventListener("click", () => {
    const id = btn.dataset.id;
    if (id) window.location.href = `student-profile.html?id=${id}`;
  }));
}

function openStudentModal(student) {
  openModal('student', async (data) => {
    if (student) {
      await studentsApi.update(student.id, data);
      toast("Student updated.", "success");
    } else {
      await studentsApi.create(data);
      toast("Student added.", "success");
    }
    loadStudents();
  }, student);
}

async function deleteStudent(id) {
  const s = state.students.find(x => x.id === id);
  const ok = await confirmDialog(`Delete <strong>${esc(s ? s.name : "this student")}</strong>? This cannot be undone.`, "Delete");
  if (!ok) return;
  try { await studentsApi.delete(id); toast("Student deleted.", "success"); if (state.students.length === 1 && state.page > 0) state.page--; loadStudents(); }
  catch (err) { toast(err.message || "Failed to delete.", "error"); }
}

const addBtn = document.getElementById("addStudentBtn");

async function initAuth() {
  try { await authApi.csrf(); const u = await authApi.me(); setCurrentUser(u); } catch {}
}

let searchTimer;
document.addEventListener("DOMContentLoaded", async () => {
  await initAuth();
  if (!canManage()) {
    showStaffOnly();
    return;
  }
  if (addBtn) addBtn.hidden = false;
  setupFooter();
  if (searchInput) searchInput.addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { state.search = searchInput.value.trim(); state.page = 0; loadStudents(); }, 300);
  });
  if (addBtn) addBtn.addEventListener("click", () => openStudentModal(null));
  loadStudents();
});
