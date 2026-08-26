import { newspapersApi } from "/js/api/newspapers-api.js";
import { authApi } from "/js/api/auth-api.js";
import { getCurrentUser, setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";
import { renderPagination, renderPageInfo } from "/js/utils/pagination.js";
import { openModal } from "/components/modal.js";
let state = { page: 0, size: 10, search: "", totalPages: 0, totalElements: 0, newspapers: [], loading: false };

const tbody = document.querySelector(".card.table-card tbody");
const foot = document.querySelector(".card.table-card .table-foot");
const muted = foot?.querySelector(".muted");
const pager = foot?.querySelector(".pager");
const searchInput = document.querySelector(".search input");
const addBtn = document.getElementById("addNewspaperBtn");

function canManage() {
  return ["ADMIN", "LIBRARIAN"].includes((getCurrentUser()?.role || "").toUpperCase());
}

function showLoading() {
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="6" class="table-state">Loading newspapers…</td></tr>';
}

function showEmpty() {
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="6" class="table-state">No newspapers found.</td></tr>';
}

function showError(msg) {
  if (!tbody) return;
  tbody.innerHTML = `<tr><td colspan="6" class="table-state table-state-error">${esc(msg)}<br><button class="btn-ghost sm" type="button" data-retry>Try again</button></td></tr>`;
  tbody.querySelector("[data-retry]")?.addEventListener("click", loadNewspapers);
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
      <td class="ttl"><div class="cov cov-${(n.id % 7) + 1}"></div>${esc(n.title)}</td>
      <td>${esc(n.publisher || "—")}</td>
      <td>${n.publicationDate || "—"}</td>
      <td>${esc(n.topHeadlines || "—")}</td>
      <td><span class="badge ${n.available ? "badge-avail" : "badge-out"}">${n.available ? "In reading room" : "Archived"}</span></td>
      <td class="row-actions">${canManage() ? `
        <button class="btn-ghost sm edit-newspaper" data-id="${n.id}" type="button">Edit</button>
        <button class="btn-ghost sm delete-newspaper" data-id="${n.id}" type="button">Delete</button>
      ` : ""}</td>
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
  openModal('newspaper', async (data) => {
    if (!data.publicationDate) data.publicationDate = null;
    
    if (newspaper) {
      await newspapersApi.update(newspaper.id, data);
      toast("Newspaper updated.", "success");
    } else {
      await newspapersApi.create(data);
      toast("Newspaper added.", "success");
    }
    loadNewspapers();
  }, newspaper);
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
document.addEventListener("DOMContentLoaded", async () => {
  await initAuth();
  if (addBtn) addBtn.hidden = !canManage();
  if (searchInput) searchInput.addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { state.search = searchInput.value.trim(); state.page = 0; loadNewspapers(); }, 300);
  });
  if (addBtn) addBtn.addEventListener("click", e => { e.preventDefault(); openNewspaperModal(null); });
  loadNewspapers();
});
