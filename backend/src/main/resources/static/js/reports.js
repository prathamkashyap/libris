import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";

document.addEventListener("DOMContentLoaded", async () => {
  try {
    await authApi.csrf();
    const user = await authApi.me();
    setCurrentUser(user);
  } catch {}

  const exports = {
    "Monthly circulation": "/api/reports/borrowing?format=csv",
    "Collection audit": "/api/reports/inventory?format=csv",
    "Overdue summary": "/api/reports/overdue?format=csv"
  };

  document.querySelectorAll(".card.span-4").forEach(card => {
    const heading = card.querySelector("b");
    if (!heading) return;
    const key = heading.textContent.trim();
    const url = exports[key];
    if (!url) return;
    const csvBtn = Array.from(card.querySelectorAll(".btn-ghost.sm")).find(b => b.textContent.trim() === "CSV");
    if (csvBtn) csvBtn.addEventListener("click", e => { e.preventDefault(); const a = document.createElement("a"); a.href = url; a.click(); });
  });
});
