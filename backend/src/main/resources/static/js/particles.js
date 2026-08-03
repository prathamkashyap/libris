/**
 * Ambient particle system — theme-reactive.
 * Blaze: rising embers. Frost: drifting snowflakes. Nebula: twinkling stars.
 * Only runs on desktop with motion allowed.
 */

let canvas = null;
let ctx = null;
let particles = [];
let rafId = null;
let running = false;
let currentTheme = 'ember';

const MAX_PARTICLES = 60;

export function initParticles(theme) {
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  if (matchMedia('(pointer: coarse)').matches) return;

  currentTheme = theme;
  if (!canvas) {
    canvas = document.createElement('canvas');
    canvas.className = 'ambient-canvas';
    canvas.setAttribute('aria-hidden', 'true');
    document.body.appendChild(canvas);
    ctx = canvas.getContext('2d');
    resize();
    window.addEventListener('resize', resize);
  }
  spawnParticles();
  if (!running) {
    running = true;
    tick();
  }
}

export function switchParticles(theme) {
  currentTheme = theme;
  particles = [];
  spawnParticles();
}

export function destroyParticles() {
  running = false;
  if (rafId) cancelAnimationFrame(rafId);
  particles = [];
  if (canvas && canvas.parentNode) canvas.parentNode.removeChild(canvas);
  canvas = null;
  ctx = null;
}

function resize() {
  if (!canvas) return;
  canvas.width = window.innerWidth;
  canvas.height = window.innerHeight;
}

function spawnParticles() {
  particles = [];
  for (var i = 0; i < MAX_PARTICLES; i++) {
    particles.push(createParticle(true));
  }
}

function createParticle(randomY) {
  var w = canvas ? canvas.width : window.innerWidth;
  var h = canvas ? canvas.height : window.innerHeight;

  if (currentTheme === 'ember' || currentTheme === 'blaze') {
    return {
      x: Math.random() * w,
      y: randomY ? Math.random() * h : h + 10,
      size: Math.random() * 2.5 + 0.8,
      speedY: -(Math.random() * 0.6 + 0.15),
      speedX: (Math.random() - 0.5) * 0.3,
      opacity: Math.random() * 0.6 + 0.2,
      color: Math.random() > 0.5 ? 'rgba(240,168,48,' : 'rgba(255,120,30,',
      life: 0,
      maxLife: Math.random() * 400 + 200,
      flicker: Math.random() * 0.3
    };
  } else if (currentTheme === 'slate' || currentTheme === 'frost') {
    return {
      x: Math.random() * w,
      y: randomY ? Math.random() * h : -10,
      size: Math.random() * 3 + 1,
      speedY: Math.random() * 0.4 + 0.1,
      speedX: (Math.random() - 0.5) * 0.2,
      opacity: Math.random() * 0.4 + 0.1,
      color: Math.random() > 0.5 ? 'rgba(56,189,248,' : 'rgba(180,220,255,',
      life: 0,
      maxLife: Math.random() * 500 + 300,
      flicker: Math.random() * 0.2,
      wobble: Math.random() * Math.PI * 2,
      wobbleSpeed: Math.random() * 0.01 + 0.005
    };
  } else {
    return {
      x: Math.random() * w,
      y: Math.random() * h,
      size: Math.random() * 1.5 + 0.3,
      speedY: 0,
      speedX: 0,
      opacity: 0,
      targetOpacity: Math.random() * 0.5 + 0.1,
      color: Math.random() > 0.6 ? 'rgba(167,139,250,' : (Math.random() > 0.5 ? 'rgba(56,189,248,' : 'rgba(255,255,255,'),
      life: 0,
      maxLife: Math.random() * 300 + 150,
      flicker: Math.random() * 0.5,
      twinkleSpeed: Math.random() * 0.03 + 0.01
    };
  }
}

function tick() {
  if (!running || !ctx) return;
  ctx.clearRect(0, 0, canvas.width, canvas.height);

  for (var i = 0; i < particles.length; i++) {
    var p = particles[i];
    p.life++;

    if (currentTheme === 'ember' || currentTheme === 'blaze') {
      p.y += p.speedY;
      p.x += p.speedX + Math.sin(p.life * 0.01) * 0.15;
      var fadeIn = Math.min(1, p.life / 40);
      var fadeOut = Math.max(0, 1 - (p.life - p.maxLife + 60) / 60);
      var alpha = p.opacity * fadeIn * fadeOut;
      alpha += Math.sin(p.life * 0.05) * p.flicker;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
      ctx.fillStyle = p.color + Math.max(0, alpha).toFixed(2) + ')';
      ctx.fill();
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.size * 2.5, 0, Math.PI * 2);
      ctx.fillStyle = p.color + (Math.max(0, alpha) * 0.15).toFixed(2) + ')';
      ctx.fill();
    } else if (currentTheme === 'slate' || currentTheme === 'frost') {
      p.wobble += p.wobbleSpeed;
      p.y += p.speedY;
      p.x += p.speedX + Math.sin(p.wobble) * 0.3;
      var fadeIn2 = Math.min(1, p.life / 50);
      var fadeOut2 = Math.max(0, 1 - (p.life - p.maxLife + 80) / 80);
      var alpha2 = p.opacity * fadeIn2 * fadeOut2;
      ctx.save();
      ctx.translate(p.x, p.y);
      ctx.rotate(p.wobble);
      drawSnowflake(ctx, 0, 0, p.size, p.color, alpha2);
      ctx.restore();
    } else {
      var twinkle = Math.sin(p.life * p.twinkleSpeed) * 0.5 + 0.5;
      var alpha3 = p.targetOpacity * twinkle;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
      ctx.fillStyle = p.color + alpha3.toFixed(2) + ')';
      ctx.fill();
      if (p.size > 1) {
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.size * 3, 0, Math.PI * 2);
        ctx.fillStyle = p.color + (alpha3 * 0.08).toFixed(2) + ')';
        ctx.fill();
      }
    }

    if (p.life >= p.maxLife) {
      particles[i] = createParticle(false);
    }
  }

  rafId = requestAnimationFrame(tick);
}

function drawSnowflake(c, x, y, size, color, alpha) {
  c.beginPath();
  c.arc(x, y, size * 0.5, 0, Math.PI * 2);
  c.fillStyle = color + alpha.toFixed(2) + ')';
  c.fill();
  c.beginPath();
  c.arc(x, y, size * 2, 0, Math.PI * 2);
  c.fillStyle = color + (alpha * 0.1).toFixed(2) + ')';
  c.fill();
}
