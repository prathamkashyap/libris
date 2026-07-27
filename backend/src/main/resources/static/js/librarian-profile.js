import { librariansApi } from "/js/api/librarians-api.js";
import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";

let currentLibrarian = null;

function getParams() {
  const params = new URLSearchParams(window.location.search);
  return { id: params.get("id") ? parseInt(params.get("id")) : null };
}

function showLoading() {
  document.getElementById("librarianProfileContainer").innerHTML = '<div class="loading-state" style="text-align:center;padding:64px 0;color:var(--ink-soft)">Loading librarian profile&hellip;</div>';
}

function showError(msg) {
  document.getElementById("librarianProfileContainer").innerHTML = `<div class="error-state" style="text-align:center;padding:64px 0"><p style="color:var(--red);font-size:14px;margin:0">${esc(msg)}</p><button class="btn-ghost sm" style="margin-top:12px" onclick="location.reload()">Try again</button></div>`;
}

async function loadLibrarian() {
  const { id } = getParams();
  if (!id) {
    showError("No librarian ID specified. <a href='librarians.html' style='color:var(--accent)'>Return to librarians</a>");
    return;
  }
  showLoading();
  try {
    currentLibrarian = await librariansApi.get(id);
    renderProfile();
  } catch (err) {
    showError(err.message || "Failed to load librarian.");
  }
}

function renderProfile() {
  const l = currentLibrarian;
  const container = document.getElementById("librarianProfileContainer");

  const breadcrumb = document.querySelector(".breadcrumb .current");
  if (breadcrumb) breadcrumb.textContent = l.name;

  container.innerHTML = `
    <div class="profile-hero-lg">
      <img src="https://api.dicebear.com/7.x/notionists/svg?seed=${esc((l.name||"").replace(/\s+/g,"-"))}&backgroundColor=2b2540" alt="">
      <div class="ph-meta">
        <h2 class="serif">${esc(l.name)}</h2>
        <div class="ph-tags">
          <span class="tag tag-role role-lib">${esc(l.role || "Librarian")}</span>
          <span class="tag">Staff</span>
        </div>
        <p class="muted" style="margin-top:2px">${esc(l.username || "")}${l.phone ? " &middot; " + esc(l.phone) : ""}</p>
      </div>
      <div class="ph-actions">
        <button class="btn-ghost" id="editLibrarianBtn">Edit</button>
        <button class="btn-ghost" id="deleteLibrarianBtn" style="color:#B5432F">Remove</button>
      </div>
    </div>

    <div class="kpi-row">
      <div class="kpi"><span class="kpi-v">${l.age || "—"}</span><span class="kpi-l">Age</span></div>
      <div class="kpi"><span class="kpi-v">${esc(l.role || "Staff")}</span><span class="kpi-l">Role</span></div>
      <div class="kpi"><span class="kpi-v">${esc(l.username || "—")}</span><span class="kpi-l">Username</span></div>
      <div class="kpi"><span class="kpi-v">${l.phone ? esc(l.phone) : "—"}</span><span class="kpi-l">Phone</span></div>
    </div>

    <div class="bento">
      <div class="card span-7">
        <div class="card-head"><h3>Account info</h3></div>
        <div class="meta-grid">
          <div><span class="meta-k">Name</span><span class="meta-v">${esc(l.name)}</span></div>
          <div><span class="meta-k">Username</span><span class="meta-v mono">${esc(l.username || "—")}</span></div>
          <div><span class="meta-k">Age</span><span class="meta-v">${l.age ? l.age : "—"}</span></div>
          <div><span class="meta-k">Phone</span><span class="meta-v">${esc(l.phone || "—")}</span></div>
          <div><span class="meta-k">Role</span><span class="meta-v"><span class="tag tag-role role-lib">${esc(l.role || "Librarian")}</span></span></div>
        </div>
      </div>
      <div class="card span-5">
        <div class="card-head"><h3>Permissions</h3></div>
        <div class="mini-table">
          <div class="mini-row"><span>Manage catalog</span><span class="badge badge-avail">Allowed</span></div>
          <div class="mini-row"><span>Issue &amp; return books</span><span class="badge badge-avail">Allowed</span></div>
          <div class="mini-row"><span>Manage staff accounts</span><span class="badge badge-muted">Restricted</span></div>
          <div class="mini-row"><span>System settings</span><span class="badge badge-muted">Restricted</span></div>
        </div>
      </div>
    </div>
  `;

  document.getElementById("editLibrarianBtn").addEventListener("click", openEditModal);
  document.getElementById("deleteLibrarianBtn").addEventListener("click", deleteLibrarian);
}

function openEditModal() {
  const l = currentLibrarian;
  if (!l) return;
  const root = document.getElementById("modal-root");
  root.innerHTML = `
    <div class="modal-backdrop" role="presentation">
      <section class="modal" role="dialog" aria-modal="true">
        <div class="modal-header">
          <h2 class="serif">Edit librarian</h2>
          <button class="modal-close modal-close-lib" type="button">&times;</button>
        </div>
        <p class="form-error" data-lib-error hidden></p>
        <form id="lib-form" novalidate>
          <div class="form-grid">
            <label class="field span-all"><span>Name *</span><input name="name" value="${esc(l.name)}" required><span class="field-error" data-error="name"></span></label>
            <label class="field"><span>Age *</span><input name="age" type="number" value="${l.age || ""}" required><span class="field-error" data-error="age"></span></label>
            <label class="field"><span>Phone *</span><input name="phone" type="tel" value="${esc(l.phone || "")}" required><span class="field-error" data-error="phone"></span></label>
            <label class="field"><span>Username *</span><input name="username" value="${esc(l.username || "")}" required><span class="field-error" data-error="username"></span></label>
          </div>
          <div class="modal-footer">
            <button class="btn-ghost modal-close-lib" type="button">Cancel</button>
            <button class="btn-primary" type="submit">Save changes</button>
          </div>
        </form>
      </section>
    </div>`;
  const close = () => { root.innerHTML = ""; };
  root.querySelectorAll(".modal-close-lib").forEach(b => b.addEventListener("click", close));
  root.querySelector(".modal-backdrop").addEventListener("click", e => { if (e.target === e.currentTarget) close(); });
  const fi = root.querySelector("input"); if (fi) fi.focus();
  root.querySelector("form").addEventListener("submit", async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const values = Object.fromEntries(fd);
    const errEl = root.querySelector("[data-lib-error]");
    errEl.hidden = true;
    if (!values.name?.trim()) { root.querySelector('[data-error="name"]').textContent = "Name is required."; return; }
    try {
      await librariansApi.update(l.id, values);
      toast("Librarian updated.", "success");
      close();
      loadLibrarian();
    } catch (err) {
      const fieldErrors = err.fieldErrors || [];
      fieldErrors.forEach(({ field, message }) => { const fe = root.querySelector(`[data-error="${field}"]`); if (fe) fe.textContent = message; });
      const unmatched = fieldErrors.filter(({ field }) => !root.querySelector(`[data-error="${field}"]`)).map(({ message }) => message);
      errEl.textContent = unmatched.join(" ") || err.message || "Failed to save.";
      errEl.hidden = false;
    }
  });
}

async function deleteLibrarian() {
  if (!currentLibrarian) return;
  const ok = await confirmDialog(`Remove <strong>${esc(currentLibrarian.name)}</strong>? This cannot be undone.`, "Remove");
  if (!ok) return;
  try {
    await librariansApi.delete(currentLibrarian.id);
    toast("Librarian removed.", "success");
    window.location.href = "librarians.html";
  } catch (err) {
    toast(err.message || "Failed to remove.", "error");
  }
}

async function initAuth() {
  try { await authApi.csrf(); const u = await authApi.me(); setCurrentUser(u); } catch {}
}

document.addEventListener("DOMContentLoaded", () => {
  initAuth();
  loadLibrarian();
});
