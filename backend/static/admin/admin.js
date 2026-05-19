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
      document.getElementById('page-title').textContent = SECTION_LABELS[section] || section;
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

    // ============================================================
    // Dashboard
    // ============================================================
    async function loadDashboard() {
      try {
        const stats = await apiJSON('/dashboard');

        const badge = document.getElementById('badge-predictions');
        if (stats.pending_predictions > 0) { badge.textContent = stats.pending_predictions; badge.style.display = ''; }
        else { badge.style.display = 'none'; }

        const cards = [
          { label: 'Người dùng', value: stats.total_users, icon: 'bi-people-fill', color: '#6366f1', bg: '#eef2ff', nav: 'users' },
          { label: 'Đối tượng', value: stats.total_objects, icon: 'bi-box-seam-fill', color: '#8b5cf6', bg: '#f5f3ff', nav: 'objects' },
          { label: 'Bản dịch', value: stats.total_translations, icon: 'bi-translate', color: '#10b981', bg: '#d1fae5', nav: 'translations' },
          { label: 'Lịch sử quét', value: stats.total_scans, icon: 'bi-camera-fill', color: '#3b82f6', bg: '#dbeafe', nav: 'scan-history' },
          { label: 'Chờ duyệt', value: stats.pending_predictions, icon: 'bi-hourglass-split', color: '#ef4444', bg: '#fee2e2', nav: 'predictions' },
          { label: 'Đã duyệt', value: stats.approved_predictions, icon: 'bi-check-circle-fill', color: '#10b981', bg: '#d1fae5', nav: 'predictions' },
          { label: 'Chưa có ảnh', value: stats.objects_without_images || 0, icon: 'bi-image-slash', color: '#f59e0b', bg: '#fef3c7', nav: 'objects' },
        ];

        document.getElementById('stat-cards').innerHTML = cards.map(c => `
      <div class="stat-box" style="--accent-color:${c.color};" ${c.nav ? `onclick="navigateTo('${c.nav}')" title="Xem ${c.label}"` : ''}>
        <div class="sb-top">
          <span class="sb-label">${c.label}</span>
          <div class="sb-icon" style="background:${c.bg};color:${c.color};"><i class="bi ${c.icon}"></i></div>
        </div>
        <div class="sb-val">${c.value.toLocaleString()}</div>
      </div>`).join('');

        const preds = await apiJSON('/predictions?trang_thai=cho_duyet&limit=8');
        document.getElementById('dash-pending-table').innerHTML = preds.length
          ? `<div class="table-responsive"><table class="table table-hover">
          <thead><tr><th style="width:54px;">STT</th><th style="width:60px;">ID</th><th style="width:52px;">Ảnh</th><th>Nhãn</th><th style="width:130px;">Tin cậy</th><th style="width:155px;">Thời gian</th><th style="width:130px;"></th></tr></thead>
          <tbody>${preds.map((p, idx) => `
            <tr>
              <td class="stt-cell">${idx + 1}</td>
              <td><span class="text-muted fw-semibold">${p.id}</span></td>
              <td>${p.scan_image_url ? `<img src="${p.scan_image_url}" style="width:40px;height:40px;object-fit:cover;border-radius:4px;cursor:pointer;" onclick="openPredModal(${p.id})" title="Xem chi tiết">` : '<span class="text-muted" style="font-size:.75rem;">N/A</span>'}</td>
              <td><code class="cell-ellipsis code-cell" title="${escHtml(p.nhan_du_doan || '')}">${escHtml(p.nhan_du_doan || '—')}</code></td>
              <td>${confBar(p.do_tin_cay)}</td>
              <td class="text-muted" style="font-size:.82rem;">${fmtDate(p.thoi_gian)}</td>
              <td>
                <div class="btn-actions">
                  <button class="btn-act" onclick="openPredModal(${p.id})" title="Chi tiết"><i class="bi bi-eye"></i></button>
                  <button class="btn-act" onclick="approvePredDash(${p.id})" title="Duyệt" style="color:#198754;"><i class="bi bi-check-lg"></i></button>
                  <button class="btn-act del" onclick="rejectPredDash(${p.id})" title="Từ chối"><i class="bi bi-x-lg"></i></button>
                </div>
              </td>
            </tr>`).join('')}
          </tbody></table></div>`
          : '<div class="empty-state"><i class="bi bi-check2-all"></i><p>Không có prediction chờ duyệt</p></div>';
      } catch (e) {
        toast('Lỗi tải dashboard: ' + e.message, 'danger');
      }
    }

    // ============================================================
    // Predictions
    // ============================================================
    function setPredFilter(status) {
      const sel = document.getElementById('pred-filter');
      if (sel) { sel.value = status; reloadPagedTable('predictions'); }
    }

    async function exportTrainingData() {
      const btn = document.getElementById('btn-export-training');
      if (btn) { btn.disabled = true; btn.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>Đang xuất...'; }
      try {
        const res = await fetch('/api/admin/predictions/export-training', {
          headers: { 'Authorization': 'Bearer ' + TOKEN }
        });
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'gemini_training_data.jsonl';
        a.click();
        URL.revokeObjectURL(url);
      } catch (e) {
        alert('Xuất thất bại: ' + e.message);
      } finally {
        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="bi bi-download me-1"></i>Export training data'; }
      }
    }

    async function exportTrainingGrouped() {
      const btn = document.getElementById('btn-export-grouped');
      if (btn) { btn.disabled = true; btn.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>Đang xuất...'; }
      try {
        const res = await fetch('/api/admin/predictions/export-training-grouped', {
          headers: { 'Authorization': 'Bearer ' + TOKEN }
        });
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'gemini_training_grouped.jsonl';
        a.click();
        URL.revokeObjectURL(url);
      } catch (e) {
        alert('Xuất thất bại: ' + e.message);
      } finally {
        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="bi bi-collection me-1"></i>Export grouped'; }
      }
    }

    async function loadTrainingStats() {
      try {
        const stats = await apiJSON('/stats');
        const approved = stats.da_duyet ?? 0;
        const pending  = stats.cho_duyet ?? 0;
        const rejected = stats.tu_choi ?? 0;
        document.getElementById('training-approved-count').textContent = approved;
        document.getElementById('training-pending-count').textContent  = pending;
        document.getElementById('training-rejected-count').textContent = rejected;
        const exportBtn = document.getElementById('btn-export-training');
        if (exportBtn) exportBtn.classList.toggle('disabled', approved === 0);
        const groupedBtn = document.getElementById('btn-export-grouped');
        if (groupedBtn) groupedBtn.classList.toggle('disabled', approved === 0);
      } catch (_) {}
    }

    async function loadPredictions() {
      loadTrainingStats();
      const status = document.getElementById('pred-filter')?.value || 'cho_duyet';
      try {
        const search = document.getElementById('pred-search')?.value.trim() || '';
        let predUrl = `/predictions?trang_thai=${status}&limit=100`;
        if (search) predUrl += `&search=${encodeURIComponent(search)}`;
        const data = await apiJSON(predUrl);
        const pageData = getPagedRows('predictions', data);
        renderTablePagination('predictions', pageData);
        const countEl = document.querySelector('#section-predictions .result-count');
        if (countEl) countEl.textContent = data.length ? `${data.length} kết quả` : '';
        const isPending = (document.getElementById('pred-filter')?.value || '') === 'cho_duyet';
        document.getElementById('pred-body').innerHTML = data.length
          ? pageData.rows.map((p, idx) => `
          <tr>
            <td>${isPending ? `<input type="checkbox" class="form-check-input pred-check" data-id="${p.id}" onchange="updateBatchToolbar()">` : ''}</td>
            <td class="stt-cell">${pageData.start + idx + 1}</td>
            <td><span class="text-muted fw-semibold">${p.id}</span></td>
            <td>${p.scan_image_url ? `<img src="${p.scan_image_url}" style="width:40px;height:40px;object-fit:cover;border-radius:4px;cursor:pointer;" onclick="openPredModal(${p.id})" title="Xem chi tiết">` : '<span class="text-muted" style="font-size:.75rem;">N/A</span>'}</td>
            <td><code class="cell-ellipsis code-cell" title="${escHtml(p.nhan_du_doan || '')}">${escHtml(p.nhan_du_doan || '—')}</code></td>
            <td>${confBar(p.do_tin_cay)}</td>
            <td>${statusBadge(p.trang_thai)}</td>
            <td class="text-muted" style="font-size:.82rem;">${fmtDate(p.thoi_gian)}</td>
            <td>
              <div class="btn-actions">
                <button class="btn-act" onclick="openPredModal(${p.id})" title="Chi tiết"><i class="bi bi-eye"></i></button>
              </div>
            </td>
          </tr>`).join('')
          : emptyRow(9, 'bi-inbox', 'Không có prediction nào');
        document.getElementById('pred-check-all').checked = false;
        updateBatchToolbar();
      } catch (e) { toast('Lỗi tải predictions: ' + e.message, 'danger'); }
    }

    function statusBadge(s) {
      const map = { cho_duyet: 'badge-pending', da_duyet: 'badge-approved', tu_choi: 'badge-rejected' };
      const labels = { cho_duyet: 'Chờ duyệt', da_duyet: 'Đã duyệt', tu_choi: 'Từ chối' };
      return `<span class="badge ${map[s] || 'badge-neutral'}">${labels[s] || s}</span>`;
    }

    async function openPredModal(id) {
      const modal = bootstrap.Modal.getOrCreateInstance(document.getElementById('pred-modal'));
      document.getElementById('pm-id').textContent = id;
      document.getElementById('pm-body').innerHTML = `<div class="text-center py-4"><div class="spinner-border text-primary" style="width:1.5rem;height:1.5rem;"></div></div>`;
      document.getElementById('pm-footer').innerHTML = '';
      modal.show();
      try {
        const p = await apiJSON(`/predictions/${id}`);
        const vp = p.vocab_payload;
        const relatedImages = p.related_images || [];
        const rootPredictionId = p.du_doan_goc_id || p.id;
        const canEditRelatedImages = p.trang_thai === 'cho_duyet';
        const relatedImagesHtml = relatedImages.length ? `
      <div class="mb-3">
        <div class="d-flex align-items-center justify-content-between mb-2">
          <p class="fw-bold mb-0" style="font-size:.85rem;">
            <i class="bi bi-images text-primary me-1"></i>Ảnh liên quan
          </p>
          <span class="badge badge-neutral">${relatedImages.length} ảnh</span>
        </div>
        <div class="d-flex flex-wrap gap-2">
          ${relatedImages.map(img => `
            <div style="width:92px;">
              <img src="${img.image_url}"
                   style="width:92px;height:92px;object-fit:cover;border-radius:8px;border:1px solid #dee2e6;cursor:zoom-in;"
                   alt="Ảnh liên quan"
                   onclick="zoomImage('${img.image_url}')"
                   title="Scan #${img.scan_id || '—'}">
              <div class="mt-1" style="font-size:.72rem;line-height:1.2;">
                <span class="badge ${img.vai_tro === 'chinh' ? 'badge-approved' : 'badge-neutral'}">
                  ${img.vai_tro === 'chinh' ? 'Chính' : 'Bổ sung'}
                </span>
                <div class="text-muted mt-1">Scan #${img.scan_id || '—'}</div>
                ${canEditRelatedImages ? `
                  <div class="d-flex gap-1 mt-1 flex-wrap">
                    ${img.vai_tro !== 'chinh' ? `<button class="btn-act del" style="width:28px;height:26px;" onclick="detachReviewImage(${img.prediction_id}, ${rootPredictionId})" title="Bỏ khỏi nhóm duyệt"><i class="bi bi-trash3"></i></button>
                    <button class="btn-act" style="width:28px;height:26px;" onclick="openReviewImageReassign(${img.prediction_id}, '${p.nhan_du_doan || ''}', ${rootPredictionId})" title="Chuyển sang object đã có"><i class="bi bi-arrow-left-right"></i></button>` : ''}
                    <button class="btn-act" style="width:28px;height:26px;color:#7c3aed;" onclick="openSplitToNewObject(${img.prediction_id}, ${rootPredictionId}, '${img.vai_tro}')" title="Tách thành object mới"><i class="bi bi-scissors"></i></button>
                  </div>` : ''}
              </div>
            </div>
          `).join('')}
        </div>
      </div>` : '';
        const langFlags = { en: '🇬🇧', vi: '🇻🇳', ja: '🇯🇵', ko: '🇰🇷', zh: '🇨🇳', fr: '🇫🇷', de: '🇩🇪' };
        let html = `
      ${p.scan_image_url ? `
      <div class="text-center mb-3">
        <img src="${p.scan_image_url}" style="max-height:220px;max-width:100%;border-radius:8px;object-fit:contain;border:1px solid #dee2e6;cursor:zoom-in;" alt="Ảnh quét" onclick="zoomImage('${p.scan_image_url}')" title="Nhấn để xem to">
      </div>` : ''}
      ${relatedImagesHtml}
      <div class="row g-2 mb-3">
        <div class="col-4"><div class="p-2 rounded bg-light">
          <div class="text-muted" style="font-size:.7rem;font-weight:600;text-transform:uppercase;">Nhãn</div>
          <code style="font-size:.88rem;">${p.nhan_du_doan || '—'}</code>
        </div></div>
        <div class="col-4"><div class="p-2 rounded bg-light">
          <div class="text-muted" style="font-size:.7rem;font-weight:600;text-transform:uppercase;">Độ tin cậy</div>
          <div class="mt-1">${confBar(p.do_tin_cay)}</div>
        </div></div>
        <div class="col-4"><div class="p-2 rounded bg-light">
          <div class="text-muted" style="font-size:.7rem;font-weight:600;text-transform:uppercase;">Trạng thái</div>
          <div class="mt-1">${statusBadge(p.trang_thai)}</div>
        </div></div>
      </div>`;
        if (vp) {
          html += `<p class="fw-bold mb-2" style="font-size:.85rem;">
        <i class="bi bi-book text-primary me-1"></i>Vocab Payload — <code style="font-size:.82rem;">${vp.object_code}</code></p>`;
          (vp.translations || []).forEach(t => {
            const examplesHtml = (t.example_sentences || []).map((s, idx) => {
              const en = typeof s === 'object' ? (s.en || '') : s;
              const vi = typeof s === 'object' ? (s.vi || '') : '';
              return `<div class="p-2 mb-2 rounded border" style="background:#fff;font-size:.84rem;line-height:1.45;">
                <div class="d-flex gap-2">
                  <span class="badge badge-neutral" style="height:fit-content;">${idx + 1}</span>
                  <div style="min-width:0;flex:1;">
                    <div>${en || ''}</div>
                    ${vi ? `<div class="text-muted fst-italic mt-1">${vi}</div>` : ''}
                  </div>
                </div>
              </div>`;
            }).join('');
            html += `<div class="vocab-lang-block">
          <div class="d-flex align-items-center gap-2 mb-2">
            <span>${langFlags[t.lang_code] || '🌐'}</span>
            <span class="badge badge-lang">${t.lang_code.toUpperCase()}</span>
            <strong>${t.word_name || ''}</strong>
            ${t.phonetic ? `<span class="text-muted fst-italic" style="font-size:.82rem;">${t.phonetic}</span>` : ''}
            ${t.part_of_speech ? `<span class="badge" style="background:#e9d5ff;color:#6b21a8;">${t.part_of_speech}</span>` : ''}
          </div>
          ${t.definition ? `<div class="mb-3" style="font-size:.85rem;line-height:1.5;"><div class="text-muted fw-semibold mb-1" style="font-size:.72rem;text-transform:uppercase;">Định nghĩa</div>${t.definition}</div>` : ''}
          ${examplesHtml ? `<div><div class="text-muted fw-semibold mb-2" style="font-size:.72rem;text-transform:uppercase;">Ví dụ</div>${examplesHtml}</div>` : ''}
        </div>`;
          });
        }
        document.getElementById('pm-body').innerHTML = html;
        if (p.trang_thai === 'cho_duyet') {
          document.getElementById('pm-footer').innerHTML = `
        <button class="btn btn-sm btn-outline-secondary" data-bs-dismiss="modal">Đóng</button>
        <button class="btn btn-sm btn-danger ms-auto me-1" onclick="rejectPred(${id})">
          <i class="bi bi-x-lg me-1"></i>Từ chối
        </button>
        <button class="btn btn-sm btn-outline-primary me-1" onclick="showAliasForm(${id})">
          <i class="bi bi-link-45deg me-1"></i>Gán bí danh
        </button>
        <button class="btn btn-sm btn-warning me-1" onclick="showApproveForm(${id})">
          <i class="bi bi-pencil me-1"></i>Chỉnh & Duyệt
        </button>
        <button class="btn btn-sm btn-success" onclick="approvePredQuick(${id})">
          <i class="bi bi-check-lg me-1"></i>Duyệt ngay
        </button>`;
        } else {
          document.getElementById('pm-footer').innerHTML = `<button class="btn btn-sm btn-outline-secondary" data-bs-dismiss="modal">Đóng</button>`;
        }
      } catch (e) {
        document.getElementById('pm-body').innerHTML = `<div class="alert alert-danger">Lỗi tải dữ liệu: ${e.message}</div>`;
      }
    }

    async function approvePred(id, overrides) {
      try {
        await apiJSON(`/predictions/${id}/approve`, { method: 'POST', body: JSON.stringify(overrides || {}) });
        toast('Đã duyệt prediction #' + id);
        bootstrap.Modal.getInstance(document.getElementById('pred-modal'))?.hide();
        loadPredictions(); updatePendingBadge();
      } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
    }

    function approvePredQuick(id) {
      confirmAction(`Duyệt prediction #${id}?`, () => approvePred(id, {}), 'Xác nhận duyệt');
    }

    async function detachReviewImage(predictionId, rootPredictionId) {
      confirmAction('Bỏ ảnh này khỏi nhóm duyệt? Ảnh vẫn được giữ trong lịch sử quét.', async () => {
        try {
          await apiJSON(`/predictions/${predictionId}/detach-image`, { method: 'PATCH' });
          toast('Đã bỏ ảnh khỏi nhóm duyệt', 'warning');
          openPredModal(rootPredictionId);
          loadPredictions();
          updatePendingBadge();
        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      }, 'Bỏ ảnh khỏi nhóm');
    }


    let _splitPredictionId = null;
    let _splitRootPredictionId = null;
    let _splitVaiTro = null;

    function openSplitToNewObject(predictionId, rootPredictionId, vaiTro) {
      _splitPredictionId = predictionId;
      _splitRootPredictionId = rootPredictionId;
      _splitVaiTro = vaiTro || null;
      document.getElementById('sm-current-code').textContent = '—';
      document.getElementById('sm-new-code').value = '';
      document.getElementById('sm-error').style.display = 'none';
      apiJSON(`/predictions/${rootPredictionId}`).then(p => {
        document.getElementById('sm-current-code').textContent = p.nhan_du_doan || '—';
      }).catch(() => {});
      bootstrap.Modal.getOrCreateInstance(document.getElementById('split-modal')).show();
    }

    document.addEventListener('DOMContentLoaded', function() {
      document.getElementById('sm-confirm').addEventListener('click', async function() {
        const newCode = (document.getElementById('sm-new-code').value || '').trim().toLowerCase().replace(/\s+/g, '_');
        const errEl = document.getElementById('sm-error');
        if (!newCode) {
          errEl.textContent = 'Vui lòng nhập mã đối tượng mới';
          errEl.style.display = '';
          return;
        }
        errEl.style.display = 'none';
        const btn = document.getElementById('sm-confirm');
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Đang xử lý...';
        try {
          const res = await apiJSON(
            `/predictions/${_splitPredictionId}/split-to-new-object?new_object_code=${encodeURIComponent(newCode)}`,
            { method: 'PATCH' }
          );
          bootstrap.Modal.getInstance(document.getElementById('split-modal')).hide();
          const vocabMsg = res.vocab_generated
            ? ' Gemini đã sinh từ vựng tự động.'
            : ' (Từ vựng chưa sinh được, admin cần nhập thủ công khi duyệt)';
          toast(`Đã tách thành prediction #${res.new_prediction_id} cho "${res.new_object_code}".${vocabMsg}`, 'success', 6000);
          if (_splitVaiTro === 'chinh') {
            bootstrap.Modal.getInstance(document.getElementById('pred-modal'))?.hide();
          } else {
            openPredModal(_splitRootPredictionId);
          }
          loadPredictions();
          updatePendingBadge();
        } catch (e) {
          errEl.textContent = 'Lỗi: ' + e.message;
          errEl.style.display = '';
        } finally {
          btn.disabled = false;
          btn.innerHTML = '<i class="bi bi-scissors me-1"></i>Tách';
        }
      });
    });

    async function showAliasForm(id) {
      const p = await apiJSON(`/predictions/${id}`).catch(() => null);
      const aliasCode = (p?.vocab_payload?.object_code || p?.nhan_du_doan || '').trim();
      let objects = [];
      try { objects = await apiJSON('/objects?limit=200'); }
      catch (e) { toast('Lỗi tải danh sách đối tượng: ' + e.message, 'danger'); return; }

      const options = objects.map(o => {
        const aliases = (o.aliases || []).map(a => a.ma_bi_danh).join(', ');
        const aliasText = aliases ? ` | alias: ${aliases}` : '';
        const categoryText = o.category_name ? ` - ${o.category_name}` : '';
        return `<option value="${o.id}">${o.ma_doi_tuong}${categoryText}${aliasText}</option>`;
      }).join('');

      document.getElementById('pm-body').innerHTML = `
    <p class="text-muted mb-3" style="font-size:.82rem;">
      <i class="bi bi-info-circle me-1"></i>
      Dùng khi Gemini gọi cùng một vật thể bằng tên khác. Prediction sẽ được duyệt, ảnh/lịch sử nối về đối tượng chính, còn tên này lưu trong <code>BiDanhDoiTuong</code>.
    </p>
    <div class="mb-3">
      <label class="form-label fw-semibold" style="font-size:.8rem;">Mã bí danh</label>
      <input id="alias-code" class="form-control form-control-sm" value="${aliasCode}" placeholder="vd: glasses">
    </div>
    <div class="mb-3">
      <label class="form-label fw-semibold" style="font-size:.8rem;">Đối tượng chính</label>
      <select id="alias-target" class="form-select form-select-sm">${options}</select>
    </div>
    <div class="row g-2">
      <div class="col-8">
        <label class="form-label fw-semibold" style="font-size:.8rem;">Tên hiển thị</label>
        <input id="alias-display" class="form-control form-control-sm" value="${aliasCode.replaceAll('_', ' ')}">
      </div>
      <div class="col-4">
        <label class="form-label fw-semibold" style="font-size:.8rem;">Ngôn ngữ</label>
        <input id="alias-lang" class="form-control form-control-sm" value="en">
      </div>
    </div>`;

      document.getElementById('pm-footer').innerHTML = `
    <button class="btn btn-sm btn-outline-secondary" onclick="openPredModal(${id})"><i class="bi bi-arrow-left me-1"></i>Quay lại</button>
    <button class="btn btn-sm btn-primary ms-auto" onclick="confirmAlias(${id})">
      <i class="bi bi-link-45deg me-1"></i>Gán bí danh
    </button>`;
    }

    async function confirmAlias(id) {
      const target = Number(document.getElementById('alias-target')?.value || 0);
      const aliasCode = document.getElementById('alias-code')?.value.trim();
      const display = document.getElementById('alias-display')?.value.trim();
      const lang = document.getElementById('alias-lang')?.value.trim() || 'en';
      if (!target) { toast('Chọn đối tượng chính', 'warning'); return; }
      if (!aliasCode) { toast('Nhập mã bí danh', 'warning'); return; }
      try {
        const result = await apiJSON(`/predictions/${id}/alias`, {
          method: 'POST',
          body: JSON.stringify({
            doi_tuong_id: target,
            ma_bi_danh: aliasCode,
            ten_hien_thi: display || null,
            ngon_ngu: lang,
          }),
        });
        toast(result.message || 'Đã gán bí danh');
        bootstrap.Modal.getInstance(document.getElementById('pred-modal'))?.hide();
        loadPredictions(); loadObjects(); updatePendingBadge();
      } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
    }

    function normalizeApproveExamples(rawExamples) {
      const source = Array.isArray(rawExamples) ? rawExamples : [];
      return source.slice(0, 3).map(item => {
        if (item && typeof item === 'object') {
          return {
            en: item.en || item.cau_vi_du || '',
            vi: item.vi || item.dich_nghia || '',
          };
        }
        const raw = String(item || '');
        const parts = raw.split('|');
        return {
          en: (parts.shift() || '').trim(),
          vi: parts.join('|').trim(),
        };
      });
    }

    function renderApproveExampleEditors(rawExamples) {
      const items = normalizeApproveExamples(rawExamples);
      while (items.length < 3) items.push({ en: '', vi: '' });
      return items.map((ex, idx) => `
        <div class="approve-example-row">
          <div class="approve-example-index">${idx + 1}</div>
          <div>
            <label class="form-label mb-1">English</label>
            <textarea id="ov-ex-en-${idx}" class="form-control form-control-sm approve-ex-en" rows="2"
              placeholder="I need an eraser.">${escHtml(ex.en || '')}</textarea>
          </div>
          <div>
            <label class="form-label mb-1">Vietnamese</label>
            <textarea id="ov-ex-vi-${idx}" class="form-control form-control-sm approve-ex-vi" rows="2"
              placeholder="Tôi cần một cục tẩy.">${escHtml(ex.vi || '')}</textarea>
          </div>
        </div>`).join('');
    }

    function collectApproveExamples() {
      return Array.from(document.querySelectorAll('.approve-example-row'))
        .map(row => {
          const en = row.querySelector('.approve-ex-en')?.value.trim() || '';
          const vi = row.querySelector('.approve-ex-vi')?.value.trim() || '';
          if (!en && !vi) return '';
          return en && vi ? `${en} | ${vi}` : (en || vi);
        })
        .filter(Boolean);
    }

    async function showApproveForm(id) {
      const p = await apiJSON(`/predictions/${id}`).catch(() => null);
      const vp = p?.vocab_payload;
      const firstTrans = vp?.translations?.[0] || {};
      const exampleEditors = renderApproveExampleEditors(firstTrans.example_sentences || []);

      document.getElementById('pm-body').innerHTML = `
    <div class="approve-note">
      <i class="bi bi-pencil-square"></i>
      <span>Chỉnh sửa trước khi duyệt. <strong>Để trống = giữ nguyên giá trị Gemini.</strong></span>
    </div>

    <div class="approve-card">
      <p class="approve-card-title">Thông tin cơ bản</p>
      <div class="approve-basic-grid mb-3">
        <div>
          <label class="form-label mb-1">Từ vựng</label>
          <input id="ov-word" class="form-control form-control-sm" placeholder="vd: Eraser" value="${escHtml(firstTrans.word_name || '')}">
        </div>
        <div>
          <label class="form-label mb-1">Phiên âm (IPA)</label>
          <input id="ov-phonetic" class="form-control form-control-sm" placeholder="vd: /ɪˈreɪ.zər/" value="${escHtml(firstTrans.phonetic || '')}">
        </div>
        <div>
          <label class="form-label mb-1">Loại từ</label>
          <input id="ov-pos" class="form-control form-control-sm" placeholder="n / v / adj" value="${escHtml(firstTrans.part_of_speech || '')}">
        </div>
      </div>

      <div>
        <label class="form-label mb-1">Định nghĩa</label>
        <textarea id="ov-def" class="form-control form-control-sm" style="resize:none;line-height:1.55;" placeholder="Nhập định nghĩa...">${escHtml(firstTrans.definition || '')}</textarea>
      </div>
    </div>

    <div class="approve-card">
      <p class="approve-card-title">Ví dụ</p>
      <div class="approve-examples-hint">Mỗi dòng là một ví dụ. Có thể chỉ nhập English nếu chưa có nghĩa tiếng Việt.</div>
      <div class="approve-examples">${exampleEditors}</div>
    </div>`;

      // Auto-resize textareas theo nội dung sẵn có.
      ['ov-def', ...Array.from(document.querySelectorAll('.approve-example-row textarea')).map(ta => ta.id)].forEach(tid => {
        const ta = document.getElementById(tid);
        if (!ta) return;
        ta.style.height = 'auto';
        ta.style.height = ta.scrollHeight + 'px';
        ta.addEventListener('input', () => { ta.style.height = 'auto'; ta.style.height = ta.scrollHeight + 'px'; });
      });

      document.getElementById('pm-footer').innerHTML = `
    <button class="btn btn-sm btn-outline-secondary" onclick="openPredModal(${id})">
      <i class="bi bi-arrow-left me-1"></i>Quay lại
    </button>
    <button class="btn btn-sm btn-success ms-auto px-3" onclick="confirmApproveWithEdits(${id})">
      <i class="bi bi-check-lg me-1"></i>Xác nhận duyệt
    </button>`;
    }

    async function confirmApproveWithEdits(id) {
      const word = document.getElementById('ov-word')?.value.trim();
      const phonetic = document.getElementById('ov-phonetic')?.value.trim();
      const pos = document.getElementById('ov-pos')?.value.trim();
      const def = document.getElementById('ov-def')?.value.trim();
      const examples = collectApproveExamples();

      const overrides = {};
      if (word) overrides.override_word_name = word;
      if (phonetic) overrides.override_phonetic = phonetic;
      if (pos) overrides.override_part_of_speech = pos;
      if (def) overrides.override_definition = def;
      if (examples.length) overrides.override_example_sentences = examples;

      await approvePred(id, overrides);
    }

    async function rejectPred(id) {
      confirmAction('Từ chối prediction này?', async () => {
        try {
          await apiJSON(`/predictions/${id}/reject`, { method: 'POST' });
          toast('Đã từ chối prediction #' + id, 'warning');
          bootstrap.Modal.getInstance(document.getElementById('pred-modal'))?.hide();
          loadPredictions(); updatePendingBadge();
        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      });
    }

    async function approvePredDash(id) {
      confirmAction(`Duyệt prediction #${id}?`, async () => {
        try {
          await apiJSON(`/predictions/${id}/approve`, { method: 'POST', body: JSON.stringify({}) });
          toast('Đã duyệt prediction #' + id);
          loadDashboard();
        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      }, 'Xác nhận duyệt');
    }

    async function rejectPredDash(id) {
      confirmAction(`Từ chối prediction #${id}?`, async () => {
        try {
          await apiJSON(`/predictions/${id}/reject`, { method: 'POST' });
          toast('Đã từ chối prediction #' + id, 'warning');
          loadDashboard();
        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      });
    }

    async function updatePendingBadge() {
      try {
        const stats = await apiJSON('/dashboard');
        const badge = document.getElementById('badge-predictions');
        if (stats.pending_predictions > 0) { badge.textContent = stats.pending_predictions; badge.style.display = ''; }
        else { badge.style.display = 'none'; }
      } catch (_) { }
    }

    // ============================================================
    // Categories
    // ============================================================
    async function loadCategories() {
      try {
        categories = await apiJSON('/categories');
        const filterEl = document.getElementById('obj-cat-filter');
        if (filterEl) {
          const cur = filterEl.value;
          filterEl.innerHTML = '<option value="">Tất cả danh mục</option>' +
            categories.map(c => `<option value="${c.id}" ${String(c.id) === cur ? 'selected' : ''}>${c.ten_danh_muc}</option>`).join('');
        }
        const tbody = document.getElementById('cat-body');
        if (!tbody) return;
        const pageData = getPagedRows('categories', categories);
        renderTablePagination('categories', pageData);
        const countEl = document.querySelector('#section-categories .result-count');
        if (countEl) countEl.textContent = categories.length ? `${categories.length} danh mục` : '';
        tbody.innerHTML = categories.length
          ? pageData.rows.map((c, idx) => {
            const parentName = categories.find(x => x.id === c.danh_muc_cha)?.ten_danh_muc;
            return `<tr>
            <td class="stt-cell">${pageData.start + idx + 1}</td>
            <td class="text-muted">${c.id}</td>
            <td class="fw-semibold">${c.ten_danh_muc || '—'}</td>
            <td>${parentName ? `<span class="badge badge-neutral">${parentName}</span>` : '<span class="text-muted">—</span>'}</td>
            <td class="text-muted" style="font-size:.82rem;">${c.mo_ta || '—'}</td>
            <td><span class="badge badge-neutral">${c.object_count ?? 0}</span></td>
            <td>
              <div class="btn-actions">
                <button class="btn-act" onclick='openEditCatModal(${JSON.stringify(c)})' title="Sửa"><i class="bi bi-pencil"></i></button>
                <button class="btn-act del" onclick="deleteCat(${c.id})" title="Xoá"><i class="bi bi-trash3"></i></button>
              </div>
            </td>
          </tr>`;
          }).join('')
          : emptyRow(7, 'bi-folder-x', 'Chưa có danh mục nào');
      } catch (e) { toast('Lỗi tải danh mục: ' + e.message, 'danger'); }
    }

    function catOptions(selectedId) {
      return `<option value="">— Không có —</option>` +
        categories.map(c => `<option value="${c.id}" ${c.id === selectedId ? 'selected' : ''}>${c.ten_danh_muc}</option>`).join('');
    }

    function openCreateCatModal() {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'Thêm danh mục mới';
      document.getElementById('fm-body').innerHTML = `
    <div class="mb-3"><label class="form-label">Tên danh mục <span class="text-danger">*</span></label>
      <input id="cat-name" class="form-control form-control-sm" placeholder="Ví dụ: Trái cây" /></div>
    <div class="mb-3"><label class="form-label">Danh mục cha</label>
      <select id="cat-parent" class="form-select form-select-sm">${catOptions(null)}</select></div>
    <div class="mb-3"><label class="form-label">Mô tả</label>
      <textarea id="cat-desc" class="form-control form-control-sm" rows="2"></textarea></div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const body = { ten_danh_muc: document.getElementById('cat-name').value.trim(), danh_muc_cha: document.getElementById('cat-parent').value || null, mo_ta: document.getElementById('cat-desc').value.trim() || null };
        if (!body.ten_danh_muc) { toast('Nhập tên danh mục', 'warning'); return; }
        try { await apiJSON('/categories', { method: 'POST', body: JSON.stringify(body) }); toast('Đã thêm danh mục'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadCategories(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    function openEditCatModal(c) {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'Sửa danh mục #' + c.id;
      document.getElementById('fm-body').innerHTML = `
    <div class="mb-3"><label class="form-label">Tên danh mục <span class="text-danger">*</span></label>
      <input id="cat-name" class="form-control form-control-sm" value="${c.ten_danh_muc || ''}" /></div>
    <div class="mb-3"><label class="form-label">Danh mục cha</label>
      <select id="cat-parent" class="form-select form-select-sm">${catOptions(c.danh_muc_cha)}</select></div>
    <div class="mb-3"><label class="form-label">Mô tả</label>
      <textarea id="cat-desc" class="form-control form-control-sm" rows="2">${c.mo_ta || ''}</textarea></div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const body = { ten_danh_muc: document.getElementById('cat-name').value.trim(), danh_muc_cha: document.getElementById('cat-parent').value || null, mo_ta: document.getElementById('cat-desc').value.trim() || null };
        try { await apiJSON(`/categories/${c.id}`, { method: 'PUT', body: JSON.stringify(body) }); toast('Đã cập nhật danh mục'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadCategories(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    async function deleteCat(id) {
      confirmAction('Xoá danh mục này? Hành động không thể hoàn tác.', async () => {
        try { await apiJSON(`/categories/${id}`, { method: 'DELETE' }); toast('Đã xoá danh mục', 'warning'); loadCategories(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      }, 'Xoá danh mục');
    }

    // ============================================================
    // Objects
    // ============================================================
    async function loadObjects() {
      const search = document.getElementById('obj-search')?.value || '';
      const catId = document.getElementById('obj-cat-filter')?.value || '';
      try {
        let url = `/objects?limit=100&offset=0`;
        if (search) url += `&search=${encodeURIComponent(search)}`;
        if (catId) url += `&category_id=${catId}`;
        const rawData = await apiJSON(url);
        const noImageObjs = rawData.filter(o => !o.has_image);
        const data = objectOnlyNoImage ? noImageObjs : rawData;
        const pageData = getPagedRows('objects', data);
        renderTablePagination('objects', pageData);
        const countEl = document.querySelector('#section-objects .result-count');
        if (countEl) {
          if (objectOnlyNoImage) {
            countEl.innerHTML = `Lọc: ${data.length} chưa có ảnh — <a href="#" onclick="clearNoImageFilter();return false;" style="font-weight:600;">Xem tất cả</a>`;
          } else {
            countEl.textContent = data.length ? `${data.length} đối tượng` : '';
          }
        }
        const warnEl = document.getElementById('obj-image-warning');
        const warnText = document.getElementById('obj-image-warning-text');
        if (noImageObjs.length > 0) {
          warnText.textContent = `${noImageObjs.length} đối tượng đang thiếu ảnh đại diện`;
          warnEl.style.display = '';
        } else {
          warnEl.style.display = 'none';
        }
        document.getElementById('obj-body').innerHTML = data.length
          ? pageData.rows.map((o, idx) => {
            const objectCode = escHtml(o.ma_doi_tuong || '—');
            const objectCodeRaw = o.ma_doi_tuong || '';
            const objectCodeArg = escHtml(JSON.stringify(objectCodeRaw));
            const aliases = (o.aliases || []).slice(0, 4);
            const aliasHtml = aliases.length ? `
              <div class="alias-chips">
                <span class="alias-label">Alias</span>
                ${aliases.map(a => `<span class="alias-chip cell-ellipsis" title="${escHtml(a.ma_bi_danh || '')}">${escHtml(a.ma_bi_danh || '')}</span>`).join('')}
                ${(o.aliases || []).length > aliases.length ? `<span class="alias-chip">+${(o.aliases || []).length - aliases.length}</span>` : ''}
              </div>` : '';
            return `
          <tr data-has-image="${o.has_image ? '1' : '0'}">
            <td class="stt-cell">${pageData.start + idx + 1}</td>
            <td class="text-muted fw-semibold">${o.id}</td>
            <td>
              <div class="object-main">
                <div class="object-code-line">
                  <code class="cell-ellipsis code-cell" title="${objectCode}">${objectCode}</code>
                </div>
                ${aliasHtml}
              </div>
            </td>
            <td>${o.category_name ? `<span class="badge badge-neutral">${escHtml(o.category_name)}</span>` : '<span class="text-muted">—</span>'}</td>
            <td><div class="object-trans-stack">${translationBadge(o.translation_count, o.pending_translation_count)}</div></td>
            <td class="text-end">
              <div class="btn-actions object-actions">
                <button class="btn-act" style="position:relative;" onclick="openObjMediaModal(${objectCodeArg})" title="${!o.has_image ? 'Thêm ảnh đại diện' : 'Quản lý ảnh'}"><i class="bi bi-image"></i>${!o.has_image ? '<span style="position:absolute;top:2px;right:2px;width:6px;height:6px;border-radius:50%;background:#f59e0b;display:block;"></span>' : ''}</button>
                <button class="btn-act" onclick='openEditObjModal(${JSON.stringify(o)})' title="Sửa"><i class="bi bi-pencil"></i></button>
                <div class="dropdown action-menu">
                  <button class="btn-act dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false" title="Thao tác khác">
                    <i class="bi bi-three-dots"></i>
                  </button>
                  <ul class="dropdown-menu dropdown-menu-end">
                    <li><button type="button" class="dropdown-item" onclick='openObjectAliasModal(${JSON.stringify(o)})'><i class="bi bi-link-45deg"></i>Tên gọi khác</button></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><button type="button" class="dropdown-item text-danger" onclick="deleteObj(${o.id})"><i class="bi bi-trash3"></i>Xoá đối tượng</button></li>
                  </ul>
                </div>
              </div>
            </td>
          </tr>`;
          }).join('')
          : emptyRow(5, 'bi-box-seam', 'Không có đối tượng nào');
      } catch (e) { toast('Lỗi tải đối tượng: ' + e.message, 'danger'); }
    }

    function openCreateObjectModal() {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'Thêm đối tượng mới';
      document.getElementById('fm-body').innerHTML = `
    <div class="mb-3"><label class="form-label">Mã đối tượng <span class="text-danger">*</span></label>
      <input id="obj-code" class="form-control form-control-sm" placeholder="vd: apple, dog, car..." /></div>
    <div class="mb-3"><label class="form-label">Danh mục</label>
      <select id="obj-cat" class="form-select form-select-sm">${catOptions(null)}</select></div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const body = { ma_doi_tuong: document.getElementById('obj-code').value.trim(), danh_muc_id: document.getElementById('obj-cat').value || null };
        if (!body.ma_doi_tuong) { toast('Nhập mã đối tượng', 'warning'); return; }
        try { await apiJSON('/objects', { method: 'POST', body: JSON.stringify(body) }); toast('Đã thêm đối tượng'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadObjects(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    function openEditObjModal(o) {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'Sửa đối tượng: ' + o.ma_doi_tuong;
      document.getElementById('fm-body').innerHTML = `
    <div class="mb-3"><label class="form-label">Danh mục</label>
      <select id="obj-cat" class="form-select form-select-sm">${catOptions(o.danh_muc_id)}</select></div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const body = { danh_muc_id: document.getElementById('obj-cat').value || null };
        try { await apiJSON(`/objects/${o.id}`, { method: 'PUT', body: JSON.stringify(body) }); toast('Đã cập nhật'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadObjects(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    function getAliasSuggestion(objectCode) {
      const code = String(objectCode || '').trim().toLowerCase();
      if (!code) {
        return {
          alias: 'tomatoes',
          display: 'Tomatoes',
          examples: ['tomatoes', 'cherry_tomato', 'ca_chua'],
        };
      }
      if (code === 'tomato' || code === 'tomatoes') {
        return {
          alias: 'tomatoes',
          display: 'Tomatoes',
          examples: ['tomatoes', 'cherry_tomato', 'ca_chua'],
        };
      }
      if (code === 'key' || code === 'keys') {
        return {
          alias: code === 'keys' ? 'key' : 'keys',
          display: code === 'keys' ? 'Key' : 'Keys',
          examples: ['key', 'keys', 'house_keys'],
        };
      }
      const plural = code.endsWith('s')
        ? code
        : code.endsWith('y')
          ? code.slice(0, -1) + 'ies'
          : code + 's';
      return {
        alias: plural,
        display: plural.replaceAll('_', ' '),
        examples: [plural, code.replaceAll('_', ' ')],
      };
    }

    function openObjectAliasModal(o) {
      document.getElementById('fm-submit').style.display = 'none';
      document.getElementById('fm-title').textContent = 'Tên gọi khác: ' + o.ma_doi_tuong;
      const aliases = o.aliases || [];
      const objectCode = escHtml(o.ma_doi_tuong || '');
      const suggestion = getAliasSuggestion(o.ma_doi_tuong);
      const aliasExample = escHtml(suggestion.alias);
      const displayExample = escHtml(suggestion.display);
      const examplesText = suggestion.examples.map(x => `<code>${escHtml(x)}</code>`).join(', ');
      document.getElementById('fm-body').innerHTML = `
    <div class="alias-help">
      <i class="bi bi-info-circle"></i>
      <div>
        <strong>Alias là nhãn phụ được gom về object chính.</strong>
        Nếu hệ thống nhận ra một tên khác nhưng thực ra vẫn là <code>${objectCode}</code>,
        nhập tên đó ở đây thay vì tạo object mới.
        <div class="mt-1">Ví dụ có thể gắn: ${examplesText}</div>
        <div class="alias-flow">
          <div class="alias-flow-cell">
            <strong>Nhãn hệ thống trả về</strong>
            <code>${aliasExample}</code>
          </div>
          <div class="alias-flow-arrow"><i class="bi bi-arrow-right"></i></div>
          <div class="alias-flow-cell">
            <strong>Object chính trong DB</strong>
            <code>${objectCode}</code>
          </div>
        </div>
      </div>
    </div>

    <div>
      <div class="d-flex align-items-center justify-content-between mb-2">
        <label class="form-label fw-semibold mb-0">Tên gọi khác hiện có</label>
        <span class="badge badge-neutral">${aliases.length} alias</span>
      </div>
      <div id="obj-alias-list" class="alias-list">
        ${aliases.length ? aliases.map(a => `
          <div class="alias-item">
            <div style="min-width:0;">
              <code class="cell-ellipsis" style="max-width:220px;" title="${escHtml(a.ma_bi_danh || '')}">${escHtml(a.ma_bi_danh || '')}</code>
              ${a.ten_hien_thi ? `<div class="text-muted mt-1" style="font-size:.78rem;">Hiển thị: ${escHtml(a.ten_hien_thi)}</div>` : ''}
            </div>
            <button class="btn-act del" onclick="deleteObjectAlias(${a.id})" title="Xoá tên gọi này">
              <i class="bi bi-trash3"></i>
            </button>
          </div>`).join('') : '<div class="alias-empty"><i class="bi bi-link-45deg me-1"></i>Chưa có tên gọi khác cho object này.</div>'}
      </div>
    </div>

    <div class="border rounded p-3" style="background:#fafbfc;">
      <div class="fw-semibold mb-2" style="font-size:.84rem;">Gắn nhãn phụ vào object này</div>
      <div class="alias-form-grid">
        <div>
          <label class="form-label">Nhãn phụ / alias <span class="text-danger">*</span></label>
          <input id="obj-alias-code" class="form-control form-control-sm" placeholder="vd: ${aliasExample}">
        </div>
        <div>
          <label class="form-label">Tên hiển thị cho người học</label>
          <input id="obj-alias-display" class="form-control form-control-sm" placeholder="vd: ${displayExample}">
        </div>
        <div>
          <label class="form-label">Ngôn ngữ</label>
          <input id="obj-alias-lang" class="form-control form-control-sm" value="en">
        </div>
      </div>
      <div class="form-text mt-2" style="font-size:.76rem;">
        Sau khi lưu, alias này sẽ trỏ về <code>${objectCode}</code>. Không tạo object mới.
      </div>
      <div class="d-flex justify-content-end mt-3">
        <button class="btn btn-sm btn-primary" onclick="saveObjectAlias(${o.id})">
          <i class="bi bi-link-45deg me-1"></i>Gắn vào ${objectCode}
        </button>
      </div>
    </div>`;
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    async function saveObjectAlias(objectId) {
      const code = document.getElementById('obj-alias-code')?.value.trim();
      const display = document.getElementById('obj-alias-display')?.value.trim();
      const lang = document.getElementById('obj-alias-lang')?.value.trim() || 'en';
      if (!code) { toast('Nhập nhãn phụ / alias', 'warning'); return; }
      try {
        await apiJSON('/object-aliases', {
          method: 'POST',
          body: JSON.stringify({
            doi_tuong_id: objectId,
            ma_bi_danh: code,
            ten_hien_thi: display || null,
            ngon_ngu: lang,
          }),
        });
        toast('Đã lưu tên gọi khác');
        bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide();
        loadObjects();
      } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
    }

    async function deleteObjectAlias(aliasId) {
      confirmAction('Xóa bí danh này?', async () => {
        try {
          await apiJSON(`/object-aliases/${aliasId}`, { method: 'DELETE' });
          toast('Đã xóa bí danh', 'warning');
          bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide();
          loadObjects();
        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      }, 'Xóa bí danh');
    }

    async function deleteObj(id) {
      confirmAction('Xoá đối tượng này? Tất cả bản dịch liên quan sẽ bị xoá.', async () => {
        try { await apiJSON(`/objects/${id}`, { method: 'DELETE' }); toast('Đã xoá đối tượng', 'warning'); loadObjects(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      }, 'Xoá đối tượng');
    }

    function zoomImage(url) {
      const overlay = document.createElement('div');
      overlay.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,.85);z-index:9999;display:flex;align-items:center;justify-content:center;cursor:zoom-out;';
      overlay.onclick = () => overlay.remove();
      const img = document.createElement('img');
      img.src = url;
      img.style.cssText = 'max-width:90vw;max-height:90vh;border-radius:8px;box-shadow:0 8px 40px rgba(0,0,0,.6);object-fit:contain;';
      overlay.appendChild(img);
      document.body.appendChild(overlay);
    }

    function toggleAllTransChecks(masterCb) {
      document.querySelectorAll('.trans-check').forEach(cb => { cb.checked = masterCb.checked; });
      updateTransBatchToolbar();
    }

    function updateTransBatchToolbar() {
      const checked = document.querySelectorAll('.trans-check:checked');
      const toolbar = document.getElementById('trans-batch-toolbar');
      if (!toolbar) return;
      if (checked.length > 0) {
        toolbar.classList.remove('is-hidden');
        document.getElementById('trans-batch-count').textContent = checked.length;
      } else {
        toolbar.classList.add('is-hidden');
      }
    }

    async function batchApproveTrans() {
      const ids = Array.from(document.querySelectorAll('.trans-check:checked')).map(el => parseInt(el.dataset.id));
      if (!ids.length) return;
      confirmAction(`Xác nhận ${ids.length} bản dịch đã chọn?`, async () => {
        let ok = 0;
        for (const id of ids) {
          try { await apiJSON(`/translations/${id}`, { method: 'PUT', body: JSON.stringify({ da_xac_nhan: true }) }); ok++; }
          catch {}
        }
        toast(`Đã xác nhận ${ok}/${ids.length} bản dịch`);
        loadTranslations();
      }, 'Xác nhận bản dịch');
    }

    function filterNoImage() {
      objectOnlyNoImage = true;
      reloadPagedTable('objects');
    }

    function clearNoImageFilter() {
      objectOnlyNoImage = false;
      reloadPagedTable('objects');
    }

    // ============================================================
    // Translations
    // ============================================================
    async function loadTranslations() {
      const search = document.getElementById('trans-search')?.value || '';
      const lang = document.getElementById('trans-lang-filter')?.value || '';
      const approved = document.getElementById('trans-approved-filter')?.value || '';
      try {
        let url = `/translations?limit=100`;
        if (search) url += `&search=${encodeURIComponent(search)}`;
        if (lang) url += `&lang_code=${lang}`;
        if (approved !== '') url += `&approved=${approved}`;
        const data = await apiJSON(url);
        window._transCache = {};
        data.forEach(t => { window._transCache[t.id] = t; });
        const pageData = getPagedRows('translations', data);
        renderTablePagination('translations', pageData);
        const countEl = document.querySelector('#section-translations .result-count');
        if (countEl) countEl.textContent = data.length ? `${data.length} bản dịch` : '';
        document.getElementById('trans-body').innerHTML = data.length
          ? pageData.rows.map((t, idx) => `
          <tr>
            <td><input type="checkbox" class="form-check-input trans-check" data-id="${t.id}" data-approved="${t.da_xac_nhan}" onchange="updateTransBatchToolbar()"></td>
            <td class="stt-cell">${pageData.start + idx + 1}</td>
            <td class="text-muted">${t.id}</td>
            <td><code class="cell-ellipsis code-cell" title="${escHtml(t.object_code || String(t.doi_tuong_id || ''))}">${escHtml(t.object_code || t.doi_tuong_id || '—')}</code></td>
            <td><span class="badge badge-lang">${(t.lang_code || '').toUpperCase()}</span></td>
            <td class="fw-semibold"><span class="cell-ellipsis name-cell" title="${escHtml(t.tu_vung || '')}">${escHtml(t.tu_vung || '—')}</span></td>
            <td class="text-muted fst-italic" style="font-size:.82rem;">${t.phien_am || '—'}</td>
            <td>${t.da_xac_nhan
              ? '<span class="badge badge-approved"><i class="bi bi-check-lg me-1"></i>Đã xác nhận</span>'
              : '<span class="badge badge-neutral">Chưa xác nhận</span>'}</td>
            <td><span class="badge badge-neutral">${t.example_count}</span></td>
            <td>
              <div class="btn-actions">
                <button class="btn-act" onclick="openViewTransModal(window._transCache[${t.id}])" title="Xem"><i class="bi bi-eye"></i></button>
                <button class="btn-act" onclick="openEditTransModal(window._transCache[${t.id}])" title="Sửa"><i class="bi bi-pencil"></i></button>
                <button class="btn-act del" onclick="deleteTrans(${t.id})" title="Xoá"><i class="bi bi-trash3"></i></button>
              </div>
            </td>
          </tr>`).join('')
          : emptyRow(10, 'bi-translate', 'Không có bản dịch nào');
      } catch (e) { toast('Lỗi tải bản dịch: ' + e.message, 'danger'); }
    }

    function openCreateTransModal() {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'Thêm bản dịch mới';
      document.getElementById('fm-body').innerHTML = `
    <div class="row g-2 mb-2">
      <div class="col-6"><label class="form-label">Object ID <span class="text-danger">*</span></label>
        <input id="t-oid" type="number" class="form-control form-control-sm" /></div>
      <div class="col-6"><label class="form-label">Ngôn ngữ <span class="text-danger">*</span></label>
        <select id="t-lang" class="form-select form-select-sm">
          <option value="en">English</option>
          <option value="vi">Tiếng Việt</option>
        </select></div>
    </div>
    <div class="mb-2"><label class="form-label">Từ vựng <span class="text-danger">*</span></label>
      <input id="t-word" class="form-control form-control-sm" /></div>
    <div class="row g-2 mb-2">
      <div class="col-6"><label class="form-label">Phiên âm</label>
        <input id="t-phon" class="form-control form-control-sm" placeholder="/ˈæp.əl/" /></div>
      <div class="col-6"><label class="form-label">Loại từ</label>
        <input id="t-pos" class="form-control form-control-sm" placeholder="n. / v. / adj." /></div>
    </div>
    <div class="mb-2"><label class="form-label">Định nghĩa</label>
      <textarea id="t-def" class="form-control form-control-sm" rows="2"></textarea></div>
    <div class="mb-2"><label class="form-label">Ví dụ <small class="text-muted">(mỗi dòng 1 câu)</small></label>
      <textarea id="t-ex" class="form-control form-control-sm" rows="3" placeholder="A red apple is on the table. | Một quả táo đỏ ở trên bàn.&#10;She ate an apple every morning."></textarea></div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const body = {
          doi_tuong_id: parseInt(document.getElementById('t-oid').value),
          lang_code: document.getElementById('t-lang').value,
          tu_vung: document.getElementById('t-word').value.trim(),
          phien_am: document.getElementById('t-phon').value.trim() || null,
          loai_tu: document.getElementById('t-pos').value.trim() || null,
          dinh_nghia: document.getElementById('t-def').value.trim() || null,
          example_sentences: document.getElementById('t-ex').value.split('\n').map(s => s.trim()).filter(Boolean),
        };
        if (!body.doi_tuong_id || !body.tu_vung) { toast('Thiếu thông tin bắt buộc', 'warning'); return; }
        try { await apiJSON('/translations', { method: 'POST', body: JSON.stringify(body) }); toast('Đã thêm bản dịch'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadTranslations(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    function openViewTransModal(t) {
      document.getElementById('fm-submit').style.display = 'none';
      document.getElementById('fm-title').textContent = 'Chi tiết bản dịch #' + t.id;
      const exList = Array.isArray(t.examples) ? t.examples : [];
      const examples = exList.length
        ? `<ol class="mb-0 ps-3">${exList.map(e => `
            <li style="font-size:.83rem;">${e.cau_vi_du || ''}
              ${e.dich_nghia ? `<div class="text-muted" style="font-size:.79rem;font-style:italic;">${e.dich_nghia}</div>` : ''}
            </li>`).join('')}</ol>`
        : '<span class="text-muted" style="font-size:.83rem;">Chưa có ví dụ</span>';
      document.getElementById('fm-body').innerHTML = `
    <div class="mb-2 p-2 rounded" style="background:#f8f9fa;font-size:.82rem;">
      Object: <code>${t.object_code || t.doi_tuong_id}</code>
      <span class="badge badge-lang ms-1">${(t.lang_code || '').toUpperCase()}</span>
      ${t.da_xac_nhan ? '<span class="badge badge-approved ms-1"><i class="bi bi-check-lg me-1"></i>Đã xác nhận</span>' : '<span class="badge badge-neutral ms-1">Chưa xác nhận</span>'}
    </div>
    <table class="table table-sm table-borderless mb-2" style="font-size:.85rem;">
      <tr><th style="width:110px;color:var(--text-muted);">Từ vựng</th><td class="fw-semibold">${t.tu_vung || '—'}</td></tr>
      <tr><th style="color:var(--text-muted);">Phiên âm</th><td class="fst-italic text-primary">${t.phien_am || '—'}</td></tr>
      <tr><th style="color:var(--text-muted);">Loại từ</th><td>${t.loai_tu || '—'}</td></tr>
      <tr><th style="color:var(--text-muted);">Định nghĩa</th><td>${t.dinh_nghia || '—'}</td></tr>
    </table>
    <div><label class="form-label fw-semibold" style="font-size:.83rem;">Ví dụ (${t.example_count || 0})</label>${examples}</div>`;
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    function openEditTransModal(t) {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'Sửa bản dịch #' + t.id;
      document.getElementById('fm-body').innerHTML = `
    <div class="mb-2 p-2 rounded bg-light" style="font-size:.82rem;">
      Object: <code>${t.object_code || t.doi_tuong_id}</code>
      <span class="badge badge-lang ms-1">${(t.lang_code || '').toUpperCase()}</span>
    </div>
    <div class="mb-2"><label class="form-label">Từ vựng</label>
      <input id="t-word" class="form-control form-control-sm" value="${t.tu_vung || ''}" /></div>
    <div class="row g-2 mb-2">
      <div class="col-6"><label class="form-label">Phiên âm</label>
        <input id="t-phon" class="form-control form-control-sm" value="${t.phien_am || ''}" /></div>
      <div class="col-6"><label class="form-label">Loại từ</label>
        <input id="t-pos" class="form-control form-control-sm" value="${t.loai_tu || ''}" /></div>
    </div>
    <div class="mb-2"><label class="form-label">Định nghĩa</label>
      <textarea id="t-def" class="form-control form-control-sm" rows="2">${t.dinh_nghia || ''}</textarea></div>
    <div class="mb-2"><label class="form-label">Trạng thái xác nhận</label>
      <select id="t-confirmed" class="form-select form-select-sm">
        <option value="true" ${t.da_xac_nhan ? 'selected' : ''}>Đã xác nhận</option>
        <option value="false" ${!t.da_xac_nhan ? 'selected' : ''}>Chưa xác nhận</option>
      </select></div>
    <div class="mb-2"><label class="form-label">Ví dụ <small class="text-muted">(mỗi dòng 1 câu, có thể thêm <code>| dịch nghĩa</code>)</small></label>
      <textarea id="t-ex" class="form-control form-control-sm" rows="4" placeholder="A red apple. | Một quả táo đỏ.&#10;She reads books.">${(t.examples||[]).map(e => e.dich_nghia ? e.cau_vi_du + ' | ' + e.dich_nghia : e.cau_vi_du||'').join('\n')}</textarea></div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const exRaw = document.getElementById('t-ex')?.value.trim();
        const body = {
          tu_vung: document.getElementById('t-word').value.trim() || null,
          phien_am: document.getElementById('t-phon').value.trim() || null,
          loai_tu: document.getElementById('t-pos').value.trim() || null,
          dinh_nghia: document.getElementById('t-def').value.trim() || null,
          da_xac_nhan: document.getElementById('t-confirmed').value === 'true',
          example_sentences: exRaw ? exRaw.split('\n').map(s => s.trim()).filter(Boolean) : [],
        };
        try { await apiJSON(`/translations/${t.id}`, { method: 'PUT', body: JSON.stringify(body) }); toast('Đã cập nhật bản dịch'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadTranslations(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    async function deleteTrans(id) {
      confirmAction('Xoá bản dịch này?', async () => {
        try { await apiJSON(`/translations/${id}`, { method: 'DELETE' }); toast('Đã xoá bản dịch', 'warning'); loadTranslations(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      }, 'Xoá bản dịch');
    }

    // ============================================================
    // Training Data
    // ============================================================
    async function loadTrainingData() {
      const grid = document.getElementById('td-grid');
      const empty = document.getElementById('td-empty');
      grid.innerHTML = '<div class="text-muted p-3"><span class="spinner-border spinner-border-sm me-2"></span>Đang tải...</div>';
      empty.style.display = 'none';
      try {
        const records = await apiJSON('/training-summary');
        if (!records || records.length === 0) {
          grid.innerHTML = '';
          empty.style.display = '';
          document.getElementById('td-total-objects').textContent = '0';
          document.getElementById('td-total-images').textContent = '0';
          return;
        }
        const totalImages = records.reduce((s, r) => s + (r.total_images || 0), 0);
        document.getElementById('td-total-objects').textContent = records.length;
        document.getElementById('td-total-images').textContent = totalImages;

        grid.innerHTML = records.map(r => {
          const thumbs = (r.images || []).map(img => `
            <div class="td-thumb-wrap">
              <img src="${img.url}" alt="" title="${img.source}" onclick="zoomImage('${img.url}')"
                style="border:2px solid ${img.source === 'confirmed' ? '#86efac' : '#93c5fd'};border-radius:6px;"
                onerror="this.parentElement.style.display='none'">
              <div class="td-thumb-actions">
                <button class="btn-act del" onclick="unlinkScan(${img.scan_id},'${r.object_code}')" title="Xoá khỏi pool"><i class="bi bi-trash3"></i></button>
                <button class="btn-act" onclick="openReassign(${img.scan_id},'${r.object_code}')" title="Chuyển đối tượng"><i class="bi bi-arrow-left-right"></i></button>
              </div>
            </div>`
          ).join('');
          const translations = (r.translations || []).slice(0, 3).map(t =>
            `<span class="badge bg-light text-dark border" title="${escHtml(t.word || '')}" style="font-size:.72rem;">${escHtml(t.language_code || '')}: ${escHtml(t.word || '')}</span>`
          ).join('');
          const badgeColor = r.total_images >= 5 ? '#22c55e' : r.total_images >= 2 ? '#f59e0b' : '#94a3b8';
          return `
          <div class="panel" style="padding:0;overflow:hidden;">
            <div style="padding:.75rem 1rem .5rem;display:flex;align-items:center;gap:.5rem;">
              <span class="cell-ellipsis code-cell" style="font-weight:600;font-size:.92rem;flex:1;" title="${escHtml(r.object_code || '')}">${escHtml(r.object_code || '')}</span>
              <span style="background:${badgeColor};color:#fff;border-radius:99px;padding:2px 10px;font-size:.75rem;font-weight:600;">
                ${r.total_images} ảnh
              </span>
            </div>
            ${r.category ? `<div style="padding:0 1rem .4rem;font-size:.75rem;color:#64748b;">${escHtml(r.category)}</div>` : ''}
            ${translations ? `<div class="td-card-translations">${translations}</div>` : ''}
            <div style="padding:.25rem .75rem .8rem;display:flex;flex-wrap:wrap;gap:5px;min-height:60px;">
              ${thumbs || '<span class="text-muted" style="font-size:.8rem;">Chưa có ảnh</span>'}
            </div>
          </div>`;
        }).join('');
      } catch (e) {
        grid.innerHTML = `<div class="text-danger p-3">Lỗi: ${e.message}</div>`;
      }
    }

    // Training image management
    async function unlinkScan(scanId, objectCode) {
      if (!confirm(`Bỏ ảnh này khỏi pool training của "${objectCode}"?\n(Ảnh vẫn được giữ trong lịch sử quét, chỉ không dùng để train)`)) return;
      try {
        await apiJSON(`/training-images/${scanId}/unlink`, { method: 'PATCH' });
        toast('Đã bỏ liên kết ảnh', 'success');
        loadTrainingData();
      } catch (e) {
        toast('Lỗi: ' + e.message, 'danger');
      }
    }

    let _reassignScanId = null;
    let _reassignCurrentCode = null;
    let _reassignMode = 'training';
    let _reassignPredictionId = null;
    let _reassignRootPredictionId = null;
    let _cachedObjects = [];

    async function openReassign(scanId, currentCode) {
      _reassignMode = 'training';
      _reassignScanId = scanId;
      _reassignPredictionId = null;
      _reassignRootPredictionId = null;
      _reassignCurrentCode = currentCode;
      document.getElementById('rm-current-code').textContent = currentCode;
      document.getElementById('rm-search').value = '';
      if (_cachedObjects.length === 0) {
        try { _cachedObjects = await apiJSON('/objects?limit=500'); } catch (_) { _cachedObjects = []; }
      }
      _fillReassignSelect('');
      bootstrap.Modal.getOrCreateInstance(document.getElementById('reassign-modal')).show();
    }

    async function openReviewImageReassign(predictionId, currentCode, rootPredictionId) {
      _reassignMode = 'review';
      _reassignScanId = null;
      _reassignPredictionId = predictionId;
      _reassignRootPredictionId = rootPredictionId;
      _reassignCurrentCode = currentCode;
      document.getElementById('rm-current-code').textContent = currentCode;
      document.getElementById('rm-search').value = '';
      if (_cachedObjects.length === 0) {
        try { _cachedObjects = await apiJSON('/objects?limit=500'); } catch (_) { _cachedObjects = []; }
      }
      _fillReassignSelect('');
      bootstrap.Modal.getOrCreateInstance(document.getElementById('reassign-modal')).show();
    }

    function _fillReassignSelect(search) {
      const sel = document.getElementById('rm-select');
      const q = (search || '').toLowerCase();
      const filtered = _cachedObjects
        .filter(o => o.ma_doi_tuong !== _reassignCurrentCode)
        .filter(o => !q || o.ma_doi_tuong.includes(q) || (o.category_name || '').toLowerCase().includes(q))
        .slice(0, 60);
      sel.innerHTML = filtered.map(o =>
        `<option value="${o.ma_doi_tuong}">${o.ma_doi_tuong}${o.category_name ? ' — ' + o.category_name : ''}</option>`
      ).join('') || '<option disabled>Không tìm thấy</option>';
    }

    document.getElementById('rm-search').addEventListener('input', e => _fillReassignSelect(e.target.value));

    document.getElementById('rm-confirm').addEventListener('click', async () => {
      const sel = document.getElementById('rm-select');
      const targetCode = sel.value;
      if (!targetCode || sel.options[sel.selectedIndex]?.disabled) {
        toast('Vui lòng chọn đối tượng đích', 'warning'); return;
      }
      try {
        if (_reassignMode === 'review') {
          await apiJSON(`/predictions/${_reassignPredictionId}/reassign-image?target_object_code=${encodeURIComponent(targetCode)}`, { method: 'PATCH' });
        } else {
          await apiJSON(`/training-images/${_reassignScanId}/reassign?target_object_code=${encodeURIComponent(targetCode)}`, { method: 'PATCH' });
        }
        bootstrap.Modal.getInstance(document.getElementById('reassign-modal')).hide();
        toast(`Đã chuyển ảnh sang "${targetCode}"`, 'success');
        _cachedObjects = [];
        if (_reassignMode === 'review') {
          openPredModal(_reassignRootPredictionId);
          loadPredictions();
          updatePendingBadge();
        } else {
          loadTrainingData();
        }
      } catch (e) {
        toast('Lỗi: ' + e.message, 'danger');
      }
    });

    // Users
    // ============================================================
    async function loadUsers() {
      const search = document.getElementById('user-search')?.value || '';
      const roleFilter = document.getElementById('user-role-filter')?.value || '';
      const statusFilter = document.getElementById('user-status-filter')?.value || '';
      try {
        let data = await apiJSON(`/users?limit=200${search ? '&search=' + encodeURIComponent(search) : ''}`);
        if (roleFilter) data = data.filter(u => (u.vai_tro || '').toLowerCase().includes(roleFilter));
        if (statusFilter === 'active') data = data.filter(u => (u.trang_thai || '').toLowerCase().includes('hoat') || u.trang_thai === 'active');
        if (statusFilter === 'locked') data = data.filter(u => !((u.trang_thai || '').toLowerCase().includes('hoat') || u.trang_thai === 'active'));
        const pageData = getPagedRows('users', data);
        renderTablePagination('users', pageData);
        const panel = document.querySelector('#section-users .panel-header .result-count');
        if (panel) panel.textContent = `${data.length} người dùng`;
        document.getElementById('user-body').innerHTML = data.length
          ? pageData.rows.map((u, idx) => {
            const isAdmin = (u.vai_tro || '').toLowerCase().includes('admin') || (u.vai_tro || '').includes('quan_tri');
            const isActive = (u.trang_thai || '').toLowerCase().includes('hoat') || u.trang_thai === 'active';
            const usernameSafe = escHtml(u.ten_dang_nhap || '');
            const emailSafe = escHtml(u.email || '');
            const fullNameSafe = escHtml(u.ho_ten || '');
            const roleSafe = escHtml(u.vai_tro || '');
            const usernameArg = escHtml(JSON.stringify(u.ten_dang_nhap || ''));
            const roleArg = escHtml(JSON.stringify(u.vai_tro || ''));
            return `<tr>
            <td class="stt-cell">${pageData.start + idx + 1}</td>
            <td class="text-muted">${u.id}</td>
            <td>
              <div class="d-flex align-items-center gap-2">
                <div style="width:28px;height:28px;border-radius:50%;background:var(--primary);display:flex;align-items:center;justify-content:center;color:#fff;font-size:.7rem;font-weight:700;flex-shrink:0;">${(u.ten_dang_nhap || '?').charAt(0).toUpperCase()}</div>
                <span class="fw-semibold cell-ellipsis name-cell" title="${usernameSafe}">${usernameSafe}</span>
              </div>
            </td>
            <td class="text-muted" style="font-size:.82rem;"><span class="cell-ellipsis email-cell" title="${emailSafe}">${emailSafe}</span></td>
            <td><span class="cell-ellipsis name-cell" title="${fullNameSafe}">${u.ho_ten ? fullNameSafe : '—'}</span></td>
            <td><span class="badge ${isAdmin ? 'badge-role-admin' : 'badge-role-user'}">${u.vai_tro ? roleSafe : '—'}</span></td>
            <td><span class="badge ${isActive ? 'badge-approved' : 'badge-rejected'}">${isActive ? 'Hoạt động' : 'Bị khoá'}</span></td>
            <td class="text-muted" style="font-size:.82rem;">${fmtDateShort(u.ngay_tao)}</td>
            <td class="text-end">
              <div class="btn-actions">
                <button class="btn-act" onclick="openUserStatsModal(${u.id}, ${usernameArg})" title="Xem thống kê"><i class="bi bi-bar-chart-line"></i></button>
                <button class="btn-act" onclick="openUserRoleModal(${u.id}, ${roleArg})" title="Đổi vai trò"><i class="bi bi-person-gear"></i></button>
                <div class="dropdown action-menu">
                  <button class="btn-act dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false" title="Thao tác khác">
                    <i class="bi bi-three-dots"></i>
                  </button>
                  <ul class="dropdown-menu dropdown-menu-end">
                    <li><button type="button" class="dropdown-item" onclick="openResetPasswordModal(${u.id}, ${usernameArg})"><i class="bi bi-key"></i>Đặt lại mật khẩu</button></li>
                    <li><button type="button" class="dropdown-item" onclick="toggleUserStatus(${u.id}, ${isActive})"><i class="bi ${isActive ? 'bi-lock' : 'bi-unlock'}"></i>${isActive ? 'Khoá tài khoản' : 'Mở khoá'}</button></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><button type="button" class="dropdown-item text-danger" onclick="deleteUser(${u.id}, ${usernameArg})"><i class="bi bi-trash3"></i>Xoá tài khoản</button></li>
                  </ul>
                </div>
              </div>
            </td>
          </tr>`;
          }).join('')
          : emptyRow(9, 'bi-people', 'Không có người dùng nào');
      } catch (e) { toast('Lỗi tải users: ' + e.message, 'danger'); }
    }

    async function toggleUserStatus(userId, isCurrentlyActive) {
      const newStatusId = isCurrentlyActive ? 2 : 1;
      const action = isCurrentlyActive ? 'Khoá tài khoản này?' : 'Mở khoá tài khoản này?';
      confirmAction(action, async () => {
        try {
          await apiJSON(`/users/${userId}/status`, { method: 'PUT', body: JSON.stringify({ trang_thai_id: newStatusId }) });
          toast(isCurrentlyActive ? 'Đã khoá tài khoản' : 'Đã mở khoá tài khoản', isCurrentlyActive ? 'warning' : 'success');
          loadUsers();
        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      });
    }

    async function deleteUser(userId, username) {
      confirmAction(`Xoá tài khoản "${username}"? Hành động này không thể hoàn tác.`, async () => {
        try {
          await apiJSON(`/users/${userId}`, { method: 'DELETE' });
          toast('Đã xoá tài khoản', 'warning');
          loadUsers();
        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      }, 'Xoá tài khoản');
    }

    function openResetPasswordModal(userId, username) {
      document.getElementById('fm-title').textContent = `Đặt lại mật khẩu — ${username}`;
      document.getElementById('fm-body').innerHTML = `
    <div class="mb-3 p-2 rounded bg-light" style="font-size:.84rem;">
      <i class="bi bi-info-circle text-muted me-1"></i>
      Mật khẩu mới sẽ được áp dụng ngay, người dùng cần đăng nhập lại.
    </div>
    <div class="mb-3">
      <label class="form-label">Mật khẩu mới <span class="text-danger">*</span></label>
      <input id="new-password" type="password" class="form-control form-control-sm" placeholder="Tối thiểu 8 ký tự, có chữ hoa, thường và số" />
    </div>
    <div class="mb-1">
      <label class="form-label">Xác nhận mật khẩu</label>
      <input id="confirm-password" type="password" class="form-control form-control-sm" placeholder="Nhập lại mật khẩu" />
    </div>`;
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-submit').textContent = 'Đặt lại mật khẩu';
      document.getElementById('fm-submit').onclick = async () => {
        const pw = document.getElementById('new-password').value.trim();
        const cpw = document.getElementById('confirm-password').value.trim();
        if (!pw || pw.length < 8) { toast('Mật khẩu tối thiểu 8 ký tự', 'warning'); return; }
        if (!/[a-z]/.test(pw)) { toast('Mật khẩu phải có ít nhất 1 chữ thường', 'warning'); return; }
        if (!/[A-Z]/.test(pw)) { toast('Mật khẩu phải có ít nhất 1 chữ viết hoa', 'warning'); return; }
        if (!/\d/.test(pw)) { toast('Mật khẩu phải có ít nhất 1 chữ số', 'warning'); return; }
        if (pw !== cpw) { toast('Mật khẩu xác nhận không khớp', 'warning'); return; }
        try {
          await apiJSON(`/users/${userId}/reset-password`, { method: 'POST', body: JSON.stringify({ new_password: pw }) });
          toast('Đã đặt lại mật khẩu');
          bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide();
        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    function openUserRoleModal(userId, currentRole) {
      document.getElementById('fm-title').textContent = 'Cập nhật vai trò — #' + userId;
      const isAdmin = (currentRole || '').toLowerCase().includes('admin') || currentRole === 'quan_tri';
      document.getElementById('fm-body').innerHTML = `
    <div class="mb-3 p-2 rounded bg-light d-flex align-items-center gap-2" style="font-size:.84rem;">
      <i class="bi bi-person-circle text-muted"></i>
      Vai trò hiện tại: <span class="badge ${isAdmin ? 'badge-role-admin' : 'badge-role-user'} ms-1">${currentRole || '—'}</span>
    </div>
    <div class="mb-3"><label class="form-label">Vai trò mới</label>
      <select id="role-id" class="form-select form-select-sm">
        <option value="1" ${!isAdmin ? 'selected' : ''}>Người dùng (nguoi_dung)</option>
        <option value="2" ${isAdmin ? 'selected' : ''}>Quản trị (admin)</option>
      </select></div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const vai_tro_id = parseInt(document.getElementById('role-id').value);
        try { await apiJSON(`/users/${userId}/role`, { method: 'PUT', body: JSON.stringify({ vai_tro_id }) }); toast('Đã cập nhật vai trò'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadUsers(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    // ============================================================
    // Batch Predictions
    // ============================================================
    function toggleAllPredChecks(masterCb) {
      document.querySelectorAll('.pred-check').forEach(cb => { cb.checked = masterCb.checked; });
      updateBatchToolbar();
    }

    function updateBatchToolbar() {
      const checked = document.querySelectorAll('.pred-check:checked');
      const toolbar = document.getElementById('batch-toolbar');
      if (!toolbar) return;
      if (checked.length > 0) {
        toolbar.classList.remove('is-hidden');
        document.getElementById('batch-count').textContent = checked.length;
      } else {
        toolbar.classList.add('is-hidden');
      }
    }

    async function batchApprove() {
      const ids = Array.from(document.querySelectorAll('.pred-check:checked')).map(el => parseInt(el.dataset.id));
      if (!ids.length) { toast('Chọn ít nhất 1 prediction', 'warning'); return; }
      confirmAction(`Duyệt ${ids.length} prediction đã chọn?`, async () => {
        let ok = 0, fail = 0;
        for (const id of ids) {
          try { await apiJSON(`/predictions/${id}/approve`, { method: 'POST', body: JSON.stringify({}) }); ok++; }
          catch { fail++; }
        }
        toast(`Đã duyệt ${ok} prediction${fail ? `, ${fail} lỗi` : ''}`, fail ? 'warning' : 'success');
        loadPredictions(); updatePendingBadge();
      }, 'Duyệt hàng loạt');
    }

    async function batchReject() {
      const ids = Array.from(document.querySelectorAll('.pred-check:checked')).map(el => parseInt(el.dataset.id));
      if (!ids.length) { toast('Chọn ít nhất 1 prediction', 'warning'); return; }
      confirmAction(`Từ chối ${ids.length} prediction đã chọn?`, async () => {
        let ok = 0, fail = 0;
        for (const id of ids) {
          try { await apiJSON(`/predictions/${id}/reject`, { method: 'POST' }); ok++; }
          catch { fail++; }
        }
        toast(`Đã từ chối ${ok} prediction${fail ? `, ${fail} lỗi` : ''}`, 'warning');
        loadPredictions(); updatePendingBadge();
      }, 'Từ chối hàng loạt');
    }

    // ============================================================
    // Scan History
    // ============================================================
    async function loadScanHistory() {
      const objCode = document.getElementById('sh-obj-search')?.value.trim() || '';
      const username = document.getElementById('sh-user-search')?.value.trim() || '';
      const dateFrom = document.getElementById('sh-date-from')?.value || '';
      const dateTo = document.getElementById('sh-date-to')?.value || '';
      try {
        let url = `/scan-history?limit=100`;
        if (objCode) url += `&object_code=${encodeURIComponent(objCode)}`;
        if (username) url += `&username=${encodeURIComponent(username)}`;
        if (dateFrom) url += `&date_from=${dateFrom}`;
        if (dateTo) url += `&date_to=${dateTo}`;
        const data = await apiJSON(url);
        const pageData = getPagedRows('scanHistory', data);
        renderTablePagination('scanHistory', pageData);
        const countEl = document.querySelector('#section-scan-history .result-count');
        if (countEl) countEl.textContent = data.length ? `${data.length} lượt quét` : '';
        document.getElementById('sh-body').innerHTML = data.length
          ? pageData.rows.map((s, idx) => `
          <tr>
            <td class="stt-cell">${pageData.start + idx + 1}</td>
            <td class="text-muted">${s.id}</td>
            <td>${s.url_anh
              ? `<img src="${s.url_anh}" style="width:40px;height:40px;object-fit:cover;border-radius:4px;cursor:zoom-in;" onclick="zoomImage('${s.url_anh}')" title="Nhấn để xem to" />`
              : '<span class="text-muted" style="font-size:.75rem;">N/A</span>'}</td>
            <td>
              <span class="fw-semibold" style="font-size:.82rem;">${s.username || '—'}</span>
              ${s.user_id ? `<br><span class="text-muted" style="font-size:.72rem;">ID: ${s.user_id}</span>` : ''}
            </td>
            <td>${s.object_code
              ? `<code class="cell-ellipsis code-cell" title="${escHtml(s.object_code)}">${escHtml(s.object_code)}</code>`
              : s.has_pending_prediction
                ? '<span class="badge badge-pending">Chờ duyệt</span>'
                : '<span class="text-muted">—</span>'}</td>
            <td>${confBar(s.do_tin_cay)}</td>
            <td class="text-muted" style="font-size:.82rem;">${fmtDate(s.thoi_gian)}</td>
            <td><button class="btn btn-sm btn-outline-danger" style="padding:2px 7px;font-size:.75rem;" onclick="deleteScanHistory(${s.id})" title="Xóa"><i class="bi bi-trash"></i></button></td>
          </tr>`).join('')
          : '<tr><td colspan="8"><div class="empty-state"><i class="bi bi-camera"></i><p>Chưa có lịch sử quét</p></div></td></tr>';
      } catch (e) { toast('Lỗi tải lịch sử quét: ' + e.message, 'danger'); }
    }

    async function deleteScanHistory(scanId) {
      if (!confirm(`Xóa lịch sử quét #${scanId}?`)) return;
      try {
        await apiJSON(`/scan-history/${scanId}`, { method: 'DELETE' });
        toast('Đã xóa lịch sử quét', 'success');
        loadScanHistory();
      } catch (e) { toast('Lỗi xóa: ' + e.message, 'danger'); }
    }

    // ============================================================
    // Object Media Management
    // ============================================================
    async function openObjMediaModal(objectCode) {
      document.getElementById('fm-title').textContent = `Ảnh đối tượng — ${objectCode}`;
      document.getElementById('fm-submit').style.display = 'none';
      document.getElementById('fm-body').innerHTML = `<div class="text-center py-3"><div class="spinner-border text-primary" style="width:1.4rem;height:1.4rem;"></div></div>`;
      const modal = new bootstrap.Modal(document.getElementById('form-modal'));
      modal.show();

      async function renderMediaModal() {
        try {
          const media = await apiJSON(`/objects/${objectCode}/media`);
          let html = `
        <div class="mb-3">
          <label class="form-label fw-semibold">Ảnh hiện tại</label>
          ${media.length === 0
              ? '<p class="text-muted" style="font-size:.83rem;">Chưa có ảnh nào.</p>'
              : `<div class="d-flex flex-wrap gap-2">${media.map(m => `
                <div style="width:80px;">
                  <img src="${m.url}" onclick="zoomImage('${m.url}')" style="width:80px;height:80px;object-fit:cover;border-radius:6px;border:2px solid ${m.is_primary ? 'var(--primary)' : 'var(--border)'};cursor:zoom-in;" />
                  <div class="d-flex gap-1 mt-1 justify-content-center">
                    ${m.is_primary
                      ? `<button class="btn-act" style="width:36px;color:#f59e0b;" onclick="unsetMediaPrimary(${m.id}, '${objectCode}')" title="Bỏ ảnh chính"><i class="bi bi-star-fill"></i></button>`
                      : `<button class="btn-act" style="width:36px;" onclick="setMediaPrimary(${m.id}, '${objectCode}')" title="Đặt làm ảnh chính"><i class="bi bi-star"></i></button>`
                    }
                    <button class="btn-act del" style="width:36px;" onclick="deleteMedia(${m.id}, '${objectCode}')" title="Xoá"><i class="bi bi-trash3"></i></button>
                  </div>
                </div>`).join('')}</div>`}
        </div>
        <hr/>
        <label class="form-label fw-semibold">Thêm ảnh mới</label>
        <div class="mb-2">
          <label class="form-label">URL ảnh</label>
          <input id="media-url" class="form-control form-control-sm" placeholder="https://..." />
        </div>
        <div class="mb-2">
          <label class="form-label">Hoặc upload file</label>
          <input id="media-file" type="file" accept="image/*" class="form-control form-control-sm" />
        </div>
        <div class="form-check mb-2">
          <input class="form-check-input" type="checkbox" id="media-primary" checked />
          <label class="form-check-label" style="font-size:.83rem;">Đặt làm ảnh chính</label>
        </div>
        <button class="btn btn-sm btn-primary w-100" onclick="uploadObjectMedia('${objectCode}')">
          <i class="bi bi-upload me-1"></i>Lưu ảnh
        </button>`;
          document.getElementById('fm-body').innerHTML = html;
        } catch (e) {
          document.getElementById('fm-body').innerHTML = `<div class="alert alert-danger">Lỗi: ${e.message}</div>`;
        }
      }
      window._currentMediaObjectCode = objectCode;
      window._renderMediaModal = renderMediaModal;
      await renderMediaModal();
    }

    async function uploadObjectMedia(objectCode) {
      const urlInput = document.getElementById('media-url')?.value.trim();
      const fileInput = document.getElementById('media-file');
      const isPrimary = document.getElementById('media-primary')?.checked ?? true;

      const formData = new FormData();
      formData.append('is_primary', isPrimary);
      if (fileInput?.files?.[0]) {
        formData.append('image', fileInput.files[0]);
      } else if (urlInput) {
        formData.append('image_url', urlInput);
      } else {
        toast('Nhập URL hoặc chọn file ảnh', 'warning'); return;
      }

      try {
        await fetch(`${API}/objects/${objectCode}/media`, {
          method: 'POST',
          headers: { 'Authorization': 'Bearer ' + TOKEN },
          body: formData,
        }).then(async r => { if (!r.ok) throw new Error(await r.text()); return r.json(); });
        toast('Đã thêm ảnh');
        if (window._renderMediaModal) await window._renderMediaModal();
      } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
    }

    async function setMediaPrimary(mediaId, objectCode) {
      try {
        await apiJSON(`/objects/media/${mediaId}/primary`, { method: 'POST' });
        toast('Đã đặt ảnh chính');
        if (window._renderMediaModal) await window._renderMediaModal();
      } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
    }

    async function deleteMedia(mediaId, objectCode) {
      if (!window.confirm('Xoá ảnh này?')) return;
      try {
        await apiJSON(`/objects/media/${mediaId}`, { method: 'DELETE' });
        toast('Đã xoá ảnh', 'warning');
        if (window._renderMediaModal) await window._renderMediaModal();
      } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
    }

    async function unsetMediaPrimary(mediaId, objectCode) {
      try {
        await apiJSON(`/objects/media/${mediaId}/unset-primary`, { method: 'POST' });
        toast('Đã bỏ ảnh chính');
        if (window._renderMediaModal) await window._renderMediaModal();
      } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
    }

    // ============================================================
    // User Stats
    // ============================================================
    async function openUserStatsModal(userId, username) {
      document.getElementById('fm-title').textContent = `Thống kê — ${username}`;
      document.getElementById('fm-submit').style.display = 'none';
      document.getElementById('fm-body').innerHTML = `<div class="text-center py-3"><div class="spinner-border text-primary" style="width:1.4rem;height:1.4rem;"></div></div>`;
      new bootstrap.Modal(document.getElementById('form-modal')).show();
      try {
        const s = await apiJSON(`/users/${userId}/stats`);
        document.getElementById('fm-body').innerHTML = `
      <div class="row g-2 mb-3">
        <div class="col-6">
          <div class="p-3 rounded text-center" style="background:#e7f1ff;">
            <div style="font-size:1.6rem;font-weight:800;color:var(--primary);">${s.total_scans}</div>
            <div style="font-size:.72rem;font-weight:600;color:var(--muted);text-transform:uppercase;">Lượt quét</div>
          </div>
        </div>
        <div class="col-6">
          <div class="p-3 rounded text-center" style="background:#d1fae5;">
            <div style="font-size:1.6rem;font-weight:800;color:#059669;">${s.total_learned}</div>
            <div style="font-size:.72rem;font-weight:600;color:var(--muted);text-transform:uppercase;">Từ đã học</div>
          </div>
        </div>
        <div class="col-6">
          <div class="p-3 rounded text-center" style="background:#fef3c7;">
            <div style="font-size:1.6rem;font-weight:800;color:#d97706;">${s.total_reviews}</div>
            <div style="font-size:.72rem;font-weight:600;color:var(--muted);text-transform:uppercase;">Lượt ôn tập</div>
          </div>
        </div>
        <div class="col-6">
          <div class="p-3 rounded text-center" style="background:#fce7f3;">
            <div style="font-size:1.6rem;font-weight:800;color:#db2777;">🔥 ${s.streak_hien_tai}</div>
            <div style="font-size:.72rem;font-weight:600;color:var(--muted);text-transform:uppercase;">Streak hiện tại</div>
          </div>
        </div>
      </div>
      <div class="p-2 rounded bg-light" style="font-size:.82rem;">
        <div class="d-flex justify-content-between mb-1">
          <span class="text-muted">Streak dài nhất:</span>
          <strong>${s.streak_dai_nhat} ngày</strong>
        </div>
        <div class="d-flex justify-content-between mb-1">
          <span class="text-muted">Quét gần nhất:</span>
          <span>${fmtDate(s.last_scan_at)}</span>
        </div>
        <div class="d-flex justify-content-between">
          <span class="text-muted">Ôn tập gần nhất:</span>
          <span>${fmtDate(s.last_review_at)}</span>
        </div>
      </div>`;
      } catch (e) {
        document.getElementById('fm-body').innerHTML = `<div class="alert alert-danger">Lỗi: ${e.message}</div>`;
      }
    }

    // ============================================================
    // Startup
    // ============================================================
    if (TOKEN) {
      document.getElementById('login-screen').style.display = 'none';
      initApp();
    }
    document.getElementById('login-pass').addEventListener('keydown', e => { if (e.key === 'Enter') doLogin(); });
