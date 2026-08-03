/**
 * Custom cursor — Star Atlas style.
 * Single ring element with CSS ::before dot. No separate dot element.
 * Only desktop + motion-safe.
 */

let ringEl = null;
let mouseX = 0;
let mouseY = 0;
let ringX = 0;
let ringY = 0;
let rafId = null;
let active = false;

const LERP = 0.12;
const RING_SIZE = 34;
const RING_ACTIVE_SIZE = 54;

export function initCursor() {
  if (matchMedia('(pointer: coarse)').matches) return;
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;

  ringEl = document.createElement('div');
  ringEl.className = 'cursor-ring';
  ringEl.setAttribute('aria-hidden', 'true');
  document.body.appendChild(ringEl);
  document.body.classList.add('cursor-ready');

  document.addEventListener('mousemove', onMove, { passive: true });
  document.addEventListener('mouseenter', () => { ringEl.style.opacity = '0.7'; });
  document.addEventListener('mouseleave', () => { ringEl.style.opacity = '0'; });

  // Observe DOM for new interactive elements
  const bindInteractive = (el) => {
    el.addEventListener('mouseenter', () => ringEl.classList.add('is-active'));
    el.addEventListener('mouseleave', () => ringEl.classList.remove('is-active'));
  };
  document.querySelectorAll('a, button, [role="button"], .btn, .quick, .theme-opt, input, select, textarea, .nav-list a, .rail-list a').forEach(bindInteractive);

  // MutationObserver for dynamically added elements
  const mo = new MutationObserver((mutations) => {
    mutations.forEach(m => {
      m.addedNodes.forEach(node => {
        if (node.nodeType === 1) {
          if (node.matches?.('a, button, [role="button"], .btn, input, select, textarea')) bindInteractive(node);
          node.querySelectorAll?.('a, button, [role="button"], .btn, input, select, textarea').forEach(bindInteractive);
        }
      });
    });
  });
  mo.observe(document.body, { childList: true, subtree: true });

  active = true;
  tick();
}

function onMove(e) {
  mouseX = e.clientX;
  mouseY = e.clientY;
}

function tick() {
  if (!active) return;
  ringX += (mouseX - ringX) * LERP;
  ringY += (mouseY - ringY) * LERP;
  if (ringEl) {
    const sz = ringEl.classList.contains('is-active') ? RING_ACTIVE_SIZE : RING_SIZE;
    ringEl.style.width = sz + 'px';
    ringEl.style.height = sz + 'px';
    ringEl.style.transform = `translate(${ringX - sz / 2}px, ${ringY - sz / 2}px)`;
  }
  rafId = requestAnimationFrame(tick);
}

export function destroyCursor() {
  active = false;
  if (rafId) cancelAnimationFrame(rafId);
  document.removeEventListener('mousemove', onMove);
  if (ringEl && ringEl.parentNode) ringEl.parentNode.removeChild(ringEl);
  document.body.classList.remove('cursor-ready');
  ringEl = null;
}
