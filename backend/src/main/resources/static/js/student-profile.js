import { studentsApi } from "/js/api/students-api.js";
import { borrowApi } from "/js/api/borrow-api.js";
import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";

let currentStudent = null;

function getParams() {
  const params = new URLSearchParams(window.location.search);
  return { id: params.get("id") ? parseInt(params.get("id")) : null };
}

function showLoading() {
  document.getElementById("studentProfileContainer").innerHTML = '<div class="loading-state" style="text-align:center;padding:64px 0;color:var(--ink-soft)">Loading student profile&hellip;</div>';
}

function showError(msg) {
  document.getElementById("studentProfileContainer").innerHTML = `<div class="error-state" style="text-align:center;padding:64px 0"><p style="color:var(--red);font-size:14px;margin:0">${esc(msg)}</p><button class="btn-ghost sm" style="margin-top:12px" onclick="location.reload()">Try again</button></div>`;
}

async function loadStudent() {
  const { id } = getParams();
  if (!id) {
    showError("No student ID specified. <a href='students.html' style='color:var(--accent)'>Return to students</a>");
    return;
  }
  showLoading();
  try {
    currentStudent = await studentsApi.get(id);
    let borrows = [];
    try {
      const records = await borrowApi.list(0, 100);
      borrows = (records.content || []).filter(r => r.studentId === id);
    } catch {}
    renderProfile(borrows);
  } catch (err) {
    showError(err.message || "Failed to load student.");
  }
}

function renderProfile(borrows) {
  const s = currentStudent;
  const container = document.getElementById("studentProfileContainer");

  const breadcrumb = document.querySelector(".breadcrumb .current");
  if (breadcrumb) breadcrumb.textContent = s.name;

  const currentBorrows = borrows.filter(r => r.status === "BORROWED");
  const returnedBorrows = borrows.filter(r => r.status === "RETURNED");
  const overdueBorrows = currentBorrows.filter(r => {
    const d = new Date(r.borrowDate);
    return (new Date() - d) / (1000 * 60 * 60 * 24) > 14;
  });

  const totalBorrowed = borrows.length;
  const currentlyOverdue = overdueBorrows.length;

  container.innerHTML = `
    <div class="profile-hero-lg">
      <div class="avatar-edit"><img src="https://api.dicebear.com/7.x/notionists/svg?seed=${esc((s.name||"User").replace(/\s+/g,"-"))}&backgroundColor=2b2540" alt=""></div>
      <div class="ph-meta">
        <h2 class="serif">${esc(s.name)}</h2>
        <div class="ph-tags">
          <span class="tag">${esc(s.role || "Student")}</span>
          ${currentlyOverdue > 0 ? `<span class="badge badge-warn">${currentlyOverdue} overdue</span>` : '<span class="badge badge-avail">Active</span>'}
        </div>
        <p class="muted" style="margin-top:2px">${esc(s.email || "")}${s.phone ? " &middot; " + esc(s.phone) : ""}</p>
      </div>
      <div class="ph-actions">
        <button class="btn-ghost" id="editStudentBtn">Edit</button>
        <button class="btn-ghost" id="deleteStudentBtn" style="color:#B5432F">Deactivate</button>
      </div>
    </div>

    <div class="kpi-row">
      <div class="kpi"><span class="kpi-v">${totalBorrowed}</span><span class="kpi-l">Books borrowed</span></div>
      <div class="kpi"><span class="kpi-v">${currentlyOverdue}</span><span class="kpi-l">Currently overdue</span></div>
      <div class="kpi"><span class="kpi-v">${currentBorrows.length}</span><span class="kpi-l">Active borrows</span></div>
      <div class="kpi"><span class="kpi-v">${returnedBorrows.length}</span><span class="kpi-l">Returned</span></div>
    </div>

    <div class="tabs">
      <button class="tab-btn active" data-tab="overview">Overview</button>
      <button class="tab-btn" data-tab="history">Borrow history</button>
      <button class="tab-btn" data-tab="reservations">Reservations</button>
      <button class="tab-btn" data-tab="activity">Activity</button>
    </div>

    <div data-tab-panel="overview">
      <div class="bento">
        <div class="card span-6">
          <div class="card-head"><h3>Currently borrowed</h3></div>
          ${currentBorrows.length === 0
            ? '<div class="empty-state" style="padding:24px"><p style="color:var(--ink-soft);font-size:13px;margin:0">No active borrows.</p></div>'
            : `<div class="mini-table">${currentBorrows.map(r => `
              <div class="mini-row">
                <span>${esc(r.itemTitle)}</span>
                <span class="muted">Due ${r.borrowDate}</span>
                <span class="badge badge-warn">Active</span>
              </div>
            `).join("")}</div>`
          }
        </div>
        <div class="card span-6">
          <div class="card-head"><h3>Contact</h3></div>
          <div class="mini-row"><span>Email</span><span>${esc(s.email || "—")}</span></div>
          <div class="mini-row"><span>Phone</span><span>${esc(s.phone || "—")}</span></div>
          <div class="mini-row"><span>Username</span><span>${esc(s.username || "—")}</span></div>
        </div>
      </div>
    </div>

    <div data-tab-panel="history" style="display:none">
      <div class="card">
        ${borrows.length === 0
          ? '<div class="empty-state" style="padding:48px"><p style="color:var(--ink-soft);font-size:13px;margin:0">No borrow history.</p></div>'
          : `<div class="mini-table">${borrows.map(r => {
            const isOverdue = r.status === "BORROWED" && (new Date() - new Date(r.borrowDate)) / (1000 * 60 * 60 * 24) > 14;
            return `<div class="mini-row">
              <span>${esc(r.itemTitle)}</span>
              <span class="muted">${r.borrowDate}${r.returnDate ? " &mdash; " + r.returnDate : " &mdash; ongoing"}</span>
              <span class="badge ${r.status === "RETURNED" ? "badge-return" : isOverdue ? "badge-warn" : "badge-out"}">${r.status === "RETURNED" ? "Returned" : "Borrowed"}</span>
            </div>`;
          }).join("")}</div>`
        }
      </div>
    </div>

    <div data-tab-panel="reservations" style="display:none">
      <div class="card">
        <div class="empty-state" style="padding:48px">
          <b>No pending reservations</b>
          <p style="color:var(--ink-soft);font-size:13px">Reservation data not available via the current API.</p>
        </div>
      </div>
    </div>

    <div data-tab-panel="activity" style="display:none">
      <div class="card">
        <div class="empty-state" style="padding:48px">
          <b>No recent activity</b>
          <p style="color:var(--ink-soft);font-size:13px">Activity log not available via the current API.</p>
        </div>
      </div>
    </div>
  `;

  document.getElementById("editStudentBtn").addEventListener("click", openEditModal);
  document.getElementById("deleteStudentBtn").addEventListener("click", deleteStudent);

  initTabs();
}

function initTabs() {
  document.querySelectorAll(".tabs").forEach(tabGroup => {
    const buttons = tabGroup.querySelectorAll(".tab-btn");
    const panels = document.querySelectorAll("[data-tab-panel]");
    buttons.forEach(btn => {
      btn.addEventListener("click", () => {
        buttons.forEach(b => b.classList.remove("active"));
        btn.classList.add("active");
        const target = btn.getAttribute("data-tab");
        panels.forEach(p => {
          p.style.display = (p.getAttribute("data-tab-panel") === target) ? "" : "none";
        });
      });
    });
  });
}

function openEditModal() {
  const s = currentStudent;
  if (!s) return;
  const root = document.getElementById("modal-root");
  root.innerHTML = `
    <div class="modal-backdrop" role="presentation">
      <section class="modal" role="dialog" aria-modal="true">
        <div class="modal-header">
          <h2 class="serif">Edit student</h2>
          <button class="modal-close modal-close-student" type="button">&times;</button>
        </div>
        <p class="form-error" data-student-error hidden></p>
        <form id="student-form" novalidate>
          <div class="form-grid">
            <label class="field span-all"><span>Name *</span><input name="name" value="${esc(s.name)}" required><span class="field-error" data-error="name"></span></label>
            <label class="field"><span>Email *</span><input name="email" type="email" value="${esc(s.email || "")}" required><span class="field-error" data-error="email"></span></label>
            <label class="field"><span>Phone *</span><input name="phone" type="tel" value="${esc(s.phone || "")}" required><span class="field-error" data-error="phone"></span></label>
            <label class="field"><span>Username *</span><input name="username" value="${esc(s.username || "")}" required><span class="field-error" data-error="username"></span></label>
          </div>
          <div class="modal-footer">
            <button class="btn-ghost modal-close-student" type="button">Cancel</button>
            <button class="btn-primary" type="submit">Save changes</button>
          </div>
        </form>
      </section>
    </div>`;
  const close = () => { root.innerHTML = ""; };
  root.querySelectorAll(".modal-close-student").forEach(b => b.addEventListener("click", close));
  root.querySelector(".modal-backdrop").addEventListener("click", e => { if (e.target === e.currentTarget) close(); });
  const fi = root.querySelector("input"); if (fi) fi.focus();
  root.querySelector("form").addEventListener("submit", async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const values = Object.fromEntries(fd);
    const errEl = root.querySelector("[data-student-error]");
    errEl.hidden = true;
    if (!values.name?.trim()) { root.querySelector('[data-error="name"]').textContent = "Name is required."; return; }
    try {
      await studentsApi.update(s.id, values);
      toast("Student updated.", "success");
      close();
      loadStudent();
    } catch (err) {
      const fieldErrors = err.fieldErrors || [];
      fieldErrors.forEach(({ field, message }) => { const fe = root.querySelector(`[data-error="${field}"]`); if (fe) fe.textContent = message; });
      const unmatched = fieldErrors.filter(({ field }) => !root.querySelector(`[data-error="${field}"]`)).map(({ message }) => message);
      errEl.textContent = unmatched.join(" ") || err.message || "Failed to save.";
      errEl.hidden = false;
    }
  });
}

async function deleteStudent() {
  if (!currentStudent) return;
  const ok = await confirmDialog(`Deactivate <strong>${esc(currentStudent.name)}</strong>? This action cannot be undone.`, "Deactivate");
  if (!ok) return;
  try {
    await studentsApi.delete(currentStudent.id);
    toast("Student deactivated.", "success");
    window.location.href = "students.html";
  } catch (err) {
    toast(err.message || "Failed to deactivate.", "error");
  }
}

async function initAuth() {
  try { await authApi.csrf(); const u = await authApi.me(); setCurrentUser(u); } catch {}
}

document.addEventListener("DOMContentLoaded", () => {
  initAuth();
  loadStudent();
});
