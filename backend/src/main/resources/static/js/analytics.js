import { analyticsApi } from "/js/api/analytics-api.js";
import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { avatarMarkup } from "/js/utils/avatar.js";

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function setText(id, value) {
  document.getElementById(id).textContent = String(value);
}

function renderError(message) {
  const error = document.getElementById("analyticsError");
  error.className = "error-state";
  error.textContent = message;
  error.hidden = false;
  document.getElementById("analyticsStatus").textContent = message;
}

function renderStats(stats) {
  setText("stat-total-books", stats.totalBooks);
  setText("stat-active-loans", stats.borrowedBooks);
  setText("stat-available-books", stats.availableBooks);
  setText("stat-overdue", stats.overdueCount);
  const total = Math.max(stats.totalBooks, 1);
  const availableRate = Math.round((stats.availableBooks / total) * 100);
  document.getElementById("availabilitySummary").innerHTML = `<strong class="availability-value">${availableRate}%</strong><span class="availability-label">${stats.availableBooks} of ${stats.totalBooks} catalog titles are available</span><progress class="availability-meter" aria-label="Catalog availability" max="${stats.totalBooks}" value="${stats.availableBooks}">${availableRate}%</progress>`;
}

function renderTrend(trends) {
  const root = document.getElementById("trendChart");
  if (!trends.length) {
    root.innerHTML = '<div class="empty-state"><p>Borrowing trend data will appear after circulation records are created.</p></div>';
    return;
  }
  const width = 720;
  const height = 250;
  const padding = { top: 20, right: 22, bottom: 42, left: 38 };
  const max = Math.max(...trends.map(trend => trend.count), 1);
  const chartWidth = width - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;
  const points = trends.map((trend, index) => {
    const x = padding.left + (trends.length === 1 ? chartWidth / 2 : index * chartWidth / (trends.length - 1));
    const y = padding.top + chartHeight - (trend.count / max) * chartHeight;
    return { x, y, trend };
  });
  const line = points.map(point => `${point.x},${point.y}`).join(" ");
  const area = `M ${points[0].x} ${padding.top + chartHeight} L ${points.map(point => `${point.x} ${point.y}`).join(" L ")} L ${points.at(-1).x} ${padding.top + chartHeight} Z`;
  const grid = [0, .25, .5, .75, 1].map(ratio => {
    const y = padding.top + chartHeight - chartHeight * ratio;
    return `<line x1="${padding.left}" y1="${y}" x2="${width - padding.right}" y2="${y}"></line><text x="${padding.left - 8}" y="${y + 4}" text-anchor="end">${Math.round(max * ratio)}</text>`;
  }).join("");
  const labels = points.map(point => `<text x="${point.x}" y="${height - 13}" text-anchor="middle">${MONTHS[point.trend.month - 1]} ${String(point.trend.year).slice(-2)}</text>`).join("");
  const dots = points.map(point => `<circle cx="${point.x}" cy="${point.y}" r="4"><title>${MONTHS[point.trend.month - 1]} ${point.trend.year}: ${point.trend.count} borrow${point.trend.count === 1 ? "" : "s"}</title></circle>`).join("");
  root.innerHTML = `<svg viewBox="0 0 ${width} ${height}" aria-hidden="true"><g class="trend-grid">${grid}</g><path class="trend-area" d="${area}"></path><polyline class="trend-line" points="${line}"></polyline><g class="trend-dots">${dots}</g><g class="trend-labels">${labels}</g></svg>`;
}

function renderReaders(readers) {
  const root = document.getElementById("top-readers");
  root.innerHTML = readers.length ? readers.map((reader, index) => `<li><span class="rk">${String(index + 1).padStart(2, "0")}</span>${avatarMarkup(reader.name, "entity-avatar")}<div><b>${esc(reader.name)}</b><small>${reader.borrowCount} borrow${reader.borrowCount === 1 ? "" : "s"}</small></div><span class="rk-count">${reader.borrowCount}</span></li>`).join("") : '<li class="rank-empty">No borrowing records yet.</li>';
}

function renderBooks(books) {
  const root = document.getElementById("top-books");
  root.innerHTML = books.length ? books.map((book, index) => `<li><span class="rk">${String(index + 1).padStart(2, "0")}</span><span class="cov cov-${(index % 7) + 1}" aria-hidden="true"></span><div><a href="/book-details.html?id=${book.id}"><b>${esc(book.title)}</b></a><small>${esc(book.author || "Author not recorded")}</small></div><span class="rk-count">${book.borrowCount}</span></li>`).join("") : '<li class="rank-empty">No borrowing records yet.</li>';
}

document.addEventListener("DOMContentLoaded", async () => {
  try { await authApi.csrf(); setCurrentUser(await authApi.me()); } catch {}
  try {
    const [stats, trends, topBooks, topReaders] = await Promise.all([analyticsApi.dashboard(), analyticsApi.trends(), analyticsApi.topBooks(5), analyticsApi.topReaders(5)]);
    renderStats(stats);
    renderTrend(trends);
    renderReaders(topReaders);
    renderBooks(topBooks);
    document.getElementById("analyticsStatus").textContent = "Analytics loaded.";
  } catch (error) {
    renderError(error.message || "Analytics could not be loaded.");
  }
});
