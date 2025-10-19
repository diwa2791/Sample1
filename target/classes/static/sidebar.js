// sidebar.js - small helper for mobile toggle and active link highlight
(function(){
  const sidebar = document.getElementById('appSidebar');
  const toggle = document.getElementById('sidebarToggle');
  const currentPage = document.querySelector('body').dataset.page; // optional: set <body data-page="pods">
  const links = sidebar.querySelectorAll('.nav-link, .nav-sublink');

  // set "active" class based on data-page attribute
  if (currentPage) {
    links.forEach(a => {
      if (a.dataset.page === currentPage) a.classList.add('active');
    });
  }

  // mobile toggle: add/remove 'expanded' class
  if (toggle) {
    toggle.addEventListener('click', () => {
      sidebar.classList.toggle('expanded');
    });
  }

  // for nav-has-sub buttons, allow click to toggle on touch devices
  sidebar.querySelectorAll('.nav-has-sub .sub-toggle').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const parent = btn.parentElement;
      const expanded = btn.getAttribute('aria-expanded') === 'true';
      btn.setAttribute('aria-expanded', String(!expanded));
      if (!expanded) parent.querySelector('.sub-list').style.maxHeight = '400px';
      else parent.querySelector('.sub-list').style.maxHeight = '0px';
    });
  });

  // collapse sidebar on outside click for mobile
  document.addEventListener('click', (e) => {
    if (window.innerWidth <= 900 && sidebar.classList.contains('expanded')) {
      if (!sidebar.contains(e.target) && !toggle.contains(e.target)) {
        sidebar.classList.remove('expanded');
      }
    }
  });
})();
