import { librariansApi } from "/js/api/librarians-api.js";
import { authApi } from "/js/api/auth-api.js";
import { getCurrentUser, setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { avatarMarkup } from "/js/utils/avatar.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";
import { openModal } from "/components/modal.js";

let currentLibrarian = null;

function getId() {
  const id = Number(new URLSearchParams(window.location.search).get("id"));
  return Number.isInteger(id) && id > 0 ? id : null;
}

function isAdmin() {
  return (getCurrentUser()?.role || "").toUpperCase() === "ADMIN";
}

function renderState(type, message, action) {
  document.getElementById("librarianProfileContainer").innerHTML = `<div class="${type}-state"><p>${esc(message)}</p>${action ? `<a class="btn-ghost sm" href="${action.href}">${esc(action.label)}</a>` : ""}</div>`;
}

async function loadLibrarian() {
  const id = getId();
  if (!id) return renderState("error", "No team member was selected.", { href: "/librarians.html", label: "Return to team" });
  renderState("loading", "Loading team member details…");
  try {
    currentLibrarian = await librariansApi.get(id);
    renderLibrarian();
  } catch (error) {
    renderState("error", error.message || "The team member could not be loaded.", { href: "/librarians.html", label: "Return to team" });
  }
}

function renderLibrarian() {
  const librarian = currentLibrarian;
  const admin = isAdmin();
  document.getElementById("librarianProfileContainer").innerHTML = `
    <header class="page-head"><p class="eyebrow">Team record</p><div class="page-title-row"><div><h1 class="serif">${esc(librarian.name)}</h1><p class="page-sub">Staff account details maintained by Libris.</p></div>${admin ? `<div class="page-actions"><button class="btn-ghost" id="editLibrarianBtn" type="button">Edit</button><button class="btn-ghost btn-danger-ghost" id="deleteLibrarianBtn" type="button">Remove</button></div>` : ""}</div></header>
    <section class="profile-hero-lg" aria-label="Team member summary">${avatarMarkup(librarian.name)}<div class="ph-meta"><h2 class="serif">${esc(librarian.name)}</h2><div class="ph-tags"><span class="tag tag-role">${esc(librarian.role || "LIBRARIAN")}</span></div><p class="muted">${esc(librarian.username || "No username recorded")}</p></div></section>
    <div class="bento"><section class="card span-8"><div class="card-head"><div><h3>Account details</h3><p class="muted">The fields exposed by the staff account API.</p></div></div><div class="meta-grid"><div><span class="meta-k">Username</span><span class="meta-v mono">${esc(librarian.username || "—")}</span></div><div><span class="meta-k">Role</span><span class="meta-v">${esc(librarian.role || "—")}</span></div><div><span class="meta-k">Age</span><span class="meta-v">${librarian.age ?? "—"}</span></div><div><span class="meta-k">Phone</span><span class="meta-v">${esc(librarian.phone || "—")}</span></div></div></section><section class="card span-4"><div class="card-head"><div><h3>Access</h3><p class="muted">Role authorization is enforced by the server.</p></div></div><p class="muted">This page intentionally does not infer permissions from the interface. The current role is shown above; protected actions are authorized by the backend.</p></section></div>`;
  if (admin) {
    document.getElementById("editLibrarianBtn").addEventListener("click", () => openModal("librarian", async values => {
      await librariansApi.update(librarian.id, values);
      toast("Team member updated successfully.", "success");
      await loadLibrarian();
    }, librarian));
    document.getElementById("deleteLibrarianBtn").addEventListener("click", deleteLibrarian);
  }
}

async function deleteLibrarian() {
  const confirmed = await confirmDialog(`Remove <strong>${esc(currentLibrarian.name)}</strong>? This cannot be undone.`, "Remove team member");
  if (!confirmed) return;
  try {
    await librariansApi.delete(currentLibrarian.id);
    toast("Team member removed successfully.", "success");
    window.location.assign("/librarians.html");
  } catch (error) {
    toast(error.message || "The team member could not be removed.", "error");
  }
}

document.addEventListener("DOMContentLoaded", async () => {
  try { await authApi.csrf(); setCurrentUser(await authApi.me()); } catch {}
  await loadLibrarian();
});
