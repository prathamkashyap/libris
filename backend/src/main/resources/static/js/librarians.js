import { librariansApi } from "/js/api/librarians-api.js";
import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";
import { renderPagination, renderPageInfo } from "/js/utils/pagination.js";
import { openModal } from "/components/modal.js";
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
  openModal('librarian', async (data) => {
    if (librarian) {
      await librariansApi.update(librarian.id, data);
      toast("Librarian updated.", "success");
    } else {
      await librariansApi.create(data);
      toast("Librarian added.", "success");
    }
    loadLibrarians();
  }, librarian);
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
