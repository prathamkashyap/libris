import assert from "node:assert/strict";
import { assertBaseUrlAllowed, assertWriteTargetConfirmation } from "./import-catalog.mjs";
import test from "node:test";
import { LibrisApiClient } from "./libris-api.mjs";

function response(status, body, cookies = []) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { getSetCookie: () => cookies },
    text: async () => body === null ? "" : JSON.stringify(body),
  };
}

test("API client bootstraps CSRF, retains cookies, and persists login session", async () => {
  const calls = [];
  const fetchImpl = async (url, options) => {
    const path = new URL(url).pathname;
    calls.push({ path, options });
    if (path === "/api/auth/csrf") return response(200, { headerName: "X-XSRF-TOKEN", token: "csrf-value" }, ["XSRF-TOKEN=csrf-value; Path=/; Secure"]);
    if (path === "/api/auth/login") return response(200, { accountId: 4, username: "admin", role: "ADMIN" }, ["JSESSIONID=session-value; Path=/; HttpOnly; Secure"]);
    if (path === "/api/auth/me") return response(200, { accountId: 4, username: "admin", role: "ADMIN" });
    if (path === "/api/auth/logout") return response(204, null);
    throw new Error(`Unexpected test request: ${path}`);
  };
  const api = new LibrisApiClient("https://libris.example", fetchImpl);

  assert.equal(await api.authenticate("admin", "not-a-real-production-secret"), "ADMIN");
  const loginCall = calls.find(call => call.path === "/api/auth/login");
  assert.equal(loginCall.options.headers["X-XSRF-TOKEN"], "csrf-value");
  assert.deepEqual(JSON.parse(loginCall.options.body), { username: "admin", password: "not-a-real-production-secret" });
  const profileCall = calls.find(call => call.path === "/api/auth/me");
  assert.match(profileCall.options.headers.Cookie, /JSESSIONID=session-value/);
  await api.logout();
  assert.equal(api.authenticated, false);
});

test("API client rejects remote plaintext HTTP", async () => {
  assert.throws(() => new LibrisApiClient("http://libris.example", async () => {}), /Use HTTPS/);
});

test("catalog pagination follows every page and preserves all returned rows", async () => {
  const pages = [
    { content: [{ id: 1, title: "First" }], totalPages: 2 },
    { content: [{ id: 2, title: "Second" }], totalPages: 2 },
  ];
  const requestedPages = [];
  const api = new LibrisApiClient("https://libris.example", async url => {
    const page = Number(new URL(url).searchParams.get("page"));
    requestedPages.push(page);
    return response(200, pages[page]);
  });
  assert.deepEqual(await api.listAll("books"), [
    { id: 1, title: "First" },
    { id: 2, title: "Second" },
  ]);
  assert.deepEqual(requestedPages, [0, 1]);
});

test("borrow-history pagination returns only matching item references", async () => {
  const pages = [
    { content: [{ itemId: 9, itemType: "BOOK", borrowerEmail: "not-exposed" }], totalPages: 2 },
    { content: [{ itemId: 4, itemType: "MAGAZINE" }, { itemId: 9, itemType: "NEWSPAPER" }], totalPages: 2 },
  ];
  const api = new LibrisApiClient("https://libris.example", async url => {
    const page = Number(new URL(url).searchParams.get("page"));
    return response(200, pages[page]);
  });
  const found = await api.borrowHistoryFor(new Set(["9", "4"]));
  assert.deepEqual([...found].sort(), ["books:9", "magazines:4", "newspapers:9"]);
});

test("CLI requires explicit confirmation for any remote write target", () => {
  assert.throws(
    () => assertWriteTargetConfirmation("apply", "https://libris.opik.net./", false),
    /Remote writes require/,
  );
  assert.throws(
    () => assertWriteTargetConfirmation("rollback-apply", "https://other.example", false),
    /Remote writes require/,
  );
  assert.doesNotThrow(() => assertWriteTargetConfirmation("apply", "http://127.0.0.1:8080", false));
  assert.doesNotThrow(() => assertWriteTargetConfirmation("preview", "https://libris.opik.net", false));
});

test("CLI rejects an unapproved custom origin before collecting credentials", () => {
  assert.throws(() => assertBaseUrlAllowed("https://libris.opik.net.attacker.example"), /Custom remote origins require/);
  assert.throws(() => assertBaseUrlAllowed("https://libris.opik.net:8443"), /Custom remote origins require/);
  assert.doesNotThrow(() => assertBaseUrlAllowed("https://libris.opik.net"));
  assert.doesNotThrow(() => assertBaseUrlAllowed("http://127.0.0.1:8080"));
  assert.doesNotThrow(() => assertBaseUrlAllowed("https://staging.example", true));
});
