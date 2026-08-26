/** Keyboard-accessible workspace navigation palette. */
export function initPalette() {
  const palette = document.getElementById('cmdPalette');
  const input = document.getElementById('cmdInput');
  const trigger = document.getElementById('searchTrigger');
  if (!palette || !input) return;

  let opener = null;
  let activeIndex = -1;
  const items = () => [...palette.querySelectorAll('.cmd-item:not([hidden])')];
  const setActive = index => {
    const visibleItems = items();
    visibleItems.forEach((item, itemIndex) => item.classList.toggle('active', itemIndex === index));
    activeIndex = index;
  };
  const open = () => {
    opener = document.activeElement;
    palette.classList.add('open');
    palette.setAttribute('aria-hidden', 'false');
    input.value = '';
    input.dispatchEvent(new Event('input'));
    setActive(-1);
    requestAnimationFrame(() => input.focus());
  };
  const close = () => {
    if (!palette.classList.contains('open')) return;
    palette.classList.remove('open');
    palette.setAttribute('aria-hidden', 'true');
    setActive(-1);
    opener?.focus?.();
  };

  trigger?.addEventListener('click', open);
  palette.addEventListener('click', event => { if (event.target === palette) close(); });
  palette.querySelectorAll('a').forEach(link => link.addEventListener('click', close));
  input.addEventListener('input', () => {
    const query = input.value.toLocaleLowerCase().trim();
    palette.querySelectorAll('.cmd-item').forEach(item => {
      item.hidden = Boolean(query && !item.textContent.toLocaleLowerCase().includes(query));
    });
    setActive(-1);
  });
  document.addEventListener('keydown', event => {
    const isShortcut = (event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k';
    if (isShortcut) {
      event.preventDefault();
      palette.classList.contains('open') ? close() : open();
      return;
    }
    if (!palette.classList.contains('open')) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      close();
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const visibleItems = items();
      if (!visibleItems.length) return;
      const direction = event.key === 'ArrowDown' ? 1 : -1;
      setActive((activeIndex + direction + visibleItems.length) % visibleItems.length);
    } else if (event.key === 'Enter' && activeIndex >= 0) {
      event.preventDefault();
      items()[activeIndex]?.click();
    } else if (event.key === 'Tab') {
      event.preventDefault();
      input.focus();
    }
  });
}
