import assert from "node:assert/strict";
import test from "node:test";
import { CATALOG_COLLECTIONS, CATALOG_DATA } from "./catalog-data.mjs";
import { buildImportPlan, CatalogConflictError, rollbackImport, runImport } from "./catalog-importer.mjs";

class MemoryManifest {
  constructor() {
    this.state = { records: [], pending: [] };
    this.readCalls = 0;
  }
  async read() {
    this.readCalls += 1;
    return structuredClone(this.state);
  }
  async hasPending(collection, key) { return this.state.pending.some(row => row.collection === collection && row.key === key); }
  async notePendingCandidate(collection, key, candidateId, observedAt) {
    const pending = this.state.pending.find(row => row.collection === collection && row.key === key);
    pending.candidateId = String(candidateId);
    pending.candidateObservedAt = observedAt;
  }
  async addPending(row) { this.state.pending.push(row); }
  async recordCreated(row) {
    this.state.pending = this.state.pending.filter(item => item.key !== row.key || item.collection !== row.collection);
    if (!this.state.records.some(item => item.collection === row.collection && item.id === row.id)) this.state.records.push(row);
  }
  async removeCreated(collection, id) { this.state.records = this.state.records.filter(item => item.collection !== collection || item.id !== String(id)); }
}

class FakeApi {
  constructor() {
    this.rows = Object.fromEntries(CATALOG_COLLECTIONS.map(collection => [collection, []]));
    this.accounts = [{ id: "student-1", role: "STUDENT" }, { id: "admin-1", role: "ADMIN" }];
    this.circulation = [{ id: "borrow-1", itemId: "existing-book", itemType: "BOOK" }];
    this.nextIds = Object.fromEntries(CATALOG_COLLECTIONS.map(collection => [collection, 1]));
    this.role = "ADMIN";
    this.getOneCalls = 0;
    this.borrowHistoryCalls = 0;
    this.deleteCalls = 0;
  }
  async currentRole() { return this.role; }
  async dashboard() {
    const books = this.rows.books;
    return {
      totalStudents: 1,
      totalLibrarians: 0,
      totalBooks: books.length,
      borrowedBooks: books.filter(row => !row.available).length,
      availableBooks: books.filter(row => row.available).length,
    };
  }
  async listAll(collection) { return structuredClone(this.rows[collection]); }
  async create(collection, payload) {
    const row = { id: String(this.nextIds[collection]++), ...structuredClone(payload), available: true };
    this.rows[collection].push(row);
    return structuredClone(row);
  }
  async getOne(collection, id) {
    this.getOneCalls += 1;
    return structuredClone(this.rows[collection].find(row => row.id === String(id)) ?? null);
  }
  async borrowHistoryFor(ids) {
    this.borrowHistoryCalls += 1;
    return new Set(this.circulation
      .filter(row => ids.has(String(row.itemId)))
      .map(row => `${({ BOOK: "books", MAGAZINE: "magazines", NEWSPAPER: "newspapers" })[row.itemType]}:${row.itemId}`));
  }
  async delete(collection, id) {
    this.deleteCalls += 1;
    this.rows[collection] = this.rows[collection].filter(row => row.id !== String(id));
  }
}

test("imports all sample titles through the API and preserves accounts and circulation", async () => {
  const api = new FakeApi();
  const manifest = new MemoryManifest();
  const beforeAccounts = structuredClone(api.accounts);
  const beforeCirculation = structuredClone(api.circulation);

  const result = await runImport({ api, manifest, apply: true, now: () => new Date("2026-10-17T00:00:00Z") });

  assert.equal(result.created.length, 20);
  assert.equal(result.created.filter(row => row.collection === "books").length, 14);
  assert.equal(result.created.filter(row => row.collection === "magazines").length, 3);
  assert.equal(result.created.filter(row => row.collection === "newspapers").length, 3);
  assert.equal(api.rows.books.filter(row => row.available).length, 14);
  assert.equal(result.after.totalBooks - result.before.totalBooks, 14);
  assert.equal(result.after.availableBooks - result.before.availableBooks, 14);
  assert.deepEqual(api.accounts, beforeAccounts);
  assert.deepEqual(api.circulation, beforeCirculation);
  assert.equal((await manifest.read()).records.length, 20);
  const visible = await buildImportPlan(api);
  assert.equal(visible.conflicts.length, 0);
  assert.ok(visible.actions.every(item => item.action === "skip"));
});

test("rerunning the import skips exact samples without duplicates", async () => {
  const api = new FakeApi();
  const manifest = new MemoryManifest();
  await runImport({ api, manifest, apply: true });
  const rowCounts = Object.fromEntries(CATALOG_COLLECTIONS.map(collection => [collection, api.rows[collection].length]));

  const rerun = await runImport({ api, manifest, apply: true });

  assert.equal(rerun.created.length, 0);
  assert.equal(rerun.skipped.length, 20);
  assert.deepEqual(Object.fromEntries(CATALOG_COLLECTIONS.map(collection => [collection, api.rows[collection].length])), rowCounts);
});

test("rerun records but does not claim an uncertain create candidate", async () => {
  const api = new FakeApi();
  const manifest = new MemoryManifest();
  const create = api.create.bind(api);
  let loseFirstResponse = true;
  api.create = async (collection, payload) => {
    const row = await create(collection, payload);
    if (loseFirstResponse) {
      loseFirstResponse = false;
      throw new Error("simulated connection loss after server commit");
    }
    return row;
  };

  await assert.rejects(runImport({ api, manifest, apply: true }), /simulated connection loss/);
  assert.equal((await manifest.read()).pending.length, 1);
  api.create = create;
  await assert.rejects(runImport({ api, manifest, apply: true }), error => {
    assert.ok(error instanceof CatalogConflictError);
    assert.match(error.conflicts[0].reason, /verify its origin/);
    return true;
  });

  const state = await manifest.read();
  assert.equal(state.pending.length, 1);
  assert.equal(state.pending[0].candidateId, "1");
  assert.equal(state.records.length, 0);
  assert.equal(Object.values(api.rows).flat().length, 1);
  await assert.rejects(rollbackImport({ api, manifest, apply: true }), /unresolved pending records/);
});

test("natural-key conflict stops the entire plan before writes", async () => {
  const api = new FakeApi();
  const manifest = new MemoryManifest();
  api.rows.books.push({ id: "900", title: "1984", author: "Different author", category: "Existing", available: true });

  await assert.rejects(runImport({ api, manifest, apply: true }), CatalogConflictError);
  assert.equal(Object.values(api.rows).flat().length, 1);
  assert.equal((await manifest.read()).records.length, 0);
});

test("multiple rows sharing a natural key are treated as ambiguous", async () => {
  const api = new FakeApi();
  api.rows.books.push(
    { id: "901", title: "1984", author: "George Orwell", category: "Sample — Fiction", available: true },
    { id: "902", title: "1984", author: "George Orwell", category: "Sample — Fiction", available: true },
  );
  const plan = await buildImportPlan(api, [CATALOG_DATA.find(row => row.title === "1984")]);
  assert.equal(plan.conflicts.length, 1);
  assert.match(plan.conflicts[0].reason, /multiple natural-key/);
});

test("import refuses an account without catalog write authority", async () => {
  const api = new FakeApi();
  api.role = "STUDENT";
  await assert.rejects(runImport({ api, manifest: new MemoryManifest(), apply: true }), /ADMIN or LIBRARIAN/);
  assert.equal(Object.values(api.rows).flat().length, 0);
});

test("rollback authorization accepts ADMIN and LIBRARIAN before planning removals", async () => {
  for (const role of ["ADMIN", "LIBRARIAN"]) {
    const api = new FakeApi();
    const manifest = new MemoryManifest();
    await runImport({ api, manifest, apply: true });
    api.role = role;

    const result = await rollbackImport({ api, manifest });

    assert.equal(result.applied, false);
    assert.equal(result.entries.length, 20);
    assert.equal(result.blocked.length, 0);
    assert.equal(api.deleteCalls, 0);
  }
});

test("rollback rejects non-operator and unknown roles before reading or deleting", async () => {
  for (const role of ["STUDENT", "UNKNOWN", null]) {
    const api = new FakeApi();
    const manifest = new MemoryManifest();
    await runImport({ api, manifest, apply: true });
    const before = await manifest.read();
    manifest.readCalls = 0;
    api.role = role;

    await assert.rejects(rollbackImport({ api, manifest, apply: true }), /ADMIN or LIBRARIAN/);

    assert.equal(manifest.readCalls, 0);
    assert.equal(api.getOneCalls, 0);
    assert.equal(api.borrowHistoryCalls, 0);
    assert.equal(api.deleteCalls, 0);
    assert.deepEqual(await manifest.read(), before);
  }
});

test("rollback rejects unauthenticated identity before reading or mutating", async () => {
  const api = new FakeApi();
  const manifest = new MemoryManifest();
  await runImport({ api, manifest, apply: true });
  const before = await manifest.read();
  manifest.readCalls = 0;
  api.currentRole = async () => {
    throw new Error("Authenticate before calling catalog APIs.");
  };

  await assert.rejects(rollbackImport({ api, manifest, apply: true }), /Authenticate before/);

  assert.equal(manifest.readCalls, 0);
  assert.equal(api.getOneCalls, 0);
  assert.equal(api.borrowHistoryCalls, 0);
  assert.equal(api.deleteCalls, 0);
  assert.deepEqual(await manifest.read(), before);
});

test("rollback preview identifies borrowing history and removes only safe manifest records", async () => {
  const api = new FakeApi();
  const manifest = new MemoryManifest();
  const run = await runImport({ api, manifest, apply: true });
  api.circulation.push({ id: "borrow-sample", itemId: run.created[0].id, itemType: "BOOK" });

  const preview = await rollbackImport({ api, manifest });

  assert.equal(preview.applied, false);
  assert.equal(preview.entries.length, 19);
  assert.equal(preview.blocked.length, 1);
  assert.equal(Object.values(api.rows).flat().length, 20);
  assert.equal((await manifest.read()).records.length, 20);
});

test("rollback apply deletes only safe imported records and retains borrowed records", async () => {
  const api = new FakeApi();
  const manifest = new MemoryManifest();
  const run = await runImport({ api, manifest, apply: true });
  const borrowed = run.created[0];
  api.circulation.push({ id: "borrow-sample", itemId: borrowed.id, itemType: "BOOK" });

  const rollback = await rollbackImport({ api, manifest, apply: true });

  assert.equal(rollback.removed.length, 19);
  assert.equal(rollback.blocked.length, 1);
  assert.equal(Object.values(api.rows).flat().length, 1);
  const remaining = (await manifest.read()).records;
  assert.equal(remaining.length, 1);
  assert.equal(remaining[0].collection, borrowed.collection);
  assert.equal(remaining[0].id, borrowed.id);
  assert.equal(api.accounts.length, 2);
  assert.equal(api.circulation.length, 2);
});

test("dry-run reports planned rows without writing", async () => {
  const api = new FakeApi();
  const result = await runImport({ api, manifest: new MemoryManifest() });
  assert.equal(result.actions.length, 20);
  assert.equal(result.created.length, 0);
  assert.equal(Object.values(api.rows).flat().length, 0);
});
