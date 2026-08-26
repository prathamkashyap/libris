import { magazinesApi } from "/js/api/magazines-api.js";
import { authApi } from "/js/api/auth-api.js";
import { getCurrentUser, setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";
import { renderPagination, renderPageInfo } from "/js/utils/pagination.js";
import { openModal } from "/components/modal.js";
let state = { page: 0, size: 10, search: "", totalPages: 0, totalElements: 0, magazines: [], loading: false };

const tbody = document.querySelector(".card.table-card tbody");
const foot = document.querySelector(".card.table-card .table-foot");
const muted = foot?.querySelector(".muted");
const pager = foot?.querySelector(".pager");
const searchInput = document.querySelector(".search input");
const addBtn = document.getElementById("addMagazineBtn");

function canManage() {
  return ["ADMIN", "LIBRARIAN"].includes((getCurrentUser()?.role || "").toUpperCase());
}

function showLoading() {
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="6" class="table-state">Loading magazines…</td></tr>';
}

function showEmpty() {
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="6" class="table-state">No magazines found.</td></tr>';
}

function showError(msg) {
  if (!tbody) return;
  tbody.innerHTML = `<tr><td colspan="6" class="table-state table-state-error">${esc(msg)}<br><button class="btn-ghost sm" type="button" data-retry>Try again</button></td></tr>`;
  tbody.querySelector("[data-retry]")?.addEventListener("click", loadMagazines);
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
      <td class="ttl"><div class="cov cov-${(m.id % 7) + 1}"></div>${esc(m.title)}</td>
      <td>${esc(m.publisher || "—")}</td>
      <td>${m.issueDate || "—"}</td>
      <td><span class="tag">${esc(m.category || "Uncategorized")}</span></td>
      <td><span class="badge ${m.available ? "badge-avail" : "badge-out"}">${m.available ? "Available" : "Checked out"}</span></td>
      <td class="row-actions">${canManage() ? `
        <button class="btn-ghost sm edit-magazine" data-id="${m.id}" type="button">Edit</button>
        <button class="btn-ghost sm delete-magazine" data-id="${m.id}" type="button">Delete</button>
      ` : ""}</td>
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
  openModal('magazine', async (data) => {
    if (!data.issueDate) data.issueDate = null;
    
    if (magazine) {
      await magazinesApi.update(magazine.id, data);
      toast("Magazine updated.", "success");
    } else {
      await magazinesApi.create(data);
      toast("Magazine added.", "success");
    }
    loadMagazines();
  }, magazine);
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
document.addEventListener("DOMContentLoaded", async () => {
  await initAuth();
  if (addBtn) addBtn.hidden = !canManage();
  if (searchInput) searchInput.addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { state.search = searchInput.value.trim(); state.page = 0; loadMagazines(); }, 300);
  });
  if (addBtn) addBtn.addEventListener("click", e => { e.preventDefault(); openMagazineModal(null); });
  loadMagazines();
});
