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
          { label: 'NgÆ°á»i dÃ¹ng', value: stats.total_users, icon: 'bi-people-fill', color: '#6366f1', bg: '#eef2ff', nav: 'users' },
          { label: 'Äá»‘i tÆ°á»£ng', value: stats.total_objects, icon: 'bi-box-seam-fill', color: '#8b5cf6', bg: '#f5f3ff', nav: 'objects' },
          { label: 'Báº£n dá»‹ch', value: stats.total_translations, icon: 'bi-translate', color: '#10b981', bg: '#d1fae5', nav: 'translations' },
          { label: 'Lá»‹ch sá»­ quÃ©t', value: stats.total_scans, icon: 'bi-camera-fill', color: '#3b82f6', bg: '#dbeafe', nav: 'scan-history' },
          { label: 'Chá» duyá»‡t', value: stats.pending_predictions, icon: 'bi-hourglass-split', color: '#ef4444', bg: '#fee2e2', nav: 'predictions' },
          { label: 'ÄÃ£ duyá»‡t', value: stats.approved_predictions, icon: 'bi-check-circle-fill', color: '#10b981', bg: '#d1fae5', nav: 'predictions' },
          { label: 'ChÆ°a cÃ³ áº£nh', value: stats.objects_without_images || 0, icon: 'bi-image-slash', color: '#f59e0b', bg: '#fef3c7', nav: 'objects' },
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
          <thead><tr><th style="width:54px;">STT</th><th style="width:60px;">ID</th><th style="width:52px;">áº¢nh</th><th>NhÃ£n</th><th style="width:130px;">Tin cáº­y</th><th style="width:155px;">Thá»i gian</th><th style="width:130px;"></th></tr></thead>
          <tbody>${preds.map((p, idx) => `
            <tr>
              <td class="stt-cell">${idx + 1}</td>
              <td><span class="text-muted fw-semibold">${p.id}</span></td>
              <td>${p.scan_image_url ? `<img src="${p.scan_image_url}" style="width:40px;height:40px;object-fit:cover;border-radius:4px;cursor:pointer;" onclick="openPredModal(${p.id})" title="Xem chi tiáº¿t">` : '<span class="text-muted" style="font-size:.75rem;">N/A</span>'}</td>
              <td><code class="cell-ellipsis code-cell" title="${escHtml(p.nhan_du_doan || '')}">${escHtml(p.nhan_du_doan || 'â€”')}</code></td>
              <td>${confBar(p.do_tin_cay)}</td>
              <td class="text-muted" style="font-size:.82rem;">${fmtDate(p.thoi_gian)}</td>
              <td>
                <div class="btn-actions">
                  <button class="btn-act" onclick="openPredModal(${p.id})" title="Chi tiáº¿t"><i class="bi bi-eye"></i></button>
                  <button class="btn-act" onclick="approvePredDash(${p.id})" title="Duyá»‡t" style="color:#198754;"><i class="bi bi-check-lg"></i></button>
                  <button class="btn-act del" onclick="rejectPredDash(${p.id})" title="Tá»« chá»‘i"><i class="bi bi-x-lg"></i></button>
                </div>
              </td>
            </tr>`).join('')}
          </tbody></table></div>`
          : '<div class="empty-state"><i class="bi bi-check2-all"></i><p>KhÃ´ng cÃ³ prediction chá» duyá»‡t</p></div>';
      } catch (e) {
        toast('Lá»—i táº£i dashboard: ' + e.message, 'danger');
      }
    }

