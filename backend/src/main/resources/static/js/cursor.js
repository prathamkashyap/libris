/**
 * Custom cursor system — dot + ring, Star Atlas style.
 * Only activates on non-touch devices.
 * The ring expands and changes color on interactive elements.
 */

let dotEl = null;
let ringEl = null;
let mouseX = 0;
let mouseY = 0;
let ringX = 0;
let ringY = 0;
let rafId = null;
let active = false;

const RING_LERP = 0.12;
const EXPAND_SIZE = 54;
const NORMAL_SIZE = 34;
const DOT_SIZE = 6;

export function initCursor() {
  if (matchMedia('(pointer: coarse)').matches) return;
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;

  dotEl = document.createElement('div');
  dotEl.className = 'cursor-dot';
  dotEl.setAttribute('aria-hidden', 'true');

  ringEl = document.createElement('div');
  ringEl.className = 'cursor-ring';
  ringEl.setAttribute('aria-hidden', 'true');

  document.body.appendChild(dotEl);
  document.body.appendChild(ringEl);
  document.body.classList.add('cursor-ready');

  document.addEventListener('mousemove', onMouseMove, { passive: true });
  document.addEventListener('mouseenter', onEnter);
  document.addEventListener('mouseleave', onLeave);

  document.querySelectorAll('a, button, [role="button"], .btn, .quick, .theme-opt, input, select, textarea, .nav-list a, .rail-list a').forEach(function (el) {
    el.addEventListener('mouseenter', onInteractiveEnter);
    el.addEventListener('mouseleave', onInteractiveLeave);
  });

  active = true;
  tick();
}

function onMouseMove(e) {
  mouseX = e.clientX;
  mouseY = e.clientY;
  if (dotEl) {
    dotEl.style.transform = 'translate(' + (mouseX - DOT_SIZE / 2) + 'px, ' + (mouseY - DOT_SIZE / 2) + 'px)';
  }
}

function onEnter() {
  if (dotEl) dotEl.style.opacity = '1';
  if (ringEl) ringEl.style.opacity = '0.7';
}

function onLeave() {
  if (dotEl) dotEl.style.opacity = '0';
  if (ringEl) ringEl.style.opacity = '0';
}

function onInteractiveEnter() {
  if (ringEl) ringEl.classList.add('is-active');
}

function onInteractiveLeave() {
  if (ringEl) ringEl.classList.remove('is-active');
}

function tick() {
  if (!active) return;
  ringX += (mouseX - ringX) * RING_LERP;
  ringY += (mouseY - ringY) * RING_LERP;
  if (ringEl) {
    var size = ringEl.classList.contains('is-active') ? EXPAND_SIZE : NORMAL_SIZE;
    ringEl.style.width = size + 'px';
    ringEl.style.height = size + 'px';
    ringEl.style.transform = 'translate(' + (ringX - size / 2) + 'px, ' + (ringY - size / 2) + 'px)';
  }
  rafId = requestAnimationFrame(tick);
}

export function destroyCursor() {
  active = false;
  if (rafId) cancelAnimationFrame(rafId);
  document.removeEventListener('mousemove', onMouseMove);
  document.removeEventListener('mouseenter', onEnter);
  document.removeEventListener('mouseleave', onLeave);
  if (dotEl && dotEl.parentNode) dotEl.parentNode.removeChild(dotEl);
  if (ringEl && ringEl.parentNode) ringEl.parentNode.removeChild(ringEl);
  document.body.classList.remove('cursor-ready');
  dotEl = null;
  ringEl = null;
}
