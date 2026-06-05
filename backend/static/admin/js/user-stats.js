    // ============================================================
    // User Stats
    // ============================================================
    async function openUserStatsModal(userId, username) {
      document.getElementById('fm-title').textContent = `Thá»‘ng kÃª â€” ${username}`;
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
            <div style="font-size:.72rem;font-weight:600;color:var(--muted);text-transform:uppercase;">LÆ°á»£t quÃ©t</div>
          </div>
        </div>
        <div class="col-6">
          <div class="p-3 rounded text-center" style="background:#d1fae5;">
            <div style="font-size:1.6rem;font-weight:800;color:#059669;">${s.total_learned}</div>
            <div style="font-size:.72rem;font-weight:600;color:var(--muted);text-transform:uppercase;">Tá»« Ä‘Ã£ há»c</div>
          </div>
        </div>
        <div class="col-6">
          <div class="p-3 rounded text-center" style="background:#fef3c7;">
            <div style="font-size:1.6rem;font-weight:800;color:#d97706;">${s.total_reviews}</div>
            <div style="font-size:.72rem;font-weight:600;color:var(--muted);text-transform:uppercase;">LÆ°á»£t Ã´n táº­p</div>
          </div>
        </div>
        <div class="col-6">
          <div class="p-3 rounded text-center" style="background:#fce7f3;">
            <div style="font-size:1.6rem;font-weight:800;color:#db2777;">ðŸ”¥ ${s.streak_hien_tai}</div>
            <div style="font-size:.72rem;font-weight:600;color:var(--muted);text-transform:uppercase;">Streak hiá»‡n táº¡i</div>
          </div>
        </div>
      </div>
      <div class="p-2 rounded bg-light" style="font-size:.82rem;">
        <div class="d-flex justify-content-between mb-1">
          <span class="text-muted">Streak dÃ i nháº¥t:</span>
          <strong>${s.streak_dai_nhat} ngÃ y</strong>
        </div>
        <div class="d-flex justify-content-between mb-1">
          <span class="text-muted">QuÃ©t gáº§n nháº¥t:</span>
          <span>${fmtDate(s.last_scan_at)}</span>
        </div>
        <div class="d-flex justify-content-between">
          <span class="text-muted">Ã”n táº­p gáº§n nháº¥t:</span>
          <span>${fmtDate(s.last_review_at)}</span>
        </div>
      </div>`;
      } catch (e) {
        document.getElementById('fm-body').innerHTML = `<div class="alert alert-danger">Lá»—i: ${e.message}</div>`;
      }
    }

