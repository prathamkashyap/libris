import { booksApi } from "/js/api/books-api.js";
import { borrowApi } from "/js/api/borrow-api.js";
import { authApi } from "/js/api/auth-api.js";
import { getCurrentUser, setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";
import { openModal } from "/components/modal.js";

let currentBook = null;
let historyAvailable = false;

function getId() {
  const id = Number(new URLSearchParams(window.location.search).get("id"));
  return Number.isInteger(id) && id > 0 ? id : null;
}

function canManage() {
  return ["ADMIN", "LIBRARIAN"].includes((getCurrentUser()?.role || "").toUpperCase());
}

function renderState(type, message, action) {
  const container = document.getElementById("bookDetailContainer");
  container.innerHTML = `<div class="${type}-state"><p>${esc(message)}</p>${action ? `<a class="btn-ghost sm" href="${action.href}">${esc(action.label)}</a>` : ""}</div>`;
}

async function loadBook() {
  const id = getId();
  if (!id) return renderState("error", "No book was selected.", { href: "/books.html", label: "Return to books" });
  renderState("loading", "Loading book details…");
  try {
    currentBook = await booksApi.get(id);
    let borrows = [];
    try {
      const data = await borrowApi.list(0, 100);
      borrows = (data.content || []).filter(record => record.itemId === id && record.itemType === "BOOK");
      historyAvailable = true;
    } catch {
      historyAvailable = false;
    }
    renderBook(borrows);
  } catch (error) {
    renderState("error", error.message || "The book could not be loaded.", { href: "/books.html", label: "Return to books" });
  }
}

function renderBook(borrows) {
  const book = currentBook;
  const manager = canManage();
  const availability = book.available ? "Available" : "Checked out";
  document.getElementById("bookDetailContainer").innerHTML = `
    <header class="page-head"><p class="eyebrow">Catalog record</p><div class="page-title-row"><div><h1 class="serif">${esc(book.title)}</h1><p class="page-sub">${esc(book.author || "Author not recorded")}</p></div>${manager ? `<div class="page-actions"><a class="btn-primary" href="/borrow.html">Open circulation</a><button class="btn-ghost" id="editBookBtn" type="button">Edit</button><button class="btn-ghost btn-danger-ghost" id="deleteBookBtn" type="button">Delete</button></div>` : ""}</div></header>
    <div class="bento">
      <section class="card span-4 book-summary"><div class="cov cov-${(book.id % 7) + 1} cover-xl" aria-hidden="true"></div><span class="badge ${book.available ? "badge-avail" : "badge-out"}">${availability}</span><p class="muted">${book.available ? "This title can be issued from Circulation." : "This title is currently assigned to a borrower."}</p></section>
      <section class="card span-8"><div class="card-head"><div><h3>Catalog metadata</h3><p class="muted">The fields maintained for this title.</p></div></div><div class="meta-grid"><div><span class="meta-k">Category</span><span class="meta-v">${esc(book.category || "General")}</span></div><div><span class="meta-k">ISBN</span><span class="meta-v mono">${esc(book.isbn || "—")}</span></div><div><span class="meta-k">Published</span><span class="meta-v">${esc(book.publishedDate || "—")}</span></div><div><span class="meta-k">Availability</span><span class="meta-v">${availability}</span></div></div></section>
      <section class="card span-12"><div class="card-head"><div><h3>Borrow history</h3><p class="muted">${historyAvailable ? "Records for this catalog item." : "Borrow records are available to library staff."}</p></div></div>${historyAvailable ? renderHistory(borrows) : '<div class="empty-state"><p>Borrow history is not available for this account.</p></div>'}</section>
    </div>`;
  if (manager) {
    document.getElementById("editBookBtn").addEventListener("click", () => openModal("book", async values => {
      if (!values.publishedDate) values.publishedDate = null;
      await booksApi.update(book.id, values);
      toast("Book updated successfully.", "success");
      await loadBook();
    }, book));
    document.getElementById("deleteBookBtn").addEventListener("click", deleteBook);
  }
}

function renderHistory(borrows) {
  if (!borrows.length) return '<div class="empty-state"><p>No borrow records exist for this title.</p></div>';
  return `<div class="timeline">${borrows.map(record => `<div class="tl-row"><span class="tl-dot ${record.status === "RETURNED" ? "dot-in" : "dot-out"}"></span><div class="tl-body"><a href="/student-profile.html?id=${record.studentId}">${esc(record.borrowerName)}</a><small>${esc(record.borrowDate)}${record.returnDate ? ` — ${esc(record.returnDate)}` : " — current loan"}</small></div><span class="badge ${record.status === "RETURNED" ? "badge-return" : "badge-out"}">${esc(record.status === "RETURNED" ? "Returned" : "Borrowed")}</span></div>`).join("")}</div>`;
}

async function deleteBook() {
  const confirmed = await confirmDialog(`Delete <strong>${esc(currentBook.title)}</strong>? This cannot be undone.`, "Delete book");
  if (!confirmed) return;
  try {
    await booksApi.delete(currentBook.id);
    toast("Book deleted successfully.", "success");
    window.location.assign("/books.html");
  } catch (error) {
    toast(error.message || "The book could not be deleted.", "error");
  }
}

document.addEventListener("DOMContentLoaded", async () => {
  try { await authApi.csrf(); setCurrentUser(await authApi.me()); } catch {}
  await loadBook();
});
