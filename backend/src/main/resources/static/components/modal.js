import { esc } from "/js/utils/esc.js";

const definitions = {
  book: { title: "Add new book", editTitle: "Edit book", fields: [["title", "Title", "text", true], ["author", "Author", "text", false], ["category", "Category", "text", false], ["isbn", "ISBN", "text", false], ["publishedDate", "Published date", "date", false]] },
  magazine: { title: "Add new magazine", editTitle: "Edit magazine", fields: [["title", "Title", "text", true], ["publisher", "Publisher", "text", false], ["issueDate", "Issue date", "date", false], ["category", "Category", "text", false], ["featuredArticle", "Featured article", "textarea", false]] },
  newspaper: { title: "Add new newspaper", editTitle: "Edit newspaper", fields: [["title", "Title", "text", true], ["publisher", "Publisher", "text", false], ["publicationDate", "Publication date", "date", false], ["topHeadlines", "Top headlines", "textarea", false]] },
  student: { title: "Add student", editTitle: "Edit student", fields: [["name", "Name", "text", true], ["email", "Email", "email", true], ["phone", "Phone", "tel", true], ["username", "Username", "text", true], ["password", "Initial password", "password", true]] },
  librarian: { title: "Add librarian", editTitle: "Edit librarian", fields: [["name", "Name", "text", true], ["age", "Age", "number", true], ["phone", "Phone", "tel", true], ["username", "Username", "text", true], ["password", "Initial password", "password", true]] },
  borrow: { title: "Record a borrow", fields: [["bookId", "Book ID", "number", false], ["magazineId", "Magazine ID", "number", false], ["newspaperId", "Newspaper ID", "number", false], ["studentId", "Student ID", "number", true], ["borrowerName", "Borrower name", "text", true], ["borrowerEmail", "Borrower email", "email", true], ["borrowerPhone", "Borrower phone", "tel", true], ["borrowDate", "Borrow date", "date", true]] }
};

/**
 * Opens a modal dialog for creating or editing a resource.
 * @param {string} kind - The type of resource (book, magazine, etc.)
 * @param {function} onSubmit - Called with form values on submit
 * @param {object} [prefill] - Optional data to prefill for edit mode
 */
export function openModal(kind, onSubmit, prefill = null) {
  const definition = definitions[kind];
  if (!definition) return;
  const root = document.getElementById("modal-root");
  const isEdit = !!prefill;
  const modalTitle = isEdit ? (definition.editTitle || definition.title) : definition.title;

  // In edit mode, password fields become optional
  const fields = definition.fields.map(([name, label, type, required]) => {
    if (isEdit && type === 'password') return [name, label, type, false];
    return [name, label, type, required];
  });

  const opener = document.activeElement;
  const fieldMarkup = fields.map(([name, label, type, required], index) => {
    const value = prefill?.[name] ?? '';
    const placeholder = isEdit && type === 'password' ? 'Leave blank to keep current' : '';
    const control = type === "textarea"
      ? `<textarea id="modal-${name}" name="${name}" ${required ? "required" : ""}>${esc(String(value))}</textarea>`
      : `<input id="modal-${name}" name="${name}" type="${type}" ${required ? "required" : ""} value="${esc(String(value))}" ${placeholder ? `placeholder="${placeholder}"` : ''}>`;
    return `<label class="field ${index < 2 ? "" : "span-all"}">
      <span>${label}</span>
      ${control}
      <span class="field-error" data-error="${name}"></span>
    </label>`;
  }).join("");

  root.innerHTML = `
    <div class="modal-backdrop" role="presentation">
      <section class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <div class="modal-header">
          <h2 id="modal-title" class="serif">${modalTitle}</h2>
          <button class="modal-close" type="button" aria-label="Close">×</button>
        </div>
        <p class="form-error" data-modal-error role="alert" hidden></p>
        <form novalidate>
          <div class="form-grid">
            ${fieldMarkup}
          </div>
          <div class="modal-footer">
            <button class="btn-ghost" type="button" data-cancel>Cancel</button>
            <button class="btn-primary" type="submit">${isEdit ? 'Update' : 'Save'}</button>
          </div>
        </form>
      </section>
    </div>
  `;

  let escHandler;
  const close = () => {
    root.innerHTML = "";
    document.removeEventListener("keydown", escHandler);
    opener?.focus?.();
  };
  const firstInput = root.querySelector("input");
  if (firstInput) firstInput.focus();

  root.querySelectorAll(".modal-close,[data-cancel]").forEach(button => button.addEventListener("click", close));
  root.querySelector(".modal-backdrop").addEventListener("click", event => {
    if (event.target === event.currentTarget) close();
  });

  // Escape key closes
  escHandler = (e) => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', escHandler);

  root.querySelector("form").addEventListener("submit", event => {
    event.preventDefault();
    const values = Object.fromEntries(new FormData(event.currentTarget));

    // In edit mode, strip empty password fields
    if (isEdit) {
      Object.keys(values).forEach(k => {
        const field = fields.find(([name]) => name === k);
        if (field && field[2] === 'password' && !values[k]) delete values[k];
      });
    }

    root.querySelector("[data-modal-error]").hidden = true;
    let valid = true;

    fields.forEach(([name]) => {
      const error = root.querySelector(`[data-error="${name}"]`);
      if (error) error.textContent = "";
      root.querySelector(`[name="${name}"]`)?.removeAttribute("aria-invalid");
    });
    fields.filter(([, , , required]) => required).forEach(([name, label]) => {
      const error = root.querySelector(`[data-error="${name}"]`);
      const val = values[name] || '';
      const invalidEmail = name.toLowerCase().includes("email") && val && !/^\S+@\S+\.\S+$/.test(val);
      error.textContent = !val.trim() ? `${label} is required.` : invalidEmail ? "Invalid email address." : "";
      if (error.textContent) root.querySelector(`[name="${name}"]`)?.setAttribute("aria-invalid", "true");
      valid &&= !error.textContent;
    });

    if (valid) {
      Promise.resolve(onSubmit(values)).then(close).catch(error => {
        const fieldErrors = error.fieldErrors || [];
        fieldErrors.forEach(({ field, message }) => {
          const fieldError = root.querySelector(`[data-error="${field}"]`);
          if (fieldError) {
            fieldError.textContent = message;
            root.querySelector(`[name="${field}"]`)?.setAttribute("aria-invalid", "true");
          }
        });
        const unmatchedMessages = fieldErrors.filter(({ field }) => !root.querySelector(`[data-error="${field}"]`)).map(({ message }) => message);
        const summary = root.querySelector("[data-modal-error]");
        summary.textContent = unmatchedMessages.join(" ") || (fieldErrors.length ? "Please correct the highlighted fields." : error.message);
        summary.hidden = false;
      });
    }
  });
}
