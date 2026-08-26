import { booksApi } from "/js/api/books-api.js";
import { authApi } from "/js/api/auth-api.js";
import { getCurrentUser, setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";
import { renderPagination, renderPageInfo } from "/js/utils/pagination.js";
import { openModal } from "/components/modal.js";

let state = {
  page: 0,
  size: 10,
  search: "",
  totalPages: 0,
  totalElements: 0,
  books: [],
  loading: false,
  view: "grid"
};

const gridEl = document.getElementById("booksGrid");
const listEl = document.getElementById("booksList");
const listTbody = listEl?.querySelector("tbody");
const tableFoot = listEl?.querySelector(".table-foot");
const pageInfo = tableFoot?.querySelector(".muted");
const pagerEl = tableFoot?.querySelector(".pager");
const searchInput = document.querySelector(".search input");
const addBtn = document.querySelector(".btn-primary");
const gridViewBtn = document.getElementById("viewGrid");
const listViewBtn = document.getElementById("viewList");

function setView(view) {
  state.view = view;
  const useGrid = view === "grid";
  if (gridEl) gridEl.hidden = !useGrid;
  if (listEl) listEl.hidden = useGrid;
  gridViewBtn?.classList.toggle("active", useGrid);
  listViewBtn?.classList.toggle("active", !useGrid);
  gridViewBtn?.setAttribute("aria-pressed", String(useGrid));
  listViewBtn?.setAttribute("aria-pressed", String(!useGrid));
}

function canManage() {
  return ["ADMIN", "LIBRARIAN"].includes((getCurrentUser()?.role || "").toUpperCase());
}

function syncManagementControls() {
  if (addBtn) addBtn.hidden = !canManage();
}

function showLoading() {
  if (!gridEl) return;
  gridEl.innerHTML = '<div class="loading-state">Loading books…</div>';
  if (listTbody) listTbody.innerHTML = '<tr><td colspan="5" class="table-state">Loading books…</td></tr>';
}

function showEmpty() {
  if (!gridEl) return;
  const msg = `<div class="empty-state"><p>${state.search ? "No books match your search." : "No books in the collection yet."}</p>${state.search || !canManage() ? "" : '<p>Add the first catalog title to get started.</p>'}</div>`;
  gridEl.innerHTML = msg;
  if (listTbody) listTbody.innerHTML = `<tr><td colspan="5" class="table-state">${state.search ? "No books match your search." : "No books in the collection yet."}</td></tr>`;
}

function showError(msg) {
  if (!gridEl) return;
  const html = `<div class="error-state"><p>${esc(msg)}</p><button class="btn-ghost sm" type="button" data-retry>Try again</button></div>`;
  gridEl.innerHTML = html;
  gridEl.querySelector("[data-retry]")?.addEventListener("click", loadBooks);
  if (listTbody) listTbody.innerHTML = `<tr><td colspan="5" class="table-state table-state-error">${esc(msg)}</td></tr>`;
}

async function loadBooks() {
  if (state.loading) return;
  state.loading = true;
  showLoading();
  try {
    const data = await booksApi.list(state.search, state.page, state.size);
    state.books = data.content || [];
    state.totalPages = data.totalPages || 0;
    state.totalElements = data.totalElements || 0;
    if (state.books.length === 0) {
      showEmpty();
    } else {
      renderGrid();
      renderList();
    }
    if (pageInfo) renderPageInfo(pageInfo, data.number, data.size, data.totalElements);
    if (pagerEl) renderPagination(pagerEl, data.number, data.totalPages, goToPage);
  } catch (err) {
    showError(err.message || "Failed to load books.");
  } finally {
    state.loading = false;
  }
}

function renderGrid() {
  if (!gridEl) return;
  gridEl.innerHTML = state.books.map(b => `
    <div class="book-gcard" data-id="${b.id}" tabindex="0" role="button">
      <div class="cov cov-${(b.id % 7) + 1}"></div>
      <b>${esc(b.title)}</b>
      <small>${esc(b.author || "Unknown")}${b.category ? ` · ${esc(b.category)}` : ""}</small>
      <span class="badge ${b.available ? "badge-avail" : "badge-out"}">${b.available ? "Available" : "Checked out"}</span>
      ${canManage() ? `<div class="gcard-actions">
        <button class="btn-ghost sm edit-book" data-id="${b.id}">Edit</button>
        <button class="btn-ghost sm delete-book" data-id="${b.id}">Delete</button>
      </div>` : ""}
    </div>
  `).join("");

  gridEl.querySelectorAll(".book-gcard").forEach(card => card.addEventListener("click", e => {
    if (e.target.closest("button")) return;
    const id = card.dataset.id;
    if (id) window.location.href = `book-details.html?id=${id}`;
  }));
  gridEl.querySelectorAll(".book-gcard").forEach(card => card.addEventListener("keydown", event => {
    if ((event.key === "Enter" || event.key === " ") && !event.target.closest("button")) {
      event.preventDefault();
      card.click();
    }
  }));
  gridEl.querySelectorAll(".edit-book").forEach(btn => btn.addEventListener("click", e => {
    e.preventDefault();
    const id = parseInt(btn.dataset.id);
    const book = state.books.find(b => b.id === id);
    if (book) openBookModal(book);
  }));
  gridEl.querySelectorAll(".delete-book").forEach(btn => btn.addEventListener("click", e => {
    e.preventDefault();
    deleteBook(parseInt(btn.dataset.id));
  }));
}

function renderList() {
  if (!listTbody) return;
  listTbody.innerHTML = state.books.map(b => `
    <tr>
      <td class="ttl"><div class="cov cov-${(b.id % 7) + 1}"></div><a href="book-details.html?id=${b.id}">${esc(b.title)}</a></td>
      <td>${esc(b.author || "—")}${b.category ? ` <small class="muted">(${esc(b.category)})</small>` : ""}</td>
      <td class="mono">${esc(b.isbn || "—")}</td>
      <td><span class="badge ${b.available ? "badge-avail" : "badge-out"}">${b.available ? "Available" : "Checked out"}</span></td>
      <td class="row-actions">${canManage() ? `
        <button class="btn-ghost sm edit-book" data-id="${b.id}">Edit</button>
        <button class="btn-ghost sm delete-book" data-id="${b.id}">Delete</button>` : ""}</td>
    </tr>
  `).join("");

  listTbody.querySelectorAll(".edit-book").forEach(btn => btn.addEventListener("click", () => {
    const id = parseInt(btn.dataset.id);
    const book = state.books.find(b => b.id === id);
    if (book) openBookModal(book);
  }));
  listTbody.querySelectorAll(".delete-book").forEach(btn => btn.addEventListener("click", () => {
    deleteBook(parseInt(btn.dataset.id));
  }));
}

function goToPage(p) {
  state.page = p;
  loadBooks();
}

function openBookModal(book) {
  openModal('book', async (data) => {
    // If publishedDate is empty string, make it null
    if (!data.publishedDate) data.publishedDate = null;
    
    if (book) {
      await booksApi.update(book.id, data);
      toast("Book updated successfully.", "success");
    } else {
      await booksApi.create(data);
      toast("Book added successfully.", "success");
    }
    loadBooks();
  }, book);
}

async function deleteBook(id) {
  const book = state.books.find(b => b.id === id);
  const title = book ? book.title : "this book";
  const ok = await confirmDialog(`Are you sure you want to delete <strong>${esc(title)}</strong>? This action cannot be undone.`, "Delete book");
  if (!ok) return;
  try {
    await booksApi.delete(id);
    toast("Book deleted successfully.", "success");
    if (state.books.length === 1 && state.page > 0) state.page--;
    loadBooks();
  } catch (err) {
    toast(err.message || "Failed to delete book.", "error");
  }
}

let searchTimer;
function onSearchInput() {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    state.search = searchInput.value.trim();
    state.page = 0;
    loadBooks();
  }, 300);
}

async function initAuth() {
  try { await authApi.csrf(); const u = await authApi.me(); setCurrentUser(u); } catch {}
}

document.addEventListener("DOMContentLoaded", async () => {
  await initAuth();
  syncManagementControls();
  if (searchInput) searchInput.addEventListener("input", onSearchInput);
  gridViewBtn?.addEventListener("click", () => setView("grid"));
  listViewBtn?.addEventListener("click", () => setView("list"));
  if (addBtn) addBtn.addEventListener("click", e => {
    e.preventDefault();
    openBookModal(null);
  });
  setView(state.view);
  loadBooks();
});
