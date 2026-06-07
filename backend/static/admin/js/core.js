    // ============================================================

    // State

    // ============================================================

    let TOKEN = localStorage.getItem('admin_token') || '';

    const API = '/api/admin';

    let categories = [];

    const TABLE_PAGE_SIZE = 10;

    const tablePages = {

      predictions: 1,

      objects: 1,

      translations: 1,

      categories: 1,

      scanHistory: 1,

      users: 1,

    };

    let objectOnlyNoImage = false;



    // ============================================================

    // Auth

    // ============================================================

    function togglePass() {

      const inp = document.getElementById('login-pass');

      const ico = document.getElementById('pass-eye-icon');

      if (inp.type === 'password') { inp.type = 'text'; ico.className = 'bi bi-eye'; }

      else { inp.type = 'password'; ico.className = 'bi bi-eye-slash'; }

    }



    async function doLogin() {

      const user = document.getElementById('login-user').value.trim();

      const pass = document.getElementById('login-pass').value;

      const err = document.getElementById('login-err');

      const btn = document.getElementById('login-label');

      err.style.display = 'none';

      btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Đang đăng nhập...';

      try {

        const res = await fetch('/api/auth/login', {

          method: 'POST',

          headers: { 'Content-Type': 'application/json' },

          body: JSON.stringify({ username: user, password: pass }),

        });

        if (!res.ok) {

          err.textContent = 'Sai tài khoản hoặc mật khẩu';

          err.style.display = '';

          btn.textContent = 'Đăng nhập';

          return;

        }

        const data = await res.json();

        TOKEN = data.access_token;

        localStorage.setItem('admin_token', TOKEN);

        document.getElementById('login-screen').style.display = 'none';

        setUserDisplay(user);

        initApp();

      } catch (e) {

        err.textContent = 'Không kết nối được server';

        err.style.display = '';

        btn.textContent = 'Đăng nhập';

      }

    }



    function setUserDisplay(user) {

      const name = user || 'Admin';

      document.getElementById('topbar-username').textContent = name;

      document.getElementById('topbar-avatar').textContent = name.charAt(0).toUpperCase();

    }



    function doLogout(expired = false) {

      TOKEN = '';

      localStorage.removeItem('admin_token');

      document.querySelectorAll('.modal.show').forEach(el => {

        bootstrap.Modal.getInstance(el)?.hide();

      });

      const err = document.getElementById('login-err');

      if (expired) {

        err.textContent = 'Phiên làm việc đã hết hạn, vui lòng đăng nhập lại.';

        err.style.display = '';

      } else {

        err.style.display = 'none';

      }

      document.getElementById('login-screen').style.display = '';

      document.getElementById('login-label').textContent = 'Đăng nhập';

    }



    // ============================================================

    // Init

    // ============================================================

    function initApp() {

      document.querySelectorAll('[data-section]').forEach(a => {

        a.addEventListener('click', e => { e.preventDefault(); navigateTo(a.dataset.section); });

      });

      fetch('/api/auth/profile', { headers: { 'Authorization': 'Bearer ' + TOKEN } })

        .then(r => r.ok ? r.json() : null)

        .then(p => { if (p?.username) setUserDisplay(p.username); })

        .catch(() => {});

      loadCategories();

      navigateTo('dashboard');

    }



    const SECTION_LABELS = {

      dashboard: 'Dashboard', predictions: 'Kiểm duyệt',

      objects: 'Đối tượng', translations: 'Bản dịch',

      'scan-history': 'Lịch sử quét', categories: 'Danh mục', users: 'Người dùng',

      'training-data': 'Training Data',

    };



    function navigateTo(section) {

      document.querySelectorAll('[data-section]').forEach(a =>

        a.classList.toggle('active', a.dataset.section === section)

      );

      document.querySelectorAll('.section').forEach(s =>

        s.classList.toggle('active', s.id === 'section-' + section)

      );

      const titleEl = document.getElementById('page-title');

      titleEl.classList.add('switching');

      requestAnimationFrame(() => {

        titleEl.textContent = SECTION_LABELS[section] || section;

        titleEl.classList.remove('switching');

      });

      const loaders = {

        dashboard: loadDashboard, predictions: loadPredictions,

        objects: loadObjects, translations: loadTranslations,

        'scan-history': loadScanHistory, categories: loadCategories, users: loadUsers,

        'training-data': loadTrainingData,

      };

      if (loaders[section]) loaders[section]();

    }



    // ============================================================

    // API helpers

    // ============================================================

    async function api(path, opts = {}) {

      const res = await fetch(API + path, {

        ...opts,

        headers: { 'Authorization': 'Bearer ' + TOKEN, 'Content-Type': 'application/json', ...(opts.headers || {}) },

      });

      if (res.status === 401) { doLogout(true); throw new Error('Phiên hết hạn'); }

      return res;

    }



    async function apiJSON(path, opts = {}) {

      const res = await api(path, opts);

      if (!res.ok) { const t = await res.text(); throw new Error(t); }

      return res.json();

    }



    function setModalBody(elId, html) {

      const el = document.getElementById(elId);

      if (!el) return;

      el.innerHTML = '<div class="modal-body-content">' + html + '</div>';

    }



    function escHtml(s) {

      return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

    }



    // ============================================================

    // Toast

    // ============================================================

    const TOAST_ICONS = { success: 'bi-check-circle-fill', danger: 'bi-x-circle-fill', warning: 'bi-exclamation-triangle-fill' };



    function toast(msg, type = 'success') {

      const el = document.createElement('div');

      el.className = `toast-item ${type}`;

      el.innerHTML = `<i class="bi ${TOAST_ICONS[type] || 'bi-info-circle'}"></i><span>${msg}</span>`;

      document.getElementById('toast-container').appendChild(el);

      setTimeout(() => { el.style.opacity = '0'; el.style.transform = 'translateX(16px)'; el.style.transition = '.18s'; setTimeout(() => el.remove(), 180); }, 2800);

    }



    // ============================================================

    // Confirm modal (thay thế browser confirm)

    // ============================================================

    function confirmAction(message, onConfirm, title = 'Xác nhận') {

      document.getElementById('cm-title').textContent = title;

      document.getElementById('cm-body').textContent = message;

      const btn = document.getElementById('cm-confirm');

      const modal = new bootstrap.Modal(document.getElementById('confirm-modal'));

      const handler = () => { modal.hide(); onConfirm(); btn.removeEventListener('click', handler); };

      btn.addEventListener('click', handler);

      document.getElementById('confirm-modal').addEventListener('hidden.bs.modal', () => {

        btn.removeEventListener('click', handler);

      }, { once: true });

      modal.show();

      requestAnimationFrame(() => {

        const backdrops = document.querySelectorAll('.modal-backdrop');

        if (backdrops.length > 1) backdrops[backdrops.length - 1].style.zIndex = '1070';

      });

    }



    // ============================================================

    // Helpers

    // ============================================================

    function confBar(v) {

      if (!v) return '<span class="text-muted">—</span>';

      const pct = Math.round(v * 100);

      const color = pct >= 80 ? '#10b981' : pct >= 60 ? '#f59e0b' : '#ef4444';

      return `<div class="conf-bar"><div class="conf-track"><div class="conf-fill" style="width:${pct}%;background:${color};"></div></div><small style="color:${color};font-weight:700;">${pct}%</small></div>`;

    }



    function translationBadge(approved, pending = 0) {

      approved = Number(approved || 0);

      pending = Number(pending || 0);

      const pendingLabel = pending > 0

        ? `<span class="badge badge-pending" title="B&#7843;n d&#7883;ch Gemini ch&#432;a duy&#7879;t">${pending} ch&#7901; duy&#7879;t</span>`

        : '';

      return `<div class="translation-stack">

    <span class="badge ${approved > 0 ? 'badge-approved' : 'badge-neutral'}" title="B&#7843;n d&#7883;ch &#273;&#227; duy&#7879;t">${approved} b&#7843;n d&#7883;ch</span>

    ${pendingLabel}

  </div>`;

    }



    function fmtDate(s) {

      if (!s) return '—';

      return new Date(s).toLocaleString('vi', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

    }



    function fmtDateShort(s) {

      if (!s) return '—';

      return new Date(s).toLocaleDateString('vi', { day: '2-digit', month: '2-digit', year: 'numeric' });

    }



    function emptyRow(cols, icon, msg) {

      return `<tr><td colspan="${cols}"><div class="empty-state"><i class="bi ${icon}"></i><p>${msg}</p></div></td></tr>`;

    }



    function loadingRow(cols) {

      return `<tr><td colspan="${cols}" class="text-center text-muted py-3"><span class="spinner-border spinner-border-sm me-2"></span>Đang tải...</td></tr>`;

    }



    const PAGINATION_TARGETS = {

      predictions: 'pred-pagination',

      objects: 'obj-pagination',

      translations: 'trans-pagination',

      categories: 'cat-pagination',

      scanHistory: 'sh-pagination',

      users: 'user-pagination',

    };



    function tableLoader(key) {

      const loaders = {

        predictions: loadPredictions,

        objects: loadObjects,

        translations: loadTranslations,

        categories: loadCategories,

        scanHistory: loadScanHistory,

        users: loadUsers,

      };

      return loaders[key];

    }



    function getPagedRows(key, rows) {

      const total = rows.length;

      const totalPages = Math.max(1, Math.ceil(total / TABLE_PAGE_SIZE));

      const page = Math.min(Math.max(tablePages[key] || 1, 1), totalPages);

      tablePages[key] = page;

      const start = (page - 1) * TABLE_PAGE_SIZE;

      const end = Math.min(start + TABLE_PAGE_SIZE, total);

      return { rows: rows.slice(start, end), page, totalPages, total, start, end };

    }



    function paginationWindow(page, totalPages) {

      if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i + 1);

      const nums = [1];

      const from = Math.max(2, page - 1);

      const to = Math.min(totalPages - 1, page + 1);

      if (from > 2) nums.push('...');

      for (let i = from; i <= to; i++) nums.push(i);

      if (to < totalPages - 1) nums.push('...');

      nums.push(totalPages);

      return nums;

    }



    function renderTablePagination(key, pageData) {

      const el = document.getElementById(PAGINATION_TARGETS[key]);

      if (!el) return;

      if (!pageData.total || pageData.total <= TABLE_PAGE_SIZE) {

        el.innerHTML = '';

        el.classList.add('is-hidden');

        return;

      }

      el.classList.remove('is-hidden');

      const pages = paginationWindow(pageData.page, pageData.totalPages).map(p => (

        p === '...'

          ? '<span class="page-dots">...</span>'

          : `<button type="button" class="page-btn ${p === pageData.page ? 'active' : ''}" onclick="setTablePage('${key}', ${p})">${p}</button>`

      )).join('');

      el.innerHTML = `

        <div class="page-info">Hiển thị ${pageData.start + 1}-${pageData.end} / ${pageData.total}</div>

        <div class="page-controls">

          <button type="button" class="page-btn icon" onclick="setTablePage('${key}', ${pageData.page - 1})" ${pageData.page <= 1 ? 'disabled' : ''} title="Trang trước">

            <i class="bi bi-chevron-left"></i>

          </button>

          ${pages}

          <button type="button" class="page-btn icon" onclick="setTablePage('${key}', ${pageData.page + 1})" ${pageData.page >= pageData.totalPages ? 'disabled' : ''} title="Trang sau">

            <i class="bi bi-chevron-right"></i>

          </button>

        </div>`;

    }



    function setTablePage(key, page) {

      tablePages[key] = Math.max(1, page);

      const loader = tableLoader(key);

      if (loader) loader();

    }



    function reloadPagedTable(key) {

      tablePages[key] = 1;

      const loader = tableLoader(key);

      if (loader) loader();

    }
