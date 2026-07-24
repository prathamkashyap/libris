/**
 * Single browser HTTP boundary. Day 8 page modules will call this rather than
 * scattering endpoint strings, headers, or error parsing through UI code.
 */
export async function requestJson(path, options = {}) {
  const headers = new Headers(options.headers);
  if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  // const csrf = document.cookie.split("; ").find(value => value.startsWith("XSRF-TOKEN="))?.split("=")[1];
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
  if (!response.ok) {
    const error = new Error(body?.message || `Request failed with status ${response.status}.`);
    error.status = response.status;
    error.code = body?.code;
    error.fieldErrors = body?.fieldErrors || [];
    throw error;
  }
  return body;
}
