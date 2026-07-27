const region = document.createElement("div");
region.className = "toast-region";
region.setAttribute("aria-live", "polite");
document.body.appendChild(region);

let id = 0;
export function toast(message, type = "info") {
  id++;
  const el = document.createElement("div");
  el.className = `toast toast-${type}`;
  el.textContent = message;
  el.id = "toast-" + id;
  el.setAttribute("role", "status");
  region.appendChild(el);
  setTimeout(() => { el.style.opacity = "0"; setTimeout(() => el.remove(), 300); }, 3500);
}
