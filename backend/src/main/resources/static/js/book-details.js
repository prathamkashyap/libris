import { booksApi } from "/js/api/books-api.js";
import { borrowApi } from "/js/api/borrow-api.js";
import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";

let currentBook = null;

function getParams() {
  const params = new URLSearchParams(window.location.search);
  return { id: params.get("id") ? parseInt(params.get("id")) : null };
}

function showLoading() {
  document.getElementById("bookDetailContainer").innerHTML = '<div class="loading-state" style="text-align:center;padding:64px 0;color:var(--ink-soft)">Loading book details&hellip;</div>';
}

function showError(msg) {
  document.getElementById("bookDetailContainer").innerHTML = `<div class="error-state" style="text-align:center;padding:64px 0"><p style="color:var(--red);font-size:14px;margin:0">${esc(msg)}</p><button class="btn-ghost sm" style="margin-top:12px" onclick="location.reload()">Try again</button></div>`;
}

async function loadBook() {
  const { id } = getParams();
  if (!id) {
    showError("No book ID specified. <a href='books.html' style='color:var(--accent)'>Return to books</a>");
    return;
  }
  showLoading();
  try {
    currentBook = await booksApi.get(id);
    let borrows = [];
    try {
      const records = await borrowApi.list(0, 100);
      borrows = (records.content || []).filter(r => r.itemId === id && r.itemType === "BOOK");
    } catch {}
    renderBook(borrows);
  } catch (err) {
    showError(err.message || "Failed to load book.");
  }
}

function renderBook(borrows) {
  const b = currentBook;
  const container = document.getElementById("bookDetailContainer");

  const breadcrumb = document.querySelector(".breadcrumb .current");
  if (breadcrumb) breadcrumb.textContent = b.title;

  container.innerHTML = `
    <header class="page-head">
      <div class="eyebrow">Volume record</div>
      <h1 class="serif">${esc(b.title)}</h1>
      <p class="page-sub">${esc(b.author || "Unknown author")}</p>
    </header>

    <div class="bento">
      <div class="card span-4 book-hero">
        <div class="cov cov-${(b.id % 7) + 1} cov-lg"></div>
        <span class="badge ${b.available ? "badge-avail" : "badge-out"}" style="margin-top:16px">${b.available ? "Available" : "Checked out"}</span>
        <button class="btn-primary full" id="issueBookBtn">Issue this book</button>
        <button class="btn-ghost full" id="reserveBookBtn">Reserve a copy</button>
        <div style="display:flex;gap:8px;margin-top:16px;width:100%">
          <button class="btn-ghost full" id="editBookBtn">Edit</button>
          <button class="btn-ghost full" id="deleteBookBtn" style="color:var(--red)">Delete</button>
        </div>
        <div class="card-head" style="margin-top:22px;width:100%"><h3 style="font-size:13px">QR / Barcode</h3></div>
        <div style="width:96px;height:96px;border-radius:12px;background:repeating-linear-gradient(90deg,var(--ink) 0 3px,transparent 3px 6px);opacity:.75"></div>
      </div>

      <div class="card span-8">
        <div class="card-head"><h3>Metadata</h3></div>
        <div class="meta-grid">
          <div><span class="meta-k">ISBN</span><span class="meta-v mono">${esc(b.isbn || "—")}</span></div>
          <div><span class="meta-k">Published</span><span class="meta-v">${b.publishedDate ? b.publishedDate : "—"}</span></div>
          <div><span class="meta-k">Status</span><span class="meta-v"><span class="badge ${b.available ? "badge-avail" : "badge-out"}">${b.available ? "Available" : "Checked out"}</span></span></div>
        </div>

        <div class="card-head" style="margin-top:28px"><h3>Borrow history</h3></div>
        <div class="mini-table" id="borrowHistoryList">
          ${borrows.length === 0
            ? '<div class="empty-state" style="padding:24px"><p style="color:var(--ink-soft);font-size:13px;margin:0">No borrow records for this book.</p></div>'
            : borrows.map(r => `
              <div class="mini-row">
                <a href="student-profile.html?id=${r.studentId}">${esc(r.borrowerName)}</a>
                <span class="muted">${r.borrowDate}${r.returnDate ? " &mdash; " + r.returnDate : " &mdash; ongoing"}</span>
                <span class="badge ${r.status === "RETURNED" ? "badge-return" : "badge-warn"}">${r.status === "RETURNED" ? "Returned" : r.status === "BORROWED" ? "Borrowed" : esc(r.status)}</span>
              </div>
            `).join("")}
        </div>
      </div>
    </div>
  `;

  document.getElementById("editBookBtn").addEventListener("click", openEditModal);
  document.getElementById("deleteBookBtn").addEventListener("click", deleteBook);
  document.getElementById("issueBookBtn").addEventListener("click", () => toast("Issue workflow coming soon.", "info"));
  document.getElementById("reserveBookBtn").addEventListener("click", () => toast("Reservation workflow coming soon.", "info"));
}

function openEditModal() {
  const book = currentBook;
  if (!book) return;
  const root = document.getElementById("modal-root");
  root.innerHTML = `
    <div class="modal-backdrop" role="presentation">
      <section class="modal" role="dialog" aria-modal="true" aria-labelledby="book-modal-title">
        <div class="modal-header">
          <h2 id="book-modal-title" class="serif">Edit book</h2>
          <button class="modal-close book-modal-close" type="button" aria-label="Close">&times;</button>
        </div>
        <p class="form-error" data-book-error hidden></p>
        <form id="book-form" novalidate>
          <div class="form-grid">
            <label class="field span-all">
              <span>Title *</span>
              <input id="f-title" name="title" type="text" value="${esc(book.title)}" required>
              <span class="field-error" data-error="title"></span>
            </label>
            <label class="field span-all">
              <span>Author</span>
              <input id="f-author" name="author" type="text" value="${esc(book.author || "")}">
              <span class="field-error" data-error="author"></span>
            </label>
            <label class="field">
              <span>ISBN</span>
              <input id="f-isbn" name="isbn" type="text" class="mono" value="${esc(book.isbn || "")}">
              <span class="field-error" data-error="isbn"></span>
            </label>
            <label class="field">
              <span>Published date</span>
              <input id="f-publishedDate" name="publishedDate" type="date" value="${book.publishedDate || ""}">
              <span class="field-error" data-error="publishedDate"></span>
            </label>
          </div>
          <div class="modal-footer">
            <button class="btn-ghost book-modal-close" type="button">Cancel</button>
            <button class="btn-primary" type="submit">Save changes</button>
          </div>
        </form>
      </section>
    </div>
  `;

  const close = () => { root.innerHTML = ""; };
  root.querySelectorAll(".book-modal-close").forEach(b => b.addEventListener("click", close));
  root.querySelector(".modal-backdrop").addEventListener("click", e => { if (e.target === e.currentTarget) close(); });
  const fi = root.querySelector("input"); if (fi) fi.focus();

  root.querySelector("form").addEventListener("submit", async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const values = {
      title: fd.get("title"),
      author: fd.get("author"),
      isbn: fd.get("isbn"),
      publishedDate: fd.get("publishedDate") || null
    };
    const errorEl = root.querySelector("[data-book-error]");
    errorEl.hidden = true;
    if (!values.title.trim()) { root.querySelector('[data-error="title"]').textContent = "Title is required."; return; }
    try {
      await booksApi.update(book.id, values);
      toast("Book updated successfully.", "success");
      close();
      loadBook();
    } catch (err) {
      const fieldErrors = err.fieldErrors || [];
      fieldErrors.forEach(({ field, message }) => { const fe = root.querySelector(`[data-error="${field}"]`); if (fe) fe.textContent = message; });
      const unmatched = fieldErrors.filter(({ field }) => !root.querySelector(`[data-error="${field}"]`)).map(({ message }) => message);
      errorEl.textContent = unmatched.join(" ") || err.message || "Failed to save book.";
      errorEl.hidden = false;
    }
  });
}

async function deleteBook() {
  if (!currentBook) return;
  const ok = await confirmDialog(`Are you sure you want to delete <strong>${esc(currentBook.title)}</strong>? This action cannot be undone.`, "Delete book");
  if (!ok) return;
  try {
    await booksApi.delete(currentBook.id);
    toast("Book deleted successfully.", "success");
    window.location.href = "books.html";
  } catch (err) {
    toast(err.message || "Failed to delete book.", "error");
  }
}

async function initAuth() {
  try { await authApi.csrf(); const u = await authApi.me(); setCurrentUser(u); } catch {}
}

document.addEventListener("DOMContentLoaded", () => {
  initAuth();
  loadBook();
});
