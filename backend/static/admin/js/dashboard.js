// ============================================================
// Dashboard
// ============================================================

let predChartInstance = null;
let scanChartInstance = null;

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

    // --- Charts ---
    renderPredChart(stats);
    renderScanChart(stats);

    // --- Tables ---
    renderTopObjects(stats.top_objects || []);
    renderRecentUsers(stats.recent_users || []);
    renderCategoryStats(stats.category_stats || []);

  } catch (e) {
    toast('Lỗi tải dashboard: ' + e.message, 'danger');
  }
}

function renderPredChart(stats) {
  const ctx = document.getElementById('predRatioChart').getContext('2d');
  if (predChartInstance) predChartInstance.destroy();

  predChartInstance = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: ['Đã duyệt', 'Từ chối', 'Chờ duyệt'],
      datasets: [{
        data: [stats.approved_predictions, stats.rejected_predictions, stats.pending_predictions],
        backgroundColor: ['#10b981', '#ef4444', '#f59e0b'],
        borderWidth: 0
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { position: 'bottom' }
      },
      cutout: '70%'
    }
  });
}

function renderScanChart(stats) {
  const ctx = document.getElementById('scanActivityChart').getContext('2d');
  if (scanChartInstance) scanChartInstance.destroy();

  const data = stats.recent_scans || [];
  const labels = data.map(d => {
    const parts = d.date.split('-');
    return parts.length === 3 ? `${parts[2]}/${parts[1]}` : d.date;
  });
  const counts = data.map(d => d.count);

  scanChartInstance = new Chart(ctx, {
    type: 'line',
    data: {
      labels: labels,
      datasets: [{
        label: 'Lượt quét',
        data: counts,
        borderColor: '#3b82f6',
        backgroundColor: 'rgba(59, 130, 246, 0.1)',
        borderWidth: 2,
        fill: true,
        tension: 0.3
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false }
      },
      scales: {
        y: { beginAtZero: true, ticks: { precision: 0 } }
      }
    }
  });
}

function renderTopObjects(objects) {
  let html = '<table class="table table-hover table-sm mt-2"><thead><tr><th>ID</th><th>Mã đối tượng</th><th class="text-end">Lượt quét</th></tr></thead><tbody>';
  if (!objects.length) {
    html += '<tr><td colspan="3" class="text-center text-muted">Chưa có dữ liệu</td></tr>';
  } else {
    objects.forEach(o => {
      html += `<tr><td><span class="text-muted fw-semibold">${o.doi_tuong_id}</span></td><td><code>${escHtml(o.ma_doi_tuong)}</code></td><td class="text-end fw-bold">${o.count}</td></tr>`;
    });
  }
  html += '</tbody></table>';
  document.getElementById('dash-top-objects').innerHTML = html;
}

function renderRecentUsers(users) {
  let html = '<table class="table table-hover table-sm mt-2"><thead><tr><th>Username</th><th>Ngày đăng ký</th></tr></thead><tbody>';
  if (!users.length) {
    html += '<tr><td colspan="2" class="text-center text-muted">Chưa có dữ liệu</td></tr>';
  } else {
    users.forEach(u => {
      html += `<tr><td><strong>${escHtml(u.ten_dang_nhap)}</strong></td><td class="text-muted">${fmtDateShort(u.ngay_tao)}</td></tr>`;
    });
  }
  html += '</tbody></table>';
  document.getElementById('dash-recent-users').innerHTML = html;
}

function renderCategoryStats(cats) {
  let html = '<table class="table table-hover table-sm mt-2"><thead><tr><th>Danh mục</th><th class="text-end">Số ĐT</th></tr></thead><tbody>';
  if (!cats.length) {
    html += '<tr><td colspan="2" class="text-center text-muted">Chưa có dữ liệu</td></tr>';
  } else {
    cats.forEach(c => {
      html += `<tr><td>${escHtml(c.category_name)}</td><td class="text-end fw-bold">${c.count}</td></tr>`;
    });
  }
  html += '</tbody></table>';
  document.getElementById('dash-category-stats').innerHTML = html;
}
