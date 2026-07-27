export function confirmDialog(message, confirmLabel = "Delete") {
  return new Promise(resolve => {
    const root = document.getElementById("modal-root");
    root.innerHTML = `
      <div class="modal-backdrop" role="presentation">
        <section class="modal" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
          <div class="modal-header">
            <h2 id="confirm-title" class="serif">Confirm</h2>
            <button class="modal-close confirm-cancel" type="button" aria-label="Close">&times;</button>
          </div>
          <p style="margin:0 0 24px;color:var(--ink-soft);font-size:14px;line-height:1.6">${message}</p>
          <div class="modal-footer">
            <button class="btn-ghost confirm-cancel" type="button">Cancel</button>
            <button class="btn-primary confirm-ok" type="button" style="background:linear-gradient(135deg,#F87171,#EF4444)">${confirmLabel}</button>
          </div>
        </section>
      </div>`;
    const close = () => { root.innerHTML = ""; resolve(false); };
    root.querySelectorAll(".confirm-cancel").forEach(b => b.addEventListener("click", close));
    root.querySelector(".modal-backdrop").addEventListener("click", e => { if (e.target === e.currentTarget) close(); });
    root.querySelector(".confirm-ok").addEventListener("click", () => { root.innerHTML = ""; resolve(true); });
  });
}
