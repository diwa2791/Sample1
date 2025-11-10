// === KDASH on-demand details patch (fresh fetch on click, loader, ns-aware, NO auto-refresh) ===
// What this does:
// 1) Never use cached data for card details; always fetch fresh when you click/refresh.
// 2) Shows a loader while fetching.
// 3) Uses the currently selected namespace (ignores "all") and remembers selection.
// 4) Stops background refresh from wiping the details table.
// 5) Adds a ↻ refresh button to each card.

(function () {
  // ----- optional: configure your services endpoint(s) here -----
  // If your backend exposes /api/services (OK), leave as-is.
  // If not, add the correct path first in the array (e.g. "/api/v1/namespaces/${ns}/services").
  const SERVICES_ENDPOINTS = [
    (ns) => `/api/services?namespace=${encodeURIComponent(ns)}`,
    (ns) => `/api/v1/namespaces/${encodeURIComponent(ns)}/services`
  ];

  const $ = (sel) => document.querySelector(sel);
  const podsBody = document.getElementById('podsBody') || $('#podsBody');
  const nsSelect = document.getElementById('namespaceSelect') || $('#namespaceSelect');

  // ---- Namespace: persist + never lose it ----
  const NS_KEY = 'kdash.lastNs';
  const nsState = { value: null };

  function setNs(v) {
    if (v && v !== 'all') {
      nsState.value = v;
      try { localStorage.setItem(NS_KEY, v); } catch (e) {}
      try { window.currentNamespace = v; } catch (e) {}
    }
  }

  function initNamespace() {
    if (nsSelect && nsSelect.value && nsSelect.value !== 'all') {
      setNs(nsSelect.value);
    } else {
      let saved = null;
      try { saved = localStorage.getItem(NS_KEY); } catch (e) {}
      const fallback = (typeof window.currentNamespace === 'string' ? window.currentNamespace : null);
      const candidate = saved || fallback;
      if (candidate && candidate !== 'all') {
        setNs(candidate);
        if (nsSelect) nsSelect.value = candidate; // reflect in UI if option exists
      }
    }
    if (nsSelect) nsSelect.addEventListener('change', (e) => setNs(e.target.value));
  }

  function getNs() {
    if (nsSelect && nsSelect.value && nsSelect.value !== 'all') {
      setNs(nsSelect.value);
      return nsSelect.value;
    }
    if (nsState.value && nsState.value !== 'all') return nsState.value;
    try {
      const saved = localStorage.getItem(NS_KEY);
      if (saved && saved !== 'all') return saved;
    } catch (e) {}
    if (typeof window.currentNamespace === 'string' && window.currentNamespace !== 'all') {
      return window.currentNamespace;
    }
    return null;
  }

  initNamespace();

  // ----- Kill/guard background refreshers that wipe the table -----
  try { if (window.__pods_poll) { clearInterval(window.__pods_poll); window.__pods_poll = null; } } catch (e) {}
  try { window.__detailLock = false; } catch (e) {}

  const nowNoCache = () => Date.now().toString();
  const showLoader = (msg) => { if (podsBody) podsBody.innerHTML = `<tr><td colspan="4">${msg || 'Loading...'}</td></tr>`; };
  const showError  = (msg) => { if (podsBody) podsBody.innerHTML = `<tr><td colspan="4" style="color:var(--danger)">${msg}</td></tr>`; };
  const esc = (s) => String(s).replace(/[&<>]/g,c=>({ '&':'&amp;','<':'&lt;','>':'&gt;' }[c]));

  // ---- Fetchers (always fresh) ----
  async function fetchPodsFresh(ns) {
    const url = `/api/pods?namespace=${encodeURIComponent(ns)}&t=${nowNoCache()}`;
    const res = await fetch(url, { cache: 'no-store' });
    if (!res.ok) throw new Error(`Pods HTTP ${res.status}`);
    const payload = await res.json();
    return Array.isArray(payload) ? payload
      : (payload && Array.isArray(payload.items) ? payload.items : []);
  }

  async function fetchServicesFresh(ns) {
    let lastErr;
    for (const fn of SERVICES_ENDPOINTS) {
      const url = `${fn(ns)}&t=${nowNoCache()}`;
      try {
        const r = await fetch(url, { cache: 'no-store' });
        if (!r.ok) { lastErr = new Error(`Services HTTP ${r.status}`); continue; }
        const payload = await r.json();
        return Array.isArray(payload) ? payload
          : (payload && Array.isArray(payload.items) ? payload.items : []);
      } catch (e) {
        lastErr = e;
      }
    }
    throw (lastErr || new Error('No services endpoint available.'));
  }

  // ---- Row renderers (use your page’s builders when available) ----
  const renderPodRow = (typeof window.createPodRow === 'function')
    ? window.createPodRow
    : (p) => {
        const tr = document.createElement('tr');
        const name = p?.metadata?.name || p?.name || '(unknown)';
        const ns   = p?.metadata?.namespace || p?.namespace || (getNs() || '');
        const phase = (p?.status?.phase || p?.status || '').toString();
        tr.innerHTML = `<td><strong>${esc(name)}</strong><div class="text-muted small">${esc(ns)}</div></td>
                        <td>${esc(phase)}</td><td class="text-muted">—</td><td class="text-muted">—</td>`;
        return tr;
      };

  const renderServiceRow = (typeof window.createServiceRow === 'function')
    ? window.createServiceRow
    : (svc) => {
        const tr = document.createElement('tr');
        const name = svc?.metadata?.name || svc?.name || '(unknown)';
        const ns   = svc?.metadata?.namespace || svc?.namespace || (getNs() || '');
        tr.innerHTML = `<td><strong>${esc(name)}</strong><div class="text-muted small">${esc(ns)}</div></td>
                        <td colspan="2"><span class="badge">0 pods</span></td><td class="text-muted">—</td>`;
        return tr;
      };

  // ---- Status helpers ----
  const normalizeStatus = (p) => {
    const s = (p?.status?.phase || p?.status || '').toString().toLowerCase();
    if (s.includes('running')) return 'running';
    if (s.includes('pending') || s.includes('containercreating')) return 'pending';
    return s;
  };
  const isAbnormal = (p) => {
    const cs = p?.status?.containerStatuses || [];
    return cs.some(c => {
      const w = c?.state?.waiting?.reason?.toLowerCase() || '';
      const t = c?.state?.terminated?.reason?.toLowerCase() || '';
      return w.includes('crashloop') || w.includes('error') || t.includes('error') || t.includes('crash');
    });
  };

  // ---- Card loaders (set detail lock while showing results) ----
  async function loadRunning() {
    const ns = getNs(); if (!ns) return showError('Select a specific namespace to view Running pods.');
    window.__detailLock = true; showLoader('Loading Running pods…');
    try {
      const pods = await fetchPodsFresh(ns);
      const filtered = pods.filter(p => normalizeStatus(p) === 'running');
      podsBody.innerHTML = ''; filtered.forEach(p => podsBody.appendChild(renderPodRow(p)));
      if (!filtered.length) showError('No Running pods found.');
    } catch (e) { showError(e.message || 'Failed to load Running pods'); }
  }

  async function loadPending() {
    const ns = getNs(); if (!ns) return showError('Select a specific namespace to view Pending pods.');
    window.__detailLock = true; showLoader('Loading Pending pods…');
    try {
      const pods = await fetchPodsFresh(ns);
      const filtered = pods.filter(p => {
        const st = normalizeStatus(p);
        return st === 'pending' || st.includes('creating');
      });
      podsBody.innerHTML = ''; filtered.forEach(p => podsBody.appendChild(renderPodRow(p)));
      if (!filtered.length) showError('No Pending pods found.');
    } catch (e) { showError(e.message || 'Failed to load Pending pods'); }
  }

  async function loadAbnormal() {
    const ns = getNs(); if (!ns) return showError('Select a specific namespace to view Abnormal pods.');
    window.__detailLock = true; showLoader('Scanning for abnormal pods…');
    try {
      const pods = await fetchPodsFresh(ns);
      const filtered = pods.filter(isAbnormal);
      podsBody.innerHTML = ''; filtered.forEach(p => podsBody.appendChild(renderPodRow(p)));
      if (!filtered.length) showError('No abnormal pods found. 🎉');
    } catch (e) { showError(e.message || 'Failed to load abnormal pods'); }
  }

  async function loadServicesZero() {
    const ns = getNs(); if (!ns) return showError('Select a specific namespace to view Services with 0 pods.');
    window.__detailLock = true; showLoader('Scanning services with zero pods…');
    try {
      const [services, pods] = await Promise.all([ fetchServicesFresh(ns), fetchPodsFresh(ns) ]);
      const podLabelsList = pods.map(p => (p?.metadata?.labels) || {});
      const selectorMatchesAnyPod = (selector) => {
        if (!selector || Object.keys(selector).length === 0) return false;
        return podLabelsList.some(lbls => Object.entries(selector).every(([k,v]) => lbls[k] === v));
      };
      const zero = services.filter(svc => !selectorMatchesAnyPod(svc?.spec?.selector || null));
      podsBody.innerHTML = '';
      if (!zero.length) showError('No services with zero matching pods. 🎉');
      else zero.forEach(svc => podsBody.appendChild(renderServiceRow(svc)));
    } catch (e) { showError(e.message || 'Failed to scan services'); }
  }

  // ---- Override default filter behavior to *always* fetch fresh on click ----
  const map = {
    Running: loadRunning,
    Pending: loadPending,
    Abnormal: loadAbnormal,
    ServicesZero: loadServicesZero,
  };

  // If the page defined applyFilter(next), override it to call our loaders.
  try {
    window.applyFilter = (next) => { if (map[next]) map[next](); };
  } catch (e) {}

  // Also wire direct click handlers in capture phase (wins over existing handlers).
  ['card-running','card-pending','card-abnormal','card-services-zero'].forEach((id) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.style.cursor = 'pointer';
    el.addEventListener('click', (ev) => {
      ev.preventDefault(); ev.stopPropagation();
      const next = el.dataset.filter;
      if (map[next]) map[next]();
    }, true);
  });

  // ---- Add tiny per-card refresh buttons (manual refresh only) ----
  function ensureRefreshButtons() {
    Object.entries({
      'card-running': loadRunning,
      'card-pending': loadPending,
      'card-abnormal': loadAbnormal,
      'card-services-zero': loadServicesZero
    }).forEach(([id, fn]) => {
      const el = document.getElementById(id);
      if (!el || el.querySelector('.card-refresh')) return;
      const btn = document.createElement('button');
      btn.className = 'card-refresh';
      btn.title = 'Refresh';
      btn.textContent = '↻';
      btn.style.cssText = 'position:absolute;top:6px;right:6px;padding:2px 6px;font-size:12px;border:1px solid rgba(0,0,0,.08);border-radius:6px;background:transparent;cursor:pointer';
      btn.addEventListener('click', (e) => { e.preventDefault(); e.stopPropagation(); fn(); });
      if (getComputedStyle(el).position === 'static') el.style.position = 'relative';
      el.appendChild(btn);
    });
  }
  ensureRefreshButtons();

  // ---- Protect against background summary refresh wiping details ----
  const origFetchSummary = window.fetchSummaryAndRender;
  if (typeof origFetchSummary === 'function') {
    window.fetchSummaryAndRender = async function() {
      if (window.__detailLock) return; // do nothing while details are visible
      return origFetchSummary.apply(this, arguments);
    };
  }
})();
