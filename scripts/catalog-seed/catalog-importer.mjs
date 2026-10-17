import { CATALOG_COLLECTIONS, CATALOG_DATA, SAMPLE_CATALOG_VERSION, naturalKey, normalizeTitle } from "./catalog-data.mjs";

const DASHBOARD_FIELDS = ["totalBooks", "availableBooks", "borrowedBooks", "totalStudents", "totalLibrarians"];

export class CatalogConflictError extends Error {
  constructor(conflicts) {
    super(`Catalog preflight found ${conflicts.length} conflict(s); this run stopped before further writes.`);
    this.name = "CatalogConflictError";
    this.conflicts = conflicts;
  }
}

export class CatalogVerificationError extends Error {
  constructor(message) {
    super(message);
    this.name = "CatalogVerificationError";
  }
}

export async function buildImportPlan(api, data = CATALOG_DATA) {
  const byCollection = new Map();
  const collections = new Set(data.map(record => record.collection));
  for (const collection of collections) {
    if (!CATALOG_COLLECTIONS.includes(collection)) throw new Error(`Unsupported catalog collection: ${collection}`);
    byCollection.set(collection, await api.listAll(collection));
  }

  const actions = [];
  const conflicts = [];
  for (const expected of data) {
    const candidates = byCollection.get(expected.collection).filter(existing =>
      hasSameNaturalKey(existing, expected),
    );
    if (candidates.length === 0) {
      actions.push({ action: "create", expected, key: naturalKey(expected) });
    } else if (candidates.length === 1 && isExactSample(candidates[0], expected)) {
      actions.push({ action: "skip", expected, key: naturalKey(expected), existingId: String(candidates[0].id) });
    } else {
      conflicts.push({
        collection: expected.collection,
        title: expected.title,
        ids: candidates.map(row => String(row.id)),
        reason: candidates.length > 1 ? "multiple natural-key matches" : "existing row differs from the sample record",
      });
    }
  }
  return { actions, conflicts };
}

export async function runImport({ api, manifest, data = CATALOG_DATA, apply = false, now = () => new Date() }) {
  await requireCatalogOperator(api);

  const before = await api.dashboard();
  validateDashboard(before);
  const plan = await buildImportPlan(api, data);
  if (plan.conflicts.length) throw new CatalogConflictError(plan.conflicts);
  if (!apply) return { ...plan, created: [], before, after: null };

  const created = [];
  const skipped = [];
  const outcomes = [];
  for (const item of plan.actions) {
    if (item.action === "skip") {
      if (await manifest.hasPending(item.expected.collection, item.key)) {
        await manifest.notePendingCandidate(item.expected.collection, item.key, item.existingId, now().toISOString());
        throw pendingConflict(item.expected, item.existingId);
      }
      skipped.push(item);
      outcomes.push(item);
      continue;
    }

    // Recheck immediately before each POST. This narrows, but cannot eliminate,
    // races with catalog edits because the schema has no natural-key uniqueness.
    const current = await buildImportPlan(api, [item.expected]);
    if (current.conflicts.length) throw new CatalogConflictError(current.conflicts);
    if (current.actions[0]?.action === "skip") {
      if (await manifest.hasPending(item.expected.collection, item.key)) {
        await manifest.notePendingCandidate(
          item.expected.collection,
          item.key,
          current.actions[0].existingId,
          now().toISOString(),
        );
        throw pendingConflict(item.expected, current.actions[0].existingId);
      }
      skipped.push(current.actions[0]);
      outcomes.push(current.actions[0]);
      continue;
    }

    await manifest.addPending({
      collection: item.expected.collection,
      title: item.expected.title,
      key: item.key,
      sampleVersion: SAMPLE_CATALOG_VERSION,
      startedAt: now().toISOString(),
    });
    const result = await api.create(item.expected.collection, payloadFor(item.expected));
    const id = validId(result?.id);
    if (!id) {
      throw new CatalogVerificationError(
        `Creation response for ${item.expected.collection} did not contain a valid ID; the pending manifest entry was retained for reconciliation.`,
      );
    }
    const entry = {
      collection: item.expected.collection,
      id,
      title: item.expected.title,
      key: item.key,
      sampleVersion: SAMPLE_CATALOG_VERSION,
      createdAt: now().toISOString(),
    };
    await manifest.recordCreated(entry);
    created.push(entry);
    outcomes.push({ ...item, action: "created", createdId: id });
  }

  const after = await api.dashboard();
  validateDashboard(after);
  const expectedBooks = created.filter(row => row.collection === "books").length;
  for (const field of DASHBOARD_FIELDS) {
    const expectedDelta =
      field === "totalBooks" || field === "availableBooks"
        ? expectedBooks
        : 0;
    if (after[field] - before[field] !== expectedDelta) {
      throw new CatalogVerificationError(
        `Dashboard verification for ${field} did not match the import delta. Created record IDs are retained in the external manifest.`,
      );
    }
  }

  const verified = await buildImportPlan(api, data);
  if (verified.conflicts.length || verified.actions.some(item => item.action !== "skip")) {
    throw new CatalogVerificationError(
      "Post-import catalog verification found missing, changed, or ambiguous sample records. Created IDs remain in the external manifest.",
    );
  }

  return { actions: outcomes, conflicts: [], created, skipped, before, after };
}

export async function rollbackImport({ api, manifest, apply = false }) {
  await requireCatalogOperator(api);
  const state = await manifest.read();
  if (state.pending.length) {
    throw new Error("The manifest has unresolved pending records; reconcile the import before rollback.");
  }
  const entries = [...state.records];
  const toDelete = [];
  for (const entry of entries) {
    if (entry.sampleVersion !== SAMPLE_CATALOG_VERSION) {
      throw new CatalogConflictError([
        { collection: entry.collection, title: entry.title, ids: [entry.id], reason: "manifest sample version is unsupported" },
      ]);
    }
    const actual = await api.getOne(entry.collection, entry.id);
    if (actual === null) continue;
    const expected = CATALOG_DATA.find(
      record => record.collection === entry.collection && naturalKey(record) === entry.key,
    );
    if (!expected || !isExactSample(actual, expected)) {
      throw new CatalogConflictError([
        { collection: entry.collection, title: entry.title, ids: [entry.id], reason: "manifest record has changed" },
      ]);
    }
    toDelete.push(entry);
  }

  const itemIds = new Set(toDelete.map(entry => entry.id));
  const inUse = await api.borrowHistoryFor(itemIds);
  const borrowedEntries = toDelete.filter(entry => inUse.has(`${entry.collection}:${entry.id}`));
  const blockedIds = new Set(borrowedEntries.map(entry => `${entry.collection}:${entry.id}`));
  const safeEntries = toDelete.filter(entry => !blockedIds.has(`${entry.collection}:${entry.id}`));

  const removed = [];
  if (apply) {
    for (const entry of safeEntries) {
      await api.delete(entry.collection, entry.id);
      await manifest.removeCreated(entry.collection, entry.id);
      removed.push(entry);
    }
  }
  return { removed, entries: safeEntries, blocked: borrowedEntries, applied: apply };
}

async function requireCatalogOperator(api) {
  const role = await api.currentRole();
  if (typeof role !== "string" || !["ADMIN", "LIBRARIAN"].includes(role)) {
    throw new Error("The authenticated account must have the ADMIN or LIBRARIAN role.");
  }
}

function hasSameNaturalKey(actual, expected) {
  if (expected.collection === "newspapers") {
    return normalizeTitle(stripSampleEdition(actual.title)) === normalizeTitle(stripSampleEdition(expected.title));
  }
  return normalizeTitle(actual.title) === normalizeTitle(expected.title);
}

function isExactSample(actual, expected) {
  const fields = Object.keys(expected).filter(key => key !== "collection");
  return fields.every(key => normalizeValue(actual[key]) === normalizeValue(expected[key]));
}

function normalizeValue(value) {
  if (value === "") return null;
  if (value === null || value === undefined) return null;
  return value;
}

function payloadFor(record) {
  const { collection, available, ...payload } = record;
  return payload;
}

function validId(value) {
  const id = String(value ?? "");
  return /^[1-9]\d*$/.test(id) ? id : null;
}

function validateDashboard(value) {
  if (!value || DASHBOARD_FIELDS.some(field => !Number.isSafeInteger(value[field]) || value[field] < 0)) {
    throw new CatalogVerificationError("Dashboard response is missing expected non-negative integer counts.");
  }
}

function pendingConflict(expected, candidateId) {
  return new CatalogConflictError([{
    collection: expected.collection,
    title: expected.title,
    ids: [String(candidateId)],
    reason: "an earlier create has an uncertain outcome; verify its origin before reconciling the pending manifest",
  }]);
}

function stripSampleEdition(title) {
  return String(title ?? "").replace(/\s+—\s+Sample edition$/i, "");
}
