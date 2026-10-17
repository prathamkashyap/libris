const REGISTRATION_FIELDS = Object.freeze({
  name: "reg-name",
  email: "reg-email",
  phone: "reg-phone",
  username: "reg-username",
  password: "reg-password",
});

export function clearRegistrationErrors(form, summary) {
  for (const inputId of Object.values(REGISTRATION_FIELDS)) {
    const input = form?.querySelector(`#${inputId}`);
    const fieldError = form?.querySelector(`#${inputId}-error`);
    input?.removeAttribute("aria-invalid");
    if (fieldError) {
      fieldError.textContent = "";
      fieldError.hidden = true;
    }
  }

  if (summary) {
    summary.textContent = "";
    summary.hidden = true;
  }
}

export function showRegistrationErrors(form, summary, fieldErrors, fallbackMessage) {
  clearRegistrationErrors(form, summary);

  let mappedCount = 0;
  const unmappedMessages = [];
  const errors = Array.isArray(fieldErrors) ? fieldErrors : [];

  for (const error of errors) {
    if (!error || typeof error.field !== "string" || typeof error.message !== "string") continue;
    const message = error.message.trim();
    if (!message) continue;

    const inputId = REGISTRATION_FIELDS[error.field];
    const input = inputId && form?.querySelector(`#${inputId}`);
    const fieldError = inputId && form?.querySelector(`#${inputId}-error`);
    if (input && fieldError) {
      fieldError.textContent = message;
      fieldError.hidden = false;
      input.setAttribute("aria-invalid", "true");
      mappedCount += 1;
    } else {
      unmappedMessages.push(message);
    }
  }

  const message = unmappedMessages.length
    ? unmappedMessages.join(" ")
    : mappedCount
      ? "Please correct the highlighted fields."
      : fallbackMessage;
  if (summary && typeof message === "string" && message.trim()) {
    summary.textContent = message;
    summary.hidden = false;
  }
}
