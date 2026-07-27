import { analyticsApi } from "/js/api/analytics-api.js";
import { borrowApi } from "/js/api/borrow-api.js";
import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";

function fmtNum(n) {
  if (n >= 1000) return (n / 1000).toFixed(n % 1000 === 0 ? 0 : 1) + "k";
  return String(n);
}

document.addEventListener("DOMContentLoaded", async () => {
  try {
    await authApi.csrf();
    const user = await authApi.me();
    setCurrentUser(user);
    const name = user.name || user.username || "there";
    const greeting = document.getElementById("dashboardGreeting");
    if (greeting) {
      const hour = new Date().getHours();
      const time = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
      greeting.textContent = `${time}, ${esc(name)}.`;
    }
  } catch {}

  try {
    const [stats, overdueData, recordsData] = await Promise.all([
      analyticsApi.dashboard(),
      analyticsApi.overdue(),
      borrowApi.list(0, 10)
    ]);

    const el = id => document.getElementById(id);

    const booksEl = el("statBooks");
    const studentsEl = el("statStudents");
    const borrowedEl = el("statBorrowed");
    const availableEl = el("statAvailable");
    const overdueEl = el("statOverdue");

    if (booksEl) booksEl.textContent = fmtNum(stats.totalBooks);
    if (studentsEl) studentsEl.textContent = fmtNum(stats.totalStudents);
    if (borrowedEl) borrowedEl.textContent = fmtNum(stats.borrowedBooks);
    if (availableEl) availableEl.textContent = fmtNum(stats.availableBooks);
    if (overdueEl) {
      overdueEl.textContent = String(stats.overdueCount);
      const delta = el("statOverdueDelta");
      if (delta) delta.textContent = stats.overdueCount > 0 ? `${stats.overdueCount} need attention` : "All clear";
    }

    const sub = document.getElementById("dashboardSub");
    if (sub) {
      sub.textContent = `${fmtNum(stats.borrowedBooks)} volumes checked out, ${fmtNum(stats.availableBooks)} available.`;
    }

    const records = recordsData.content || [];
    const recentList = document.getElementById("recentBorrowsList");
    if (recentList && records.length > 0) {
      recentList.innerHTML = records.slice(0, 5).map(r => {
        const isReturn = r.status === "RETURNED";
        const dotClass = isReturn ? "dot-in" : "dot-out";
        const action = isReturn ? "returned" : "borrowed";
        return `<div class="activity-row"><span class="dot ${dotClass}"></span><div><b>${esc(r.borrowerName)}</b> ${action} <i>${esc(r.itemTitle)}</i><small>${r.borrowDate}${r.returnDate ? " &mdash; " + r.returnDate : ""}</small></div></div>`;
      }).join("");
    }

    const overdueList = document.getElementById("overdueList");
    if (overdueList) {
      const items = overdueData.items || [];
      if (items.length > 0) {
        overdueList.innerHTML = items.slice(0, 5).map(r => {
          return `<div class="activity-row"><span class="dot dot-warn"></span><div><b>${esc(r.borrowerName)}</b> &mdash; <i>${esc(r.itemTitle)}</i>, ${r.daysOverdue} days late<small>Active borrow</small></div></div>`;
        }).join("");
      } else {
        overdueList.innerHTML = '<div class="activity-row"><span class="dot dot-in"></span><div><b>No overdue items</b><small>All clear</small></div></div>';
      }
    }
  } catch (err) {
    console.error("Dashboard load error:", err);
    const sub = document.getElementById("dashboardSub");
    if (sub) sub.textContent = "Could not load dashboard data.";
  }
});
