/**
 * Command palette — open/close, keyboard shortcuts, and live filtering.
 */

export function initPalette() {
  const palette = document.getElementById('cmdPalette');
  const input = document.getElementById('cmdInput');
  const trigger = document.getElementById('searchTrigger');

  if (!palette) return;

  const open = () => {
    palette.classList.add('open');
    setTimeout(() => input && input.focus(), 30);
  };
  const close = () => palette.classList.remove('open');

  // Trigger button
  if (trigger) trigger.addEventListener('click', open);

  // Backdrop click closes
  palette.addEventListener('click', (e) => { if (e.target === palette) close(); });

  // Keyboard shortcuts
  document.addEventListener('keydown', (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      palette.classList.contains('open') ? close() : open();
    }
    if (e.key === 'Escape') close();
  });

  // Live search filtering
  if (input) {
    input.addEventListener('input', () => {
      const query = input.value.toLowerCase().trim();
      const sections = palette.querySelectorAll('.cmd-section');

      sections.forEach(section => {
        const items = section.querySelectorAll('.cmd-item');
        let visibleCount = 0;

        items.forEach(item => {
          const text = item.textContent.toLowerCase();
          const match = !query || text.includes(query);
          item.style.display = match ? '' : 'none';
          if (match) visibleCount++;
        });

        // Hide section label if no items match
        const label = section.querySelector('.cmd-section-label');
        if (label) label.style.display = visibleCount > 0 ? '' : 'none';
      });
    });
  }
}
