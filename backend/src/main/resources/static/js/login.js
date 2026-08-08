/**
 * Login page initializer.
 *
 * Reuses the shared theme (theme.js) and particle (particles.js) modules.
 * Login-specific interactions (card tilt, magnetic button, click burst)
 * live here because they are unique to the login UI.
 */
import { initTheme, getTheme, renderThemeSwitcher } from '/js/theme.js';
import { initParticles, switchParticles } from '/js/particles.js';

const root = document.documentElement;
const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
const isTouch = matchMedia('(pointer: coarse)').matches;

/* ---- Shared systems ---- */
const currentTheme = initTheme();

const themeMount = document.getElementById('topbar-theme');
renderThemeSwitcher(themeMount);

initParticles(currentTheme);

window.addEventListener('themechange', (e) => {
  switchParticles(e.detail.theme);
});

/* ---- Click burst ---- */
function burst(x, y) {
  if (reduced) return;
  const theme = getTheme();
  const colors = { ember: '#e89030', verdigris: '#2a6b62' };
  const col = colors[theme] || '#e89030';
  for (let i = 0; i < 9; i++) {
    const bit = document.createElement('span');
    bit.className = 'burst-bit spark';
    bit.style.left = x + 'px';
    bit.style.top = y + 'px';
    bit.style.background = 'radial-gradient(circle,' + col + ',transparent)';
    const ang = Math.random() * Math.PI * 2;
    const dist = 26 + Math.random() * 46;
    bit.style.setProperty('--dx', Math.cos(ang) * dist + 'px');
    bit.style.setProperty('--dy', Math.sin(ang) * dist + 'px');
    document.body.appendChild(bit);
    setTimeout(() => { bit.remove(); }, 720);
  }
}

/* ---- Card 3D tilt ---- */
const card = document.getElementById('loginCard');
if (card && !reduced && !isTouch) {
  card.addEventListener('pointermove', (e) => {
    const r = card.getBoundingClientRect();
    const px = (e.clientX - r.left) / r.width - 0.5;
    const py = (e.clientY - r.top) / r.height - 0.5;
    card.style.setProperty('--rx', (-py * 5) + 'deg');
    card.style.setProperty('--ry', (px * 7) + 'deg');
    card.style.setProperty('--mx', (px * 100 + 50) + '%');
    card.style.setProperty('--my', (py * 100 + 50) + '%');
  });
  card.addEventListener('pointerleave', () => {
    card.style.setProperty('--rx', '0deg');
    card.style.setProperty('--ry', '0deg');
  });
}

/* ---- Magnetic submit button ---- */
const submitBtn = document.getElementById('submitBtn');
if (submitBtn && !reduced && !isTouch) {
  submitBtn.addEventListener('pointermove', (e) => {
    const r = submitBtn.getBoundingClientRect();
    const x = (e.clientX - r.left - r.width / 2) * 0.22;
    const y = (e.clientY - r.top - r.height / 2) * 0.35;
    submitBtn.style.transform = 'translate(' + x + 'px,' + y + 'px)';
  });
  submitBtn.addEventListener('pointerleave', () => { submitBtn.style.transform = ''; });
}

/* ---- Wire burst to clickable elements ---- */
const burstTargets = [
  submitBtn,
  document.getElementById('staffBtn'),
  ...(themeMount ? themeMount.querySelectorAll('.theme-opt') : [])
].filter(Boolean);
burstTargets.forEach((btn) => {
  btn.addEventListener('click', (e) => burst(e.clientX, e.clientY));
});
