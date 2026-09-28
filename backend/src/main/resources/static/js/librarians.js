import { librariansApi } from "/js/api/librarians-api.js";
import { authApi } from "/js/api/auth-api.js";
import { getCurrentUser, setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";
import { renderPagination, renderPageInfo } from "/js/utils/pagination.js";
import { openModal } from "/components/modal.js";
import { avatarMarkup } from "/js/utils/avatar.js";
let state = { page: 0, size: 10, search: "", totalPages: 0, totalElements: 0, librarians: [], loading: false };

const tbody = document.querySelector("#librariansTable tbody");
const foot = document.querySelector(".collection-panel .table-foot");
const muted = foot?.querySelector(".muted");
const pager = foot?.querySelector(".pager");
const searchInput = document.querySelector(".search input");

function isAdmin() {
  return (getCurrentUser()?.role || "").toUpperCase() === "ADMIN";
}

function showAdminOnly() {
  document.querySelector(".toolbar")?.setAttribute("hidden", "");
  if (tbody) tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><p>Library team management is available to administrators only.</p><a class="btn-ghost sm" href="/index.html">Open dashboard</a></div></td></tr>';
  if (muted) muted.textContent = '';
  if (pager) pager.innerHTML = '';
}

function showLoading() {
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="6"><div class="loading-state">Loading librarians…</div></td></tr>';
  if (muted) muted.textContent = 'Loading results…';
  if (pager) pager.innerHTML = '';
}

function showEmpty() {
  if (!tbody) return;
  const message = state.search ? "No librarians match your search." : "No librarians found";
  const description = state.search ? "Try adjusting your search terms." : "Add librarians to manage your library team.";
  tbody.innerHTML = `
    <tr><td colspan="6">
      <div class="empty-state">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>
        <h3>${message}</h3>
        <p>${description}</p>
        ${!state.search && isAdmin() ? '<button class="btn-primary" id="emptyAddLibrarian">Add librarian</button>' : ''}
      </div>
    </td></tr>
  `;
  const emptyAddBtn = document.getElementById('emptyAddLibrarian');
  if (emptyAddBtn) {
    emptyAddBtn.addEventListener('click', () => openLibrarianModal(null));
  }
  if (muted) muted.textContent = 'Showing 0–0 of 0';
  if (pager) pager.innerHTML = '';
}

function showError(msg) {
  if (!tbody) return;
  tbody.innerHTML = `<tr><td colspan="6"><div class="error-state"><p>${esc(msg)}</p><button class="btn-ghost sm" type="button" data-retry>Try again</button></div></td></tr>`;
  tbody.querySelector("[data-retry]")?.addEventListener("click", loadLibrarians);
  if (muted) muted.textContent = '';
  if (pager) pager.innerHTML = '';
}

async function loadLibrarians() {
  if (state.loading) return;
  state.loading = true;
  showLoading();
  try {
    const data = await librariansApi.list(state.page, state.size, state.search);
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
    <tr class="person-row" data-id="${l.id}">
      <td data-label="Name">
        <div class="person-cell">
          ${avatarMarkup(l.name)}
          <a href="librarian-profile.html?id=${l.id}">${esc(l.name)}</a>
        </div>
      </td>
      <td data-label="Username" class="mono">${esc(l.username || "—")}</td>
      <td data-label="Role"><span class="badge badge-muted">${esc(l.role || "Librarian")}</span></td>
      <td data-label="Age">${l.age || "—"}</td>
      <td data-label="Phone">${esc(l.phone || "—")}</td>
      <td data-label="Actions">
        <div class="row-actions">
          <button class="btn-ghost sm edit-librarian" data-id="${l.id}" type="button">Edit</button>
          <button class="btn-ghost sm delete-librarian" data-id="${l.id}" type="button">Delete</button>
        </div>
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
document.addEventListener("DOMContentLoaded", async () => {
  await initAuth();
  if (!isAdmin()) {
    showAdminOnly();
    return;
  }
  if (addLibrarianBtn) addLibrarianBtn.hidden = false;
  if (searchInput) searchInput.addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { state.search = searchInput.value.trim(); state.page = 0; loadLibrarians(); }, 300);
  });
  if (addLibrarianBtn) addLibrarianBtn.addEventListener("click", () => openLibrarianModal(null));
  loadLibrarians();
});
