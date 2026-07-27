import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser, getCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";

document.addEventListener("DOMContentLoaded", async () => {
  try {
    await authApi.csrf();
    const user = await authApi.me();
    setCurrentUser(user);

    const name = user.name || user.username || "User";
    const role = user.role || "Member";

    // Profile hero
    const heroName = document.querySelector(".profile-hero-lg h2");
    const heroTags = document.querySelector(".ph-tags");
    const heroMeta = document.querySelector(".ph-meta p.muted");
    if (heroName) heroName.textContent = esc(name);
    if (heroTags) heroTags.innerHTML = `<span class="tag tag-role">${esc(role)}</span>`;
    if (heroMeta) {
      const since = user.createdAt ? `Joined ${new Date(user.createdAt).toLocaleDateString("en-US", { year: "numeric", month: "long" })}` : "";
      heroMeta.textContent = since || `Registered as ${esc(role)}`;
    }

    // Contact tab
    const contactRows = document.querySelectorAll('[data-tab-panel="pf-overview"] .mini-row');
    if (contactRows.length >= 3) {
      if (contactRows[0]) contactRows[0].innerHTML = `<span>Email</span><span>${esc(user.email || "—")}</span>`;
      if (contactRows[1]) contactRows[1].innerHTML = `<span>Phone</span><span>${esc(user.phone || "—")}</span>`;
    }

    // Role tab
    const roleRows = document.querySelectorAll('[data-tab-panel="pf-overview"] .card:nth-child(2) .mini-row');
    if (roleRows.length >= 1 && roleRows[0]) {
      roleRows[0].innerHTML = `<span>Role</span><span class="tag tag-role">${esc(role)}</span>`;
    }

    // Avatar
    const avatar = document.querySelector(".profile-hero-lg img");
    if (avatar) {
      const seed = encodeURIComponent(name.replace(/\s+/g, "-"));
      avatar.src = `https://api.dicebear.com/7.x/notionists/svg?seed=${seed}&backgroundColor=f7e6e9`;
    }
  } catch {
    // Not authenticated; page stays as-is
  }
});
