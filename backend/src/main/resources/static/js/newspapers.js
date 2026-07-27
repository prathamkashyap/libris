import { newspapersApi } from "/js/api/newspapers-api.js";
import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";
import { renderPagination, renderPageInfo } from "/js/utils/pagination.js";

let state = { page: 0, size: 10, search: "", totalPages: 0, totalElements: 0, newspapers: [], loading: false };

const tbody = document.querySelector(".card.table-card tbody");
const foot = document.querySelector(".card.table-card .table-foot");
const muted = foot?.querySelector(".muted");
const pager = foot?.querySelector(".pager");
const searchInput = document.querySelector(".search input");
const addBtn = document.querySelector(".btn-primary");

function showLoading() {
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:48px;color:var(--ink-soft)">Loading newspapers&hellip;</td></tr>';
}

function showEmpty() {
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:48px;color:var(--ink-soft)">No newspapers found.</td></tr>';
}

function showError(msg) {
  if (!tbody) return;
  tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:48px;color:var(--red)">${esc(msg)}<br><button class="btn-ghost sm" style="margin-top:8px" onclick="location.reload()">Try again</button></td></tr>`;
}

async function loadNewspapers() {
  if (state.loading) return;
  state.loading = true;
  showLoading();
  try {
    const data = await newspapersApi.list(state.search, state.page, state.size);
    state.newspapers = data.content || [];
    state.totalPages = data.totalPages || 0;
    state.totalElements = data.totalElements || 0;
    if (state.newspapers.length === 0) showEmpty();
    else renderTable();
    if (muted) renderPageInfo(muted, data.number, data.size, data.totalElements);
    if (pager) renderPagination(pager, data.number, data.totalPages, p => { state.page = p; loadNewspapers(); });
  } catch (err) {
    showError(err.message || "Failed to load newspapers.");
  } finally {
    state.loading = false;
  }
}

function renderTable() {
  if (!tbody) return;
  tbody.innerHTML = state.newspapers.map(n => `
    <tr>
      <td><input type="checkbox"></td>
      <td class="ttl"><div class="cov cov-${(n.id % 7) + 1}"></div>${esc(n.title)}</td>
      <td>${n.publicationDate || "—"}</td>
      <td><span class="tag">${esc(n.language || "English")}</span></td>
      <td><span class="badge ${n.available ? "badge-avail" : "badge-out"}">${n.available ? "In reading room" : "Archived"}</span></td>
      <td class="row-actions">
        <button class="btn-ghost sm edit-newspaper" data-id="${n.id}">Edit</button>
        <button class="btn-ghost sm delete-newspaper" data-id="${n.id}" style="color:var(--red)">Delete</button>
      </td>
    </tr>
  `).join("");

  tbody.querySelectorAll(".edit-newspaper").forEach(btn => btn.addEventListener("click", () => {
    const n = state.newspapers.find(x => x.id === parseInt(btn.dataset.id));
    if (n) openNewspaperModal(n);
  }));
  tbody.querySelectorAll(".delete-newspaper").forEach(btn => btn.addEventListener("click", () => {
    deleteNewspaper(parseInt(btn.dataset.id));
  }));
}

function openNewspaperModal(newspaper) {
  const isEdit = !!newspaper;
  const root = document.getElementById("modal-root");
  root.innerHTML = `
    <div class="modal-backdrop" role="presentation">
      <section class="modal" role="dialog" aria-modal="true">
        <div class="modal-header">
          <h2 class="serif">${isEdit ? "Edit newspaper" : "Add newspaper"}</h2>
          <button class="modal-close modal-close-news" type="button">&times;</button>
        </div>
        <p class="form-error" data-news-error hidden></p>
        <form novalidate>
          <div class="form-grid">
            <label class="field span-all"><span>Title *</span><input name="title" value="${isEdit ? esc(newspaper.title) : ""}" required><span class="field-error" data-error="title"></span></label>
            <label class="field"><span>Publication date</span><input name="publicationDate" type="date" value="${isEdit && newspaper.publicationDate ? newspaper.publicationDate : ""}"><span class="field-error" data-error="publicationDate"></span></label>
          </div>
          <div class="modal-footer">
            <button class="btn-ghost modal-close-news" type="button">Cancel</button>
            <button class="btn-primary" type="submit">${isEdit ? "Save changes" : "Add newspaper"}</button>
          </div>
        </form>
      </section>
    </div>`;
  const close = () => { root.innerHTML = ""; };
  root.querySelectorAll(".modal-close-news").forEach(b => b.addEventListener("click", close));
  root.querySelector(".modal-backdrop").addEventListener("click", e => { if (e.target === e.currentTarget) close(); });
  const fi = root.querySelector("input"); if (fi) fi.focus();
  root.querySelector("form").addEventListener("submit", async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const values = { title: fd.get("title"), publicationDate: fd.get("publicationDate") || null };
    const errEl = root.querySelector("[data-news-error]");
    errEl.hidden = true;
    if (!values.title?.trim()) { root.querySelector('[data-error="title"]').textContent = "Title is required."; return; }
    try {
      if (isEdit) { await newspapersApi.update(newspaper.id, values); toast("Newspaper updated.", "success"); }
      else { await newspapersApi.create(values); toast("Newspaper added.", "success"); }
      close(); loadNewspapers();
    } catch (err) {
      const fieldErrors = err.fieldErrors || [];
      fieldErrors.forEach(({ field, message }) => { const fe = root.querySelector(`[data-error="${field}"]`); if (fe) fe.textContent = message; });
      const unmatched = fieldErrors.filter(({ field }) => !root.querySelector(`[data-error="${field}"]`)).map(({ message }) => message);
      errEl.textContent = unmatched.join(" ") || err.message || "Failed to save.";
      errEl.hidden = false;
    }
  });
}

async function deleteNewspaper(id) {
  const n = state.newspapers.find(x => x.id === id);
  const ok = await confirmDialog(`Delete <strong>${esc(n ? n.title : "this newspaper")}</strong>?`, "Delete");
  if (!ok) return;
  try { await newspapersApi.delete(id); toast("Newspaper deleted.", "success"); if (state.newspapers.length === 1 && state.page > 0) state.page--; loadNewspapers(); }
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
    searchTimer = setTimeout(() => { state.search = searchInput.value.trim(); state.page = 0; loadNewspapers(); }, 300);
  });
  if (addBtn) addBtn.addEventListener("click", e => { e.preventDefault(); openNewspaperModal(null); });
  loadNewspapers();
});
