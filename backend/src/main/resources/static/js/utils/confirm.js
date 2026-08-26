export function confirmDialog(message, confirmLabel = "Delete") {
  return new Promise(resolve => {
    const root = document.getElementById("modal-root");
    const opener = document.activeElement;
    root.innerHTML = `
      <div class="modal-backdrop" role="presentation">
        <section class="modal" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
          <div class="modal-header">
            <h2 id="confirm-title" class="serif">Confirm</h2>
            <button class="modal-close confirm-cancel" type="button" aria-label="Close">&times;</button>
          </div>
          <p class="modal-body">${message}</p>
          <div class="modal-footer">
            <button class="btn-ghost confirm-cancel" type="button">Cancel</button>
            <button class="btn-danger confirm-ok" type="button">${confirmLabel}</button>
          </div>
        </section>
      </div>`;
    const close = () => { root.innerHTML = ""; opener?.focus?.(); resolve(false); };
    root.querySelectorAll(".confirm-cancel").forEach(b => b.addEventListener("click", close));
    root.querySelector(".modal-backdrop").addEventListener("click", e => { if (e.target === e.currentTarget) close(); });
    root.querySelector(".confirm-ok").addEventListener("click", () => { root.innerHTML = ""; resolve(true); });
  });
}
