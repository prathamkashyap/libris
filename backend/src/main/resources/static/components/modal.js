const definitions = {
  book: { title: "Add new book", fields: [["title", "Title", "text", true], ["author", "Author", "text", true], ["isbn", "ISBN", "text", false], ["publishedDate", "Published date", "date", false]] },
  magazine: { title: "Add new magazine", fields: [["title", "Title", "text", true], ["publisher", "Publisher", "text", true], ["issueDate", "Issue date", "date", false]] },
  newspaper: { title: "Add new newspaper", fields: [["title", "Title", "text", true], ["publicationDate", "Publication date", "date", false]] },
  student: { title: "Add student", fields: [["name", "Name", "text", true], ["email", "Email", "email", true], ["phone", "Phone", "tel", true], ["username", "Username", "text", true], ["password", "Initial password", "password", true]] },
  librarian: { title: "Add librarian", fields: [["name", "Name", "text", true], ["age", "Age", "number", true], ["phone", "Phone", "tel", true], ["username", "Username", "text", true], ["password", "Initial password", "password", true]] },
  borrow: { title: "Record a borrow", fields: [["bookId", "Book ID", "number", false], ["magazineId", "Magazine ID", "number", false], ["newspaperId", "Newspaper ID", "number", false], ["studentId", "Student ID", "number", true], ["borrowerName", "Borrower name", "text", true], ["borrowerEmail", "Borrower email", "email", true], ["borrowerPhone", "Borrower phone", "tel", true], ["borrowDate", "Borrow date", "date", true]] }
};

export function openModal(kind, onSubmit) {
  const definition = definitions[kind];
  if (!definition) return;
  const root = document.getElementById("modal-root");
  
  const fieldMarkup = definition.fields.map(([name, label, type, required], index) => 
    `<label class="field ${index < 2 ? "" : "span-all"}">
      <span>${label}</span>
      <input id="modal-${name}" name="${name}" type="${type}" ${required ? "required" : ""}>
      <span class="field-error" data-error="${name}"></span>
    </label>`
  ).join("");
  
  root.innerHTML = `
    <div class="modal-backdrop" role="presentation">
      <section class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <div class="modal-header">
          <h2 id="modal-title" class="serif">${definition.title}</h2>
          <button class="modal-close" type="button" aria-label="Close">×</button>
        </div>
        <p class="form-error" data-modal-error hidden></p>
        <form novalidate>
          <div class="form-grid">
            ${fieldMarkup}
          </div>
          <div class="modal-footer">
            <button class="btn-ghost" type="button" data-cancel>Cancel</button>
            <button class="btn-primary" type="submit">Save</button>
          </div>
        </form>
      </section>
    </div>
  `;
  
  const close = () => { root.innerHTML = ""; };
  const firstInput = root.querySelector("input");
  if (firstInput) firstInput.focus();
  
  root.querySelectorAll(".modal-close,[data-cancel]").forEach(button => button.addEventListener("click", close));
  root.querySelector(".modal-backdrop").addEventListener("click", event => { 
    if (event.target === event.currentTarget) close(); 
  });
  
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
    
    if (valid) {
      Promise.resolve(onSubmit(values)).then(close).catch(error => {
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
    }
  });
}
