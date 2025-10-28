// sidebar.js - robust helper for mobile toggle and active link highlight
(function(){
  const sidebar = document.getElementById('appSidebar');
  if (!sidebar) return; // guard when sidebar not yet in DOM

  const toggle = document.getElementById('sidebarToggle');
  const pageFromBody = (document.querySelector('body') || {}).dataset?.page || '';
  const links = sidebar.querySelectorAll('.nav-link, .nav-sublink');

  // set "active" class by either body[data-page] or current path
  const path = window.location.pathname.toLowerCase();
  links.forEach(a => {
    const page = (a.dataset && a.dataset.page) || '';
    const href = (a.getAttribute('href') || '').toLowerCase();
    if (page && page === pageFromBody) a.classList.add('active');
    else if (href && path.endsWith(href.replace(/^\//,''))) a.classList.add('active');
    else if (href && path.includes(page)) a.classList.add('active');
  });

  if (toggle) {
    toggle.addEventListener('click', () => sidebar.classList.toggle('expanded'));
  }

  // sub-menu toggle
  sidebar.querySelectorAll('.nav-has-sub .sub-toggle').forEach(btn => {
    btn.addEventListener('click', () => {
      const parent = btn.parentElement;
      const expanded = btn.getAttribute('aria-expanded') === 'true';
      btn.setAttribute('aria-expanded', String(!expanded));
      const sub = parent.querySelector('.sub-list');
      if (sub) sub.style.maxHeight = expanded ? '0px' : '400px';
    });
  });

  // collapse on outside click (mobile)
  document.addEventListener('click', (e) => {
    if (window.innerWidth <= 900 && sidebar.classList.contains('expanded')) {
      if (!sidebar.contains(e.target) && (!toggle || !toggle.contains(e.target))) {
        sidebar.classList.remove('expanded');
      }
    }
  });
})();
