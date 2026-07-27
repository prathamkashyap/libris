import { magazinesApi } from "/js/api/magazines-api.js";
import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";
import { renderPagination, renderPageInfo } from "/js/utils/pagination.js";

let state = { page: 0, size: 10, search: "", totalPages: 0, totalElements: 0, magazines: [], loading: false };

const tbody = document.querySelector(".card.table-card tbody");
const foot = document.querySelector(".card.table-card .table-foot");
const muted = foot?.querySelector(".muted");
const pager = foot?.querySelector(".pager");
const searchInput = document.querySelector(".search input");
const addBtn = document.querySelector(".btn-primary");

function showLoading() {
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:48px;color:var(--ink-soft)">Loading magazines&hellip;</td></tr>';
}

function showEmpty() {
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:48px;color:var(--ink-soft)">No magazines found.</td></tr>';
}

function showError(msg) {
  if (!tbody) return;
  tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:48px;color:var(--red)">${esc(msg)}<br><button class="btn-ghost sm" style="margin-top:8px" onclick="location.reload()">Try again</button></td></tr>`;
}

async function loadMagazines() {
  if (state.loading) return;
  state.loading = true;
  showLoading();
  try {
    const data = await magazinesApi.list(state.search, state.page, state.size);
    state.magazines = data.content || [];
    state.totalPages = data.totalPages || 0;
    state.totalElements = data.totalElements || 0;
    if (state.magazines.length === 0) showEmpty();
    else renderTable();
    if (muted) renderPageInfo(muted, data.number, data.size, data.totalElements);
    if (pager) renderPagination(pager, data.number, data.totalPages, p => { state.page = p; loadMagazines(); });
  } catch (err) {
    showError(err.message || "Failed to load magazines.");
  } finally {
    state.loading = false;
  }
}

function renderTable() {
  if (!tbody) return;
  tbody.innerHTML = state.magazines.map(m => `
    <tr>
      <td><input type="checkbox"></td>
      <td class="ttl"><div class="cov cov-${(m.id % 7) + 1}"></div>${esc(m.title)}</td>
      <td>${m.issueDate || "—"}</td>
      <td><span class="tag">${esc(m.publisher || "Periodical")}</span></td>
      <td><span class="badge ${m.available ? "badge-avail" : "badge-out"}">${m.available ? "Available" : "Checked out"}</span></td>
      <td class="row-actions">
        <button class="btn-ghost sm edit-magazine" data-id="${m.id}">Edit</button>
        <button class="btn-ghost sm delete-magazine" data-id="${m.id}" style="color:var(--red)">Delete</button>
      </td>
    </tr>
  `).join("");

  tbody.querySelectorAll(".edit-magazine").forEach(btn => btn.addEventListener("click", () => {
    const m = state.magazines.find(x => x.id === parseInt(btn.dataset.id));
    if (m) openMagazineModal(m);
  }));
  tbody.querySelectorAll(".delete-magazine").forEach(btn => btn.addEventListener("click", () => {
    deleteMagazine(parseInt(btn.dataset.id));
  }));
}

function openMagazineModal(magazine) {
  const isEdit = !!magazine;
  const root = document.getElementById("modal-root");
  root.innerHTML = `
    <div class="modal-backdrop" role="presentation">
      <section class="modal" role="dialog" aria-modal="true">
        <div class="modal-header">
          <h2 class="serif">${isEdit ? "Edit magazine" : "Add magazine"}</h2>
          <button class="modal-close modal-close-mag" type="button">&times;</button>
        </div>
        <p class="form-error" data-mag-error hidden></p>
        <form novalidate>
          <div class="form-grid">
            <label class="field span-all"><span>Title *</span><input name="title" value="${isEdit ? esc(magazine.title) : ""}" required><span class="field-error" data-error="title"></span></label>
            <label class="field"><span>Publisher *</span><input name="publisher" value="${isEdit ? esc(magazine.publisher || "") : ""}" required><span class="field-error" data-error="publisher"></span></label>
            <label class="field"><span>Issue date</span><input name="issueDate" type="date" value="${isEdit && magazine.issueDate ? magazine.issueDate : ""}"><span class="field-error" data-error="issueDate"></span></label>
          </div>
          <div class="modal-footer">
            <button class="btn-ghost modal-close-mag" type="button">Cancel</button>
            <button class="btn-primary" type="submit">${isEdit ? "Save changes" : "Add magazine"}</button>
          </div>
        </form>
      </section>
    </div>`;
  const close = () => { root.innerHTML = ""; };
  root.querySelectorAll(".modal-close-mag").forEach(b => b.addEventListener("click", close));
  root.querySelector(".modal-backdrop").addEventListener("click", e => { if (e.target === e.currentTarget) close(); });
  const fi = root.querySelector("input"); if (fi) fi.focus();
  root.querySelector("form").addEventListener("submit", async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const values = { title: fd.get("title"), publisher: fd.get("publisher"), issueDate: fd.get("issueDate") || null };
    const errEl = root.querySelector("[data-mag-error]");
    errEl.hidden = true;
    if (!values.title?.trim()) { root.querySelector('[data-error="title"]').textContent = "Title is required."; return; }
    try {
      if (isEdit) { await magazinesApi.update(magazine.id, values); toast("Magazine updated.", "success"); }
      else { await magazinesApi.create(values); toast("Magazine added.", "success"); }
      close(); loadMagazines();
    } catch (err) {
      const fieldErrors = err.fieldErrors || [];
      fieldErrors.forEach(({ field, message }) => { const fe = root.querySelector(`[data-error="${field}"]`); if (fe) fe.textContent = message; });
      const unmatched = fieldErrors.filter(({ field }) => !root.querySelector(`[data-error="${field}"]`)).map(({ message }) => message);
      errEl.textContent = unmatched.join(" ") || err.message || "Failed to save.";
      errEl.hidden = false;
    }
  });
}

async function deleteMagazine(id) {
  const m = state.magazines.find(x => x.id === id);
  const ok = await confirmDialog(`Delete <strong>${esc(m ? m.title : "this magazine")}</strong>?`, "Delete");
  if (!ok) return;
  try { await magazinesApi.delete(id); toast("Magazine deleted.", "success"); if (state.magazines.length === 1 && state.page > 0) state.page--; loadMagazines(); }
  catch (err) { toast(err.message || "Failed to delete.", "error"); }
}

async function initAuth() {
  try { await authApi.csrf(); const u = await authApi.me(); setCurrentUser(u); } catch {}
}

let searchTimer;
document.addEventListener("DOMContentLoaded", () => {
  initAuth();
  if (searchInput) searchInput.addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { state.search = searchInput.value.trim(); state.page = 0; loadMagazines(); }, 300);
  });
  if (addBtn) addBtn.addEventListener("click", e => { e.preventDefault(); openMagazineModal(null); });
  loadMagazines();
});
