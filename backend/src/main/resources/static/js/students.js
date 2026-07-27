import { studentsApi } from "/js/api/students-api.js";
import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";
import { renderPagination, renderPageInfo } from "/js/utils/pagination.js";

let state = { page: 0, size: 12, search: "", totalPages: 0, totalElements: 0, students: [], loading: false };

const gridEl = document.querySelector(".people-grid");
const searchInput = document.querySelector(".search input");
const pageInfoEl = document.createElement("div");
const pagerEl = document.createElement("div");
const tableFoot = document.createElement("div");

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
  gridEl.innerHTML = '<div style="grid-column:1/-1;text-align:center;padding:48px 0;color:var(--ink-soft)">Loading students&hellip;</div>';
}

function showEmpty() {
  if (!gridEl) return;
  gridEl.innerHTML = `<div style="grid-column:1/-1;text-align:center;padding:48px 0">
    <p style="color:var(--ink-soft);font-size:14px;margin:0">${state.search ? "No students match your search." : "No students registered yet."}</p>
  </div>`;
}

function showError(msg) {
  if (!gridEl) return;
  gridEl.innerHTML = `<div style="grid-column:1/-1;text-align:center;padding:48px 0">
    <p style="color:var(--red);font-size:14px;margin:0">${esc(msg)}</p>
    <button class="btn-ghost sm" style="margin-top:12px" onclick="location.reload()">Try again</button>
  </div>`;
}

async function loadStudents() {
  if (state.loading) return;
  state.loading = true;
  showLoading();
  try {
    const data = await studentsApi.list(state.page, state.size);
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
    <div class="person-card" data-id="${s.id}" tabindex="0" role="button">
      <img src="https://api.dicebear.com/7.x/notionists/svg?seed=${esc((s.name||"User").replace(/\s+/g,"-"))}&backgroundColor=eef0fb" alt="">
      <b>${esc(s.name)}</b>
      <small>${esc(s.email || "")} ${s.phone ? "· " + esc(s.phone) : ""}</small>
      <div class="person-stats">
        <span>${esc(s.username || "")}</span>
        <span class="badge badge-avail">Active</span>
      </div>
      <div style="display:flex;gap:6px;margin-top:8px;width:100%">
        <button class="btn-ghost sm edit-student" data-id="${s.id}" style="flex:1">Edit</button>
        <button class="btn-ghost sm delete-student" data-id="${s.id}" style="flex:1;color:var(--red)">Delete</button>
      </div>
    </div>
  `).join("");

  gridEl.querySelectorAll(".person-card").forEach(card => card.addEventListener("click", e => {
    if (e.target.closest("button")) return;
    const id = card.dataset.id;
    if (id) window.location.href = `student-profile.html?id=${id}`;
  }));
  gridEl.querySelectorAll(".edit-student").forEach(btn => btn.addEventListener("click", () => {
    const s = state.students.find(x => x.id === parseInt(btn.dataset.id));
    if (s) openStudentModal(s);
  }));
  gridEl.querySelectorAll(".delete-student").forEach(btn => btn.addEventListener("click", () => {
    deleteStudent(parseInt(btn.dataset.id));
  }));
}

function openStudentModal(student) {
  const isEdit = !!student;
  const root = document.getElementById("modal-root");
  root.innerHTML = `
    <div class="modal-backdrop" role="presentation">
      <section class="modal" role="dialog" aria-modal="true">
        <div class="modal-header">
          <h2 class="serif">${isEdit ? "Edit student" : "Add student"}</h2>
          <button class="modal-close modal-close-student" type="button">&times;</button>
        </div>
        <p class="form-error" data-student-error hidden></p>
        <form id="student-form" novalidate>
          <div class="form-grid">
            <label class="field span-all"><span>Name *</span><input name="name" value="${isEdit ? esc(student.name) : ""}" required><span class="field-error" data-error="name"></span></label>
            <label class="field"><span>Email *</span><input name="email" type="email" value="${isEdit ? esc(student.email || "") : ""}" required><span class="field-error" data-error="email"></span></label>
            <label class="field"><span>Phone *</span><input name="phone" type="tel" value="${isEdit ? esc(student.phone || "") : ""}" required><span class="field-error" data-error="phone"></span></label>
            <label class="field"><span>Username *</span><input name="username" value="${isEdit ? esc(student.username || "") : ""}" required><span class="field-error" data-error="username"></span></label>
            ${isEdit ? "" : '<label class="field"><span>Password *</span><input name="password" type="password" required><span class="field-error" data-error="password"></span></label>'}
          </div>
          <div class="modal-footer">
            <button class="btn-ghost modal-close-student" type="button">Cancel</button>
            <button class="btn-primary" type="submit">${isEdit ? "Save changes" : "Add student"}</button>
          </div>
        </form>
      </section>
    </div>`;
  const close = () => { root.innerHTML = ""; };
  root.querySelectorAll(".modal-close-student").forEach(b => b.addEventListener("click", close));
  root.querySelector(".modal-backdrop").addEventListener("click", e => { if (e.target === e.currentTarget) close(); });
  const fi = root.querySelector("input");
  if (fi) fi.focus();
  root.querySelector("form").addEventListener("submit", async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const values = Object.fromEntries(fd);
    const errEl = root.querySelector("[data-student-error]");
    errEl.hidden = true;
    if (!values.name?.trim()) { root.querySelector('[data-error="name"]').textContent = "Name is required."; return; }
    try {
      if (isEdit) { await studentsApi.update(student.id, values); toast("Student updated.", "success"); }
      else { await studentsApi.create(values); toast("Student added.", "success"); }
      close(); loadStudents();
    } catch (err) {
      const fieldErrors = err.fieldErrors || [];
      fieldErrors.forEach(({ field, message }) => { const fe = root.querySelector(`[data-error="${field}"]`); if (fe) fe.textContent = message; });
      const unmatched = fieldErrors.filter(({ field }) => !root.querySelector(`[data-error="${field}"]`)).map(({ message }) => message);
      errEl.textContent = unmatched.join(" ") || err.message || "Failed to save.";
      errEl.hidden = false;
    }
  });
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
document.addEventListener("DOMContentLoaded", () => {
  initAuth();
  setupFooter();
  if (searchInput) searchInput.addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { state.search = searchInput.value.trim(); state.page = 0; loadStudents(); }, 300);
  });
  if (addBtn) addBtn.addEventListener("click", () => openStudentModal(null));
  loadStudents();
});
