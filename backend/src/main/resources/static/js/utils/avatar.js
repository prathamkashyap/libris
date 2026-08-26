import { esc } from "/js/utils/esc.js";

export function initials(name) {
  return String(name || "User")
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map(part => part[0])
    .join("")
    .toUpperCase() || "U";
}

export function avatarMarkup(name, className = "entity-avatar") {
  return `<span class="${className}" aria-hidden="true">${esc(initials(name))}</span>`;
}
