/**
 * Single browser HTTP boundary. Day 8 page modules will call this rather than
 * scattering endpoint strings, headers, or error parsing through UI code.
 */
let currentUser = null;

function updateRail(user) {
  if (!user) return;
  const railName = document.querySelector(".rail-user .ru-name");
  const railRole = document.querySelector(".rail-user .ru-role");
  const railInitials = document.querySelector(".rail-user .avatar-initials");
  const topbarName = document.getElementById("topbar-user-name");
  const topbarRole = document.getElementById("topbar-user-role");
  const topbarInitials = document.getElementById("topbar-user-initials");
  const name = user.name || user.username || "User";
  const role = user.role || "Member";
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join("").toUpperCase() || "U";
  if (railName) railName.textContent = name;
  if (railRole) railRole.textContent = role;
  if (railInitials) railInitials.textContent = initials;
  if (topbarName) topbarName.textContent = name;
  if (topbarRole) topbarRole.textContent = role;
  if (topbarInitials) topbarInitials.textContent = initials;
}

export function setCurrentUser(user) {
  currentUser = user;
  updateRail(user);
  window.dispatchEvent(new CustomEvent("userchange", { detail: { user } }));
}

export function getCurrentUser() {
  return currentUser;
}

export async function requestJson(path, options = {}) {
  const headers = new Headers(options.headers);
  if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const csrfCookie = document.cookie
    .split("; ")
    .find(cookie => cookie.startsWith("XSRF-TOKEN="));

  const csrf = csrfCookie
      ? csrfCookie.substring("XSRF-TOKEN=".length)
      : null;

  if (csrf && !["GET", "HEAD", "OPTIONS"].includes((options.method || "GET").toUpperCase())) headers.set("X-XSRF-TOKEN", decodeURIComponent(csrf));
  const response = await fetch(path, { credentials: "include", ...options, headers });
  const isJson = response.headers.get("content-type")?.includes("application/json");
  const body = response.status === 204 ? null : isJson ? await response.json() : null;
  
  if (response.status === 401) {
    currentUser = null;
    // On pages other than login, redirect to login with a session expired message
    if (!window.location.pathname.endsWith("/login.html") && !window.location.pathname.endsWith("/register.html")) {
      window.location.replace("/login.html");
      throw new Error('Session expired. Please log in again.');
    }
    const error = new Error(body?.message || 'Invalid username or password. Please check your credentials.');
    error.status = 401;
    error.code = body?.code;
    throw error;
  }
  
  if (!response.ok) {
    const error = new Error(body?.message || `Request failed with status ${response.status}.`);
    error.status = response.status;
    error.code = body?.code;
    error.fieldErrors = body?.fieldErrors || [];
    throw error;
  }
  return body;
}
