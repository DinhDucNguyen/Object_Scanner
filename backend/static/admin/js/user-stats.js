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
        setModalBody('fm-body', `
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
      </div>`);
      } catch (e) {
        setModalBody('fm-body', `<div class="alert alert-danger">Lỗi: ${e.message}</div>`);
      }
    }
