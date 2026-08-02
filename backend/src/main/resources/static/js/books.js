import { booksApi } from "/js/api/books-api.js";
import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";
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

function showLoading() {
  if (!gridEl) return;
  gridEl.innerHTML = '<div class="loading-state" style="grid-column:1/-1;text-align:center;padding:48px 0;color:var(--ink-soft)">Loading books&hellip;</div>';
  if (listTbody) listTbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:48px;color:var(--ink-soft)">Loading books&hellip;</td></tr>';
}

function showEmpty() {
  if (!gridEl) return;
  const msg = `<div class="empty-state" style="grid-column:1/-1;text-align:center;padding:48px 0">
    <svg viewBox="0 0 24 24" style="width:40px;height:40px;stroke:var(--ink-soft);fill:none;stroke-width:1.5;margin-bottom:12px"><path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v18H6.5A2.5 2.5 0 0 0 4 22.5V4.5Z"/><path d="M8 2v18"/></svg>
    <p style="color:var(--ink-soft);font-size:14px;margin:0">${state.search ? "No books match your search." : "No books in the collection yet."}</p>
    ${state.search ? "" : '<p style="color:var(--ink-soft);font-size:13px;margin-top:4px">Add your first book to get started.</p>'}
  </div>`;
  gridEl.innerHTML = msg;
  if (listTbody) listTbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:48px;color:var(--ink-soft)">${state.search ? "No books match your search." : "No books in the collection yet."}</td></tr>`;
}

function showError(msg) {
  if (!gridEl) return;
  const html = `<div class="error-state" style="grid-column:1/-1;text-align:center;padding:48px 0">
    <p style="color:var(--red);font-size:14px;margin:0">${esc(msg)}</p>
    <button class="btn-ghost sm" style="margin-top:12px" onclick="location.reload()">Try again</button>
  </div>`;
  gridEl.innerHTML = html;
  if (listTbody) listTbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:48px;color:var(--red)">${esc(msg)}</td></tr>`;
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
      <small>${esc(b.author || "Unknown")}</small>
      <span class="badge ${b.available ? "badge-avail" : "badge-out"}">${b.available ? "Available" : "Checked out"}</span>
      <div class="gcard-actions">
        <button class="btn-ghost sm edit-book" data-id="${b.id}">Edit</button>
        <button class="btn-ghost sm delete-book" data-id="${b.id}" style="color:var(--red)">Delete</button>
      </div>
    </div>
  `).join("");

  gridEl.querySelectorAll(".book-gcard").forEach(card => card.addEventListener("click", e => {
    if (e.target.closest("button")) return;
    const id = card.dataset.id;
    if (id) window.location.href = `book-details.html?id=${id}`;
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
      <td><input type="checkbox"></td>
      <td class="ttl"><div class="cov cov-${(b.id % 7) + 1}"></div><a href="book-details.html?id=${b.id}">${esc(b.title)}</a></td>
      <td>${esc(b.author || "—")}</td>
      <td><span class="tag">Book</span></td>
      <td class="mono">${esc(b.isbn || "—")}</td>
      <td><span class="badge ${b.available ? "badge-avail" : "badge-out"}">${b.available ? "Available" : "Checked out"}</span></td>
      <td class="row-actions">
        <button class="btn-ghost sm edit-book" data-id="${b.id}">Edit</button>
        <button class="btn-ghost sm delete-book" data-id="${b.id}" style="color:var(--red)">Delete</button>
      </td>
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

document.addEventListener("DOMContentLoaded", () => {
  initAuth();
  if (searchInput) searchInput.addEventListener("input", onSearchInput);
  if (addBtn) addBtn.addEventListener("click", e => {
    e.preventDefault();
    openBookModal(null);
  });
  loadBooks();
});
