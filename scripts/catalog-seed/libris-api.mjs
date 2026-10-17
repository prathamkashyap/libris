export class LibrisApiError extends Error {
  constructor(method, pathname, status) {
    super(`${method} ${pathname} failed with HTTP ${status}.`);
    this.name = "LibrisApiError";
    this.method = method;
    this.pathname = pathname;
    this.status = status;
  }
}

export class LibrisApiClient {
  constructor(baseUrl, fetchImpl = fetch) {
    this.baseUrl = validateBaseUrl(baseUrl);
    this.fetchImpl = fetchImpl;
    this.cookies = new Map();
    this.csrfToken = null;
    this.authenticated = false;
  }

  async authenticate(username, password) {
    const csrf = await this.request("GET", "/api/auth/csrf");
    if (csrf.headerName !== "X-XSRF-TOKEN" || typeof csrf.token !== "string" || !csrf.token) {
      throw new Error("CSRF bootstrap response did not match Libris's expected header contract.");
    }
    this.csrfToken = csrf.token;
    if (!this.cookies.has("XSRF-TOKEN")) {
      throw new Error("CSRF bootstrap did not set the expected cookie.");
    }
    await this.request("POST", "/api/auth/login", { username, password });
    this.authenticated = true;
    try {
      const current = await this.request("GET", "/api/auth/me");
      if (!current || typeof current.role !== "string") throw new Error("Authenticated profile response is invalid.");
      return current.role;
    } catch (error) {
      await this.logout().catch(() => {});
      throw error;
    }
  }

  async currentRole() {
    if (!this.authenticated) throw new Error("Authenticate before calling catalog APIs.");
    const current = await this.request("GET", "/api/auth/me");
    return current.role;
  }

  async dashboard() {
    return this.request("GET", "/api/dashboard");
  }

  async listAll(collection) {
    ensureCollection(collection);
    const rows = [];
    let page = 0;
    let totalPages = 1;
    while (page < totalPages) {
      const result = await this.request("GET", `/api/${collection}?page=${page}&size=100`);
      if (!Array.isArray(result?.content) || !Number.isInteger(result.totalPages) || result.totalPages < 0) {
        throw new Error(`Unexpected paginated response from /api/${collection}.`);
      }
      rows.push(...result.content);
      totalPages = result.totalPages;
      page += 1;
      if (page > 10000) throw new Error(`Pagination limit exceeded for /api/${collection}.`);
    }
    return rows;
  }

  async create(collection, payload) {
    ensureCollection(collection);
    return this.request("POST", `/api/${collection}`, payload);
  }

  async getOne(collection, id) {
    ensureCollection(collection);
    try {
      return await this.request("GET", `/api/${collection}/${encodeURIComponent(id)}`);
    } catch (error) {
      if (error instanceof LibrisApiError && error.status === 404) return null;
      throw error;
    }
  }

  async delete(collection, id) {
    ensureCollection(collection);
    return this.request("DELETE", `/api/${collection}/${encodeURIComponent(id)}`);
  }

  async borrowHistoryFor(recordIds) {
    const result = new Set();
    if (recordIds.size === 0) return result;
    let page = 0;
    let totalPages = 1;
    while (page < totalPages) {
      const response = await this.request("GET", `/api/borrow-records?page=${page}&size=100`);
      if (!Array.isArray(response?.content) || !Number.isInteger(response.totalPages) || response.totalPages < 0) {
        throw new Error("Unexpected paginated response from /api/borrow-records.");
      }
      for (const record of response.content) {
        const collection = collectionForItemType(record.itemType);
        const id = String(record.itemId ?? "");
        if (collection && recordIds.has(id)) result.add(`${collection}:${id}`);
      }
      totalPages = response.totalPages;
      page += 1;
      if (page > 10000) throw new Error("Borrow-record pagination limit exceeded.");
    }
    return result;
  }

  async logout() {
    if (!this.authenticated) return;
    try {
      await this.request("POST", "/api/auth/logout");
    } finally {
      this.authenticated = false;
      this.cookies.clear();
      this.csrfToken = null;
    }
  }

  async request(method, pathname, body) {
    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (this.cookies.size) {
      headers.Cookie = [...this.cookies.entries()].map(([key, value]) => `${key}=${value}`).join("; ");
    }
    if (method !== "GET" && this.csrfToken) {
      headers["X-XSRF-TOKEN"] = this.cookies.get("XSRF-TOKEN") || this.csrfToken;
    }

    let response;
    try {
      response = await this.fetchImpl(new URL(pathname, this.baseUrl), {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        redirect: "manual",
        signal: AbortSignal.timeout(20000),
      });
    } catch {
      throw new Error(`${method} ${pathname} could not reach the Libris API.`);
    }
    this.updateCookies(response.headers);
    if (!response.ok) throw new LibrisApiError(method, pathname, response.status);
    if (response.status === 204) return null;
    const text = await response.text();
    if (!text) return null;
    try {
      return JSON.parse(text);
    } catch {
      throw new Error(`${method} ${pathname} returned an invalid JSON response.`);
    }
  }

  updateCookies(headers) {
    const setCookies = typeof headers.getSetCookie === "function" ? headers.getSetCookie() : [];
    for (const setCookie of setCookies) {
      const pair = setCookie.split(";", 1)[0];
      const separator = pair.indexOf("=");
      if (separator <= 0) continue;
      const name = pair.slice(0, separator).trim();
      const value = pair.slice(separator + 1).trim();
      if (!value) this.cookies.delete(name);
      else this.cookies.set(name, value);
    }
  }
}

function validateBaseUrl(value) {
  const parsed = new URL(value);
  const local = ["localhost", "127.0.0.1", "::1", "[::1]"].includes(parsed.hostname);
  if (parsed.protocol !== "https:" && !(parsed.protocol === "http:" && local)) {
    throw new Error("Use HTTPS for Libris API access; HTTP is allowed only for localhost tests.");
  }
  if (parsed.username || parsed.password || parsed.search || parsed.hash || parsed.pathname !== "/") {
    throw new Error("Base URL must contain only the origin; credentials, paths, query, and fragments are not allowed.");
  }
  return parsed;
}

function ensureCollection(collection) {
  if (!["books", "magazines", "newspapers"].includes(collection)) {
    throw new Error("Unsupported catalog collection.");
  }
}

function collectionForItemType(value) {
  const itemType = String(value ?? "").toUpperCase();
  return ({ BOOK: "books", MAGAZINE: "magazines", NEWSPAPER: "newspapers" })[itemType] ?? null;
}
