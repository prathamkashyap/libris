import { studentsApi } from "/js/api/students-api.js";
import { borrowApi } from "/js/api/borrow-api.js";
import { authApi } from "/js/api/auth-api.js";
import { getCurrentUser, setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { avatarMarkup } from "/js/utils/avatar.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";
import { openModal } from "/components/modal.js";

let currentStudent = null;
let historyAvailable = false;

function getId() {
  const id = Number(new URLSearchParams(window.location.search).get("id"));
  return Number.isInteger(id) && id > 0 ? id : null;
}

function canManage() {
  return ["ADMIN", "LIBRARIAN"].includes((getCurrentUser()?.role || "").toUpperCase());
}

function renderState(type, message, action) {
  document.getElementById("studentProfileContainer").innerHTML = `<div class="${type}-state"><p>${esc(message)}</p>${action ? `<a class="btn-ghost sm" href="${action.href}">${esc(action.label)}</a>` : ""}</div>`;
}

async function loadStudent() {
  const id = getId();
  if (!id) return renderState("error", "No student was selected.", { href: "/students.html", label: "Return to students" });
  renderState("loading", "Loading student details…");
  try {
    currentStudent = await studentsApi.get(id);
    let borrows = [];
    try {
      const data = await borrowApi.list(0, 100);
      borrows = (data.content || []).filter(record => record.studentId === id);
      historyAvailable = true;
    } catch {
      historyAvailable = false;
    }
    renderStudent(borrows);
  } catch (error) {
    renderState("error", error.message || "The student could not be loaded.", { href: "/students.html", label: "Return to students" });
  }
}

function renderStudent(borrows) {
  const student = currentStudent;
  const activeLoans = borrows.filter(record => record.status === "BORROWED");
  const overdue = activeLoans.filter(record => (Date.now() - new Date(record.borrowDate).getTime()) > 14 * 86400000);
  const manager = canManage();
  document.getElementById("studentProfileContainer").innerHTML = `
    <header class="page-head"><p class="eyebrow">Member record</p><div class="page-title-row"><div><h1 class="serif">${esc(student.name)}</h1><p class="page-sub">Student account and borrowing record.</p></div>${manager ? `<div class="page-actions"><button class="btn-ghost" id="editStudentBtn" type="button">Edit</button><button class="btn-ghost btn-danger-ghost" id="deleteStudentBtn" type="button">Deactivate</button></div>` : ""}</div></header>
    <section class="profile-hero-lg" aria-label="Student summary">${avatarMarkup(student.name)}<div class="ph-meta"><h2 class="serif">${esc(student.name)}</h2><div class="ph-tags"><span class="tag tag-role">${esc(student.role || "STUDENT")}</span>${historyAvailable ? `<span class="badge ${overdue.length ? "badge-warn" : "badge-avail"}">${overdue.length ? `${overdue.length} overdue` : "No overdue loans"}</span>` : ""}</div><p class="muted">${esc(student.email || "No email recorded")}</p></div></section>
    <div class="bento">
      <section class="card span-5"><div class="card-head"><div><h3>Account details</h3><p class="muted">Contact details stored for this student.</p></div></div><div class="meta-grid"><div><span class="meta-k">Username</span><span class="meta-v mono">${esc(student.username || "—")}</span></div><div><span class="meta-k">Phone</span><span class="meta-v">${esc(student.phone || "—")}</span></div><div><span class="meta-k">Email</span><span class="meta-v">${esc(student.email || "—")}</span></div></div></section>
      <section class="card span-7"><div class="card-head"><div><h3>Borrowing summary</h3><p class="muted">${historyAvailable ? "Calculated from current circulation records." : "Circulation history is available to library staff."}</p></div></div>${historyAvailable ? `<div class="kpi-row"><div class="kpi"><span class="kpi-v">${borrows.length}</span><span class="kpi-l">All records</span></div><div class="kpi"><span class="kpi-v">${activeLoans.length}</span><span class="kpi-l">Current loans</span></div><div class="kpi"><span class="kpi-v">${overdue.length}</span><span class="kpi-l">Overdue</span></div><div class="kpi"><span class="kpi-v">${borrows.filter(record => record.status === "RETURNED").length}</span><span class="kpi-l">Returned</span></div></div>` : '<div class="empty-state"><p>Use the student dashboard to view loans available to this account.</p></div>'}</section>
      <section class="card span-12"><div class="card-head"><div><h3>Borrow history</h3><p class="muted">${historyAvailable ? "Most recent records for this student." : "Not available at this access level."}</p></div></div>${historyAvailable ? renderHistory(borrows) : ""}</section>
    </div>`;
  if (manager) {
    document.getElementById("editStudentBtn").addEventListener("click", () => openModal("student", async values => {
      await studentsApi.update(student.id, values);
      toast("Student updated successfully.", "success");
      await loadStudent();
    }, student));
    document.getElementById("deleteStudentBtn").addEventListener("click", deleteStudent);
  }
}

function renderHistory(borrows) {
  if (!borrows.length) return '<div class="empty-state"><p>This student has no borrow records.</p></div>';
  return `<div class="timeline">${borrows.map(record => `<div class="tl-row"><span class="tl-dot ${record.status === "RETURNED" ? "dot-in" : "dot-out"}"></span><div class="tl-body">${record.itemType === "BOOK" ? `<a href="/book-details.html?id=${record.itemId}">${esc(record.itemTitle)}</a>` : `<b>${esc(record.itemTitle)}</b>`}<small>${esc(record.borrowDate)}${record.returnDate ? ` — ${esc(record.returnDate)}` : " — current loan"}</small></div><span class="badge ${record.status === "RETURNED" ? "badge-return" : "badge-out"}">${esc(record.status === "RETURNED" ? "Returned" : "Borrowed")}</span></div>`).join("")}</div>`;
}

async function deleteStudent() {
  const confirmed = await confirmDialog(`Deactivate <strong>${esc(currentStudent.name)}</strong>? This cannot be undone.`, "Deactivate student");
  if (!confirmed) return;
  try {
    await studentsApi.delete(currentStudent.id);
    toast("Student deactivated successfully.", "success");
    window.location.assign("/students.html");
  } catch (error) {
    toast(error.message || "The student could not be deactivated.", "error");
  }
}

document.addEventListener("DOMContentLoaded", async () => {
  try { await authApi.csrf(); setCurrentUser(await authApi.me()); } catch {}
  await loadStudent();
});
