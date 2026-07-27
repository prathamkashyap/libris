import { librariansApi } from "/js/api/librarians-api.js";
import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";
import { renderPagination, renderPageInfo } from "/js/utils/pagination.js";

let state = { page: 0, size: 10, search: "", totalPages: 0, totalElements: 0, librarians: [], loading: false };

const tbody = document.querySelector(".card.table-card tbody");
const foot = document.querySelector(".card.table-card .table-foot");
const muted = foot?.querySelector(".muted");
const pager = foot?.querySelector(".pager");
const searchInput = document.querySelector(".search input");

function showLoading() {
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:48px;color:var(--ink-soft)">Loading librarians&hellip;</td></tr>';
}

function showEmpty() {
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:48px;color:var(--ink-soft)">No librarians found.</td></tr>';
}

function showError(msg) {
  if (!tbody) return;
  tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:48px;color:var(--red)">${esc(msg)}<br><button class="btn-ghost sm" style="margin-top:8px" onclick="location.reload()">Try again</button></td></tr>`;
}

async function loadLibrarians() {
  if (state.loading) return;
  state.loading = true;
  showLoading();
  try {
    const data = await librariansApi.list(state.page, state.size);
    state.librarians = data.content || [];
    state.totalPages = data.totalPages || 0;
    state.totalElements = data.totalElements || 0;
    if (state.librarians.length === 0) showEmpty();
    else renderTable();
    if (muted) renderPageInfo(muted, data.number, data.size, data.totalElements);
    if (pager) renderPagination(pager, data.number, data.totalPages, p => { state.page = p; loadLibrarians(); });
  } catch (err) {
    showError(err.message || "Failed to load librarians.");
  } finally {
    state.loading = false;
  }
}

function renderTable() {
  if (!tbody) return;
  tbody.innerHTML = state.librarians.map(l => `
    <tr>
      <td class="person-row">
        <img src="https://api.dicebear.com/7.x/notionists/svg?seed=${esc((l.name||"").replace(/\s+/g,"-"))}&backgroundColor=fbeee0" alt="">
        <a href="librarian-profile.html?id=${l.id}">${esc(l.name)}</a>
      </td>
      <td>—</td>
      <td><span class="tag tag-role role-lib">${esc(l.role || "Librarian")}</span></td>
      <td>—</td>
      <td><span class="badge badge-avail">Active</span></td>
      <td class="row-actions">
        <button class="btn-ghost sm edit-librarian" data-id="${l.id}">Edit</button>
        <button class="btn-ghost sm delete-librarian" data-id="${l.id}" style="color:var(--red)">Delete</button>
      </td>
    </tr>
  `).join("");

  tbody.querySelectorAll(".edit-librarian").forEach(btn => btn.addEventListener("click", () => {
    const l = state.librarians.find(x => x.id === parseInt(btn.dataset.id));
    if (l) openLibrarianModal(l);
  }));
  tbody.querySelectorAll(".delete-librarian").forEach(btn => btn.addEventListener("click", () => {
    deleteLibrarian(parseInt(btn.dataset.id));
  }));
}

function openLibrarianModal(librarian) {
  const isEdit = !!librarian;
  const root = document.getElementById("modal-root");
  root.innerHTML = `
    <div class="modal-backdrop" role="presentation">
      <section class="modal" role="dialog" aria-modal="true">
        <div class="modal-header">
          <h2 class="serif">${isEdit ? "Edit librarian" : "Add librarian"}</h2>
          <button class="modal-close modal-close-lib" type="button">&times;</button>
        </div>
        <p class="form-error" data-lib-error hidden></p>
        <form id="lib-form" novalidate>
          <div class="form-grid">
            <label class="field span-all"><span>Name *</span><input name="name" value="${isEdit ? esc(librarian.name) : ""}" required><span class="field-error" data-error="name"></span></label>
            <label class="field"><span>Age *</span><input name="age" type="number" value="${isEdit ? (librarian.age || "") : ""}" required><span class="field-error" data-error="age"></span></label>
            <label class="field"><span>Phone *</span><input name="phone" type="tel" value="${isEdit ? esc(librarian.phone || "") : ""}" required><span class="field-error" data-error="phone"></span></label>
            <label class="field"><span>Username *</span><input name="username" value="${isEdit ? esc(librarian.username || "") : ""}" required><span class="field-error" data-error="username"></span></label>
            ${isEdit ? "" : '<label class="field"><span>Password *</span><input name="password" type="password" required><span class="field-error" data-error="password"></span></label>'}
          </div>
          <div class="modal-footer">
            <button class="btn-ghost modal-close-lib" type="button">Cancel</button>
            <button class="btn-primary" type="submit">${isEdit ? "Save changes" : "Add librarian"}</button>
          </div>
        </form>
      </section>
    </div>`;
  const close = () => { root.innerHTML = ""; };
  root.querySelectorAll(".modal-close-lib").forEach(b => b.addEventListener("click", close));
  root.querySelector(".modal-backdrop").addEventListener("click", e => { if (e.target === e.currentTarget) close(); });
  const fi = root.querySelector("input"); if (fi) fi.focus();
  root.querySelector("form").addEventListener("submit", async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const values = Object.fromEntries(fd);
    const errEl = root.querySelector("[data-lib-error]");
    errEl.hidden = true;
    if (!values.name?.trim()) { root.querySelector('[data-error="name"]').textContent = "Name is required."; return; }
    try {
      if (isEdit) { await librariansApi.update(librarian.id, values); toast("Librarian updated.", "success"); }
      else { await librariansApi.create(values); toast("Librarian added.", "success"); }
      close(); loadLibrarians();
    } catch (err) {
      const fieldErrors = err.fieldErrors || [];
      fieldErrors.forEach(({ field, message }) => { const fe = root.querySelector(`[data-error="${field}"]`); if (fe) fe.textContent = message; });
      const unmatched = fieldErrors.filter(({ field }) => !root.querySelector(`[data-error="${field}"]`)).map(({ message }) => message);
      errEl.textContent = unmatched.join(" ") || err.message || "Failed to save.";
      errEl.hidden = false;
    }
  });
}

async function deleteLibrarian(id) {
  const l = state.librarians.find(x => x.id === id);
  const ok = await confirmDialog(`Delete <strong>${esc(l ? l.name : "this librarian")}</strong>? This cannot be undone.`, "Delete");
  if (!ok) return;
  try { await librariansApi.delete(id); toast("Librarian deleted.", "success"); if (state.librarians.length === 1 && state.page > 0) state.page--; loadLibrarians(); }
  catch (err) { toast(err.message || "Failed to delete.", "error"); }
}

async function initAuth() {
  try { await authApi.csrf(); const u = await authApi.me(); setCurrentUser(u); } catch {}
}

const addLibrarianBtn = document.getElementById("addLibrarianBtn");

let searchTimer;
document.addEventListener("DOMContentLoaded", () => {
  initAuth();
  if (searchInput) searchInput.addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { state.search = searchInput.value.trim(); state.page = 0; loadLibrarians(); }, 300);
  });
  if (addLibrarianBtn) addLibrarianBtn.addEventListener("click", () => openLibrarianModal(null));
  loadLibrarians();
});
