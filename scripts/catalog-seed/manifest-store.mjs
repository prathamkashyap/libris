import { chmod, lstat, mkdir, open, readFile, realpath, rename, stat, unlink, writeFile } from "node:fs/promises";
import { dirname, isAbsolute, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const REPOSITORY_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const EMPTY_MANIFEST = () => ({ format: "libris-catalog-seed", version: 1, records: [], pending: [] });

export class ManifestStore {
  constructor(filePath) {
    if (!isAbsolute(filePath)) throw new Error("Manifest path must be absolute.");
    this.filePath = resolve(filePath);
    this.lockPath = `${this.filePath}.lock`;
    this.lockHandle = null;
    this.state = null;
  }

  async acquireLock() {
    if (isInsideRepository(this.filePath)) {
      throw new Error("Manifest must be stored outside the repository.");
    }
    await mkdir(dirname(this.filePath), { recursive: true, mode: 0o700 });
    const resolvedParent = await realpath(dirname(this.filePath));
    const parentInfo = await stat(resolvedParent);
    if (typeof process.getuid === "function" && parentInfo.uid !== process.getuid()) {
      throw new Error("Manifest directory must be owned by the current user.");
    }
    if ((parentInfo.mode & 0o077) !== 0) {
      throw new Error("Manifest directory must be private to its owner (mode 700 or stricter).");
    }
    const resolvedTarget = resolve(resolvedParent, this.filePath.split(sep).at(-1));
    if (isInsideRepository(resolvedTarget)) {
      throw new Error("Manifest path resolves inside the repository.");
    }
    try {
      const existing = await lstat(this.filePath);
      if (existing.isSymbolicLink() || !existing.isFile()) {
        throw new Error("Manifest path must be a regular file, not a symbolic link.");
      }
      if ((existing.mode & 0o077) !== 0) {
        throw new Error("Existing manifest must be private to its owner (mode 600 or stricter).");
      }
      if (typeof process.getuid === "function" && existing.uid !== process.getuid()) {
        throw new Error("Existing manifest must be owned by the current user.");
      }
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
    try {
      this.lockHandle = await open(this.lockPath, "wx", 0o600);
      await this.lockHandle.writeFile(`${process.pid}\n`, "utf8");
      await this.lockHandle.sync();
    } catch (error) {
      if (this.lockHandle) {
        await this.lockHandle.close().catch(() => {});
        this.lockHandle = null;
        await unlink(this.lockPath).catch(() => {});
      }
      if (error.code === "EEXIST") throw new Error("Another catalog import or rollback is using this manifest.");
      throw error;
    }
    try {
      await this.read();
    } catch (error) {
      await this.releaseLock().catch(() => {});
      throw error;
    }
    return this;
  }

  async releaseLock() {
    if (!this.lockHandle) return;
    await this.lockHandle.close();
    this.lockHandle = null;
    await unlink(this.lockPath).catch(error => {
      if (error.code !== "ENOENT") throw error;
    });
  }

  async read() {
    if (this.state) return structuredClone(this.state);
    try {
      const content = await readFile(this.filePath, "utf8");
      const parsed = JSON.parse(content);
      if (parsed.format !== "libris-catalog-seed" || parsed.version !== 1 || !Array.isArray(parsed.records) || !Array.isArray(parsed.pending)) {
        throw new Error("Manifest format is not recognized; refusing to overwrite it.");
      }
      this.state = parsed;
    } catch (error) {
      if (error.code === "ENOENT") this.state = EMPTY_MANIFEST();
      else if (error instanceof SyntaxError) throw new Error("Manifest JSON is invalid; refusing to overwrite it.");
      else throw error;
    }
    return structuredClone(this.state);
  }

  async hasPending(collection, key) {
    const state = await this.read();
    return state.pending.some(entry => entry.collection === collection && entry.key === key);
  }

  async notePendingCandidate(collection, key, candidateId, observedAt) {
    const state = await this.read();
    const pending = state.pending.find(item => item.collection === collection && item.key === key);
    if (!pending) throw new Error("Cannot record a reconciliation candidate without a pending create.");
    pending.candidateId = String(candidateId);
    pending.candidateObservedAt = observedAt;
    await this.write(state);
  }

  async addPending(entry) {
    const state = await this.read();
    if (!state.pending.some(item => item.collection === entry.collection && item.key === entry.key)) {
      state.pending.push(entry);
      await this.write(state);
    }
  }

  async recordCreated(entry) {
    const state = await this.read();
    state.pending = state.pending.filter(item => !(item.collection === entry.collection && item.key === entry.key));
    const existing = state.records.find(item => item.id === entry.id && item.collection === entry.collection);
    if (!existing) state.records.push(entry);
    await this.write(state);
  }

  async removeCreated(collection, id) {
    const state = await this.read();
    state.records = state.records.filter(item => item.collection !== collection || item.id !== String(id));
    await this.write(state);
  }

  async write(state) {
    if (!this.lockHandle) throw new Error("Manifest lock is required before writing.");
    const temporaryPath = `${this.filePath}.tmp-${process.pid}`;
    const data = `${JSON.stringify(state, null, 2)}\n`;
    await writeFile(temporaryPath, data, { encoding: "utf8", mode: 0o600, flag: "wx" }).catch(async error => {
      if (error.code !== "EEXIST") throw error;
      await unlink(temporaryPath);
      await writeFile(temporaryPath, data, { encoding: "utf8", mode: 0o600, flag: "wx" });
    });
    await chmod(temporaryPath, 0o600);
    await rename(temporaryPath, this.filePath);
    await chmod(this.filePath, 0o600);
    this.state = structuredClone(state);
  }
}

function isInsideRepository(candidate) {
  const pathFromRepo = relative(REPOSITORY_ROOT, candidate);
  return pathFromRepo === "" || (!pathFromRepo.startsWith(`..${sep}`) && pathFromRepo !== "..");
}
