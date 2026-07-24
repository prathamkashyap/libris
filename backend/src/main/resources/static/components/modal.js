const definitions = {
  book: { title: "Add new book", fields: [["title", "Title", "text", true], ["author", "Author", "text", true], ["isbn", "ISBN", "text", false], ["publishedDate", "Published date", "date", false]] },
  student: { title: "Add student", fields: [["name", "Name", "text", true], ["email", "Email", "email", true], ["phone", "Phone", "tel", true], ["username", "Username", "text", true], ["password", "Initial password", "password", true]] },
  librarian: { title: "Add librarian", fields: [["name", "Name", "text", true], ["age", "Age", "number", true], ["phone", "Phone", "tel", true], ["username", "Username", "text", true], ["password", "Initial password", "password", true]] },
  borrow: { title: "Record a borrow", fields: [["bookId", "Book ID", "number", true], ["studentId", "Student ID", "number", true], ["borrowerName", "Borrower name", "text", true], ["borrowerEmail", "Borrower email", "email", true], ["borrowerPhone", "Borrower phone", "tel", true], ["borrowDate", "Borrow date", "date", true]] }
};

export function openModal(kind, onSubmit) {
  const definition = definitions[kind];
  const root = document.getElementById("modal-root");
  const fieldMarkup = definition.fields.map(([name, label, type, required], index) => `<div class="${index < 2 ? "" : "span-all"}"><label for="modal-${name}">${label}</label><input id="modal-${name}" name="${name}" type="${type}" ${required ? "required" : ""}><p class="field-error" data-error="${name}"></p></div>`).join("");
  root.innerHTML = `<div class="modal-backdrop" role="presentation"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title"><div class="modal-header"><h2 id="modal-title">${definition.title}</h2><button class="modal-close" type="button" aria-label="Close">×</button></div><p class="form-error" data-modal-error hidden></p><form novalidate><div class="form-grid">${fieldMarkup}</div><div class="modal-footer"><button class="button button-secondary" type="button" data-cancel>Cancel</button><button class="button button-primary" type="submit">Save</button></div></form></section></div>`;
  const close = () => { root.innerHTML = ""; };
  const firstInput = root.querySelector("input");
  firstInput.focus();
  root.querySelectorAll(".modal-close,[data-cancel]").forEach(button => button.addEventListener("click", close));
  root.querySelector(".modal-backdrop").addEventListener("click", event => { if (event.target === event.currentTarget) close(); });
  root.querySelector("form").addEventListener("submit", event => {
    event.preventDefault();
    const values = Object.fromEntries(new FormData(event.currentTarget));
    root.querySelector("[data-modal-error]").hidden = true;
    let valid = true;
    definition.fields.filter(([, , , required]) => required).forEach(([name, label]) => {
      const error = root.querySelector(`[data-error="${name}"]`);
      const invalidEmail = name.toLowerCase().includes("email") && values[name] && !/^\S+@\S+\.\S+$/.test(values[name]);
      error.textContent = !values[name].trim() ? `${label} is required.` : invalidEmail ? "Invalid email address." : "";
      valid &&= !error.textContent;
    });
    if (valid) Promise.resolve(onSubmit(values)).then(close).catch(error => {
      const fieldErrors = error.fieldErrors || [];
      fieldErrors.forEach(({ field, message }) => {
        const fieldError = root.querySelector(`[data-error="${field}"]`);
        if (fieldError) fieldError.textContent = message;
      });
      const unmatchedMessages = fieldErrors.filter(({ field }) => !root.querySelector(`[data-error="${field}"]`)).map(({ message }) => message);
      const summary = root.querySelector("[data-modal-error]");
      summary.textContent = unmatchedMessages.join(" ") || (fieldErrors.length ? "Please correct the highlighted fields." : error.message);
      summary.hidden = false;
    });
  });
}
