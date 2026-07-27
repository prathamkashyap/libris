/**
 * Single browser HTTP boundary. Day 8 page modules will call this rather than
 * scattering endpoint strings, headers, or error parsing through UI code.
 */
let currentUser = null;

function updateRail(user) {
  if (!user) return;
  const railName = document.querySelector(".rail-user .ru-name");
  const railRole = document.querySelector(".rail-user .ru-role");
  const railImg = document.querySelector(".rail-user img");
  const name = user.name || user.username || "User";
  const role = user.role || "Member";
  if (railName) railName.textContent = name;
  if (railRole) railRole.textContent = role;
  if (railImg) {
    const seed = encodeURIComponent(name.replace(/\s+/g, "-"));
    railImg.src = `https://api.dicebear.com/7.x/notionists/svg?seed=${seed}&backgroundColor=2b2540`;
  }
}

export function setCurrentUser(user) {
  currentUser = user;
  updateRail(user);
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
    // This is a multi-page application, so hash routing leaves the user on the
    // protected page. Send an expired session to the real sign-in page instead.
    if (!window.location.pathname.endsWith("/login.html")) {
      window.location.replace("/login.html");
    }
    throw new Error('Session expired. Please log in again.');
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
