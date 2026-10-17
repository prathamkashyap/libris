import assert from "node:assert/strict";
import test from "node:test";
import { clearRegistrationErrors, showRegistrationErrors } from "../../main/resources/static/js/utils/registration-errors.mjs";

function createForm() {
  const elements = new Map();
  for (const id of ["reg-name", "reg-email", "reg-phone", "reg-username", "reg-password"]) {
    const attributes = new Set();
    elements.set(`#${id}`, {
      attributes,
      setAttribute(name) { attributes.add(name); },
      removeAttribute(name) { attributes.delete(name); },
    });
    elements.set(`#${id}-error`, { textContent: "", hidden: true });
  }
  return {
    elements,
    querySelector(selector) { return elements.get(selector) ?? null; },
  };
}

function createSummary() {
  return { textContent: "", hidden: true };
}

test("maps field errors to safe text and marks matching inputs invalid", () => {
  const form = createForm();
  const summary = createSummary();

  showRegistrationErrors(form, summary, [
    { field: "password", message: "Password needs a special character." },
    { field: "email", message: "Invalid email address." },
  ], "Registration failed.");

  assert.equal(form.elements.get("#reg-password-error").textContent, "Password needs a special character.");
  assert.equal(form.elements.get("#reg-password-error").hidden, false);
  assert.equal(form.elements.get("#reg-password").attributes.has("aria-invalid"), true);
  assert.equal(form.elements.get("#reg-email-error").textContent, "Invalid email address.");
  assert.equal(summary.textContent, "Please correct the highlighted fields.");
  assert.equal(summary.hidden, false);
});

test("shows unmapped server errors in the summary and ignores malformed entries", () => {
  const form = createForm();
  const summary = createSummary();

  showRegistrationErrors(form, summary, [
    null,
    { field: "confirmPassword", message: "Confirmation does not match." },
    { field: 3, message: "Ignored." },
  ], "Registration failed.");

  assert.equal(summary.textContent, "Confirmation does not match.");
  assert.equal(summary.hidden, false);
  assert.equal(form.elements.get("#reg-name-error").textContent, "");
});

test("falls back safely for missing or malformed field errors and clears stale state", () => {
  const form = createForm();
  const summary = createSummary();
  form.elements.get("#reg-name").attributes.add("aria-invalid");
  form.elements.get("#reg-name-error").textContent = "Old error";
  form.elements.get("#reg-name-error").hidden = false;

  showRegistrationErrors(form, summary, { field: "name", message: "Unexpected shape" }, "Request failed.");
  assert.equal(summary.textContent, "Request failed.");
  assert.equal(form.elements.get("#reg-name-error").textContent, "");
  assert.equal(form.elements.get("#reg-name").attributes.has("aria-invalid"), false);

  clearRegistrationErrors(form, summary);
  assert.equal(summary.hidden, true);
});
