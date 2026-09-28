// Libris landing page - handles modal interactions and authentication
document.addEventListener('DOMContentLoaded', () => {
  // Initialize theme toggle - handle both nav and fixed positions
  const themeToggleRoots = document.querySelectorAll('#nav-theme, #topbar-theme');
  themeToggleRoots.forEach(root => {
    if (root && window.initThemeToggle) {
      window.initThemeToggle(root);
    }
  });

  // Scroll reveal animations using IntersectionObserver
  const revealElements = document.querySelectorAll('.reveal');
  const staggerContainers = document.querySelectorAll('.stagger-reveal');

  const revealObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
      } else {
        entry.target.classList.remove('visible');
      }
    });
  }, {
    threshold: 0.1,
    rootMargin: '0px 0px -50px 0px'
  });

  revealElements.forEach(el => revealObserver.observe(el));

  const staggerObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
      } else {
        entry.target.classList.remove('visible');
      }
    });
  }, {
    threshold: 0.1,
    rootMargin: '0px 0px -50px 0px'
  });

  staggerContainers.forEach(el => staggerObserver.observe(el));

  // Active section indicator for navigation
  const navLinks = document.querySelectorAll('.landing-nav-link');
  const sections = document.querySelectorAll('#workspace, #features, #how-it-works, #technology');

  const sectionObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        const sectionId = entry.target.id;
        navLinks.forEach(link => {
          link.classList.remove('active');
          if (link.dataset.section === sectionId) {
            link.classList.add('active');
          }
        });
      }
    });
  }, {
    threshold: 0.3,
    rootMargin: '-100px 0px -50% 0px'
  });

  sections.forEach(section => sectionObserver.observe(section));

  // Check if hash indicates registration
  if (window.location.hash === '#register') {
    setTimeout(() => {
      const openRegisterBtn = document.getElementById('openRegisterModal');
      if (openRegisterBtn) openRegisterBtn.click();
    }, 100);
  }

  const loginModal = document.getElementById('loginModal');
  const loginBackdrop = document.getElementById('loginModalBackdrop');
  const registerModal = document.getElementById('registerModal');
  const registerBackdrop = document.getElementById('registerModalBackdrop');

  // Login modal controls
  const openLoginBtn = document.getElementById('openLoginModal');
  const closeLoginBtn = document.getElementById('closeLoginModal');
  const openRegisterFromLogin = document.getElementById('openRegisterFromLogin');

  // Register modal controls
  const openRegisterBtn = document.getElementById('openRegisterModal');
  const closeRegisterBtn = document.getElementById('closeRegisterModal');
  const openLoginFromRegister = document.getElementById('openLoginFromRegister');

  // CTA buttons
  const heroLoginBtn = document.getElementById('heroLogin');
  const heroPreviewBtn = document.getElementById('heroPreview');
  const ctaLoginBtn = document.getElementById('ctaLogin');
  const ctaPreviewBtn = document.getElementById('ctaPreview');

  // Smooth scroll for anchor links
  document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function (e) {
      const href = this.getAttribute('href');
      if (href === '#') return;

      const target = document.querySelector(href);
      if (target) {
        e.preventDefault();
        const headerHeight = 72; // Match --header-height
        const targetPosition = target.getBoundingClientRect().top + window.pageYOffset - headerHeight;
        window.scrollTo({
          top: targetPosition,
          behavior: 'smooth'
        });
      } else if (href === '#workspace') {
        // Special handling for workspace section (in hero)
        e.preventDefault();
        const workspaceSection = document.getElementById('workspace');
        if (workspaceSection) {
          const headerHeight = 72;
          const targetPosition = workspaceSection.getBoundingClientRect().top + window.pageYOffset - headerHeight;
          window.scrollTo({
            top: targetPosition,
            behavior: 'smooth'
          });
        }
      }
    });
  });

  // Password toggles
  const pwToggles = document.querySelectorAll('.pw-toggle');

  // Login form
  const loginForm = document.getElementById('login-form');
  const loginError = document.getElementById('login-error');

  // Register form
  const registerForm = document.getElementById('registerForm');
  const registerError = document.getElementById('register-error');

  // Functions to open/close modals
  function openLoginModal() {
    loginBackdrop.hidden = false;
    closeLoginBtn.focus();
    document.body.style.overflow = 'hidden';
  }

  function closeLoginModal() {
    loginBackdrop.hidden = true;
    document.body.style.overflow = '';
    if (openLoginBtn) openLoginBtn.focus();
  }

  function openRegisterModal() {
    registerBackdrop.hidden = false;
    closeRegisterBtn.focus();
    document.body.style.overflow = 'hidden';
  }

  function closeRegisterModal() {
    registerBackdrop.hidden = true;
    document.body.style.overflow = '';
    if (openRegisterBtn) openRegisterBtn.focus();
  }

  // Event listeners for login modal
  if (openLoginBtn) openLoginBtn.addEventListener('click', openLoginModal);
  if (heroLoginBtn) heroLoginBtn.addEventListener('click', openLoginModal);
  if (heroPreviewBtn) heroPreviewBtn.addEventListener('click', () => {
    const workspaceSection = document.getElementById('workspace');
    if (workspaceSection) {
      const headerHeight = 72;
      const targetPosition = workspaceSection.getBoundingClientRect().top + window.pageYOffset - headerHeight;
      window.scrollTo({
        top: targetPosition,
        behavior: 'smooth'
      });
    } else {
      // Fallback to hero section if workspace section doesn't exist
      document.querySelector('.landing-hero').scrollIntoView({ behavior: 'smooth' });
    }
  });
  if (ctaLoginBtn) ctaLoginBtn.addEventListener('click', openLoginModal);
  if (closeLoginBtn) closeLoginBtn.addEventListener('click', closeLoginModal);
  if (openRegisterFromLogin) {
    openRegisterFromLogin.addEventListener('click', () => {
      closeLoginModal();
      openRegisterModal();
    });
  }

  // Event listeners for CTA preview button
  if (ctaPreviewBtn) {
    ctaPreviewBtn.addEventListener('click', () => {
      const howItWorksSection = document.getElementById('how-it-works');
      if (howItWorksSection) {
        const headerHeight = 72;
        const targetPosition = howItWorksSection.getBoundingClientRect().top + window.pageYOffset - headerHeight;
        window.scrollTo({
          top: targetPosition,
          behavior: 'smooth'
        });
      }
    });
  }

  // Event listeners for register modal
  if (openRegisterBtn) openRegisterBtn.addEventListener('click', openRegisterModal);
  if (closeRegisterBtn) closeRegisterBtn.addEventListener('click', closeRegisterModal);
  if (openLoginFromRegister) {
    openLoginFromRegister.addEventListener('click', () => {
      closeRegisterModal();
      openLoginModal();
    });
  }

  // Close modals on backdrop click
  if (loginBackdrop) {
    loginBackdrop.addEventListener('click', (e) => {
      if (e.target === loginBackdrop) closeLoginModal();
    });
  }

  if (registerBackdrop) {
    registerBackdrop.addEventListener('click', (e) => {
      if (e.target === registerBackdrop) closeRegisterModal();
    });
  }

  // Close modals on Escape key
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if (!loginBackdrop.hidden) closeLoginModal();
      if (!registerBackdrop.hidden) closeRegisterModal();
    }
  });

  // Password toggle functionality
  pwToggles.forEach(toggle => {
    toggle.addEventListener('click', () => {
      const targetId = toggle.dataset.target;
      const targetInput = document.getElementById(targetId);
      const isOpen = toggle.getAttribute('aria-pressed') === 'true';

      if (targetInput) {
        targetInput.type = isOpen ? 'password' : 'text';
        toggle.setAttribute('aria-pressed', !isOpen);

        const eyeOpen = toggle.querySelector('.pw-eye-open');
        const eyeClosed = toggle.querySelector('.pw-eye-closed');

        if (eyeOpen && eyeClosed) {
          eyeOpen.hidden = !isOpen;
          eyeClosed.hidden = isOpen;
        }
      }
    });
  });

  // Login form submission
  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      e.preventDefault();

      const formData = new FormData(loginForm);
      const username = formData.get('username');
      const password = formData.get('password');

      // Get fresh CSRF token from cookie before making request
      const currentCsrfToken = getCsrfTokenFromCookie();

      try {
        const response = await fetch('/api/auth/login', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': currentCsrfToken
          },
          body: JSON.stringify({ username, password })
        });

        if (response.ok) {
          window.location.href = '/index.html';
        } else {
          const error = await response.json();
          if (loginError) {
            loginError.textContent = error.message || 'Login failed. Please try again.';
            loginError.hidden = false;
          }
        }
      } catch (error) {
        if (loginError) {
          loginError.textContent = 'Network error. Please check your connection.';
          loginError.hidden = false;
        }
      }
    });
  }

  // Register form submission
  if (registerForm) {
    registerForm.addEventListener('submit', async (e) => {
      e.preventDefault();

      const formData = new FormData(registerForm);
      const registerData = {
        name: formData.get('name'),
        email: formData.get('email'),
        phone: formData.get('phone'),
        username: formData.get('username'),
        password: formData.get('password'),
        confirmPassword: formData.get('confirmPassword')
      };

      // Basic client-side validation
      if (registerData.password !== registerData.confirmPassword) {
        if (registerError) {
          registerError.textContent = 'Passwords do not match.';
          registerError.hidden = false;
        }
        return;
      }

      // Get fresh CSRF token from cookie before making request
      const currentCsrfToken = getCsrfTokenFromCookie();

      try {
        const response = await fetch('/api/auth/register', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': currentCsrfToken
          },
          body: JSON.stringify({
            username: registerData.username,
            email: registerData.email,
            password: registerData.password,
            name: registerData.name,
            phone: registerData.phone
          })
        });

        if (response.ok) {
          window.location.href = '/index.html';
        } else {
          const error = await response.json();
          if (registerError) {
            registerError.textContent = error.message || 'Registration failed. Please try again.';
            registerError.hidden = false;
          }
        }
      } catch (error) {
        if (registerError) {
          registerError.textContent = 'Network error. Please check your connection.';
          registerError.hidden = false;
        }
      }
    });
  }

  // Get CSRF token from cookie (not the API response which is XOR-encoded)
  function getCsrfTokenFromCookie() {
    const name = 'XSRF-TOKEN=';
    const cookies = document.cookie.split(';');
    for (let cookie of cookies) {
      while (cookie.charAt(0) === ' ') cookie = cookie.substring(1);
      if (cookie.indexOf(name) === 0) {
        return cookie.substring(name.length, cookie.length);
      }
    }
    return '';
  }
});