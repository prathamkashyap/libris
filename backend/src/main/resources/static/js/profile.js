import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { avatarMarkup } from "/js/utils/avatar.js";

function showError(message) {
  document.getElementById("profileContainer").innerHTML = `<div class="error-state"><p>${esc(message)}</p><a class="btn-ghost sm" href="/login.html">Go to sign in</a></div>`;
}

function renderProfile(user) {
  const name = user.displayName || user.username || "Library member";
  const role = user.role || "Member";
  document.getElementById("profileContainer").innerHTML = `
    <header class="page-head"><p class="eyebrow">Account</p><h1 class="serif">My account</h1><p class="page-sub">Your current Libris workspace identity.</p></header>
    <section class="profile-hero-lg" aria-label="Account summary">
      ${avatarMarkup(name)}
      <div class="ph-meta"><h2 class="serif">${esc(name)}</h2><div class="ph-tags"><span class="tag tag-role">${esc(role)}</span></div><p class="muted">Signed in to the current browser session</p></div>
      <div class="ph-actions"><button class="btn-ghost" id="signOutButton" type="button">Sign out</button></div>
    </section>
    <div class="bento">
      <section class="card span-6"><div class="card-head"><div><h3>Account details</h3><p class="muted">Details exposed by the current account API.</p></div></div>
        <div class="meta-grid"><div><span class="meta-k">Username</span><span class="meta-v mono">${esc(user.username || "—")}</span></div><div><span class="meta-k">Role</span><span class="meta-v">${esc(role)}</span></div><div><span class="meta-k">Account ID</span><span class="meta-v mono">${user.accountId ?? "—"}</span></div></div>
      </section>
      <section class="card span-6"><div class="card-head"><div><h3>Workspace preferences</h3><p class="muted">The colour theme is saved in this browser.</p></div></div>
        <p class="muted">Use the theme control in the top bar to switch between the dark and light application themes. Account profile editing and password changes are not exposed by the current API.</p>
        <a class="btn-ghost" href="/books.html">Browse the catalog</a>
      </section>
    </div>`;
  document.getElementById("signOutButton").addEventListener("click", async () => {
    await authApi.logout();
    setCurrentUser(null);
    window.location.replace("/login.html");
  });
}

document.addEventListener("DOMContentLoaded", async () => {
  try {
    await authApi.csrf();
    const user = await authApi.profile();
    setCurrentUser(user);
    renderProfile(user);
  } catch (error) {
    showError(error.message || "Your account could not be loaded.");
  }
});
