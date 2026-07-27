import { analyticsApi } from "/js/api/analytics-api.js";
import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";

document.addEventListener("DOMContentLoaded", async () => {
  try {
    await authApi.csrf();
    const user = await authApi.me();
    setCurrentUser(user);
  } catch {}

  try {
    const [stats, trends, topBooks, topReaders] = await Promise.all([
      analyticsApi.dashboard(),
      analyticsApi.trends(),
      analyticsApi.topBooks(5),
      analyticsApi.topReaders(5)
    ]);

    const statValues = document.querySelectorAll(".stat-grid .stat-value");
    if (statValues.length >= 4) {
      const fillRate = stats.totalBooks > 0
        ? Math.round((1 - stats.borrowedBooks / stats.totalBooks) * 100)
        : 100;
      statValues[0].textContent = `${stats.overdueCount}`;
      if (statValues[0].querySelector("small")) statValues[0].innerHTML = `${stats.overdueCount} <small>overdue</small>`;
      statValues[1].textContent = `${trends.length > 0 ? trends[trends.length - 1].count : 0}`;
      statValues[2].textContent = `${topBooks.length > 0 ? topBooks[0].borrowCount : 0}`;
      statValues[3].textContent = `${fillRate}%`;
    }

    const rankCard = document.querySelector(".card.span-6 .rank-list");
    if (rankCard) {
      const parent = rankCard.closest(".card");
      const head = parent?.querySelector(".card-head h3");
      if (head) head.textContent = "Active borrowers";
      if (topReaders.length > 0) {
        rankCard.innerHTML = topReaders.map((r, i) => `
          <li><span class="rk">${String(i + 1).padStart(2, "0")}</span>
          <img src="https://api.dicebear.com/7.x/notionists/svg?seed=${encodeURIComponent(r.name.replace(/\s+/g,"-"))}&backgroundColor=2b2540" style="width:30px;height:30px;border-radius:50%" alt="">
          <div><b>${esc(r.name)}</b><small>${r.borrowCount} borrows</small></div><span class="rk-count">${r.borrowCount}</span></li>
        `).join("");
      }
    }
  } catch {}
});
