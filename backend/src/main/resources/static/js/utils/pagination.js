import { esc } from "/js/utils/esc.js";

export function renderPagination(container, page, totalPages, onPage) {
  if (totalPages <= 1) { container.innerHTML = ""; return; }
  const p = page;
  const t = totalPages;
  let html = '<div class="pager">';

  html += `<button data-page="${p - 1}" ${p === 0 ? "disabled" : ""}>&lsaquo;</button>`;

  const range = [];
  const start = Math.max(0, p - 2);
  const end = Math.min(t - 1, p + 2);
  if (start > 0) { range.push(0); if (start > 1) range.push("ellipsis"); }
  for (let i = start; i <= end; i++) range.push(i);
  if (end < t - 1) { if (end < t - 2) range.push("ellipsis"); range.push(t - 1); }

  range.forEach(i => {
    if (i === "ellipsis") { html += '<span>&hellip;</span>'; }
    else { html += `<button data-page="${i}" class="${i === p ? "active" : ""}">${i + 1}</button>`; }
  });

  html += `<button data-page="${p + 1}" ${p === t - 1 ? "disabled" : ""}>&rsaquo;</button>`;
  html += "</div>";
  container.innerHTML = html;

  container.querySelectorAll("button[data-page]").forEach(btn => {
    btn.addEventListener("click", () => {
      const np = parseInt(btn.dataset.page);
      if (!isNaN(np) && np >= 0 && np < t) onPage(np);
    });
  });
}

export function renderPageInfo(container, page, size, totalElements) {
  const start = totalElements === 0 ? 0 : page * size + 1;
  const end = Math.min((page + 1) * size, totalElements);
  container.textContent = `Showing ${start}–${end} of ${totalElements}`;
}
