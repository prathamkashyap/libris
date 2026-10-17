#!/usr/bin/env node
import { createInterface } from "node:readline/promises";
import { stdin, stdout } from "node:process";
import { homedir } from "node:os";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { CatalogConflictError, rollbackImport, runImport } from "./catalog-importer.mjs";
import { LibrisApiClient } from "./libris-api.mjs";
import { ManifestStore } from "./manifest-store.mjs";

const DEFAULT_BASE_URL = "https://libris.opik.net";
const DEFAULT_MANIFEST = resolve(homedir(), ".local/state/libris-catalog-seed/manifest.json");

export async function main(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  const baseUrl = options.baseUrl ?? DEFAULT_BASE_URL;
  assertBaseUrlAllowed(baseUrl, options.allowCustomOrigin);
  assertWriteTargetConfirmation(options.mode, baseUrl, options.confirmProduction);
  const api = new LibrisApiClient(baseUrl);

  const terminal = createInterface({ input: stdin, output: stdout });
  const username = await terminal.question("Libris username: ");
  terminal.close();
  const password = await promptHidden("Libris password (hidden): ");

  const manifest = new ManifestStore(options.manifestPath ?? DEFAULT_MANIFEST);
  let authenticated = false;
  try {
    await manifest.acquireLock();
    const role = await api.authenticate(username.trim(), password);
    authenticated = true;
    if (options.mode === "rollback-preview" || options.mode === "rollback-apply") {
      const result = await rollbackImport({ api, manifest, apply: options.mode === "rollback-apply" });
      reportRollback(result);
      return;
    }
    const result = await runImport({
      api,
      manifest,
      apply: options.mode === "apply",
    });
    reportImport(result, role, options.mode);
  } finally {
    if (authenticated) await api.logout().catch(() => {});
    await manifest.releaseLock().catch(() => {});
  }
}

export function assertBaseUrlAllowed(baseUrl, allowCustomOrigin = false) {
  const parsed = new URL(baseUrl);
  const hostname = parsed.hostname.toLowerCase().replace(/\.$/, "");
  const localTarget = ["localhost", "127.0.0.1", "::1", "[::1]"].includes(hostname);
  const productionTarget = hostname === "libris.opik.net" && !parsed.port;
  if (!localTarget && !productionTarget && !allowCustomOrigin) {
    throw new Error("Custom remote origins require the explicit --allow-custom-origin flag; verify the TLS hostname before entering credentials.");
  }
}

export function assertWriteTargetConfirmation(mode, baseUrl, confirmed) {
  const writes = mode === "apply" || mode === "rollback-apply";
  const hostname = new URL(baseUrl).hostname.toLowerCase().replace(/\.$/, "");
  const localTarget = ["localhost", "127.0.0.1", "::1", "[::1]"].includes(hostname);
  if (writes && !localTarget && !confirmed) {
    throw new Error("Remote writes require the explicit --confirm-production flag after separate approval.");
  }
}

function parseArgs(argv) {
  let mode = "preview";
  let modeChosen = false;
  let baseUrl;
  let manifestPath;
  let confirmProduction = false;
  let allowCustomOrigin = false;
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (["--preview", "--apply", "--rollback-preview", "--rollback-apply"].includes(arg)) {
      if (modeChosen) throw new Error("Choose exactly one operation mode.");
      modeChosen = true;
      mode = arg.slice(2);
    } else if (arg === "--base-url" || arg === "--manifest") {
      const value = argv[++index];
      if (!value) throw new Error(`${arg} requires a value.`);
      if (arg === "--base-url") baseUrl = value;
      else manifestPath = value;
    } else if (arg === "--confirm-production") confirmProduction = true;
    else if (arg === "--allow-custom-origin") allowCustomOrigin = true;
    else throw new Error(`Unknown option: ${arg}`);
  }
  return { mode, baseUrl, manifestPath, confirmProduction, allowCustomOrigin };
}

function promptHidden(prompt) {
  if (!stdin.isTTY || typeof stdin.setRawMode !== "function") {
    return Promise.reject(new Error("A real interactive SSH terminal is required for hidden password input."));
  }
  return new Promise((resolvePrompt, reject) => {
    stdout.write(prompt);
    stdin.setRawMode(true);
    stdin.resume();
    stdin.setEncoding("utf8");
    let value = "";
    const onData = chunk => {
      for (const char of chunk) {
        if (char === "\u0003") {
          cleanup();
          reject(new Error("Password entry cancelled."));
          return;
        }
        if (char === "\r" || char === "\n") {
          cleanup();
          stdout.write("\n");
          resolvePrompt(value);
          return;
        }
        if (char === "\u007f" || char === "\b") value = value.slice(0, -1);
        else if (char >= " ") value += char;
      }
    };
    const cleanup = () => {
      stdin.off("data", onData);
      stdin.setRawMode(false);
      stdin.pause();
    };
    stdin.on("data", onData);
  });
}

function reportImport(result, role, mode) {
  stdout.write(`Authenticated role: ${role}\n`);
  if (mode === "preview") stdout.write("Mode: preview only; no catalog records were written.\n");
  else stdout.write("Mode: applied through authenticated catalog APIs.\n");
  for (const item of result.actions) {
    stdout.write(`${item.action.toUpperCase()} ${item.expected.collection}: ${item.expected.title}\n`);
  }
  if (mode !== "preview") {
    stdout.write(`Created records recorded in external manifest: ${result.created.length}\n`);
    stdout.write(`Dashboard books: ${result.before.totalBooks} -> ${result.after.totalBooks}; available: ${result.before.availableBooks} -> ${result.after.availableBooks}\n`);
  }
}

function reportRollback(result) {
  stdout.write(`Rollback mode: ${result.applied ? "applied" : "preview only"}\n`);
  for (const entry of result.entries) {
    stdout.write(`${result.applied ? "REMOVE" : "WOULD REMOVE"} ${entry.collection}: ${entry.title}\n`);
  }
  stdout.write(`Records with borrowing history are blocked: ${result.blocked.length}\n`);
  if (result.applied) stdout.write(`Removed records: ${result.removed.length}\n`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => {
    const detail = error instanceof CatalogConflictError
      ? error.conflicts.map(conflict => `${conflict.collection}: ${conflict.title} (${conflict.reason}; candidate IDs: ${(conflict.ids ?? []).join(",") || "none"})`).join("; ")
      : error.message;
    console.error(`Catalog seed stopped: ${detail}`);
    process.exitCode = 1;
  });
}
