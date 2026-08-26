import { borrowApi } from "/js/api/borrow-api.js";
import { booksApi } from "/js/api/books-api.js";
import { magazinesApi } from "/js/api/magazines-api.js";
import { newspapersApi } from "/js/api/newspapers-api.js";
import { studentsApi } from "/js/api/students-api.js";
import { authApi } from "/js/api/auth-api.js";
import { getCurrentUser, setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { renderPagination, renderPageInfo } from "/js/utils/pagination.js";

let state = { page: 0, size: 10, search: "", totalPages: 0, totalElements: 0, records: [], loading: false };

const timelineEl = document.querySelector(".timeline");
const searchInput = document.querySelector(".search input");
const toolbarEl = document.querySelector(".toolbar");
const issueButton = document.getElementById("issueItemBtn");
const pageInfoEl = document.createElement("div");
const pagerEl = document.createElement("div");
const tableFoot = document.createElement("div");

function setupFooter() {
  const card = document.querySelector(".card");
  if (!card || card.contains(tableFoot)) return;
  tableFoot.className = "table-foot";
  pageInfoEl.className = "muted";
  tableFoot.appendChild(pageInfoEl);
  tableFoot.appendChild(pagerEl);
  card.appendChild(tableFoot);
}

function showLoading() {
  if (!timelineEl) return;
  timelineEl.innerHTML = '<div class="loading-state">Loading records…</div>';
}

function showEmpty() {
  if (!timelineEl) return;
  timelineEl.innerHTML = '<div class="empty-state"><p>No borrow records match this view.</p></div>';
}

function showError(msg) {
  if (!timelineEl) return;
  timelineEl.innerHTML = `<div class="error-state"><p>${esc(msg)}</p><button class="btn-ghost sm" type="button" data-retry>Try again</button></div>`;
  timelineEl.querySelector("[data-retry]")?.addEventListener("click", loadRecords);
}

async function loadRecords() {
  if (state.loading) return;
  state.loading = true;
  showLoading();
  try {
    const data = await borrowApi.list(state.page, state.size, state.search);
    state.records = data.content || [];
    state.totalPages = data.totalPages || 0;
    state.totalElements = data.totalElements || 0;
    if (state.records.length === 0) showEmpty();
    else renderTimeline();
    if (pageInfoEl) renderPageInfo(pageInfoEl, data.number, data.size, data.totalElements);
    if (pagerEl) renderPagination(pagerEl, data.number, data.totalPages, p => { state.page = p; loadRecords(); });
  } catch (err) {
    showError(err.message || "Failed to load records.");
  } finally {
    state.loading = false;
  }
}

function renderTimeline() {
  if (!timelineEl) return;
  timelineEl.innerHTML = state.records.map(r => {
    const isBorrowed = r.status === "BORROWED";
    const isReturned = r.status === "RETURNED";
    const isOverdue = isBorrowed && (r.daysOverdue > 0);
    const dotClass = isOverdue ? "dot-warn" : isBorrowed ? "dot-out" : "dot-in";
    const badgeClass = isOverdue ? "badge-warn" : isBorrowed ? "badge-out" : "badge-return";
    const label = isOverdue ? `${r.daysOverdue}d Overdue` : isBorrowed ? "Active" : "Returned";
    return `
      <div class="tl-row">
        <div class="tl-dot ${dotClass}"></div>
        <div class="tl-body">
          ${r.itemType === "BOOK" ? `<a href="book-details.html?id=${r.itemId}">${esc(r.itemTitle)}</a>` : `<b>${esc(r.itemTitle)}</b>`} (${esc(r.itemType)}) ${isBorrowed ? "issued to" : "returned by"} <a href="student-profile.html?id=${r.studentId}">${esc(r.borrowerName)}</a>
          <span class="badge ${badgeClass}">${label}</span>
          <small>Issued ${r.borrowDate}${r.dueDate ? ` · Due ${r.dueDate}` : ""}${r.returnDate ? ` · Returned ${r.returnDate}` : ""}</small>
        </div>
        ${isBorrowed ? `<button class="btn-ghost sm return-book" data-id="${r.id}">Quick return</button>` : `<button class="btn-ghost sm" disabled>Closed</button>`}
      </div>
    `;
  }).join("");

  timelineEl.querySelectorAll(".return-book").forEach(btn => btn.addEventListener("click", async () => {
    const id = parseInt(btn.dataset.id);
    try {
      await borrowApi.returnBook(id);
      toast("Book returned successfully.", "success");
      loadRecords();
    } catch (err) {
      toast(err.message || "Failed to return book.", "error");
    }
  }));
}

async function initAuth() {
  try { await authApi.csrf(); const u = await authApi.me(); setCurrentUser(u); } catch {}
}

function optionList(items, type, label) {
  return items.filter(item => item.available).map(item =>
    `<option value="${type}:${item.id}">${esc(item.title)} (${label})</option>`
  ).join("");
}

function canManageCirculation() {
  return ["ADMIN", "LIBRARIAN"].includes((getCurrentUser()?.role || "").toUpperCase());
}

function showStudentAccessState() {
  toolbarEl?.setAttribute("hidden", "");
  issueButton?.setAttribute("hidden", "");
  if (timelineEl) timelineEl.innerHTML = '<div class="empty-state"><p>Circulation management is available to library staff. Use your student dashboard to view your own borrowing information.</p><a class="btn-ghost sm" href="/index.html">Open student dashboard</a></div>';
}

async function openBorrowModal() {
  const root = document.getElementById("modal-root");
  const opener = document.activeElement;
  root.innerHTML = '<div class="modal-backdrop" role="presentation"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="borrow-modal-title"><div class="modal-header"><h2 id="borrow-modal-title" class="serif">Issue an item</h2><button class="modal-close borrow-modal-close" type="button" aria-label="Close">&times;</button></div><div class="loading-state">Loading available items…</div></section></div>';
  let escHandler;
  const close = () => { root.innerHTML = ""; document.removeEventListener("keydown", escHandler); opener?.focus?.(); };
  escHandler = event => { if (event.key === "Escape") close(); };
  document.addEventListener("keydown", escHandler);
  root.querySelectorAll(".borrow-modal-close").forEach(button => button.addEventListener("click", close));

  try {
    const [books, magazines, newspapers, students] = await Promise.all([
      booksApi.list("", 0, 100), magazinesApi.list("", 0, 100), newspapersApi.list("", 0, 100), studentsApi.list(0, 100)
    ]);
    const items = [
      optionList(books.content || [], "book", "Book"),
      optionList(magazines.content || [], "magazine", "Magazine"),
      optionList(newspapers.content || [], "newspaper", "Newspaper")
    ].join("");
    const readers = (students.content || []).map(student =>
      `<option value="${student.id}">${esc(student.name)} — ${esc(student.username)}</option>`
    ).join("");

    if (!items || !readers) {
      root.querySelector("section").innerHTML = `<div class="modal-header"><h2 id="borrow-modal-title" class="serif">Issue an item</h2><button class="modal-close borrow-modal-close" type="button" aria-label="Close">&times;</button></div><div class="empty-state"><p>${!items ? "Add an available item" : "Add a student"} before creating a borrow record.</p></div>`;
      root.querySelector(".borrow-modal-close").addEventListener("click", close);
      return;
    }

    const defaultIssue = new Date();
    const defaultDue = new Date(Date.now() + 14 * 24 * 60 * 60 * 1000);

    root.querySelector("section").innerHTML = `
      <div class="modal-header"><h2 id="borrow-modal-title" class="serif">Issue an item</h2><button class="modal-close borrow-modal-close" type="button" aria-label="Close">&times;</button></div>
      <p class="form-error" data-borrow-error hidden></p>
      <form id="borrow-form" novalidate>
        <div class="form-grid">
          <label class="field span-all"><span>Item *</span><select name="item" required><option value="">Select an available item</option>${items}</select></label>
          <label class="field span-all"><span>Student *</span><select name="studentId" required><option value="">Select a student</option>${readers}</select></label>
          <label class="field"><span>Issue date *</span><input name="borrowDate" type="date" value="${defaultIssue.toISOString().slice(0, 10)}" required></label>
          <label class="field"><span>Due date</span><input name="dueDate" type="date" value="${defaultDue.toISOString().slice(0, 10)}"></label>
        </div>
        <div class="modal-footer"><button class="btn-ghost borrow-modal-close" type="button">Cancel</button><button class="btn-primary" type="submit">Issue item</button></div>
      </form>`;
    root.querySelectorAll(".borrow-modal-close").forEach(button => button.addEventListener("click", close));
    root.querySelector(".modal-backdrop").addEventListener("click", event => { if (event.target === event.currentTarget) close(); });
    root.querySelector("select")?.focus();
    root.querySelector("form").addEventListener("submit", async event => {
      event.preventDefault();
      const data = new FormData(event.currentTarget);
      const [type, id] = String(data.get("item")).split(":");
      const request = {
        studentId: Number(data.get("studentId")),
        borrowDate: data.get("borrowDate"),
        dueDate: data.get("dueDate") || null
      };
      if (type === "book") request.bookId = Number(id);
      if (type === "magazine") request.magazineId = Number(id);
      if (type === "newspaper") request.newspaperId = Number(id);
      const error = root.querySelector("[data-borrow-error]");
      if (!request.studentId || !id || !request.borrowDate) {
        error.textContent = "Choose an item, a student, and an issue date.";
        error.hidden = false;
        return;
      }
      try {
        await borrowApi.create(request);
        close();
        toast("Item issued successfully.", "success");
        loadRecords();
      } catch (err) {
        error.textContent = err.message || "Could not issue this item.";
        error.hidden = false;
      }
    });
  } catch (err) {
    root.querySelector("section").innerHTML = `<div class="modal-header"><h2 id="borrow-modal-title" class="serif">Issue an item</h2><button class="modal-close borrow-modal-close" type="button" aria-label="Close">&times;</button></div><div class="error-state"><p>${esc(err.message || "Could not load issue form.")}</p></div>`;
    root.querySelector(".borrow-modal-close").addEventListener("click", close);
  }
}

let searchTimer;
document.addEventListener("DOMContentLoaded", async () => {
  await initAuth();
  if (!canManageCirculation()) {
    showStudentAccessState();
    return;
  }
  setupFooter();
  if (searchInput) searchInput.addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { state.search = searchInput.value.trim(); state.page = 0; loadRecords(); }, 300);
  });
  issueButton?.addEventListener("click", openBorrowModal);
  loadRecords();
});
