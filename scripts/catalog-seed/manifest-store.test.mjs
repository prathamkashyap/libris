import assert from "node:assert/strict";
import { chmod, mkdtemp, readFile, rm, stat } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { ManifestStore } from "./manifest-store.mjs";

test("manifest is stored outside the repository with mode 600 and serialized access", async () => {
  const directory = await mkdtemp(join(tmpdir(), "libris-catalog-seed-"));
  const path = join(directory, "manifest.json");
  const first = new ManifestStore(path);
  const second = new ManifestStore(path);
  try {
    await first.acquireLock();
    await assert.rejects(second.acquireLock(), /Another catalog import or rollback/);
    await first.recordCreated({ collection: "books", id: "1", title: "Sample", key: "books:sample", createdAt: "2026-10-17T00:00:00.000Z" });
    await first.releaseLock();
    assert.equal((await stat(path)).mode & 0o777, 0o600);
    const contents = JSON.parse(await readFile(path, "utf8"));
    assert.deepEqual(contents.records.map(row => row.id), ["1"]);
  } finally {
    await first.releaseLock();
    await second.releaseLock();
    await rm(directory, { recursive: true, force: true });
  }
});

test("manifest store refuses paths inside the repository", async () => {
  const store = new ManifestStore(join(process.cwd(), "scripts/catalog-seed/unsafe.json"));
  await assert.rejects(store.acquireLock(), /outside the repository/);
});

test("manifest store requires an absolute path", () => {
  assert.throws(() => new ManifestStore("relative/manifest.json"), /Manifest path must be absolute/);
});

test("manifest store rejects a broadly accessible parent directory", async () => {
  const directory = await mkdtemp(join(tmpdir(), "libris-catalog-seed-public-"));
  try {
    await chmod(directory, 0o755);
    const store = new ManifestStore(join(directory, "manifest.json"));
    await assert.rejects(store.acquireLock(), /Manifest directory must be private/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
